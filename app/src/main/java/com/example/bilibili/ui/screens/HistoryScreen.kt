package com.example.bilibili.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BiliHistoryCursor
import com.example.bilibili.data.BiliHistoryItem
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.data.FeedLayoutStore
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.ui.BindFeedTabReselectEffect
import com.example.bilibili.ui.FeedTabReselectHandler
import com.example.bilibili.ui.isScrolledToTop
import com.example.bilibili.ui.LocalFeedTabReselectController
import com.example.bilibili.ui.MainTab
import com.example.bilibili.ui.components.ActionMenuDestructiveColor
import com.example.bilibili.ui.components.ActionMenuOverlay
import com.example.bilibili.ui.components.ActionMenuRequest
import com.example.bilibili.ui.components.ActionMenuOneRowHeight
import com.example.bilibili.ui.components.ActionMenuRow
import com.example.bilibili.ui.components.ObserveListNearEnd
import com.example.bilibili.ui.components.ObserveStaggeredGridNearEnd
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.SlidingTextTabs
import com.example.bilibili.ui.components.UpAuthorBadge
import com.example.bilibili.ui.components.VideoCoverBottomScrim
import com.example.bilibili.ui.components.VideoCoverOverlayText
import com.example.bilibili.ui.components.VideoFeedCard
import com.example.bilibili.ui.format.formatBiliHistorySectionLabel
import com.example.bilibili.ui.format.formatBiliHistoryViewTime
import com.example.bilibili.ui.format.formatHistoryDurationBadge
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private enum class HistoryContentTab(val label: String) {
    History("历史"),
    Favorites("收藏"),
}

private data class HistoryEntry(
    val item: BiliHistoryItem,
    val sectionLabel: String,
    val showSectionHeader: Boolean,
)

private val HistorySectionHeaderHeight = 32.dp
private val HistoryCoverWidth = 136.dp
private val HistoryCoverHeight = 77.dp

private fun buildHistoryEntries(items: List<BiliHistoryItem>): List<HistoryEntry> {
    if (items.isEmpty()) return emptyList()
    val entries = mutableListOf<HistoryEntry>()
    var currentSection: String? = null
    items.forEach { item ->
        val section = formatBiliHistorySectionLabel(item.viewAtSeconds)
        val isFirstInSection = section != currentSection
        if (isFirstInSection) currentSection = section
        entries += HistoryEntry(
            item = item,
            sectionLabel = section,
            showSectionHeader = isFirstInSection,
        )
    }
    return entries
}

private fun resolveStickyHistorySectionLabel(
    listState: LazyListState,
    entries: List<HistoryEntry>,
    sectionHeaderHeightPx: Int,
): String? {
    if (entries.isEmpty()) return null
    val topRowIndex = listState.layoutInfo.visibleItemsInfo
        .mapNotNull { info -> info.index.takeIf { it in entries.indices } }
        .minOrNull() ?: return null
    val sectionLabel = entries[topRowIndex].sectionLabel
    val firstRowIndex = entries.indexOfFirst { entry ->
        entry.sectionLabel == sectionLabel && entry.showSectionHeader
    }
    if (firstRowIndex < 0) return null
    val firstVisible = listState.firstVisibleItemIndex
    val scrollOffset = listState.firstVisibleItemScrollOffset
    val showSticky = when {
        firstVisible > firstRowIndex -> true
        firstVisible == firstRowIndex -> scrollOffset >= sectionHeaderHeightPx
        else -> false
    }
    return if (showSticky) sectionLabel else null
}

internal data class HistoryActionMenuRequest(
    val kid: String,
    val anchorBoundsInRoot: Rect,
)

class HistoryMenuController {
    internal var request by mutableStateOf<HistoryActionMenuRequest?>(null)
    internal var visible by mutableStateOf(false)
    internal var onDelete: ((String) -> Unit)? = null
    internal var openGeneration by mutableStateOf(0)

    fun open(
        kid: String,
        anchorBoundsInRoot: Rect,
        onDelete: (String) -> Unit,
    ) {
        if (anchorBoundsInRoot.width <= 0f || anchorBoundsInRoot.height <= 0f) return
        if (visible && request?.kid == kid) {
            dismiss()
            return
        }
        request = HistoryActionMenuRequest(kid, anchorBoundsInRoot)
        this.onDelete = onDelete
        openGeneration++
        visible = true
    }

