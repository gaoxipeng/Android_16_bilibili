package com.example.bilibili.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bilibili.ui.liquidglass.LiquidBottomTab
import com.example.bilibili.ui.liquidglass.LiquidBottomTabs
import com.example.bilibili.ui.liquidglass.LiquidGlassConfig
import com.example.bilibili.ui.liquidglass.LocalLiquidBottomTabBackdropRow
import com.example.bilibili.ui.liquidglass.SurfaceLiquidIconButton
import com.example.bilibili.ui.theme.TabAccentDark
import com.example.bilibili.ui.theme.TabAccentLight
import com.kyant.backdrop.Backdrop
import kotlin.math.abs

@Composable
internal fun BilibiliLiquidBottomBar(
    selectedTab: MainTab,
    onTabChange: (MainTab) -> Unit,
    expanded: Boolean,
    backdrop: Backdrop,
    onExpandRequest: () -> Unit,
    onCollapsedTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) TabAccentLight else TabAccentDark
    val unselectedColor = if (isLightTheme) Color(0xFF1F1F1F) else Color.White.copy(0.72f)
    val collapsedSize = 64.dp
    val barHeight = 64.dp
    val animationOverflow = 12.dp
    val tabs = MainTab.entries
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    var tabsMounted by remember { mutableStateOf(expanded) }
    if (expanded) tabsMounted = true

    val widthSpring = spring<Dp>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 980f)
    val contentSpring = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 1050f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomStart,
    ) {
        val fullBarWidth = maxWidth
        val transition = updateTransition(targetState = expanded, label = "bottom-bar-expansion")
        val isMorphing = transition.isRunning

        val barWidth by transition.animateDp(transitionSpec = { widthSpring }, label = "bar-width") { expandedState ->
            if (expandedState) fullBarWidth else collapsedSize
        }
        val expandedAlpha by transition.animateFloat(transitionSpec = { contentSpring }, label = "expanded-alpha") { expandedState ->
            if (expandedState) 1f else 0f
        }
        val collapsedAlpha = (1f - expandedAlpha).coerceIn(0f, 1f)
        val revealAlpha = expandedAlpha.coerceIn(0f, 1f)
        val bounceOvershoot = (revealAlpha - 1f).coerceAtLeast(0f)
        val morphT = 1f - abs(revealAlpha - 0.5f) * 2f
        val morphScaleX = 1f + morphT * 0.06f + bounceOvershoot * 0.1f
        val morphScaleY = 1f - morphT * 0.02f + bounceOvershoot * 0.06f
        val collapsedOnTop = revealAlpha < 0.5f

        BoxWithConstraints(Modifier.width(fullBarWidth).height(barHeight + animationOverflow)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(barWidth)
                    .height(barHeight)
                    .graphicsLayer {
                        clip = false
                        scaleX = morphScaleX
                        scaleY = morphScaleY
                        transformOrigin = TransformOrigin(0f, 1f)
                    },
            ) {
                if (tabsMounted && (revealAlpha > 0.01f || isMorphing)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (collapsedOnTop) 0f else 2f)
                            .graphicsLayer {
                                alpha = revealAlpha
                                scaleX = 0.92f + revealAlpha * 0.08f + bounceOvershoot * 0.03f
                                scaleY = 0.92f + revealAlpha * 0.08f + bounceOvershoot * 0.03f
                            },
                    ) {
                        LiquidBottomTabs(
                            selectedTabIndex = { selectedIndex },
                            onTabSelected = { index -> onTabChange(tabs[index]) },
                            backdrop = backdrop,
                            tabsCount = tabs.size,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                val isBackdropRow = LocalLiquidBottomTabBackdropRow.current
                                val isSelected = index == selectedIndex
                                val tabColor = when {
                                    isBackdropRow -> accentColor
                                    isSelected -> accentColor
                                    else -> unselectedColor
                                }
                                val tabAlpha = if (
                                    !isBackdropRow &&
                                    isSelected &&
                                    LiquidGlassConfig.enableLensEffects
                                ) {
                                    0f
                                } else {
                                    1f
                                }
                                LiquidBottomTab(onClick = { onTabChange(tab) }) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .graphicsLayer { alpha = tabAlpha },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BilibiliTabIcon(tab = tab, color = tabColor)
                                    }
                                    Text(
                                        text = tab.label,
                                        fontSize = 12.sp,
                                        color = tabColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.graphicsLayer { alpha = tabAlpha },
                                    )
                                }
                            }
                        }
                    }
                }

                if (collapsedAlpha > 0.01f || isMorphing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (collapsedOnTop) 2f else 0f)
                            .graphicsLayer {
                                alpha = collapsedAlpha
                                scaleX = 0.9f + collapsedAlpha * 0.1f
                                scaleY = 0.9f + collapsedAlpha * 0.1f
                            },
                    ) {
                        SurfaceLiquidIconButton(
                            onClick = onExpandRequest,
                            onDoubleClick = onCollapsedTap,
                            backdrop = backdrop,
                            isInteractive = collapsedOnTop && collapsedAlpha > 0.5f,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            BilibiliTabIcon(tab = selectedTab, color = accentColor, size = 24.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BilibiliTabIcon(
    tab: MainTab,
    color: Color,
    size: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val icon: ImageVector = when (tab) {
        MainTab.Home -> Icons.Filled.Home
        MainTab.Following -> Icons.Filled.Subscriptions
        MainTab.Hot -> Icons.Filled.Whatshot
        MainTab.Live -> Icons.Filled.LiveTv
        MainTab.Mine -> Icons.Filled.Person
    }
    Icon(
        imageVector = icon,
        contentDescription = tab.label,
        modifier = Modifier.size(size),
        tint = color,
    )
}
