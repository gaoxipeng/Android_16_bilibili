package com.example.bilibili.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BilibiliAccountStore
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliHomeFeedStore
import com.example.bilibili.data.CachedHomeFeed
import com.example.bilibili.data.BilibiliPlayerPreferences
import com.example.bilibili.data.FeedLayoutStore
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
import com.example.bilibili.ui.components.FeedRefreshHintOverlay
import com.example.bilibili.ui.components.NavAnimatedOverlay
import com.example.bilibili.ui.components.feedRefreshHintMessage
import com.example.bilibili.ui.FeedTabReselectController
import com.example.bilibili.ui.LocalFeedTabForReselect
import com.example.bilibili.ui.LocalFeedTabReselectController
import com.example.bilibili.ui.rememberFeedTabReselectController
import com.example.bilibili.ui.screens.FollowingScreen
import com.example.bilibili.ui.screens.HistoryScreen
import com.example.bilibili.ui.screens.HistoryMenuOverlay
import com.example.bilibili.ui.screens.rememberHistoryMenuController
import com.example.bilibili.ui.screens.HomeScreen
import com.example.bilibili.ui.screens.HomeSearchCapsule
import com.example.bilibili.ui.screens.HotScreen
import com.example.bilibili.ui.screens.LoginSheet
import com.example.bilibili.ui.screens.SearchScreen
import com.example.bilibili.ui.screens.UserProfileScreen
import com.example.bilibili.ui.screens.VideoDetailScreen
import com.example.bilibili.ui.screens.MineScreen
import com.example.bilibili.ui.liquidglass.LocalLiquidMenuBackdrop
import com.example.bilibili.ui.liquidglass.BottomBarBackdropSampleExtension
import com.example.bilibili.ui.liquidglass.BottomBarFeedOverlapReserve
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch

