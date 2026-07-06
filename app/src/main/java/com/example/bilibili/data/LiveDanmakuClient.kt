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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.brotli.dec.BrotliInputStream
import org.json.JSONObject
import java.io.ByteArrayInputStream
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
        val danmuInfo = api.getLiveDanmuInfo(playInfo.realRoomId, credential)
            ?: error("无法获取弹幕服务器配置")
        realRoomId = playInfo.realRoomId
        val token = danmuInfo.token
        var lastError: Throwable? = null
        for (host in danmuInfo.hosts.asReversed()) {
            val url = "wss://${host.host}:${host.wssPort}/sub"
            try {
                connectToHost(url, realRoomId, token)
                return
            } catch (error: Throwable) {
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
                        webSocket.send(buildVerifyPacket(realRoomId, token))
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        handlePacket(bytes.toByteArray()) {
                            if (!_connected.value) {
                                _connected.value = true
                                startHeartbeat(realRoomId)
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }
                        }
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

    private fun handlePacket(raw: ByteArray, onVerified: () -> Unit = {}) {
        if (raw.size < 16) return
        val packets = unpack(raw)
        for (packet in packets) {
            when (packet.type) {
                PACKET_VERIFY_SUCCESS -> {
                    val code = packet.json?.optInt("code", -1) ?: -1
                    if (code == 0) onVerified()
                }
                PACKET_HEARTBEAT_RESPONSE -> {
                    heartbeatCounter.set(30)
                    packet.view?.let { _popularity.value = it.toLong() }
                }
                PACKET_NOTICE -> {
                    val cmd = packet.json?.optString("cmd").orEmpty()
                    when {
                        cmd.startsWith("DANMU_MSG") -> {
                            packet.json?.optJSONArray("info")?.let { info ->
                                BilibiliJsonParser.parseLiveDanmakuMessage(info)?.let { item ->
                                    scope.launch { _danmaku.emit(item) }
                                }
                            }
                        }
                        cmd == "ONLINE_RANK_V2" -> {
                            packet.json?.let { payload ->
                                val users = BilibiliJsonParser.parseLiveOnlineRankList(payload)
                                if (users.isNotEmpty()) {
                                    _onlineRankTop3.value = users
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
    )

    private fun unpack(data: ByteArray): List<LivePacket> {
        if (data.size < 16) return emptyList()
        val topHeader = readHeader(data, 0)
        val realData = if (topHeader.protocolVersion == PROTOCOL_BROTLI) {
            val compressed = data.copyOfRange(16, data.size)
            BrotliInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        } else {
            data
        }
        if (topHeader.protocolVersion == PROTOCOL_HEARTBEAT &&
            topHeader.packetType == PACKET_HEARTBEAT_RESPONSE &&
            realData.size >= 20
        ) {
            val view = ByteBuffer.wrap(realData, 16, 4).order(ByteOrder.BIG_ENDIAN).int
            return listOf(LivePacket(type = PACKET_HEARTBEAT_RESPONSE, view = view))
        }
        val packets = mutableListOf<LivePacket>()
        var offset = 0
        while (offset + 16 <= realData.size) {
            val header = readHeader(realData, offset)
            if (header.totalLength <= 16) break
            val end = (offset + header.totalLength).coerceAtMost(realData.size)
            val chunk = realData.copyOfRange(offset + 16, end)
            val json = runCatching {
                JSONObject(chunk.toString(Charsets.UTF_8))
            }.getOrNull()
            packets += LivePacket(type = header.packetType, json = json)
            offset += header.totalLength
        }
        return packets
    }

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
