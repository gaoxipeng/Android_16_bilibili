package com.example.bilibili.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.abs

private const val VideoPeekFloatingPlaybackSpeed = 2f
private const val VideoPeekMaxDockHeightFraction = 1f / 3f
private const val VideoPeekDockAspectRatio = 16f / 9f
private const val VideoPeekDockTopExtraOffsetDp = 16
private const val VideoPeekTransitionDurationMs = 300

@Composable
fun VideoPeekOverlay(
    video: BiliVideoItem,
    playStream: BiliPlayStream,
    playbackKey: String,
    coordinator: VideoPlaybackCoordinator,
    anchorBounds: Rect,
    expandFromAnchor: Boolean,
    dockImmediately: Boolean,
    isFloating: Boolean,
    isFullscreenMode: Boolean,
    dismissReason: VideoPeekDismissReason?,
    onRequestCancel: () -> Unit,
    onDismissComplete: () -> Unit,
    onPlaybackEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val videoPeekController = LocalVideoPeekController.current
    val backdrop = rememberLayerBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var aspectRatio by remember(video.bvid) { mutableFloatStateOf(16f / 9f) }

    val enterProgress = remember(expandFromAnchor, dockImmediately) {
        Animatable(if (dockImmediately) 1f else 0f)
    }
    val dockProgress = remember(dockImmediately) {
        Animatable(if (dockImmediately) 1f else 0f)
    }
    val fullscreenExpandProgress = remember { Animatable(if (isFullscreenMode) 1f else 0f) }
    val isDocked = isFloating || dockProgress.value > 0f || isFullscreenMode

    ImmersiveVideoChromeEffect(enabled = isFullscreenMode)

    LaunchedEffect(expandFromAnchor, dockImmediately) {
        when {
            expandFromAnchor -> enterProgress.snapTo(0f)
            dockImmediately -> enterProgress.snapTo(1f)
            else -> enterProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    LaunchedEffect(isFloating, dockImmediately, isFullscreenMode) {
        if (isFullscreenMode) {
            enterProgress.snapTo(1f)
            dockProgress.snapTo(1f)
            fullscreenExpandProgress.snapTo(1f)
            return@LaunchedEffect
        }
        if (isFloating) {
            if (enterProgress.value < 1f) {
                enterProgress.snapTo(1f)
            }
            if (dockImmediately) {
                dockProgress.snapTo(1f)
            } else {
                dockProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
        } else if (dismissReason == null) {
            dockProgress.snapTo(0f)
        }
    }

    LaunchedEffect(dismissReason) {
        when (dismissReason) {
            null -> return@LaunchedEffect
            VideoPeekDismissReason.EnterFullscreen -> {
                if (!expandFromAnchor && enterProgress.value < 1f) {
                    enterProgress.snapTo(1f)
                }
                if (isFloating && dockProgress.value < 1f) {
                    dockProgress.snapTo(1f)
                }
                fullscreenExpandProgress.snapTo(0f)
                fullscreenExpandProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = VideoPeekTransitionDurationMs,
                        easing = FastOutSlowInEasing,
                    ),
                )
                videoPeekController.completeFullscreenExpand()
            }
            else -> {
                fullscreenExpandProgress.snapTo(0f)
                if (dockProgress.value > 0f) {
                    dockProgress.animateTo(0f, tween(180))
                }
                enterProgress.animateTo(0f, tween(180))
                onDismissComplete()
            }
        }
    }

    BackHandler(enabled = isFullscreenMode || (dismissReason != VideoPeekDismissReason.EnterFullscreen && isDocked)) {
        onRequestCancel()
    }

    val expandProgressForBackdrop = fullscreenExpandProgress.value.coerceIn(0f, 1f)
    val expandingToFullscreen = isFullscreenMode ||
        expandProgressForBackdrop > 0f ||
        dismissReason == VideoPeekDismissReason.EnterFullscreen

    BoxWithConstraints(
        modifier = modifier.then(
            if (expandingToFullscreen || !isDocked) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth()
            },
        ),
    ) {
        val peekProgress = enterProgress.value.coerceIn(0f, 1f)
        val expandProgress = expandProgressForBackdrop
        val dockedProgress = dockProgress.value.coerceIn(0f, 1f)
        val effectiveMaxHeight = if (maxHeight == Dp.Infinity) screenHeight else maxHeight

        val previewWidth = minOf(maxWidth - 32.dp, maxWidth * 0.94f)
        val previewHeight = previewWidth / aspectRatio.coerceIn(0.56f, 1.78f)
        val holdLeft = (maxWidth - previewWidth) / 2
        val holdTop = effectiveMaxHeight * 0.18f
        val dockAspect = VideoPeekDockAspectRatio
        val maxDockHeight = effectiveMaxHeight * VideoPeekMaxDockHeightFraction
        val dockWidthCap = maxWidth
        var dockWidth = dockWidthCap
        var dockHeight = dockWidth / dockAspect
        if (dockHeight > maxDockHeight) {
            dockHeight = maxDockHeight
        }
        val dockLeft = 0.dp
        val dockTop = statusBarTop + VideoPeekDockTopExtraOffsetDp.dp

        val anchorLeft = with(density) { anchorBounds.left.toDp() }
        val anchorTop = with(density) { anchorBounds.top.toDp() }
        val anchorWidth = with(density) { anchorBounds.width.toDp().coerceAtLeast(1.dp) }
        val anchorHeight = with(density) { anchorBounds.height.toDp().coerceAtLeast(1.dp) }

        val floatLeft = anchorLeft + (holdLeft - anchorLeft) * peekProgress
        val floatTop = anchorTop + (holdTop - anchorTop) * peekProgress
        val floatWidth = anchorWidth + (previewWidth - anchorWidth) * peekProgress
        val floatHeight = anchorHeight + (previewHeight - anchorHeight) * peekProgress
        val floatCorner = 4.dp + (18.dp - 4.dp) * peekProgress

        val settledLeft = floatLeft + (dockLeft - floatLeft) * dockedProgress
        val settledTop = floatTop + (dockTop - floatTop) * dockedProgress
        val settledWidth = floatWidth + (dockWidth - floatWidth) * dockedProgress
        val settledHeight = floatHeight + (dockHeight - floatHeight) * dockedProgress
        val settledCorner = floatCorner * (1f - dockedProgress) + 12.dp * dockedProgress

        val previewLeft = settledLeft * (1f - expandProgress)
        val previewTop = settledTop * (1f - expandProgress)
        val previewW = settledWidth + (maxWidth - settledWidth) * expandProgress
        val previewH = settledHeight + (effectiveMaxHeight - settledHeight) * expandProgress
        val previewCorner = settledCorner * (1f - expandProgress)
        val previewAlpha = when {
            expandProgress > 0f -> 1f
            isDocked -> 1f
            else -> peekProgress
        }
        val previewShape = RoundedCornerShape(previewCorner)
        val useDockedColumnLayout = isDocked && !expandingToFullscreen

        if (expandingToFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        } else if (!isDocked) {
            val scrimAlpha = 0.18f * peekProgress
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        enabled = dismissReason == null,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onRequestCancel() },
                    )
                    .pointerInput(dismissReason) {
                        if (dismissReason != null) return@pointerInput
                        val dragGestureThreshold = 82f
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDrag = Offset.Zero
                            var handled = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                    ?: break
                                if (!change.pressed) {
                                    if (!handled) {
                                        val verticalDominant = abs(totalDrag.y) > abs(totalDrag.x) * 1.15f
                                        when {
                                            totalDrag.y > dragGestureThreshold && verticalDominant -> onRequestCancel()
                                            totalDrag.y < -dragGestureThreshold && verticalDominant -> videoPeekController.release()
                                            else -> videoPeekController.enterFullscreen()
                                        }
                                    }
                                    break
                                }
                                totalDrag += change.position - change.previousPosition
                                val verticalDominant = abs(totalDrag.y) > abs(totalDrag.x) * 1.15f
                                if (totalDrag.y > dragGestureThreshold && verticalDominant) {
                                    onRequestCancel()
                                    handled = true
                                    consumeRemainingPointer(down.id)
                                    break
                                }
                                if (totalDrag.y < -dragGestureThreshold && verticalDominant) {
                                    videoPeekController.release()
                                    handled = true
                                    consumeRemainingPointer(down.id)
                                    break
                                }
                            }
                        }
                    },
            )
        }

        val videoCardModifier = Modifier
            .size(previewW, previewH)
            .graphicsLayer { alpha = previewAlpha.coerceIn(0f, 1f) }
            .shadow(
                elevation = if (expandProgress > 0f || isDocked) 8.dp else (18f * peekProgress * (1f - expandProgress)).dp,
                shape = previewShape,
                clip = false,
            )
            .clip(previewShape)
            .background(Color.Black)
            .then(
                if (isDocked && dismissReason == null) {
                    Modifier.pointerInput(video.bvid) {
                        val dragGestureThreshold = 82f
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDrag = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                    ?: break
                                if (!change.pressed) break
                                totalDrag += change.position - change.previousPosition
                                val verticalDominant = abs(totalDrag.y) > abs(totalDrag.x) * 1.15f
                                if (verticalDominant && abs(totalDrag.y) > dragGestureThreshold) {
                                    change.consume()
                                    onRequestCancel()
                                    consumeRemainingPointer(down.id)
                                    break
                                }
                            }
                        }
                    }
                } else if (!isDocked && dismissReason == null) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { videoPeekController.enterFullscreen() },
                    )
                } else {
                    Modifier
                },
            )

        @Composable
        fun VideoPeekCard(cardModifier: Modifier = Modifier) {
            Box(modifier = cardModifier.then(videoCardModifier)) {
                BilibiliVideoSurface(
                    playbackKey = playbackKey,
                    stream = playStream,
                    isFullscreen = isFullscreenMode,
                    coordinator = coordinator,
                    backdrop = backdrop,
                    onFullscreen = { videoPeekController.enterFullscreen() },
                    onCloseFullscreen = onRequestCancel,
                    isPeekPlayback = !isFullscreenMode,
                    controlsEnabled = isDocked || isFullscreenMode,
                    initialControlsVisible = false,
                    showFullscreenButton = false,
                    playbackSpeedOverride = when {
                        isFullscreenMode || isDocked -> null
                        else -> VideoPeekFloatingPlaybackSpeed
                    },
                    onPlaybackEnded = onPlaybackEnded,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (useDockedColumnLayout) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(previewTop))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    VideoPeekCard()
                }
            }
        } else {
            VideoPeekCard(
                cardModifier = Modifier.offset(x = previewLeft, y = previewTop),
            )
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.consumeRemainingPointer(
    @Suppress("UNUSED_PARAMETER") pointerId: androidx.compose.ui.input.pointer.PointerId,
) {
    while (true) {
        val consumeEvent = awaitPointerEvent(PointerEventPass.Initial)
        consumeEvent.changes.forEach { it.consume() }
        if (consumeEvent.changes.all { !it.pressed }) break
    }
}
