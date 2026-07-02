package com.example.bilibili.ui.components

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bilibili.util.BiliArticleUrl

data class BiliWebReaderState(
    val url: String,
    val title: String = "",
)

private const val BILI_MOBILE_WEB_UA =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Mobile/15E148 BiliApp/72600100 os/ios model/iPhone mobi_ios"

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
            var height = window.innerHeight + 'px';
            document.documentElement.style.height = height;
            document.body.style.minHeight = height;
            ${if (scrollToTop) "window.scrollTo(0, 0);" else ""}
        })();
        """.trimIndent(),
        null,
    )
}

private fun webRequestHeaders(url: String): Map<String, String> {
    val referer = if (url.contains("m.bilibili.com", ignoreCase = true)) {
        "https://m.bilibili.com/"
    } else {
        "https://www.bilibili.com/"
    }
    return mapOf(
        "Referer" to referer,
        "Origin" to referer.trimEnd('/'),
        "User-Agent" to BILI_MOBILE_WEB_UA,
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BiliWebReaderOverlay(
    state: BiliWebReaderState?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    if (state == null) return

    val context = LocalContext.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val viewportCoordinator = remember(state.url) { MobileWebViewportCoordinator() }
    var loading by remember(state.url) { mutableStateOf(true) }

    BackHandler(onBack = onBack)

    Surface(
        modifier = modifier
            .fillMaxSize()
            .consumeTouchEvents(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (showHeader) {
                RowHeader(
                    title = state.title,
                    topInset = topInset,
                    onBack = onBack,
                )
                HorizontalDivider()
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (!showHeader) {
                            Modifier.padding(top = topInset)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val webView = remember(state.url) {
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadsImagesAutomatically = true
                            useWideViewPort = true
                            loadWithOverviewMode = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString = BILI_MOBILE_WEB_UA
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val target = request?.url?.toString().orEmpty()
                                if (target.startsWith("bilibili://", ignoreCase = true)) {
                                    return true
                                }
                                BiliArticleUrl.resolveMobileOpusUrl(target)?.let { opusUrl ->
                                    view?.loadUrl(opusUrl, webRequestHeaders(opusUrl))
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                if (!viewportCoordinator.shouldFitOnPageFinished(url)) return
                                view?.fitMobileWebViewport(scrollToTop = true)
                            }
                        }
                        loadUrl(state.url, webRequestHeaders(state.url))
                    }
                }

                DisposableEffect(state.url) {
                    onDispose {
                        webView.stopLoading()
                        webView.destroy()
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { webView },
                )

                if (loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun RowHeader(
    title: String,
    topInset: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topInset)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
            )
        }
        Text(
            text = title.ifBlank { "网页" },
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
