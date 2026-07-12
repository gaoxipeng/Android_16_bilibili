package com.example.bilibili.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BiliVideoItem
import kotlinx.coroutines.delay

private val HintCapsuleProgressBg = Color(0xFF00AEEC)
private val HintCapsuleProgressText = Color.White

fun feedRefreshHintMessage(
    previousItems: List<BiliVideoItem>,
    refreshedItems: List<BiliVideoItem>,
): String {
    val previousIds = previousItems.asSequence().map { it.bvid }.toSet()
    val newCount = refreshedItems.count { it.bvid !in previousIds }
    return if (newCount == 0) "暂无新视频" else "更新了 $newCount 条视频"
}

@Composable
fun FeedRefreshHintOverlay(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    zIndex: Float = 95f,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(tween(220)) { fullHeight -> -fullHeight / 2 },
        exit = slideOutVertically(tween(180)) { fullHeight -> -fullHeight / 2 },
        modifier = modifier
            .zIndex(zIndex)
            .padding(top = topInset + 10.dp),
    ) {
        message?.let {
            FeedRefreshCapsuleHint(
                message = it,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun FeedRefreshCapsuleHint(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long = 2200L,
) {
    LaunchedEffect(message, autoDismissMillis) {
        delay(autoDismissMillis)
        onDismiss()
    }

    BlueHintCapsule(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = HintCapsuleProgressText,
            )
        }
    }
}

@Composable
private fun BlueHintCapsule(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(HintCapsuleProgressBg, shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.wrapContentSize(),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}
