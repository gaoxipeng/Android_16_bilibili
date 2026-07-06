package com.example.bilibili.ui.screens

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.bilibili.R
import com.example.bilibili.data.BiliDanmakuItem
import com.example.bilibili.data.BiliLivePlayResult
import com.example.bilibili.data.BiliLiveRoom
import com.example.bilibili.data.BiliLiveRoomDetail
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.data.DanmakuSettings
import com.example.bilibili.data.LiveDanmakuClient
import com.example.bilibili.player.ImmersiveVideoChromeEffect
import com.example.bilibili.player.LightContentStatusBarEffect
import com.example.bilibili.player.LiveDanmakuOverlay
import com.example.bilibili.player.isPortraitVideoSize
import androidx.compose.ui.platform.LocalView
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.player.buildLiveMediaSource
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.theme.BiliPink
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun LiveRoomScreen(
    room: BiliLiveRoom,
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    coordinator: VideoPlaybackCoordinator,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember(room.roomId) { mutableStateOf(true) }
    var error by remember(room.roomId) { mutableStateOf<String?>(null) }
    var detail by remember(room.roomId) { mutableStateOf<BiliLiveRoomDetail?>(null) }
    var playInfo by remember(room.roomId) { mutableStateOf<BiliLivePlayResult?>(null) }
    var selectedQn by remember(room.roomId) { mutableIntStateOf(10_000) }
    var onlineCount by remember(room.roomId) { mutableStateOf(room.online) }
    var danmakuEnabled by remember(room.roomId) {
        mutableStateOf(coordinator.danmakuVisible)
    }
    var danmakuInput by remember(room.roomId) { mutableStateOf("") }
    val danmakuItems = remember(room.roomId) { mutableStateListOf<BiliDanmakuItem>() }
    var reloadToken by remember(room.roomId) { mutableIntStateOf(0) }
    var isPortraitStream by remember(room.roomId) { mutableStateOf<Boolean?>(null) }

    val exoPlayer = remember(room.roomId) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    val danmakuClient = remember(room.roomId) {
        LiveDanmakuClient(api = api, roomId = room.roomId, credential = credential)
    }

    BackHandler(onBack = onBack)

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

    ImmersiveVideoChromeEffect(enabled = isPortraitStream == true)
    LightContentStatusBarEffect(enabled = isPortraitStream != true)

    LaunchedEffect(room.roomId, selectedQn, reloadToken) {
        loading = true
        error = null
        runCatching {
            val roomDetail = api.getLiveRoomDetail(room.roomId, credential)
                ?: throw IllegalStateException("无法获取直播间信息")
            detail = roomDetail
            onlineCount = roomDetail.online.takeIf { it > 0L } ?: room.online
            val play = api.getLivePlayInfo(room.roomId, qn = selectedQn, credential = credential)
                ?: throw IllegalStateException(
                    if (roomDetail.isLive) "无法获取直播流" else "主播暂未开播",
                )
            playInfo = play
            exoPlayer.setMediaSource(buildLiveMediaSource(context, play.streamUrl, room.roomId))
            exoPlayer.prepare()
            exoPlayer.play()
            danmakuClient.connect(play)
        }.onFailure {
            error = it.message ?: "加载失败"
        }
        loading = false
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

    LaunchedEffect(room.roomId) {
        danmakuClient.onlineCount.collectLatest { count ->
            if (count > 0L) onlineCount = count
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

    val videoResizeMode = if (isPortraitStream == true) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
    ) {
        if (isPortraitStream == true) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                LiveRoomPlayerSurface(
                    loading = loading,
                    error = error,
                    playInfo = playInfo,
                    exoPlayer = exoPlayer,
                    danmakuItems = danmakuItems,
                    danmakuEnabled = danmakuEnabled,
                    videoResizeMode = videoResizeMode,
                    onRetry = { reloadToken++ },
                )
                LiveRoomTopBar(
                    room = room,
                    detail = detail,
                    onlineCount = onlineCount,
                    loading = loading,
                    onBack = onBack,
                    onRefresh = { reloadToken++ },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top)),
                )
                if (playInfo?.isLive == true) {
                    LiveRoomLiveBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                            .padding(top = 8.dp, end = 12.dp),
                    )
                }
                LiveRoomPortraitBottomBar(
                    danmakuEnabled = danmakuEnabled,
                    onDanmakuToggle = { danmakuEnabled = !danmakuEnabled },
                    danmakuInput = danmakuInput,
                    onDanmakuInputChange = { danmakuInput = it },
                    credential = credential,
                    onSendDanmaku = { message ->
                        scope.launch {
                            val cred = credential ?: return@launch
                            if (api.sendLiveDanmaku(room.roomId, message, cred)) {
                                danmakuInput = ""
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                ) {
                    LiveRoomPlayerSurface(
                        loading = loading,
                        error = error,
                        playInfo = playInfo,
                        exoPlayer = exoPlayer,
                        danmakuItems = danmakuItems,
                        danmakuEnabled = danmakuEnabled,
                        videoResizeMode = videoResizeMode,
                        onRetry = { reloadToken++ },
                    )
                    LiveRoomTopBar(
                        room = room,
                        detail = detail,
                        onlineCount = onlineCount,
                        loading = loading,
                        onBack = onBack,
                        onRefresh = { reloadToken++ },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    if (playInfo?.isLive == true) {
                        LiveRoomLiveBadge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 12.dp),
                        )
                    }
                }
                LiveRoomInfoPanel(
                    room = room,
                    detail = detail,
                    playInfo = playInfo,
                    selectedQn = selectedQn,
                    onQualitySelected = { selectedQn = it },
                    danmakuEnabled = danmakuEnabled,
                    onDanmakuToggle = { danmakuEnabled = !danmakuEnabled },
                    danmakuInput = danmakuInput,
                    onDanmakuInputChange = { danmakuInput = it },
                    credential = credential,
                    onSendDanmaku = { message ->
                        scope.launch {
                            val cred = credential ?: return@launch
                            if (api.sendLiveDanmaku(room.roomId, message, cred)) {
                                danmakuInput = ""
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.LiveRoomPlayerSurface(
    loading: Boolean,
    error: String?,
    playInfo: BiliLivePlayResult?,
    exoPlayer: ExoPlayer,
    danmakuItems: List<BiliDanmakuItem>,
    danmakuEnabled: Boolean,
    videoResizeMode: Int,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            loading -> Unit
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
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx)
                            .inflate(R.layout.view_video_player, null, false) as PlayerView
                        view.useController = false
                        view.resizeMode = videoResizeMode
                        view.player = exoPlayer
                        view
                    },
                    update = {
                        it.resizeMode = videoResizeMode
                        it.player = exoPlayer
                    },
                )
                LiveDanmakuOverlay(
                    items = danmakuItems,
                    enabled = danmakuEnabled,
                    settings = DanmakuSettings(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LiveRoomTopBar(
    room: BiliLiveRoom,
    detail: BiliLiveRoomDetail?,
    onlineCount: Long,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                ),
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail?.title?.ifBlank { room.title } ?: room.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append(detail?.userName?.ifBlank { room.userName } ?: room.userName)
                        if (onlineCount > 0L) {
                            append(" · ")
                            append(formatBiliCount(onlineCount))
                            append("人气")
                        }
                    },
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onRefresh,
                enabled = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveRoomLiveBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = BiliPink,
    ) {
        Text(
            text = "直播中",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LiveRoomPortraitBottomBar(
    danmakuEnabled: Boolean,
    onDanmakuToggle: () -> Unit,
    danmakuInput: String,
    onDanmakuInputChange: (String) -> Unit,
    credential: BilibiliCredential?,
    onSendDanmaku: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDanmakuToggle) {
                Text(
                    text = if (danmakuEnabled) "弹幕开" else "弹幕关",
                    color = if (danmakuEnabled) BiliPink else Color.White.copy(alpha = 0.6f),
                )
            }
        }
        if (credential != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = danmakuInput,
                    onValueChange = onDanmakuInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发个弹幕呗~") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = danmakuInput.isNotBlank(),
                    onClick = {
                        val message = danmakuInput.trim()
                        if (message.isNotBlank()) onSendDanmaku(message)
                    },
                ) {
                    Text("发送", color = BiliPink)
                }
            }
        }
    }
}

@Composable
private fun LiveRoomInfoPanel(
    room: BiliLiveRoom,
    detail: BiliLiveRoomDetail?,
    playInfo: BiliLivePlayResult?,
    selectedQn: Int,
    onQualitySelected: (Int) -> Unit,
    danmakuEnabled: Boolean,
    onDanmakuToggle: () -> Unit,
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(
                url = detail?.userFace?.ifBlank { room.userFace } ?: room.userFace,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail?.userName?.ifBlank { room.userName } ?: room.userName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOfNotNull(
                        detail?.parentAreaName?.takeIf { it.isNotBlank() },
                        detail?.areaName?.takeIf { it.isNotBlank() },
                        room.areaName.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onDanmakuToggle) {
                Text(
                    text = if (danmakuEnabled) "弹幕开" else "弹幕关",
                    color = if (danmakuEnabled) BiliPink else Color.White.copy(alpha = 0.6f),
                )
            }
        }

        playInfo?.qualities?.takeIf { it.size > 1 }?.let { qualities ->
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(qualities, key = { it.qn }) { quality ->
                    FilterChip(
                        selected = quality.qn == selectedQn,
                        onClick = { onQualitySelected(quality.qn) },
                        label = { Text(quality.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BiliPink.copy(alpha = 0.18f),
                            selectedLabelColor = BiliPink,
                        ),
                    )
                }
            }
        }

        detail?.description?.takeIf { it.isNotBlank() }?.let { description ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.weight(1f))

        if (credential != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = danmakuInput,
                    onValueChange = onDanmakuInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发个弹幕呗~") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = danmakuInput.isNotBlank(),
                    onClick = {
                        val message = danmakuInput.trim()
                        if (message.isNotBlank()) onSendDanmaku(message)
                    },
                ) {
                    Text("发送", color = BiliPink)
                }
            }
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
