package com.example.bilibili.data

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object BiliAccessKeyExchange {
    private const val PINK_APP_KEY = "783bbb7264451d82"
    private const val PINK_APP_SECRET = "2653583c8873dea268ab9386918b1d65"
    private const val ANDROID_UA =
        "Mozilla/5.0 BiliDroid/8.51.0 (bbcallen@gmail.com) os/android model/Mi 11 mobi_app/android build/8510300 channel/master innerVer/8510310 osVer/13 network/2"

    private val client = OkHttpClient()

    suspend fun exchange(credential: BilibiliCredential): BilibiliCredential? = runCatching {
        val authCode = requestAuthCode(credential) ?: return@runCatching null
        confirmAuthCode(authCode, credential) ?: return@runCatching null
        val tokenData = pollAuthCode(authCode, credential) ?: return@runCatching null
        val accessKey = tokenData.optString("access_token").takeIf { it.isNotBlank() }
            ?: tokenData.optJSONObject("token_info")?.optString("access_token")?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val refreshToken = tokenData.optString("refresh_token").takeIf { it.isNotBlank() }
            ?: tokenData.optJSONObject("token_info")?.optString("refresh_token").orEmpty()
        credential.copy(accessKey = accessKey, refreshToken = refreshToken)
    }.getOrNull()

    private fun requestAuthCode(credential: BilibiliCredential): String? {
        val buvid = resolveBuvid(credential)
        val params = BiliAppSign.signQuery(
            baseParams = mapOf(
                "appkey" to PINK_APP_KEY,
                "build" to "8510300",
                "c_locale" to "zh-Hans_CN",
                "channel" to "master",
                "local_id" to buvid,
                "mobi_app" to "android",
                "platform" to "android",
                "s_locale" to "zh-Hans_CN",
            ),
            appSecret = PINK_APP_SECRET,
        )
        val body = FormBody.Builder().apply {
            params.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code")
            .post(body)
            .header("User-Agent", ANDROID_UA)
            .header("buvid", buvid)
            .header("Cookie", credential.toCookieHeader())
            .build()
        return executeJson(request)?.optJSONObject("data")?.optString("auth_code")?.takeIf { it.isNotBlank() }
    }

    private fun confirmAuthCode(authCode: String, credential: BilibiliCredential): JSONObject? {
        if (credential.biliJct.isBlank()) return null
        val body = FormBody.Builder()
            .add("auth_code", authCode)
            .add("csrf", credential.biliJct)
            .add("scanning_type", "1")
            .build()
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-tv-login/h5/qrcode/confirm")
            .post(body)
            .header("User-Agent", ANDROID_UA)
            .header("buvid", resolveBuvid(credential))
            .header("Cookie", credential.toCookieHeader())
            .build()
        return executeJson(request)
    }

    private fun pollAuthCode(authCode: String, credential: BilibiliCredential): JSONObject? {
        val params = BiliAppSign.signQuery(
            baseParams = mapOf(
                "appkey" to PINK_APP_KEY,
                "auth_code" to authCode,
                "build" to "8510300",
                "c_locale" to "zh-Hans_CN",
                "channel" to "master",
                "local_id" to "0",
                "mobi_app" to "android",
                "platform" to "android",
                "s_locale" to "zh-Hans_CN",
            ),
            appSecret = PINK_APP_SECRET,
        )
        val body = FormBody.Builder().apply {
            params.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-tv-login/qrcode/poll")
            .post(body)
            .header("User-Agent", ANDROID_UA)
            .header("buvid", resolveBuvid(credential))
            .header("Cookie", credential.toCookieHeader())
            .build()
        return executeJson(request)?.optJSONObject("data")
    }

    private fun executeJson(request: Request): JSONObject? {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            if (json.optInt("code") != 0) return null
            return json
        }
    }

    private fun resolveBuvid(credential: BilibiliCredential?): String =
        credential?.buvid3?.takeIf { it.isNotBlank() } ?: "XY0000000000000000000000000000infoc"
}
