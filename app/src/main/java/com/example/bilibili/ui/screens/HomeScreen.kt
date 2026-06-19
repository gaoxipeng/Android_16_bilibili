package com.example.bilibili.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.ui.components.VideoFeedCard
import com.example.bilibili.ui.liquidglass.SurfaceLiquidCapsule
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val HomeSearchBarBottomGap = 8.dp
internal val HomeSearchBarHeight = 40.dp
internal val HomeSearchBarTopGap = 8.dp
internal val HomeSearchBarHorizontalInset = 12.dp
internal val HomeSearchBarReservedHeight =
    HomeSearchBarTopGap + HomeSearchBarHeight + HomeSearchBarBottomGap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    videos: List<BiliVideoItem>,
    playUrls: Map<String, BiliPlayStream>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onVideoClick: (BiliVideoItem) -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
    onAuthorClick: (Long) -> Unit = {},
    coordinator: VideoPlaybackCoordinator,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    emptyHint: String? = null,
    onSearchVisibleChange: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    var showSearch by remember { mutableStateOf(true) }
    var previousScrollKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(showSearch) {
        onSearchVisibleChange(showSearch)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .map { (index, offset) -> index * 100_000 + offset }
            .collectLatest { current ->
                val delta = current - previousScrollKey
                previousScrollKey = current

                // 向上滑（内容上移）一段距离后隐藏；向下滑立即显示
                if (delta > 0) {
                    val shouldHide = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240
                    if (shouldHide) showSearch = false
                } else if (delta < 0) {
                    showSearch = true
                }
            }
    }

    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            error != null && videos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
            videos.isEmpty() && loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            videos.isEmpty() && !loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = emptyHint ?: "暂无内容",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = contentPadding.calculateTopPadding() + HomeSearchBarReservedHeight,
                        bottom = contentPadding.calculateBottomPadding() + 88.dp,
                        start = 12.dp,
                        end = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(videos, key = { it.bvid }) { video ->
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
                }
            }
        }
    }
}

@Composable
fun HomeSearchCapsule(
    visible: Boolean,
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { -it / 2 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 },
        modifier = modifier
            .fillMaxWidth()
            .padding(top = contentPadding.calculateTopPadding() + HomeSearchBarTopGap)
            .padding(horizontal = HomeSearchBarHorizontalInset),
    ) {
        SurfaceLiquidCapsule(
            backdrop = backdrop,
            pill = true,
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
