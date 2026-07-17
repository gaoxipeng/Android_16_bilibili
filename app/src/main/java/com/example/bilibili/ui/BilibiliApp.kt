package com.example.bilibili.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.AppearanceMode
import com.example.bilibili.data.AppearanceSettingsStore
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
import com.example.bilibili.player.LocalBilibiliCredential
import com.example.bilibili.player.LocalWatchHistoryReporter
import com.example.bilibili.player.WatchHistoryReporter
import com.example.bilibili.player.PlaybackKeepScreenOnWindowEffect
import com.example.bilibili.player.PlaybackCookieProvider
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.player.isPlayStreamCacheStale
import com.example.bilibili.player.withCacheTimestamp
import com.example.bilibili.player.VideoPlaybackMediaBridge
import com.example.bilibili.player.VideoPlaybackMetadata
import com.example.bilibili.player.resolveStoredProgressSeconds
import com.example.bilibili.player.saveResolvedProgress
import com.example.bilibili.player.videoPlaybackKey
import com.example.bilibili.ui.components.FeedRefreshHintOverlay
import com.example.bilibili.ui.components.PressAgainExitConfirmWindowMillis
import com.example.bilibili.ui.components.PressAgainExitHintOverlay
import com.example.bilibili.ui.components.NavAnimatedOverlay
import com.example.bilibili.ui.components.NavFullscreenZIndex
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
import com.example.bilibili.ui.components.NavTransitionTouchShield
import com.example.bilibili.ui.navigation.LocalNavTransitionCoordinator
import com.example.bilibili.ui.navigation.AppNavController
import com.example.bilibili.ui.navigation.AppNavEntry
import com.example.bilibili.ui.navigation.NavExitingLayer
import com.example.bilibili.ui.navigation.NavTransitionCoordinator
import com.example.bilibili.ui.navigation.findVideoDetail
import com.example.bilibili.ui.navigation.stableKey
import com.example.bilibili.ui.navigation.lastVideoDetail
import com.example.bilibili.ui.theme.BilibiliTheme
import com.example.bilibili.util.BiliArticleUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private fun resolveStoredPlayStream(
    video: BiliVideoItem,
    playUrls: Map<String, BiliPlayStream>,
): BiliPlayStream? {
    val targetCid = video.cid.takeIf { it > 0L }
    fun accept(cached: BiliPlayStream): Boolean {
        if (cached.videoUrl.isBlank()) return false
        if (cached.isPlayStreamCacheStale()) return false
        val cachedCid = cached.cid.takeIf { it > 0L } ?: return false
        return targetCid == null || cachedCid == targetCid
    }
    playUrls[video.playbackId()]?.let { if (accept(it)) return it }
    if (video.bvid.isNotBlank()) {
        playUrls[video.bvid]?.let { if (accept(it)) return it }
    }
    val cid = video.cid
    if (cid > 0L) {
        return playUrls.values.firstOrNull { it.cid == cid && accept(it) }
    }
    return null
}

private fun MutableMap<String, BiliPlayStream>.cachePlayStream(
    video: BiliVideoItem,
    stream: BiliPlayStream,
) {
    val stamped = stream.withCacheTimestamp()
    this[video.playbackId()] = stamped
    if (video.bvid.isNotBlank() && !video.bvid.startsWith("pgc") && video.cid <= 0L) {
        this[video.bvid] = stamped
    }
}

private fun enrichVideoForDetailOpen(
    video: BiliVideoItem,
    playUrls: Map<String, BiliPlayStream>,
): BiliVideoItem {
    val cachedStream = resolveStoredPlayStream(video, playUrls) ?: return video
    return video.copy(
        cid = video.cid.takeIf { it > 0L } ?: cachedStream.cid,
        aid = video.aid.takeIf { it > 0L } ?: cachedStream.aid,
    )
}

private fun detailPlaybackKeyFor(video: BiliVideoItem): String =
    videoPlaybackKey(video.playbackId(), ownerId = "detail")

