package com.example.bilibili.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BiliUserProfile
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BilibiliAccountStore
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliWebSession
import com.example.bilibili.data.StoredBilibiliAccount
import com.example.bilibili.player.BilibiliVideoSurface
import com.example.bilibili.player.LocalVideoPeekController
import com.example.bilibili.player.LocalBilibiliCredential
import com.example.bilibili.player.LocalWatchHistoryReporter
import com.example.bilibili.player.WatchHistoryReporter
import com.example.bilibili.player.VideoPeekController
import com.example.bilibili.player.VideoPeekOverlay
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.player.videoPlaybackKey
import com.example.bilibili.ui.components.NavAnimatedOverlay
import com.example.bilibili.ui.screens.FollowingScreen
import com.example.bilibili.ui.screens.HomeSearchCapsule
import com.example.bilibili.ui.screens.HomeScreen
import com.example.bilibili.ui.screens.HotScreen
import com.example.bilibili.ui.screens.LiveScreen
import com.example.bilibili.ui.screens.LoginSheet
import com.example.bilibili.ui.screens.SearchScreen
import com.example.bilibili.ui.screens.UserProfileScreen
import com.example.bilibili.ui.screens.VideoDetailScreen
import com.example.bilibili.ui.screens.MineScreen
import com.example.bilibili.ui.liquidglass.LocalLiquidMenuBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch

private data class UserProfileTarget(
    val mid: Long,
    val name: String = "",
    val face: String = "",
)

