package com.example.bilibili.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.data.BiliCommentItem
import com.example.bilibili.data.BiliCommentSort
import com.example.bilibili.data.BiliDynamicIpWebResolver
import com.example.bilibili.data.BiliDynamicItem
import com.example.bilibili.data.BiliUserProfile
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BiliViewerImage
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.ui.components.BiliCommentAvatarSize
import com.example.bilibili.ui.components.BiliCommentAuthorFontSize
import com.example.bilibili.ui.components.BiliCommentListEntry
import com.example.bilibili.ui.components.BiliCommentReplyEntry
import com.example.bilibili.ui.components.BiliCommentReplyFooterEntry
import com.example.bilibili.ui.components.BiliCommentReplyFooterRow
import com.example.bilibili.ui.components.BiliCommentRootEntry
import com.example.bilibili.ui.components.BiliCommentRow
import com.example.bilibili.ui.components.BiliCommentRowOuterStart
import com.example.bilibili.ui.components.BiliCommentSortToggle
import com.example.bilibili.ui.components.BiliCommentText
import com.example.bilibili.ui.components.BiliUserLevelIcon
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.buildBiliCommentListEntries
import com.example.bilibili.ui.components.findAutoLoadSubReplyRootId
import com.example.bilibili.ui.components.imageviewer.BiliFullscreenImageViewer
import com.example.bilibili.ui.components.imageviewer.BiliImageGrid
import com.example.bilibili.ui.components.resolveCommentsEnd
import com.example.bilibili.ui.components.resolveShownCommentReplies
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.format.formatBiliPublishTime
import com.example.bilibili.ui.theme.BiliPink
import com.example.bilibili.util.BiliLinkTarget
import com.example.bilibili.util.ExternalUrlOpener
import com.example.bilibili.player.StatusBarIconsEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val DynamicCommentPageSize = 20

private data class DynamicCommentImageViewerRequest(
    val images: List<BiliViewerImage>,
    val initialIndex: Int,
    val sourceBoundsByIndex: Map<Int, Rect>,
)

