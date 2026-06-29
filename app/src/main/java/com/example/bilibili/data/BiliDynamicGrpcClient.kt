package com.example.bilibili.data

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

object BiliDynamicGrpcClient {
    private const val ANDROID_UA =
        "Dalvik/2.1.0 (Linux; U; Android 13; Mi 11 Build/TKQ1.221114.001) 8.51.0 os/android model/Mi 11 mobi_app/android build/8510300 channel/master innerVer/8510310 osVer/13 network/2"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    fun fetchAuthorIpLocation(dynamicId: String, credential: BilibiliCredential): String? {
        val accessKey = credential.accessKey.takeIf { it.isNotBlank() } ?: return null
        val buvid = credential.buvid3.takeIf { it.isNotBlank() } ?: "XY0000000000000000000000000000infoc"
        val payload = BiliProtobufCodec.buildDynDetailReq(dynamicId)
        val grpcBody = ByteArray(5 + payload.size)
        grpcBody[0] = 0
        grpcBody[1] = ((payload.size shr 24) and 0xFF).toByte()
        grpcBody[2] = ((payload.size shr 16) and 0xFF).toByte()
        grpcBody[3] = ((payload.size shr 8) and 0xFF).toByte()
        grpcBody[4] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, grpcBody, 5, payload.size)

        val metadata = BiliProtobufCodec.buildMetadata(accessKey, buvid)
        val device = BiliProtobufCodec.buildDevice(buvid)
        val fawkes = BiliProtobufCodec.buildFawkesReq()
        val network = BiliProtobufCodec.buildNetwork()
        val locale = BiliProtobufCodec.buildLocale()
        val traceId = buildTraceId()

        val request = Request.Builder()
            .url("https://grpc.biliapi.net/bilibili.app.dynamic.v2.Dynamic/DynDetail")
            .post(grpcBody.toRequestBody("application/grpc".toMediaType()))
            .header("te", "trailers")
            .header("grpc-accept-encoding", "identity")
            .header("user-agent", ANDROID_UA)
            .header("authorization", "identify_v1 $accessKey")
            .header("buvid", buvid)
            .header("x-bili-mid", credential.dedeUserId)
            .header("x-bili-trace-id", traceId)
            .header("x-bili-metadata-bin", metadata.toGrpcHeader())
            .header("x-bili-device-bin", device.toGrpcHeader())
            .header("x-bili-fawkes-req-bin", fawkes.toGrpcHeader())
            .header("x-bili-network-bin", network.toGrpcHeader())
            .header("x-bili-locale-bin", locale.toGrpcHeader())
            .header("x-bili-restriction-bin", ByteArray(0).toGrpcHeader())
            .header("x-bili-exps-bin", ByteArray(0).toGrpcHeader())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.bytes() ?: return null
            if (body.size <= 5) return null
            val message = body.copyOfRange(5, body.size)
            return BiliProtobufCodec.findField14Strings(message)
                .asSequence()
                .mapNotNull(BilibiliJsonParser::normalizeIpLocation)
                .firstOrNull()
        }
    }

    private fun ByteArray.toGrpcHeader(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun buildTraceId(): String {
        val left = UUID.randomUUID().toString().replace("-", "").take(26)
        val right = UUID.randomUUID().toString().replace("-", "").take(16)
        return "$left:$right:0:0"
    }
}
