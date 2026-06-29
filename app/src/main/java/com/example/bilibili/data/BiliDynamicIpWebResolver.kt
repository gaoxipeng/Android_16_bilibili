package com.example.bilibili.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object BiliDynamicIpWebResolver {
    private val mutex = Mutex()
    private const val LOAD_TIMEOUT_MS = 15_000L
    private const val EXTRACT_RETRY_COUNT = 6
    private const val EXTRACT_RETRY_DELAY_MS = 500L

    private val EXTRACT_JS = """
        (function() {
          function pickLocation(value) {
            if (!value || typeof value !== 'string') return '';
            var text = value.trim();
            if (!text || text === '未知') return '';
            text = text.replace(/^IP\s*属地[：:]\s*/i, '').trim();
            if (text.indexOf('IP属地') >= 0) {
              text = text.split('IP属地').pop().replace(/^[：:\s]+/, '').trim();
            }
            if (!text || text === '未知') return '';
            if (/\d/.test(text) || /浏览|播放|弹幕|转发|评论/.test(text)) return '';
            if (text.length < 2 || text.length > 12) return '';
            if (!/[\u4e00-\u9fff]/.test(text)) return '';
            return text;
          }
          function scanObject(obj, depth) {
            if (!obj || depth > 10) return '';
            if (typeof obj === 'object') {
              var direct = pickLocation(obj.pub_location_text)
                || pickLocation(obj.ptime_location_text)
                || pickLocation(obj.ptimeLocationText);
              if (direct) return direct;
              if (Array.isArray(obj)) {
                for (var i = 0; i < obj.length; i++) {
                  var nested = scanObject(obj[i], depth + 1);
                  if (nested) return nested;
                }
                return '';
              }
              for (var key in obj) {
                var nested = scanObject(obj[key], depth + 1);
                if (nested) return nested;
              }
            }
            return '';
          }
          var candidates = [
            window.__INITIAL_STATE__,
            window.__PINIA__,
            window.__NUXT__,
          ];
          for (var i = 0; i < candidates.length; i++) {
            var fromState = scanObject(candidates[i], 0);
            if (fromState) return fromState;
          }
          var bodyText = document.body ? document.body.innerText : '';
          var match = bodyText.match(/IP\s*属地\s*[：:]\s*([\u4e00-\u9fa5]{2,12})/);
          return match ? match[1].trim() : '';
        })()
    """.trimIndent()

    suspend fun resolve(
        context: Context,
        dynamicId: String,
        credential: BilibiliCredential? = null,
    ): String? {
        if (dynamicId.isBlank()) return null
        return mutex.withLock {
            withContext(Dispatchers.Main) {
                loadIpFromWeb(context.applicationContext, dynamicId, credential)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadIpFromWeb(
        context: Context,
        dynamicId: String,
        credential: BilibiliCredential?,
    ): String? = suspendCancellableCoroutine { continuation ->
        syncCredentialCookies(credential)
        val webView = WebView(context)
        val handler = Handler(Looper.getMainLooper())
        var finished = false

        fun finish(value: String?) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
            if (continuation.isActive) {
                continuation.resume(BilibiliJsonParser.normalizeIpLocation(value))
            }
        }

        fun extract(attempt: Int) {
            webView.evaluateJavascript(EXTRACT_JS) { raw ->
                val value = decodeJsString(raw)
                if (!value.isNullOrBlank() || attempt >= EXTRACT_RETRY_COUNT) {
                    finish(value)
                } else {
                    handler.postDelayed({ extract(attempt + 1) }, EXTRACT_RETRY_DELAY_MS)
                }
            }
        }

        handler.postDelayed({ finish(null) }, LOAD_TIMEOUT_MS)
        continuation.invokeOnCancellation {
            handler.removeCallbacksAndMessages(null)
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        CookieManager.getInstance().flush()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                extract(0)
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?,
            ) {
                finish(null)
            }
        }
        webView.loadUrl("https://www.bilibili.com/opus/$dynamicId")
    }

    private fun syncCredentialCookies(credential: BilibiliCredential?) {
        if (credential == null) return
        val cookieManager = CookieManager.getInstance()
        val domains = listOf(
            "https://www.bilibili.com",
            "https://bilibili.com",
            "https://t.bilibili.com",
            "https://m.bilibili.com",
            "https://api.bilibili.com",
        )
        val cookiePairs = credential.toCookieHeader().split("; ")
        for (domain in domains) {
            for (pair in cookiePairs) {
                cookieManager.setCookie(domain, pair)
            }
        }
        cookieManager.flush()
    }

    private fun decodeJsString(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return raw.trim().removeSurrounding("\"").replace("\\u0026", "&")
    }
}
