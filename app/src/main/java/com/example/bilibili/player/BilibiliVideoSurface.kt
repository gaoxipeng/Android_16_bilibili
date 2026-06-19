package com.example.bilibili.player

import android.view.LayoutInflater
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.bilibili.R
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.ui.liquidglass.TransparentLiquidCapsule
import com.example.bilibili.ui.liquidglass.TransparentLiquidTextButton
import com.example.bilibili.ui.theme.BiliPink
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlin.math.abs

private val VideoProgressLineWidth = 2.5.dp

@Composable
fun BilibiliVideoSurface(
    playbackKey: String,
    stream: BiliPlayStream,
    isFullscreen: Boolean,
    coordinator: VideoPlaybackCoordinator,
    backdrop: Backdrop,
    onFullscreen: () -> Unit,
    onCloseFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    isPeekPlayback: Boolean = false,
    controlsEnabled: Boolean = true,
    initialControlsVisible: Boolean = true,
    showFullscreenButton: Boolean = true,
    playbackSpeedOverride: Float? = null,
    onPlaybackEnded: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val videoPeekController = LocalVideoPeekController.current
    val layerBackdrop = backdrop as? LayerBackdrop ?: rememberLayerBackdrop()
    var positionMs by remember(playbackKey) { mutableLongStateOf(0L) }
    var durationMs by remember(playbackKey) { mutableLongStateOf(0L) }
    var isPlaying by remember(playbackKey) { mutableStateOf(true) }
    var isBuffering by remember(playbackKey) { mutableStateOf(true) }
    var selectedSpeed by remember(playbackKey) { mutableStateOf(1f) }
    var controlsVisible by remember(playbackKey) { mutableStateOf(initialControlsVisible) }
    var controlsHideSignal by remember(playbackKey) { mutableIntStateOf(0) }
    var isScrubbing by remember(playbackKey) { mutableStateOf(false) }

    var player by remember(playbackKey) { mutableStateOf<ExoPlayer?>(null) }

    ImmersiveVideoChromeEffect(enabled = isFullscreen)
    FullscreenLandscapeEffect(enabled = isFullscreen)

    LaunchedEffect(playbackKey, stream, isPeekPlayback, isFullscreen) {
        val existing = player
        if (existing != null) {
            if (existing.mediaItemCount > 0) return@LaunchedEffect
            runCatching { existing.release() }
            player = null
        }
        val waitForHandoff = isPeekPlayback || isFullscreen
        val maxAttempts = if (waitForHandoff) 16 else 1
        repeat(maxAttempts) { attempt ->
            coordinator.consumeHandoffPlayer(playbackKey)?.let { handedOff ->
                handedOff.playWhenReady = true
                if (handedOff.playbackState == Player.STATE_IDLE) {
                    handedOff.prepare()
                }
                handedOff.play()
                positionMs = handedOff.currentPosition.coerceAtLeast(0L)
                durationMs = handedOff.duration.coerceAtLeast(0L)
                isBuffering = handedOff.playbackState == Player.STATE_BUFFERING
                player = handedOff
                return@LaunchedEffect
            }
            if (attempt < maxAttempts - 1) {
                delay(32)
            }
        }
        val startPositionMs = coordinator.getPlaybackPosition(playbackKey)
        player = createExoPlayer(
            context = context,
            stream = stream,
            startPositionMs = startPositionMs,
        ) {
            isBuffering = false
        }.also {
            it.playWhenReady = true
            positionMs = startPositionMs
        }
    }

    val activePlayer = player
    if (activePlayer == null) {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
            )
        }
        return
    }

    WatchHistoryEffect(stream = stream, player = activePlayer)

    DisposableEffect(activePlayer, playbackKey, isFullscreen, isPeekPlayback) {
        val pauseHandler = {
            coordinator.savePlaybackPosition(playbackKey, activePlayer.currentPosition)
            activePlayer.pause()
        }
        if (isPeekPlayback) {
            coordinator.registerPeekPauseHandler(pauseHandler)
        } else if (!isFullscreen) {
            coordinator.registerInlinePauseHandler(pauseHandler)
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    isScrubbing = false
                }
                positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
                val duration = activePlayer.duration
                if (duration > 0L) {
                    durationMs = duration
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = activePlayer.duration.coerceAtLeast(0L)
                    isBuffering = false
                    positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    onPlaybackEnded?.invoke()
                }
            }
        }
        activePlayer.addListener(listener)
        onDispose {
            coordinator.savePlaybackPosition(playbackKey, activePlayer.currentPosition)
            activePlayer.removeListener(listener)
            if (isPeekPlayback) {
                coordinator.unregisterPeekPauseHandler(pauseHandler)
                if (videoPeekController.activeRequest == null) {
                    coordinator.releasePeekPlayback(playbackKey)
                }
            } else if (!isFullscreen) {
                coordinator.unregisterInlinePauseHandler(pauseHandler)
            }
            val handoffToPeekFullscreen = isPeekPlayback &&
                !isFullscreen &&
                videoPeekController.isFullscreenMode &&
                videoPeekController.activeRequest != null
            when {
                handoffToPeekFullscreen -> Unit
                !isFullscreen && coordinator.fullscreenKey == playbackKey -> {
                    coordinator.stashPlayer(playbackKey, activePlayer)
                }
                isFullscreen && coordinator.fullscreenKey == null && coordinator.activeKey == playbackKey -> {
                    coordinator.stashPlayer(playbackKey, activePlayer)
                }
                !isFullscreen && coordinator.activeKey == playbackKey -> {
                    coordinator.stashPlayer(playbackKey, activePlayer)
                }
                !isFullscreen && (
                    coordinator.peekPlaybackKey == playbackKey ||
                        coordinator.pendingPeekHandoffKey == playbackKey
                    ) -> {
                    coordinator.stashPlayer(playbackKey, activePlayer)
                }
                else -> activePlayer.release()
            }
        }
    }

    LaunchedEffect(isFullscreen, isPeekPlayback, activePlayer) {
        if (isFullscreen && !isPeekPlayback && videoPeekController.isFullscreenMode) {
            activePlayer.setPlaybackSpeed(1f)
            selectedSpeed = 1f
            activePlayer.playWhenReady = true
            if (activePlayer.playbackState == Player.STATE_IDLE) {
                activePlayer.prepare()
            }
            activePlayer.play()
        }
    }

    LaunchedEffect(activePlayer, playbackSpeedOverride, isPeekPlayback, isFullscreen) {
        when {
            playbackSpeedOverride != null -> {
                activePlayer.setPlaybackSpeed(playbackSpeedOverride)
                selectedSpeed = playbackSpeedOverride
            }
            isPeekPlayback || isFullscreen -> {
                activePlayer.setPlaybackSpeed(1f)
                selectedSpeed = 1f
            }
        }
    }

    val positionState by rememberUpdatedState(positionMs)
    val durationState by rememberUpdatedState(durationMs)
    val onSeekState = rememberUpdatedState<(Long) -> Unit> { target ->
        controlsHideSignal++
        controlsVisible = true
        positionMs = target
        activePlayer.seekTo(target)
    }

    LaunchedEffect(activePlayer, isPlaying, isBuffering, isScrubbing) {
        while (true) {
            if (!isScrubbing) {
                positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
                durationMs = activePlayer.duration.coerceAtLeast(durationMs)
            }
            delay(if (isScrubbing) 500 else 250)
        }
    }

    LaunchedEffect(isPlaying, isBuffering, controlsHideSignal) {
        if (!isPlaying || isBuffering) {
            controlsVisible = true
            return@LaunchedEffect
        }
        delay(5_000)
        controlsVisible = false
    }

    val onPlayPauseState = rememberUpdatedState<() -> Unit>({
        controlsHideSignal++
        if (activePlayer.isPlaying) {
            activePlayer.pause()
        } else {
            if (durationMs > 0 && positionMs >= durationMs - 500) {
                activePlayer.seekTo(0)
            }
            activePlayer.play()
        }
    })

    Box(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds()
            .then(
                if (controlsEnabled) {
                    Modifier
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val slop = viewConfiguration.touchSlop
                                val anchorX = down.position.x
                                val startY = down.position.y
                                val anchorPosition = positionState
                                val width = size.width.toFloat()
                                var dragging = false
                                var lastSeekPosition = anchorPosition
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                        ?: break
                                    val dx = abs(change.position.x - anchorX)
                                    val dy = abs(change.position.y - startY)
                                    val duration = durationState
                                    if (!dragging && dx > slop && dx > dy) {
                                        dragging = true
                                        isScrubbing = true
                                        controlsVisible = true
                                        controlsHideSignal++
                                    }
                                    if (dragging && duration > 0L && width > 0f && change.pressed) {
                                        change.consume()
                                        val deltaMs = ((change.position.x - anchorX) / width * duration).toLong()
                                        val newPosition = (anchorPosition + deltaMs).coerceIn(0L, duration)
                                        if (newPosition != lastSeekPosition) {
                                            lastSeekPosition = newPosition
                                            onSeekState.value(newPosition)
                                        }
                                    }
                                    if (event.changes.all { it.changedToUpIgnoreConsumed() }) break
                                }
                                if (dragging) {
                                    isScrubbing = false
                                }
                            }
                        }
                        .pointerInput(isFullscreen, controlsEnabled) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                    controlsHideSignal++
                                },
                                onDoubleTap = { onPlayPauseState.value() },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(layerBackdrop),
        ) {
            AndroidView(
                factory = { ctx ->
                    LayoutInflater.from(ctx).inflate(R.layout.view_video_player, null, false)
                        as androidx.media3.ui.PlayerView
                },
                update = { playerView ->
                    playerView.player = activePlayer
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                strokeWidth = 2.dp,
            )
        }

        if (isFullscreen) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it },
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                TransparentLiquidTextButton(
                    text = "关闭",
                    onClick = onCloseFullscreen,
                    backdrop = layerBackdrop,
                    textColor = Color.White,
                    modifier = Modifier
                        .padding(12.dp)
                        .widthIn(min = 54.dp)
                        .height(28.dp),
                )
            }
        } else if (showFullscreenButton) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                TransparentLiquidTextButton(
                    text = "全屏",
                    onClick = onFullscreen,
                    backdrop = layerBackdrop,
                    textColor = Color.White,
                    modifier = Modifier
                        .padding(8.dp)
                        .widthIn(min = 54.dp)
                        .height(28.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsEnabled && controlsVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 6.dp, end = 6.dp, bottom = if (isFullscreen) 40.dp else 8.dp)
                .fillMaxWidth(),
        ) {
            VideoControls(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                speed = selectedSpeed,
                backdrop = layerBackdrop,
                isScrubbing = isScrubbing,
                onScrubbingChange = { isScrubbing = it },
                onPlayPause = { onPlayPauseState.value() },
                onSeek = { onSeekState.value(it) },
                onSpeedClick = {
                    controlsHideSignal++
                    selectedSpeed = when (selectedSpeed) {
                        1f -> 1.5f
                        1.5f -> 2f
                        else -> 1f
                    }
                    activePlayer.setPlaybackSpeed(selectedSpeed)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
            )
        }
    }
}

