package com.example.bilibili.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.brotli.dec.BrotliInputStream
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LiveDanmakuClient(
    private val api: BilibiliApiClient,
    private val roomId: Long,
    private val credential: BilibiliCredential?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _danmaku = MutableSharedFlow<BiliDanmakuItem>(extraBufferCapacity = 256)
    val danmaku: SharedFlow<BiliDanmakuItem> = _danmaku.asSharedFlow()

    private val _onlineRankTop3 = MutableStateFlow<List<BiliLiveRankUser>>(emptyList())
    val onlineRankTop3: StateFlow<List<BiliLiveRankUser>> = _onlineRankTop3.asStateFlow()
    private val _likeCount = MutableStateFlow<Long?>(null)
    val likeCount: StateFlow<Long?> = _likeCount.asStateFlow()

    private val _popularity = MutableStateFlow(0L)
    val popularity: StateFlow<Long> = _popularity.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _roomEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val roomEvents: SharedFlow<String> = _roomEvents.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var webHeartbeatJob: Job? = null
    private val heartbeatCounter = AtomicInteger(30)
    private var realRoomId: Long = 0L

    suspend fun connect(playInfo: BiliLivePlayResult) {
        android.util.Log.i("BiliLiveDanmaku", "开始获取弹幕配置 room=${playInfo.realRoomId}")
        val danmuInfo = api.getLiveDanmuInfo(playInfo.realRoomId, credential)
            ?: error("无法获取弹幕服务器配置")
        android.util.Log.i("BiliLiveDanmaku", "弹幕服务器数=${danmuInfo.hosts.size}")
        realRoomId = playInfo.realRoomId
        val token = danmuInfo.token
        var lastError: Throwable? = null
        for (host in danmuInfo.hosts.asReversed()) {
            val url = "wss://${host.host}:${host.wssPort}/sub"
            try {
                withTimeout(8_000) { connectToHost(url, realRoomId, token) }
                return
            } catch (error: Throwable) {
                if (error is CancellationException && !kotlin.coroutines.coroutineContext.isActive) throw error
                android.util.Log.w("BiliLiveDanmaku", "弹幕服务器连接失败", error)
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("无法连接弹幕服务器")
    }

    private suspend fun connectToHost(url: String, realRoomId: Long, token: String) =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BilibiliEndpoints.USER_AGENT)
                .header("Origin", BilibiliEndpoints.LIVE_HOME.trimEnd('/'))
                .build()
            val socket = client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        android.util.Log.i("BiliLiveDanmaku", "弹幕连接已建立")
                        webSocket.send(buildVerifyPacket(realRoomId, token))
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        runCatching { handlePacket(bytes.toByteArray(), onRejected = { code ->
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("弹幕认证失败：$code"))
                            webSocket.close(1000, "verification failed")
                        }) {
                            if (!_connected.value) {
                                _connected.value = true
                                startHeartbeat(realRoomId)
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }
                        } }.onFailure { error -> android.util.Log.w("BiliLiveDanmaku", "弹幕消息解析失败", error) }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        _connected.value = false
                        if (continuation.isActive) {
                            continuation.resumeWithException(t)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        _connected.value = false
                    }
                },
            )
            webSocket = socket
            continuation.invokeOnCancellation {
                socket.cancel()
            }
        }

    private fun handlePacket(raw: ByteArray, onRejected: (Int) -> Unit = {}, onVerified: () -> Unit = {}) {
        if (raw.size < 16) return
        val packets = unpack(raw)
        for (packet in packets) {
            when (packet.type) {
                PACKET_VERIFY_SUCCESS -> {
                    val code = packet.json?.optInt("code", -1) ?: -1
                    android.util.Log.i("BiliLiveDanmaku", "弹幕认证结果=$code")
                    if (code == 0) onVerified() else onRejected(code)
                }
                PACKET_HEARTBEAT_RESPONSE -> {
                    heartbeatCounter.set(30)
                    packet.view?.let { _popularity.value = it.toLong() }
                }
                PACKET_NOTICE -> {
                    val cmd = packet.json?.optString("cmd").orEmpty()
                if (cmd.isBlank() && packet.body != null) {
                    val users = parseOnlineRankProto(packet.body)
                    if (users.isNotEmpty()) _onlineRankTop3.value = users
                }
                    when {
                        cmd.startsWith("DANMU_MSG") -> {
                            android.util.Log.i("BiliLiveDanmaku", "收到弹幕消息")
                            packet.json?.optJSONArray("info")?.let { info ->
                                BilibiliJsonParser.parseLiveDanmakuMessage(info)?.let { item ->
                                    scope.launch { _danmaku.emit(item) }
                                }
                            }
                        }
                        cmd == "ONLINE_RANK_V2" || cmd == "ONLINE_RANK_V3" -> {
                            packet.json?.let { payload ->
                                val users = BilibiliJsonParser.parseLiveOnlineRankList(payload)
                                android.util.Log.i("BiliLiveDanmaku", "收到榜单，头像数=${users.size}")
                                if (users.isNotEmpty()) {
                                    _onlineRankTop3.value = users
                                }
                            }
                        }
                        cmd == "LIKE_INFO_V3_UPDATE" -> {
                            packet.json?.optJSONObject("data")?.let { data ->
                                when {
                                    data.has("click_count") -> _likeCount.value = data.optLong("click_count")
                                }
                            }
                        }
                        cmd in listOf("LIVE", "PREPARING", "CUT_OFF", "CUT_OFF_V2", "ROOM_CHANGE") -> {
                            scope.launch { _roomEvents.emit(cmd) }
                        }
                    }
                }
            }
        }
    }

    private fun startHeartbeat(realRoomId: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _connected.value) {
                if (heartbeatCounter.decrementAndGet() <= 0) {
                    webSocket?.send(buildHeartbeatPacket())
                    heartbeatCounter.set(30)
                }
                delay(1_000)
            }
        }
        webHeartbeatJob?.cancel()
        webHeartbeatJob = scope.launch {
            while (isActive && _connected.value) {
                runCatching {
                    api.sendLiveHeartbeat(realRoomId, roomId)
                }
                delay(60_000)
            }
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webHeartbeatJob?.cancel()
        webSocket?.close(1000, "leave room")
        webSocket = null
        _connected.value = false
        scope.cancel()
    }

    private data class LivePacket(
        val type: Int,
        val json: JSONObject? = null,
        val view: Int? = null,
        val body: ByteArray? = null,
    )

    private fun unpack(data: ByteArray): List<LivePacket> {
        if (data.size < 16) return emptyList()
        val packets = mutableListOf<LivePacket>()
        var offset = 0
        while (offset + 16 <= data.size) {
            val header = readHeader(data, offset)
            if (header.headerLength < 16 || header.totalLength < header.headerLength || header.totalLength > data.size - offset) break
            val end = offset + header.totalLength
            val chunk = data.copyOfRange(offset + header.headerLength, end)
            if (header.protocolVersion == PROTOCOL_BROTLI || header.protocolVersion == 2) {
                val decoded = if (header.protocolVersion == PROTOCOL_BROTLI) BrotliInputStream(ByteArrayInputStream(chunk)).use { it.readBytes() } else InflaterInputStream(ByteArrayInputStream(chunk)).use { it.readBytes() }
                packets += unpack(decoded)
                offset = end
                continue
            }
            if (header.packetType == PACKET_HEARTBEAT_RESPONSE && chunk.size >= 4) {
                packets += LivePacket(type = header.packetType, view = ByteBuffer.wrap(chunk).order(ByteOrder.BIG_ENDIAN).int)
                offset = end
                continue
            }
            val json = runCatching {
                JSONObject(chunk.toString(Charsets.UTF_8))
            }.getOrNull()
            packets += LivePacket(type = header.packetType, json = json, body = chunk)
            offset = end
        }
        return packets
    }

    private fun parseOnlineRankProto(body: ByteArray): List<BiliLiveRankUser> {
        fun readVarint(pos: IntArray): Long {
            var value = 0L; var shift = 0
            while (pos[0] < body.size && shift < 64) {
                val b = body[pos[0]++].toInt() and 0xFF
                value = value or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            return value
        }
        fun readBytes(pos: IntArray): ByteArray {
            val length = readVarint(pos).toInt().coerceAtLeast(0)
            val end = (pos[0] + length).coerceAtMost(body.size)
            val result = body.copyOfRange(pos[0], end); pos[0] = end; return result
        }
        fun parseItem(bytes: ByteArray, index: Int): BiliLiveRankUser? {
            val old = body
            val p = intArrayOf(0); var uid = 0L; var face = ""; var name = ""; var rank = index + 1; var guard = 0
            while (p[0] < bytes.size) {
                val key = run { var v = 0L; var s = 0; while (p[0] < bytes.size) { val b=bytes[p[0]++].toInt() and 255; v = v or ((b and 127).toLong() shl s); if (b and 128 == 0) break; s += 7 }; v }
                when (key.toInt() ushr 3) {
                    1 -> uid = readVarintFrom(bytes, p)
                    2 -> face = readBytesFrom(bytes, p).toString(Charsets.UTF_8)
                    4 -> name = readBytesFrom(bytes, p).toString(Charsets.UTF_8)
                    5 -> rank = readVarintFrom(bytes, p).toInt()
                    6 -> guard = readVarintFrom(bytes, p).toInt()
                    else -> if ((key.toInt() and 7) == 2) readBytesFrom(bytes, p) else readVarintFrom(bytes, p)
                }
            }
            return face.takeIf { it.isNotBlank() }?.let { BiliLiveRankUser(uid, it, name, rank, guard) }
        }
        val result = mutableListOf<BiliLiveRankUser>(); val p = intArrayOf(0)
        while (p[0] < body.size) {
            val key = readVarint(p).toInt(); val field = key ushr 3; val wire = key and 7
            if (wire == 2 && (field == 2 || field == 3)) parseItem(readBytes(p), result.size)?.let(result::add)
            else if (wire == 2) readBytes(p) else if (wire == 0) readVarint(p) else break
        }
        return result.sortedBy { it.rank }.distinctBy { it.uid to it.face }.take(3)
    }

    private fun readVarintFrom(bytes: ByteArray, pos: IntArray): Long {
        var value=0L; var shift=0; while (pos[0] < bytes.size && shift < 64) { val b=bytes[pos[0]++].toInt() and 255; value=value or ((b and 127).toLong() shl shift); if (b and 128 == 0) break; shift += 7 }; return value
    }
    private fun readBytesFrom(bytes: ByteArray, pos: IntArray): ByteArray { val n=readVarintFrom(bytes,pos).toInt().coerceAtLeast(0); val e=(pos[0]+n).coerceAtMost(bytes.size); val r=bytes.copyOfRange(pos[0],e); pos[0]=e; return r }

    private data class PacketHeader(
        val totalLength: Int,
        val headerLength: Int,
        val protocolVersion: Int,
        val packetType: Int,
    )

    private fun readHeader(data: ByteArray, offset: Int): PacketHeader {
        val buffer = ByteBuffer.wrap(data, offset, 16).order(ByteOrder.BIG_ENDIAN)
        return PacketHeader(
            totalLength = buffer.int,
            headerLength = buffer.short.toInt() and 0xFFFF,
            protocolVersion = buffer.short.toInt() and 0xFFFF,
            packetType = buffer.int,
        )
    }

    private fun buildVerifyPacket(realRoomId: Long, token: String): ByteString {
        val uid = credential?.dedeUserId?.toLongOrNull() ?: 0L
        val payload = JSONObject()
            .put("uid", uid)
            .put("roomid", realRoomId)
            .put("protover", 3)
            .put("platform", "web")
            .put("type", 2)
            .put("buvid", credential?.buvid3.orEmpty())
            .put("key", token)
            .toString()
        return ByteString.of(*pack(payload.toByteArray(), PROTOCOL_HEARTBEAT, PACKET_VERIFY))
    }

    private fun buildHeartbeatPacket(): ByteString =
        ByteString.of(*pack("[object Object]".toByteArray(), PROTOCOL_HEARTBEAT, PACKET_HEARTBEAT))

    private fun pack(body: ByteArray, protocolVersion: Int, packetType: Int): ByteArray {
        val packetBody = ByteBuffer.allocate(16 + body.size).order(ByteOrder.BIG_ENDIAN)
        packetBody.putShort(16)
        packetBody.putShort(protocolVersion.toShort())
        packetBody.putInt(packetType)
        packetBody.putInt(1)
        packetBody.put(body)
        val inner = packetBody.array().copyOf(packetBody.position())
        val result = ByteBuffer.allocate(4 + inner.size).order(ByteOrder.BIG_ENDIAN)
        result.putInt(inner.size + 4)
        result.put(inner)
        return result.array()
    }

    companion object {
        private const val PROTOCOL_HEARTBEAT = 1
        private const val PROTOCOL_BROTLI = 3
        private const val PACKET_HEARTBEAT = 2
        private const val PACKET_HEARTBEAT_RESPONSE = 3
        private const val PACKET_NOTICE = 5
        private const val PACKET_VERIFY = 7
        private const val PACKET_VERIFY_SUCCESS = 8
    }
}