private const val HOME_LOAD_MORE_MAX_COUNT = 10
private const val BottomBarHideGestureThresholdPx = 36f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { BilibiliApiClient() }
    val accountStore = remember { BilibiliAccountStore(context) }
    val appearanceSettingsStore = remember { AppearanceSettingsStore(context) }
    var appearanceMode by remember { mutableStateOf(appearanceSettingsStore.readAppearanceMode()) }
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
            readPersistedPosition = playerPreferences::readPlaybackPositionMs,
            writePersistedPosition = playerPreferences::writePlaybackPositionMs,
        )
    }
    LaunchedEffect(Unit) {
        VideoPlaybackMediaBridge.initialize(context)
    }
    PlaybackKeepScreenOnWindowEffect(coordinator)

    var selectedTab by remember { mutableStateOf(MainTab.Home) }
    var promoteHistoryKid by remember { mutableStateOf<String?>(null) }
    var lastOpenedHistoryKid by remember { mutableStateOf<String?>(null) }
    var bottomBarExpanded by remember { mutableStateOf(true) }
    var bottomBarVisible by remember { mutableStateOf(true) }
    var bottomBarScrollDistance by remember { mutableFloatStateOf(0f) }
    val bottomBarScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta == 0f) return Offset.Zero
                if (bottomBarScrollDistance != 0f &&
                    (bottomBarScrollDistance > 0f) != (delta > 0f)
                ) {
                    bottomBarScrollDistance = 0f
                }
                bottomBarScrollDistance = (bottomBarScrollDistance + delta)
                    .coerceIn(-BottomBarHideGestureThresholdPx, BottomBarHideGestureThresholdPx)
                when {
                    bottomBarScrollDistance <= -BottomBarHideGestureThresholdPx -> {
                        bottomBarVisible = false
                        bottomBarScrollDistance = 0f
                    }
                    bottomBarScrollDistance >= BottomBarHideGestureThresholdPx -> {
                        bottomBarVisible = true
                        bottomBarScrollDistance = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    var homeVideos by remember { mutableStateOf(cachedHomeFeed?.videos ?: emptyList()) }
    var followVideos by remember { mutableStateOf<List<BiliVideoItem>>(emptyList()) }
    var hotVideos by remember { mutableStateOf<List<BiliVideoItem>>(emptyList()) }

    var homeLoading by remember { mutableStateOf(false) }
    var homeLoadingMore by remember { mutableStateOf(false) }
    var homeHasMore by remember { mutableStateOf(cachedHomeFeed?.hasMore ?: true) }
    var homeFreshIdx by remember { mutableIntStateOf(cachedHomeFeed?.freshIdx ?: 1) }
    var homeFetchRow by remember { mutableIntStateOf(cachedHomeFeed?.fetchRow ?: 1) }
    var homeLastShowList by remember { mutableStateOf(cachedHomeFeed?.lastShowList.orEmpty()) }
    var homeLoadMoreCount by remember { mutableIntStateOf(0) }
    val homeListState = rememberLazyListState()
    val homeStaggeredGridState = rememberLazyStaggeredGridState()

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
    var storedAccounts by remember { mutableStateOf(accountStore.readAccounts()) }
    var activeAccountId by remember { mutableStateOf(accountStore.readActiveAccountId()) }

    fun reloadStoredAccounts() {
        storedAccounts = accountStore.readAccounts()
        activeAccountId = accountStore.readActiveAccountId()
        activeAccount = accountStore.getActiveAccount()
    }

    DisposableEffect(accountStore, scope) {
        api.onAccessKeyUpdated = { updated ->
            val account = accountStore.getActiveAccount()
            if (account != null && account.uid == updated.dedeUserId) {
                scope.launch {
                    val next = account.copy(credential = updated)
                    accountStore.upsertAccount(next)
                    reloadStoredAccounts()
                }
            }
        }
        onDispose { api.onAccessKeyUpdated = null }
    }
    var showLoginSheet by remember { mutableStateOf(false) }
    val navController = remember { AppNavController() }
    val navTransitionCoordinator = remember { NavTransitionCoordinator() }
    var pendingPopSideEffect by remember { mutableStateOf<AppNavEntry?>(null) }
    var feedRefreshHint by remember { mutableStateOf<String?>(null) }
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

    fun refreshPlaybackSession() {
        api.invalidateWbiCache()
        activeAccount?.credential?.let { credential ->
            webSession.applyCredential(credential)
            PlaybackCookieProvider.update(credential.toCookieHeader())
        }
        playUrls.keys.toList().forEach { key ->
            if (playUrls[key]?.isPlayStreamCacheStale() == true) {
                playUrls.remove(key)
            }
        }
    }

    DisposableEffect(lifecycleOwner, backgroundPlaybackEnabled, activeAccount?.uid) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> refreshPlaybackSession()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (!backgroundPlaybackEnabled) {
                        coordinator.pauseAll()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun handleBottomTabClick(tab: MainTab) {
        bottomBarVisible = true
        if (tab == selectedTab && tab in FeedTabReselectController.FEED_RESELECT_TABS) {
            feedTabReselectController.handleReselect(tab, scope)
            return
        }
        selectedTab = tab
        bottomBarExpanded = true
        bottomBarVisible = true
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
            if (cacheValid && !cached.isPlayStreamCacheStale()) return@runCatching cached
        }
        if (video.isPgcPlayback()) {
            val epid = video.pgcEpid()
            val cid = targetCid ?: 0L
            val referer = if (epid > 0L) {
                "https://www.bilibili.com/bangumi/play/ep$epid"
            } else {
                video.playbackReferer.ifBlank { BilibiliEndpoints.HOME }
            }
            val stream = if (video.bvid.isNotBlank() && !video.bvid.startsWith("pgc") && cid > 0L) {
                api.getPlayUrl(
                    bvid = video.bvid,
                    cid = cid,
                    credential = credential(),
                    aid = video.aid,
                    referer = "https://www.bilibili.com/video/${video.bvid}",
                ) ?: api.resolvePgcPlayUrl(epid, cid, credential(), referer)
            } else {
                api.resolvePgcPlayUrl(epid, cid, credential(), referer)
            } ?: return@runCatching null
            val resolved = stream.copy(
                aid = video.aid.takeIf { it > 0L } ?: stream.aid,
                cid = cid.takeIf { it > 0L } ?: stream.cid,
            )
            playUrls.cachePlayStream(video, resolved)
            return@runCatching resolved
        }
        val detail = api.getVideoView(video.bvid, credential()) ?: return@runCatching null
        val resolvedVideo = if (targetCid != null && targetCid > 0L) {
            video.copy(aid = video.aid.takeIf { it > 0L } ?: detail.aid)
        } else {
            api.resolveVideoForPlayback(video, credential())
        }
        val cid = resolvedVideo.cid.takeIf { it > 0L }
            ?: detail.cid.takeIf { it > 0L }
            ?: return@runCatching null
        val aid = resolvedVideo.aid.takeIf { it > 0L } ?: detail.aid
        val playBvid = resolvedVideo.bvid.takeIf { it.isNotBlank() } ?: video.bvid
        val stream = api.getPlayUrl(
            bvid = playBvid,
            cid = cid,
            credential = credential(),
            aid = aid,
            referer = "https://www.bilibili.com/video/$playBvid",
        ) ?: return@runCatching null
        val resolved = stream.copy(aid = aid, cid = cid)
        playUrls.cachePlayStream(resolvedVideo.copy(bvid = playBvid, cid = cid, aid = aid), resolved)
        resolved
    }.getOrNull()

    fun refreshPlayStream(video: BiliVideoItem) {
        playUrls.remove(video.playbackId())
        if (video.bvid.isNotBlank() && !video.bvid.startsWith("pgc") && video.cid <= 0L) {
            playUrls.remove(video.bvid)
        }
        coordinator.releaseHandoffPlayer()
        scope.launch { resolvePlayUrl(video) }
    }

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
        preferRecentWatchPart: Boolean = false,
    ) {
        val replacingVideoDetail = navController.top is AppNavEntry.VideoDetail
        val initialVideo = enrichVideoForDetailOpen(video, playUrls)
        val initialProgress = if (progressSeconds > 0) {
            progressSeconds
        } else {
            resolveStoredProgressSeconds(coordinator, initialVideo.playbackId(), 0)
        }
        val handoffKey = coordinator.handoffPlaybackKeyForVideo(initialVideo)
            ?: detailPlaybackKeyFor(initialVideo)
        if (!replacingVideoDetail) {
            navController.push(AppNavEntry.VideoDetail(initialVideo, initialProgress))
            if (coordinator.hasHandoffPlayer(handoffKey)) {
                coordinator.requestInlinePlayback(handoffKey)
            } else {
                coordinator.stopPlayback()
            }
        }
        scope.launch {
            val cred = credential()
            var effectivePartPage = partPage
            var hintProgress = progressSeconds
            var workingVideo = video

            if (
                preferRecentWatchPart &&
                partPage <= 0 &&
                progressSeconds <= 0 &&
                video.cid <= 0L
            ) {
                api.findRecentWatchHistoryForVideo(video, cred)?.let { history ->
                    if (history.cid > 0L) {
                        workingVideo = video.copy(
                            cid = history.cid,
                            aid = video.aid.takeIf { it > 0L } ?: history.aid,
                        )
                    } else if (history.page > 0) {
                        effectivePartPage = history.page
                    }
                    if (hintProgress <= 0 && history.progressSeconds > 0) {
                        hintProgress = history.progressSeconds
                    }
                }
            }

            val seededVideo = seedVideoPartPage(workingVideo, effectivePartPage)
            val resolvedVideo = api.resolveVideoForPlayback(seededVideo, cred)
            val resolvedProgress = if (hintProgress > 0) {
                hintProgress
            } else {
                val playbackId = resolvedVideo.playbackId()
                val serverProgress = api.getVideoWatchProgress(resolvedVideo, cred) ?: 0
                resolveStoredProgressSeconds(coordinator, playbackId, serverProgress)
            }
            val playStream = resolvePlayUrl(resolvedVideo)
            playStream?.let { stream -> playUrls.cachePlayStream(resolvedVideo, stream) }
            val resolvedDetailKey = detailPlaybackKeyFor(resolvedVideo)
            if (navController.top is AppNavEntry.VideoDetail) {
                val current = navController.top as AppNavEntry.VideoDetail
                if (current.video.bvid == resolvedVideo.bvid &&
                    current.video.playbackId() == resolvedVideo.playbackId() &&
                    playStream != null
                ) {
                    if (current.progressSeconds != resolvedProgress) {
                        navController.replaceTop(
                            AppNavEntry.VideoDetail(resolvedVideo, resolvedProgress),
                        )
                    }
                } else {
                    navController.replaceTop(
                        AppNavEntry.VideoDetail(resolvedVideo, resolvedProgress),
                    )
                }
            } else {
                navController.push(AppNavEntry.VideoDetail(resolvedVideo, resolvedProgress))
            }
            if (replacingVideoDetail) {
                coordinator.releaseHandoffPlayer()
            }
            saveResolvedProgress(
                coordinator = coordinator,
                playbackId = resolvedVideo.playbackId(),
                progressSeconds = resolvedProgress,
            )
            if (playStream != null || replacingVideoDetail) {
                val playbackKey = coordinator.handoffPlaybackKeyForVideo(resolvedVideo)
                    ?: resolvedDetailKey
                coordinator.requestInlinePlayback(playbackKey)
            }
        }
    }

    fun openHistoryVideo(item: BiliHistoryItem) {
        lastOpenedHistoryKid = item.kid.takeIf { it.isNotBlank() }
        scope.launch {
            val resolvedVideo = api.resolveHistoryVideo(item, credential())
            if (resolvedVideo.cid > 0L && resolvedVideo.bvid.isNotBlank()) {
                playUrls.remove(resolvedVideo.bvid)
            }
            val playStream = resolvePlayUrl(resolvedVideo)
            playStream?.let { stream -> playUrls.cachePlayStream(resolvedVideo, stream) }
            val playbackId = resolvedVideo.playbackId()
            val serverProgress = api.getVideoWatchProgress(resolvedVideo, credential()) ?: 0
            val resolvedProgress = resolveStoredProgressSeconds(
                coordinator = coordinator,
                playbackId = playbackId,
                serverProgressSeconds = maxOf(item.progressSeconds, serverProgress),
            )
            val replacingVideoDetail = navController.top is AppNavEntry.VideoDetail
            if (!replacingVideoDetail) {
                coordinator.stopPlayback()
            }
            navController.push(AppNavEntry.VideoDetail(resolvedVideo, resolvedProgress))
            if (replacingVideoDetail) {
                coordinator.releaseHandoffPlayer()
            }
            saveResolvedProgress(
                coordinator = coordinator,
                playbackId = playbackId,
                progressSeconds = resolvedProgress,
            )
            if (playStream != null || replacingVideoDetail) {
                coordinator.requestInlinePlayback(
                    videoPlaybackKey(resolvedVideo.playbackId(), ownerId = "detail"),
                )
            }
        }
    }

    fun replaceVideoDetail(video: BiliVideoItem, stream: BiliPlayStream) {
        coordinator.releaseHandoffPlayer()
        coordinator.stopPlayback()
        playUrls.cachePlayStream(video, stream)
        navController.push(AppNavEntry.VideoDetail(video, progressSeconds = 0))
        coordinator.requestInlinePlayback(
            videoPlaybackKey(video.playbackId(), ownerId = "detail"),
        )
    }

    fun switchVideoPart(
        video: BiliVideoItem,
        page: BiliVideoPage,
        stream: BiliPlayStream? = null,
        inPlace: Boolean = false,
    ) {
        fun videoForPage(resolved: BiliPlayStream): BiliVideoItem =
            video.copy(
                bvid = page.bvid.takeIf { it.isNotBlank() } ?: video.bvid,
                cid = page.cid.takeIf { it > 0L } ?: resolved.cid,
                epid = page.epid.takeIf { it > 0L } ?: video.epid,
                title = page.title.takeIf { it.isNotBlank() } ?: video.title,
                durationSeconds = page.durationSeconds.takeIf { it > 0 } ?: video.durationSeconds,
            )
        val applyStream: (BiliPlayStream) -> Unit = { resolved ->
            val item = videoForPage(resolved)
            playUrls.cachePlayStream(item, resolved)
            if (!inPlace) {
                val playbackId = item.playbackId()
                val playbackKey = videoPlaybackKey(playbackId, ownerId = "detail")
                val sameArchive = item.bvid.isNotBlank() && item.bvid == video.bvid
                if (!sameArchive) {
                    val progressSeconds = (
                        coordinator.getPlaybackPosition(playbackKey) / 1000L
                    ).toInt()
                    navController.push(AppNavEntry.VideoDetail(item, progressSeconds = progressSeconds))
                }
                coordinator.releaseHandoffPlayer()
                coordinator.requestInlinePlayback(playbackKey)
            }
        }
        stream?.let { resolved ->
            val fixed = resolved.copy(
                aid = video.aid.takeIf { it > 0L } ?: resolved.aid,
                cid = page.cid.takeIf { it > 0L } ?: resolved.cid,
            )
            applyStream(fixed)
            return
        }
        scope.launch {
            val resolved = if (video.isPgcPlayback() || page.epid > 0L) {
                val epid = page.epid.takeIf { it > 0L } ?: video.pgcEpid()
                val referer = if (epid > 0L) {
                    "https://www.bilibili.com/bangumi/play/ep$epid"
                } else {
                    video.playbackReferer.ifBlank { BilibiliEndpoints.HOME }
                }
                val cid = page.cid.takeIf { it > 0L } ?: video.cid
                val videoReferer = "https://www.bilibili.com/video/${video.bvid}"
                if (video.bvid.isNotBlank() && !video.bvid.startsWith("pgc") && cid > 0L) {
                    api.getPlayUrl(
                        bvid = video.bvid,
                        cid = cid,
                        credential = credential(),
                        aid = video.aid,
                        referer = videoReferer,
                    ) ?: api.resolvePgcPlayUrl(epid, cid, credential(), referer)
                } else {
                    api.resolvePgcPlayUrl(epid, cid, credential(), referer)
                }?.copy(
                    aid = video.aid.takeIf { it > 0L } ?: 0L,
                    cid = cid,
                )
            } else {
                api.getPlayUrl(
                    bvid = video.bvid,
                    cid = page.cid,
                    credential = credential(),
                    aid = video.aid,
                    referer = "https://www.bilibili.com/video/${video.bvid}",
                )?.copy(aid = video.aid, cid = page.cid)
            } ?: return@launch
            applyStream(resolved)
        }
    }

    fun updateVideoDetailSeed(video: BiliVideoItem, stream: BiliPlayStream) {
        switchVideoPart(
            video = video,
            page = BiliVideoPage(
                page = 0,
                cid = stream.cid.takeIf { it > 0L } ?: video.cid,
                title = video.title,
                durationSeconds = video.durationSeconds,
                epid = video.pgcEpid(),
            ),
            stream = stream,
        )
    }

    fun openDynamicDetail(item: BiliDynamicItem) {
        item.resolveArticleMobileUrl()?.let { webUrl ->
            coordinator.pauseForOverlay()
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
        navController.push(
            AppNavEntry.ArticleDetail(
                webUrl = url,
                seedTitle = seedTitle,
            ),
        )
    }

    fun completeNavPopSideEffects() {
        if (pendingPopSideEffect == null && navController.exitingLayer == null) return
        when (val removed = pendingPopSideEffect) {
            is AppNavEntry.VideoDetail -> {
                coordinator.pauseAll()
                coordinator.activeKey = null
                coordinator.fullscreenKey = null
                coordinator.fullscreenPortraitVideo = null
                coordinator.fullscreenVideo = null
                coordinator.fullscreenStream = null
                if (selectedTab == MainTab.History) {
                    lastOpenedHistoryKid?.takeIf { it.isNotBlank() }?.let { kid ->
                        promoteHistoryKid = kid
                    }
                    lastOpenedHistoryKid = null
                }
            }
            is AppNavEntry.UserProfile -> {
                navController.stack.lastVideoDetail()?.let { entry ->
                    coordinator.requestInlinePlayback(
                        videoPlaybackKey(entry.video.playbackId(), ownerId = "detail"),
                    )
                }
            }
            AppNavEntry.Search,
            is AppNavEntry.DynamicDetail,
            is AppNavEntry.ArticleDetail,
            is AppNavEntry.UserRelationList,
            null -> Unit
        }
        pendingPopSideEffect = null
        navController.clearExitingLayer()
    }

    fun popNavEntry() {
        val removed = navController.pop() ?: return
        if (removed is AppNavEntry.VideoDetail) {
            coordinator.requestDetailHandoffPreserve(
                detailPlaybackKeyFor(removed.video),
            )
        }
        pendingPopSideEffect = removed
    }

    fun openUserProfile(mid: Long, name: String = "", face: String = "") {
        if (mid <= 0L) return
        coordinator.pauseForOverlay()
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
            homeLoadMoreCount = 0
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
        if (
            homeLoading ||
            homeLoadingMore ||
            !homeHasMore ||
            homeLoadMoreCount >= HOME_LOAD_MORE_MAX_COUNT
        ) {
            return
        }
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
                homeLoadMoreCount++
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
            val hadNoActiveAccount = activeAccount == null
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
            reloadStoredAccounts()
            api.invalidateWbiCache()
            if (hadNoActiveAccount) {
                selectedTab = MainTab.Home
            }
            refreshHome()
            feedRefreshHint = "登录成功"
            refreshFollow()
        }
    }

    fun switchStoredAccount(uid: String) {
        if (uid == activeAccountId) return
        scope.launch {
            val account = storedAccounts.firstOrNull { it.uid == uid } ?: return@launch
            runCatching {
                webSession.activateAccount(account)
                accountStore.setActiveAccountId(uid)
                reloadStoredAccounts()
                api.invalidateWbiCache()
            }.onSuccess {
                refreshHome()
                refreshFollow()
                Toast.makeText(context, "已切换账号", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    error.message ?: "切换账号失败",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun deleteStoredAccount(uid: String) {
        scope.launch {
            val deletingActive = activeAccountId == uid
            accountStore.removeAccount(uid)
            reloadStoredAccounts()
            if (!deletingActive) return@launch
            val nextAccount = accountStore.getActiveAccount()
            if (nextAccount != null) {
                runCatching {
                    webSession.activateAccount(nextAccount)
                    api.invalidateWbiCache()
                    refreshHome()
                    refreshFollow()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "切换到下一个账号失败",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } else {
                webSession.clearAllCookies()
                activeAccount = null
                homeVideos = emptyList()
                followVideos = emptyList()
                hotVideos = emptyList()
            }
        }
    }

    fun prepareAddAccount() {
        scope.launch {
            webSession.prepareAddAccount()
            showLoginSheet = true
        }
    }

    LaunchedEffect(activeAccount?.uid) {
        val account = activeAccount ?: return@LaunchedEffect
        webSession.applyCredential(account.credential)
        PlaybackCookieProvider.update(account.credential.toCookieHeader())
        if (account.credential.accessKey.isNotBlank()) return@LaunchedEffect
        val updatedCredential = api.exchangeAccessKey(account.credential)
        if (updatedCredential.accessKey.isBlank()) return@LaunchedEffect
        val updatedAccount = account.copy(credential = updatedCredential)
        accountStore.upsertAccount(updatedAccount)
        reloadStoredAccounts()
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
        bottomBarVisible = true
        when (selectedTab) {
            MainTab.Home -> Unit
            MainTab.Following -> if (activeAccount != null && followVideos.isEmpty()) refreshFollow()
            MainTab.Live -> Unit
            MainTab.History -> Unit
            MainTab.Mine -> Unit
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
    LaunchedEffect(navOverlayOpen) {
        if (navOverlayOpen) {
            feedRefreshHint = null
        }
    }
    val topVideoDetailEntry = navStack.lastVideoDetail()
    val coordinatorFullscreenVideo = coordinator.fullscreenVideo
    val coordinatorFullscreenStream = coordinator.fullscreenStream
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
    val fullscreenVideo = remember(
        fullscreenKey,
        navStack,
        homeVideos,
        followVideos,
        hotVideos,
        coordinatorFullscreenVideo,
    ) {
        coordinatorFullscreenVideo ?: run {
            val playbackId = fullscreenKey?.substringAfter(':') ?: return@run null
            navStack.findVideoDetail(playbackId)
                ?: run {
                    val all = homeVideos + followVideos + hotVideos
                    all.firstOrNull { it.playbackId() == playbackId }
                        ?: all.firstOrNull { it.bvid == playbackId.substringBefore(":cid:") }
                }
        }
    }
    val fullscreenPlayStream = remember(
        fullscreenKey,
        fullscreenVideo,
        playUrls,
        coordinatorFullscreenStream,
    ) {
        coordinatorFullscreenStream ?: fullscreenVideo?.let { video ->
            playUrls[video.playbackId()] ?: playUrls[video.bvid]
        }
    }

    BackHandler(enabled = fullscreenKey != null && topVideoDetailEntry != null) {
        coordinator.closeFullscreen()
    }

    BackHandler(
        enabled = navOverlayOpen && fullscreenKey == null,
    ) {
        popNavEntry()
    }

    BackHandler(enabled = fullscreenKey != null && topVideoDetailEntry == null) {
        coordinator.closeFullscreen()
    }

    val canPressAgainExit =
        !navOverlayOpen &&
            !liveRoomOpen &&
            fullscreenKey == null &&
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

    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (appearanceMode) {
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
        AppearanceMode.System -> systemInDarkTheme
    }

    BilibiliTheme(darkTheme = darkTheme) {
    CompositionLocalProvider(
        LocalLiquidMenuBackdrop provides bottomBarBackdrop,
        LocalBilibiliCredential provides activeAccount?.credential,
        LocalWatchHistoryReporter provides watchHistoryReporter,
        LocalBiliImageSaveHint provides imageSaveHintController,
        LocalNavTransitionCoordinator provides navTransitionCoordinator,
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(bottomBarBackdrop)
                    .nestedScroll(bottomBarScrollConnection)
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
                KeepAliveTabLayer(visible = selectedTab == MainTab.Home) {
                    FeedTabReselectScope(MainTab.Home, feedTabReselectController) {
                        HomeScreen(
                            loggedIn = activeAccount != null,
                            onLoginClick = { showLoginSheet = true },
                            videos = homeVideos,
                            playUrls = playUrls,
                            loading = homeLoading,
                            loadingMore = homeLoadingMore,
                            hasMore = homeHasMore && homeLoadMoreCount < HOME_LOAD_MORE_MAX_COUNT,
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
                            showSearchBar = activeAccount != null,
                            // Search and bottom navigation share the root scroll visibility state.
                            onSearchVisibleChange = {},
                            onSearchClick = ::openSearch,
                            pullRefreshState = homePullRefreshState,
                            showEmbeddedPullRefreshIndicator = false,
                            feedColumnCount = feedColumnCount,
                            listState = homeListState,
                            staggeredGridState = homeStaggeredGridState,
                        )
                    }
                }
                KeepAliveTabLayer(visible = selectedTab == MainTab.Following) {
                    FeedTabReselectScope(MainTab.Following, feedTabReselectController) {
                        FollowingScreen(
                            loggedIn = activeAccount != null,
                            followVideos = followVideos,
                            followPlayUrls = playUrls,
                            followLoading = followLoading,
                            followLoadingMore = followLoadingMore,
                            followHasMore = followHasMore,
                            followError = followError,
                            onLoginClick = { showLoginSheet = true },
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
                }
                KeepAliveTabLayer(visible = selectedTab == MainTab.Live) {
                    FeedTabReselectScope(MainTab.Live, feedTabReselectController) {
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
                            onOpenAnchorProfile = { mid, name, face ->
                                openUserProfile(mid, name, face)
                            },
                            navOverlayOpen = navOverlayOpen,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                KeepAliveTabLayer(visible = selectedTab == MainTab.History) {
                    FeedTabReselectScope(MainTab.History, feedTabReselectController) {
                        HistoryScreen(
                            api = api,
                            credential = credential(),
                            loggedIn = activeAccount != null,
                            promoteHistoryKid = promoteHistoryKid,
                            onHistoryPromoteConsumed = { promoteHistoryKid = null },
                            onLoginClick = { showLoginSheet = true },
                            onHistoryItemClick = { item -> openHistoryVideo(item) },
                            onVideoClick = { video -> openVideoDetail(video) },
                            onFavoriteVideoClick = { video ->
                                openVideoDetail(video, preferRecentWatchPart = true)
                            },
                            playUrls = playUrls,
                            coordinator = coordinator,
                            onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                            onAuthorClick = { mid, name, face -> openUserProfile(mid, name, face) },
                            contentPadding = padding,
                            pullRefreshState = historyPullRefreshState,
                            showEmbeddedPullRefreshIndicator = true,
                            onPullRefreshingChange = { historyPullRefreshing = it },
                            feedColumnCount = feedColumnCount,
                            menuController = historyMenuController,
                        )
                    }
                }
                KeepAliveTabLayer(visible = selectedTab == MainTab.Mine) {
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
                        onLoginClick = { showLoginSheet = true },
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
                        appearanceMode = appearanceMode,
                        onAppearanceModeChange = { mode ->
                            appearanceMode = mode
                            appearanceSettingsStore.writeAppearanceMode(mode)
                        },
                        storedAccounts = storedAccounts,
                        activeAccountId = activeAccountId,
                        onSwitchAccount = ::switchStoredAccount,
                        onDeleteAccount = ::deleteStoredAccount,
                        onAddAccount = ::prepareAddAccount,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            NavTransitionTouchShield(
                coordinator = navTransitionCoordinator,
                modifier = Modifier.zIndex(594f),
            )

            AppNavStackLayers(
                navStack = navStack,
                exitingLayer = navController.exitingLayer,
                pendingEnterKey = navController.pendingEnterKey,
                pendingExitKey = navController.pendingExitKey,
                onClearPendingEnter = navController::clearPendingEnterKey,
                onClearPendingExit = ::completeNavPopSideEffects,
                onExitHidden = ::completeNavPopSideEffects,
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
                onUpdateVideoSeed = ::updateVideoDetailSeed,
                onReplaceVideo = ::replaceVideoDetail,
                onOpenProfile = ::openUserProfile,
                onOpenDynamic = ::openDynamicDetail,
                onOpenArticle = ::openArticleDetail,
                onOpenRelationList = ::openUserRelationList,
                onLoginRequired = { showLoginSheet = true },
                onEnsurePlayStream = { video -> scope.launch { resolvePlayUrl(video) } },
                onRefreshPlayStream = ::refreshPlayStream,
                episodeSwitchScope = scope,
            )

            if (fullscreenKey != null && fullscreenVideo != null && fullscreenPlayStream != null) {
                val stream = fullscreenPlayStream
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
                        .zIndex(NavFullscreenZIndex),
                ) {
                    BilibiliVideoSurface(
                        playbackKey = fullscreenKey,
                        contentPlaybackKey = videoPlaybackKey(
                            fullscreenVideo.playbackId(),
                            ownerId = "detail",
                        ),
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
                        historyVideo = fullscreenVideo,
                        onStreamSourceError = { refreshPlayStream(fullscreenVideo) },
                    )
                }
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
                    visible = bottomBarVisible,
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

            if (!liveRoomOpen) {
                AnimatedVisibility(
                    visible = bottomBarVisible,
                    enter = slideInVertically(tween(200)) { it },
                    exit = slideOutVertically(tween(160)) { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(20f),
                ) {
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
                    )
                }
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
}

private data class NavRenderLayer(
    val entry: AppNavEntry,
    val index: Int,
    val target: AppNavEntry?,
) {
    val key: String get() = entry.stableKey(index)
}

@Composable
private fun AppNavStackLayers(
    navStack: List<AppNavEntry>,
    exitingLayer: NavExitingLayer?,
    pendingEnterKey: String?,
    pendingExitKey: String?,
    onClearPendingEnter: () -> Unit,
    onClearPendingExit: () -> Unit,
    onExitHidden: () -> Unit,
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
    onSwitchVideoPart: (BiliVideoItem, BiliVideoPage, BiliPlayStream?, Boolean) -> Unit,
    onUpdateVideoSeed: (BiliVideoItem, BiliPlayStream) -> Unit,
    onReplaceVideo: (BiliVideoItem, BiliPlayStream) -> Unit,
    onOpenProfile: (Long, String, String) -> Unit,
    onOpenDynamic: (BiliDynamicItem) -> Unit,
    onOpenArticle: (String, String) -> Unit,
    onOpenRelationList: (Long, String, String, String, UserRelationTab) -> Unit,
    onLoginRequired: () -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
    onRefreshPlayStream: (BiliVideoItem) -> Unit,
    episodeSwitchScope: CoroutineScope,
) {
    val topActiveKey = navStack.lastOrNull()?.let { entry ->
        entry.stableKey(navStack.lastIndex)
    }
    val renderLayers = buildList {
        navStack.forEachIndexed { index, entry ->
            add(NavRenderLayer(entry = entry, index = index, target = entry))
        }
        exitingLayer?.let { exiting ->
            add(NavRenderLayer(entry = exiting.entry, index = exiting.index, target = null))
        }
    }

    renderLayers.forEach { layer ->
        key(layer.key) {
            val isTop = layer.target != null && layer.key == topActiveKey
            val isExitingLayer = layer.target == null && pendingExitKey == layer.key
            NavAnimatedOverlay(
                target = layer.target,
                exitSeed = if (layer.target == null) layer.entry else null,
                modifier = Modifier.fillMaxSize(),
                stackTop = isTop || isExitingLayer,
                layerBaseZIndex = 90f + layer.index,
                visible = isTop,
                animationKey = layer.key,
                layerKey = layer.key,
                pendingEnterKey = pendingEnterKey,
                pendingExitKey = pendingExitKey,
                onClearPendingEnter = onClearPendingEnter,
                onClearPendingExit = onClearPendingExit,
                onHidden = onExitHidden,
            ) { activeEntry ->
                Box(Modifier.fillMaxSize().blockHiddenTouches(isTop || isExitingLayer)) {
                    AppNavEntryContent(
                        entry = activeEntry,
                        isActive = isTop,
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
                        onUpdateVideoSeed = onUpdateVideoSeed,
                        onReplaceVideo = onReplaceVideo,
                        onOpenProfile = onOpenProfile,
                        onOpenDynamic = onOpenDynamic,
                        onOpenArticle = onOpenArticle,
                        onOpenRelationList = onOpenRelationList,
                        onLoginRequired = onLoginRequired,
                        onEnsurePlayStream = onEnsurePlayStream,
                        onRefreshPlayStream = onRefreshPlayStream,
                        episodeSwitchScope = episodeSwitchScope,
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
    onSwitchVideoPart: (BiliVideoItem, BiliVideoPage, BiliPlayStream?, Boolean) -> Unit,
    onUpdateVideoSeed: (BiliVideoItem, BiliPlayStream) -> Unit,
    onReplaceVideo: (BiliVideoItem, BiliPlayStream) -> Unit,
    onOpenProfile: (Long, String, String) -> Unit,
    onOpenDynamic: (BiliDynamicItem) -> Unit,
    onOpenArticle: (String, String) -> Unit,
    onOpenRelationList: (Long, String, String, String, UserRelationTab) -> Unit,
    onLoginRequired: () -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
    onRefreshPlayStream: (BiliVideoItem) -> Unit,
    episodeSwitchScope: CoroutineScope,
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
            val detailStateKey = entry.video.bvid.ifBlank { "av:${entry.video.aid}" }
                .ifBlank { entry.video.playbackId() }
            key(detailStateKey) {
            VideoDetailScreen(
                seedVideo = entry.video,
                playStream = resolveStoredPlayStream(entry.video, playUrls),
                initialProgressSeconds = entry.progressSeconds,
                api = api,
                coordinator = coordinator,
                credential = credential,
                myMid = myMid,
                onLoginRequired = onLoginRequired,
                onAuthorClick = { profile ->
                    onOpenProfile(profile.mid, profile.name, profile.face)
                },
                onSwitchVideoPart = onSwitchVideoPart,
                onUpdateVideoSeed = onUpdateVideoSeed,
                onReplaceVideo = onReplaceVideo,
                onOpenUgcEpisode = { video ->
                    onOpenVideo(video, 0)
                },
                onOpenDescriptionVideo = onOpenDescriptionVideo,
                playbackActive = isActive,
                onStreamSourceError = onRefreshPlayStream,
                episodeSwitchScope = episodeSwitchScope,
                modifier = Modifier.fillMaxSize(),
            )
            }
        }
        is AppNavEntry.UserProfile -> {
            BackHandler(enabled = isActive, onBack = onPopNav)
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
private fun KeepAliveTabLayer(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (visible) 1f else 0f)
            .zIndex(if (visible) 0f else -1f)
            .blockHiddenTouches(visible),
    ) {
        content()
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
