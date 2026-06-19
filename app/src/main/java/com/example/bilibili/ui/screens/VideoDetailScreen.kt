package com.example.bilibili.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.data.BiliAuthorCard
import com.example.bilibili.data.BiliAuthorRelation
import com.example.bilibili.data.BiliCommentItem
import com.example.bilibili.data.BiliCommentPage
import com.example.bilibili.data.BiliCommentPicture
import com.example.bilibili.data.BiliCommentSort
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliUserProfile
import com.example.bilibili.data.BiliVideoDetail
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.player.BilibiliVideoSurface
import com.example.bilibili.player.LightContentStatusBarEffect
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.player.rememberVideoControlBackdrop
import com.example.bilibili.player.videoPlaybackKey
import com.example.bilibili.ui.components.BiliCommentImageStrip
import com.example.bilibili.ui.components.BiliCommentText
import com.example.bilibili.ui.components.BilibiliFollowButton
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.VideoDetailTab
import com.example.bilibili.ui.components.VideoDetailTabBar
import com.example.bilibili.ui.format.formatBiliCommentTime
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.format.formatBiliPublishTime
import com.example.bilibili.ui.theme.BiliPink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val VideoDetailPlayerTopPadding = 8.dp
private const val CommentPageSize = 20

private val CommentRowOuterStart = 18.dp
private val CommentAvatarSize = 34.dp
private val CommentAuthorFontSize = 13.sp

private fun placeholderAuthorCard(video: BiliVideoItem): BiliAuthorCard =
    BiliAuthorCard(
        profile = BiliUserProfile(
            mid = video.authorMid,
            name = video.authorName.ifBlank { "UP主" },
            face = "",
            sign = "",
            level = 0,
        ),
    )

private sealed interface CommentListEntry {
    val stableKey: String
}

private data class CommentRootEntry(
    val comment: BiliCommentItem,
) : CommentListEntry {
    override val stableKey: String = "root-${comment.id}"
}

private data class CommentReplyEntry(
    val rootId: Long,
    val reply: BiliCommentItem,
) : CommentListEntry {
    override val stableKey: String = "reply-${rootId}-${reply.id}"
}

private data class CommentReplyFooterEntry(
    val rootId: Long,
    val remainingCount: Long,
    val isLoading: Boolean,
    val isFirstExpand: Boolean,
) : CommentListEntry {
    override val stableKey: String = "reply-footer-$rootId"
}

private fun resolveShownReplies(
    comment: BiliCommentItem,
    loadedSubReplies: Map<Long, List<BiliCommentItem>>,
): List<BiliCommentItem> =
    (comment.replies + loadedSubReplies[comment.id].orEmpty()).distinctBy { it.id }

private fun buildCommentListEntries(
    comments: List<BiliCommentItem>,
    loadedSubReplies: Map<Long, List<BiliCommentItem>>,
    expandedCommentRoots: Set<Long>,
    subRepliesEnd: Set<Long>,
    subRepliesLoading: Set<Long>,
): List<CommentListEntry> = buildList {
    for (comment in comments) {
        add(CommentRootEntry(comment))
        if (comment.replyCount <= 0L) continue
        val shownReplies = resolveShownReplies(comment, loadedSubReplies)
        shownReplies.forEach { reply ->
            add(CommentReplyEntry(comment.id, reply))
        }
        val hasMoreReplies = comment.replyCount > shownReplies.size && comment.id !in subRepliesEnd
        if (hasMoreReplies) {
            val remainingCount = (comment.replyCount - shownReplies.size).coerceAtLeast(0L)
            val isFirstExpand = shownReplies.isEmpty() || comment.id !in expandedCommentRoots
            add(
                CommentReplyFooterEntry(
                    rootId = comment.id,
                    remainingCount = remainingCount,
                    isLoading = comment.id in subRepliesLoading,
                    isFirstExpand = isFirstExpand,
                ),
            )
        }
    }
}

