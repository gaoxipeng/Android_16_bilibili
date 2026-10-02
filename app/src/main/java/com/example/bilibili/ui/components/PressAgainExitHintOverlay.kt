package com.example.bilibili.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.Text
import com.example.bilibili.ui.liquidglass.LiquidMenuBorderWidth
import com.example.bilibili.ui.liquidglass.liquidMenuBorderColor
import com.example.bilibili.ui.theme.isAppLightTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay

private const val ExitHintAutoDismissMillis = 2000L
internal val PressAgainExitConfirmWindowMillis = ExitHintAutoDismissMillis
private const val ExitHintLabel = "再按一次退出程序"

@Composable
fun PressAgainExitHintOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    zIndex: Float = 95f,
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(ExitHintAutoDismissMillis)
            onDismiss()
        }
    }

    val isLightTheme = isAppLightTheme()
    val pillShape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(zIndex),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(120)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.94f),
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { clip = false }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = {
                            vibrancy()
                            lens(16.dp.toPx(), 28.dp.toPx())
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow.Default },
                        onDrawSurface = {
                            drawRect(Color(0xFFE00000).copy(alpha = 0.5f))
                        },
                    )
                    .border(
                        LiquidMenuBorderWidth,
                        liquidMenuBorderColor(isLightTheme),
                        pillShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ExitHintLabel,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium.merge(
                        TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.58f),
                                offset = Offset(0f, 2f),
                                blurRadius = 4f,
                            ),
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    ),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White,
                )
            }
        }
    }
}
