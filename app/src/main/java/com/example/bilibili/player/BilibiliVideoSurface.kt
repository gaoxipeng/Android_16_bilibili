package com.example.bilibili.player

import android.view.LayoutInflater
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import com.example.bilibili.ui.components.OverlayFadeTransition
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
import androidx.media3.common.PlaybackException
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

private val VideoProgressLineWidth = 3.dp

private val VideoControlBarHeight = 34.dp
private val VideoControlBarBottomGap = 6.dp
private val VideoControlBorderWidth = 0.5.dp
private val VideoControlBorderColor = Color(0x80999999)

@Composable
fun BilibiliVideoSurface(
    playbackKey: String,
    contentPlaybackKey: String = playbackKey,
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
    onStreamSourceError: (() -> Unit)? = null,
    showLoadingIndicator: Boolean = true,
    onLoadingStateChange: ((Boolean) -> Unit)? = null,
    autoPlayWhenReady: Boolean = false,
) {
    val context = LocalContext.current
    val layerBackdrop = backdrop as? LayerBackdrop ?: rememberLayerBackdrop()
    val onStreamSourceErrorState = rememberUpdatedState(onStreamSourceError)
    val streamToken = "${stream.cid}:${stream.videoUrl}:${stream.audioUrl.orEmpty()}"
    val handoffLookupKey = remember(playbackKey, historyVideo?.bvid, historyVideo?.cid) {
        historyVideo?.let { coordinator.handoffPlaybackKeyForVideo(it) } ?: playbackKey
    }
    val initialHandoffPlayer = remember(playbackKey, handoffLookupKey) {
        coordinator.consumeHandoffPlayer(handoffLookupKey)
    }
    var boundStreamToken by remember(playbackKey) {
        mutableStateOf(if (initialHandoffPlayer != null) streamToken else null)
    }
    var boundContentPlaybackKey by remember(playbackKey) {
        mutableStateOf(if (initialHandoffPlayer != null) contentPlaybackKey else null)
    }
    var positionMs by remember(contentPlaybackKey, streamToken) {
        mutableLongStateOf(initialHandoffPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L)
    }
    var durationMs by remember(contentPlaybackKey, streamToken) {
        mutableLongStateOf(initialHandoffPlayer?.duration?.takeIf { it > 0L } ?: 0L)
    }
    var isPlaying by remember(playbackKey) {
        mutableStateOf(initialHandoffPlayer?.isPlaying ?: true)
    }
    var isBuffering by remember(playbackKey) {
        mutableStateOf(initialHandoffPlayer?.playbackState == Player.STATE_BUFFERING || initialHandoffPlayer == null)
    }
    var playbackState by remember(playbackKey) {
        mutableIntStateOf(initialHandoffPlayer?.playbackState ?: Player.STATE_IDLE)
    }
    var playWhenReady by remember(playbackKey) {
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
    var showEpisodePicker by remember(playbackKey) { mutableStateOf(false) }
    val showDanmakuFeature = danmakuEnabled && danmakuCid > 0L && loadDanmaku != null
    val danmakuVisible = coordinator.danmakuVisible
    val danmakuSettings = coordinator.danmakuSettings
    val episodePicker = coordinator.episodePickerFor(playbackKey).takeIf { isFullscreen }
    val showEpisodePickerButton = episodePicker?.isAvailable == true

    LaunchedEffect(danmakuEnabled, danmakuCid, loadDanmaku) {
        val loader = loadDanmaku
        if (!danmakuEnabled || danmakuCid <= 0L || loader == null) {
            danmakuItems = emptyList()
            return@LaunchedEffect
        }
        danmakuItems = coordinator.cachedDanmaku(danmakuCid) { loader(danmakuCid) }
    }

    var player by remember(playbackKey) { mutableStateOf<ExoPlayer?>(initialHandoffPlayer) }
    var playerHandedOff by remember(playbackKey) { mutableStateOf(false) }
    var sourceErrorReported by remember(playbackKey, streamToken) { mutableStateOf(false) }
    var isPortraitPlayback by remember(playbackKey) { mutableStateOf(portraitVideo) }
    var playerSizeKnown by remember(playbackKey) { mutableStateOf(false) }
    val currentContentPlaybackKey = rememberUpdatedState(contentPlaybackKey)

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
        playbackKey = contentPlaybackKey,
        playbackMetadata = playbackMetadata,
    )
    val resolvedVideoShot = videoShot
        ?: stream.cid.takeIf { it > 0L }?.let { coordinator.cachedVideoShot(it) }
    val resolvedScrubPreviewAspectRatio =
        scrubPreviewAspectRatio ?: coordinator.cachedVideoAspectRatio(contentPlaybackKey)

    LaunchedEffect(contentPlaybackKey, scrubPreviewAspectRatio) {
        coordinator.cacheVideoAspectRatio(contentPlaybackKey, scrubPreviewAspectRatio)
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
            coordinator.stashPlayer(
                key = playbackKey,
                player = currentPlayer,
                keepPlaying = true,
                positionKey = currentContentPlaybackKey.value,
            )
            player = null
            playerHandedOff = true
        }
        coordinator.registerHandoffPrepareHandler(handoffHandler)
        onDispose {
            coordinator.unregisterHandoffPrepareHandler(handoffHandler)
        }
    }

    LaunchedEffect(
        playbackKey,
        contentPlaybackKey,
        stream.cid,
        stream.videoUrl,
        stream.audioUrl,
        shotRefererUrl,
        playbackEnabled,
        playbackMetadata?.title,
        playbackMetadata?.artworkUrl,
        playbackMetadata?.bvid,
        isFullscreen,
        coordinator.fullscreenKey,
        historyVideo?.bvid,
        historyVideo?.cid,
        handoffLookupKey,
    ) {
        if (!isFullscreen && coordinator.fullscreenKey == playbackKey) {
            return@LaunchedEffect
        }
        if (!isFullscreen && player == null && coordinator.fullscreenKey != playbackKey) {
            coordinator.consumeHandoffPlayer(handoffLookupKey)?.let { handedOff ->
                if (playbackEnabled) {
                    handedOff.safePrepareAndPlay()
                } else {
                    coordinator.savePlaybackPosition(contentPlaybackKey, handedOff.safeCurrentPosition())
                    handedOff.safePause()
                }
                positionMs = handedOff.safeCurrentPosition()
                durationMs = runCatching { handedOff.duration.coerceAtLeast(0L) }.getOrDefault(0L)
                playbackState = handedOff.playbackState
                playWhenReady = handedOff.playWhenReady
                isPlaying = handedOff.isPlaying
                isBuffering = handedOff.playbackState == Player.STATE_BUFFERING
                player = handedOff
                playerHandedOff = false
                boundStreamToken = streamToken
                boundContentPlaybackKey = contentPlaybackKey
                return@LaunchedEffect
            }
        }

        if (player != null && boundStreamToken == streamToken && boundContentPlaybackKey == contentPlaybackKey) {
            return@LaunchedEffect
        }

        val existingPlayer = player
        if (existingPlayer != null) {
            if (boundStreamToken == streamToken && boundContentPlaybackKey == contentPlaybackKey) {
                return@LaunchedEffect
            }
            val boundCid = boundStreamToken?.substringBefore(':')?.toLongOrNull() ?: 0L
            val samePlaybackContent = boundContentPlaybackKey == contentPlaybackKey ||
                (boundCid > 0L && boundCid == stream.cid && stream.cid > 0L)
            if (samePlaybackContent && existingPlayer.playbackState != Player.STATE_IDLE) {
                boundStreamToken = streamToken
                boundContentPlaybackKey = contentPlaybackKey
                return@LaunchedEffect
            }
            // Keep the ExoPlayer (and therefore its MediaSession) alive while changing episodes.
            // Recreating it makes Android briefly remove the notification's custom controls.
            coordinator.releaseHandoffPlayer()
            boundContentPlaybackKey
                ?.takeIf { it != contentPlaybackKey }
                ?.let { previousContentKey ->
                    coordinator.savePlaybackPosition(
                        previousContentKey,
                        existingPlayer.currentPosition,
                    )
                }
            val startPositionMs = if (boundContentPlaybackKey == contentPlaybackKey) {
                existingPlayer.currentPosition.coerceAtLeast(0L)
            } else {
                0L
            }
            existingPlayer.setMediaSource(
                buildVideoMediaSource(
                    context = context,
                    stream = stream,
                    playbackMetadata = playbackMetadata,
                    referer = shotRefererUrl,
                ),
                startPositionMs,
            )
            existingPlayer.prepare()
            existingPlayer.playWhenReady = playbackEnabled
            if (playbackEnabled) {
                existingPlayer.play()
            } else {
                existingPlayer.pause()
            }
            boundStreamToken = streamToken
            boundContentPlaybackKey = contentPlaybackKey
            playerHandedOff = false
            positionMs = startPositionMs
            durationMs = 0L
            playbackState = existingPlayer.playbackState
            isPlaying = existingPlayer.isPlaying
            isBuffering = true
            return@LaunchedEffect
        }

        playerHandedOff = false
        boundStreamToken = streamToken
        boundContentPlaybackKey = contentPlaybackKey

        fun bindHandoffPlayer(handedOff: ExoPlayer) {
            if (playbackEnabled) {
                handedOff.playWhenReady = true
                if (handedOff.playbackState == Player.STATE_IDLE) {
                    handedOff.prepare()
                }
                handedOff.play()
            } else {
                coordinator.savePlaybackPosition(contentPlaybackKey, handedOff.currentPosition)
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

        coordinator.consumeHandoffPlayer(handoffLookupKey)?.let { handedOff ->
            bindHandoffPlayer(handedOff)
            return@LaunchedEffect
        }

        val waitForHandoff = isFullscreen ||
            coordinator.hasHandoffPlayer(playbackKey) ||
            coordinator.hasHandoffPlayer(handoffLookupKey)
        if (waitForHandoff) {
        repeat(96) { attempt ->
            coordinator.consumeHandoffPlayer(handoffLookupKey)?.let { handedOff ->
                bindHandoffPlayer(handedOff)
                return@LaunchedEffect
            }
            if (attempt < 95) {
                delay(32)
            }
        }
        }
        val startPositionMs = coordinator.getPlaybackPosition(contentPlaybackKey)
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
    val activePlayerState = rememberUpdatedState(activePlayer)
    val hasPendingHandoff = coordinator.hasHandoffPlayer(playbackKey) ||
        coordinator.hasHandoffPlayer(handoffLookupKey)
    val surfaceLoading = (activePlayer == null && !hasPendingHandoff) || isBuffering
    LaunchedEffect(surfaceLoading, onLoadingStateChange) {
        onLoadingStateChange?.invoke(surfaceLoading)
    }
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

    LaunchedEffect(playbackEnabled, activePlayer, coordinator.activeKey, coordinator.fullscreenKey, playbackKey, isFullscreen, autoPlayWhenReady, isBuffering) {
        val playerRef = activePlayerState.value ?: return@LaunchedEffect
        if (!playbackEnabled) {
            coordinator.savePlaybackPosition(contentPlaybackKey, playerRef.safeCurrentPosition())
            playerRef.safePause()
            isPlaying = false
            playWhenReady = false
            return@LaunchedEffect
        }
        val isPrimaryPlayback = when {
            isFullscreen -> coordinator.fullscreenKey == playbackKey
            else -> coordinator.activeKey == playbackKey && coordinator.fullscreenKey == null
        }
        if (!isPrimaryPlayback && !autoPlayWhenReady) return@LaunchedEffect
        playerRef.safePrepareAndPlay()
        isPlaying = playerRef.isPlaying
        playWhenReady = playerRef.playWhenReady
    }

    if (activePlayer == null) {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (showLoadingIndicator && !hasPendingHandoff) {
                VideoPlayerLoadingIndicator()
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
        bvid = contentPlaybackKey.substringAfter(':', contentPlaybackKey),
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

    LaunchedEffect(
        activePlayer,
        playbackKey,
        isFullscreen,
        coordinator.activeKey,
        coordinator.fullscreenKey,
        resolvedPlaybackMetadata.title,
        resolvedPlaybackMetadata.artworkUrl,
        resolvedPlaybackMetadata.bvid,
    ) {
        val isPrimaryPlayback = when {
            isFullscreen -> coordinator.fullscreenKey == playbackKey
            else -> coordinator.activeKey == playbackKey && coordinator.fullscreenKey == null
        }
        if (!isPrimaryPlayback) return@LaunchedEffect
        VideoPlaybackMediaBridge.setPlaybackMetadataProvider(playbackKey) {
            resolvedPlaybackMetadata
        }
        VideoPlaybackMediaBridge.refreshPlaybackMetadata(playbackKey)
    }

    DisposableEffect(playbackKey) {
        onDispose {
            VideoPlaybackMediaBridge.setPlaybackMetadataProvider(playbackKey, null)
        }
    }

    DisposableEffect(activePlayer, playbackKey, isFullscreen) {
        val pauseHandler = {
            val currentPlayer = activePlayerState.value
            if (currentPlayer != null && currentPlayer === player) {
                coordinator.savePlaybackPosition(
                    currentContentPlaybackKey.value,
                    currentPlayer.safeCurrentPosition(),
                )
                currentPlayer.safePause()
                isPlaying = false
                playWhenReady = false
            }
        }
        if (isFullscreen) {
            coordinator.registerFullscreenPauseHandler(playbackKey, pauseHandler)
        } else {
            coordinator.registerInlinePauseHandler(playbackKey, pauseHandler)
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                val livePlayer = activePlayerState.value ?: return
                runCatching {
                    isPlaying = playing
                    playWhenReady = livePlayer.playWhenReady
                    positionMs = livePlayer.currentPosition.coerceAtLeast(0L)
                    val duration = livePlayer.duration
                    if (duration > 0L) {
                        durationMs = duration
                    }
                    if (playing && livePlayer.playbackState == Player.STATE_READY) {
                        isBuffering = false
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                val livePlayer = activePlayerState.value ?: return
                runCatching {
                    playbackState = state
                    isBuffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) {
                        durationMs = livePlayer.duration.coerceAtLeast(0L)
                        isBuffering = false
                        positionMs = livePlayer.currentPosition.coerceAtLeast(0L)
                        VideoPlaybackMediaBridge.refreshPlaybackMetadata(playbackKey)
                    }
                    if (state == Player.STATE_ENDED) {
                        playWhenReady = livePlayer.playWhenReady
                        onPlaybackEnded?.invoke()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (sourceErrorReported) return
                sourceErrorReported = true
                onStreamSourceErrorState.value?.invoke()
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
        activePlayerState.value?.let { livePlayer ->
            runCatching {
                livePlayer.videoSize.let { videoSize ->
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
                livePlayer.addListener(listener)
                when (livePlayer.playbackState) {
                    Player.STATE_READY -> {
                        isBuffering = false
                        durationMs = livePlayer.duration.coerceAtLeast(0L)
                        positionMs = livePlayer.currentPosition.coerceAtLeast(0L)
                    }
                    Player.STATE_BUFFERING -> isBuffering = true
                    else -> isBuffering = false
                }
            }
        }
        onDispose {
            val disposedPlayer = activePlayerState.value
            if (disposedPlayer != null) {
                coordinator.savePlaybackPosition(
                    currentContentPlaybackKey.value,
                    disposedPlayer.safeCurrentPosition(),
                )
                runCatching { disposedPlayer.removeListener(listener) }
            }
            if (isFullscreen) {
                coordinator.unregisterFullscreenPauseHandler(playbackKey)
            } else {
                coordinator.unregisterInlinePauseHandler(playbackKey)
            }
            if (playerHandedOff) return@onDispose
            val preserveForReentry = coordinator.shouldPreserveDetailHandoff(playbackKey)
            val stoppingPlayback = coordinator.playbackStopping ||
                coordinator.activeKey == null && !isFullscreen && !preserveForReentry
            when {
                preserveForReentry -> {
                    disposedPlayer?.let { livePlayer ->
                        coordinator.stashPlayer(
                            key = playbackKey,
                            player = livePlayer,
                            positionKey = currentContentPlaybackKey.value,
                        )
                    }
                    coordinator.clearDetailHandoffPreserve()
                }
                stoppingPlayback -> {
                    coordinator.releasePlayerOnce(disposedPlayer)
                    coordinator.releaseHandoffPlayer()
                    VideoPlaybackMediaBridge.detachAll()
                    coordinator.clearReleasedPlayers()
                }
                !isFullscreen && coordinator.fullscreenKey == playbackKey -> {
                    disposedPlayer?.let { livePlayer ->
                        coordinator.stashPlayer(
                            key = playbackKey,
                            player = livePlayer,
                            keepPlaying = true,
                            positionKey = currentContentPlaybackKey.value,
                        )
                    }
                }
                isFullscreen && coordinator.fullscreenKey == null && coordinator.activeKey == playbackKey -> {
                    disposedPlayer?.let { livePlayer ->
                        coordinator.stashPlayer(
                            key = playbackKey,
                            player = livePlayer,
                            keepPlaying = true,
                            positionKey = currentContentPlaybackKey.value,
                        )
                    }
                }
                !isFullscreen && coordinator.activeKey == playbackKey -> {
                    disposedPlayer?.let { livePlayer ->
                        coordinator.stashPlayer(
                            key = playbackKey,
                            player = livePlayer,
                            positionKey = currentContentPlaybackKey.value,
                        )
                    }
                }
                else -> {
                    VideoPlaybackMediaBridge.detach(playbackKey)
                    coordinator.releasePlayerOnce(disposedPlayer)
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
        coordinator.savePlaybackPosition(contentPlaybackKey, target)
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
            .background(Color.Black)
            .clipToBounds(),
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
                    runCatching {
                        playerView.player = activePlayerState.value?.takeIf { it === player }
                    }
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

        if (controlsEnabled && !showDanmakuSettings && !showEpisodePicker) {
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

        if (isBuffering && showLoadingIndicator) {
            VideoPlayerLoadingIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (isFullscreen) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = OverlayFadeTransition.enter,
                exit = OverlayFadeTransition.exit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(10f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (isPortraitPlayback) 16.dp else 60.dp,
                            top = 20.dp,
                            end = 12.dp,
                            bottom = 12.dp,
                        ),
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
                enter = OverlayFadeTransition.enter,
                exit = OverlayFadeTransition.exit,
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
            visible = controlsEnabled && controlsVisible && !showDanmakuSettings && !showEpisodePicker,
            enter = OverlayFadeTransition.enter,
            exit = OverlayFadeTransition.exit,
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
                showEpisodePickerButton = showEpisodePickerButton,
                onEpisodePickerClick = {
                    controlsHideSignal++
                    controlsVisible = false
                    showEpisodePicker = true
                },
                isFullscreen = isFullscreen,
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

        FullscreenEpisodePickerOverlay(
            visible = showEpisodePicker,
            pickerState = episodePicker,
            onDismiss = { showEpisodePicker = false },
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
    showEpisodePickerButton: Boolean = false,
    onEpisodePickerClick: () -> Unit = {},
    isFullscreen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val controlSpacing = if (isFullscreen) 12.dp else 6.dp
    val playToDanmakuExtra = when {
        isFullscreen -> 6.dp
        else -> 4.dp
    }
    val rightControlExtra = if (isFullscreen) 6.dp else 0.dp
    val speedToRemainingExtra = if (isFullscreen) rightControlExtra else 8.dp
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
            horizontalArrangement = Arrangement.spacedBy(controlSpacing),
        ) {
            Text(
                text = formatVideoTime(positionMs),
                modifier = Modifier.widthIn(min = 42.dp),
                color = Color.White,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
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
                if (playToDanmakuExtra > 0.dp) {
                    Spacer(Modifier.width(playToDanmakuExtra))
                }
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
            if (showEpisodePickerButton) {
                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .sizeIn(minWidth = 40.dp, minHeight = 28.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onEpisodePickerClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "选集",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontSize = 14.sp),
                    )
                }
            }
            if (showEpisodePickerButton && rightControlExtra > 0.dp) {
                Spacer(Modifier.width(rightControlExtra))
            }
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
            if (speedToRemainingExtra > 0.dp) {
                Spacer(Modifier.width(speedToRemainingExtra))
            }
            Text(
                text = formatVideoTime((durationMs - positionMs).coerceAtLeast(0L)),
                modifier = Modifier.widthIn(min = 42.dp),
                color = Color.White,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
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