private fun findAutoLoadSubReplyRootId(
    entries: List<CommentListEntry>,
    layoutInfo: LazyListLayoutInfo,
): Long? {
    val total = layoutInfo.totalItemsCount
    if (total == 0) return null
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return null
    if (lastVisibleIndex < total - 2) return null
    for (index in lastVisibleIndex downTo (lastVisibleIndex - 2).coerceAtLeast(0)) {
        when (val entry = entries.getOrNull(index)) {
            is CommentReplyFooterEntry -> {
                if (!entry.isLoading && !entry.isFirstExpand) return entry.rootId
            }
            is CommentReplyEntry -> {
                val next = entries.getOrNull(index + 1)
                if (next is CommentReplyFooterEntry && !next.isLoading && !next.isFirstExpand) {
                    return next.rootId
                }
            }
            else -> Unit
        }
    }
    return null
}

@Composable
fun VideoDetailScreen(
    seedVideo: BiliVideoItem,
    playStream: BiliPlayStream?,
    api: BilibiliApiClient,
    coordinator: VideoPlaybackCoordinator,
    credential: BilibiliCredential?,
    myMid: Long?,
    onLoginRequired: () -> Unit,
    onAuthorClick: (BiliUserProfile) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playbackKey = remember(seedVideo.bvid) { videoPlaybackKey(seedVideo.bvid, ownerId = "detail") }
    val controlBackdrop = rememberVideoControlBackdrop()

    var detail by remember(seedVideo.bvid) { mutableStateOf<BiliVideoDetail?>(null) }
    var authorCard by remember(seedVideo.bvid) { mutableStateOf(placeholderAuthorCard(seedVideo)) }
    var onlineCount by remember(seedVideo.bvid) { mutableStateOf(0L) }
    var commentSort by remember(seedVideo.bvid) { mutableStateOf(BiliCommentSort.Hot) }
    var comments by remember(seedVideo.bvid) { mutableStateOf<List<BiliCommentItem>>(emptyList()) }
    var commentCount by remember(seedVideo.bvid) { mutableStateOf(0L) }
    var commentsLoading by remember(seedVideo.bvid) { mutableStateOf(false) }
    var commentsRefreshing by remember(seedVideo.bvid) { mutableStateOf(false) }
    var commentsLoadingMore by remember(seedVideo.bvid) { mutableStateOf(false) }
    var commentsNextCursor by remember(seedVideo.bvid) { mutableStateOf<String?>(null) }
    var commentsEnd by remember(seedVideo.bvid) { mutableStateOf(true) }
    var followLoading by remember(seedVideo.bvid) { mutableStateOf(false) }
    var expandedCommentRoots by remember(seedVideo.bvid) { mutableStateOf(setOf<Long>()) }
    var loadedSubReplies by remember(seedVideo.bvid) { mutableStateOf(mapOf<Long, List<BiliCommentItem>>()) }
    var subRepliesLoading by remember(seedVideo.bvid) { mutableStateOf(setOf<Long>()) }
    var subRepliesEnd by remember(seedVideo.bvid) { mutableStateOf(setOf<Long>()) }

    val pagerState = rememberPagerState(initialPage = 0) { VideoDetailTab.entries.size }
    val tabScrollPosition by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val introListState = rememberLazyListState()
    val commentsListState = rememberLazyListState()
    val commentsLoadMutex = remember(seedVideo.bvid) { Mutex() }

    suspend fun loadComments(reset: Boolean) {
        val aid = detail?.video?.aid ?: seedVideo.aid
        if (aid <= 0L) return
        commentsLoadMutex.withLock {
            if (reset) {
                commentsLoading = true
                commentsNextCursor = null
                commentsEnd = false
                expandedCommentRoots = emptySet()
                loadedSubReplies = emptyMap()
                subRepliesLoading = emptySet()
                subRepliesEnd = emptySet()
            } else {
                if (commentsEnd || commentsLoadingMore || comments.isEmpty() || commentsLoading) return@withLock
                if (commentsNextCursor.isNullOrBlank()) {
                    commentsEnd = true
                    return@withLock
                }
                commentsLoadingMore = true
            }
            val previousCount = comments.size
            try {
                runCatching {
                    api.getVideoComments(
                        aid = aid,
                        sort = commentSort,
                        paginationStr = if (reset) null else commentsNextCursor,
                        credential = credential,
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
                    )
                    if (reset) {
                        commentCount = when {
                            page.totalCount > 0L -> page.totalCount
                            detail?.replyCount != null && detail!!.replyCount > 0L -> detail!!.replyCount
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

    suspend fun reloadCommentsTab(refreshMeta: Boolean = true) {
        if (refreshMeta) {
            runCatching {
                val loadedDetail = api.getVideoDetail(seedVideo.bvid, credential)
                if (loadedDetail != null) {
                    detail = loadedDetail
                    commentCount = loadedDetail.replyCount
                    onlineCount = api.getVideoOnlineCount(
                        loadedDetail.video.bvid,
                        loadedDetail.video.aid,
                        loadedDetail.video.cid,
                        credential,
                    )
                }
            }
        }
        loadComments(reset = true)
    }

    suspend fun loadSubReplies(rootId: Long, reset: Boolean) {
        val aid = detail?.video?.aid ?: seedVideo.aid
        val bvid = detail?.video?.bvid ?: seedVideo.bvid
        if (aid <= 0L || rootId in subRepliesLoading || rootId in subRepliesEnd) return
        val rootComment = comments.find { it.id == rootId } ?: return
        subRepliesLoading = subRepliesLoading + rootId
        try {
            if (reset) {
                loadedSubReplies = loadedSubReplies - rootId
            }
            val alreadyLoaded = loadedSubReplies[rootId]?.size ?: 0
            val pn = alreadyLoaded / CommentPageSize + 1
            runCatching {
                api.getCommentReplies(
                    aid = aid,
                    rootRpid = rootId,
                    bvid = bvid,
                    pn = pn,
                    credential = credential,
                )
            }.onSuccess { page ->
                loadedSubReplies = loadedSubReplies.toMutableMap().apply {
                    val existing = this[rootId].orEmpty()
                    this[rootId] = (existing + page.replies).distinctBy { it.id }
                }
                expandedCommentRoots = expandedCommentRoots + rootId
                val loadedCount = resolveShownReplies(rootComment, loadedSubReplies).size
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

    LaunchedEffect(seedVideo.bvid, credential?.dedeUserId) {
        authorCard = placeholderAuthorCard(seedVideo)
        runCatching {
            coroutineScope {
                val authorMid = seedVideo.authorMid
                val profileDeferred = async {
                    runCatching { api.getUserInfo(authorMid, credential) }.getOrNull()
                }
                val navnumDeferred = async { api.getUserNavnum(authorMid, credential) }
                val relationDeferred = async {
                    credential?.let { cred ->
                        runCatching { api.getRelationStat(authorMid, cred) }
                            .getOrDefault(BiliAuthorRelation())
                    } ?: BiliAuthorRelation()
                }
                val detailDeferred = async { api.getVideoDetail(seedVideo.bvid, credential) }

                launch {
                    val profile = profileDeferred.await()
                    val videoCount = navnumDeferred.await()
                    val relation = relationDeferred.await()
                    val placeholder = authorCard.profile
                    authorCard = BiliAuthorCard(
                        profile = BiliUserProfile(
                            mid = authorMid,
                            name = profile?.name?.takeIf { it.isNotBlank() } ?: placeholder.name,
                            face = profile?.face.orEmpty(),
                            sign = profile?.sign.orEmpty(),
                            level = profile?.level ?: 0,
                            follower = profile?.follower ?: 0,
                            videoCount = videoCount,
                        ),
                        relation = relation,
                    )
                }

                val loadedDetail = detailDeferred.await() ?: error("无法加载视频详情")
                detail = loadedDetail
                commentCount = loadedDetail.replyCount

                val video = loadedDetail.video
                launch {
                    onlineCount = api.getVideoOnlineCount(
                        video.bvid,
                        video.aid,
                        video.cid,
                        credential,
                    )
                }
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Toast.makeText(context, error.message ?: "加载失败", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(commentSort) {
        if (pagerState.currentPage != VideoDetailTab.Comments.ordinal) return@LaunchedEffect
        commentsListState.scrollToItem(0)
    }

    LaunchedEffect(pagerState.currentPage, commentSort, detail?.video?.aid) {
        if (pagerState.currentPage != VideoDetailTab.Comments.ordinal) return@LaunchedEffect
        if (detail == null) return@LaunchedEffect
        reloadCommentsTab(refreshMeta = true)
    }

    val commentEntries by remember {
        derivedStateOf {
            buildCommentListEntries(
                comments = comments,
                loadedSubReplies = loadedSubReplies,
                expandedCommentRoots = expandedCommentRoots,
                subRepliesEnd = subRepliesEnd,
                subRepliesLoading = subRepliesLoading,
            )
        }
    }

    LaunchedEffect(
        commentsListState,
        pagerState.currentPage,
        commentsEnd,
        commentsLoading,
        commentsLoadingMore,
        commentsNextCursor,
        comments.size,
    ) {
        snapshotFlow {
            if (pagerState.currentPage != VideoDetailTab.Comments.ordinal) return@snapshotFlow false
            if (comments.isEmpty() || commentsEnd || commentsLoading || commentsLoadingMore) {
                return@snapshotFlow false
            }
            if (commentsNextCursor.isNullOrBlank()) return@snapshotFlow false
            val layoutInfo = commentsListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 1
        }.distinctUntilChanged().collect { shouldLoad ->
            if (shouldLoad) {
                loadComments(reset = false)
            }
        }
    }

    LaunchedEffect(
        commentsListState,
        pagerState.currentPage,
        commentEntries,
        commentsLoading,
        subRepliesLoading,
        subRepliesEnd,
        expandedCommentRoots,
    ) {
        snapshotFlow {
            if (pagerState.currentPage != VideoDetailTab.Comments.ordinal) return@snapshotFlow null
            if (commentsLoading) return@snapshotFlow null
            findAutoLoadSubReplyRootId(commentEntries, commentsListState.layoutInfo)
        }.distinctUntilChanged().collect { rootId ->
            if (rootId == null) return@collect
            if (rootId in subRepliesLoading || rootId in subRepliesEnd) return@collect
            loadSubReplies(rootId, reset = false)
        }
    }

    LaunchedEffect(playbackKey) {
        coordinator.requestInlinePlayback(playbackKey)
    }

    val currentDetail = detail
    val currentVideo = currentDetail?.video ?: seedVideo
    val currentStream = playStream
    val isVideoFullscreen = coordinator.fullscreenKey == playbackKey

    LightContentStatusBarEffect()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                Spacer(Modifier.height(VideoDetailPlayerTopPadding))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                ) {
                    if (currentStream != null && !isVideoFullscreen) {
                        BilibiliVideoSurface(
                            playbackKey = playbackKey,
                            stream = currentStream,
                            isFullscreen = false,
                            coordinator = coordinator,
                            backdrop = controlBackdrop,
                            onFullscreen = { coordinator.openFullscreen(playbackKey) },
                            onCloseFullscreen = { coordinator.closeFullscreen() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (currentStream != null) {
                        Box(Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
        }

        VideoDetailTabBar(
            scrollPosition = tabScrollPosition,
            commentCount = commentCount,
            commentSort = commentSort,
            onCommentSortToggle = {
                commentSort = if (commentSort == BiliCommentSort.Time) {
                    BiliCommentSort.Hot
                } else {
                    BiliCommentSort.Time
                }
            },
            onTabSelected = { tab ->
                val sameTab = tab == VideoDetailTab.entries[pagerState.currentPage]
                if (sameTab) {
                    scope.launch {
                        when (tab) {
                            VideoDetailTab.Intro -> introListState.animateScrollToItem(0)
                            VideoDetailTab.Comments -> commentsListState.animateScrollToItem(0)
                        }
                    }
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(VideoDetailTab.entries.indexOf(tab))
                    }
                }
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
        ) { page ->
            when (VideoDetailTab.entries[page]) {
                VideoDetailTab.Intro -> {
                    LazyColumn(
                        state = introListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item(key = "intro-author") {
                            VideoDetailAuthorSection(
                                authorCard = authorCard,
                                showFollowButton = myMid == null || myMid != currentVideo.authorMid,
                                followLoading = followLoading,
                                onAuthorClick = {
                                    val profile = authorCard.profile
                                    val authorMid = profile.mid.takeIf { it > 0L }
                                        ?: currentVideo.authorMid
                                    if (authorMid > 0L) {
                                        onAuthorClick(
                                            profile.copy(
                                                mid = authorMid,
                                                name = profile.name.ifBlank { currentVideo.authorName },
                                            ),
                                        )
                                    }
                                },
                                onFollowClick = {
                                    val card = authorCard
                                    val cred = credential ?: run {
                                        onLoginRequired()
                                        return@VideoDetailAuthorSection
                                    }
                                    scope.launch {
                                        followLoading = true
                                        val previousRelation = card.relation
                                        val targetFollow = !card.relation.following
                                        val referer = "https://www.bilibili.com/video/${currentVideo.bvid}"
                                        authorCard = card.copy(
                                            relation = card.relation.copy(following = targetFollow),
                                        )
                                        runCatching {
                                            api.modifyFollow(
                                                mid = card.profile.mid,
                                                follow = targetFollow,
                                                credential = cred,
                                                referer = referer,
                                            ).getOrThrow()
                                            val relation = api.getUserRelation(
                                                mid = card.profile.mid,
                                                credential = cred,
                                                referer = referer,
                                            )
                                            authorCard = authorCard.copy(relation = relation)
                                        }.onFailure {
                                            authorCard = card.copy(relation = previousRelation)
                                            Toast.makeText(
                                                context,
                                                it.message ?: "关注失败",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        followLoading = false
                                    }
                                },
                            )
                        }
                        item(key = "intro-title") {
                            Text(
                                text = currentVideo.title,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        item(key = "intro-stats") {
                            VideoDetailStatsSection(
                                viewCount = currentVideo.viewCount,
                                danmakuCount = currentVideo.danmakuCount,
                                publishTimeSeconds = currentDetail?.publishTimeSeconds ?: 0L,
                                onlineCount = onlineCount,
                            )
                        }
                        item(key = "intro-desc") {
                            Text(
                                text = currentVideo.description.ifBlank { "暂无简介" },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                VideoDetailTab.Comments -> {
                    PullToRefreshBox(
                            isRefreshing = commentsRefreshing,
                            onRefresh = {
                                scope.launch {
                                    commentsRefreshing = true
                                    runCatching {
                                        reloadCommentsTab(refreshMeta = true)
                                    }.onFailure {
                                        Toast.makeText(context, it.message ?: "刷新失败", Toast.LENGTH_SHORT).show()
                                    }
                                    commentsRefreshing = false
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                state = commentsListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                            ) {
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
                                        fontSize = CommentAuthorFontSize,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            items(commentEntries, key = { it.stableKey }) { entry ->
                                when (entry) {
                                    is CommentRootEntry -> {
                                        VideoCommentRow(
                                            comment = entry.comment,
                                            depth = 0,
                                            onAuthorClick = onAuthorClick,
                                        )
                                    }
                                    is CommentReplyEntry -> {
                                        VideoCommentRow(
                                            comment = entry.reply,
                                            depth = 1,
                                            onAuthorClick = onAuthorClick,
                                        )
                                    }
                                    is CommentReplyFooterEntry -> {
                                        CommentReplyFooterRow(
                                            remainingCount = entry.remainingCount,
                                            isLoading = entry.isLoading,
                                            isFirstExpand = entry.isFirstExpand,
                                            nestedContentStart = CommentRowOuterStart + CommentAvatarSize + 10.dp,
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
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                            if (!commentsEnd && !commentsLoading && !commentsLoadingMore && comments.isNotEmpty()) {
                                item(key = "comment-load-more") {
                                    CommentLoadMoreRow(
                                        onClick = { scope.launch { loadComments(reset = false) } },
                                    )
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
private fun VideoDetailAuthorSection(
    authorCard: BiliAuthorCard,
    showFollowButton: Boolean,
    followLoading: Boolean,
    onAuthorClick: () -> Unit,
    onFollowClick: () -> Unit,
) {
    val profile = authorCard.profile
    val relation = authorCard.relation
    val avatarPlaceholder = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val canOpenProfile = profile.mid > 0L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (profile.face.isNotBlank()) {
            RemoteImage(
                url = profile.face,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canOpenProfile, onClick = onAuthorClick),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(avatarPlaceholder)
                    .clickable(enabled = canOpenProfile, onClick = onAuthorClick),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = canOpenProfile, onClick = onAuthorClick),
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatAuthorMeta(profile),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (showFollowButton) {
            Spacer(Modifier.width(8.dp))
            BilibiliFollowButton(
                following = relation.following,
                followerMe = relation.followerMe,
                loading = followLoading,
                onClick = onFollowClick,
            )
        }
    }
}

@Composable
private fun VideoDetailStatsSection(
    viewCount: Long,
    danmakuCount: Long,
    publishTimeSeconds: Long,
    onlineCount: Long,
) {
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = metaColor,
            )
            Text(
                text = formatBiliCount(viewCount),
                style = MaterialTheme.typography.bodySmall,
                color = metaColor,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Subtitles,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = metaColor,
            )
            Text(
                text = formatBiliCount(danmakuCount),
                style = MaterialTheme.typography.bodySmall,
                color = metaColor,
            )
        }
        Text(
            text = formatBiliPublishTime(publishTimeSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = metaColor,
        )
        if (onlineCount > 0L) {
            Text(
                text = "${formatBiliCount(onlineCount)} 人在看",
                style = MaterialTheme.typography.bodySmall,
                color = metaColor,
            )
        }
    }
}

@Composable
private fun VideoCommentRow(
    comment: BiliCommentItem,
    depth: Int = 0,
    onAuthorClick: (BiliUserProfile) -> Unit = {},
) {
    val rowStart = CommentRowOuterStart + (depth * 24).dp
    val canOpenProfile = comment.authorMid > 0L
    val openProfile = {
        if (canOpenProfile) {
            onAuthorClick(
                BiliUserProfile(
                    mid = comment.authorMid,
                    name = comment.authorName,
                    face = comment.authorFace,
                    sign = "",
                    level = comment.level,
                ),
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = rowStart, end = 18.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RemoteImage(
            url = comment.authorFace,
            modifier = Modifier
                .size(CommentAvatarSize)
                .clip(CircleShape)
                .clickable(enabled = canOpenProfile, onClick = openProfile),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.clickable(enabled = canOpenProfile, onClick = openProfile),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = comment.authorName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = CommentAuthorFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (comment.level > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "LV${comment.level}",
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        color = BiliPink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(BiliPink.copy(alpha = 0.1f))
                            .padding(horizontal = 3.dp, vertical = 0.dp),
                    )
                }
            }
            if (shouldShowCommentText(comment.content, comment.pictures)) {
                BiliCommentText(
                    text = comment.content,
                    emoticons = comment.emoticons,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (comment.pictures.isNotEmpty()) {
                BiliCommentImageStrip(pictures = comment.pictures)
            }
            Text(
                text = buildList {
                    add(formatBiliCommentTime(comment.publishTimeSeconds))
                    comment.ipLocation?.let { add("来自$it") }
                    add("赞 ${formatBiliCount(comment.likeCount)}")
                }.joinToString("  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
            )
        }
    }
}

@Composable
private fun CommentLoadMoreRow(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "加载更多评论",
            fontSize = CommentAuthorFontSize,
            color = BiliPink,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

@Composable
private fun CommentReplyFooterRow(
    remainingCount: Long,
    isLoading: Boolean,
    isFirstExpand: Boolean,
    nestedContentStart: Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = nestedContentStart, end = 18.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
            )
            Text(
                text = "加载中...",
                fontSize = CommentAuthorFontSize,
                color = BiliPink,
            )
        } else {
            val actionLabel = if (isFirstExpand) {
                "共${formatBiliCount(remainingCount)}条回复"
            } else {
                "加载更多"
            }
            Text(
                text = actionLabel,
                fontSize = CommentAuthorFontSize,
                color = BiliPink,
                modifier = Modifier.clickable(onClick = onClick),
            )
        }
    }
}

private fun formatAuthorMeta(profile: BiliUserProfile): String = buildString {
    append(formatBiliCount(profile.follower))
    append("粉丝 ")
    append(formatBiliCount(profile.videoCount))
    append("视频")
}

private fun resolveCommentsEnd(
    page: BiliCommentPage,
    mergedCount: Int,
    previousCount: Int,
): Boolean {
    if (page.isEnd) return true
    if (page.nextCursor.isNullOrBlank()) return true
    if (page.comments.isEmpty()) return true
    if (mergedCount == previousCount) return true
    return false
}

private fun shouldShowCommentText(content: String, pictures: List<BiliCommentPicture>): Boolean {
    if (content.isBlank()) return false
    if (pictures.isEmpty()) return true
    val trimmed = content.trim()
    return trimmed != "[图片]" && trimmed != "图片评论"
}
