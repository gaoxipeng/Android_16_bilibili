package com.example.bilibili.ui.screens

import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.bilibili.R
import com.example.bilibili.data.BiliAuthorRelation
import com.example.bilibili.data.BiliDanmakuEmoticon
import com.example.bilibili.data.BiliDanmakuItem
import com.example.bilibili.data.emoticonUrlMap
import com.example.bilibili.data.BiliLiveRankUser
import com.example.bilibili.data.BiliLivePlayResult
import com.example.bilibili.data.BiliLiveRoom
import com.example.bilibili.data.BiliLiveRoomDetail
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.data.DanmakuSettings
import com.example.bilibili.data.LiveDanmakuClient
import com.example.bilibili.player.DanmakuSettingsOverlay
import com.example.bilibili.player.FullscreenOrientationEffect
import com.example.bilibili.player.LightContentStatusBarEffect
import com.example.bilibili.player.LiveDanmakuOverlay
import com.example.bilibili.player.isPortraitVideoSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.graphics.Color as AndroidColor
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.player.buildLiveMediaSource
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.theme.BiliPink
import com.example.bilibili.ui.components.BiliCommentText
import com.example.bilibili.ui.components.BilibiliFollowButton
import com.example.bilibili.ui.components.RemoteImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val LiveRoomHeaderHeight = 56.dp
private const val LIVE_ONLINE_REFRESH_INTERVAL_MS = 5_000L
private const val LIVE_PLAYER_CONTROLS_HIDE_DELAY_MS = 5_000L
private const val LIVE_CLEAR_SCREEN_SWIPE_THRESHOLD_PX = 72f

private data class LiveRoomHeaderFollowState(
    val relation: BiliAuthorRelation,
    val loading: Boolean,
    val onClick: () -> Unit,
)

private fun BiliDanmakuItem.withLiveEmoticonFallback(
    liveEmoticons: Map<String, BiliDanmakuEmoticon>,
): BiliDanmakuItem {
    if (content.isBlank() || liveEmoticons.isEmpty()) return this
    val merged = linkedMapOf<String, BiliDanmakuEmoticon>()
    merged.putAll(emoticons)
    val spec = liveEmoticonPhraseCandidates(content)
        .firstNotNullOfOrNull { phrase -> liveEmoticons[phrase] }
        ?: return if (merged.isEmpty()) this else copy(emoticons = merged)
    liveEmoticonPhraseCandidates(content).forEach { phrase ->
        merged[phrase] = spec
    }
    return copy(emoticons = merged)
}

private fun liveEmoticonPhraseCandidates(phrase: String): List<String> {
    val trimmed = phrase.trim()
    if (trimmed.isBlank()) return emptyList()
    return listOf(
        trimmed,
        trimmed.removeSurrounding("[", "]"),
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) trimmed else "[$trimmed]",
    ).filter { it.isNotBlank() }.distinct()
}

