package com.example.bilibili.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import com.example.bilibili.data.BiliHistoryItem
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.data.BiliDynamicItem
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BilibiliAccountStore
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliEndpoints
import com.example.bilibili.data.BiliVideoPage
import com.example.bilibili.data.BilibiliHomeFeedStore
import com.example.bilibili.data.CachedHomeFeed
import com.example.bilibili.data.BilibiliPlayerPreferences
import com.example.bilibili.data.FeedLayoutStore
import com.example.bilibili.data.BilibiliWebSession
import com.example.bilibili.data.StoredBilibiliAccount
import com.example.bilibili.data.UserRelationTab
import com.example.bilibili.player.BilibiliVideoSurface
import com.example.bilibili.player.LocalVideoPeekController
import com.example.bilibili.player.LocalBilibiliCredential
import com.example.bilibili.player.LocalWatchHistoryReporter
import com.example.bilibili.player.WatchHistoryReporter
import com.example.bilibili.player.PlaybackKeepScreenOnWindowEffect
import com.example.bilibili.player.VideoPeekController
import com.example.bilibili.player.VideoPeekOverlay
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.player.VideoPlaybackMediaBridge
import com.example.bilibili.player.VideoPlaybackMetadata
import com.example.bilibili.player.videoPlaybackKey
import com.example.bilibili.ui.components.FeedRefreshHintOverlay
import com.example.bilibili.ui.components.PressAgainExitConfirmWindowMillis
import com.example.bilibili.ui.components.PressAgainExitHintOverlay
import com.example.bilibili.ui.components.NavAnimatedOverlay
import com.example.bilibili.ui.components.blockHiddenTouches
import com.example.bilibili.ui.components.feedRefreshHintMessage
import com.example.bilibili.ui.components.imageviewer.BiliImageSaveHintController
import com.example.bilibili.ui.components.imageviewer.BiliImageSaveHintOverlay
import com.example.bilibili.ui.components.imageviewer.LocalBiliImageSaveHint
import com.example.bilibili.ui.FeedTabReselectController
import com.example.bilibili.ui.LocalFeedTabForReselect
import com.example.bilibili.ui.LocalFeedTabReselectController
import com.example.bilibili.ui.rememberFeedTabReselectController
import com.example.bilibili.ui.screens.FollowingScreen
import com.example.bilibili.ui.screens.HistoryMenuOverlay
import com.example.bilibili.ui.screens.HistoryScreen
import com.example.bilibili.ui.screens.rememberHistoryMenuController
import com.example.bilibili.ui.screens.HomeScreen
import com.example.bilibili.ui.screens.HomeSearchCapsule
import com.example.bilibili.ui.screens.LiveScreen
import com.example.bilibili.ui.screens.LoginSheet
import com.example.bilibili.ui.screens.SearchScreen
import com.example.bilibili.ui.screens.UserProfileScreen
import com.example.bilibili.ui.screens.UserRelationListScreen
import com.example.bilibili.ui.screens.ArticleDetailScreen
import com.example.bilibili.ui.screens.DynamicDetailScreen
import com.example.bilibili.ui.screens.VideoDetailScreen
import com.example.bilibili.ui.screens.MineScreen
import com.example.bilibili.ui.liquidglass.LocalLiquidMenuBackdrop
import com.example.bilibili.ui.liquidglass.BottomBarBackdropSampleExtension
import com.example.bilibili.ui.liquidglass.BottomBarFeedOverlapReserve
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.bilibili.ui.navigation.AppNavController
import com.example.bilibili.ui.navigation.AppNavEntry
import com.example.bilibili.ui.navigation.findVideoDetail
import com.example.bilibili.ui.navigation.stableKey
import com.example.bilibili.ui.navigation.lastVideoDetail
import com.example.bilibili.util.BiliArticleUrl
import kotlinx.coroutines.launch

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
    var backgroundPlaybackEnabled by remember {
        mutableStateOf(playerPreferences.readBackgroundPlaybackEnabled())
    }
    val webSession = remember { BilibiliWebSession(context) }
    val coordinator = remember(playerPreferences) {
        VideoPlaybackCoordinator(
            initialDanmakuVisible = playerPreferences.isDanmakuVisible(),
            initialDanmakuSettings = playerPreferences.readDanmakuSettings(),
            persistDanmakuVisible = playerPreferences::setDanmakuVisible,
            persistDanmakuSettings = playerPreferences::setDanmakuSettings,
        )
    }
    LaunchedEffect(Unit) {
        VideoPlaybackMediaBridge.initialize(context)
    }
    PlaybackKeepScreenOnWindowEffect(coordinator)
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
    DisposableEffect(accountStore, scope) {
        api.onAccessKeyUpdated = { updated ->
            val account = accountStore.getActiveAccount()
            if (account != null && account.uid == updated.dedeUserId) {
                scope.launch {
                    val next = account.copy(credential = updated)
                    accountStore.upsertAccount(next)
                    activeAccount = next
                }
            }
        }
        onDispose { api.onAccessKeyUpdated = null }
    }
    var showLoginSheet by remember { mutableStateOf(false) }
    val navController = remember { AppNavController() }
    var feedRefreshHint by remember { mutableStateOf<String?>(null) }
    var feedSearchVisible by remember { mutableStateOf(true) }
    var liveRoomOpen by remember { mutableStateOf(false) }
    var pressAgainExitHintVisible by remember { mutableStateOf(false) }
    var lastExitBackPressAt by remember { mutableStateOf(0L) }

    val bottomBarBackdrop = rememberLayerBackdrop()
    val historyMenuController = rememberHistoryMenuController()
    val homePullRefreshState = rememberPullToRefreshState()
    val followPullRefreshState = rememberPullToRefreshState()
    val hotPullRefreshState = rememberPullToRefreshState()
    val livePullRefreshState = rememberPullToRefreshState()
    val historyPullRefreshState = rememberPullToRefreshState()
    var historyPullRefreshing by remember { mutableStateOf(false) }
    val watchHistoryReporter = remember(api) { WatchHistoryReporter(api) }
    val feedTabReselectController = rememberFeedTabReselectController()
    val imageSaveHintController = remember { BiliImageSaveHintController() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, backgroundPlaybackEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (!backgroundPlaybackEnabled &&
                (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP)
            ) {
                coordinator.pauseAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        val playbackId = video.playbackId()
        val targetCid = video.cid.takeIf { it > 0L }
        playUrls[playbackId]?.let { cached ->
            val cacheValid = if (video.isPgcPlayback()) {
                cached.videoUrl.isNotBlank() &&
                    (targetCid == null || targetCid == 0L || cached.cid == targetCid)
            } else {
                cached.aid > 0L && cached.cid > 0L &&
                    (targetCid == null || cached.cid == targetCid)
            }
            if (cacheValid) return@runCatching cached
        }
        if (video.isPgcPlayback()) {
            val epid = video.pgcEpid()
            val cid = targetCid ?: 0L
            val referer = video.playbackReferer.ifBlank {
                if (epid > 0L) "https://www.bilibili.com/bangumi/play/ep$epid" else BilibiliEndpoints.HOME
            }
            val stream = if (video.bvid.isNotBlank() && !video.bvid.startsWith("pgc") && cid > 0L) {
                api.getPlayUrl(video.bvid, cid, credential())
                    ?: api.getPgcPlayUrl(epid, cid, credential(), referer)
            } else {
                api.getPgcPlayUrl(epid, cid, credential(), referer)
            } ?: return@runCatching null
            val resolved = stream.copy(
                aid = video.aid.takeIf { it > 0L } ?: stream.aid,
                cid = cid.takeIf { it > 0L } ?: stream.cid,
            )
            playUrls[playbackId] = resolved
            return@runCatching resolved
        }
        val detail = api.getVideoView(video.bvid, credential()) ?: return@runCatching null
        val cid = targetCid ?: detail.cid.takeIf { it > 0L } ?: return@runCatching null
        val aid = video.aid.takeIf { it > 0L } ?: detail.aid
        val stream = api.getPlayUrl(video.bvid, cid, credential()) ?: return@runCatching null
        val resolved = stream.copy(aid = aid, cid = cid)
        playUrls[playbackId] = resolved
        resolved
    }.getOrNull()

    suspend fun seedVideoPartPage(video: BiliVideoItem, partPage: Int): BiliVideoItem {
        val identified = if (video.bvid.isBlank() && video.aid > 0L) {
            api.getVideoDetailByAid(video.aid, credential())?.video?.let { view ->
                video.copy(
                    bvid = view.bvid,
                    aid = view.aid,
                    title = video.title.ifBlank { view.title },
                    coverUrl = video.coverUrl.ifBlank { view.coverUrl },
                    authorName = video.authorName.ifBlank { view.authorName },
                    authorMid = video.authorMid.takeIf { it > 0L } ?: view.authorMid,
                )
            } ?: video
        } else {
            video
        }
        if (partPage <= 0 || identified.bvid.isBlank() || identified.cid > 0L) return identified
        val detail = api.getVideoDetail(identified.bvid, credential()) ?: return identified
        val cid = detail.pages.find { it.page == partPage }?.cid
            ?: detail.pages.getOrNull(partPage - 1)?.cid
            ?: return video
        return video.copy(
            cid = cid,
            aid = identified.aid.takeIf { it > 0L } ?: detail.video.aid,
            bvid = identified.bvid,
            title = identified.title.ifBlank { detail.video.title },
            coverUrl = identified.coverUrl.ifBlank { detail.video.coverUrl },
            authorName = identified.authorName.ifBlank { detail.video.authorName },
            authorMid = identified.authorMid.takeIf { it > 0L } ?: detail.video.authorMid,
            durationSeconds = identified.durationSeconds.takeIf { it > 0 }
                ?: detail.pages.find { it.cid == cid }?.durationSeconds
                ?: detail.video.durationSeconds,
        )
    }

    fun openVideoDetail(
        video: BiliVideoItem,
        progressSeconds: Int = 0,
        partPage: Int = 0,
    ) {
        scope.launch {
            val seededVideo = seedVideoPartPage(video, partPage)
            val resolvedVideo = api.resolveVideoForPlayback(seededVideo, credential())
            val playStream = resolvePlayUrl(resolvedVideo)
            val replacingVideoDetail = navController.top is AppNavEntry.VideoDetail
            navController.push(AppNavEntry.VideoDetail(resolvedVideo, progressSeconds))
            if (replacingVideoDetail) {
                coordinator.pauseInlineOnly()
            } else {
                coordinator.stopPlayback()
            }
            if (progressSeconds > 0) {
                coordinator.savePlaybackPosition(
                    videoPlaybackKey(resolvedVideo.bvid, ownerId = "detail"),
                    progressSeconds * 1000L,
                )
            }
            if (playStream != null || replacingVideoDetail) {
                coordinator.requestInlinePlayback(
                    videoPlaybackKey(resolvedVideo.bvid, ownerId = "detail"),
                )
            }
        }
    }

    fun openHistoryVideo(item: BiliHistoryItem) {
        scope.launch {
            val resolvedVideo = api.resolveHistoryVideo(item, credential())
            resolvePlayUrl(resolvedVideo)
            val replacingVideoDetail = navController.top is AppNavEntry.VideoDetail
            navController.push(AppNavEntry.VideoDetail(resolvedVideo, item.progressSeconds))
            if (replacingVideoDetail) {
                coordinator.pauseInlineOnly()
            } else {
                coordinator.stopPlayback()
            }
            if (item.progressSeconds > 0) {
                coordinator.savePlaybackPosition(
                    videoPlaybackKey(resolvedVideo.bvid, ownerId = "detail"),
                    item.progressSeconds * 1000L,
                )
            }
            if (replacingVideoDetail) {
                coordinator.requestInlinePlayback(
                    videoPlaybackKey(resolvedVideo.bvid, ownerId = "detail"),
                )
            }
        }
    }

    fun switchVideoPart(video: BiliVideoItem, page: BiliVideoPage, stream: BiliPlayStream? = null) {
        val playbackId = video.playbackId()
        val applyStream: (BiliPlayStream) -> Unit = { resolved ->
            playUrls[playbackId] = resolved
            coordinator.requestInlinePlayback(
                videoPlaybackKey(video.bvid, ownerId = "detail"),
            )
        }
        stream?.copy(aid = video.aid, cid = page.cid)?.let {
            applyStream(it)
            return
        }
        scope.launch {
            val resolved = if (video.isPgcPlayback() || page.epid > 0L) {
                val epid = page.epid.takeIf { it > 0L } ?: video.pgcEpid()
                val referer = video.playbackReferer.ifBlank {
                    if (epid > 0L) {
                        "https://www.bilibili.com/bangumi/play/ep$epid"
                    } else {
                        BilibiliEndpoints.HOME
                    }
                }
                val cid = page.cid.takeIf { it > 0L } ?: video.cid
                if (video.bvid.isNotBlank() && !video.bvid.startsWith("pgc") && cid > 0L) {
                    api.getPlayUrl(video.bvid, cid, credential())
                        ?: api.getPgcPlayUrl(epid, cid, credential(), referer)
                } else {
                    api.getPgcPlayUrl(epid, cid, credential(), referer)
                }?.copy(
                    aid = video.aid.takeIf { it > 0L } ?: 0L,
                    cid = cid,
                )
            } else {
                api.getPlayUrl(video.bvid, page.cid, credential())
                    ?.copy(aid = video.aid, cid = page.cid)
            } ?: return@launch
            applyStream(resolved)
        }
    }

    fun openDynamicDetail(item: BiliDynamicItem) {
        item.resolveArticleMobileUrl()?.let { webUrl ->
            coordinator.pauseForOverlay()
            videoPeekController.cancel()
            navController.push(
                AppNavEntry.ArticleDetail(
                    webUrl = webUrl,
                    seedTitle = item.link?.title?.ifBlank { item.text }.orEmpty(),
                ),
            )
            return
        }
        navController.push(AppNavEntry.DynamicDetail(item))
    }

    fun openArticleDetail(webUrl: String, seedTitle: String = "") {
        val url = BiliArticleUrl.resolveMobileOpusUrl(webUrl) ?: return
        coordinator.pauseForOverlay()
        videoPeekController.cancel()
        navController.push(
            AppNavEntry.ArticleDetail(
                webUrl = url,
                seedTitle = seedTitle,
            ),
        )
    }

    fun popNavEntry() {
        when (navController.pop()) {
            is AppNavEntry.VideoDetail -> coordinator.stopPlayback()
            is AppNavEntry.UserProfile -> {
                navController.stack.lastVideoDetail()?.let { entry ->
                    coordinator.requestInlinePlayback(
                        videoPlaybackKey(entry.video.bvid, ownerId = "detail"),
                    )
                }
            }
            AppNavEntry.Search,
            is AppNavEntry.DynamicDetail,
            is AppNavEntry.ArticleDetail,
            is AppNavEntry.UserRelationList,
            null -> Unit
        }
    }

    fun openUserProfile(mid: Long, name: String = "", face: String = "") {
        if (mid <= 0L) return
        coordinator.pauseForOverlay()
        videoPeekController.cancel()
        navController.push(AppNavEntry.UserProfile(mid = mid, name = name, face = face))
    }

    fun openUserRelationList(
        mid: Long,
        name: String = "",
        face: String = "",
        sign: String = "",
        initialTab: UserRelationTab,
    ) {
        if (mid <= 0L) return
        coordinator.pauseForOverlay()
        videoPeekController.cancel()
        navController.push(
            AppNavEntry.UserRelationList(
                mid = mid,
                name = name,
                face = face,
                sign = sign,
                initialTab = initialTab,
            ),
        )
    }

    fun openSearch() {
        coordinator.pauseForOverlay()
        videoPeekController.cancel()
        navController.push(AppNavEntry.Search)
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
        if (activeAccount == null) {
            homeVideos = emptyList()
            homeError = null
            homeHasMore = false
            return
        }
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

    fun refreshHot(showRefreshHint: Boolean = false) {
        scope.launch {
            hotLoading = true
            hotError = null
            val previousItems = hotVideos
            runCatching {
                hotVideos = api.getSiteRanking()
                if (showRefreshHint) {
                    feedRefreshHint = if (hotVideos.isNotEmpty()) {
                        feedRefreshHintMessage(previousItems, hotVideos)
                    } else {
                        null
                    }
                }
            }.onFailure {
                hotError = it.message
                if (showRefreshHint) feedRefreshHint = null
            }
            hotLoading = false
        }
    }

    fun persistLogin() {
        scope.launch {
            val credential = webSession.readCredentialFromCookies() ?: return@launch
            val exchangedCredential = api.exchangeAccessKey(credential)
            val profile = api.getMyInfo(exchangedCredential)
            val account = StoredBilibiliAccount(
                uid = exchangedCredential.dedeUserId,
                name = profile?.name ?: "B站用户",
                face = profile?.face,
                credential = exchangedCredential,
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

    LaunchedEffect(activeAccount?.uid) {
        val account = activeAccount ?: return@LaunchedEffect
        if (account.credential.accessKey.isNotBlank()) return@LaunchedEffect
        val updatedCredential = api.exchangeAccessKey(account.credential)
        if (updatedCredential.accessKey.isBlank()) return@LaunchedEffect
        val updatedAccount = account.copy(credential = updatedCredential)
        accountStore.upsertAccount(updatedAccount)
        activeAccount = updatedAccount
    }

    LaunchedEffect(Unit) {
        if (activeAccount != null) {
            if (cachedHomeFeed == null) {
                refreshHome()
            }
            refreshFollow()
        }
    }

    LaunchedEffect(selectedTab) {
        feedSearchVisible = true
        when (selectedTab) {
            MainTab.Home -> Unit
            MainTab.Following -> if (activeAccount != null && followVideos.isEmpty()) refreshFollow()
            MainTab.Live -> Unit
            MainTab.History -> Unit
            MainTab.Mine -> Unit
        }
    }

    LaunchedEffect(homeVideos) {
        homeVideos.take(10).forEach { video ->
            if (playUrls[video.playbackId()] == null) {
                launch { resolvePlayUrl(video) }
            }
        }
    }

    LaunchedEffect(followVideos) {
        followVideos.take(10).forEach { video ->
            if (playUrls[video.playbackId()] == null) {
                launch { resolvePlayUrl(video) }
            }
        }
    }

    LaunchedEffect(hotVideos) {
        hotVideos.take(10).forEach { video ->
            if (playUrls[video.playbackId()] == null) {
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
    val navStack = navController.stack
    val navOverlayOpen = navController.hasOverlay
    val topVideoDetailEntry = navStack.lastVideoDetail()
    val showFeedSearchBar = !navOverlayOpen && when (selectedTab) {
        MainTab.Home -> activeAccount != null
        else -> false
    }
    val feedPullRefreshVisible = !navOverlayOpen && when (selectedTab) {
        MainTab.Home -> activeAccount != null
        else -> false
    }
    val feedPullRefreshing = when (selectedTab) {
        MainTab.Home -> homeLoading
        else -> false
    }
    val feedPullRefreshState = when (selectedTab) {
        MainTab.Home -> homePullRefreshState
        else -> homePullRefreshState
    }
    val fullscreenVideo = remember(fullscreenKey, navStack, homeVideos, followVideos, hotVideos) {
        val bvid = fullscreenKey?.substringAfter(":") ?: return@remember null
        navStack.findVideoDetail(bvid)
            ?: run {
                val all = homeVideos + followVideos + hotVideos
                all.firstOrNull { it.bvid == bvid }
            }
    }

    BackHandler(enabled = fullscreenKey != null && topVideoDetailEntry != null) {
        coordinator.closeFullscreen()
    }

    BackHandler(
        enabled = navOverlayOpen &&
            fullscreenKey == null &&
            videoPeekController.activeRequest == null,
    ) {
        popNavEntry()
    }

    BackHandler(enabled = fullscreenKey != null && videoPeekController.activeRequest == null && topVideoDetailEntry == null) {
        coordinator.closeFullscreen()
    }

    val canPressAgainExit =
        !navOverlayOpen &&
            !liveRoomOpen &&
            fullscreenKey == null &&
            videoPeekController.activeRequest == null &&
            !showLoginSheet &&
            !historyMenuController.visible

    LaunchedEffect(canPressAgainExit) {
        if (!canPressAgainExit) {
            pressAgainExitHintVisible = false
        }
    }

    BackHandler(enabled = canPressAgainExit) {
        val now = System.currentTimeMillis()
        if (pressAgainExitHintVisible && now - lastExitBackPressAt <= PressAgainExitConfirmWindowMillis) {
            pressAgainExitHintVisible = false
            (context as? Activity)?.finish()
        } else {
            lastExitBackPressAt = now
            pressAgainExitHintVisible = true
        }
    }

    CompositionLocalProvider(
        LocalLiquidMenuBackdrop provides bottomBarBackdrop,
        LocalVideoPeekController provides videoPeekController,
        LocalBilibiliCredential provides activeAccount?.credential,
        LocalWatchHistoryReporter provides watchHistoryReporter,
        LocalBiliImageSaveHint provides imageSaveHintController,
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(bottomBarBackdrop)
                    .blockHiddenTouches(!navOverlayOpen),
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
                    loggedIn = activeAccount != null,
                    onLoginClick = { showLoginSheet = true },
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
                    followVideos = followVideos,
                    followPlayUrls = playUrls,
                    followLoading = followLoading,
                    followLoadingMore = followLoadingMore,
                    followHasMore = followHasMore,
                    followError = followError,
                    onLoginClick = {
                        showLoginSheet = true
                    },
                    onRefreshFollow = ::refreshFollow,
                    onPullRefreshFollow = { refreshFollow(showRefreshHint = true) },
                    onLoadMoreFollow = ::loadMoreFollow,
                    rankVideos = hotVideos,
                    rankPlayUrls = playUrls,
                    rankLoading = hotLoading,
                    rankError = hotError,
                    onRefreshRank = ::refreshHot,
                    onPullRefreshRank = { refreshHot(showRefreshHint = true) },
                    onVideoClick = { video -> openVideoDetail(video) },
                    onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                    onFollowAuthorClick = { mid ->
                        val video = followVideos.firstOrNull { it.authorMid == mid }
                        openUserProfile(mid, video?.authorName.orEmpty())
                    },
                    onRankAuthorClick = { mid ->
                        val video = hotVideos.firstOrNull { it.authorMid == mid }
                        openUserProfile(mid, video?.authorName.orEmpty())
                    },
                    coordinator = coordinator,
                    contentPadding = padding,
                    followPullRefreshState = followPullRefreshState,
                    rankPullRefreshState = hotPullRefreshState,
                    showEmbeddedPullRefreshIndicator = true,
                    feedColumnCount = feedColumnCount,
                    )
                }
                MainTab.Live -> FeedTabReselectScope(MainTab.Live, feedTabReselectController) {
                    LiveScreen(
                        api = api,
                        credential = credential(),
                        loggedIn = activeAccount != null,
                        coordinator = coordinator,
                        onLoginClick = { showLoginSheet = true },
                        contentPadding = padding,
                        pullRefreshState = livePullRefreshState,
                        showEmbeddedPullRefreshIndicator = true,
                        onLiveRoomOpenChange = { liveRoomOpen = it },
                        modifier = Modifier.fillMaxSize(),
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
                        onHistoryItemClick = { item ->
                            openHistoryVideo(item)
                        },
                        onVideoClick = { video -> openVideoDetail(video) },
                        playUrls = playUrls,
                        coordinator = coordinator,
                        onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                        onAuthorClick = { mid, name, face -> openUserProfile(mid, name, face) },
                        contentPadding = padding,
                        pullRefreshState = historyPullRefreshState,
                        showEmbeddedPullRefreshIndicator = false,
                        onPullRefreshingChange = { historyPullRefreshing = it },
                        feedColumnCount = feedColumnCount,
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
                        onOpenRelationList = ::openUserRelationList,
                        contentPadding = padding,
                        feedColumnCount = feedColumnCount,
                        onFeedColumnCountChange = { count ->
                            feedColumnCount = count
                            feedLayoutStore.writeColumnCount(count)
                        },
                        backgroundPlaybackEnabled = backgroundPlaybackEnabled,
                        onBackgroundPlaybackChange = { enabled ->
                            backgroundPlaybackEnabled = enabled
                            playerPreferences.writeBackgroundPlaybackEnabled(enabled)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                }
            }

            AppNavStackLayers(
                navStack = navStack,
                api = api,
                credential = credential(),
                myMid = activeAccount?.uid?.toLongOrNull(),
                playUrls = playUrls,
                coordinator = coordinator,
                feedColumnCount = feedColumnCount,
                onFeedColumnCountChange = { count ->
                    feedColumnCount = count
                    feedLayoutStore.writeColumnCount(count)
                },
                contentPadding = padding,
                onPopNav = ::popNavEntry,
                onOpenVideo = ::openVideoDetail,
                onOpenDescriptionVideo = { video, partPage ->
                    openVideoDetail(video, progressSeconds = 0, partPage = partPage)
                },
                onSwitchVideoPart = ::switchVideoPart,
                onOpenProfile = ::openUserProfile,
                onOpenDynamic = ::openDynamicDetail,
                onOpenArticle = ::openArticleDetail,
                onOpenRelationList = ::openUserRelationList,
                onLoginRequired = { showLoginSheet = true },
                onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
            )

            if (fullscreenKey != null && fullscreenVideo != null && videoPeekController.activeRequest == null) {
                val stream = playUrls[fullscreenVideo.playbackId()]
                if (stream != null) {
                    val fullscreenBackdrop = rememberLayerBackdrop()
                    val fullscreenCid = stream.cid.takeIf { it > 0L } ?: fullscreenVideo.cid
                    var fullscreenVideoShot by remember(fullscreenVideo.bvid, fullscreenCid) {
                        mutableStateOf(coordinator.cachedVideoShot(fullscreenCid))
                    }
                    LaunchedEffect(fullscreenVideo.bvid, fullscreenVideo.aid, fullscreenCid, activeAccount?.uid) {
                        if (fullscreenCid <= 0L) return@LaunchedEffect
                        coordinator.cachedVideoShot(fullscreenCid)?.let {
                            fullscreenVideoShot = it
                            return@LaunchedEffect
                        }
                        val shot = api.getVideoShot(
                            bvid = fullscreenVideo.bvid,
                            aid = fullscreenVideo.aid,
                            cid = fullscreenCid,
                            credential = credential(),
                        )
                        coordinator.cacheVideoShot(fullscreenCid, shot)
                        fullscreenVideoShot = shot
                    }
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
                            portraitVideo = fullscreenVideo.isPortraitVideo,
                            videoShot = fullscreenVideoShot,
                            scrubPreviewAspectRatio = com.example.bilibili.player.knownVideoAspectRatio(
                                fullscreenVideo.videoWidth,
                                fullscreenVideo.videoHeight,
                            ),
                            playbackMetadata = VideoPlaybackMetadata.fromVideo(fullscreenVideo),
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

            if (selectedTab == MainTab.History && !navOverlayOpen) {
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

            if (!navOverlayOpen) {
                FeedRefreshHintOverlay(
                    message = feedRefreshHint,
                    onDismiss = { feedRefreshHint = null },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            BiliImageSaveHintOverlay(
                modifier = Modifier.align(Alignment.TopCenter),
            )

            if (canPressAgainExit) {
                PressAgainExitHintOverlay(
                    visible = pressAgainExitHintVisible,
                    onDismiss = { pressAgainExitHintVisible = false },
                    backdrop = bottomBarBackdrop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (!navOverlayOpen && !liveRoomOpen) {
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
private fun AppNavStackLayers(
    navStack: List<AppNavEntry>,
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    myMid: Long?,
    playUrls: Map<String, BiliPlayStream>,
    coordinator: VideoPlaybackCoordinator,
    feedColumnCount: Int,
    onFeedColumnCountChange: (Int) -> Unit,
    contentPadding: PaddingValues,
    onPopNav: () -> Unit,
    onOpenVideo: (BiliVideoItem, Int) -> Unit,
    onOpenDescriptionVideo: (BiliVideoItem, Int) -> Unit,
    onSwitchVideoPart: (BiliVideoItem, BiliVideoPage, BiliPlayStream?) -> Unit,
    onOpenProfile: (Long, String, String) -> Unit,
    onOpenDynamic: (BiliDynamicItem) -> Unit,
    onOpenArticle: (String, String) -> Unit,
    onOpenRelationList: (Long, String, String, String, UserRelationTab) -> Unit,
    onLoginRequired: () -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
) {
    navStack.forEachIndexed { index, entry ->
        key(entry.stableKey(index)) {
            NavAnimatedOverlay(
                target = entry,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(90f + index),
            ) {
                Box(Modifier.fillMaxSize().blockHiddenTouches(index == navStack.lastIndex)) {
                    AppNavEntryContent(
                        entry = it,
                        isActive = index == navStack.lastIndex,
                    api = api,
                    credential = credential,
                    myMid = myMid,
                    playUrls = playUrls,
                    coordinator = coordinator,
                    feedColumnCount = feedColumnCount,
                    onFeedColumnCountChange = onFeedColumnCountChange,
                    contentPadding = contentPadding,
                    onPopNav = onPopNav,
                    onOpenVideo = onOpenVideo,
                    onOpenDescriptionVideo = onOpenDescriptionVideo,
                    onSwitchVideoPart = onSwitchVideoPart,
                    onOpenProfile = onOpenProfile,
                    onOpenDynamic = onOpenDynamic,
                    onOpenArticle = onOpenArticle,
                    onOpenRelationList = onOpenRelationList,
                    onLoginRequired = onLoginRequired,
                    onEnsurePlayStream = onEnsurePlayStream,
                )
                }
            }
        }
    }
}

@Composable
private fun AppNavEntryContent(
    entry: AppNavEntry,
    isActive: Boolean,
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    myMid: Long?,
    playUrls: Map<String, BiliPlayStream>,
    coordinator: VideoPlaybackCoordinator,
    feedColumnCount: Int,
    onFeedColumnCountChange: (Int) -> Unit,
    contentPadding: PaddingValues,
    onPopNav: () -> Unit,
    onOpenVideo: (BiliVideoItem, Int) -> Unit,
    onOpenDescriptionVideo: (BiliVideoItem, Int) -> Unit,
    onSwitchVideoPart: (BiliVideoItem, BiliVideoPage, BiliPlayStream?) -> Unit,
    onOpenProfile: (Long, String, String) -> Unit,
    onOpenDynamic: (BiliDynamicItem) -> Unit,
    onOpenArticle: (String, String) -> Unit,
    onOpenRelationList: (Long, String, String, String, UserRelationTab) -> Unit,
    onLoginRequired: () -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
) {
    when (entry) {
        AppNavEntry.Search -> {
            SearchScreen(
                api = api,
                credential = credential,
                playUrls = playUrls,
                coordinator = coordinator,
                onClose = onPopNav,
                onVideoClick = { video -> onOpenVideo(video, 0) },
                onUserClick = { mid, name, face -> onOpenProfile(mid, name, face) },
                onEnsurePlayStream = onEnsurePlayStream,
                feedColumnCount = feedColumnCount,
                handleSystemBack = isActive,
                modifier = Modifier.fillMaxSize(),
            )
        }
        is AppNavEntry.VideoDetail -> {
            VideoDetailScreen(
                seedVideo = entry.video,
                playStream = playUrls[entry.video.playbackId()],
                api = api,
                coordinator = coordinator,
                credential = credential,
                myMid = myMid,
                onLoginRequired = onLoginRequired,
                onAuthorClick = { profile ->
                    onOpenProfile(profile.mid, profile.name, profile.face)
                },
                onSwitchVideoPart = { page, stream ->
                    onSwitchVideoPart(entry.video, page, stream)
                },
                onOpenUgcEpisode = { video ->
                    onOpenVideo(video, 0)
                },
                onOpenDescriptionVideo = onOpenDescriptionVideo,
                playbackActive = isActive,
                modifier = Modifier.fillMaxSize(),
            )
        }
        is AppNavEntry.UserProfile -> {
            UserProfileScreen(
                mid = entry.mid,
                seedName = entry.name,
                seedFace = entry.face,
                api = api,
                credential = credential,
                myMid = myMid,
                playUrls = playUrls,
                coordinator = coordinator,
                onVideoClick = { item -> onOpenVideo(item, 0) },
                onDynamicClick = onOpenDynamic,
                onArticleClick = onOpenArticle,
                onEnsurePlayStream = onEnsurePlayStream,
                onLoginRequired = onLoginRequired,
                onOpenRelationList = { name, face, sign, tab ->
                    onOpenRelationList(entry.mid, name, face, sign, tab)
                },
                enableSettings = myMid != null && myMid == entry.mid,
                feedColumnCount = feedColumnCount,
                onFeedColumnCountChange = onFeedColumnCountChange,
                modifier = Modifier.fillMaxSize(),
            )
        }
        is AppNavEntry.DynamicDetail -> {
            DynamicDetailScreen(
                item = entry.item,
                api = api,
                credential = credential,
                onBack = onPopNav,
                onAuthorClick = { profile ->
                    onOpenProfile(profile.mid, profile.name, profile.face)
                },
                onOpenVideo = { video, partPage ->
                    onOpenDescriptionVideo(video, partPage)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        is AppNavEntry.ArticleDetail -> {
            ArticleDetailScreen(
                webUrl = entry.webUrl,
                seedTitle = entry.seedTitle,
                onBack = onPopNav,
                modifier = Modifier.fillMaxSize(),
            )
        }
        is AppNavEntry.UserRelationList -> {
            UserRelationListScreen(
                hostMid = entry.mid,
                hostName = entry.name,
                hostFace = entry.face,
                hostSign = entry.sign,
                initialTab = entry.initialTab,
                api = api,
                credential = credential,
                myMid = myMid,
                onBack = onPopNav,
                onUserClick = { mid, name, face -> onOpenProfile(mid, name, face) },
                onLoginRequired = onLoginRequired,
                handleSystemBack = isActive,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
