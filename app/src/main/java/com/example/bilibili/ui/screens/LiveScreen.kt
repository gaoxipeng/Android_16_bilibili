package com.example.bilibili.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BiliLiveArea
import com.example.bilibili.data.BiliLiveRoom
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.ui.FeedTabReselectHandler
import com.example.bilibili.ui.LocalFeedTabReselectController
import com.example.bilibili.ui.MainTab
import com.example.bilibili.ui.components.ObserveGridNearEnd
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.VideoCoverBottomScrim
import com.example.bilibili.ui.components.VideoCoverOverlayText
import com.example.bilibili.ui.components.VideoFeedCardCorner
import com.example.bilibili.ui.components.SlidingTextTabs
import com.example.bilibili.ui.isScrolledToTop
import com.example.bilibili.ui.liquidglass.BottomBarFeedOverlapReserve
import com.example.bilibili.ui.theme.BiliPink
import com.example.bilibili.player.VideoPlaybackCoordinator
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private enum class LiveContentTab(val label: String) {
    Following("关注"),
    Recommend("推荐"),
    Hot("人气"),
}

private val LiveAllArea = BiliLiveArea(id = 0L, name = "全部")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    loggedIn: Boolean,
    coordinator: VideoPlaybackCoordinator,
    onLoginClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    pullRefreshState: PullToRefreshState = rememberPullToRefreshState(),
    showEmbeddedPullRefreshIndicator: Boolean = true,
    onLiveRoomOpenChange: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { LiveContentTab.entries.size },
    )
    val tabScrollPosition by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val hotGridState = rememberLazyGridState()
    val recommendGridState = rememberLazyGridState()
    val followingGridState = rememberLazyGridState()

    var hotRooms by remember { mutableStateOf<List<BiliLiveRoom>>(emptyList()) }
    var hotLoading by remember { mutableStateOf(false) }
    var hotError by remember { mutableStateOf<String?>(null) }

    var parentAreas by remember { mutableStateOf<List<BiliLiveArea>>(emptyList()) }
    var selectedParentAreaId by remember { mutableLongStateOf(0L) }
    var recommendRooms by remember { mutableStateOf<List<BiliLiveRoom>>(emptyList()) }
    var recommendPage by remember { mutableIntStateOf(1) }
    var recommendHasMore by remember { mutableStateOf(true) }
    var recommendLoading by remember { mutableStateOf(false) }
    var recommendLoadingMore by remember { mutableStateOf(false) }
    var recommendError by remember { mutableStateOf<String?>(null) }

    var followingRooms by remember { mutableStateOf<List<BiliLiveRoom>>(emptyList()) }
    var followingLoading by remember { mutableStateOf(false) }
    var followingError by remember { mutableStateOf<String?>(null) }
    var followingLoaded by remember { mutableStateOf(false) }

    var refreshing by remember { mutableStateOf(false) }
    var activeRoom by remember { mutableStateOf<BiliLiveRoom?>(null) }

    LaunchedEffect(activeRoom) {
        onLiveRoomOpenChange(activeRoom != null)
    }
    DisposableEffect(Unit) {
        onDispose { onLiveRoomOpenChange(false) }
    }

    val listTopInset = contentPadding.calculateTopPadding()
    val listBottomInset = contentPadding.calculateBottomPadding() + BottomBarFeedOverlapReserve

    suspend fun loadHot(reset: Boolean = true) {
        if (hotLoading && reset) return
        if (reset) {
            hotLoading = true
            hotError = null
        }
        try {
            hotRooms = api.getLiveHotRank()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (reset) {
                hotError = e.message ?: "加载失败"
                hotRooms = emptyList()
            }
        } finally {
            if (reset) hotLoading = false
        }
    }

    suspend fun loadRecommend(reset: Boolean) {
        if (reset) {
            if (recommendLoading) return
            recommendLoading = true
            recommendError = null
            recommendPage = 1
            recommendHasMore = true
        } else {
            if (recommendLoadingMore || !recommendHasMore) return
            recommendLoadingMore = true
        }
        try {
            val page = if (reset) 1 else recommendPage + 1
            val result = api.getLiveRoomList(
                parentAreaId = selectedParentAreaId,
                areaId = 0L,
                page = page,
                credential = credential,
            )
            recommendRooms = if (reset) {
                result.rooms
            } else {
                (recommendRooms + result.rooms).distinctBy { it.roomId }
            }
            recommendPage = page
            recommendHasMore = result.hasMore && result.rooms.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (reset) {
                recommendError = e.message ?: "加载失败"
                recommendRooms = emptyList()
            }
        } finally {
            if (reset) recommendLoading = false else recommendLoadingMore = false
        }
    }

    suspend fun loadFollowing(reset: Boolean = true) {
        val cred = credential ?: return
        if (followingLoading && reset) return
        if (reset) {
            followingLoading = true
            followingError = null
        }
        try {
            followingRooms = api.getLiveFollowingRooms(cred)
            followingLoaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (reset) {
                followingError = e.message ?: "加载失败"
                followingRooms = emptyList()
            }
        } finally {
            if (reset) followingLoading = false
        }
    }

    suspend fun loadParentAreas() {
        if (parentAreas.isNotEmpty()) return
        runCatching {
            parentAreas = api.getLiveAreas()
        }
    }

    fun refreshCurrentTab(scrollToTopWhenDone: Boolean = false) {
        scope.launch {
            refreshing = true
            try {
                when (LiveContentTab.entries[pagerState.currentPage]) {
                    LiveContentTab.Hot -> loadHot(reset = true)
                    LiveContentTab.Recommend -> {
                        loadParentAreas()
                        loadRecommend(reset = true)
                    }
                    LiveContentTab.Following -> loadFollowing(reset = true)
                }
                if (scrollToTopWhenDone) {
                    when (LiveContentTab.entries[pagerState.currentPage]) {
                        LiveContentTab.Hot -> hotGridState.animateScrollToItem(0)
                        LiveContentTab.Recommend -> recommendGridState.animateScrollToItem(0)
                        LiveContentTab.Following -> followingGridState.animateScrollToItem(0)
                    }
                }
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadParentAreas()
        loadRecommend(reset = true)
        if (loggedIn && credential != null) {
            loadFollowing(reset = true)
        }
    }

    LaunchedEffect(pagerState.currentPage, loggedIn, credential?.dedeUserId) {
        when (LiveContentTab.entries[pagerState.currentPage]) {
            LiveContentTab.Following -> {
                if (loggedIn && credential != null && !followingLoaded && !followingLoading) {
                    loadFollowing(reset = true)
                }
            }
            LiveContentTab.Hot -> {
                if (hotRooms.isEmpty() && !hotLoading) {
                    loadHot(reset = true)
                }
            }
            else -> Unit
        }
    }

    LaunchedEffect(selectedParentAreaId) {
        if (pagerState.currentPage == LiveContentTab.Recommend.ordinal) {
            loadRecommend(reset = true)
        }
    }

    ObserveGridNearEnd(
        gridState = recommendGridState,
        enabled = pagerState.currentPage == LiveContentTab.Recommend.ordinal &&
            recommendHasMore &&
            !recommendLoading &&
            !recommendLoadingMore,
        onNearEnd = { scope.launch { loadRecommend(reset = false) } },
    )

    val feedTabReselectController = LocalFeedTabReselectController.current
    if (feedTabReselectController != null) {
        DisposableEffect(
            feedTabReselectController,
            pagerState,
            hotGridState,
            recommendGridState,
            followingGridState,
        ) {
            feedTabReselectController.register(
                MainTab.Live,
                FeedTabReselectHandler(
                    isAtTop = {
                        when (LiveContentTab.entries[pagerState.currentPage]) {
                            LiveContentTab.Hot -> hotGridState.isScrolledToTop()
                            LiveContentTab.Recommend -> recommendGridState.isScrolledToTop()
                            LiveContentTab.Following -> followingGridState.isScrolledToTop()
                        }
                    },
                    scrollToTop = {
                        when (LiveContentTab.entries[pagerState.currentPage]) {
                            LiveContentTab.Hot -> hotGridState.animateScrollToItem(0)
                            LiveContentTab.Recommend -> recommendGridState.animateScrollToItem(0)
                            LiveContentTab.Following -> followingGridState.animateScrollToItem(0)
                        }
                    },
                    refresh = { refreshCurrentTab(scrollToTopWhenDone = true) },
                ),
            )
            onDispose { feedTabReselectController.unregister(MainTab.Live) }
        }
    }

    val activeRefreshing = when (LiveContentTab.entries[pagerState.currentPage]) {
        LiveContentTab.Hot -> hotLoading
        LiveContentTab.Recommend -> recommendLoading
        LiveContentTab.Following -> followingLoading
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing || activeRefreshing,
            onRefresh = { refreshCurrentTab(scrollToTopWhenDone = true) },
            state = pullRefreshState,
            indicator = {
                if (showEmbeddedPullRefreshIndicator) {
                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(2f),
                        isRefreshing = refreshing || activeRefreshing,
                        state = pullRefreshState,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(top = listTopInset),
                ) {
                    LiveContentTabBar(
                        scrollPosition = tabScrollPosition,
                        onTabSelected = { tab ->
                            val sameTab = tab == LiveContentTab.entries[pagerState.currentPage]
                            if (sameTab) {
                                scope.launch {
                                    when (tab) {
                                        LiveContentTab.Hot -> hotGridState.animateScrollToItem(0)
                                        LiveContentTab.Recommend -> recommendGridState.animateScrollToItem(0)
                                        LiveContentTab.Following -> followingGridState.animateScrollToItem(0)
                                    }
                                }
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(LiveContentTab.entries.indexOf(tab))
                                }
                            }
                        },
                    )
                }

                if (pagerState.currentPage == LiveContentTab.Recommend.ordinal) {
                    LiveAreaFilterRow(
                        areas = listOf(LiveAllArea) + parentAreas,
                        selectedAreaId = selectedParentAreaId,
                        onAreaSelected = { selectedParentAreaId = it },
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    beyondViewportPageCount = 2,
                    userScrollEnabled = true,
                ) { page ->
                    when (LiveContentTab.entries[page]) {
                        LiveContentTab.Hot -> LiveRoomGridPage(
                            rooms = hotRooms,
                            loading = hotLoading,
                            error = hotError,
                            emptyHint = "暂无人气直播",
                            gridState = hotGridState,
                            contentPadding = PaddingValues(bottom = listBottomInset),
                            onRoomClick = { room -> activeRoom = room },
                        )
                        LiveContentTab.Recommend -> LiveRoomGridPage(
                            rooms = recommendRooms,
                            loading = recommendLoading,
                            error = recommendError,
                            emptyHint = "暂无推荐直播",
                            gridState = recommendGridState,
                            contentPadding = PaddingValues(bottom = listBottomInset),
                            loadingMore = recommendLoadingMore,
                            onRoomClick = { room -> activeRoom = room },
                        )
                        LiveContentTab.Following -> {
                            when {
                                !loggedIn -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = listBottomInset),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "登录后查看关注主播的直播",
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Button(onClick = onLoginClick) {
                                                Text("打开哔哩哔哩登录")
                                            }
                                        }
                                    }
                                }
                                else -> LiveRoomGridPage(
                                    rooms = followingRooms,
                                    loading = followingLoading,
                                    error = followingError,
                                    emptyHint = "关注的主播暂无开播",
                                    gridState = followingGridState,
                                    contentPadding = PaddingValues(bottom = listBottomInset),
                                    onRoomClick = { room -> activeRoom = room },
                                )
                            }
                        }
                    }
                }
            }
        }

        activeRoom?.let { room ->
            LiveRoomScreen(
                room = room,
                api = api,
                credential = credential,
                coordinator = coordinator,
                onBack = { activeRoom = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LiveContentTabBar(
    scrollPosition: Float,
    onTabSelected: (LiveContentTab) -> Unit,
) {
    val tabs = LiveContentTab.entries
    SlidingTextTabs(
        labels = tabs.map { it.label },
        scrollPosition = scrollPosition,
        onTabSelected = { index -> onTabSelected(tabs[index]) },
        modifier = Modifier.fillMaxWidth(),
        fontSize = 22.sp,
        selectedFontWeight = FontWeight.Bold,
        unselectedFontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LiveAreaFilterRow(
    areas: List<BiliLiveArea>,
    selectedAreaId: Long,
    onAreaSelected: (Long) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(areas, key = { it.id }) { area ->
            FilterChip(
                selected = selectedAreaId == area.id,
                onClick = { onAreaSelected(area.id) },
                label = { Text(area.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BiliPink.copy(alpha = 0.12f),
                    selectedLabelColor = BiliPink,
                ),
            )
        }
    }
}

@Composable
private fun LiveRoomGridPage(
    rooms: List<BiliLiveRoom>,
    loading: Boolean,
    error: String?,
    emptyHint: String,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    contentPadding: PaddingValues,
    onRoomClick: (BiliLiveRoom) -> Unit,
    loadingMore: Boolean = false,
) {
    when {
        error != null && rooms.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
        rooms.isEmpty() && loading -> {
            Box(Modifier.fillMaxSize())
        }
        rooms.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyHint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    start = 12.dp,
                    end = 12.dp,
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rooms, key = { it.roomId }) { room ->
                    LiveRoomCard(room = room, onClick = { onRoomClick(room) })
                }
            }
        }
    }
}

@Composable
private fun LiveRoomCard(
    room: BiliLiveRoom,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(VideoFeedCardCorner)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, LiveFeedCardBorderColor, shape),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = LiveFeedMetaBackground,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                RemoteImage(
                    url = room.coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                VideoCoverBottomScrim(
                    coverUrl = room.coverUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(LiveFeedCoverScrimHeightFraction)
                        .align(Alignment.BottomCenter),
                )
                VideoCoverOverlayText(
                    text = "直播",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(LiveFeedCoverOverlayPadding),
                )
                VideoCoverOverlayText(
                    text = "${formatLiveCount(room.online)}人气",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(LiveFeedCoverOverlayPadding),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LiveFeedMetaBackground)
                    .padding(8.dp),
            ) {
                Text(
                    text = room.title.ifBlank { room.userName },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1A1A1A),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RemoteImage(
                        url = room.userFace,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape),
                    )
                    Text(
                        text = room.userName,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF999999),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (room.areaName.isNotBlank()) {
                    Text(
                        text = room.areaName,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF999999),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val LiveFeedCardBorderColor = Color(0xFFE8E8E8)
private val LiveFeedMetaBackground = Color(0xFFFFFFFF)
private val LiveFeedCoverOverlayPadding = 6.dp
private val LiveFeedCoverScrimHeightFraction = 0.45f

private fun formatLiveCount(count: Long): String = when {
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> count.toString()
}
