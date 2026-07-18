package com.example.bilibili.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.example.bilibili.ui.theme.isAppLightTheme
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.FeedLayoutStore
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.ui.components.ObserveListNearEnd
import com.example.bilibili.ui.components.ObserveStaggeredGridNearEnd
import com.example.bilibili.ui.components.VideoFeedCard
import com.example.bilibili.ui.BindFeedTabReselectEffect
import com.example.bilibili.ui.LocalFeedTabForReselect
import com.example.bilibili.ui.LocalFeedTabReselectController
import com.example.bilibili.ui.liquidglass.BottomBarFeedOverlapReserve
import com.example.bilibili.ui.liquidglass.SurfaceLiquidCapsule
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val HomeSearchBarBottomGap = 8.dp
internal val HomeSearchBarHeight = 40.dp
internal val HomeSearchBarTopGap = 8.dp
internal val HomeSearchBarHorizontalInset = 12.dp
internal val HomeSearchBarBorderWidth = 0.5.dp
private val HomeSearchBarBorderColorLight = Color(0x80999999)
private val HomeSearchBarBorderColorOnSurfaceLight = Color(0xFFBBBBBB)
internal val HomeSearchBarReservedHeight =
    HomeSearchBarTopGap + HomeSearchBarHeight + HomeSearchBarBottomGap

internal val HomeFeedGridSpacing = 6.dp
internal val HomeFeedGridHorizontalPadding = 10.dp
internal val HomeFeedSingleColumnHorizontalPadding = 12.dp
internal val HomeFeedSingleColumnSpacing = 12.dp

@Composable
internal fun homeSearchBarBorderColor(): Color =
    if (isAppLightTheme()) HomeSearchBarBorderColorLight else MaterialTheme.colorScheme.outline

@Composable
internal fun homeSearchBarBorderColorOnSurface(): Color =
    if (isAppLightTheme()) HomeSearchBarBorderColorOnSurfaceLight else MaterialTheme.colorScheme.outline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    loggedIn: Boolean = true,
    onLoginClick: () -> Unit = {},
    videos: List<BiliVideoItem>,
    playUrls: Map<String, BiliPlayStream>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onPullRefresh: () -> Unit = onRefresh,
    onVideoClick: (BiliVideoItem) -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
    onAuthorClick: (Long) -> Unit = {},
    coordinator: VideoPlaybackCoordinator,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    emptyHint: String? = null,
    onSearchVisibleChange: (Boolean) -> Unit = {},
    loadingMore: Boolean = false,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    showSearchBar: Boolean = false,
    onSearchClick: () -> Unit = {},
    pullRefreshState: PullToRefreshState = rememberPullToRefreshState(),
    showEmbeddedPullRefreshIndicator: Boolean = true,
    feedColumnCount: Int = FeedLayoutStore.COLUMN_COUNT_TWO,
    listState: LazyListState = rememberLazyListState(),
    staggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    bindFeedTabReselect: Boolean = true,
    enablePullToRefresh: Boolean = true,
) {
    if (!loggedIn) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("登录后查看个人主页", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onLoginClick) {
                    Text("打开哔哩哔哩登录")
                }
            }
        }
        return
    }

    val useSingleColumn = feedColumnCount == FeedLayoutStore.COLUMN_COUNT_ONE
    var showSearch by remember { mutableStateOf(true) }
    val feedTab = LocalFeedTabForReselect.current
    val feedTabReselectController = LocalFeedTabReselectController.current

    if (bindFeedTabReselect && feedTab != null && feedTabReselectController != null) {
        if (useSingleColumn) {
            BindFeedTabReselectEffect(
                tab = feedTab,
                controller = feedTabReselectController,
                listState = listState,
                onRefresh = onPullRefresh,
                onScrolledToTop = { showSearch = true },
            )
        } else {
            BindFeedTabReselectEffect(
                tab = feedTab,
                controller = feedTabReselectController,
                staggeredGridState = staggeredGridState,
                onRefresh = onPullRefresh,
                onScrolledToTop = { showSearch = true },
            )
        }
    }

    LaunchedEffect(showSearch) {
        onSearchVisibleChange(showSearch)
    }

    LaunchedEffect(useSingleColumn, listState, staggeredGridState) {
        var previousScrollKey = 0
        val scrollFlow = if (useSingleColumn) {
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
        } else {
            snapshotFlow {
                staggeredGridState.firstVisibleItemIndex to staggeredGridState.firstVisibleItemScrollOffset
            }
        }
        scrollFlow
            .distinctUntilChanged()
            .map { (index, offset) -> index * 100_000 + offset }
            .collectLatest { current ->
                val delta = current - previousScrollKey
                previousScrollKey = current

                if (delta > 0) {
                    val shouldHide = if (useSingleColumn) {
                        listState.firstVisibleItemIndex > 0 ||
                            listState.firstVisibleItemScrollOffset > 240
                    } else {
                        staggeredGridState.firstVisibleItemIndex > 0 ||
                            staggeredGridState.firstVisibleItemScrollOffset > 240
                    }
                    if (shouldHide) showSearch = false
                } else if (delta < 0) {
                    showSearch = true
                }
            }
    }

    if (useSingleColumn) {
        ObserveListNearEnd(
            listState = listState,
            enabled = hasMore && !loading && !loadingMore && videos.isNotEmpty(),
            onNearEnd = onLoadMore,
        )
    } else {
        ObserveStaggeredGridNearEnd(
            gridState = staggeredGridState,
            enabled = hasMore && !loading && !loadingMore && videos.isNotEmpty(),
            onNearEnd = onLoadMore,
        )
    }

    val feedContent: @Composable () -> Unit = {
        HomeFeedContent(
            useSingleColumn = useSingleColumn,
            listState = listState,
            staggeredGridState = staggeredGridState,
            contentPadding = contentPadding,
            showSearchBar = showSearchBar,
            videos = videos,
            playUrls = playUrls,
            loading = loading,
            loadingMore = loadingMore,
            error = error,
            emptyHint = emptyHint,
            onVideoClick = onVideoClick,
            onEnsurePlayStream = onEnsurePlayStream,
            onAuthorClick = onAuthorClick,
            coordinator = coordinator,
        )
    }

    if (enablePullToRefresh) {
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = onPullRefresh,
            state = pullRefreshState,
            indicator = {
                if (showEmbeddedPullRefreshIndicator) {
                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(2f),
                        isRefreshing = loading,
                        state = pullRefreshState,
                    )
                }
            },
            modifier = modifier.fillMaxSize(),
        ) {
            feedContent()
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            feedContent()
        }
    }
}

