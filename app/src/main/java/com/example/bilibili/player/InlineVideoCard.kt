package com.example.bilibili.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.ui.components.RemoteImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun InlineVideoCard(
    video: BiliVideoItem,
    playStream: BiliPlayStream?,
    coordinator: VideoPlaybackCoordinator,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    sharedBackdrop: LayerBackdrop? = null,
    roundAllCorners: Boolean = false,
    showDurationBadge: Boolean = true,
    enforceAspectRatio: Boolean = true,
    onEnsurePlayStream: (() -> Unit)? = null,
    currentPlayStream: () -> BiliPlayStream? = { playStream },
    onCardClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val playbackKey = videoPlaybackKey(video.bvid)
    val videoPeekController = LocalVideoPeekController.current
    val haptic = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    var anchorBounds by remember(video.bvid) { mutableStateOf<Rect?>(null) }
    var actionOpen by remember(video.bvid) { mutableStateOf(false) }
    var peekActive by remember(video.bvid) { mutableStateOf(false) }
    val inlinePlaying = coordinator.activeKey == playbackKey &&
        coordinator.fullscreenKey != playbackKey &&
        coordinator.peekPlaybackKey != playbackKey
    val coverBackdrop = sharedBackdrop ?: rememberLayerBackdrop()
    val clipShape = if (roundAllCorners) {
        RoundedCornerShape(cornerRadius)
    } else {
        RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
    }
    val peekScale by animateFloatAsState(
        targetValue = if (peekActive) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "inline-video-peek-scale",
    )
    val currentStreamState = rememberUpdatedState(currentPlayStream)
    val ensurePlayStreamState = rememberUpdatedState(onEnsurePlayStream)
    val hideCardContent = actionOpen ||
        coordinator.peekPlaybackKey == playbackKey ||
        videoPeekController.activeRequest?.video?.bvid == video.bvid

    fun resetPeekState() {
        actionOpen = false
        peekActive = false
    }

    fun openVideoPeek(stream: BiliPlayStream) {
        val bounds = anchorBounds ?: return
        val wasPlayingBeforePeek = coordinator.activeKey == playbackKey &&
            coordinator.fullscreenKey != playbackKey
        coordinator.beginPeekHandoff(playbackKey)
        if (!wasPlayingBeforePeek && !coordinator.hasHandoffPlayer(playbackKey)) {
            val player = createExoPlayer(
                context = context,
                stream = stream,
                startPositionMs = coordinator.getPlaybackPosition(playbackKey),
            )
            coordinator.stashPlayer(playbackKey, player)
        }
        actionOpen = true
        peekActive = true
        coordinator.claimPeekPlayback(playbackKey)
        videoPeekController.open(
            VideoPeekRequest(
                video = video,
                playStream = stream,
                anchorBounds = bounds,
                onCancel = {
                    coordinator.cancelPeekHandoff(playbackKey)
                    coordinator.releasePeekPlayback(playbackKey)
                    resetPeekState()
                    if (wasPlayingBeforePeek) {
                        coordinator.requestInlinePlayback(playbackKey)
                    }
                },
                onRelease = {},
                onPlaybackEnded = {
                    coordinator.releasePeekPlayback(playbackKey)
                    resetPeekState()
                },
                onEnterFullscreenHandoffComplete = {
                    coordinator.releasePeekPlayback(playbackKey)
                    resetPeekState()
                },
            ),
        )
    }

    Box(
        modifier = modifier
            .then(
                if (enforceAspectRatio) {
                    Modifier.aspectRatio(16f / 9f)
                } else {
                    Modifier.fillMaxSize()
                },
            )
            .clip(clipShape)
            .then(
                if (hideCardContent) {
                    Modifier
                } else {
                    Modifier.background(Color.Black)
                },
            )
            .onGloballyPositioned { coordinates ->
                anchorBounds = coordinates.boundsInWindow()
            }
            .graphicsLayer {
                scaleX = peekScale
                scaleY = peekScale
            }
            .pointerInput(video.bvid) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    ensurePlayStreamState.value?.invoke()
                    var cancelledByMoveBeforeLongPress = false
                    var releasedBeforeLongPress = false
                    val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: return@withTimeoutOrNull false
                            if (!change.pressed) {
                                releasedBeforeLongPress = true
                                return@withTimeoutOrNull false
                            }
                            val preLongPressMove = change.position - down.position
                            if (hypot(preLongPressMove.x, preLongPressMove.y) > viewConfiguration.touchSlop) {
                                cancelledByMoveBeforeLongPress = true
                                return@withTimeoutOrNull false
                            }
                        }
                    } == null && !cancelledByMoveBeforeLongPress && !releasedBeforeLongPress

                    if (!longPressed) {
                        if (releasedBeforeLongPress && !cancelledByMoveBeforeLongPress) {
                            onCardClick?.invoke()
                        }
                        return@awaitEachGesture
                    }

                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val stream = currentStreamState.value() ?: return@awaitEachGesture
                    down.consume()
                    openVideoPeek(stream)

                    val dragGestureThreshold = 82f
                    var cancelledByDrag = false
                    var floatByDrag = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break
                        change.consume()
                        if (!change.pressed) break
                        val totalDrag = change.position - down.position
                        val verticalDominant = abs(totalDrag.y) > abs(totalDrag.x) * 1.15f
                        if (totalDrag.y > dragGestureThreshold && verticalDominant) {
                            cancelledByDrag = true
                            videoPeekController.cancel()
                            while (true) {
                                val consumeEvent = awaitPointerEvent(PointerEventPass.Initial)
                                consumeEvent.changes.forEach { it.consume() }
                                if (consumeEvent.changes.all { !it.pressed }) break
                            }
                            break
                        }
                        if (totalDrag.y < -dragGestureThreshold && verticalDominant) {
                            floatByDrag = true
                            videoPeekController.release()
                            while (true) {
                                val consumeEvent = awaitPointerEvent(PointerEventPass.Initial)
                                consumeEvent.changes.forEach { it.consume() }
                                if (consumeEvent.changes.all { !it.pressed }) break
                            }
                            break
                        }
                    }

                    if (!cancelledByDrag && !floatByDrag) {
                        videoPeekController.enterFullscreen()
                    }
                }
            },
    ) {
        if (!hideCardContent) {
            if (inlinePlaying && playStream != null) {
                BilibiliVideoSurface(
                    playbackKey = playbackKey,
                    stream = playStream,
                    isFullscreen = false,
                    coordinator = coordinator,
                    backdrop = coverBackdrop,
                    onFullscreen = { coordinator.openFullscreen(playbackKey) },
                    onCloseFullscreen = { coordinator.closeFullscreen() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .layerBackdrop(coverBackdrop),
                ) {
                    RemoteImage(
                        url = video.coverUrl,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (showDurationBadge) {
                    Text(
                        text = formatDuration(video.durationSeconds),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remain = seconds % 60
    return "%d:%02d".format(minutes, remain)
}