@Composable
fun LiveRoomScreen(
    room: BiliLiveRoom,
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    coordinator: VideoPlaybackCoordinator,
    onBack: () -> Unit,
    onOpenAnchorProfile: (mid: Long, name: String, face: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember(room.roomId) { mutableStateOf(true) }
    var error by remember(room.roomId) { mutableStateOf<String?>(null) }
    var detail by remember(room.roomId) { mutableStateOf<BiliLiveRoomDetail?>(null) }
    var playInfo by remember(room.roomId) { mutableStateOf<BiliLivePlayResult?>(null) }
    var showDanmakuSettings by remember(room.roomId) { mutableStateOf(false) }
    var onlineCount by remember(room.roomId) { mutableStateOf(0L) }
    val danmakuEnabled = coordinator.danmakuVisible
    val danmakuSettings = coordinator.danmakuSettings
    var danmakuInput by remember(room.roomId) { mutableStateOf("") }
    val danmakuItems = remember(room.roomId) { mutableStateListOf<BiliDanmakuItem>() }
    var reloadToken by remember(room.roomId) { mutableIntStateOf(0) }
    var isPortraitStream by remember(room.roomId) { mutableStateOf<Boolean?>(room.isPortrait) }
    var isFullscreen by remember(room.roomId) { mutableStateOf(false) }
    var rankUsers by remember(room.roomId) { mutableStateOf<List<BiliLiveRankUser>>(emptyList()) }
    var livePlayerOverlayVisible by remember(room.roomId) { mutableStateOf(true) }
    var livePlayerOverlayHideSignal by remember(room.roomId) { mutableIntStateOf(0) }
    var liveInfoCleared by remember(room.roomId) { mutableStateOf(false) }
    var anchorRelation by remember(room.roomId) { mutableStateOf(BiliAuthorRelation()) }
    var followLoading by remember(room.roomId) { mutableStateOf(false) }
    var liveEmoticons by remember(room.roomId) {
        mutableStateOf<Map<String, BiliDanmakuEmoticon>>(emptyMap())
    }
    val liveDanmakuItems = remember(danmakuItems.size, liveEmoticons) {
        danmakuItems.map { item -> item.withLiveEmoticonFallback(liveEmoticons) }
    }
    val recentChatItems = liveDanmakuItems
        .asReversed()
        .filter { it.content.isNotBlank() }
        .take(80)
        .reversed()

    val exoPlayer = remember(room.roomId) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    val danmakuClient = remember(room.roomId) {
        LiveDanmakuClient(api = api, roomId = room.roomId, credential = credential)
    }

    BackHandler {
        when {
            showDanmakuSettings -> showDanmakuSettings = false
            isFullscreen -> isFullscreen = false
            liveInfoCleared -> liveInfoCleared = false
            else -> onBack()
        }
    }

    DisposableEffect(room.roomId) {
        coordinator.pauseForOverlay()
        onDispose {
            danmakuClient.disconnect()
            exoPlayer.release()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width <= 0 || videoSize.height <= 0) return
                isPortraitStream = isPortraitVideoSize(
                    width = videoSize.width,
                    height = videoSize.height,
                    rotationDegrees = videoSize.unappliedRotationDegrees,
                )
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(room.roomId, credential) {
        liveEmoticons = api.getLiveEmoticons(room.roomId, credential)
    }

    FullscreenOrientationEffect(
        enabled = isFullscreen,
        portraitVideo = isPortraitStream,
    )
    LightContentStatusBarEffect(enabled = true)

    LaunchedEffect(room.roomId, reloadToken) {
        val isFirstLoad = playInfo == null
        if (isFirstLoad) loading = true
        error = null
        runCatching {
            val roomDetail = api.getLiveRoomDetail(room.roomId, credential)
                ?: throw IllegalStateException("无法获取直播间信息")
            detail = roomDetail
            roomDetail.isPortrait?.let { isPortraitStream = it }
            val play = api.getLivePlayInfo(
                roomId = room.roomId,
                qn = LIVE_MAX_QN,
                credential = credential,
            )
                ?: throw IllegalStateException(
                    if (roomDetail.isLive) "当前直播间不支持该清晰度" else "主播暂未开播",
                )
            playInfo = play
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaSource(buildLiveMediaSource(context, play.streamUrl, room.roomId))
            exoPlayer.prepare()
            exoPlayer.play()
            danmakuClient.connect(play)
        }.onFailure {
            error = it.message ?: "加载失败"
        }
        loading = false
    }

    suspend fun refreshOnlineRank() {
        val anchorUid = detail?.anchorUid ?: return
        val result = api.getLiveOnlineGoldRank(room.roomId, anchorUid, credential)
        if (result.onlineNum > 0L) {
            onlineCount = result.onlineNum
        }
        if (result.users.isNotEmpty()) {
            rankUsers = result.users
        }
    }

    LaunchedEffect(room.roomId, detail?.anchorUid, reloadToken) {
        refreshOnlineRank()
    }

    LaunchedEffect(room.roomId) {
        danmakuClient.danmaku.collectLatest { item ->
            danmakuItems += item
            if (danmakuItems.size > 500) {
                repeat(danmakuItems.size - 400) {
                    if (danmakuItems.isNotEmpty()) danmakuItems.removeAt(0)
                }
            }
        }
    }

    LaunchedEffect(room.roomId, detail?.anchorUid, playInfo?.isLive) {
        if (playInfo?.isLive != true || detail?.anchorUid == null) return@LaunchedEffect
        while (true) {
            delay(LIVE_ONLINE_REFRESH_INTERVAL_MS)
            refreshOnlineRank()
        }
    }

    LaunchedEffect(loading, livePlayerOverlayHideSignal, showDanmakuSettings) {
        if (loading || showDanmakuSettings) {
            livePlayerOverlayVisible = true
            return@LaunchedEffect
        }
        delay(LIVE_PLAYER_CONTROLS_HIDE_DELAY_MS)
        livePlayerOverlayVisible = false
    }

    val onLivePlayerOverlayTap: () -> Unit = {
        livePlayerOverlayVisible = !livePlayerOverlayVisible
        livePlayerOverlayHideSignal++
    }
    val onLivePlayerControlInteraction: () -> Unit = {
        livePlayerOverlayHideSignal++
    }

    LaunchedEffect(room.roomId) {
        danmakuClient.onlineRankTop3.collectLatest { ranks ->
            if (ranks.isNotEmpty()) rankUsers = ranks
        }
    }

    val view = LocalView.current
    DisposableEffect(playInfo?.isLive) {
        val previous = view.keepScreenOn
        view.keepScreenOn = playInfo?.isLive == true
        onDispose {
            view.keepScreenOn = previous
        }
    }

    val isLayoutKnown = isPortraitStream != null || playInfo != null
    val isPortraitLive = !isFullscreen && isPortraitStream == true
    val supportsClearScreen = !isFullscreen && isLayoutKnown
    val showLiveInfoUi = supportsClearScreen && !liveInfoCleared
    val showLiveDanmakuOverlay = danmakuEnabled && !liveInfoCleared
    val showLivePlayerControls = livePlayerOverlayVisible && !liveInfoCleared

    LaunchedEffect(room.roomId, isFullscreen, isPortraitStream) {
        liveInfoCleared = false
    }

    val videoResizeMode = if (isPortraitLive) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    LaunchedEffect(isPortraitStream) {
        if (isPortraitStream == true) {
            isFullscreen = false
        }
    }

    val livePlayerBackdrop = rememberLayerBackdrop()

    fun sendDanmaku(message: String) {
        scope.launch {
            val cred = credential ?: return@launch
            val targetRoomId = playInfo?.realRoomId?.takeIf { it > 0L } ?: room.roomId
            if (api.sendLiveDanmaku(targetRoomId, message, cred)) {
                danmakuInput = ""
            } else {
                Toast.makeText(context, "弹幕发送失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val liveRoomTitle = detail?.title?.ifBlank { room.title } ?: room.title
    val anchorUid = detail?.anchorUid?.takeIf { it > 0L } ?: playInfo?.anchorUid ?: 0L
    val anchorName = detail?.userName?.ifBlank { room.userName } ?: room.userName
    val anchorFace = detail?.userFace?.ifBlank { room.userFace } ?: room.userFace
    val onAnchorProfileClick = if (anchorUid > 0L) {
        { onOpenAnchorProfile(anchorUid, anchorName, anchorFace) }
    } else {
        null
    }
    val myMid = credential?.dedeUserId?.toLongOrNull()
    val showFollowButton = credential != null && anchorUid > 0L && myMid != anchorUid
    val liveRoomReferer = "https://live.bilibili.com/${room.roomId}"

    LaunchedEffect(anchorUid, credential?.dedeUserId, myMid) {
        val cred = credential
        if (cred == null || anchorUid <= 0L || myMid == anchorUid) {
            anchorRelation = BiliAuthorRelation()
            return@LaunchedEffect
        }
        anchorRelation = api.getUserRelation(
            mid = anchorUid,
            credential = cred,
            referer = liveRoomReferer,
        )
    }

    fun onFollowAnchorClick() {
        val cred = credential ?: return
        if (anchorUid <= 0L || followLoading) return
        scope.launch {
            followLoading = true
            val previousRelation = anchorRelation
            val targetFollow = !anchorRelation.following
            anchorRelation = anchorRelation.copy(following = targetFollow)
            runCatching {
                api.modifyFollow(
                    mid = anchorUid,
                    follow = targetFollow,
                    credential = cred,
                    referer = liveRoomReferer,
                ).getOrThrow()
                anchorRelation = api.getUserRelation(
                    mid = anchorUid,
                    credential = cred,
                    referer = liveRoomReferer,
                )
            }.onFailure {
                anchorRelation = previousRelation
                Toast.makeText(context, it.message ?: "关注失败", Toast.LENGTH_SHORT).show()
            }
            followLoading = false
        }
    }

    val liveHeaderFollowState = if (showFollowButton) {
        LiveRoomHeaderFollowState(
            relation = anchorRelation,
            loading = followLoading,
            onClick = ::onFollowAnchorClick,
        )
    } else {
        null
    }
    val onClearScreenChange: ((Boolean) -> Unit)? = if (supportsClearScreen) {
        { liveInfoCleared = it }
    } else {
        null
    }

    LaunchedEffect(liveInfoCleared) {
        if (liveInfoCleared) {
            showDanmakuSettings = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (error != null) {
            val errorMessage = error.orEmpty()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { reloadToken++ }) {
                        Text("重试")
                    }
                }
            }
        } else if (!isLayoutKnown) {
            LiveRoomResolvingLayout(
                room = room,
                detail = detail,
                onlineCount = onlineCount,
                topRankUsers = rankUsers,
                onAnchorClick = onAnchorProfileClick,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (isFullscreen) {
            Box(modifier = Modifier.fillMaxSize()) {
                LiveRoomPlayerBlock(
                    modifier = Modifier.fillMaxSize(),
                    backdrop = livePlayerBackdrop,
                    loading = loading,
                    error = error,
                    playInfo = playInfo,
                    exoPlayer = exoPlayer,
                    danmakuItems = liveDanmakuItems,
                    danmakuEnabled = danmakuEnabled,
                    danmakuSettings = danmakuSettings,
                    videoResizeMode = videoResizeMode,
                    showDanmakuSettings = showDanmakuSettings,
                    isFullscreen = true,
                    isPortraitLive = false,
                    onDanmakuToggle = { coordinator.toggleDanmaku() },
                    onDanmakuLongPress = { showDanmakuSettings = true },
                    onFullscreenToggle = { isFullscreen = false },
                    onRetry = { reloadToken++ },
                    onRefresh = {
                        onLivePlayerControlInteraction()
                        reloadToken++
                    },
                    overlayControlsVisible = livePlayerOverlayVisible,
                    onPlayerOverlayTap = onLivePlayerOverlayTap,
                    onControlInteraction = onLivePlayerControlInteraction,
                    onDanmakuSettingsChange = coordinator::updateDanmakuSettings,
                    onDismissDanmakuSettings = { showDanmakuSettings = false },
                )
                if (credential != null) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(4f),
                    ) {
                        val chatListMaxHeight = maxHeight * 0.45f
                        Column(Modifier.fillMaxSize()) {
                            Spacer(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = { onLivePlayerOverlayTap() })
                                    },
                            )
                            LiveRoomChatBottomBar(
                                recentChatItems = recentChatItems,
                                danmakuInput = danmakuInput,
                                onDanmakuInputChange = { danmakuInput = it },
                                onSendDanmaku = ::sendDanmaku,
                                maxListHeight = chatListMaxHeight,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = livePlayerOverlayVisible && !showDanmakuSettings,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it },
                    exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .zIndex(8f),
                ) {
                    LiveRoomFullscreenTopBar(
                        title = liveRoomTitle,
                        onClose = {
                            onLivePlayerControlInteraction()
                            isFullscreen = false
                        },
                    )
                }
            }
        } else if (isPortraitLive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F0F))
                    .liveClearScreenSwipeGesture(
                        cleared = liveInfoCleared,
                        enabled = supportsClearScreen,
                        onClearedChange = { liveInfoCleared = it },
                    )
                    .pointerInput(showLiveInfoUi) {
                        if (showLiveInfoUi) {
                            detectTapGestures { /* 拦截点击，避免穿透到下层直播列表 */ }
                        }
                    },
            ) {
                LiveRoomPlayerBlock(
                    modifier = Modifier.fillMaxSize(),
                    backdrop = livePlayerBackdrop,
                    loading = loading,
                    error = error,
                    playInfo = playInfo,
                    exoPlayer = exoPlayer,
                    danmakuItems = liveDanmakuItems,
                    danmakuEnabled = showLiveDanmakuOverlay,
                    danmakuSettings = danmakuSettings,
                    videoResizeMode = videoResizeMode,
                    showDanmakuSettings = showDanmakuSettings,
                    isFullscreen = false,
                    isPortraitLive = true,
                    onDanmakuToggle = { coordinator.toggleDanmaku() },
                    onDanmakuLongPress = { showDanmakuSettings = true },
                    onFullscreenToggle = {},
                    onRetry = { reloadToken++ },
                    onRefresh = {
                        onLivePlayerControlInteraction()
                        reloadToken++
                    },
                    overlayControlsVisible = showLivePlayerControls,
                    onPlayerOverlayTap = onLivePlayerOverlayTap,
                    onControlInteraction = onLivePlayerControlInteraction,
                    onDanmakuSettingsChange = coordinator::updateDanmakuSettings,
                    onDismissDanmakuSettings = { showDanmakuSettings = false },
                    clearScreenCleared = liveInfoCleared,
                    onClearScreenChange = onClearScreenChange,
                )
                AnimatedVisibility(
                    visible = showLiveInfoUi,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(180)),
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(4f),
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .liveClearScreenSwipeGesture(
                                cleared = liveInfoCleared,
                                enabled = supportsClearScreen,
                                onClearedChange = { liveInfoCleared = it },
                            ),
                    ) {
                        val maxChatListHeight = maxHeight * 0.5f
                        Column(Modifier.fillMaxSize()) {
                            LiveRoomHeader(
                                room = room,
                                detail = detail,
                                onlineCount = onlineCount,
                                topRankUsers = rankUsers,
                                followState = liveHeaderFollowState,
                                onAnchorClick = onAnchorProfileClick,
                                overlayStyle = true,
                                refreshLoading = loading,
                                overlayControlsVisible = showLivePlayerControls,
                                onRefresh = {
                                    onLivePlayerControlInteraction()
                                    reloadToken++
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding(),
                            )
                            Spacer(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = { onLivePlayerOverlayTap() })
                                    },
                            )
                            LiveRoomChatBottomBar(
                                recentChatItems = recentChatItems,
                                danmakuInput = danmakuInput,
                                onDanmakuInputChange = { danmakuInput = it },
                                onSendDanmaku = ::sendDanmaku,
                                maxListHeight = maxChatListHeight,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F0F))
                    .liveClearScreenSwipeGesture(
                        cleared = liveInfoCleared,
                        enabled = supportsClearScreen,
                        onClearedChange = { liveInfoCleared = it },
                    )
                    .pointerInput(showLiveInfoUi) {
                        if (showLiveInfoUi) {
                            detectTapGestures { /* 拦截点击，避免穿透到下层直播列表 */ }
                        }
                    },
            ) {
                LiveRoomHeaderSlot(
                    visible = showLiveInfoUi,
                    room = room,
                    detail = detail,
                    onlineCount = onlineCount,
                    topRankUsers = rankUsers,
                    followState = liveHeaderFollowState,
                    onAnchorClick = onAnchorProfileClick,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                ) {
                    LiveRoomPlayerBlock(
                        modifier = Modifier.fillMaxSize(),
                        backdrop = livePlayerBackdrop,
                        loading = loading,
                        error = error,
                        playInfo = playInfo,
                        exoPlayer = exoPlayer,
                        danmakuItems = liveDanmakuItems,
                        danmakuEnabled = showLiveDanmakuOverlay,
                        danmakuSettings = danmakuSettings,
                        videoResizeMode = videoResizeMode,
                        showDanmakuSettings = showDanmakuSettings,
                        isFullscreen = false,
                        isPortraitLive = false,
                        onDanmakuToggle = { coordinator.toggleDanmaku() },
                        onDanmakuLongPress = { showDanmakuSettings = true },
                        onFullscreenToggle = { isFullscreen = true },
                        onRetry = { reloadToken++ },
                        onRefresh = {
                            onLivePlayerControlInteraction()
                            reloadToken++
                        },
                        overlayControlsVisible = showLivePlayerControls,
                        onPlayerOverlayTap = onLivePlayerOverlayTap,
                        onControlInteraction = onLivePlayerControlInteraction,
                        onDanmakuSettingsChange = coordinator::updateDanmakuSettings,
                        onDismissDanmakuSettings = { showDanmakuSettings = false },
                        clearScreenCleared = liveInfoCleared,
                        onClearScreenChange = onClearScreenChange,
                    )
                }
                if (showLiveInfoUi) {
                    if (credential != null) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            LiveRecentDanmakuList(
                                recentChatItems = recentChatItems,
                                modifier = Modifier
                                    .weight(1f, fill = true)
                                    .fillMaxWidth()
                                    .padding(bottom = LiveDanmakuListInputGap),
                            )
                            LiveDanmakuInputBar(
                                danmakuInput = danmakuInput,
                                onDanmakuInputChange = { danmakuInput = it },
                                onSendDanmaku = ::sendDanmaku,
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LiveRoomHeaderSlot(
    visible: Boolean,
    room: BiliLiveRoom,
    detail: BiliLiveRoomDetail?,
    onlineCount: Long,
    topRankUsers: List<BiliLiveRankUser>,
    followState: LiveRoomHeaderFollowState?,
    onAnchorClick: (() -> Unit)? = null,
    overlayStyle: Boolean = false,
    refreshLoading: Boolean = false,
    overlayControlsVisible: Boolean = true,
    onRefresh: (() -> Unit)? = null,
) {
    if (visible) {
        LiveRoomHeader(
            room = room,
            detail = detail,
            onlineCount = onlineCount,
            topRankUsers = topRankUsers,
            followState = followState,
            onAnchorClick = onAnchorClick,
            overlayStyle = overlayStyle,
            refreshLoading = refreshLoading,
            overlayControlsVisible = overlayControlsVisible,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        )
    } else {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = LiveRoomHeaderHeight)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (overlayStyle) 4.dp else 8.dp,
                    bottom = if (overlayStyle) 4.dp else 8.dp,
                ),
        )
    }
}

@Composable
private fun LiveRoomResolvingLayout(
    room: BiliLiveRoom,
    detail: BiliLiveRoomDetail?,
    onlineCount: Long,
    topRankUsers: List<BiliLiveRankUser>,
    onAnchorClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xFF0F0F0F))
            .pointerInput(Unit) {
                detectTapGestures { /* 拦截点击，避免穿透到下层直播列表 */ }
            },
    ) {
        LiveRoomHeader(
            room = room,
            detail = detail,
            onlineCount = onlineCount,
            topRankUsers = topRankUsers,
            onAnchorClick = onAnchorClick,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        ) {
            RemoteImage(
                url = detail?.coverUrl?.ifBlank { room.coverUrl } ?: room.coverUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LiveRoomPlayerBlock(
    modifier: Modifier,
    backdrop: LayerBackdrop,
    loading: Boolean,
    error: String?,
    playInfo: BiliLivePlayResult?,
    exoPlayer: ExoPlayer,
    danmakuItems: List<BiliDanmakuItem>,
    danmakuEnabled: Boolean,
    danmakuSettings: DanmakuSettings,
    videoResizeMode: Int,
    showDanmakuSettings: Boolean,
    isFullscreen: Boolean,
    isPortraitLive: Boolean,
    onDanmakuToggle: () -> Unit,
    onDanmakuLongPress: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onDanmakuSettingsChange: (DanmakuSettings) -> Unit,
    onDismissDanmakuSettings: () -> Unit,
    overlayControlsVisible: Boolean,
    onPlayerOverlayTap: () -> Unit,
    onControlInteraction: () -> Unit,
    clearScreenCleared: Boolean = false,
    onClearScreenChange: ((Boolean) -> Unit)? = null,
) {
    Box(
        modifier = modifier.background(Color.Black),
    ) {
        LiveRoomPlayerContent(
            backdrop = backdrop,
            loading = loading,
            error = error,
            playInfo = playInfo,
            exoPlayer = exoPlayer,
            danmakuItems = danmakuItems,
            danmakuEnabled = danmakuEnabled,
            danmakuSettings = danmakuSettings,
            videoResizeMode = videoResizeMode,
            showDanmakuSettings = showDanmakuSettings,
            isFullscreen = isFullscreen,
            isPortraitLive = isPortraitLive,
            onDanmakuToggle = {
                onControlInteraction()
                onDanmakuToggle()
            },
            onDanmakuLongPress = {
                onControlInteraction()
                onDanmakuLongPress()
            },
            onFullscreenToggle = {
                onControlInteraction()
                onFullscreenToggle()
            },
            onRetry = onRetry,
            onRefresh = {
                onControlInteraction()
                onRefresh()
            },
            onDanmakuSettingsChange = onDanmakuSettingsChange,
            onDismissDanmakuSettings = onDismissDanmakuSettings,
            overlayControlsVisible = overlayControlsVisible,
            onPlayerOverlayTap = onPlayerOverlayTap,
            clearScreenCleared = clearScreenCleared,
            onClearScreenChange = onClearScreenChange,
        )
    }
}

@Composable
private fun BoxScope.LiveRoomPlayerContent(
    backdrop: LayerBackdrop,
    loading: Boolean,
    error: String?,
    playInfo: BiliLivePlayResult?,
    exoPlayer: ExoPlayer,
    danmakuItems: List<BiliDanmakuItem>,
    danmakuEnabled: Boolean,
    danmakuSettings: DanmakuSettings,
    videoResizeMode: Int,
    showDanmakuSettings: Boolean,
    isFullscreen: Boolean,
    isPortraitLive: Boolean,
    onDanmakuToggle: () -> Unit,
    onDanmakuLongPress: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onDanmakuSettingsChange: (DanmakuSettings) -> Unit,
    onDismissDanmakuSettings: () -> Unit,
    overlayControlsVisible: Boolean,
    onPlayerOverlayTap: () -> Unit,
    clearScreenCleared: Boolean = false,
    onClearScreenChange: ((Boolean) -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        LiveRoomPlayerSurface(
            backdrop = backdrop,
            loading = loading,
            error = error,
            playInfo = playInfo,
            exoPlayer = exoPlayer,
            danmakuItems = danmakuItems,
            danmakuEnabled = danmakuEnabled,
            danmakuSettings = danmakuSettings,
            videoResizeMode = videoResizeMode,
            onRetry = onRetry,
        )
        if (!showDanmakuSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(5f)
                    .liveClearScreenSwipeGesture(
                        cleared = clearScreenCleared,
                        enabled = onClearScreenChange != null,
                        onClearedChange = { onClearScreenChange?.invoke(it) },
                    )
                    .pointerInput(onPlayerOverlayTap) {
                        detectTapGestures(onTap = { onPlayerOverlayTap() })
                    },
            )
        }
        if (!showDanmakuSettings && !isPortraitLive) {
            AnimatedVisibility(
                visible = overlayControlsVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 10.dp)
                    .then(
                        if (isFullscreen) {
                            Modifier.statusBarsPadding()
                        } else {
                            Modifier
                        },
                    )
                    .zIndex(6f),
            ) {
                LiveRefreshCapsule(
                    loading = loading,
                    onRefresh = onRefresh,
                )
            }
        }
        if (!showDanmakuSettings && !isPortraitLive) {
            AnimatedVisibility(
                visible = overlayControlsVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = if (isFullscreen) 40.dp else 10.dp)
                    .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                    .zIndex(6f),
            ) {
                LiveRoomPlayerControls(
                    danmakuEnabled = danmakuEnabled,
                    onDanmakuToggle = onDanmakuToggle,
                    onDanmakuLongPress = onDanmakuLongPress,
                    isFullscreen = isFullscreen,
                    onFullscreenToggle = onFullscreenToggle,
                )
            }
        }
        DanmakuSettingsOverlay(
            visible = showDanmakuSettings,
            settings = danmakuSettings,
            onSettingsChange = onDanmakuSettingsChange,
            onDismiss = onDismissDanmakuSettings,
            backdrop = backdrop,
            maxHeightFraction = 0.86f,
            contentVerticalPadding = 22.dp,
            rowSpacing = 18.dp,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(30f),
        )
    }
}

@Composable
private fun BoxScope.LiveRoomPlayerSurface(
    backdrop: LayerBackdrop,
    loading: Boolean,
    error: String?,
    playInfo: BiliLivePlayResult?,
    exoPlayer: ExoPlayer,
    danmakuItems: List<BiliDanmakuItem>,
    danmakuEnabled: Boolean,
    danmakuSettings: DanmakuSettings,
    videoResizeMode: Int,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            error != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
            playInfo != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                        )
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val view = LayoutInflater.from(ctx)
                                    .inflate(R.layout.view_video_player, null, false) as PlayerView
                                view.useController = false
                                view.setKeepContentOnPlayerReset(true)
                                view.setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                                view
                            },
                            update = { view ->
                                view.resizeMode = videoResizeMode
                                if (view.player !== exoPlayer) {
                                    view.player = exoPlayer
                                }
                            },
                            onRelease = { view ->
                                view.player = null
                            },
                        )
                    }
                    LiveDanmakuOverlay(
                        items = danmakuItems,
                        enabled = danmakuEnabled,
                        settings = danmakuSettings,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LiveRoomHeader(
    room: BiliLiveRoom,
    detail: BiliLiveRoomDetail?,
    onlineCount: Long,
    topRankUsers: List<BiliLiveRankUser>,
    followState: LiveRoomHeaderFollowState? = null,
    onAnchorClick: (() -> Unit)? = null,
    overlayStyle: Boolean = false,
    refreshLoading: Boolean = false,
    overlayControlsVisible: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val authorName = detail?.userName?.ifBlank { room.userName } ?: room.userName
    val title = detail?.title?.ifBlank { room.title } ?: room.title
    val face = detail?.userFace?.ifBlank { room.userFace } ?: room.userFace
    val titleShadow = Shadow(
        color = Color.Black.copy(alpha = 0.65f),
        offset = androidx.compose.ui.geometry.Offset(0f, 1f),
        blurRadius = 4f,
    )
    val anchorClickModifier = if (onAnchorClick != null) {
        Modifier.clickable(onClick = onAnchorClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .then(
                if (overlayStyle) {
                    Modifier
                } else {
                    Modifier.background(Color(0xFF0F0F0F))
                },
            )
            .heightIn(min = LiveRoomHeaderHeight)
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = if (overlayStyle) 4.dp else 8.dp,
                bottom = if (overlayStyle) 4.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = face,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(anchorClickModifier),
        )
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = authorName,
                    modifier = anchorClickModifier,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = if (overlayStyle) {
                        TextStyle(shadow = titleShadow)
                    } else {
                        TextStyle()
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        shadow = if (overlayStyle) titleShadow else null,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            followState?.let { follow ->
                Spacer(Modifier.width(8.dp))
                BilibiliFollowButton(
                    following = follow.relation.following,
                    followerMe = follow.relation.followerMe,
                    loading = follow.loading,
                    onClick = follow.onClick,
                    compact = true,
                    transparent = true,
                )
            }
        }
        if (topRankUsers.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-6).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                topRankUsers.take(3).forEach { user ->
                    RemoteImage(
                        url = user.face,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        if (onlineCount > 0L || onRefresh != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onlineCount > 0L) {
                    Text(
                        text = "${formatBiliCount(onlineCount)}人",
                        color = Color.White.copy(alpha = if (overlayStyle) 0.88f else 0.72f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            shadow = if (overlayStyle) titleShadow else null,
                        ),
                        maxLines = 1,
                    )
                }
                if (onRefresh != null) {
                    AnimatedVisibility(
                        visible = overlayControlsVisible,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(180)),
                    ) {
                        LiveRefreshCapsule(
                            loading = refreshLoading,
                            onRefresh = onRefresh,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveRefreshCapsule(
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiveControlCapsule(
        onClick = if (loading) null else onRefresh,
        modifier = modifier,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = "刷新",
                color = Color.White,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun LiveRoomPlayerControls(
    danmakuEnabled: Boolean,
    onDanmakuToggle: () -> Unit,
    onDanmakuLongPress: () -> Unit,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveControlCapsule(
            modifier = Modifier.pointerInput(onDanmakuToggle, onDanmakuLongPress) {
                detectTapGestures(
                    onTap = { onDanmakuToggle() },
                    onLongPress = { onDanmakuLongPress() },
                )
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bili_danmaku),
                contentDescription = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                tint = if (danmakuEnabled) BiliPink else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        LiveControlCapsule(
            onClick = onFullscreenToggle,
        ) {
            Icon(
                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private val LiveControlCapsuleShape = RoundedCornerShape(percent = 50)
private val LiveControlCapsuleBorderColor = Color(0x80999999)

@Composable
private fun LiveControlCapsule(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .border(0.5.dp, LiveControlCapsuleBorderColor, LiveControlCapsuleShape)
            .clip(LiveControlCapsuleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

private val LiveDanmakuListInputGap = 12.dp
private val LiveDanmakuInputBarHeight = 48.dp

@Composable
private fun LiveDanmakuInputBar(
    danmakuInput: String,
    onDanmakuInputChange: (String) -> Unit,
    onSendDanmaku: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val isKeyboardVisible = imeBottomPx > 0
    val capsuleShape = RoundedCornerShape(percent = 50)
    val inputTextStyle = TextStyle(
        color = Color.White,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )

    fun sendMessage() {
        val message = danmakuInput.trim()
        if (message.isNotBlank()) onSendDanmaku(message)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LiveDanmakuInputBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = danmakuInput,
            onValueChange = onDanmakuInputChange,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 36.dp)
                .height(36.dp)
                .border(0.5.dp, LiveControlCapsuleBorderColor, capsuleShape)
                .clip(capsuleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            textStyle = inputTextStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { sendMessage() }),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(BiliPink),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (danmakuInput.isEmpty()) {
                        Text(
                            text = "发个弹幕呗~",
                            style = inputTextStyle.copy(color = Color.White.copy(alpha = 0.5f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        AnimatedVisibility(
            visible = isKeyboardVisible,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
        ) {
            TextButton(
                enabled = danmakuInput.isNotBlank(),
                onClick = { sendMessage() },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("发送", color = BiliPink, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun LiveRecentDanmakuList(
    recentChatItems: List<BiliDanmakuItem>,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 5.dp,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(recentChatItems.size) {
        if (recentChatItems.isNotEmpty()) {
            listState.animateScrollToItem(recentChatItems.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.Bottom),
        contentPadding = PaddingValues(top = 2.dp),
    ) {
        items(
            items = recentChatItems,
            key = { item -> item.id },
        ) { item ->
            LiveRecentDanmakuLine(item = item)
        }
    }
}

private val LiveDanmakuSenderColor = Color(0xFF7ABBFF)
private val LiveDanmakuChatFontSize = 13.sp
private val LiveDanmakuChatLineHeight = 16.sp
private val LiveDanmakuSenderStyle = TextStyle(
    color = LiveDanmakuSenderColor,
    fontSize = LiveDanmakuChatFontSize,
    lineHeight = LiveDanmakuChatLineHeight,
    fontWeight = FontWeight.SemiBold,
)
private val LiveDanmakuContentStyle = TextStyle(
    color = Color.White.copy(alpha = 0.92f),
    fontSize = LiveDanmakuChatFontSize,
    lineHeight = LiveDanmakuChatLineHeight,
    fontWeight = FontWeight.Medium,
)

@Composable
private fun LiveRecentDanmakuLine(
    item: BiliDanmakuItem,
    modifier: Modifier = Modifier,
) {
    val sender = when {
        item.senderName.isNotBlank() -> item.senderName
        item.senderId > 0L -> "UID:${item.senderId}"
        else -> "游客"
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$sender：",
            style = LiveDanmakuSenderStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BiliCommentText(
            text = item.content,
            emoticons = item.emoticons.emoticonUrlMap(),
            style = LiveDanmakuContentStyle,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveRecentDanmakuPanel(
    recentChatItems: List<BiliDanmakuItem>,
    modifier: Modifier = Modifier,
) {
    if (recentChatItems.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        recentChatItems.takeLast(4).forEach { item ->
            LiveRecentDanmakuLine(item = item)
        }
    }
}

@Composable
private fun LiveRoomFullscreenTopBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiveControlCapsule(onClick = onClose) {
            Text(
                text = "关闭",
                color = Color.White,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Text(
            text = title,
            color = Color.White,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.65f),
                    offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                    blurRadius = 4f,
                ),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LiveRoomChatBottomBar(
    recentChatItems: List<BiliDanmakuItem>,
    danmakuInput: String,
    onDanmakuInputChange: (String) -> Unit,
    onSendDanmaku: (String) -> Unit,
    maxListHeight: Dp,
    reserveNavigationBar: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (reserveNavigationBar) {
                    Modifier.navigationBarsPadding()
                } else {
                    Modifier
                },
            )
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        LiveRecentDanmakuList(
            recentChatItems = recentChatItems,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxListHeight)
                .padding(bottom = LiveDanmakuListInputGap),
        )
        LiveDanmakuInputBar(
            danmakuInput = danmakuInput,
            onDanmakuInputChange = onDanmakuInputChange,
            onSendDanmaku = onSendDanmaku,
        )
    }
}

@Composable
private fun LiveRoomInfoPanel(
    detail: BiliLiveRoomDetail?,
    recentChatItems: List<BiliDanmakuItem>,
    danmakuInput: String,
    onDanmakuInputChange: (String) -> Unit,
    credential: BilibiliCredential?,
    onSendDanmaku: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xFF111111))
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        detail?.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.weight(1f))

        if (credential != null) {
            LiveRecentDanmakuPanel(
                recentChatItems = recentChatItems,
                modifier = Modifier.padding(bottom = LiveDanmakuListInputGap),
            )
            LiveDanmakuInputBar(
                danmakuInput = danmakuInput,
                onDanmakuInputChange = onDanmakuInputChange,
                onSendDanmaku = onSendDanmaku,
            )
        } else {
            Text(
                text = "登录后可发送弹幕",
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        }
    }
}

private fun Modifier.liveClearScreenSwipeGesture(
    cleared: Boolean,
    enabled: Boolean,
    onClearedChange: (Boolean) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(cleared) {
        var totalDrag = 0f
        detectHorizontalDragGestures(
            onDragStart = { totalDrag = 0f },
            onHorizontalDrag = { _, dragAmount ->
                totalDrag += dragAmount
            },
            onDragEnd = {
                when {
                    !cleared && totalDrag >= LIVE_CLEAR_SCREEN_SWIPE_THRESHOLD_PX -> onClearedChange(true)
                    cleared && totalDrag <= -LIVE_CLEAR_SCREEN_SWIPE_THRESHOLD_PX -> onClearedChange(false)
                }
                totalDrag = 0f
            },
            onDragCancel = { totalDrag = 0f },
        )
    }
}
