package com.example.bilibili.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.example.bilibili.data.BiliAuthorRelation
import com.example.bilibili.data.BiliRelationUser
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.data.UserRelationTab
import com.example.bilibili.player.StatusBarIconsEffect
import com.example.bilibili.ui.components.BilibiliFollowButton
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.SlidingTextTabs
import com.example.bilibili.ui.format.formatBiliCount
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

private const val RelationPageSize = 20
private const val RelationGuestMaxPage = 5

private data class RelationTabCache(
    val users: List<BiliRelationUser>,
    val page: Int,
    val hasMore: Boolean,
    val errorMessage: String?,
)

private fun relationTabCacheKey(hostMid: Long, tab: UserRelationTab): String =
    "$hostMid|${tab.name}"

private fun relationListHasMore(
    fetchedCount: Int,
    page: Int,
    isSelf: Boolean,
): Boolean {
    if (fetchedCount < RelationPageSize) return false
    if (isSelf) return true
    return page < RelationGuestMaxPage
}

@Composable
fun UserRelationListScreen(
    hostMid: Long,
    hostName: String,
    hostFace: String,
    hostSign: String,
    initialTab: UserRelationTab,
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    myMid: Long?,
    onBack: () -> Unit,
    onUserClick: (Long, String, String) -> Unit,
    onLoginRequired: () -> Unit,
    handleSystemBack: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val tabs = UserRelationTab.entries
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(initialTab).coerceAtLeast(0),
        pageCount = { tabs.size },
    )
    val followingListState = rememberLazyListState()
    val followersListState = rememberLazyListState()
    val tabCaches = remember { mutableStateMapOf<String, RelationTabCache>() }
    var selectedTab by remember { mutableStateOf(initialTab) }

    BackHandler(enabled = handleSystemBack, onBack = onBack)
    StatusBarIconsEffect(darkIcons = true)

    LaunchedEffect(initialTab) {
        val target = tabs.indexOf(initialTab).coerceAtLeast(0)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
        selectedTab = initialTab
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                selectedTab = tabs[page]
            }
    }

    val tabScrollPosition by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = topInset + 12.dp,
                        bottom = 12.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RemoteImage(
                    url = hostFace,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = hostName.ifBlank { "UP主" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (hostSign.isNotBlank()) {
                        Text(
                            text = hostSign,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            SlidingTextTabs(
                labels = tabs.map { it.label },
                scrollPosition = tabScrollPosition,
                onTabSelected = { index ->
                    val tab = tabs[index]
                    val sameTab = tab == tabs[pagerState.currentPage]
                    if (sameTab) {
                        scope.launch {
                            when (tab) {
                                UserRelationTab.Following -> followingListState.animateScrollToItem(0)
                                UserRelationTab.Followers -> followersListState.animateScrollToItem(0)
                            }
                        }
                    } else {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, top = 2.dp, bottom = 8.dp),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                beyondViewportPageCount = 1,
            ) { page ->
                UserRelationTabPage(
                    hostMid = hostMid,
                    tab = tabs[page],
                    api = api,
                    credential = credential,
                    myMid = myMid,
                    tabCaches = tabCaches,
                    listState = when (tabs[page]) {
                        UserRelationTab.Following -> followingListState
                        UserRelationTab.Followers -> followersListState
                    },
                    onUserClick = onUserClick,
                    onLoginRequired = onLoginRequired,
                    showToast = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}

@Composable
private fun UserRelationTabPage(
    hostMid: Long,
    tab: UserRelationTab,
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    myMid: Long?,
    tabCaches: MutableMap<String, RelationTabCache>,
    listState: LazyListState,
    onUserClick: (Long, String, String) -> Unit,
    onLoginRequired: () -> Unit,
    showToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cacheKey = relationTabCacheKey(hostMid, tab)
    val isSelf = myMid != null && myMid == hostMid
    var users by remember(hostMid, tab) { mutableStateOf<List<BiliRelationUser>>(emptyList()) }
    var page by remember(hostMid, tab) { mutableIntStateOf(1) }
    var hasMore by remember(hostMid, tab) { mutableStateOf(true) }
    var loading by remember(hostMid, tab) { mutableStateOf(true) }
    var loadingMore by remember(hostMid, tab) { mutableStateOf(false) }
    var errorMessage by remember(hostMid, tab) { mutableStateOf<String?>(null) }
    val loadMutex = remember(hostMid, tab) { Mutex() }

    fun persistCache() {
        tabCaches[cacheKey] = RelationTabCache(
            users = users,
            page = page,
            hasMore = hasMore,
            errorMessage = errorMessage,
        )
    }

    suspend fun loadPage(targetPage: Int, reset: Boolean) {
        val result = api.getUserRelationListPage(
            mid = hostMid,
            tab = tab,
            page = targetPage,
            pageSize = RelationPageSize,
            credential = credential,
        )
        if (reset) {
            errorMessage = result.errorMessage
            users = result.users
            page = targetPage
            hasMore = result.errorMessage == null &&
                relationListHasMore(result.users.size, targetPage, isSelf)
        } else {
            if (result.errorMessage != null) {
                showToast(result.errorMessage)
                return
            }
            val previousSize = users.size
            val merged = (users + result.users).distinctBy { it.mid }
            users = merged
            page = targetPage
            hasMore = relationListHasMore(result.users.size, targetPage, isSelf) &&
                merged.size > previousSize
        }
        persistCache()
    }

    LaunchedEffect(hostMid, tab) {
        tabCaches[cacheKey]?.let { cache ->
            users = cache.users
            page = cache.page
            hasMore = cache.hasMore
            errorMessage = cache.errorMessage
            loading = false
            return@LaunchedEffect
        }
        loading = true
        loadingMore = false
        users = emptyList()
        page = 1
        hasMore = true
        errorMessage = null
        loadPage(targetPage = 1, reset = true)
        loading = false
    }

    LaunchedEffect(listState, hostMid, tab, hasMore, loading, loadingMore, errorMessage) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                loadMutex.withLock {
                    if (loading || loadingMore || !hasMore || errorMessage != null) return@withLock
                    loadingMore = true
                    try {
                        loadPage(page + 1, reset = false)
                        persistCache()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showToast(e.message ?: "加载失败")
                    } finally {
                        loadingMore = false
                    }
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        when {
            loading && users.isEmpty() -> {
                item(key = "relation-loading") {
                    Box(
                        Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            users.isEmpty() -> {
                item(key = "relation-empty") {
                    Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = errorMessage ?: if (tab == UserRelationTab.Followers) {
                                "还没有粉丝"
                            } else {
                                "还没有关注"
                            },
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                items(
                    count = users.size,
                    key = { index -> "${tab.name}-${users[index].mid}" },
                ) { index ->
                    val user = users[index]
                    RelationUserRow(
                        user = user,
                        showFollowButton = credential != null &&
                            myMid != null &&
                            user.mid != myMid,
                        onClick = { onUserClick(user.mid, user.name, user.face) },
                        onFollowClick = {
                            val cred = credential ?: run {
                                onLoginRequired()
                                return@RelationUserRow
                            }
                            val targetFollow = !user.relation.following
                            val previous = user.relation
                            users = users.map { item ->
                                if (item.mid == user.mid) {
                                    item.copy(
                                        relation = item.relation.copy(following = targetFollow),
                                    )
                                } else {
                                    item
                                }
                            }
                            val result = api.modifyFollow(
                                mid = user.mid,
                                follow = targetFollow,
                                credential = cred,
                                referer = "https://space.bilibili.com/$hostMid",
                            )
                            if (result.isSuccess) {
                                val updated = api.getUserRelation(
                                    mid = user.mid,
                                    credential = cred,
                                    referer = "https://space.bilibili.com/$hostMid",
                                )
                                users = users.map { item ->
                                    if (item.mid == user.mid) item.copy(relation = updated) else item
                                }
                                persistCache()
                            } else {
                                users = users.map { item ->
                                    if (item.mid == user.mid) item.copy(relation = previous) else item
                                }
                                showToast(result.exceptionOrNull()?.message ?: "操作失败")
                            }
                        },
                    )
                }
                if (loadingMore) {
                    item(key = "relation-loading-more") {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                if (!hasMore && users.isNotEmpty()) {
                    item(key = "relation-end") {
                        Text(
                            text = "已经到底了",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationUserRow(
    user: BiliRelationUser,
    showFollowButton: Boolean,
    onClick: () -> Unit,
    onFollowClick: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var followLoading by remember(user.mid) { mutableStateOf(false) }
    val relation = user.relation

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RemoteImage(
                url = user.face,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = user.name.ifBlank { "用户" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.sign.isNotBlank()) {
                    Text(
                        text = user.sign,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val metaParts = buildList {
                    if (user.fanCount > 0L) {
                        add("${formatBiliCount(user.fanCount)} 粉丝")
                    }
                    user.ipLocation?.let { location ->
                        add("IP属地：$location")
                    }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString("  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showFollowButton) {
                Spacer(Modifier.width(8.dp))
                BilibiliFollowButton(
                    following = relation.following,
                    followerMe = relation.followerMe,
                    loading = followLoading,
                    onClick = {
                        if (followLoading) return@BilibiliFollowButton
                        scope.launch {
                            followLoading = true
                            try {
                                onFollowClick()
                            } finally {
                                followLoading = false
                            }
                        }
                    },
                    compact = true,
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 76.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
        )
    }
}