    fun dismiss() {
        visible = false
    }

    fun dismissIfShowing(kid: String) {
        if (visible && request?.kid == kid) {
            dismiss()
        }
    }
}

@Composable
fun rememberHistoryMenuController(): HistoryMenuController = remember { HistoryMenuController() }

@Composable
fun HistoryMenuOverlay(
    controller: HistoryMenuController,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    ActionMenuOverlay(
        activeRequest = controller.request?.let {
            ActionMenuRequest(anchorBoundsInRoot = it.anchorBoundsInRoot)
        },
        menuVisible = controller.visible,
        menuRevealKey = controller.openGeneration,
        menuHeight = ActionMenuOneRowHeight,
        menuLabels = listOf("删除"),
        onDismiss = { controller.dismiss() },
        backdrop = backdrop,
        useFeedCardAlignment = false,
        menuContainerColor = ActionMenuDestructiveColor,
        zIndex = 50f,
        modifier = modifier,
    ) {
        ActionMenuRow(
            label = "删除",
            destructive = true,
            onClick = {
                val kid = controller.request?.kid ?: return@ActionMenuRow
                controller.dismiss()
                controller.onDelete?.invoke(kid)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    loggedIn: Boolean,
    onLoginClick: () -> Unit,
    onHistoryItemClick: (BiliHistoryItem) -> Unit,
    onVideoClick: (BiliVideoItem) -> Unit,
    playUrls: Map<String, BiliPlayStream>,
    coordinator: VideoPlaybackCoordinator,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
    onAuthorClick: (Long) -> Unit = {},
    menuController: HistoryMenuController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    pullRefreshState: PullToRefreshState = rememberPullToRefreshState(),
    showEmbeddedPullRefreshIndicator: Boolean = false,
    onPullRefreshingChange: (Boolean) -> Unit = {},
    feedColumnCount: Int = FeedLayoutStore.COLUMN_COUNT_TWO,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val historyListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()
    val favoritesGridState = rememberLazyStaggeredGridState()
    val useSingleColumnFavorites = feedColumnCount == FeedLayoutStore.COLUMN_COUNT_ONE
    val sectionHeaderHeightPx = remember(density) {
        with(density) { HistorySectionHeaderHeight.roundToPx() }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { HistoryContentTab.entries.size },
    )
    val tabScrollPosition by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }

    var historyItems by remember { mutableStateOf<List<BiliHistoryItem>>(emptyList()) }
    var historyCursor by remember { mutableStateOf<BiliHistoryCursor?>(null) }
    var historyLoading by remember { mutableStateOf(false) }
    var historyLoadingMore by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf<String?>(null) }

    var favoriteVideos by remember { mutableStateOf<List<BiliVideoItem>>(emptyList()) }
    var favoritePage by remember { mutableStateOf(1) }
    var favoriteHasMore by remember { mutableStateOf(true) }
    var favoriteLoading by remember { mutableStateOf(false) }
    var favoriteLoadingMore by remember { mutableStateOf(false) }
    var favoriteError by remember { mutableStateOf<String?>(null) }
    var favoritesLoaded by remember { mutableStateOf(false) }

    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshing) {
        onPullRefreshingChange(refreshing)
    }

    suspend fun loadHistory(reset: Boolean, scrollToTopWhenDone: Boolean = false) {
        val cred = credential ?: return
        if (reset) {
            historyLoading = true
            historyError = null
        } else {
            if (historyLoadingMore || historyCursor?.hasMore != true) return
            historyLoadingMore = true
        }
        try {
            val page = api.getWatchHistory(
                credential = cred,
                max = if (reset) 0L else historyCursor?.max ?: 0L,
                viewAt = if (reset) 0L else historyCursor?.viewAt ?: 0L,
                business = if (reset) "" else historyCursor?.business.orEmpty(),
            )
            if (reset) {
                historyItems = page.items
            } else {
                historyItems = (historyItems + page.items).distinctBy { it.kid }
            }
            historyCursor = page.cursor
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (reset) {
                historyError = e.message ?: "加载失败"
                historyItems = emptyList()
            }
        } finally {
            historyLoading = false
            historyLoadingMore = false
            if (scrollToTopWhenDone) {
                historyListState.animateScrollToItem(0)
            }
        }
    }

    suspend fun loadFavorites(reset: Boolean, scrollToTopWhenDone: Boolean = false) {
        val cred = credential ?: return
        if (reset) {
            favoriteLoading = true
            favoriteError = null
            favoritePage = 1
            favoriteHasMore = true
        } else {
            if (favoriteLoadingMore || !favoriteHasMore) return
            favoriteLoadingMore = true
        }
        try {
            val nextPage = if (reset) 1 else favoritePage + 1
            val page = api.getFavoriteVideoPage(credential = cred, page = nextPage)
            favoriteVideos = if (reset) {
                page.videos
            } else {
                (favoriteVideos + page.videos).distinctBy { it.bvid }
            }
            favoritePage = nextPage
            favoriteHasMore = page.hasMore && page.videos.isNotEmpty()
            favoritesLoaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (reset) {
                favoriteError = e.message ?: "加载失败"
                favoriteVideos = emptyList()
            }
        } finally {
            favoriteLoading = false
            favoriteLoadingMore = false
            if (scrollToTopWhenDone) {
                if (useSingleColumnFavorites) {
                    favoritesListState.animateScrollToItem(0)
                } else {
                    favoritesGridState.animateScrollToItem(0)
                }
            }
        }
    }

    fun refreshCurrentTab(scrollToTopWhenDone: Boolean = false) {
        scope.launch {
            refreshing = true
            try {
                when (HistoryContentTab.entries[pagerState.currentPage]) {
                    HistoryContentTab.History -> loadHistory(reset = true, scrollToTopWhenDone = scrollToTopWhenDone)
                    HistoryContentTab.Favorites -> loadFavorites(reset = true, scrollToTopWhenDone = scrollToTopWhenDone)
                }
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(loggedIn, credential?.dedeUserId) {
        if (loggedIn && credential != null) {
            loadHistory(reset = true)
            favoritesLoaded = false
            favoriteVideos = emptyList()
        } else {
            historyItems = emptyList()
            historyCursor = null
            favoriteVideos = emptyList()
            favoritesLoaded = false
            favoriteHasMore = true
        }
    }

    LaunchedEffect(pagerState.currentPage, loggedIn, credential?.dedeUserId) {
        if (!loggedIn || credential == null) return@LaunchedEffect
        if (pagerState.currentPage == HistoryContentTab.Favorites.ordinal && !favoritesLoaded && !favoriteLoading) {
            loadFavorites(reset = true)
        }
    }

    LaunchedEffect(historyListState, historyCursor?.hasMore, loggedIn) {
        if (!loggedIn || pagerState.currentPage != HistoryContentTab.History.ordinal) return@LaunchedEffect
        snapshotFlow {
            val info = historyListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            if (total <= 0 || last < total - 4) null else last
        }
            .distinctUntilChanged()
            .filterNotNull()
            .collect { loadHistory(reset = false) }
    }

    ObserveListNearEnd(
        listState = favoritesListState,
        enabled = loggedIn &&
            useSingleColumnFavorites &&
            pagerState.currentPage == HistoryContentTab.Favorites.ordinal &&
            favoriteHasMore &&
            !favoriteLoadingMore,
        onNearEnd = { scope.launch { loadFavorites(reset = false) } },
    )
    ObserveStaggeredGridNearEnd(
        gridState = favoritesGridState,
        enabled = loggedIn &&
            !useSingleColumnFavorites &&
            pagerState.currentPage == HistoryContentTab.Favorites.ordinal &&
            favoriteHasMore &&
            !favoriteLoadingMore,
        onNearEnd = { scope.launch { loadFavorites(reset = false) } },
    )

    val feedTabReselectController = LocalFeedTabReselectController.current
    if (feedTabReselectController != null) {
        DisposableEffect(
            feedTabReselectController,
            historyListState,
            favoritesListState,
            favoritesGridState,
            useSingleColumnFavorites,
            pagerState,
        ) {
            feedTabReselectController.register(
                MainTab.History,
                FeedTabReselectHandler(
                    isAtTop = {
                        when (HistoryContentTab.entries[pagerState.currentPage]) {
                            HistoryContentTab.History -> historyListState.isScrolledToTop()
                            HistoryContentTab.Favorites -> if (useSingleColumnFavorites) {
                                favoritesListState.isScrolledToTop()
                            } else {
                                favoritesGridState.isScrolledToTop()
                            }
                        }
                    },
                    scrollToTop = {
                        when (HistoryContentTab.entries[pagerState.currentPage]) {
                            HistoryContentTab.History -> historyListState.animateScrollToItem(0)
                            HistoryContentTab.Favorites -> if (useSingleColumnFavorites) {
                                favoritesListState.animateScrollToItem(0)
                            } else {
                                favoritesGridState.animateScrollToItem(0)
                            }
                        }
                    },
                    refresh = { refreshCurrentTab(scrollToTopWhenDone = true) },
                ),
            )
            onDispose { feedTabReselectController.unregister(MainTab.History) }
        }
    }

    if (!loggedIn) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("登录后查看历史与收藏", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onLoginClick) {
                    Text("打开哔哩哔哩登录")
                }
            }
        }
        return
    }

    val historyEntries = remember(historyItems) { buildHistoryEntries(historyItems) }
    val stickySection by remember(historyEntries, historyListState, sectionHeaderHeightPx) {
        derivedStateOf {
            resolveStickyHistorySectionLabel(historyListState, historyEntries, sectionHeaderHeightPx)
        }
    }
    val listTopInset = contentPadding.calculateTopPadding()
    val listBottomInset = contentPadding.calculateBottomPadding() + 88.dp

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshCurrentTab(scrollToTopWhenDone = true) },
        state = pullRefreshState,
        indicator = {
            if (showEmbeddedPullRefreshIndicator) {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(2f),
                    isRefreshing = refreshing,
                    state = pullRefreshState,
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(top = listTopInset),
            ) {
                HistoryContentTabBar(
                    scrollPosition = tabScrollPosition,
                    onTabSelected = { tab ->
                        val sameTab = tab == HistoryContentTab.entries[pagerState.currentPage]
                        if (sameTab) {
                            scope.launch {
                                when (tab) {
                                    HistoryContentTab.History -> historyListState.animateScrollToItem(0)
                                    HistoryContentTab.Favorites -> if (useSingleColumnFavorites) {
                                        favoritesListState.animateScrollToItem(0)
                                    } else {
                                        favoritesGridState.animateScrollToItem(0)
                                    }
                                }
                            }
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(HistoryContentTab.entries.indexOf(tab))
                            }
                        }
                    },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                beyondViewportPageCount = 1,
            ) { page ->
                when (HistoryContentTab.entries[page]) {
                    HistoryContentTab.History -> {
                        when {
                            historyLoading && historyItems.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            historyError != null && historyItems.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(historyError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                }
                            }
                            historyItems.isEmpty() && !historyLoading -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "暂无观看历史",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            else -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        state = historyListState,
                                        contentPadding = PaddingValues(bottom = listBottomInset),
                                    ) {
                                        items(
                                            items = historyEntries,
                                            key = { it.item.kid },
                                        ) { entry ->
                                            Column(
                                                modifier = Modifier.animateItem(
                                                    placementSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMedium,
                                                    ),
                                                ),
                                            ) {
                                                if (entry.showSectionHeader) {
                                                    HistorySectionHeader(entry.sectionLabel)
                                                }
                                                HistoryItemRow(
                                                    item = entry.item,
                                                    onClick = {
                                                        onHistoryItemClick(entry.item)
                                                    },
                                                    onMoreClick = { anchor ->
                                                        menuController.open(entry.item.kid, anchor) { kid ->
                                                            val cred = credential ?: return@open
                                                            scope.launch {
                                                                val deleted = api.deleteWatchHistory(kid, cred)
                                                                if (deleted) {
                                                                    menuController.dismissIfShowing(kid)
                                                                    historyItems = historyItems.filterNot { it.kid == kid }
                                                                } else {
                                                                    Toast.makeText(
                                                                        context,
                                                                        "删除失败",
                                                                        Toast.LENGTH_SHORT,
                                                                    ).show()
                                                                }
                                                            }
                                                        }
                                                    },
                                                )
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(start = 16.dp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                                                )
                                            }
                                        }
                                        if (historyLoadingMore) {
                                            item(key = "history-loading-more") {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 16.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                }
                                            }
                                        }
                                    }
                                    stickySection?.let { label ->
                                        HistoryStickySectionHeader(
                                            label = label,
                                            modifier = Modifier.align(Alignment.TopStart),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HistoryContentTab.Favorites -> {
                        when {
                            favoriteLoading && favoriteVideos.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            favoriteError != null && favoriteVideos.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(favoriteError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                }
                            }
                            favoriteVideos.isEmpty() && !favoriteLoading -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "暂无收藏视频",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            useSingleColumnFavorites -> {
                                LazyColumn(
                                    state = favoritesListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = HomeFeedSingleColumnHorizontalPadding,
                                        end = HomeFeedSingleColumnHorizontalPadding,
                                        bottom = listBottomInset,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(HomeFeedSingleColumnSpacing),
                                ) {
                                    items(favoriteVideos, key = { it.bvid }) { video ->
                                        VideoFeedCard(
                                            video = video,
                                            playStream = playUrls[video.bvid],
                                            coordinator = coordinator,
                                            onClick = { onVideoClick(video) },
                                            onEnsurePlayStream = { onEnsurePlayStream(video) },
                                            onAuthorClick = onAuthorClick,
                                            overlayMetaOnCover = true,
                                        )
                                    }
                                    if (favoriteLoadingMore) {
                                        item(key = "favorites-loading-more") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Fixed(2),
                                    state = favoritesGridState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = HomeFeedGridHorizontalPadding,
                                        end = HomeFeedGridHorizontalPadding,
                                        bottom = listBottomInset,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(HomeFeedGridSpacing),
                                    verticalItemSpacing = HomeFeedGridSpacing,
                                ) {
                                    items(favoriteVideos, key = { it.bvid }) { video ->
                                        VideoFeedCard(
                                            video = video,
                                            playStream = playUrls[video.bvid],
                                            coordinator = coordinator,
                                            onClick = { onVideoClick(video) },
                                            onEnsurePlayStream = { onEnsurePlayStream(video) },
                                            onAuthorClick = onAuthorClick,
                                            gridStyle = true,
                                        )
                                    }
                                    if (favoriteLoadingMore) {
                                        item(key = "favorites-loading-more", span = StaggeredGridItemSpan.FullLine) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun HistoryContentTabBar(
    scrollPosition: Float,
    onTabSelected: (HistoryContentTab) -> Unit,
) {
    val tabs = HistoryContentTab.entries
    SlidingTextTabs(
        labels = tabs.map { it.label },
        scrollPosition = scrollPosition,
        onTabSelected = { index -> onTabSelected(tabs[index]) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HistorySectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 2.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun HistoryStickySectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    HistorySectionHeader(
        label = label,
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White),
    )
}

@Composable
private fun HistoryItemRow(
    item: BiliHistoryItem,
    onClick: () -> Unit,
    onMoreClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        HistoryThumbnail(
            coverUrl = item.coverUrl,
            progressSeconds = item.progressSeconds,
            durationSeconds = item.durationSeconds,
            modifier = Modifier
                .width(HistoryCoverWidth)
                .height(HistoryCoverHeight)
                .clickable(onClick = onClick),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .height(HistoryCoverHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
            ) {
                Text(
                    text = item.title.ifBlank { "未命名视频" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.authorName.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.height(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UpAuthorBadge()
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = item.authorName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatBiliHistoryViewTime(item.viewAtSeconds),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onClick),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                var menuCoordinates by remember(item.kid) { mutableStateOf<LayoutCoordinates?>(null) }
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    modifier = Modifier
                        .size(24.dp)
                        .onGloballyPositioned { coordinates ->
                            if (coordinates.isAttached) {
                                menuCoordinates = coordinates
                            }
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {
                                val coordinates = menuCoordinates?.takeIf { it.isAttached } ?: return@clickable
                                onMoreClick(coordinates.boundsInRoot())
                            },
                        )
                        .padding(3.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryThumbnail(
    coverUrl: String,
    progressSeconds: Int,
    durationSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val progressFraction = when {
        durationSeconds <= 0 -> 0f
        progressSeconds <= 0 -> 0f
        progressSeconds >= durationSeconds -> 1f
        else -> progressSeconds.toFloat() / durationSeconds.toFloat()
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(6.dp)),
    ) {
        RemoteImage(
            url = coverUrl,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        VideoCoverBottomScrim(
            coverUrl = coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter),
        )
        if (progressFraction in 0.001f..0.999f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxSize()
                        .background(Color(0xFF00AEEC)),
                )
            }
        }
        VideoCoverOverlayText(
            text = formatHistoryDurationBadge(progressSeconds, durationSeconds),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp),
        )
    }
}