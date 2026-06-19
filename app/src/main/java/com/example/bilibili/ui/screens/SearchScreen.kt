package com.example.bilibili.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.data.BiliHotSearchItem
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliSearchUserItem
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.VideoFeedCard
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.theme.BiliPink
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

private enum class SearchContentTab(val label: String) {
    Videos("视频"),
    Users("UP主"),
}

@Composable
fun SearchScreen(
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    playUrls: Map<String, BiliPlayStream>,
    coordinator: VideoPlaybackCoordinator,
    backdrop: Backdrop,
    onClose: () -> Unit,
    onVideoClick: (BiliVideoItem) -> Unit,
    onUserClick: (Long, String, String) -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
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
    var resultError by remember { mutableStateOf<String?>(null) }
    var searchGeneration by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(pageCount = { SearchContentTab.entries.size })
    val videoListState = rememberLazyListState()
    val userListState = rememberLazyListState()

    BackHandler(onBack = onClose)

    fun submitQuery(raw: String) {
        val normalized = raw.trim()
        if (normalized.isBlank()) return
        queryInput = normalized
        activeQuery = normalized
        videos = emptyList()
        users = emptyList()
        videoPage = 1
        userPage = 1
        videoHasMore = false
        userHasMore = false
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
        loadVideos(reset = true, generation = generation)
        loadUsers(reset = true, generation = generation)
    }

    LaunchedEffect(videoListState, activeQuery, searchGeneration, videoHasMore) {
        if (activeQuery == null) return@LaunchedEffect
        snapshotFlow {
            val info = videoListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            if (total <= 0 || last < total - 3) null else last
        }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        SearchInputCapsule(
            query = queryInput,
            onQueryChange = {
                queryInput = it
                if (activeQuery != null) activeQuery = null
            },
            onSearch = { submitQuery(queryInput) },
            backdrop = backdrop,
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
                .padding(bottom = HomeSearchBarBottomGap),
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
                SearchHotPanel(
                    hotWords = hotWords,
                    loading = hotLoading,
                    onSelect = ::submitQuery,
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
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

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
                        when {
                            videoLoading && videos.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            videos.isEmpty() && !videoLoading -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "没有找到相关视频",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(
                                    state = videoListState,
                                    contentPadding = PaddingValues(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 8.dp,
                                        bottom = 24.dp,
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
                                            onAuthorClick = { mid ->
                                                onUserClick(mid, video.authorName, "")
                                            },
                                            overlayMetaOnCover = true,
                                        )
                                    }
                                    if (videoLoadingMore) {
                                        item(key = "video-loading-more") {
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
                                    contentPadding = PaddingValues(bottom = 24.dp),
                                ) {
                                    items(users, key = { it.mid }) { user ->
                                        SearchUserRow(
                                            user = user,
                                            onClick = { onUserClick(user.mid, user.name, user.face) },
                                        )
                                    }
                                    if (userLoadingMore) {
                                        item(key = "user-loading-more") {
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
private fun SearchHotPanel(
    hotWords: List<BiliHotSearchItem>,
    loading: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "B站热搜",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            hotWords.isEmpty() -> {
                Text(
                    text = "暂无热搜",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            else -> {
                hotWords.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item.keyword) }
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
                }
            }
        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SearchContentTab.entries.forEachIndexed { index, tab ->
            val selected = scrollPosition.roundToInt() == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onTabSelected(tab) },
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) BiliPink else Color.Transparent),
                )
            }
        }
    }
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
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.level > 0) {
                    Spacer(Modifier.width(6.dp))
                    SearchLevelBadge(user.level)
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
private fun SearchLevelBadge(level: Int) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = BiliPink.copy(alpha = 0.12f),
    ) {
        Text(
            text = "LV$level",
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = BiliPink,
            fontWeight = FontWeight.Bold,
        )
    }
}
