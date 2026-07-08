package com.example.bilibili.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.FeedLayoutStore
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.ui.FeedTabReselectHandler
import com.example.bilibili.ui.LocalFeedTabReselectController
import com.example.bilibili.ui.MainTab
import com.example.bilibili.ui.components.SlidingTextTabs
import com.example.bilibili.ui.isScrolledToTop
import com.example.bilibili.ui.liquidglass.BottomBarFeedOverlapReserve
import kotlinx.coroutines.launch

private enum class FollowingContentTab(val label: String) {
    Following("关注"),
    Ranking("排行"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingScreen(
    loggedIn: Boolean,
    followVideos: List<BiliVideoItem>,
    followPlayUrls: Map<String, BiliPlayStream>,
    followLoading: Boolean,
    followError: String?,
    followLoadingMore: Boolean,
    followHasMore: Boolean,
    onLoginClick: () -> Unit,
    onRefreshFollow: () -> Unit,
    onPullRefreshFollow: () -> Unit,
    onLoadMoreFollow: () -> Unit,
    rankVideos: List<BiliVideoItem>,
    rankPlayUrls: Map<String, BiliPlayStream>,
    rankLoading: Boolean,
    rankError: String?,
    onRefreshRank: () -> Unit,
    onPullRefreshRank: () -> Unit,
    onVideoClick: (BiliVideoItem) -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
    onFollowAuthorClick: (Long) -> Unit,
    onRankAuthorClick: (Long) -> Unit,
    coordinator: VideoPlaybackCoordinator,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    followPullRefreshState: PullToRefreshState = rememberPullToRefreshState(),
    rankPullRefreshState: PullToRefreshState = rememberPullToRefreshState(),
    showEmbeddedPullRefreshIndicator: Boolean = true,
    feedColumnCount: Int = FeedLayoutStore.COLUMN_COUNT_TWO,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { FollowingContentTab.entries.size },
    )
    val tabScrollPosition by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val useSingleColumn = feedColumnCount == FeedLayoutStore.COLUMN_COUNT_ONE
    val followListState = rememberLazyListState()
    val followGridState = rememberLazyStaggeredGridState()
    val rankListState = rememberLazyListState()
    val rankGridState = rememberLazyStaggeredGridState()
    var refreshing by remember { mutableStateOf(false) }

    val listTopInset = contentPadding.calculateTopPadding()
    val listBottomInset = contentPadding.calculateBottomPadding() + BottomBarFeedOverlapReserve
    val contentPaddingWithoutTop = PaddingValues(
        top = 0.dp,
        bottom = listBottomInset,
    )
    val activePullRefreshState = when (FollowingContentTab.entries[pagerState.currentPage]) {
        FollowingContentTab.Following -> followPullRefreshState
        FollowingContentTab.Ranking -> rankPullRefreshState
    }
    val activeRefreshing = when (FollowingContentTab.entries[pagerState.currentPage]) {
        FollowingContentTab.Following -> followLoading
        FollowingContentTab.Ranking -> rankLoading
    }

    fun refreshCurrentTab(scrollToTopWhenDone: Boolean = false) {
        scope.launch {
            refreshing = true
            try {
                when (FollowingContentTab.entries[pagerState.currentPage]) {
                    FollowingContentTab.Following -> onPullRefreshFollow()
                    FollowingContentTab.Ranking -> onPullRefreshRank()
                }
                if (scrollToTopWhenDone) {
                    when (FollowingContentTab.entries[pagerState.currentPage]) {
                        FollowingContentTab.Following -> if (useSingleColumn) {
                            followListState.animateScrollToItem(0)
                        } else {
                            followGridState.animateScrollToItem(0)
                        }
                        FollowingContentTab.Ranking -> if (useSingleColumn) {
                            rankListState.animateScrollToItem(0)
                        } else {
                            rankGridState.animateScrollToItem(0)
                        }
                    }
                }
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == FollowingContentTab.Ranking.ordinal &&
            rankVideos.isEmpty() &&
            !rankLoading
        ) {
            onRefreshRank()
        }
    }

    val feedTabReselectController = LocalFeedTabReselectController.current
    if (feedTabReselectController != null) {
        DisposableEffect(
            feedTabReselectController,
            pagerState,
            followListState,
            followGridState,
            rankListState,
            rankGridState,
            useSingleColumn,
        ) {
            feedTabReselectController.register(
                MainTab.Following,
                FeedTabReselectHandler(
                    isAtTop = {
                        when (FollowingContentTab.entries[pagerState.currentPage]) {
                            FollowingContentTab.Following -> if (useSingleColumn) {
                                followListState.isScrolledToTop()
                            } else {
                                followGridState.isScrolledToTop()
                            }
                            FollowingContentTab.Ranking -> if (useSingleColumn) {
                                rankListState.isScrolledToTop()
                            } else {
                                rankGridState.isScrolledToTop()
                            }
                        }
                    },
                    scrollToTop = {
                        when (FollowingContentTab.entries[pagerState.currentPage]) {
                            FollowingContentTab.Following -> if (useSingleColumn) {
                                followListState.animateScrollToItem(0)
                            } else {
                                followGridState.animateScrollToItem(0)
                            }
                            FollowingContentTab.Ranking -> if (useSingleColumn) {
                                rankListState.animateScrollToItem(0)
                            } else {
                                rankGridState.animateScrollToItem(0)
                            }
                        }
                    },
                    refresh = { refreshCurrentTab(scrollToTopWhenDone = true) },
                ),
            )
            onDispose { feedTabReselectController.unregister(MainTab.Following) }
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing || activeRefreshing,
        onRefresh = { refreshCurrentTab(scrollToTopWhenDone = true) },
        state = activePullRefreshState,
        indicator = {
            if (showEmbeddedPullRefreshIndicator) {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(2f),
                    isRefreshing = refreshing || activeRefreshing,
                    state = activePullRefreshState,
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                    .padding(top = listTopInset),
            ) {
                FollowingContentTabBar(
                    scrollPosition = tabScrollPosition,
                    onTabSelected = { tab ->
                        val sameTab = tab == FollowingContentTab.entries[pagerState.currentPage]
                        if (sameTab) {
                            scope.launch {
                                when (tab) {
                                    FollowingContentTab.Following -> if (useSingleColumn) {
                                        followListState.animateScrollToItem(0)
                                    } else {
                                        followGridState.animateScrollToItem(0)
                                    }
                                    FollowingContentTab.Ranking -> if (useSingleColumn) {
                                        rankListState.animateScrollToItem(0)
                                    } else {
                                        rankGridState.animateScrollToItem(0)
                                    }
                                }
                            }
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(FollowingContentTab.entries.indexOf(tab))
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
                when (FollowingContentTab.entries[page]) {
                    FollowingContentTab.Following -> {
                        if (!loggedIn) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(contentPaddingWithoutTop),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "登录后查看关注 UP 的最新视频",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = onLoginClick) {
                                        Text("打开哔哩哔哩登录")
                                    }
                                }
                            }
                        } else {
                            HomeScreen(
                                videos = followVideos,
                                playUrls = followPlayUrls,
                                loading = followLoading,
                                loadingMore = followLoadingMore,
                                hasMore = followHasMore,
                                error = followError,
                                onRefresh = onRefreshFollow,
                                onPullRefresh = onPullRefreshFollow,
                                onLoadMore = onLoadMoreFollow,
                                onVideoClick = onVideoClick,
                                onEnsurePlayStream = onEnsurePlayStream,
                                onAuthorClick = onFollowAuthorClick,
                                coordinator = coordinator,
                                contentPadding = contentPaddingWithoutTop,
                                modifier = Modifier.fillMaxSize(),
                                emptyHint = "暂无关注视频，先去关注几个 UP 主吧",
                                showSearchBar = false,
                                pullRefreshState = followPullRefreshState,
                                showEmbeddedPullRefreshIndicator = false,
                                feedColumnCount = feedColumnCount,
                                listState = followListState,
                                staggeredGridState = followGridState,
                                bindFeedTabReselect = false,
                                enablePullToRefresh = false,
                            )
                        }
                    }
                    FollowingContentTab.Ranking -> {
                        HomeScreen(
                            videos = rankVideos,
                            playUrls = rankPlayUrls,
                            loading = rankLoading,
                            error = rankError,
                            onRefresh = onRefreshRank,
                            onPullRefresh = onPullRefreshRank,
                            onVideoClick = onVideoClick,
                            onEnsurePlayStream = onEnsurePlayStream,
                            onAuthorClick = onRankAuthorClick,
                            coordinator = coordinator,
                            contentPadding = contentPaddingWithoutTop,
                            modifier = Modifier.fillMaxSize(),
                            emptyHint = "暂无排行内容",
                            showSearchBar = false,
                            pullRefreshState = rankPullRefreshState,
                            showEmbeddedPullRefreshIndicator = false,
                            feedColumnCount = feedColumnCount,
                            listState = rankListState,
                            staggeredGridState = rankGridState,
                            bindFeedTabReselect = false,
                            enablePullToRefresh = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowingContentTabBar(
    scrollPosition: Float,
    onTabSelected: (FollowingContentTab) -> Unit,
) {
    val tabs = FollowingContentTab.entries
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