private data class UserProfileTarget(
    val mid: Long,
    val name: String = "",
    val face: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { BilibiliApiClient() }
    val accountStore = remember { BilibiliAccountStore(context) }
    val homeFeedStore = remember { BilibiliHomeFeedStore(context) }
    val cachedHomeFeed = remember { homeFeedStore.read() }
    val playerPreferences = remember { BilibiliPlayerPreferences(context) }
    val feedLayoutStore = remember { FeedLayoutStore(context) }
    var feedColumnCount by remember { mutableIntStateOf(feedLayoutStore.readColumnCount()) }
    val webSession = remember { BilibiliWebSession(context) }
    val coordinator = remember(playerPreferences) {
        VideoPlaybackCoordinator(
            initialDanmakuVisible = playerPreferences.isDanmakuVisible(),
            persistDanmakuVisible = playerPreferences::setDanmakuVisible,
        )
    }
    val videoPeekController = remember { VideoPeekController() }

    var selectedTab by remember { mutableStateOf(MainTab.Home) }
    var bottomBarExpanded by remember { mutableStateOf(true) }

    var homeVideos by remember { mutableStateOf(cachedHomeFeed?.videos ?: emptyList()) }
    var followVideos by remember { mutableStateOf<List<BiliVideoItem>>(emptyList()) }
    var hotVideos by remember { mutableStateOf<List<BiliVideoItem>>(emptyList()) }

    var homeLoading by remember { mutableStateOf(false) }
    var homeLoadingMore by remember { mutableStateOf(false) }
    var homeHasMore by remember { mutableStateOf(cachedHomeFeed?.hasMore ?: true) }
    var homeFreshIdx by remember { mutableIntStateOf(cachedHomeFeed?.freshIdx ?: 1) }
    var homeFetchRow by remember { mutableIntStateOf(cachedHomeFeed?.fetchRow ?: 1) }
    var homeLastShowList by remember { mutableStateOf(cachedHomeFeed?.lastShowList.orEmpty()) }

    var followLoading by remember { mutableStateOf(false) }
    var followLoadingMore by remember { mutableStateOf(false) }
    var followHasMore by remember { mutableStateOf(true) }
    var followOffset by remember { mutableStateOf<String?>(null) }

    var hotLoading by remember { mutableStateOf(false) }

    var homeError by remember { mutableStateOf<String?>(null) }
    var followError by remember { mutableStateOf<String?>(null) }
    var hotError by remember { mutableStateOf<String?>(null) }

    val playUrls = remember { mutableStateMapOf<String, BiliPlayStream>() }
    var activeAccount by remember { mutableStateOf(accountStore.getActiveAccount()) }
    var showLoginSheet by remember { mutableStateOf(false) }
    var detailVideo by remember { mutableStateOf<BiliVideoItem?>(null) }
    var visitedProfile by remember { mutableStateOf<UserProfileTarget?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var feedRefreshHint by remember { mutableStateOf<String?>(null) }
    var feedSearchVisible by remember { mutableStateOf(true) }

    val bottomBarBackdrop = rememberLayerBackdrop()
    val historyMenuController = rememberHistoryMenuController()
    val homePullRefreshState = rememberPullToRefreshState()
    val followPullRefreshState = rememberPullToRefreshState()
    val hotPullRefreshState = rememberPullToRefreshState()
    val watchHistoryReporter = remember(api) { WatchHistoryReporter(api) }
    val feedTabReselectController = rememberFeedTabReselectController()

    fun handleBottomTabClick(tab: MainTab) {
        if (tab == selectedTab && tab in FeedTabReselectController.FEED_RESELECT_TABS) {
            feedTabReselectController.handleReselect(tab, scope)
            return
        }
        selectedTab = tab
        bottomBarExpanded = true
    }

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

    fun openVideoDetail(video: BiliVideoItem, progressSeconds: Int = 0) {
        scope.launch {
            coordinator.stopPlayback()
            if (progressSeconds > 0) {
                coordinator.savePlaybackPosition(
                    videoPlaybackKey(video.bvid, ownerId = "detail"),
                    progressSeconds * 1000L,
                )
            }
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

    fun persistHomeFeedCache() {
        homeFeedStore.save(
            CachedHomeFeed(
                videos = homeVideos,
                freshIdx = homeFreshIdx,
                fetchRow = homeFetchRow,
                lastShowList = homeLastShowList,
                hasMore = homeHasMore,
            ),
        )
    }

    fun refreshHome(showRefreshHint: Boolean = false) {
        scope.launch {
            val previousItems = homeVideos
            homeLoading = true
            homeError = null
            homeFreshIdx = 1
            homeFetchRow = 1
            homeLastShowList = ""
            runCatching {
                val page = api.getHomeRecommend(credential())
                homeVideos = page.videos
                homeFreshIdx = page.nextFreshIdx
                homeFetchRow = page.nextFetchRow
                homeLastShowList = page.lastShowList
                homeHasMore = page.hasMore
                persistHomeFeedCache()
                if (showRefreshHint) {
                    feedRefreshHint = if (page.videos.isNotEmpty()) {
                        feedRefreshHintMessage(previousItems, page.videos)
                    } else {
                        null
                    }
                }
            }.onFailure {
                homeError = it.message
                if (showRefreshHint) feedRefreshHint = null
            }
            homeLoading = false
        }
    }

    fun loadMoreHome() {
        if (homeLoading || homeLoadingMore || !homeHasMore) return
        scope.launch {
            homeLoadingMore = true
            runCatching {
                val page = api.getHomeRecommend(
                    credential = credential(),
                    freshIdx = homeFreshIdx,
                    fetchRow = homeFetchRow,
                    lastShowList = homeLastShowList,
                )
                homeVideos = (homeVideos + page.videos).distinctBy { it.bvid }
                homeFreshIdx = page.nextFreshIdx
                homeFetchRow = page.nextFetchRow
                homeLastShowList = page.lastShowList
                homeHasMore = page.hasMore
                persistHomeFeedCache()
            }.onFailure { homeError = it.message }
            homeLoadingMore = false
        }
    }

    fun refreshFollow(showRefreshHint: Boolean = false) {
        val account = activeAccount ?: run {
            followVideos = emptyList()
            followError = null
            followOffset = null
            followHasMore = false
            return
        }
        scope.launch {
            val previousItems = followVideos
            followLoading = true
            followError = null
            followOffset = null
            runCatching {
                val page = api.getFollowingVideos(account.credential)
                followVideos = page.videos
                followOffset = page.nextOffset
                followHasMore = page.hasMore
                if (showRefreshHint) {
                    feedRefreshHint = if (page.videos.isNotEmpty()) {
                        feedRefreshHintMessage(previousItems, page.videos)
                    } else {
                        null
                    }
                }
            }.onFailure {
                followError = it.message
                if (showRefreshHint) feedRefreshHint = null
            }
            followLoading = false
        }
    }

    fun loadMoreFollow() {
        val account = activeAccount ?: return
        if (followLoading || followLoadingMore || !followHasMore) return
        scope.launch {
            followLoadingMore = true
            runCatching {
                val page = api.getFollowingVideos(
                    credential = account.credential,
                    offset = followOffset,
                )
                followVideos = (followVideos + page.videos).distinctBy { it.bvid }
                followOffset = page.nextOffset
                followHasMore = page.hasMore
            }.onFailure { followError = it.message }
            followLoadingMore = false
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
            refreshHome()
            Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
            refreshFollow()
        }
    }

    LaunchedEffect(Unit) {
        if (cachedHomeFeed == null) {
            refreshHome()
        }
        if (activeAccount != null) refreshFollow()
        refreshHot()
    }

    LaunchedEffect(selectedTab) {
        feedSearchVisible = true
        when (selectedTab) {
            MainTab.Home -> Unit
            MainTab.Following -> if (activeAccount != null && followVideos.isEmpty()) refreshFollow()
            MainTab.Hot -> if (hotVideos.isEmpty()) refreshHot()
            MainTab.History -> Unit
            MainTab.Mine -> Unit
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
    val showFeedSearchBar = detailVideo == null && !searchOpen && when (selectedTab) {
        MainTab.Home -> true
        MainTab.Following -> activeAccount != null
        MainTab.Hot -> true
        else -> false
    }
    val feedPullRefreshVisible = detailVideo == null && !searchOpen && when (selectedTab) {
        MainTab.Home -> true
        MainTab.Following -> activeAccount != null
        MainTab.Hot -> true
        else -> false
    }
    val feedPullRefreshing = when (selectedTab) {
        MainTab.Home -> homeLoading
        MainTab.Following -> followLoading
        MainTab.Hot -> hotLoading
        else -> false
    }
    val feedPullRefreshState = when (selectedTab) {
        MainTab.Home -> homePullRefreshState
        MainTab.Following -> followPullRefreshState
        MainTab.Hot -> hotPullRefreshState
        else -> homePullRefreshState
    }
    val fullscreenVideo = remember(fullscreenKey, detailVideo, homeVideos, followVideos, hotVideos) {
        val bvid = fullscreenKey?.substringAfter(":") ?: return@remember null
        when {
            detailVideo?.bvid == bvid -> detailVideo
            else -> {
                val all = homeVideos + followVideos + hotVideos
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
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.background),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(BottomBarFeedOverlapReserve + BottomBarBackdropSampleExtension)
                        .background(MaterialTheme.colorScheme.background),
                )
                when (selectedTab) {
                MainTab.Home -> FeedTabReselectScope(MainTab.Home, feedTabReselectController) {
                    HomeScreen(
                    videos = homeVideos,
                    playUrls = playUrls,
                    loading = homeLoading,
                    loadingMore = homeLoadingMore,
                    hasMore = homeHasMore,
                    error = homeError,
                    onRefresh = ::refreshHome,
                    onPullRefresh = { refreshHome(showRefreshHint = true) },
                    onLoadMore = ::loadMoreHome,
                    onVideoClick = { video -> openVideoDetail(video) },
                    onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                    onAuthorClick = { mid ->
                        val video = homeVideos.firstOrNull { it.authorMid == mid }
                        openUserProfile(mid, video?.authorName.orEmpty())
                    },
                    coordinator = coordinator,
                    contentPadding = padding,
                    showSearchBar = showFeedSearchBar,
                    onSearchVisibleChange = { feedSearchVisible = it },
                    onSearchClick = ::openSearch,
                    pullRefreshState = homePullRefreshState,
                    showEmbeddedPullRefreshIndicator = false,
                    feedColumnCount = feedColumnCount,
                    )
                }
                MainTab.Following -> FeedTabReselectScope(MainTab.Following, feedTabReselectController) {
                    FollowingScreen(
                    loggedIn = activeAccount != null,
                    videos = followVideos,
                    playUrls = playUrls,
                    loading = followLoading,
                    loadingMore = followLoadingMore,
                    hasMore = followHasMore,
                    error = followError,
                    onLoginClick = {
                        showLoginSheet = true
                    },
                    onRefresh = ::refreshFollow,
                    onPullRefresh = { refreshFollow(showRefreshHint = true) },
                    onLoadMore = ::loadMoreFollow,
                    onVideoClick = { video -> openVideoDetail(video) },
                    onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                    onAuthorClick = { mid ->
                        val video = followVideos.firstOrNull { it.authorMid == mid }
                        openUserProfile(mid, video?.authorName.orEmpty())
                    },
                    coordinator = coordinator,
                    contentPadding = padding,
                    showSearchBar = showFeedSearchBar,
                    onSearchVisibleChange = { feedSearchVisible = it },
                    onSearchClick = ::openSearch,
                    pullRefreshState = followPullRefreshState,
                    showEmbeddedPullRefreshIndicator = false,
                    feedColumnCount = feedColumnCount,
                    )
                }
                MainTab.Hot -> FeedTabReselectScope(MainTab.Hot, feedTabReselectController) {
                    HotScreen(
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
                    showSearchBar = showFeedSearchBar,
                    onSearchVisibleChange = { feedSearchVisible = it },
                    onSearchClick = ::openSearch,
                    pullRefreshState = hotPullRefreshState,
                    showEmbeddedPullRefreshIndicator = false,
                    feedColumnCount = feedColumnCount,
                    )
                }
                MainTab.History -> FeedTabReselectScope(MainTab.History, feedTabReselectController) {
                    HistoryScreen(
                    api = api,
                    credential = credential(),
                    loggedIn = activeAccount != null,
                    onLoginClick = {
                        showLoginSheet = true
                    },
                    onVideoClick = { video, progressSeconds ->
                        openVideoDetail(video, progressSeconds)
                    },
                    contentPadding = padding,
                    menuController = historyMenuController,
                    )
                }
                MainTab.Mine -> {
                    val account = activeAccount
                    MineScreen(
                        loggedIn = account != null,
                        mid = account?.uid?.toLongOrNull() ?: 0L,
                        seedName = account?.name.orEmpty(),
                        seedFace = account?.face.orEmpty(),
                        api = api,
                        credential = credential(),
                        playUrls = playUrls,
                        coordinator = coordinator,
                        onLoginClick = {
                            showLoginSheet = true
                        },
                        onVideoClick = { video -> openVideoDetail(video) },
                        onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                        onLoginRequired = { showLoginSheet = true },
                        contentPadding = padding,
                        feedColumnCount = feedColumnCount,
                        onFeedColumnCountChange = { count ->
                            feedColumnCount = count
                            feedLayoutStore.writeColumnCount(count)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
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
                val myMid = activeAccount?.uid?.toLongOrNull()
                UserProfileScreen(
                    mid = target.mid,
                    seedName = target.name,
                    seedFace = target.face,
                    api = api,
                    credential = credential(),
                    myMid = myMid,
                    playUrls = playUrls,
                    coordinator = coordinator,
                    onVideoClick = { item -> openVideoDetail(item) },
                    onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                    onLoginRequired = { showLoginSheet = true },
                    enableSettings = myMid != null && myMid == target.mid,
                    feedColumnCount = feedColumnCount,
                    onFeedColumnCountChange = { count ->
                        feedColumnCount = count
                        feedLayoutStore.writeColumnCount(count)
                    },
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
                            danmakuEnabled = true,
                            danmakuCid = stream.cid.takeIf { it > 0L } ?: fullscreenVideo.cid,
                            loadDanmaku = { cid ->
                                api.getDanmakuList(
                                    cid = cid,
                                    durationSeconds = fullscreenVideo.durationSeconds,
                                    credential = credential(),
                                    referer = "https://www.bilibili.com/video/${fullscreenVideo.bvid}",
                                )
                            },
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

            if (selectedTab == MainTab.History && detailVideo == null && !searchOpen) {
                HistoryMenuOverlay(
                    controller = historyMenuController,
                    backdrop = bottomBarBackdrop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (feedPullRefreshVisible) {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(100f),
                    isRefreshing = feedPullRefreshing,
                    state = feedPullRefreshState,
                )
            }

            if (showFeedSearchBar) {
                HomeSearchCapsule(
                    visible = feedSearchVisible,
                    contentPadding = padding,
                    onSearchClick = ::openSearch,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(10f),
                )
            }

            if (detailVideo == null && !searchOpen) {
                FeedRefreshHintOverlay(
                    message = feedRefreshHint,
                    onDismiss = { feedRefreshHint = null },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            if (detailVideo == null && !searchOpen) {
                BilibiliLiquidBottomBar(
                selectedTab = selectedTab,
                onTabClick = ::handleBottomTabClick,
                expanded = bottomBarExpanded,
                backdrop = bottomBarBackdrop,
                onExpandRequest = { bottomBarExpanded = true },
                onCollapsedTap = {
                    if (selectedTab in FeedTabReselectController.FEED_RESELECT_TABS) {
                        feedTabReselectController.handleReselect(selectedTab, scope)
                    } else when (selectedTab) {
                        MainTab.Mine -> if (activeAccount == null) showLoginSheet = true
                        else -> Unit
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

@Composable
private fun FeedTabReselectScope(
    tab: MainTab,
    controller: FeedTabReselectController,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFeedTabForReselect provides tab,
        LocalFeedTabReselectController provides controller,
    ) {
        content()
    }
}