@Composable
private fun HomeFeedContent(
    useSingleColumn: Boolean,
    listState: LazyListState,
    staggeredGridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    showSearchBar: Boolean,
    videos: List<BiliVideoItem>,
    playUrls: Map<String, BiliPlayStream>,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    emptyHint: String?,
    onVideoClick: (BiliVideoItem) -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
    onAuthorClick: (Long) -> Unit,
    coordinator: VideoPlaybackCoordinator,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
            val listContentHeight = maxHeight
            val feedTopPadding = contentPadding.calculateTopPadding() +
                if (showSearchBar) HomeSearchBarReservedHeight else 0.dp
            val feedBottomPadding = contentPadding.calculateBottomPadding() + BottomBarFeedOverlapReserve

            if (useSingleColumn) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = feedTopPadding,
                        bottom = feedBottomPadding,
                        start = HomeFeedSingleColumnHorizontalPadding,
                        end = HomeFeedSingleColumnHorizontalPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(HomeFeedSingleColumnSpacing),
                ) {
                    when {
                        error != null && videos.isEmpty() -> {
                            item(key = "home-error") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(listContentHeight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(error, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        videos.isEmpty() -> {
                            item(key = "home-empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(listContentHeight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!loading) {
                                        Text(
                                            text = emptyHint ?: "暂无内容",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            items(videos, key = { it.bvid }) { video ->
                                VideoFeedCard(
                                    video = video,
                                    playStream = playUrls[video.playbackId()],
                                    coordinator = coordinator,
                                    onClick = { onVideoClick(video) },
                                    onEnsurePlayStream = { onEnsurePlayStream(video) },
                                    onAuthorClick = onAuthorClick,
                                    overlayMetaOnCover = true,
                                )
                            }
                            if (loadingMore) {
                                item(key = "feed-loading-more") {
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
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = staggeredGridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = feedTopPadding,
                        bottom = feedBottomPadding,
                        start = HomeFeedGridHorizontalPadding,
                        end = HomeFeedGridHorizontalPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(HomeFeedGridSpacing),
                    verticalItemSpacing = HomeFeedGridSpacing,
                ) {
                    when {
                        error != null && videos.isEmpty() -> {
                            item(key = "home-error", span = StaggeredGridItemSpan.FullLine) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(listContentHeight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(error, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        videos.isEmpty() -> {
                            item(key = "home-empty", span = StaggeredGridItemSpan.FullLine) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(listContentHeight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!loading) {
                                        Text(
                                            text = emptyHint ?: "暂无内容",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            items(videos, key = { it.bvid }) { video ->
                                VideoFeedCard(
                                    video = video,
                                    playStream = playUrls[video.playbackId()],
                                    coordinator = coordinator,
                                    onClick = { onVideoClick(video) },
                                    onEnsurePlayStream = { onEnsurePlayStream(video) },
                                    onAuthorClick = onAuthorClick,
                                    gridStyle = true,
                                )
                            }
                            if (loadingMore) {
                                item(key = "feed-loading-more", span = StaggeredGridItemSpan.FullLine) {
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

@Composable
fun HomeSearchCapsule(
    visible: Boolean,
    contentPadding: PaddingValues,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topOffsetPx = with(LocalDensity.current) {
        (contentPadding.calculateTopPadding() + HomeSearchBarTopGap).roundToPx()
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(200)) { -(it + topOffsetPx) },
        exit = slideOutVertically(tween(160)) { -(it + topOffsetPx) },
        modifier = modifier
            .padding(top = contentPadding.calculateTopPadding() + HomeSearchBarTopGap)
            .padding(horizontal = HomeSearchBarHorizontalInset),
    ) {
        SurfaceLiquidCapsule(
            pill = true,
            useMenuGlassStyle = true,
            borderWidth = HomeSearchBarBorderWidth,
            borderColor = homeSearchBarBorderColor(),
            modifier = Modifier
                .fillMaxWidth()
                .height(HomeSearchBarHeight)
                .clickable(onClick = onSearchClick),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "搜索",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
                Text(
                    text = "搜索视频、UP主",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SearchInputCapsule(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClear: (() -> Unit)? = null,
    placeholder: String = "搜索视频、UP主",
) {
    val hintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    SurfaceLiquidCapsule(
        backdrop = backdrop,
        pill = true,
        useMenuGlassStyle = true,
        borderWidth = HomeSearchBarBorderWidth,
        borderColor = homeSearchBarBorderColorOnSurface(),
        modifier = modifier
            .fillMaxWidth()
            .height(HomeSearchBarHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "搜索",
                modifier = Modifier.size(18.dp),
                tint = hintColor,
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        },
                    ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = hintColor,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotEmpty() && onClear != null) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "清空",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onClear),
                    tint = hintColor,
                )
            }
        }
    }
}
