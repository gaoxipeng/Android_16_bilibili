package com.example.bilibili.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.ui.liquidglass.LiquidMenuBorderWidth
import com.example.bilibili.ui.liquidglass.liquidMenuBorderColor
import com.example.bilibili.ui.screens.HomeSearchBarHeight
import com.example.bilibili.ui.screens.HomeSearchBarTopGap
import com.example.bilibili.ui.theme.isAppLightTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow as GlassShadow
import kotlinx.coroutines.delay

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
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    zIndex: Float = 95f,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { fullHeight -> -fullHeight / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { fullHeight -> -fullHeight / 2 },
        modifier = modifier
            .zIndex(zIndex)
            .fillMaxWidth()
            .padding(top = topInset + HomeSearchBarTopGap),
    ) {
        message?.let {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                FeedRefreshCapsuleHint(
                    message = it,
                    onDismiss = onDismiss,
                    backdrop = backdrop,
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(HomeSearchBarHeight),
                )
            }
        }
    }
}

@Composable
private fun FeedRefreshCapsuleHint(
    message: String,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long = 2200L,
) {
    LaunchedEffect(message, autoDismissMillis) {
        delay(autoDismissMillis)
        onDismiss()
    }

    BlueHintCapsule(modifier = modifier, backdrop = backdrop) {
        Box(
            modifier = Modifier
                .height(HomeSearchBarHeight)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                        blurRadius = 4f,
                    ),
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun BlueHintCapsule(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val isLightTheme = isAppLightTheme()
    val surfaceTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.56f)
    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    lens(16.dp.toPx(), 28.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = { GlassShadow.Default },
                onDrawSurface = {
                    drawRect(surfaceTint)
                },
            )
            .border(
                LiquidMenuBorderWidth,
                liquidMenuBorderColor(isLightTheme),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.wrapContentSize(),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}
