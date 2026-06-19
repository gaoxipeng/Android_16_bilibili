package com.example.bilibili.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bilibili.data.BilibiliWebSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSheet(
    session: BilibiliWebSession,
    visible: Boolean,
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column {
            Text(
                text = "在下方页面完成哔哩哔哩登录",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AndroidView(
                factory = {
                    (session.webView.parent as? ViewGroup)?.removeView(session.webView)
                    session.openLogin()
                    session.webView
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
            )
            TextButton(
                onClick = {
                    if (session.hasLoginCookie()) {
                        onLoginSuccess()
                    } else {
                        onDismiss()
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(if (session.hasLoginCookie()) "完成登录" else "关闭")
            }
        }
    }
}
