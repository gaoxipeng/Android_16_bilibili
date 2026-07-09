package com.example.bilibili.player

import android.view.LayoutInflater
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.input.pointer.PointerInputScope
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BiliVideoShot
import com.example.bilibili.ui.theme.BiliPink
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    videoShot: BiliVideoShot? = null,
    scrubPreviewAspectRatio: Float? = null,
    playbackMetadata: VideoPlaybackMetadata? = null,
    historyVideo: BiliVideoItem? = null,
) {
    val context = LocalContext.current
    val layerBackdrop = backdrop as? LayerBackdrop ?: rememberLayerBackdrop()
    val streamToken = "${stream.cid}:${stream.videoUrl}:${stream.audioUrl.orEmpty()}"
    val initialHandoffPlayer = remember(playbackKey, streamToken) {
        coordinator.consumeHandoffPlayer(playbackKey)
    }
    var boundStreamToken by remember(playbackKey, streamToken) {
        mutableStateOf(if (initialHandoffPlayer != null) streamToken else null)
    }
    var positionMs by remember(playbackKey, streamToken) {
        mutableLongStateOf(initialHandoffPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L)
    }
    var durationMs by remember(playbackKey, streamToken) {
        mutableLongStateOf(initialHandoffPlayer?.duration?.takeIf { it > 0L } ?: 0L)
    }
    var isPlaying by remember(playbackKey, streamToken) {
        mutableStateOf(initialHandoffPlayer?.isPlaying ?: true)
    }
    var isBuffering by remember(playbackKey, streamToken) {
        mutableStateOf(initialHandoffPlayer?.playbackState == Player.STATE_BUFFERING || initialHandoffPlayer == null)
    }
    var playbackState by remember(playbackKey, streamToken) {
        mutableIntStateOf(initialHandoffPlayer?.playbackState ?: Player.STATE_IDLE)
    }
    var playWhenReady by remember(playbackKey, streamToken) {
        mutableStateOf(initialHandoffPlayer?.playWhenReady ?: true)
    }
    var selectedSpeed by remember(playbackKey) { mutableStateOf(1f) }
    var controlsVisible by remember(playbackKey) { mutableStateOf(initialControlsVisible) }
    var controlsHideSignal by remember(playbackKey) { mutableIntStateOf(0) }
    var isScrubbing by remember(playbackKey) { mutableStateOf(false) }
    var resumePlaybackAfterScrub by remember(playbackKey) { mutableStateOf(false) }
    var danmakuItems by remember(playbackKey) { mutableStateOf<List<BiliDanmakuItem>>(emptyList()) }
    var fullscreenDanmakuMountAllowed by remember(playbackKey, isFullscreen) {
        mutableStateOf(!isFullscreen)
    }
    var showDanmakuSettings by remember(playbackKey) { mutableStateOf(false) }
    val showDanmakuFeature = danmakuEnabled && danmakuCid > 0L && loadDanmaku != null
    val danmakuVisible = coordinator.danmakuVisible
    val danmakuSettings = coordinator.danmakuSettings

    LaunchedEffect(danmakuEnabled, danmakuCid, loadDanmaku) {
        val loader = loadDanmaku
        if (!danmakuEnabled || danmakuCid <= 0L || loader == null) {
            danmakuItems = emptyList()
            return@LaunchedEffect
        }
        danmakuItems = coordinator.cachedDanmaku(danmakuCid) { loader(danmakuCid) }
    }

    var player by remember(playbackKey, streamToken) { mutableStateOf<ExoPlayer?>(initialHandoffPlayer) }
    var playerHandedOff by remember(playbackKey) { mutableStateOf(false) }
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
        !coordinator.fullscreenOrientationLocked -> null
        portraitVideo -> true
        coordinator.fullscreenPortraitVideo != null -> coordinator.fullscreenPortraitVideo
        playerSizeKnown -> isPortraitPlayback
        else -> null
    }
    val shotRefererUrl = resolvePlaybackReferer(
        playbackKey = playbackKey,
        playbackMetadata = playbackMetadata,
    )
    val resolvedVideoShot = videoShot
        ?: stream.cid.takeIf { it > 0L }?.let { coordinator.cachedVideoShot(it) }
    val resolvedScrubPreviewAspectRatio =
        scrubPreviewAspectRatio ?: coordinator.cachedVideoAspectRatio(playbackKey)

    LaunchedEffect(playbackKey, scrubPreviewAspectRatio) {
        coordinator.cacheVideoAspectRatio(playbackKey, scrubPreviewAspectRatio)
    }

    LaunchedEffect(resolvedVideoShot, shotRefererUrl) {
        resolvedVideoShot?.images?.take(2)?.forEach { spriteUrl ->
            VideoShotImageLoader.preloadSprite(spriteUrl, shotRefererUrl)
        }
    }

    ImmersiveVideoChromeEffect(enabled = isFullscreen)
    FullscreenOrientationEffect(
        enabled = isFullscreen,
        portraitVideo = fullscreenPortraitOrientation,
    )

    DisposableEffect(playbackKey, isFullscreen) {
        val handoffHandler: (String) -> Unit = handoffHandler@{ requestedKey ->
            if (requestedKey != playbackKey) return@handoffHandler
            val currentPlayer = player ?: return@handoffHandler
            coordinator.stashPlayer(playbackKey, currentPlayer, keepPlaying = true)
            playerHandedOff = true
        }
        coordinator.registerHandoffPrepareHandler(handoffHandler)
        onDispose {
            coordinator.unregisterHandoffPrepareHandler(handoffHandler)
        }
    }

    LaunchedEffect(playbackKey, stream.cid, stream.videoUrl, stream.audioUrl, shotRefererUrl, playbackEnabled) {
        if (player != null && boundStreamToken == streamToken) return@LaunchedEffect

        val streamChanged = boundStreamToken != null && boundStreamToken != streamToken
        if (streamChanged) {
            coordinator.releaseHandoffPlayer()
        }

        player?.let { coordinator.releasePlayerOnce(it) }
        player = null
        playerHandedOff = false
        boundStreamToken = streamToken

        fun bindHandoffPlayer(handedOff: ExoPlayer) {
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
            playbackState = handedOff.playbackState
            playWhenReady = handedOff.playWhenReady
            isPlaying = handedOff.isPlaying
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
            playerHandedOff = false
        }

        coordinator.consumeHandoffPlayer(playbackKey)?.let { handedOff ->
            bindHandoffPlayer(handedOff)
            return@LaunchedEffect
        }

        val waitForHandoff = isFullscreen || coordinator.hasHandoffPlayer(playbackKey)
        if (waitForHandoff) {
        repeat(96) { attempt ->
            coordinator.consumeHandoffPlayer(playbackKey)?.let { handedOff ->
                bindHandoffPlayer(handedOff)
                return@LaunchedEffect
            }
            if (attempt < 95) {
                delay(32)
            }
        }
        }
        if (player != null) return@LaunchedEffect
        val startPositionMs = coordinator.getPlaybackPosition(playbackKey)
        player = createExoPlayer(
            context = context,
            stream = stream,
            startPositionMs = startPositionMs,
            playbackMetadata = playbackMetadata,
            referer = shotRefererUrl,
        ) {
            isBuffering = false
        }.also {
            it.playWhenReady = playbackEnabled
            positionMs = startPositionMs
            playerHandedOff = false
            if (playbackEnabled) {
                it.play()
            } else {
                it.pause()
            }
        }
    }

    val activePlayer = player
    LaunchedEffect(showDanmakuFeature, danmakuVisible, isFullscreen, activePlayer) {
        if (!showDanmakuFeature || !danmakuVisible || activePlayer == null) {
            fullscreenDanmakuMountAllowed = false
            return@LaunchedEffect
        }
        if (isFullscreen) {
            fullscreenDanmakuMountAllowed = false
            delay(500)
        }
        fullscreenDanmakuMountAllowed = true
    }

    LaunchedEffect(playbackEnabled, activePlayer) {
        val playerRef = activePlayer ?: return@LaunchedEffect
        if (playbackEnabled) {
            if (playerRef.playbackState == Player.STATE_IDLE) {
                playerRef.prepare()
            }
            playerRef.playWhenReady = true
            if (!playerRef.isPlaying || playerRef.playbackState == Player.STATE_READY) {
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
            if (!coordinator.hasHandoffPlayer(playbackKey)) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        return
    }

    WatchHistoryEffect(stream = stream, player = activePlayer, video = historyVideo)

    VideoPlaybackKeepScreenOnEffect(
        playbackKey = playbackKey,
        playbackEnabled = playbackEnabled,
        playerHandedOff = playerHandedOff,
        playWhenReady = playWhenReady,
        playbackState = playbackState,
        coordinator = coordinator,
    )

    val resolvedPlaybackMetadata = playbackMetadata ?: VideoPlaybackMetadata(
        title = "哔哩哔哩视频",
        artist = "哔哩哔哩",
        artworkUrl = "",
        bvid = playbackKey.substringAfter(':', playbackKey),
    )
    DisposableEffect(
        activePlayer,
        playbackKey,
        isFullscreen,
        coordinator.activeKey,
        coordinator.fullscreenKey,
        resolvedPlaybackMetadata,
    ) {
        val isPrimaryPlayback = when {
            isFullscreen -> coordinator.fullscreenKey == playbackKey
            else -> coordinator.activeKey == playbackKey && coordinator.fullscreenKey == null
        }
        if (isPrimaryPlayback) {
            VideoPlaybackMediaBridge.attach(playbackKey, activePlayer, resolvedPlaybackMetadata)
        }
        onDispose {
            if (coordinator.playbackStopping ||
                coordinator.activeKey == null && !isFullscreen
            ) {
                return@onDispose
            }
            val stillPrimary = when {
                isFullscreen -> coordinator.fullscreenKey == playbackKey
                else -> coordinator.activeKey == playbackKey && coordinator.fullscreenKey == null
            }
            val keepSessionForHandoff = when {
                !isFullscreen && coordinator.fullscreenKey == playbackKey -> true
                isFullscreen && coordinator.fullscreenKey == null &&
                    coordinator.activeKey == playbackKey -> true
                else -> false
            }
            if (stillPrimary || keepSessionForHandoff) return@onDispose
            VideoPlaybackMediaBridge.detach(playbackKey)
        }
    }

    DisposableEffect(activePlayer, playbackKey, isFullscreen) {
        val streamTokenAtMount = boundStreamToken
        val pauseHandler = {
            coordinator.savePlaybackPosition(playbackKey, activePlayer.currentPosition)
            activePlayer.playWhenReady = false
            activePlayer.pause()
            isPlaying = false
            playWhenReady = false
        }
        if (isFullscreen) {
            coordinator.registerFullscreenPauseHandler(playbackKey, pauseHandler)
        } else {
            coordinator.registerInlinePauseHandler(playbackKey, pauseHandler)
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                playWhenReady = activePlayer.playWhenReady
                positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
                val duration = activePlayer.duration
                if (duration > 0L) {
                    durationMs = duration
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs = activePlayer.duration.coerceAtLeast(0L)
                    isBuffering = false
                    positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
                }
                if (state == Player.STATE_ENDED) {
                    playWhenReady = activePlayer.playWhenReady
                    onPlaybackEnded?.invoke()
                }
            }

            override fun onPlayWhenReadyChanged(newPlayWhenReady: Boolean, reason: Int) {
                playWhenReady = newPlayWhenReady
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
            if (isFullscreen) {
                coordinator.unregisterFullscreenPauseHandler(playbackKey)
            } else {
                coordinator.unregisterInlinePauseHandler(playbackKey)
            }
            if (playerHandedOff) return@onDispose
            val stoppingPlayback = coordinator.playbackStopping ||
                coordinator.activeKey == null && !isFullscreen
            val streamStillCurrent = streamTokenAtMount != null && streamTokenAtMount == boundStreamToken
            when {
                stoppingPlayback -> {
                    coordinator.releasePlayerOnce(activePlayer)
                    coordinator.releaseHandoffPlayer()
                    VideoPlaybackMediaBridge.detachAll()
                    coordinator.clearReleasedPlayers()
                }
                !streamStillCurrent -> {
                    coordinator.releaseHandoffPlayer()
                    coordinator.releasePlayerOnce(activePlayer)
                }
                !isFullscreen && coordinator.fullscreenKey == playbackKey -> {
                    coordinator.stashPlayer(playbackKey, activePlayer, keepPlaying = true)
                }
                isFullscreen && coordinator.fullscreenKey == null && coordinator.activeKey == playbackKey -> {
                    coordinator.stashPlayer(playbackKey, activePlayer, keepPlaying = true)
                }
                !isFullscreen && coordinator.activeKey == playbackKey -> {
                    coordinator.stashPlayer(playbackKey, activePlayer)
                }
                else -> {
                    VideoPlaybackMediaBridge.detach(playbackKey)
                    coordinator.releasePlayerOnce(activePlayer)
                }
            }
        }
    }

    LaunchedEffect(activePlayer, playbackSpeedOverride, isFullscreen) {
        when {
            playbackSpeedOverride != null -> {
                activePlayer.setPlaybackSpeed(playbackSpeedOverride)
                selectedSpeed = playbackSpeedOverride
            }
            isFullscreen -> {
                activePlayer.setPlaybackSpeed(1f)
                selectedSpeed = 1f
            }
        }
    }

    val positionState by rememberUpdatedState(positionMs)
    val durationState by rememberUpdatedState(durationMs)
    val onScrubPreviewState = rememberUpdatedState<(Long) -> Unit> { target ->
        controlsHideSignal++
        controlsVisible = true
        positionMs = target
    }
    val onScrubCommitState = rememberUpdatedState<(Long) -> Unit> { target ->
        val shouldResume = resumePlaybackAfterScrub
        isScrubbing = false
        controlsHideSignal++
        controlsVisible = true
        positionMs = target
        coordinator.savePlaybackPosition(playbackKey, target)
        activePlayer.seekTo(target)
        if (shouldResume) {
            if (activePlayer.playbackState == Player.STATE_IDLE ||
                activePlayer.playbackState == Player.STATE_ENDED
            ) {
                activePlayer.prepare()
            }
            activePlayer.playWhenReady = true
            activePlayer.play()
        } else {
            activePlayer.playWhenReady = false
        }
        isPlaying = activePlayer.isPlaying
    }

    LaunchedEffect(activePlayer, isPlaying, isBuffering, isScrubbing) {
        while (isActive) {
            if (!isScrubbing) {
                runCatching {
                    positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
                    val duration = activePlayer.duration
                    if (duration > 0L) {
                        durationMs = duration
                    }
                }
            }
            delay(if (isScrubbing) 500 else 250)
        }
    }

    LaunchedEffect(isPlaying, isBuffering, controlsHideSignal) {
        if (!isPlaying || isBuffering) {
            controlsVisible = true
            return@LaunchedEffect
        }
        delay(2_000)
        controlsVisible = false
    }

    val onPlayPauseState = rememberUpdatedState<() -> Unit>({
        controlsHideSignal++
        if (activePlayer.isPlaying) {
            activePlayer.playWhenReady = false
            activePlayer.pause()
            isPlaying = false
        } else {
            if (durationMs > 0 && positionMs >= durationMs - 500) {
                activePlayer.seekTo(0)
            }
            when {
                isFullscreen -> Unit
                coordinator.activeKey != playbackKey -> coordinator.requestInlinePlayback(playbackKey)
            }
            if (activePlayer.playbackState == Player.STATE_IDLE ||
                activePlayer.playbackState == Player.STATE_ENDED
            ) {
                activePlayer.prepare()
            }
            activePlayer.playWhenReady = true
            activePlayer.play()
            isPlaying = activePlayer.isPlaying
        }
    })

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(layerBackdrop),
        ) {
            AndroidView(
                factory = { ctx ->
                    val playerView = LayoutInflater.from(ctx).inflate(R.layout.view_video_player, null, false)
                        as androidx.media3.ui.PlayerView
                    playerView.apply {
                        setKeepContentOnPlayerReset(true)
                        setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                    }
                },
                update = { playerView ->
                    playerView.player = activePlayer
                    playerView.resizeMode = if (isFullscreen && isPortraitPlayback) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showDanmakuFeature && fullscreenDanmakuMountAllowed) {
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
                trackLineHeight = if (isFullscreen) {
                    DanmakuTrackLineHeightDp
                } else {
                    DanmakuCompactTrackLineHeightDp
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f),
            )
        }
        }

        if (controlsEnabled && !showDanmakuSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(5f)
                    .pointerInput(Unit) {
                        detectHorizontalVideoScrub(
                            positionState = { positionState },
                            durationState = { durationState },
                            onDragStart = {
                                resumePlaybackAfterScrub = activePlayer.isPlaying
                                if (activePlayer.isPlaying) {
                                    activePlayer.playWhenReady = false
                                    activePlayer.pause()
                                    isPlaying = false
                                }
                                controlsVisible = true
                                controlsHideSignal++
                            },
                            onScrubbingChange = { isScrubbing = it },
                            onScrubPreview = { onScrubPreviewState.value(it) },
                            onScrubCommit = { onScrubCommitState.value(it) },
                        )
                    }
                    .pointerInput(isFullscreen, controlsEnabled) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                                controlsHideSignal++
                            },
                            onDoubleTap = { onPlayPauseState.value() },
                        )
                    },
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
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(10f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 60.dp, top = 20.dp, end = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    VideoOverlayTextButton(
                        text = "关闭",
                        onClick = onCloseFullscreen,
                        modifier = Modifier
                            .widthIn(min = 54.dp)
                            .height(VideoControlBarHeight),
                    )
                    Text(
                        text = resolvedPlaybackMetadata.title,
                        color = Color.White,
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else if (showFullscreenButton) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(10f),
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
            visible = controlsEnabled && controlsVisible && !showDanmakuSettings,
            enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(10f)
                .padding(start = 6.dp, end = 6.dp, bottom = if (isFullscreen) 40.dp else 8.dp)
                .fillMaxWidth(),
        ) {
            VideoControls(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                speed = selectedSpeed,
                isScrubbing = isScrubbing,
                onScrubbingChange = { scrubbing ->
                    if (!scrubbing) {
                        isScrubbing = false
                    } else if (!isScrubbing) {
                        resumePlaybackAfterScrub = activePlayer.isPlaying
                        if (activePlayer.isPlaying) {
                            activePlayer.playWhenReady = false
                            activePlayer.pause()
                            isPlaying = false
                        }
                        isScrubbing = true
                    }
                },
                onScrubPreview = { onScrubPreviewState.value(it) },
                onScrubCommit = { onScrubCommitState.value(it) },
                onPlayPause = { onPlayPauseState.value() },
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
                    controlsVisible = false
                    showDanmakuSettings = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VideoControlBarHeight),
            )
        }

        val scrubProgressFraction = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        VideoScrubPreviewOverlay(
            visible = isScrubbing,
            positionMs = positionMs,
            durationMs = durationMs,
            progressFraction = scrubProgressFraction,
            videoShot = resolvedVideoShot,
            previewAspectRatio = resolvedScrubPreviewAspectRatio,
            refererUrl = shotRefererUrl,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
                .padding(horizontal = 6.dp)
                .padding(bottom = (if (isFullscreen) 40.dp else 8.dp) + VideoControlBarHeight + 10.dp),
        )

        DanmakuSettingsOverlay(
            visible = showDanmakuSettings,
            settings = danmakuSettings,
            onSettingsChange = coordinator::updateDanmakuSettings,
            onDismiss = { showDanmakuSettings = false },
            backdrop = layerBackdrop,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(30f),
        )
    }
}