@Composable
fun BilibiliApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { BilibiliApiClient() }
    val accountStore = remember { BilibiliAccountStore(context) }
    val webSession = remember { BilibiliWebSession(context) }
    val coordinator = remember { VideoPlaybackCoordinator() }
    val videoPeekController = remember { VideoPeekController() }

    var selectedTab by remember { mutableStateOf(MainTab.Home) }
    var bottomBarExpanded by remember { mutableStateOf(true) }

    var homeVideos by remember { mutableStateOf<List<BiliVideoItem>>(emptyList()) }
    var followVideos by remember { mutableStateOf<List<BiliVideoItem>>(emptyList()) }
    var hotVideos by remember { mutableStateOf<List<BiliVideoItem>>(emptyList()) }
    var liveRooms by remember { mutableStateOf(emptyList<com.example.bilibili.data.BiliLiveRoom>()) }
    var mineProfile by remember { mutableStateOf<BiliUserProfile?>(null) }
    var mineVideos by remember { mutableStateOf<List<BiliVideoItem>>(emptyList()) }

    var homeLoading by remember { mutableStateOf(false) }
    var followLoading by remember { mutableStateOf(false) }
    var hotLoading by remember { mutableStateOf(false) }
    var liveLoading by remember { mutableStateOf(false) }
    var mineLoading by remember { mutableStateOf(false) }

    var homeError by remember { mutableStateOf<String?>(null) }
    var followError by remember { mutableStateOf<String?>(null) }
    var hotError by remember { mutableStateOf<String?>(null) }
    var liveError by remember { mutableStateOf<String?>(null) }
    var mineError by remember { mutableStateOf<String?>(null) }

    val playUrls = remember { mutableStateMapOf<String, BiliPlayStream>() }
    var activeAccount by remember { mutableStateOf(accountStore.getActiveAccount()) }
    var showLoginSheet by remember { mutableStateOf(false) }
    var showHomeSearch by remember { mutableStateOf(true) }
    var detailVideo by remember { mutableStateOf<BiliVideoItem?>(null) }
    var visitedProfile by remember { mutableStateOf<UserProfileTarget?>(null) }
    var searchOpen by remember { mutableStateOf(false) }

    val bottomBarBackdrop = rememberLayerBackdrop()
    val watchHistoryReporter = remember(api) { WatchHistoryReporter(api) }

    fun credential() = activeAccount?.credential

    suspend fun resolvePlayUrl(video: BiliVideoItem): BiliPlayStream? = runCatching {
        playUrls[video.bvid]?.let { cached ->
            if (cached.aid > 0L && cached.cid > 0L) return@runCatching cached
        }
        val detail = api.getVideoView(video.bvid, credential()) ?: return@runCatching null
        val cid = detail.cid.takeIf { it > 0L } ?: return@runCatching null
        val stream = api.getPlayUrl(video.bvid, cid, credential()) ?: return@runCatching null
        val resolved = stream.copy(aid = detail.aid, cid = cid)
        playUrls[video.bvid] = resolved
        resolved
    }.getOrNull()

    fun openVideoDetail(video: BiliVideoItem) {
        scope.launch {
            coordinator.stopPlayback()
            resolvePlayUrl(video)
            detailVideo = video
        }
    }

    fun closeVideoDetail() {
        coordinator.stopPlayback()
        detailVideo = null
    }

    fun openUserProfile(mid: Long, name: String = "", face: String = "") {
        if (mid <= 0L) return
        coordinator.pauseForOverlay()
        videoPeekController.cancel()
        visitedProfile = UserProfileTarget(mid = mid, name = name, face = face)
    }

    fun closeUserProfile() {
        visitedProfile = null
        detailVideo?.let { video ->
            coordinator.requestInlinePlayback(
                videoPlaybackKey(video.bvid, ownerId = "detail"),
            )
        }
    }

    fun openSearch() {
        coordinator.pauseForOverlay()
        videoPeekController.cancel()
        searchOpen = true
    }

    fun closeSearch() {
        searchOpen = false
    }

    fun refreshHome() {
        scope.launch {
            homeLoading = true
            homeError = null
            runCatching {
                homeVideos = api.getHomeRecommend(credential())
            }.onFailure { homeError = it.message }
            homeLoading = false
        }
    }

    fun refreshFollow() {
        val account = activeAccount ?: run {
            followVideos = emptyList()
            followError = null
            return
        }
        scope.launch {
            followLoading = true
            followError = null
            runCatching {
                followVideos = api.getFollowingVideos(account.credential)
            }.onFailure { followError = it.message }
            followLoading = false
        }
    }

    fun refreshHot() {
        scope.launch {
            hotLoading = true
            hotError = null
            runCatching {
                hotVideos = api.getSiteRanking()
            }.onFailure { hotError = it.message }
            hotLoading = false
        }
    }

    fun refreshLive() {
        scope.launch {
            liveLoading = true
            liveError = null
            runCatching {
                liveRooms = api.getLiveHotRank().ifEmpty { api.getLiveAreaList() }
            }.onFailure { liveError = it.message }
            liveLoading = false
        }
    }

    fun refreshMine() {
        val account = activeAccount ?: return
        scope.launch {
            mineLoading = true
            mineError = null
            runCatching {
                val basic = api.getMyInfo(account.credential)
                val mid = basic?.mid ?: account.uid.toLong()
                val full = api.getUserInfo(mid, account.credential)
                mineProfile = full ?: basic
                mineVideos = api.getUserVideos(mid, credential = account.credential)
            }.onFailure { mineError = it.message }
            mineLoading = false
        }
    }

    fun persistLogin() {
        scope.launch {
            val credential = webSession.readCredentialFromCookies() ?: return@launch
            val profile = api.getMyInfo(credential)
            val account = StoredBilibiliAccount(
                uid = credential.dedeUserId,
                name = profile?.name ?: "B站用户",
                face = profile?.face,
                credential = credential,
            )
            accountStore.upsertAccount(account)
            accountStore.setActiveAccountId(account.uid)
            activeAccount = account
            api.invalidateWbiCache()
            refreshMine()
            Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
            refreshFollow()
        }
    }

    LaunchedEffect(Unit) {
        refreshHome()
        if (activeAccount != null) refreshFollow()
        refreshHot()
        refreshLive()
        if (activeAccount != null) refreshMine()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Home) {
            showHomeSearch = true
        }
        when (selectedTab) {
            MainTab.Home -> if (homeVideos.isEmpty()) refreshHome()
            MainTab.Following -> if (activeAccount != null && followVideos.isEmpty()) refreshFollow()
            MainTab.Hot -> if (hotVideos.isEmpty()) refreshHot()
            MainTab.Live -> if (liveRooms.isEmpty()) refreshLive()
            MainTab.Mine -> if (activeAccount != null && mineProfile == null) refreshMine()
        }
    }

    LaunchedEffect(homeVideos) {
        homeVideos.take(10).forEach { video ->
            if (playUrls[video.bvid] == null) {
                launch { resolvePlayUrl(video) }
            }
        }
    }

    LaunchedEffect(followVideos) {
        followVideos.take(10).forEach { video ->
            if (playUrls[video.bvid] == null) {
                launch { resolvePlayUrl(video) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            coordinator.stopPlayback()
        }
    }

    val fullscreenKey = coordinator.fullscreenKey
    val fullscreenVideo = remember(fullscreenKey, detailVideo, homeVideos, followVideos, hotVideos, mineVideos) {
        val bvid = fullscreenKey?.substringAfter(":") ?: return@remember null
        when {
            detailVideo?.bvid == bvid -> detailVideo
            else -> {
                val all = homeVideos + followVideos + hotVideos + mineVideos
                all.firstOrNull { it.bvid == bvid }
            }
        }
    }

    BackHandler(enabled = fullscreenKey != null && detailVideo != null) {
        coordinator.closeFullscreen()
    }

    BackHandler(enabled = visitedProfile != null) {
        closeUserProfile()
    }

    BackHandler(enabled = searchOpen && visitedProfile == null && detailVideo == null) {
        closeSearch()
    }

    BackHandler(enabled = detailVideo != null && fullscreenKey == null && visitedProfile == null) {
        closeVideoDetail()
    }

    BackHandler(enabled = fullscreenKey != null && videoPeekController.activeRequest == null && detailVideo == null) {
        coordinator.closeFullscreen()
    }

    CompositionLocalProvider(
        LocalLiquidMenuBackdrop provides bottomBarBackdrop,
        LocalVideoPeekController provides videoPeekController,
        LocalBilibiliCredential provides activeAccount?.credential,
        LocalWatchHistoryReporter provides watchHistoryReporter,
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(bottomBarBackdrop),
            ) {
                when (selectedTab) {
                MainTab.Home -> HomeScreen(
                    videos = homeVideos,
                    playUrls = playUrls,
                    loading = homeLoading,
                    error = homeError,
                    onRefresh = ::refreshHome,
                    onVideoClick = { video -> openVideoDetail(video) },
                    onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                    onAuthorClick = { mid ->
                        val video = homeVideos.firstOrNull { it.authorMid == mid }
                        openUserProfile(mid, video?.authorName.orEmpty())
                    },
                    coordinator = coordinator,
                    contentPadding = padding,
                    onSearchVisibleChange = { showHomeSearch = it },
                )
                MainTab.Following -> FollowingScreen(
                    loggedIn = activeAccount != null,
                    videos = followVideos,
                    playUrls = playUrls,
                    loading = followLoading,
                    error = followError,
                    onLoginClick = {
                        showLoginSheet = true
                        webSession.openLogin()
                    },
                    onRefresh = ::refreshFollow,
                    onVideoClick = { video -> openVideoDetail(video) },
                    onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                    onAuthorClick = { mid ->
                        val video = followVideos.firstOrNull { it.authorMid == mid }
                        openUserProfile(mid, video?.authorName.orEmpty())
                    },
                    coordinator = coordinator,
                    contentPadding = padding,
                )
                MainTab.Hot -> HotScreen(
                    videos = hotVideos,
                    playUrls = playUrls,
                    loading = hotLoading,
                    error = hotError,
                    onRefresh = ::refreshHot,
                    onVideoClick = { video -> openVideoDetail(video) },
                    onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                    onAuthorClick = { mid ->
                        val video = hotVideos.firstOrNull { it.authorMid == mid }
                        openUserProfile(mid, video?.authorName.orEmpty())
                    },
                    coordinator = coordinator,
                    contentPadding = padding,
                )
                MainTab.Live -> LiveScreen(
                    rooms = liveRooms,
                    loading = liveLoading,
                    error = liveError,
                    onRefresh = ::refreshLive,
                    onRoomClick = { room ->
                        Toast.makeText(context, "直播间 ${room.roomId}", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = padding,
                )
                MainTab.Mine -> MineScreen(
                    loggedIn = activeAccount != null,
                    profile = mineProfile,
                    videos = mineVideos,
                    playUrls = playUrls,
                    loading = mineLoading,
                    error = mineError,
                    onLoginClick = {
                        showLoginSheet = true
                        webSession.openLogin()
                    },
                    onLogoutClick = {
                        activeAccount = null
                        mineProfile = null
                        mineVideos = emptyList()
                        followVideos = emptyList()
                        accountStore.setActiveAccountId(null)
                    },
                    onRefresh = ::refreshMine,
                    onVideoClick = { video ->
                        scope.launch {
                            resolvePlayUrl(video)?.let { coordinator.requestInlinePlayback(videoPlaybackKey(video.bvid)) }
                        }
                    },
                    coordinator = coordinator,
                    contentPadding = padding,
                )
            }
            }

            if (selectedTab == MainTab.Home && detailVideo == null && !searchOpen) {
                HomeSearchCapsule(
                    visible = showHomeSearch,
                    backdrop = bottomBarBackdrop,
                    contentPadding = padding,
                    onSearchClick = ::openSearch,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(15f),
                )
            }

            detailVideo?.let { video ->
                VideoDetailScreen(
                    seedVideo = video,
                    playStream = playUrls[video.bvid],
                    api = api,
                    coordinator = coordinator,
                    credential = credential(),
                    myMid = activeAccount?.uid?.toLongOrNull(),
                    onLoginRequired = { showLoginSheet = true },
                    onAuthorClick = { profile ->
                        openUserProfile(
                            mid = profile.mid,
                            name = profile.name,
                            face = profile.face,
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(90f),
                )
            }

            NavAnimatedOverlay(
                target = if (searchOpen) Unit else null,
                modifier = Modifier.fillMaxSize().zIndex(92f),
            ) {
                SearchScreen(
                    api = api,
                    credential = credential(),
                    playUrls = playUrls,
                    coordinator = coordinator,
                    backdrop = bottomBarBackdrop,
                    onClose = ::closeSearch,
                    onVideoClick = { video ->
                        closeSearch()
                        openVideoDetail(video)
                    },
                    onUserClick = { mid, name, face -> openUserProfile(mid, name, face) },
                    onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            NavAnimatedOverlay(
                target = visitedProfile,
                modifier = Modifier.fillMaxSize().zIndex(95f),
            ) { target ->
                UserProfileScreen(
                    mid = target.mid,
                    seedName = target.name,
                    seedFace = target.face,
                    api = api,
                    credential = credential(),
                    myMid = activeAccount?.uid?.toLongOrNull(),
                    playUrls = playUrls,
                    coordinator = coordinator,
                    onVideoClick = { item -> openVideoDetail(item) },
                    onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                    onLoginRequired = { showLoginSheet = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (fullscreenKey != null && fullscreenVideo != null && videoPeekController.activeRequest == null) {
                val stream = playUrls[fullscreenVideo.bvid]
                if (stream != null) {
                    val fullscreenBackdrop = rememberLayerBackdrop()
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .zIndex(100f),
                    ) {
                        BilibiliVideoSurface(
                            playbackKey = fullscreenKey,
                            stream = stream,
                            isFullscreen = true,
                            coordinator = coordinator,
                            backdrop = fullscreenBackdrop,
                            onFullscreen = {},
                            onCloseFullscreen = { coordinator.closeFullscreen() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            videoPeekController.activeRequest?.let { request ->
                VideoPeekOverlay(
                    video = request.video,
                    playStream = request.playStream,
                    playbackKey = videoPlaybackKey(request.video.bvid),
                    coordinator = coordinator,
                    anchorBounds = request.anchorBounds,
                    expandFromAnchor = request.expandFromAnchor,
                    dockImmediately = request.dockImmediately,
                    isFloating = videoPeekController.isFloating,
                    isFullscreenMode = videoPeekController.isFullscreenMode,
                    dismissReason = videoPeekController.pendingDismiss,
                    onRequestCancel = { videoPeekController.cancel() },
                    onDismissComplete = { videoPeekController.completeDismiss() },
                    onPlaybackEnded = { videoPeekController.dismissForPlaybackEnded() },
                    modifier = Modifier.zIndex(
                        when {
                            videoPeekController.isFullscreenMode -> 600f
                            videoPeekController.isFloating -> 575f
                            else -> 565f
                        },
                    ),
                )
            }

            if (detailVideo == null && !searchOpen) {
                BilibiliLiquidBottomBar(
                selectedTab = selectedTab,
                onTabChange = { tab ->
                    selectedTab = tab
                    bottomBarExpanded = true
                },
                expanded = bottomBarExpanded,
                backdrop = bottomBarBackdrop,
                onExpandRequest = { bottomBarExpanded = true },
                onCollapsedTap = {
                    when (selectedTab) {
                        MainTab.Home -> refreshHome()
                        MainTab.Following -> if (activeAccount != null) refreshFollow() else showLoginSheet = true
                        MainTab.Hot -> refreshHot()
                        MainTab.Live -> refreshLive()
                        MainTab.Mine -> if (activeAccount != null) refreshMine() else showLoginSheet = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(20f),
            )
            }
        }
    }

    LoginSheet(
        session = webSession,
        visible = showLoginSheet,
        onDismiss = { showLoginSheet = false },
        onLoginSuccess = {
            showLoginSheet = false
            persistLogin()
        },
    )
    }
}
