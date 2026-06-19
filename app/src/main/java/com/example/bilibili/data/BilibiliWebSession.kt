package com.example.bilibili.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BilibiliWebSession(context: Context) {
    val webView: WebView = WebView(context)

    init {
        configureWebView()
        webView.loadUrl(BilibiliEndpoints.HOME)
    }

    fun openLogin() {
        webView.loadUrl(BilibiliEndpoints.PASSPORT_LOGIN)
    }

    fun hasLoginCookie(): Boolean {
        CookieManager.getInstance().flush()
        val cookie = CookieManager.getInstance().getCookie(BilibiliEndpoints.HOME)
            ?: CookieManager.getInstance().getCookie("https://www.bilibili.com/")
            ?: ""
        return cookie.contains("SESSDATA=")
    }

    suspend fun readCredentialFromCookies(): BilibiliCredential? = withContext(Dispatchers.IO) {
        CookieManager.getInstance().flush()
        val cookie = CookieManager.getInstance().getCookie(BilibiliEndpoints.HOME)
            ?: CookieManager.getInstance().getCookie("https://www.bilibili.com/")
            ?: return@withContext null
        val map = cookie.split(';')
            .mapNotNull { part ->
                val trimmed = part.trim()
                val index = trimmed.indexOf('=')
                if (index <= 0) return@mapNotNull null
                trimmed.substring(0, index) to trimmed.substring(index + 1)
            }
            .toMap()
        val sessdata = map["SESSDATA"] ?: return@withContext null
        val dedeUserId = map["DedeUserID"] ?: return@withContext null
        BilibiliCredential(
            dedeUserId = dedeUserId,
            sessdata = sessdata,
            biliJct = map["bili_jct"].orEmpty(),
            buvid3 = map["buvid3"].orEmpty(),
            buvid4 = map["buvid4"].orEmpty(),
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = BilibiliEndpoints.USER_AGENT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
        }
    }
}
