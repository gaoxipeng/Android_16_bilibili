package com.example.bilibili.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

class BilibiliWebSession(context: Context) {
    val webView: WebView = WebView(context)
    private val viewportCoordinator = MobileWebViewportCoordinator()

    init {
        configureWebView()
        webView.loadUrl(BilibiliEndpoints.HOME)
    }

    fun openLogin(forceReload: Boolean = false) {
        val activeUrl = webView.url.orEmpty()
        if (!forceReload && activeUrl.contains("passport.bilibili.com")) return
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

    fun applyCredential(credential: BilibiliCredential) {
        val cookieManager = CookieManager.getInstance()
        val domains = listOf(
            "https://www.bilibili.com",
            "https://bilibili.com",
            "https://t.bilibili.com",
            "https://m.bilibili.com",
            "https://api.bilibili.com",
            "https://passport.bilibili.com",
        )
        val cookiePairs = credential.toCookieHeader().split("; ")
        for (domain in domains) {
            for (pair in cookiePairs) {
                cookieManager.setCookie(domain, pair)
            }
        }
        cookieManager.flush()
    }

    suspend fun clearAllCookies() {
        suspendCancellableCoroutine { continuation ->
            CookieManager.getInstance().removeAllCookies { success ->
                CookieManager.getInstance().flush()
                continuation.resume(success)
            }
        }
    }

    suspend fun prepareAddAccount() {
        withContext(Dispatchers.Main) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
        }
        clearAllCookies()
    }

    suspend fun activateAccount(account: StoredBilibiliAccount) {
        withContext(Dispatchers.Main) {
            webView.stopLoading()
        }
        clearAllCookies()
        applyCredential(account.credential)
        withContext(Dispatchers.Main) {
            webView.loadUrl(BilibiliEndpoints.HOME)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
            userAgentString = BilibiliEndpoints.USER_AGENT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!viewportCoordinator.shouldFitOnPageFinished(url)) return
                view?.fitMobileWebViewport(scrollToTop = true)
            }
        }
    }
}

private class MobileWebViewportCoordinator {
    private var lastFittedUrl: String? = null

    fun shouldFitOnPageFinished(url: String?): Boolean {
        val normalized = url.orEmpty()
        if (normalized == lastFittedUrl) return false
        lastFittedUrl = normalized
        return true
    }
}

private fun WebView.fitMobileWebViewport(scrollToTop: Boolean = true) {
    evaluateJavascript(
        """
        (function() {
            var meta = document.querySelector('meta[name="viewport"]');
            if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                document.head.appendChild(meta);
            }
            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover';
            var width = window.innerWidth + 'px';
            var height = window.innerHeight + 'px';
            document.documentElement.style.width = width;
            document.documentElement.style.height = height;
            document.body.style.width = width;
            document.body.style.minHeight = height;
            document.body.style.margin = '0';
            ${if (scrollToTop) "window.scrollTo(0, 0);" else ""}
        })();
        """.trimIndent(),
        null,
    )
}
