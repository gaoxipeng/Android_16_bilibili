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
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.bilibili.R
import com.example.bilibili.data.BiliDanmakuItem
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.ui.theme.BiliPink
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlin.math.abs

private val VideoProgressLineWidth = 2.5.dp

private val VideoControlBarHeight = 32.dp
private val VideoControlBarBottomGap = 6.dp
private val VideoControlBorderWidth = 0.5.dp
private val VideoControlBorderColor = Color(0x80999999)

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
    danmakuEnabled: Boolean = false,
    danmakuCid: Long = 0L,
    loadDanmaku: (suspend (Long) -> List<BiliDanmakuItem>)? = null,
    playbackEnabled: Boolean = true,
    portraitVideo: Boolean = false,
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
    var danmakuItems by remember(playbackKey) { mutableStateOf<List<BiliDanmakuItem>>(emptyList()) }
    var showDanmakuSettings by remember(playbackKey) { mutableStateOf(false) }
    val showDanmakuFeature = danmakuEnabled && danmakuCid > 0L && loadDanmaku != null
    val danmakuVisible = coordinator.danmakuVisible
    val danmakuSettings = coordinator.danmakuSettings

    LaunchedEffect(showDanmakuFeature, danmakuCid, loadDanmaku) {
        if (!showDanmakuFeature || loadDanmaku == null) {
            danmakuItems = emptyList()
            return@LaunchedEffect
        }
        danmakuItems = coordinator.cachedDanmaku(danmakuCid) { loadDanmaku(danmakuCid) }
    }

    var player by remember(playbackKey) { mutableStateOf<ExoPlayer?>(null) }
    var isPortraitPlayback by remember(playbackKey) { mutableStateOf(portraitVideo) }
    var playerSizeKnown by remember(playbackKey) { mutableStateOf(false) }

    LaunchedEffect(portraitVideo) {
        if (portraitVideo) {
            isPortraitPlayback = true
            if (isFullscreen) {
                coordinator.updateFullscreenPortrait(true)
            }
        }
    }

    val fullscreenPortraitOrientation = when {
        !isFullscreen -> null
        portraitVideo -> true
        coordinator.fullscreenPortraitVideo != null -> coordinator.fullscreenPortraitVideo
        playerSizeKnown -> isPortraitPlayback
        else -> null
    }

    ImmersiveVideoChromeEffect(enabled = isFullscreen)
    FullscreenOrientationEffect(
        enabled = isFullscreen,
        portraitVideo = fullscreenPortraitOrientation,
    )

    LaunchedEffect(playbackKey, stream, isPeekPlayback, isFullscreen, playbackEnabled) {
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
                if (playbackEnabled) {
                    handedOff.playWhenReady = true
                    if (handedOff.playbackState == Player.STATE_IDLE) {
                        handedOff.prepare()
                    }
                    handedOff.play()
                } else {
                    coordinator.savePlaybackPosition(playbackKey, handedOff.currentPosition)
                    handedOff.playWhenReady = false
                    handedOff.pause()
                }
                positionMs = handedOff.currentPosition.coerceAtLeast(0L)
                durationMs = handedOff.duration.coerceAtLeast(0L)
                isBuffering = handedOff.playbackState == Player.STATE_BUFFERING
                handedOff.videoSize.let { videoSize ->
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        playerSizeKnown = true
                        val portrait = isPortraitVideoSize(
                            width = videoSize.width,
                            height = videoSize.height,
                            rotationDegrees = videoSize.unappliedRotationDegrees,
                        )
                        isPortraitPlayback = portrait
                        if (isFullscreen) {
                            coordinator.updateFullscreenPortrait(portrait)
                        }
                    }
                }
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
            it.playWhenReady = playbackEnabled
            positionMs = startPositionMs
            if (!playbackEnabled) {
                it.pause()
            }
        }
    }

    val activePlayer = player
    LaunchedEffect(playbackEnabled, activePlayer) {
        val playerRef = activePlayer ?: return@LaunchedEffect
        if (playbackEnabled) {
            if (playerRef.playbackState == Player.STATE_IDLE) {
                playerRef.prepare()
            }
            playerRef.playWhenReady = true
            if (!playerRef.isPlaying) {
                playerRef.play()
            }
        } else {
            coordinator.savePlaybackPosition(playbackKey, playerRef.currentPosition)
            playerRef.playWhenReady = false
            playerRef.pause()
        }
    }

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
            activePlayer.playWhenReady = false
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

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    playerSizeKnown = true
                    val portrait = isPortraitVideoSize(
                        width = videoSize.width,
                        height = videoSize.height,
                        rotationDegrees = videoSize.unappliedRotationDegrees,
                    )
                    isPortraitPlayback = portrait
                    if (isFullscreen) {
                        coordinator.updateFullscreenPortrait(portrait)
                    }
                }
            }
        }
        activePlayer.videoSize.let { videoSize ->
            if (videoSize.width > 0 && videoSize.height > 0) {
                playerSizeKnown = true
                val portrait = isPortraitVideoSize(
                    width = videoSize.width,
                    height = videoSize.height,
                    rotationDegrees = videoSize.unappliedRotationDegrees,
                )
                isPortraitPlayback = portrait
                if (isFullscreen) {
                    coordinator.updateFullscreenPortrait(portrait)
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
                if (controlsEnabled && !showDanmakuSettings) {
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
                    playerView.resizeMode = if (isFullscreen && isPortraitPlayback) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showDanmakuFeature) {
            val danmakuBottomReserve = VideoControlBarHeight +
                VideoControlBarBottomGap +
                if (isFullscreen) 40.dp else 8.dp
            DanmakuOverlay(
                items = danmakuItems,
                positionMs = positionMs,
                isPlaying = isPlaying && !isScrubbing,
                playbackSpeed = selectedSpeed,
                topInset = 0.dp,
                bottomReserve = danmakuBottomReserve,
                enabled = danmakuVisible,
                settings = danmakuSettings,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f),
            )
        }

        DanmakuSettingsOverlay(
            visible = showDanmakuSettings,
            settings = danmakuSettings,
            onSettingsChange = coordinator::updateDanmakuSettings,
            onDismiss = { showDanmakuSettings = false },
            backdrop = layerBackdrop,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(25f),
        )

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
                VideoOverlayTextButton(
                    text = "关闭",
                    onClick = onCloseFullscreen,
                    modifier = Modifier
                        .padding(12.dp)
                        .widthIn(min = 54.dp)
                        .height(VideoControlBarHeight),
                )
            }
        } else if (showFullscreenButton) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                VideoOverlayTextButton(
                    text = "全屏",
                    onClick = onFullscreen,
                    modifier = Modifier
                        .padding(8.dp)
                        .widthIn(min = 54.dp)
                        .height(VideoControlBarHeight),
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
                showDanmakuToggle = showDanmakuFeature,
                danmakuVisible = danmakuVisible,
                onDanmakuToggle = {
                    controlsHideSignal++
                    coordinator.toggleDanmaku()
                },
                onDanmakuLongPress = {
                    controlsHideSignal++
                    showDanmakuSettings = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VideoControlBarHeight),
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
    isScrubbing: Boolean,
    onScrubbingChange: (Boolean) -> Unit,
    showDanmakuToggle: Boolean = false,
    danmakuVisible: Boolean = true,
    onDanmakuToggle: () -> Unit = {},
    onDanmakuLongPress: () -> Unit = {},
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

    Box(
        modifier = modifier
            .border(VideoControlBorderWidth, VideoControlBorderColor, VideoControlCapsuleShape)
            .clip(VideoControlCapsuleShape)
            .pointerInput(durationState) {
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
            if (showDanmakuToggle) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "弹",
                    color = if (danmakuVisible) Color.White else Color.White.copy(alpha = 0.42f),
                    fontWeight = if (danmakuVisible) FontWeight.Bold else FontWeight.Normal,
                    style = TextStyle(fontSize = 14.sp),
                    modifier = Modifier.pointerInput(onDanmakuToggle, onDanmakuLongPress) {
                        detectTapGestures(
                            onTap = { onDanmakuToggle() },
                            onLongPress = { onDanmakuLongPress() },
                        )
                    },
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
private fun VideoOverlayTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(VideoControlBorderWidth, VideoControlBorderColor, VideoControlCapsuleShape)
            .clip(VideoControlCapsuleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

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