@Composable
fun DynamicDetailScreen(
    item: BiliDynamicItem,
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    onBack: () -> Unit,
    onAuthorClick: (BiliUserProfile) -> Unit = {},
    onOpenVideo: (BiliVideoItem, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val referer = remember(item.id) { "https://t.bilibili.com/${item.id}" }

    var commentSort by remember(item.id) { mutableStateOf(BiliCommentSort.Hot) }
    var comments by remember(item.id) { mutableStateOf<List<BiliCommentItem>>(emptyList()) }
    var commentCount by remember(item.id) { mutableStateOf(item.commentCount) }
    var commentsLoading by remember(item.id) { mutableStateOf(false) }
    var commentsRefreshing by remember(item.id) { mutableStateOf(false) }
    var commentsLoadingMore by remember(item.id) { mutableStateOf(false) }
    var commentsNextCursor by remember(item.id) { mutableStateOf<String?>(null) }
    var commentsEnd by remember(item.id) { mutableStateOf(true) }
    var commentsLastRequestedCursor by remember(item.id, commentSort) { mutableStateOf<String?>(null) }
    var expandedCommentRoots by remember(item.id) { mutableStateOf(setOf<Long>()) }
    var loadedSubReplies by remember(item.id) { mutableStateOf(mapOf<Long, List<BiliCommentItem>>()) }
    var subRepliesLoading by remember(item.id) { mutableStateOf(setOf<Long>()) }
    var subRepliesEnd by remember(item.id) { mutableStateOf(setOf<Long>()) }
    var commentImageViewer by remember(item.id) { mutableStateOf<DynamicCommentImageViewerRequest?>(null) }
    var detailItem by remember(item.id) { mutableStateOf(item) }

    val listState = rememberLazyListState()
    val commentsLoadMutex = remember(item.id) { Mutex() }
    var commentsNearEndLoaded by remember(item.id, commentSort) { mutableStateOf(false) }

    val handleCommentLinkClick: (BiliLinkTarget) -> Unit = { target ->
        when (target) {
            is BiliLinkTarget.UserSpace -> {
                onAuthorClick(
                    BiliUserProfile(
                        mid = target.mid,
                        name = "",
                        face = "",
                        sign = "",
                        level = 0,
                    ),
                )
            }
            is BiliLinkTarget.Video -> {
                onOpenVideo(
                    BiliVideoItem(
                        bvid = target.bvid,
                        aid = target.aid,
                        title = "",
                        coverUrl = "",
                        authorName = detailItem.authorName,
                        authorMid = detailItem.authorMid,
                        viewCount = 0,
                        danmakuCount = 0,
                        likeCount = 0,
                        durationSeconds = 0,
                    ),
                    target.partPage,
                )
            }
            is BiliLinkTarget.External -> {
                ExternalUrlOpener.open(context, target.url)
            }
        }
    }

    suspend fun loadComments(reset: Boolean) {
        if (item.commentOid <= 0L || item.commentType <= 0) return
        commentsLoadMutex.withLock {
            if (reset) {
                commentsLoading = true
                commentsNextCursor = null
                commentsEnd = false
                commentsNearEndLoaded = false
                commentsLastRequestedCursor = null
                expandedCommentRoots = emptySet()
                loadedSubReplies = emptyMap()
                subRepliesLoading = emptySet()
                subRepliesEnd = emptySet()
            } else {
                if (commentsEnd || commentsLoadingMore || comments.isEmpty() || commentsLoading) return@withLock
                val cursor = commentsNextCursor
                if (cursor.isNullOrBlank()) {
                    commentsEnd = true
                    return@withLock
                }
                if (cursor == commentsLastRequestedCursor) {
                    return@withLock
                }
                commentsLoadingMore = true
                commentsLastRequestedCursor = cursor
            }
            val previousCount = if (reset) 0 else comments.size
            val requestedCursor = if (reset) null else commentsNextCursor
            try {
                runCatching {
                    api.getSubjectComments(
                        oid = item.commentOid,
                        type = item.commentType,
                        sort = commentSort,
                        paginationStr = if (reset) null else commentsNextCursor,
                        credential = credential,
                        referer = referer,
                    )
                }.onSuccess { page ->
                    val mergedComments = if (reset) {
                        page.comments
                    } else {
                        (comments + page.comments).distinctBy { it.id }
                    }
                    comments = mergedComments
                    commentsNextCursor = page.nextCursor
                    commentsEnd = resolveCommentsEnd(
                        page = page,
                        mergedCount = mergedComments.size,
                        previousCount = previousCount,
                        expectedTotal = commentCount,
                    )
                    if (!reset) {
                        val normalizedNext = page.nextCursor?.trim().orEmpty()
                        val normalizedReq = requestedCursor?.trim().orEmpty()
                        if (mergedComments.size == previousCount &&
                            (page.comments.isEmpty() || normalizedNext == normalizedReq)
                        ) {
                            commentsEnd = true
                            commentsNextCursor = null
                        }
                    }
                    if (reset) {
                        commentCount = when {
                            page.totalCount > 0L -> page.totalCount
                            item.commentCount > 0L -> item.commentCount
                            else -> page.comments.size.toLong()
                        }
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    if (reset) {
                        comments = emptyList()
                        commentsEnd = true
                    } else {
                        Toast.makeText(context, error.message ?: "加载更多评论失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                commentsLoading = false
                commentsLoadingMore = false
            }
        }
    }

    suspend fun loadSubReplies(rootId: Long, reset: Boolean) {
        if (item.commentOid <= 0L || rootId in subRepliesLoading || rootId in subRepliesEnd) return
        val rootComment = comments.find { it.id == rootId } ?: return
        subRepliesLoading = subRepliesLoading + rootId
        try {
            if (reset) {
                loadedSubReplies = loadedSubReplies - rootId
            }
            val alreadyLoaded = loadedSubReplies[rootId]?.size ?: 0
            val pn = alreadyLoaded / DynamicCommentPageSize + 1
            runCatching {
                api.getCommentReplies(
                    oid = item.commentOid,
                    type = item.commentType,
                    rootRpid = rootId,
                    referer = referer,
                    pn = pn,
                    credential = credential,
                )
            }.onSuccess { page ->
                loadedSubReplies = loadedSubReplies.toMutableMap().apply {
                    val existing = this[rootId].orEmpty()
                    this[rootId] = (existing + page.replies).distinctBy { it.id }
                }
                expandedCommentRoots = expandedCommentRoots + rootId
                val loadedCount = resolveShownCommentReplies(rootComment, loadedSubReplies).size
                val replyTotal = rootComment.replyCount
                if (page.isEnd || page.replies.isEmpty() || (replyTotal > 0L && loadedCount >= replyTotal)) {
                    subRepliesEnd = subRepliesEnd + rootId
                } else {
                    subRepliesEnd = subRepliesEnd - rootId
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Toast.makeText(context, error.message ?: "回复加载失败", Toast.LENGTH_SHORT).show()
            }
        } finally {
            subRepliesLoading = subRepliesLoading - rootId
        }
    }

    LaunchedEffect(item.id, item.commentOid, item.commentType, commentSort) {
        commentsNearEndLoaded = false
        loadComments(reset = true)
    }

    LaunchedEffect(item.id, credential?.sessdata) {
        if (!detailItem.ipLocation.isNullOrBlank()) return@LaunchedEffect
        val ipLocation = runCatching {
            credential?.let { api.getDynamicAuthorIpLocation(item.id, it) }
                ?: api.getDynamicDetail(item.id, credential)?.ipLocation?.takeIf { !it.isNullOrBlank() }
                ?: BiliDynamicIpWebResolver.resolve(context, item.id, credential)
        }.getOrNull()?.takeIf { !it.isNullOrBlank() } ?: return@LaunchedEffect
        detailItem = detailItem.copy(ipLocation = ipLocation)
    }

    val commentEntries by remember {
        derivedStateOf {
            buildBiliCommentListEntries(
                comments = comments,
                loadedSubReplies = loadedSubReplies,
                expandedCommentRoots = expandedCommentRoots,
                subRepliesEnd = subRepliesEnd,
                subRepliesLoading = subRepliesLoading,
            )
        }
    }

    LaunchedEffect(listState, commentEntries) {
        snapshotFlow {
            findAutoLoadSubReplyRootId(commentEntries, listState.layoutInfo)
        }
            .distinctUntilChanged()
            .collect { rootId ->
                if (rootId != null && rootId !in subRepliesLoading && rootId !in subRepliesEnd) {
                    loadSubReplies(rootId, reset = false)
                }
            }
    }

    LaunchedEffect(listState, commentsEnd, commentsLoading, commentsNextCursor, commentSort, item.id) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearEnd = total > 0 && lastVisible >= total - 2
            listState.isScrollInProgress to nearEnd
        }
            .distinctUntilChanged()
            .collect { (scrolling, nearEnd) ->
                if (scrolling) {
                    commentsNearEndLoaded = false
                    return@collect
                }
                if (!nearEnd) {
                    commentsNearEndLoaded = false
                    return@collect
                }
                if (commentsNearEndLoaded) return@collect
                if (comments.isEmpty() || commentsEnd || commentsLoading || commentsLoadingMore) return@collect
                if (commentsNextCursor.isNullOrBlank()) return@collect
                commentsNearEndLoaded = true
                loadComments(reset = false)
            }
    }

    BackHandler(onBack = onBack)

    StatusBarIconsEffect(darkIcons = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PullToRefreshBox(
            isRefreshing = commentsRefreshing,
            onRefresh = {
                scope.launch {
                    commentsRefreshing = true
                    runCatching { loadComments(reset = true) }
                        .onFailure {
                            Toast.makeText(context, it.message ?: "刷新失败", Toast.LENGTH_SHORT).show()
                        }
                    commentsRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "dynamic-author") {
                    DynamicDetailAuthorSection(
                        item = detailItem,
                        onAuthorClick = onAuthorClick,
                    )
                }
                if (detailItem.text.isNotBlank()) {
                    item(key = "dynamic-text") {
                        BiliCommentText(
                            text = detailItem.text,
                            emoticons = detailItem.emoticons,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                        )
                    }
                }
                if (detailItem.imageUrls.isNotEmpty()) {
                    item(key = "dynamic-images") {
                        BiliImageGrid(
                            images = detailItem.imageUrls.map(BiliViewerImage::fromUrl),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                if (!detailItem.ipLocation.isNullOrBlank()) {
                    item(key = "dynamic-ip") {
                        Text(
                            text = "IP 属地：${detailItem.ipLocation}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                item(key = "comment-header") {
                    DynamicDetailCommentHeader(
                        commentCount = commentCount,
                        commentSort = commentSort,
                        onSortToggle = {
                            commentSort = if (commentSort == BiliCommentSort.Time) {
                                BiliCommentSort.Hot
                            } else {
                                BiliCommentSort.Time
                            }
                        },
                    )
                }
                if (commentsLoading && comments.isEmpty()) {
                    item(key = "comment-loading") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                if (comments.isEmpty() && !commentsLoading) {
                    item(key = "comment-empty") {
                        Text(
                            text = "暂无评论",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            fontSize = BiliCommentAuthorFontSize,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                items(commentEntries, key = { it.stableKey }) { entry ->
                    when (entry) {
                        is BiliCommentRootEntry -> {
                            BiliCommentRow(
                                comment = entry.comment,
                                depth = 0,
                                onAuthorClick = onAuthorClick,
                                onLinkClick = handleCommentLinkClick,
                                onCommentImageClick = { pictures, index, bounds ->
                                    commentImageViewer = DynamicCommentImageViewerRequest(
                                        images = pictures.map(BiliViewerImage::fromCommentPicture),
                                        initialIndex = index,
                                        sourceBoundsByIndex = mapOf(index to bounds),
                                    )
                                },
                            )
                        }
                        is BiliCommentReplyEntry -> {
                            BiliCommentRow(
                                comment = entry.reply,
                                depth = 1,
                                onAuthorClick = onAuthorClick,
                                onLinkClick = handleCommentLinkClick,
                                onCommentImageClick = { pictures, index, bounds ->
                                    commentImageViewer = DynamicCommentImageViewerRequest(
                                        images = pictures.map(BiliViewerImage::fromCommentPicture),
                                        initialIndex = index,
                                        sourceBoundsByIndex = mapOf(index to bounds),
                                    )
                                },
                            )
                        }
                        is BiliCommentReplyFooterEntry -> {
                            BiliCommentReplyFooterRow(
                                remainingCount = entry.remainingCount,
                                isLoading = entry.isLoading,
                                isFirstExpand = entry.isFirstExpand,
                                nestedContentStart = BiliCommentRowOuterStart + BiliCommentAvatarSize + 10.dp,
                                onClick = {
                                    val reset = entry.isFirstExpand
                                    scope.launch { loadSubReplies(entry.rootId, reset = reset) }
                                },
                            )
                        }
                    }
                }
                if (commentsLoadingMore && !commentsEnd) {
                    item(key = "comment-loading-more") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }

    commentImageViewer?.let { request ->
        BiliFullscreenImageViewer(
            images = request.images,
            initialIndex = request.initialIndex,
            sourceBoundsByIndex = request.sourceBoundsByIndex,
            animateOpenFromSource = true,
            onDismiss = { commentImageViewer = null },
        )
    }
}

@Composable
private fun DynamicDetailAuthorSection(
    item: BiliDynamicItem,
    onAuthorClick: (BiliUserProfile) -> Unit,
) {
    val canOpenProfile = item.authorMid > 0L
    val openProfile = {
        if (canOpenProfile) {
            onAuthorClick(
                BiliUserProfile(
                    mid = item.authorMid,
                    name = item.authorName,
                    face = item.authorFace,
                    sign = "",
                    level = item.authorLevel,
                ),
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .clickable(
                enabled = canOpenProfile,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = openProfile,
            )
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.authorFace.isNotBlank()) {
            RemoteImage(
                url = item.authorFace,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.authorName.ifBlank { "UP主" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = BiliPink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.authorLevel > 0) {
                    BiliUserLevelIcon(level = item.authorLevel)
                }
            }
            if (item.publishTimeSeconds > 0L) {
                Text(
                    text = formatBiliPublishTime(item.publishTimeSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun DynamicDetailCommentHeader(
    commentCount: Long,
    commentSort: BiliCommentSort,
    onSortToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "评论 ${formatBiliCount(commentCount)}",
                fontSize = 11.sp,
                color = BiliPink,
            )
            BiliCommentSortToggle(
                selected = commentSort,
                onToggle = onSortToggle,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
    }
}