@Composable
private fun VideoControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    onPlayPause: () -> Unit,
    onScrubPreview: (Long) -> Unit,
    onScrubCommit: (Long) -> Unit,
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
    val onScrubPreviewState by rememberUpdatedState(onScrubPreview)
    val onScrubCommitState by rememberUpdatedState(onScrubCommit)
    val onScrubbingChangeState by rememberUpdatedState(onScrubbingChange)

    Box(
        modifier = modifier
            .border(VideoControlBorderWidth, VideoControlBorderColor, VideoControlCapsuleShape)
            .clip(VideoControlCapsuleShape),
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
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
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
                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .sizeIn(minWidth = 28.dp, minHeight = 28.dp)
                        .pointerInput(onDanmakuToggle, onDanmakuLongPress) {
                            detectTapGestures(
                                onTap = { onDanmakuToggle() },
                                onLongPress = { onDanmakuLongPress() },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "弹",
                        color = if (danmakuVisible) Color.White else Color.White.copy(alpha = 0.42f),
                        fontWeight = if (danmakuVisible) FontWeight.Bold else FontWeight.Normal,
                        style = TextStyle(fontSize = 14.sp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(durationState) {
                        detectHorizontalVideoScrub(
                            positionState = { positionState },
                            durationState = { durationState },
                            onDragStart = { onScrubbingChangeState(true) },
                            onScrubbingChange = onScrubbingChangeState,
                            onScrubPreview = { onScrubPreviewState(it) },
                            onScrubCommit = { onScrubCommitState(it) },
                        )
                    },
            )
            Text(
                text = speedLabel(speed),
                color = Color.White,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
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
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
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

private suspend fun PointerInputScope.detectHorizontalVideoScrub(
    positionState: () -> Long,
    durationState: () -> Long,
    onDragStart: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    onScrubPreview: (Long) -> Unit,
    onScrubCommit: (Long) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val anchorX = down.position.x
        val startY = down.position.y
        val anchorPosition = positionState()
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
            val duration = durationState()
            if (!dragging && dx > slop && dx > dy && duration > 0L && width > 0f) {
                dragging = true
                onDragStart()
                onScrubbingChange(true)
            }
            if (dragging && duration > 0L && width > 0f && change.pressed) {
                change.consume()
                val deltaMs = ((change.position.x - anchorX) / width * duration).toLong()
                val newPosition = (anchorPosition + deltaMs).coerceIn(0L, duration)
                if (newPosition != lastSeekPosition) {
                    lastSeekPosition = newPosition
                    onScrubPreview(newPosition)
                }
            }
            if (event.changes.all { it.changedToUpIgnoreConsumed() }) break
        }
        if (dragging) {
            onScrubCommit(lastSeekPosition)
            onScrubbingChange(false)
        }
    }
}