@Composable
private fun VideoControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedClick: () -> Unit,
    backdrop: Backdrop,
    isScrubbing: Boolean,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val positionState by rememberUpdatedState(positionMs)
    val durationState by rememberUpdatedState(durationMs)
    val onSeekState by rememberUpdatedState(onSeek)
    val onScrubbingChangeState by rememberUpdatedState(onScrubbingChange)

    TransparentLiquidCapsule(
        modifier = modifier.pointerInput(durationState) {
            awaitEachGesture {
                onScrubbingChangeState(true)
                try {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val duration = durationState
                    val width = size.width.toFloat()
                    if (duration <= 0L || width <= 0f) return@awaitEachGesture
                    val anchorPosition = positionState
                    val anchorX = down.position.x
                    var lastSeekPosition = anchorPosition
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            val deltaMs = ((change.position.x - anchorX) / width * duration).toLong()
                            val newPosition = (anchorPosition + deltaMs).coerceIn(0L, duration)
                            if (newPosition != lastSeekPosition) {
                                lastSeekPosition = newPosition
                                onSeekState(newPosition)
                            }
                        }
                        if (event.changes.all { it.changedToUpIgnoreConsumed() }) break
                    }
                } finally {
                    onScrubbingChangeState(false)
                }
            }
        },
        backdrop = backdrop,
        pill = true,
    ) {
        VideoControlCapsuleProgressBackground(
            progress = progress,
            animate = isPlaying && !isScrubbing,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = formatVideoTime(positionMs),
                modifier = Modifier.widthIn(min = 36.dp),
                color = Color.White,
                style = TextStyle(fontSize = 12.sp),
                maxLines = 1,
            )
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(25.dp),
            ) {
                Icon(
                    painter = painterResource(
                        if (isPlaying) R.drawable.ic_video_pause else R.drawable.ic_video_play,
                    ),
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(20.dp),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = speedLabel(speed),
                color = Color.White,
                style = TextStyle(fontSize = 13.sp),
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onSpeedClick,
                ),
            )
            Text(
                text = formatVideoTime((durationMs - positionMs).coerceAtLeast(0L)),
                modifier = Modifier.widthIn(min = 36.dp),
                color = Color.White.copy(alpha = 0.82f),
                style = TextStyle(fontSize = 12.sp),
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
    }
}

private val VideoControlCapsuleShape = RoundedCornerShape(percent = 50)

@Composable
private fun VideoControlCapsuleProgressBackground(
    progress: Float,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val targetProgress = progress.coerceIn(0f, 1f)
    val displayedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (animate) {
            tween(durationMillis = 120, easing = LinearEasing)
        } else {
            snap()
        },
        label = "video-control-progress",
    )
    BoxWithConstraints(
        modifier = modifier.clip(VideoControlCapsuleShape),
    ) {
        if (displayedProgress > 0f) {
            val lineOffset = (maxWidth * displayedProgress - VideoProgressLineWidth)
                .coerceIn(0.dp, maxWidth - VideoProgressLineWidth)
            Box(
                modifier = Modifier
                    .offset(x = lineOffset)
                    .width(VideoProgressLineWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(BiliPink),
            )
        }
    }
}

@Composable
fun rememberVideoControlBackdrop(): Backdrop = rememberLayerBackdrop()
