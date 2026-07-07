package com.example.bilibili.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.data.BiliHotSearchItem
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliSearchBangumi
import com.example.bilibili.data.BiliSearchUserItem
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.data.SearchHistoryStore
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.ui.components.BiliUserLevelIcon
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.SearchBangumiCard
import com.example.bilibili.ui.components.SlidingTextTabs
import com.example.bilibili.ui.components.VideoFeedCard
import com.example.bilibili.data.FeedLayoutStore
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.theme.BiliPink
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private enum class SearchContentTab(val label: String) {
    Videos("视频"),
    Users("UP主"),
}

private val SearchHistoryChipBackground = Color(0xFFF7F7F7)
private val SearchHistoryChipRadius = 8.dp
private val SearchHistoryChipMaxWidth = 168.dp
private val SearchHistoryTitleToChipsGap = 2.dp
private val SearchContentTopGap = 2.dp

@Composable
fun SearchScreen(
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    playUrls: Map<String, BiliPlayStream>,
    coordinator: VideoPlaybackCoordinator,
    onClose: () -> Unit,
    onVideoClick: (BiliVideoItem) -> Unit,
    onUserClick: (Long, String, String) -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
    modifier: Modifier = Modifier,
    feedColumnCount: Int = FeedLayoutStore.COLUMN_COUNT_TWO,
    handleSystemBack: Boolean = true,
) {
    val useSingleColumnVideos = feedColumnCount == FeedLayoutStore.COLUMN_COUNT_ONE
    val context = LocalContext.current
    val searchHistoryStore = remember { SearchHistoryStore(context) }
    val scope = rememberCoroutineScope()
    val searchBackdrop = rememberLayerBackdrop()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var queryInput by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf<String?>(null) }
    var suggests by remember { mutableStateOf<List<String>>(emptyList()) }
    var hotWords by remember { mutableStateOf<List<BiliHotSearchItem>>(emptyList()) }
    var hotLoading by remember { mutableStateOf(true) }

    var videos by remember { mutableStateOf<List<BiliVideoItem>>(emptyList()) }
    var users by remember { mutableStateOf<List<BiliSearchUserItem>>(emptyList()) }
    var videoPage by remember { mutableIntStateOf(1) }
    var userPage by remember { mutableIntStateOf(1) }
    var videoHasMore by remember { mutableStateOf(false) }
    var userHasMore by remember { mutableStateOf(false) }
    var videoLoading by remember { mutableStateOf(false) }
    var userLoading by remember { mutableStateOf(false) }
    var videoLoadingMore by remember { mutableStateOf(false) }
    var userLoadingMore by remember { mutableStateOf(false) }
    var pinnedMedia by remember { mutableStateOf<List<BiliSearchBangumi>>(emptyList()) }
    var pinnedMediaPage by remember { mutableIntStateOf(1) }
    var pinnedMediaHasMore by remember { mutableStateOf(false) }
    var pinnedMediaLoading by remember { mutableStateOf(false) }
    var pinnedMediaLoadingMore by remember { mutableStateOf(false) }
    var resultError by remember { mutableStateOf<String?>(null) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var searchHistory by remember { mutableStateOf(searchHistoryStore.read()) }
    var searchHistoryExpanded by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { SearchContentTab.entries.size })
    val videoListState = rememberLazyListState()
    val videoStaggeredGridState = rememberLazyStaggeredGridState()
    val userListState = rememberLazyListState()

    BackHandler(enabled = handleSystemBack, onBack = onClose)

    fun submitQuery(raw: String) {
        val normalized = raw.trim()
        if (normalized.isBlank()) return
        queryInput = normalized
        activeQuery = normalized
        searchHistory = searchHistoryStore.touch(normalized)
        videos = emptyList()
        users = emptyList()
        pinnedMedia = emptyList()
        videoPage = 1
        userPage = 1
        pinnedMediaPage = 1
        videoHasMore = false
        userHasMore = false
        pinnedMediaHasMore = false
        resultError = null
        searchGeneration++
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    suspend fun loadVideos(reset: Boolean, generation: Int) {
        val query = activeQuery ?: return
        if (generation != searchGeneration) return
        if (reset) {
            videoLoading = true
            videoPage = 1
        } else {
            if (videoLoadingMore || !videoHasMore) return
            videoLoadingMore = true
        }
        try {
            val page = if (reset) 1 else videoPage + 1
            val result = api.searchVideos(query, page, credential)
            if (generation != searchGeneration) return
            if (reset) {
                videos = result.items
                videoPage = result.page
            } else {
                videos = (videos + result.items).distinctBy { it.bvid }
                videoPage = result.page
            }
            videoHasMore = result.hasMore
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == searchGeneration && reset) {
                resultError = error.message ?: "搜索失败"
            }
        } finally {
            if (generation == searchGeneration) {
                videoLoading = false
                videoLoadingMore = false
            }
        }
    }

    suspend fun loadUsers(reset: Boolean, generation: Int) {
        val query = activeQuery ?: return
        if (generation != searchGeneration) return
        if (reset) {
            userLoading = true
            userPage = 1
        } else {
            if (userLoadingMore || !userHasMore) return
            userLoadingMore = true
        }
        try {
            val page = if (reset) 1 else userPage + 1
            val result = api.searchUsers(query, page, credential)
            if (generation != searchGeneration) return
            if (reset) {
                users = result.items
                userPage = result.page
            } else {
                users = (users + result.items).distinctBy { it.mid }
                userPage = result.page
            }
            userHasMore = result.hasMore
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == searchGeneration && reset) {
                resultError = error.message ?: "搜索失败"
            }
        } finally {
            if (generation == searchGeneration) {
                userLoading = false
                userLoadingMore = false
            }
        }
    }

    suspend fun loadPinnedMedia(reset: Boolean, generation: Int) {
        val query = activeQuery ?: return
        if (generation != searchGeneration) return
        if (reset) {
            pinnedMediaLoading = true
            pinnedMediaPage = 1
        } else {
            if (pinnedMediaLoadingMore || !pinnedMediaHasMore) return
            pinnedMediaLoadingMore = true
        }
        try {
            val page = if (reset) 1 else pinnedMediaPage + 1
            val result = api.searchPinnedMedia(query, page, credential)
            if (generation != searchGeneration) return
            if (reset) {
                pinnedMedia = result.items.distinctBy { it.seasonId }
                pinnedMediaPage = result.page
            } else {
                val seen = pinnedMedia.map { it.seasonId }.toMutableSet()
                pinnedMedia = pinnedMedia + result.items.filter { seen.add(it.seasonId) }
                pinnedMediaPage = result.page
            }
            pinnedMediaHasMore = result.hasMore
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (generation == searchGeneration && reset) {
                pinnedMedia = emptyList()
                pinnedMediaHasMore = false
            }
        } finally {
            if (generation == searchGeneration) {
                pinnedMediaLoading = false
                pinnedMediaLoadingMore = false
            }
        }
    }

    fun openBangumi(bangumi: BiliSearchBangumi) {
        if (bangumi.canPlayInApp) {
            onVideoClick(bangumi.toVideoItem())
            return
        }
        val webUrl = bangumi.webUrl.trim()
        if (webUrl.isNotBlank()) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        hotLoading = true
        hotWords = api.getSearchHotWords()
        hotLoading = false
    }

    LaunchedEffect(queryInput, activeQuery) {
        if (activeQuery != null) {
            suggests = emptyList()
            return@LaunchedEffect
        }
        val term = queryInput.trim()
        if (term.isBlank()) {
            suggests = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggests = api.getSearchSuggest(term)
    }

    LaunchedEffect(activeQuery, searchGeneration) {
        val generation = searchGeneration
        if (activeQuery == null) return@LaunchedEffect
        coroutineScope {
            launch { loadVideos(reset = true, generation = generation) }
            launch { loadPinnedMedia(reset = true, generation = generation) }
            launch { loadUsers(reset = true, generation = generation) }
        }
    }

    LaunchedEffect(searchGeneration) {
        videoListState.scrollToItem(0)
        videoStaggeredGridState.scrollToItem(0)
    }

    LaunchedEffect(pinnedMedia, searchGeneration) {
        if (pinnedMedia.isNotEmpty()) {
            videoListState.animateScrollToItem(0)
            videoStaggeredGridState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(videoListState, videoStaggeredGridState, useSingleColumnVideos, activeQuery, searchGeneration, videoHasMore) {
        if (activeQuery == null) return@LaunchedEffect
        val scrollFlow = if (useSingleColumnVideos) {
            snapshotFlow {
                val info = videoListState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = info.totalItemsCount
                if (total <= 0 || last < total - 3) null else last
            }
        } else {
            snapshotFlow {
                val info = videoStaggeredGridState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = info.totalItemsCount
                if (total <= 0 || last < total - 3) null else last
            }
        }
        scrollFlow
            .distinctUntilChanged()
            .filterNotNull()
            .collect {
                loadVideos(reset = false, generation = searchGeneration)
            }
    }

    LaunchedEffect(userListState, activeQuery, searchGeneration, userHasMore) {
        if (activeQuery == null) return@LaunchedEffect
        snapshotFlow {
            val info = userListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            if (total <= 0 || last < total - 3) null else last
        }
            .distinctUntilChanged()
            .filterNotNull()
            .collect {
                loadUsers(reset = false, generation = searchGeneration)
            }
    }

    LaunchedEffect(activeQuery, videos) {
        if (activeQuery == null) return@LaunchedEffect
        videos.take(12).forEach { video ->
            if (playUrls[video.playbackId()] == null) {
                onEnsurePlayStream(video)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(searchBackdrop)
                .background(MaterialTheme.colorScheme.surface),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            SearchInputCapsule(
                query = queryInput,
                onQueryChange = {
                    queryInput = it
                    if (activeQuery != null) activeQuery = null
                },
                onSearch = { submitQuery(queryInput) },
                backdrop = searchBackdrop,
            focusRequester = focusRequester,
            onClear = {
                queryInput = ""
                activeQuery = null
                videos = emptyList()
                users = emptyList()
                resultError = null
            },
            modifier = Modifier
                .padding(top = HomeSearchBarTopGap)
                .padding(horizontal = HomeSearchBarHorizontalInset)
                .padding(bottom = SearchContentTopGap),
        )

        if (activeQuery == null) {
            if (suggests.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(suggests, key = { it }) { term ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { submitQuery(term) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = term,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                SearchDiscoveryPanel(
                    searchHistory = searchHistory,
                    searchHistoryExpanded = searchHistoryExpanded,
                    onHistoryClick = ::submitQuery,
                    onHistoryDelete = { query ->
                        searchHistory = searchHistoryStore.remove(query)
                    },
                    onHistoryExpandToggle = { searchHistoryExpanded = !searchHistoryExpanded },
                    onHistoryClear = {
                        searchHistory = searchHistoryStore.clear()
                        searchHistoryExpanded = false
                    },
                    hotWords = hotWords,
                    hotLoading = hotLoading,
                    onHotSelect = ::submitQuery,
                )
            }
        } else {
            SearchResultTabs(
                scrollPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                onTabSelected = { tab ->
                    scope.launch {
                        pagerState.animateScrollToPage(SearchContentTab.entries.indexOf(tab))
                    }
                },
            )

            resultError?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
            ) { page ->
                when (SearchContentTab.entries[page]) {
                    SearchContentTab.Videos -> {
                        val isWaitingPinned = pinnedMediaLoading && pinnedMedia.isEmpty()
                        val isInitialLoading = videos.isEmpty() &&
                            pinnedMedia.isEmpty() &&
                            (videoLoading || pinnedMediaLoading)
                        val isSettlingPinned = isWaitingPinned && videos.isNotEmpty()
                        when {
                            isInitialLoading || isSettlingPinned -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            videos.isEmpty() && pinnedMedia.isEmpty() && !videoLoading && !pinnedMediaLoading -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "没有找到相关结果",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            else -> {
                                val horizontalPadding = if (useSingleColumnVideos) {
                                    HomeFeedSingleColumnHorizontalPadding
                                } else {
                                    HomeFeedGridHorizontalPadding
                                }
                                val showPinnedSection = pinnedMedia.isNotEmpty()
                                if (useSingleColumnVideos) {
                                    LazyColumn(
                                        state = videoListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = horizontalPadding,
                                            end = horizontalPadding,
                                            top = 8.dp,
                                            bottom = 24.dp,
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(HomeFeedSingleColumnSpacing),
                                    ) {
                                        if (showPinnedSection) {
                                            item(key = "pinned-media") {
                                                SearchPinnedMediaSection(
                                                    media = pinnedMedia,
                                                    columnCount = 1,
                                                    hasMore = pinnedMediaHasMore,
                                                    loadingMore = pinnedMediaLoadingMore,
                                                    onBangumiClick = ::openBangumi,
                                                    onLoadMore = {
                                                        scope.launch {
                                                            loadPinnedMedia(
                                                                reset = false,
                                                                generation = searchGeneration,
                                                            )
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                        if (videos.isNotEmpty() && showPinnedSection) {
                                            item(key = "video-results-header") {
                                                Text(
                                                    text = "相关视频",
                                                    modifier = Modifier.padding(bottom = 4.dp),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                        items(videos, key = { it.bvid }) { video ->
                                            VideoFeedCard(
                                                video = video,
                                                playStream = playUrls[video.playbackId()],
                                                coordinator = coordinator,
                                                onClick = { onVideoClick(video) },
                                                onEnsurePlayStream = { onEnsurePlayStream(video) },
                                                onAuthorClick = { mid ->
                                                    onUserClick(mid, video.authorName, "")
                                                },
                                                overlayMetaOnCover = true,
                                            )
                                        }
                                        if (videoLoadingMore) {
                                            item(key = "video-loading-more") {
                                                SearchLoadingMoreIndicator()
                                            }
                                        }
                                    }
                                } else {
                                    LazyVerticalStaggeredGrid(
                                        columns = StaggeredGridCells.Fixed(2),
                                        state = videoStaggeredGridState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = horizontalPadding,
                                            end = horizontalPadding,
                                            top = 8.dp,
                                            bottom = 24.dp,
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(HomeFeedGridSpacing),
                                        verticalItemSpacing = HomeFeedGridSpacing,
                                    ) {
                                        if (showPinnedSection) {
                                            item(
                                                key = "pinned-media",
                                                span = StaggeredGridItemSpan.FullLine,
                                            ) {
                                                SearchPinnedMediaSection(
                                                    media = pinnedMedia,
                                                    columnCount = 2,
                                                    hasMore = pinnedMediaHasMore,
                                                    loadingMore = pinnedMediaLoadingMore,
                                                    onBangumiClick = ::openBangumi,
                                                    onLoadMore = {
                                                        scope.launch {
                                                            loadPinnedMedia(
                                                                reset = false,
                                                                generation = searchGeneration,
                                                            )
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                        if (videos.isNotEmpty() && showPinnedSection) {
                                            item(
                                                key = "video-results-header",
                                                span = StaggeredGridItemSpan.FullLine,
                                            ) {
                                                Text(
                                                    text = "相关视频",
                                                    modifier = Modifier.padding(bottom = 4.dp),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                        items(videos, key = { it.bvid }) { video ->
                                            VideoFeedCard(
                                                video = video,
                                                playStream = playUrls[video.playbackId()],
                                                coordinator = coordinator,
                                                onClick = { onVideoClick(video) },
                                                onEnsurePlayStream = { onEnsurePlayStream(video) },
                                                onAuthorClick = { mid ->
                                                    onUserClick(mid, video.authorName, "")
                                                },
                                                gridStyle = true,
                                            )
                                        }
                                        if (videoLoadingMore) {
                                            item(
                                                key = "video-loading-more",
                                                span = StaggeredGridItemSpan.FullLine,
                                            ) {
                                                SearchLoadingMoreIndicator()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SearchContentTab.Users -> {
                        when {
                            userLoading && users.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            users.isEmpty() && !userLoading -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "没有找到相关 UP 主",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(
                                    state = userListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                                ) {
                                    itemsIndexed(users, key = { _, user -> user.mid }) { index, user ->
                                        SearchUserRow(
                                            user = user,
                                            onClick = { onUserClick(user.mid, user.name, user.face) },
                                        )
                                        if (index < users.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                                            )
                                        }
                                    }
                                    if (userLoadingMore) {
                                        item(key = "user-loading-more") {
                                            SearchLoadingMoreIndicator()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchDiscoveryPanel(
    searchHistory: List<String>,
    searchHistoryExpanded: Boolean,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onHistoryExpandToggle: () -> Unit,
    onHistoryClear: () -> Unit,
    hotWords: List<BiliHotSearchItem>,
    hotLoading: Boolean,
    onHotSelect: (String) -> Unit,
) {
    var deleteHistoryQuery by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(searchHistory, searchHistoryExpanded) {
        if (deleteHistoryQuery != null && deleteHistoryQuery !in searchHistory) {
            deleteHistoryQuery = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = SearchContentTopGap, bottom = 24.dp),
    ) {
        if (searchHistory.isNotEmpty()) {
            item(key = "history-section") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "搜索历史",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = {
                                deleteHistoryQuery = null
                                onHistoryClear()
                            },
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            Text(
                                text = "清除",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(SearchHistoryTitleToChipsGap))
                    SearchHistoryChipsSection(
                        searchHistory = searchHistory,
                        searchHistoryExpanded = searchHistoryExpanded,
                        deleteHistoryQuery = deleteHistoryQuery,
                        onDeleteHistoryQueryChange = { deleteHistoryQuery = it },
                        onHistoryClick = onHistoryClick,
                        onHistoryDelete = onHistoryDelete,
                        onHistoryExpandToggle = onHistoryExpandToggle,
                    )
                }
            }
            item(key = "history-spacer") {
                Spacer(Modifier.height(16.dp))
            }
        }

        item(key = "hot-title") {
            Text(
                text = "bilibili热搜",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
        }

        when {
            hotLoading -> {
                item(key = "hot-loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            hotWords.isEmpty() -> {
                item(key = "hot-empty") {
                    Text(
                        text = "暂无热搜",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            else -> {
                itemsIndexed(hotWords, key = { _, item -> item.keyword }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onHotSelect(item.keyword) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SearchHotRankBadge(rank = item.rank)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = item.showName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (index < hotWords.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistoryChipFlow(
    queries: List<String>,
    maxLines: Int,
    deleteHistoryQuery: String?,
    onDeleteHistoryQueryChange: (String?) -> Unit,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxLines = maxLines,
    ) {
        queries.forEach { query ->
            key(query) {
                SearchHistoryChip(
                    query = query,
                    showDelete = deleteHistoryQuery == query,
                    onClick = {
                        onDeleteHistoryQueryChange(null)
                        onHistoryClick(query)
                    },
                    onLongClick = { onDeleteHistoryQueryChange(query) },
                    onDelete = {
                        onDeleteHistoryQueryChange(null)
                        onHistoryDelete(query)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistoryChipsSection(
    searchHistory: List<String>,
    searchHistoryExpanded: Boolean,
    deleteHistoryQuery: String?,
    onDeleteHistoryQueryChange: (String?) -> Unit,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onHistoryExpandToggle: () -> Unit,
) {
    val maxRows = SearchHistoryStore.DISPLAY_MAX_ROWS

    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val fullHeight = subcompose("measure-full") {
            SearchHistoryChipFlow(
                queries = searchHistory,
                maxLines = Int.MAX_VALUE,
                deleteHistoryQuery = deleteHistoryQuery,
                onDeleteHistoryQueryChange = onDeleteHistoryQueryChange,
                onHistoryClick = onHistoryClick,
                onHistoryDelete = onHistoryDelete,
            )
        }.first().measure(constraints).height

        val cappedHeight = subcompose("measure-capped") {
            SearchHistoryChipFlow(
                queries = searchHistory,
                maxLines = maxRows,
                deleteHistoryQuery = deleteHistoryQuery,
                onDeleteHistoryQueryChange = onDeleteHistoryQueryChange,
                onHistoryClick = onHistoryClick,
                onHistoryDelete = onHistoryDelete,
            )
        }.first().measure(constraints).height

        val exceeds = fullHeight > cappedHeight

        subcompose("sync-collapse") {
            LaunchedEffect(exceeds, searchHistoryExpanded) {
                if (!exceeds && searchHistoryExpanded) {
                    onHistoryExpandToggle()
                }
            }
        }.forEach { it.measure(constraints.copy(minWidth = 0, maxWidth = 0, minHeight = 0, maxHeight = 0)) }

        val visiblePlaceable = subcompose("visible") {
            Column(modifier = Modifier.fillMaxWidth()) {
                SearchHistoryChipFlow(
                    queries = searchHistory,
                    maxLines = if (searchHistoryExpanded) Int.MAX_VALUE else maxRows,
                    deleteHistoryQuery = deleteHistoryQuery,
                    onDeleteHistoryQueryChange = onDeleteHistoryQueryChange,
                    onHistoryClick = onHistoryClick,
                    onHistoryDelete = onHistoryDelete,
                )
                if (exceeds) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (searchHistoryExpanded) "收起" else "展开更多",
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onHistoryExpandToggle,
                                )
                                .padding(vertical = 2.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = BiliPink,
                        )
                    }
                }
            }
        }.first().measure(constraints)

        layout(visiblePlaceable.width, visiblePlaceable.height) {
            visiblePlaceable.place(0, 0)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchHistoryChip(
    query: String,
    showDelete: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val chipShape = RoundedCornerShape(SearchHistoryChipRadius)
    Box(
        modifier = Modifier
            .widthIn(max = SearchHistoryChipMaxWidth)
            .clip(chipShape)
            .background(SearchHistoryChipBackground)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Text(
            text = query,
            modifier = Modifier.padding(
                start = 12.dp,
                end = if (showDelete) 26.dp else 12.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showDelete) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF999999))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SearchLoadingMoreIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SearchHotRankBadge(rank: Int) {
    val color = when (rank) {
        1 -> Color(0xFFFE2D46)
        2 -> Color(0xFFFF6600)
        3 -> Color(0xFFFFAA00)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
    Text(
        text = rank.toString(),
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier.width(22.dp),
    )
}

@Composable
private fun SearchResultTabs(
    scrollPosition: Float,
    onTabSelected: (SearchContentTab) -> Unit,
) {
    val tabs = SearchContentTab.entries
    SlidingTextTabs(
        labels = tabs.map { it.label },
        scrollPosition = scrollPosition,
        onTabSelected = { index -> onTabSelected(tabs[index]) },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = SearchContentTopGap),
    )
}

@Composable
private fun SearchUserRow(
    user: BiliSearchUserItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RemoteImage(
            url = user.face,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.name.ifBlank { "UP主" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.level > 0) {
                    BiliUserLevelIcon(
                        level = user.level,
                        width = 24.dp,
                        height = 15.dp,
                    )
                }
            }
            Text(
                text = "${formatBiliCount(user.fans)} 粉丝",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (user.sign.isNotBlank()) {
                Text(
                    text = user.sign,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchPinnedMediaSection(
    media: List<BiliSearchBangumi>,
    columnCount: Int,
    hasMore: Boolean,
    loadingMore: Boolean,
    onBangumiClick: (BiliSearchBangumi) -> Unit,
    onLoadMore: () -> Unit,
) {
    var isExpanded by remember(media) { mutableStateOf(false) }
    var selectedCategory by remember(media) { mutableStateOf<String?>(null) }
    val categories = remember(media) { BiliSearchBangumi.availableCategories(media) }
    val filteredMedia = remember(media, selectedCategory) {
        selectedCategory?.let { category ->
            media.filter { it.categoryName == category }
        } ?: media
    }
    val previewCount = columnCount.coerceAtLeast(1)
    val visibleMedia = if (isExpanded) {
        filteredMedia
    } else {
        filteredMedia.take(previewCount)
    }
    val canExpand = !isExpanded && (filteredMedia.size > previewCount || hasMore)
    val canCollapse = isExpanded && (filteredMedia.size > previewCount || hasMore)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "相关作品",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (canExpand) {
                TextButton(onClick = { isExpanded = true }) {
                    Text("展开", color = BiliPink)
                }
            } else if (canCollapse) {
                TextButton(onClick = { isExpanded = false }) {
                    Text("收起", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (categories.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchPinnedCategoryChip(
                    title = "全部",
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                )
                categories.forEach { category ->
                    SearchPinnedCategoryChip(
                        title = category,
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        if (visibleMedia.isEmpty() && selectedCategory != null) {
            Text(
                text = "暂无${selectedCategory}相关结果",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            val rows = visibleMedia.chunked(columnCount.coerceAtLeast(1))
            Column(verticalArrangement = Arrangement.spacedBy(HomeFeedGridSpacing)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HomeFeedGridSpacing),
                    ) {
                        row.forEach { bangumi ->
                            SearchBangumiCard(
                                bangumi = bangumi,
                                onClick = { onBangumiClick(bangumi) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columnCount - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (isExpanded && hasMore && selectedCategory == null) {
                if (loadingMore) {
                    SearchLoadingMoreIndicator()
                } else {
                    TextButton(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("加载更多", color = BiliPink)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SearchPinnedCategoryChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = title,
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) BiliPink.copy(alpha = 0.12f) else Color(0x0D000000),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) BiliPink else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
