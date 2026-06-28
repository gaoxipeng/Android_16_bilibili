package com.example.bilibili.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BilibiliWebSession

@Composable
fun LoginSheet(
    session: BilibiliWebSession,
    visible: Boolean,
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    if (!visible) return

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(Unit) {
        session.openLogin()
    }

    BackHandler {
        if (session.webView.canGoBack()) {
            session.webView.goBack()
        } else {
            onDismiss()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(200f),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topInset)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("哔哩哔哩登录", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "在下方页面完成登录。登录成功后点击「完成登录」返回应用。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                if (session.hasLoginCookie()) {
                                    onLoginSuccess()
                                } else {
                                    onDismiss()
                                }
                            },
                        ) {
                            Text(if (session.hasLoginCookie()) "完成登录" else "关闭")
                        }
                        TextButton(onClick = { session.openLogin(forceReload = true) }) {
                            Text("重新打开")
                        }
                    }
                }
            }
            LoginSessionWebView(
                session = session,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun LoginSessionWebView(
    session: BilibiliWebSession,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                session.webView.detachFromParent()
                addView(
                    session.webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        update = { host ->
            if (session.webView.parent !== host) {
                session.webView.detachFromParent()
                host.removeAllViews()
                host.addView(
                    session.webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        onRelease = { host ->
            if (session.webView.parent === host) {
                host.removeView(session.webView)
            }
        },
    )
}

private fun WebView.detachFromParent(): WebView {
    (parent as? ViewGroup)?.removeView(this)
    return this
}
