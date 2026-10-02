package com.example.bilibili.ui.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.example.bilibili.ui.theme.isAppLightTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.example.bilibili.ui.theme.TabAccentDark
import com.example.bilibili.ui.theme.TabAccentLight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    gestureController: LiquidBottomTabsGestureController = rememberLiquidBottomTabsGestureController(),
    feedTabIndex: Int = 0,
    onTabLongPress: (index: Int) -> Unit = {},
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = isAppLightTheme()
    val accentColor = if (isLightTheme) TabAccentLight else TabAccentDark
    val capsuleSurfaceColor = liquidLargeCapsuleSurfaceColor(isLightTheme)

    BoxWithConstraints(
        modifier.graphicsLayer { clip = false },
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val selectedIndex = selectedTabIndex().fastCoerceIn(0, tabsCount - 1)
        val currentSelectedIndex = rememberUpdatedState(selectedIndex)
        val currentOnTabSelected = rememberUpdatedState(onTabSelected)
        val currentOnTabLongPress = rememberUpdatedState(onTabLongPress)
        val horizontalInsetPx = with(density) { 4.dp.toPx() }
        var isUserGesturing by remember { mutableStateOf(false) }
        var lastGesturePosition by remember { mutableStateOf(Offset.Zero) }
        val barWidthPx = constraints.maxWidth.toFloat()

        fun nearestTabIndex(position: Offset): Int {
            var bestIndex = 0
            var bestDistance = Float.MAX_VALUE
            for (i in 0 until tabsCount) {
                val centerX = horizontalInsetPx + tabWidth * (i + 0.5f)
                val distance = abs(position.x - centerX)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = i
                }
            }
            return if (isLtr) bestIndex else tabsCount - 1 - bestIndex
        }

        fun valueAt(position: Offset): Float {
            val visualValue = ((position.x - horizontalInsetPx) / tabWidth - 0.5f)
                .fastCoerceIn(0f, (tabsCount - 1).toFloat())
            return if (isLtr) visualValue else (tabsCount - 1).toFloat() - visualValue
        }

        fun commitTabSelection(index: Int) {
            currentOnTabSelected.value(index)
        }

        val dampedDragAnimation = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _, _ -> },
            )
        }

        LaunchedEffect(dampedDragAnimation) {
            dampedDragAnimation.snapToValue(selectedIndex.toFloat())
        }

        LaunchedEffect(selectedIndex, isUserGesturing) {
            if (isUserGesturing) return@LaunchedEffect
            dampedDragAnimation.updateValue(selectedIndex.toFloat())
        }

        DisposableEffect(gestureController, dampedDragAnimation) {
            gestureController.impl = object : LiquidBottomTabsGestureController.GestureImpl {
                override fun begin(position: Offset) {
                    isUserGesturing = true
                    lastGesturePosition = position
                    dampedDragAnimation.press()
                    dampedDragAnimation.updateValue(valueAt(position))
                }

                override fun drag(position: Offset, dragAmount: Offset) {
                    lastGesturePosition = position
                    val newValue = if (dragAmount != Offset.Zero) {
                        (dampedDragAnimation.targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    } else {
                        valueAt(position)
                    }
                    dampedDragAnimation.updateValue(newValue)
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }

                override fun end() {
                    val clampedIndex = nearestTabIndex(lastGesturePosition)
                        .fastCoerceIn(0, tabsCount - 1)
                    dampedDragAnimation.updateValue(clampedIndex.toFloat())
                    commitTabSelection(clampedIndex)
                    dampedDragAnimation.release()
                    isUserGesturing = false
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                }

                override fun cancel() {
                    isUserGesturing = false
                    dampedDragAnimation.animateToValue(currentSelectedIndex.value.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                }
            }
            onDispose {
                gestureController.impl = null
            }
        }

        var indicatorValue by remember(dampedDragAnimation) {
            mutableFloatStateOf(dampedDragAnimation.value)
        }
        var indicatorPressProgress by remember(dampedDragAnimation) {
            mutableFloatStateOf(dampedDragAnimation.pressProgress)
        }
        var indicatorScaleX by remember(dampedDragAnimation) {
            mutableFloatStateOf(dampedDragAnimation.scaleX)
        }
        var indicatorScaleY by remember(dampedDragAnimation) {
            mutableFloatStateOf(dampedDragAnimation.scaleY)
        }

        LaunchedEffect(dampedDragAnimation) {
            while (true) {
                withFrameMillis {
                    indicatorValue = dampedDragAnimation.value
                    indicatorPressProgress = dampedDragAnimation.pressProgress
                    indicatorScaleX = dampedDragAnimation.scaleX
                    indicatorScaleY = dampedDragAnimation.scaleY
                }
            }
        }

        val interactiveHighlight = remember(animationScope, tabWidth) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (indicatorValue + 0.5f) * tabWidth + panelOffset
                        else size.width - (indicatorValue + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        val barShape = RoundedCornerShape(percent = 50)
        Box(
            Modifier
                .graphicsLayer {
                    clip = false
                    translationX = panelOffset
                    val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, indicatorPressProgress)
                    scaleX = scale
                    scaleY = scale
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { barShape },
                    effects = { liquidLargeCapsuleGlassEffects() },
                    highlight = { Highlight.Default },
                    shadow = { Shadow.Default },
                    onDrawSurface = { drawRect(capsuleSurfaceColor) },
                )
                .then(liquidLargeCapsuleEdgeBorder(barShape, isLightTheme))
                .height(64.dp)
                .fillMaxWidth(),
        )

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    clip = false
                    transformOrigin = TransformOrigin.Center
                    val visualIndex = if (isLtr) indicatorValue else tabsCount - 1f - indicatorValue
                    translationX = visualIndex * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(percent = 50) },
                    effects = {
                        val progress = indicatorPressProgress
                        blur(8f.dp.toPx() * (1f - progress), TileMode.Decal)
                        lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = 0.2f + 0.6f * indicatorPressProgress)
                    },
                    shadow = null,
                    layerBlock = {
                        scaleX = indicatorScaleX
                        scaleY = indicatorScaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        // Match Weibo's indicator tint so the small glass capsule
                        // remains visible against bright and white page backgrounds.
                        drawRect(
                            (if (isLightTheme) Color(0xFFB9BDC3) else Color(0xFF92979E)).copy(
                                alpha = if (isLightTheme) lerp(0.32f, 0.015f, indicatorPressProgress)
                                else lerp(0.22f, 0.01f, indicatorPressProgress),
                            ),
                        )
                    },
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount)
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabPressProgress provides indicatorPressProgress,
            LocalLiquidBottomTabCoverage provides { index: Int ->
                val iconHalfWidth = with(density) { 11.dp.toPx() }
                val capsuleHalfWidth = tabWidth * indicatorScaleX / 2f
                val centerDistance = abs(index - indicatorValue) * tabWidth
                ((capsuleHalfWidth + iconHalfWidth - centerDistance) / (2f * iconHalfWidth))
                    .coerceIn(0f, 1f)
            },
        ) {
            Row(
                Modifier
                    .graphicsLayer {
                        clip = false
                        translationX = panelOffset
                    }
                    .then(interactiveHighlight.modifier)
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(tabsCount, feedTabIndex, barWidthPx, isLtr) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        dampedDragAnimation.press()
                        var latestPosition = down.position
                        var releasedBeforeLongPress = false
                        var movedBeforeLongPress = false
                        val completedBeforeTimeout = withTimeoutOrNull(
                            viewConfiguration.longPressTimeoutMillis,
                        ) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                    ?: return@withTimeoutOrNull true
                                latestPosition = change.position
                                if (!change.pressed) {
                                    releasedBeforeLongPress = true
                                    change.consume()
                                    return@withTimeoutOrNull true
                                }
                                if ((change.position - down.position).getDistance() >
                                    viewConfiguration.touchSlop
                                ) {
                                    movedBeforeLongPress = true
                                    return@withTimeoutOrNull true
                                }
                                change.consume()
                            }
                            @Suppress("UNREACHABLE_CODE")
                            true
                        } != null

                        if (completedBeforeTimeout && releasedBeforeLongPress) {
                            val index = nearestTabIndex(down.position)
                            dampedDragAnimation.animateToValue(index.toFloat())
                            commitTabSelection(index)
                            isUserGesturing = false
                            animationScope.launch {
                                offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                            }
                            return@awaitEachGesture
                        }

                        isUserGesturing = true
                        lastGesturePosition = latestPosition
                        dampedDragAnimation.updateValue(valueAt(latestPosition))
                        if (!completedBeforeTimeout && !movedBeforeLongPress &&
                            nearestTabIndex(down.position) == feedTabIndex
                        ) {
                            currentOnTabLongPress.value(feedTabIndex)
                        }

                        var previousPosition = latestPosition
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break
                            latestPosition = change.position
                            change.consume()
                            if (!change.pressed) break

                            val dragAmount = latestPosition - previousPosition
                            previousPosition = latestPosition
                            lastGesturePosition = latestPosition
                            dampedDragAnimation.updateValue(valueAt(latestPosition))
                            animationScope.launch {
                                offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                            }
                        }

                        val targetIndex = nearestTabIndex(latestPosition)
                        dampedDragAnimation.animateToValue(targetIndex.toFloat())
                        if (targetIndex != currentSelectedIndex.value) commitTabSelection(targetIndex)
                        isUserGesturing = false
                        animationScope.launch {
                            offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                        }
                    }
                },
        )
    }
}
