package com.example.bilibili.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.view.OrientationEventListener
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.bilibili.data.BiliAuthorCard
import com.example.bilibili.data.BiliAuthorRelation
import com.example.bilibili.data.BiliCommentItem
import com.example.bilibili.data.BiliCommentPage
import com.example.bilibili.data.BiliCommentPicture
import com.example.bilibili.data.BiliCommentSort
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliUserProfile
import com.example.bilibili.data.BiliVideoPage
import com.example.bilibili.data.BiliUgcSeason
import com.example.bilibili.data.BiliVideoDetail
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BiliVideoShot
import com.example.bilibili.data.BiliVideoRelation
import com.example.bilibili.data.BiliViewerImage
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.data.BilibiliEndpoints
import com.example.bilibili.data.BilibiliJsonParser
import com.example.bilibili.player.BilibiliVideoSurface
import com.example.bilibili.player.LightContentStatusBarEffect
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.player.VideoPlaybackMediaBridge
import com.example.bilibili.player.VideoPlaybackMetadata
import com.example.bilibili.player.resolveMediaEpisodeControls
import com.example.bilibili.player.resolveVideoEpisodePickerState
import com.example.bilibili.player.rememberVideoControlBackdrop
import com.example.bilibili.player.knownPortraitVideoHint
import com.example.bilibili.player.knownVideoAspectRatio
import com.example.bilibili.player.isPlayStreamCacheStale
import com.example.bilibili.player.resolveStoredProgressSeconds
import com.example.bilibili.player.saveResolvedProgress
import com.example.bilibili.player.VideoPlayerLoadingIndicator
import com.example.bilibili.player.videoPlaybackKey
import com.example.bilibili.ui.components.CommentAuthorHeaderRow
import com.example.bilibili.ui.components.BiliCommentImageStrip
import com.example.bilibili.ui.components.BiliCommentText
import com.example.bilibili.ui.components.BiliRichText
import com.example.bilibili.ui.components.BiliUserLevelIcon
import com.example.bilibili.ui.components.BiliVideoDanmakuCountIcon
import com.example.bilibili.ui.components.BiliVideoPlayCountIcon
import com.example.bilibili.ui.components.BilibiliFollowButton
import com.example.bilibili.ui.components.VideoCoinChoiceDialog
import com.example.bilibili.ui.components.VideoDetailActionBar
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.VideoDetailMoreMenu
import com.example.bilibili.ui.components.VideoDetailTab
import com.example.bilibili.ui.components.VideoDetailTabBar
import com.example.bilibili.ui.components.VideoDetailCollectionSheet
import com.example.bilibili.ui.components.VideoDetailMultiPartSection
import com.example.bilibili.ui.components.VideoDetailUgcSeasonSection
import com.example.bilibili.util.BiliLinkTarget
import com.example.bilibili.util.ExternalUrlOpener
import com.example.bilibili.util.BilibiliAppLauncher
import com.example.bilibili.ui.components.imageviewer.BiliFullscreenImageViewer
import com.example.bilibili.ui.format.formatBiliCommentTime
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.format.formatBiliPublishTime
import com.example.bilibili.ui.theme.BiliPink
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val VideoDetailPlayerTopPadding = 8.dp
private const val CommentPageSize = 20

private val CommentRowOuterStart = 18.dp
private val CommentAvatarSize = 34.dp
private val CommentAuthorFontSize = 13.sp

private data class CommentImageViewerRequest(
    val images: List<BiliViewerImage>,
    val initialIndex: Int,
    val sourceBoundsByIndex: Map<Int, Rect>,
)

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

private sealed interface VideoCollectionSheetState {
    data class MultiPart(val pages: List<BiliVideoPage>) : VideoCollectionSheetState
    data class UgcSeason(
        val season: BiliUgcSeason,
        val highlightSectionId: Long?,
    ) : VideoCollectionSheetState
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
    onSwitchVideoPart: (BiliVideoItem, BiliVideoPage, BiliPlayStream, Boolean) -> Unit = { _, _, _, _ -> },
    onUpdateVideoSeed: (BiliVideoItem, BiliPlayStream) -> Unit = { _, _ -> },
    onReplaceVideo: (BiliVideoItem, BiliPlayStream) -> Unit = { _, _ -> },
    episodeSwitchScope: CoroutineScope? = null,
    onOpenUgcEpisode: (BiliVideoItem) -> Unit = {},
    onOpenDescriptionVideo: (BiliVideoItem, Int) -> Unit = { video, _ -> onOpenUgcEpisode(video) },
    playbackActive: Boolean = true,
    onStreamSourceError: (BiliVideoItem) -> Unit = {},
    initialProgressSeconds: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val switchScope = episodeSwitchScope ?: scope
    val seedPlaybackId = seedVideo.playbackId()
    val seedStateKey = seedVideo.bvid.ifBlank { "av:${seedVideo.aid}" }.ifBlank { seedPlaybackId }
    val controlBackdrop = rememberVideoControlBackdrop()

    var preservedUgcSeason by remember(seedStateKey) { mutableStateOf<BiliUgcSeason?>(null) }
    var preservedPages by remember(seedStateKey) { mutableStateOf<List<BiliVideoPage>>(emptyList()) }

    var detail by remember(seedStateKey) { mutableStateOf<BiliVideoDetail?>(null) }
    var authorCard by remember(seedStateKey) { mutableStateOf(placeholderAuthorCard(seedVideo)) }
    var onlineCount by remember(seedStateKey) { mutableStateOf(0L) }
    var commentSort by remember(seedStateKey) { mutableStateOf(BiliCommentSort.Hot) }
    var comments by remember(seedStateKey) { mutableStateOf<List<BiliCommentItem>>(emptyList()) }
    var commentCount by remember(seedStateKey) { mutableStateOf(0L) }
    var commentsLoading by remember(seedStateKey) { mutableStateOf(false) }
    var commentsRefreshing by remember(seedStateKey) { mutableStateOf(false) }
    var commentsLoadingMore by remember(seedStateKey) { mutableStateOf(false) }
    var commentsNextCursor by remember(seedStateKey) { mutableStateOf<String?>(null) }
    var commentsEnd by remember(seedStateKey) { mutableStateOf(true) }
    var followLoading by remember(seedStateKey) { mutableStateOf(false) }
    var expandedCommentRoots by remember(seedStateKey) { mutableStateOf(setOf<Long>()) }
    var loadedSubReplies by remember(seedStateKey) { mutableStateOf(mapOf<Long, List<BiliCommentItem>>()) }
    var subRepliesLoading by remember(seedStateKey) { mutableStateOf(setOf<Long>()) }
    var subRepliesEnd by remember(seedStateKey) { mutableStateOf(setOf<Long>()) }
    var commentImageViewer by remember(seedStateKey) { mutableStateOf<CommentImageViewerRequest?>(null) }
    var videoRelation by remember(seedStateKey) { mutableStateOf(BiliVideoRelation()) }
    var videoActionLoading by remember(seedStateKey) { mutableStateOf(false) }
    var showCoinDialog by remember(seedStateKey) { mutableStateOf(false) }
    var coinMenuAnchor by remember(seedStateKey) { mutableStateOf(Rect.Zero) }
    var showMoreMenu by remember(seedStateKey) { mutableStateOf(false) }
    var moreMenuAnchor by remember(seedStateKey) { mutableStateOf(Rect.Zero) }
    var showCollectionSheet by remember(seedStateKey) { mutableStateOf(false) }
    var collectionSheetState by remember(seedStateKey) { mutableStateOf<VideoCollectionSheetState?>(null) }
    var collectionAnchor by remember(seedStateKey) { mutableStateOf(Rect.Zero) }
    var sheetUgcSeason by remember(seedStateKey) { mutableStateOf<BiliUgcSeason?>(null) }
    var activePart by remember(seedStateKey) { mutableStateOf<BiliVideoPage?>(null) }
    var activeEpid by remember(seedStateKey) { mutableStateOf(seedVideo.pgcEpid()) }
    var overridePlayStream by remember(seedStateKey) { mutableStateOf<BiliPlayStream?>(null) }

    val pagerState = rememberPagerState(initialPage = 0) { VideoDetailTab.entries.size }
    val tabScrollPosition by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val introListState = rememberLazyListState()
    val commentsListState = rememberLazyListState()
    val commentsLoadMutex = remember(seedStateKey) { Mutex() }

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
                    detail = api.hydrateVideoUgcSeason(loadedDetail, credential)
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
                api.getVideoCommentReplies(
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

    LaunchedEffect(showCollectionSheet, collectionSheetState, credential?.dedeUserId) {
        val state = collectionSheetState
        if (!showCollectionSheet || state !is VideoCollectionSheetState.UgcSeason) {
            if (!showCollectionSheet) {
                sheetUgcSeason = null
            }
            return@LaunchedEffect
        }
        val season = state.season
        val authorMid = detail?.video?.authorMid ?: seedVideo.authorMid
        sheetUgcSeason = if (season.needsHydration()) {
            api.getUgcSeasonArchives(
                mid = season.mid.takeIf { it > 0L } ?: authorMid,
                seasonId = season.id,
                credential = credential,
            )?.let { hydrated ->
                BilibiliJsonParser.mergeUgcSeasonArchives(season, hydrated)
            } ?: season
        } else {
            season
        }
    }

    suspend fun resolvePagePlayStream(
        cid: Long,
        pageEpid: Long = 0L,
        bvid: String = detail?.video?.bvid?.takeIf { it.isNotBlank() } ?: seedVideo.bvid,
    ): BiliPlayStream? {
        val epid = pageEpid.takeIf { it > 0L } ?: activeEpid
        val video = detail?.video ?: seedVideo
        val targetCid = cid.takeIf { it > 0L } ?: video.cid.takeIf { it > 0L } ?: 0L
        val referer = if (epid > 0L) {
            "https://www.bilibili.com/bangumi/play/ep$epid"
        } else {
            video.playbackReferer.ifBlank {
                if (bvid.isNotBlank()) "https://www.bilibili.com/video/$bvid" else BilibiliEndpoints.HOME
            }
        }
        if (seedVideo.isPgcPlayback() || epid > 0L) {
            if (epid > 0L) {
                api.resolvePgcPlayUrl(epid, targetCid, credential, referer)?.let { stream ->
                    return stream.copy(
                        aid = video.aid.takeIf { it > 0L } ?: stream.aid,
                        cid = targetCid.takeIf { it > 0L } ?: stream.cid,
                    )
                }
            }
            if (bvid.isNotBlank() && !bvid.startsWith("pgc") && targetCid > 0L) {
                api.getPlayUrl(
                    bvid = bvid,
                    cid = targetCid,
                    credential = credential,
                    aid = video.aid.takeIf { it > 0L } ?: 0L,
                    referer = referer,
                )?.let { stream ->
                    return stream.copy(
                        aid = video.aid.takeIf { it > 0L } ?: stream.aid,
                        cid = targetCid,
                    )
                }
            }
            return null
        }
        if (bvid.isBlank() || targetCid <= 0L) return null
        return api.getPlayUrl(
            bvid = bvid,
            cid = targetCid,
            credential = credential,
            aid = video.aid.takeIf { it > 0L } ?: 0L,
            referer = referer,
        )?.copy(
            aid = video.aid.takeIf { it > 0L } ?: 0L,
            cid = targetCid,
        )
    }

    LaunchedEffect(seedStateKey, activeEpid, credential?.dedeUserId) {
        authorCard = placeholderAuthorCard(seedVideo)
        runCatching {
            coroutineScope {
                if (seedVideo.isPgcPlayback() && activeEpid > 0L) {
                    val loadedDetail = api.getPgcVideoDetail(activeEpid, credential)
                        ?: error("无法加载番剧详情")
                    val authorMid = loadedDetail.video.authorMid
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

                    detail = loadedDetail
                    commentCount = loadedDetail.replyCount
                    videoRelation = BilibiliJsonParser.mergeVideoRelations(
                        loadedDetail.userRelation,
                        api.getVideoArchiveRelation(
                            bvid = loadedDetail.video.bvid,
                            aid = loadedDetail.video.aid,
                            credential = credential,
                        ),
                    )

                    val video = loadedDetail.video
                    launch {
                        onlineCount = api.getVideoOnlineCount(
                            video.bvid,
                            video.aid,
                            video.cid,
                            credential,
                        )
                    }
                } else {
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
                    val resolvedSeed = api.resolveVideoForPlayback(seedVideo, credential)
                    val detailBvid = resolvedSeed.bvid.ifBlank { seedVideo.bvid }
                    val detailDeferred = async { api.getVideoDetail(detailBvid, credential) }

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
                    var hydratedDetail = api.hydrateVideoUgcSeason(loadedDetail, credential)
                    if (resolvedSeed.cid > 0L || resolvedSeed.bvid != loadedDetail.video.bvid) {
                        val targetCid = resolvedSeed.cid.takeIf { it > 0L } ?: hydratedDetail.video.cid
                        hydratedDetail = hydratedDetail.copy(
                            video = hydratedDetail.video.copy(
                                bvid = resolvedSeed.bvid.ifBlank { hydratedDetail.video.bvid },
                                cid = targetCid,
                                title = resolvedSeed.title.ifBlank { hydratedDetail.video.title },
                                coverUrl = resolvedSeed.coverUrl.ifBlank { hydratedDetail.video.coverUrl },
                                durationSeconds = resolvedSeed.durationSeconds.takeIf { it > 0 }
                                    ?: hydratedDetail.video.durationSeconds,
                            ),
                        )
                        activePart = hydratedDetail.pages.find { it.cid == targetCid }
                            ?: hydratedDetail.ugcSeason?.toVideoPages()?.find { it.cid == targetCid }
                    }
                    detail = hydratedDetail
                    commentCount = loadedDetail.replyCount
                    videoRelation = BilibiliJsonParser.mergeVideoRelations(
                        loadedDetail.userRelation,
                        api.getVideoArchiveRelation(
                            bvid = loadedDetail.video.bvid,
                            aid = loadedDetail.video.aid,
                            credential = credential,
                        ),
                    )

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

    var loadedCommentsKey by remember(seedStateKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(pagerState.currentPage, detail?.video?.aid, commentSort) {
        if (pagerState.currentPage != VideoDetailTab.Comments.ordinal) return@LaunchedEffect
        val aid = detail?.video?.aid ?: return@LaunchedEffect
        if (aid <= 0L) return@LaunchedEffect
        val key = "$aid:$commentSort"
        if (loadedCommentsKey == key) return@LaunchedEffect
        reloadCommentsTab(refreshMeta = loadedCommentsKey == null)
        loadedCommentsKey = key
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

    val currentDetail = detail
    val effectiveUgcSeason = currentDetail?.ugcSeason?.takeIf { it.shouldDisplay }
        ?: preservedUgcSeason?.takeIf { it.shouldDisplay }
    val ugcSeasonPages = effectiveUgcSeason?.toVideoPages().orEmpty()
    val effectivePages = currentDetail?.pages?.takeIf { it.size > 1 }
        ?: preservedPages.takeIf { it.size > 1 }
        ?: ugcSeasonPages.takeIf { it.size > 1 }
        ?: emptyList()
    val currentVideo = currentDetail?.video ?: seedVideo
    val currentStream = overridePlayStream ?: playStream
    val currentCid = currentStream?.cid?.takeIf { it > 0L } ?: activePart?.cid ?: seedVideo.cid
    val currentContentPlaybackId = when {
        activeEpid > 0L -> "pgc:$activeEpid"
        currentVideo.bvid.isNotBlank() && currentCid > 0L -> currentVideo.copy(cid = currentCid, epid = 0L).playbackId()
        else -> seedPlaybackId
    }
    // The detail screen owns one playback session even when its collection changes episode.
    // Keeping this key stable lets the existing ExoPlayer and MediaSession swap media sources
    // without Android removing and recreating the playback notification between episodes.
    val playbackKey = remember(seedPlaybackId) {
        videoPlaybackKey(seedPlaybackId, ownerId = "detail")
    }
    val contentPlaybackKey = remember(currentContentPlaybackId) {
        videoPlaybackKey(currentContentPlaybackId, ownerId = "detail")
    }

    LaunchedEffect(seedStateKey, initialProgressSeconds, credential?.dedeUserId) {
        if (initialProgressSeconds > 0) {
            val resolvedVideo = api.resolveVideoForPlayback(seedVideo, credential)
            saveResolvedProgress(
                coordinator = coordinator,
                playbackId = resolvedVideo.playbackId(),
                progressSeconds = initialProgressSeconds,
            )
            return@LaunchedEffect
        }
        val resolvedVideo = api.resolveVideoForPlayback(seedVideo, credential)
        val serverProgress = api.getVideoWatchProgress(resolvedVideo, credential) ?: 0
        val resolvedProgress = resolveStoredProgressSeconds(
            coordinator = coordinator,
            playbackId = resolvedVideo.playbackId(),
            serverProgressSeconds = serverProgress,
        )
        saveResolvedProgress(
            coordinator = coordinator,
            playbackId = resolvedVideo.playbackId(),
            progressSeconds = resolvedProgress,
        )
    }

    val displayTitle = activePart?.title?.takeIf { it.isNotBlank() }
        ?: effectivePages.find {
            it.cid == currentCid &&
                (it.bvid.isBlank() || it.bvid == currentVideo.bvid)
        }?.title?.takeIf { it.isNotBlank() }
        ?: currentVideo.title
    val displayDurationSeconds = activePart?.durationSeconds?.takeIf { it > 0 }
        ?: currentVideo.durationSeconds

    val currentEpisodeCover = effectiveUgcSeason?.sections
        ?.asSequence()
        ?.flatMap { it.episodes.asSequence() }
        ?.firstOrNull { episode ->
            (episode.cid > 0L && episode.cid == currentCid) ||
                (episode.bvid.isNotBlank() && episode.bvid == currentVideo.bvid)
        }
        ?.coverUrl
        ?.takeIf { it.isNotBlank() }

    val historyVideo = currentVideo.copy(
        cid = currentCid,
        aid = currentStream?.aid?.takeIf { it > 0L } ?: currentVideo.aid,
        title = displayTitle,
        coverUrl = currentEpisodeCover ?: currentVideo.coverUrl,
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentStreamState = rememberUpdatedState(currentStream)
    val historyVideoState = rememberUpdatedState(historyVideo)
    val streamRefreshState = rememberUpdatedState(onStreamSourceError)

    DisposableEffect(lifecycleOwner, playbackActive) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && playbackActive) {
                val resumedStream = currentStreamState.value
                if (resumedStream == null || resumedStream.isPlayStreamCacheStale()) {
                    streamRefreshState.value(historyVideoState.value)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentDetail?.ugcSeason, currentDetail?.pages) {
        currentDetail?.ugcSeason?.takeIf { it.shouldDisplay }?.let { preservedUgcSeason = it }
        val pages = currentDetail?.pages?.takeIf { it.size > 1 }
            ?: currentDetail?.ugcSeason?.toVideoPages()?.takeIf { it.size > 1 }
        pages?.let { preservedPages = it }
    }

    val playbackTargetCid = activePart?.cid?.takeIf { it > 0L }
        ?: playStream?.cid?.takeIf { it > 0L }
        ?: seedVideo.cid.takeIf { it > 0L }
        ?: currentCid.takeIf { it > 0L }
    val streamMatchesTarget = when {
        overridePlayStream != null -> true
        playbackTargetCid == null -> currentStream != null
        currentStream == null -> false
        else -> {
            val streamCid = currentStream.cid.takeIf { it > 0L }
            streamCid == null || streamCid == playbackTargetCid
        }
    }

    var playerSurfaceLoading by remember(playbackKey) {
        val handoffKey = coordinator.handoffPlaybackKeyForVideo(currentVideo)
        mutableStateOf(
            !(coordinator.hasHandoffPlayer(playbackKey) || handoffKey != null),
        )
    }
    val playerStreamReady = currentStream != null && streamMatchesTarget
    val playbackActiveState = rememberUpdatedState(playbackActive)
    val hasHandoffPlayer = coordinator.hasHandoffPlayer(playbackKey) ||
        coordinator.handoffPlaybackKeyForVideo(currentVideo) != null

    LaunchedEffect(playbackKey, playbackActive, playerStreamReady, hasHandoffPlayer, coordinator.fullscreenKey) {
        when {
            coordinator.fullscreenKey == playbackKey -> Unit
            playbackActive && playerStreamReady ->
                coordinator.requestInlinePlayback(playbackKey)
            !playbackActive ->
                coordinator.pauseForOverlay()
        }
    }

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
                onOpenDescriptionVideo(
                    BiliVideoItem(
                        bvid = target.bvid,
                        aid = target.aid,
                        title = "",
                        coverUrl = "",
                        authorName = currentVideo.authorName,
                        authorMid = currentVideo.authorMid,
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

    var videoShot by remember(currentVideo.bvid, currentCid) { mutableStateOf<BiliVideoShot?>(null) }
    LaunchedEffect(currentVideo.bvid, currentVideo.aid, currentCid, credential?.dedeUserId) {
        val cid = currentCid.takeIf { it > 0L } ?: return@LaunchedEffect
        val shot = api.getVideoShot(
            bvid = currentVideo.bvid,
            aid = currentVideo.aid,
            cid = cid,
            credential = credential,
        )
        videoShot = shot
        coordinator.cacheVideoShot(cid, shot)
    }

    LaunchedEffect(
        seedVideo.bvid,
        seedVideo.cid,
        playStream?.cid,
        overridePlayStream?.cid,
        effectivePages,
        currentDetail?.video?.cid,
        credential?.dedeUserId,
    ) {
        if (overridePlayStream?.cid?.takeIf { it > 0L } != null) return@LaunchedEffect

        val targetCid = seedVideo.cid.takeIf { it > 0L }
            ?: currentDetail?.video?.cid?.takeIf { it > 0L }
            ?: return@LaunchedEffect
        playStream?.cid?.takeIf { it > 0L }?.let { cachedCid ->
            if (cachedCid == targetCid) return@LaunchedEffect
        }

        val pageBvid = effectivePages.find { it.cid == targetCid }?.bvid?.takeIf { it.isNotBlank() }
            ?: seedVideo.bvid
        runCatching {
            val stream = resolvePagePlayStream(targetCid, bvid = pageBvid)
                ?: return@LaunchedEffect
            overridePlayStream = stream.copy(
                aid = seedVideo.aid.takeIf { it > 0L } ?: stream.aid,
                cid = targetCid,
            )
            currentDetail?.pages?.find { it.cid == targetCid }?.let { activePart = it }
        }
    }

    LaunchedEffect(
        effectiveUgcSeason,
        seedVideo.cid,
        seedVideo.bvid,
        playStream?.cid,
        overridePlayStream?.cid,
    ) {
        if (overridePlayStream?.cid?.takeIf { it > 0L } != null) return@LaunchedEffect

        val targetCid = seedVideo.cid.takeIf { it > 0L } ?: return@LaunchedEffect
        val season = effectiveUgcSeason ?: return@LaunchedEffect
        val currentStreamCid = overridePlayStream?.cid?.takeIf { it > 0L }
            ?: playStream?.cid?.takeIf { it > 0L }
        if (currentStreamCid == targetCid) return@LaunchedEffect

        val episode = season.sections.asSequence()
            .flatMap { it.episodes.asSequence() }
            .firstOrNull { episode ->
                episode.cid == targetCid ||
                    (episode.bvid.isNotBlank() && episode.bvid == seedVideo.bvid)
            }
            ?: return@LaunchedEffect

        val episodeBvid = episode.bvid.takeIf { it.isNotBlank() } ?: seedVideo.bvid
        runCatching {
            val stream = resolvePagePlayStream(targetCid, bvid = episodeBvid) ?: return@LaunchedEffect
            overridePlayStream = stream.copy(
                aid = seedVideo.aid.takeIf { it > 0L } ?: stream.aid,
                cid = targetCid,
            )
            activePart = BiliVideoPage(
                page = 0,
                cid = targetCid,
                title = episode.title,
                durationSeconds = episode.durationSeconds,
                bvid = episodeBvid,
            )
        }
    }

    LaunchedEffect(
        effectivePages,
        playStream?.cid,
        seedVideo.cid,
        seedVideo.bvid,
        seedVideo.pgcEpid(),
        overridePlayStream?.cid,
        activePart?.cid,
        currentVideo.bvid,
    ) {
        val pages = effectivePages
        if (pages.isEmpty()) return@LaunchedEffect

        val activeBvid = currentVideo.bvid
        val seedBvid = seedVideo.bvid
        val playingCid = overridePlayStream?.cid?.takeIf { it > 0L }
            ?: playStream?.cid?.takeIf { it > 0L }
            ?: activePart?.cid?.takeIf { it > 0L }
        val seedEpid = seedVideo.pgcEpid().takeIf { it > 0L }
        val seedCid = seedVideo.cid.takeIf { it > 0L }
        fun pageMatches(page: BiliVideoPage): Boolean {
            if (seedBvid.isNotBlank() && page.bvid.isNotBlank() && page.bvid == seedBvid) {
                return true
            }
            if (page.bvid.isNotBlank() && activeBvid.isNotBlank() && page.bvid != activeBvid) {
                return false
            }
            return true
        }
        val page = when {
            playingCid != null -> pages.find { it.cid == playingCid && pageMatches(it) }
            seedEpid != null -> pages.find { it.epid == seedEpid && pageMatches(it) }
            seedCid != null -> pages.find { it.cid == seedCid && pageMatches(it) }
            else -> null
        } ?: return@LaunchedEffect

        if (page.epid > 0L && page.epid != activeEpid) {
            activeEpid = page.epid
        }
        if (activePart?.cid != page.cid) {
            activePart = page
        }

        if (overridePlayStream?.cid == page.cid || playStream?.cid == page.cid) {
            return@LaunchedEffect
        }
        if (overridePlayStream != null) return@LaunchedEffect

        runCatching {
            val streamBvid = page.bvid.takeIf { it.isNotBlank() } ?: seedVideo.bvid
            val stream = resolvePagePlayStream(page.cid, page.epid, streamBvid)
                ?: return@LaunchedEffect
            overridePlayStream = stream.copy(aid = seedVideo.aid, cid = page.cid)
            onlineCount = api.getVideoOnlineCount(
                bvid = seedVideo.bvid,
                aid = seedVideo.aid,
                cid = page.cid,
                credential = credential,
            )
        }
    }

    fun pushNotificationMetadata(video: BiliVideoItem) {
        VideoPlaybackMediaBridge.pushEpisodeMetadata(
            playbackKey,
            VideoPlaybackMetadata.fromVideo(video),
        )
    }

    fun resetCommentState() {
        loadedCommentsKey = null
        comments = emptyList()
        commentsEnd = false
        expandedCommentRoots = emptySet()
        loadedSubReplies = emptyMap()
        subRepliesLoading = emptySet()
        subRepliesEnd = emptySet()
    }

    fun switchToPart(page: BiliVideoPage) {
        showCollectionSheet = false
        val samePart = page.cid == currentCid &&
            (page.bvid.isBlank() || page.bvid == currentVideo.bvid) &&
            (page.epid <= 0L || page.epid == activeEpid)
        if (samePart) return
        switchScope.launch {
            runCatching {
                coordinator.releaseHandoffPlayer()
                if (page.epid > 0L && page.epid != activeEpid) {
                    val loadedDetail = api.getPgcVideoDetail(page.epid, credential)
                        ?: error("无法加载番剧详情")
                    val targetCid = page.cid.takeIf { it > 0L } ?: loadedDetail.video.cid
                    val stream = resolvePagePlayStream(targetCid, page.epid, page.bvid.ifBlank { loadedDetail.video.bvid })
                        ?: error("无法获取播放地址")
                    val resolvedStream = stream.copy(
                        aid = loadedDetail.video.aid.takeIf { it > 0L } ?: stream.aid,
                        cid = targetCid,
                    )
                    val targetVideo = loadedDetail.video.copy(
                        cid = targetCid,
                        epid = page.epid,
                        title = page.title.ifBlank { loadedDetail.video.title },
                        durationSeconds = page.durationSeconds.takeIf { it > 0 }
                            ?: loadedDetail.video.durationSeconds,
                    )
                    // Publish the new episode identity and its stream together. If the title is
                    // changed before the stream lookup finishes, the player can bind the old
                    // source to the new episode identity and never perform the real source swap.
                    detail = loadedDetail.copy(video = targetVideo)
                    resetCommentState()
                    activeEpid = page.epid
                    activePart = page
                    overridePlayStream = resolvedStream
                    coordinator.updateFullscreenMedia(playbackKey, targetVideo, resolvedStream)
                    onSwitchVideoPart(targetVideo, page, resolvedStream, true)
                    pushNotificationMetadata(targetVideo)
                    return@launch
                }

                if (page.bvid.isNotBlank() && page.bvid != currentVideo.bvid) {
                    val loadedDetail = api.hydrateVideoUgcSeason(
                        api.getVideoDetail(page.bvid, credential) ?: error("无法加载视频详情"),
                        credential,
                    )
                    val targetCid = page.cid.takeIf { it > 0L } ?: loadedDetail.video.cid
                    val stream = api.getPlayUrl(
                        bvid = page.bvid,
                        cid = targetCid,
                        credential = credential,
                        aid = loadedDetail.video.aid,
                        referer = "https://www.bilibili.com/video/${page.bvid}",
                    )
                        ?: error("无法获取播放地址")
                    val resolvedStream = stream.copy(
                        aid = loadedDetail.video.aid.takeIf { it > 0L } ?: stream.aid,
                        cid = targetCid,
                    )
                    val episodeCover = loadedDetail.ugcSeason?.sections
                        ?.asSequence()
                        ?.flatMap { it.episodes.asSequence() }
                        ?.firstOrNull { episode ->
                            episode.bvid == page.bvid ||
                                (episode.cid > 0L && episode.cid == targetCid)
                        }
                        ?.coverUrl
                        ?.takeIf { it.isNotBlank() }
                    val targetVideo = loadedDetail.video.copy(
                        bvid = page.bvid,
                        cid = targetCid,
                        title = page.title.ifBlank { loadedDetail.video.title },
                        coverUrl = episodeCover ?: loadedDetail.video.coverUrl,
                        durationSeconds = page.durationSeconds.takeIf { it > 0 }
                            ?: loadedDetail.video.durationSeconds,
                    )
                    detail = loadedDetail.copy(video = targetVideo)
                    preservedPages = loadedDetail.pages.takeIf { it.size > 1 }.orEmpty()
                    preservedUgcSeason = loadedDetail.ugcSeason?.takeIf { it.shouldDisplay }
                    resetCommentState()
                    activePart = page
                    overridePlayStream = resolvedStream
                    coordinator.updateFullscreenMedia(playbackKey, targetVideo, resolvedStream)
                    onSwitchVideoPart(targetVideo, page, resolvedStream, true)
                    pushNotificationMetadata(targetVideo)
                    return@launch
                }

                val playbackVideo = detail?.video ?: seedVideo
                val streamBvid = page.bvid.ifBlank { playbackVideo.bvid }
                val targetCid = page.cid.takeIf { it > 0L } ?: error("无法确定分P")
                val stream = resolvePagePlayStream(targetCid, page.epid, streamBvid)
                    ?: error("无法获取播放地址")
                val resolvedStream = stream.copy(
                    aid = playbackVideo.aid.takeIf { it > 0L } ?: stream.aid,
                    cid = targetCid,
                )
                val targetVideo = playbackVideo.copy(
                    cid = targetCid,
                    bvid = streamBvid,
                    epid = page.epid.takeIf { it > 0L } ?: playbackVideo.epid,
                    title = page.title.ifBlank { playbackVideo.title },
                    durationSeconds = page.durationSeconds.takeIf { it > 0 }
                        ?: playbackVideo.durationSeconds,
                )
                detail = detail?.copy(video = targetVideo)
                activePart = page
                overridePlayStream = resolvedStream
                coordinator.updateFullscreenMedia(playbackKey, targetVideo, resolvedStream)
                onSwitchVideoPart(targetVideo, page, resolvedStream, true)
                pushNotificationMetadata(targetVideo)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                Toast.makeText(context, error.message ?: "切换分P失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun switchToUgcEpisode(episode: BiliVideoItem) {
        switchToPart(
            BiliVideoPage(
                page = 0,
                cid = episode.cid,
                title = episode.title,
                durationSeconds = episode.durationSeconds,
                bvid = episode.bvid,
            ),
        )
    }

    SideEffect {
        if (!playbackActive) return@SideEffect
        VideoPlaybackMediaBridge.setPlaybackMetadataProvider(playbackKey) {
            VideoPlaybackMetadata.fromVideo(historyVideo)
        }
        VideoPlaybackMediaBridge.setEpisodeControlsProvider(playbackKey) {
            resolveMediaEpisodeControls(
                pages = effectivePages,
                ugcSeason = effectiveUgcSeason,
                currentCid = currentCid,
                currentBvid = currentVideo.bvid,
                activePartPage = activePart?.page,
                onSwitchPart = ::switchToPart,
                onSwitchEpisode = { episode ->
                    switchToUgcEpisode(
                        episode.toVideoItem(
                            authorName = currentVideo.authorName,
                            authorMid = currentVideo.authorMid,
                        ),
                    )
                },
            )
        }
        coordinator.updateEpisodePicker(
            playbackKey,
            resolveVideoEpisodePickerState(
                pages = effectivePages,
                ugcSeason = effectiveUgcSeason,
                currentCid = currentCid,
                currentBvid = currentVideo.bvid,
                activePartPage = activePart?.page,
                onSwitchPart = ::switchToPart,
                onSwitchEpisode = { episode ->
                    switchToUgcEpisode(
                        episode.toVideoItem(
                            authorName = currentVideo.authorName,
                            authorMid = currentVideo.authorMid,
                        ),
                    )
                },
            ),
        )
        VideoPlaybackMediaBridge.refreshEpisodeControls(playbackKey)
        VideoPlaybackMediaBridge.refreshPlaybackMetadata(playbackKey)
    }

    DisposableEffect(playbackKey) {
        onDispose {
            VideoPlaybackMediaBridge.setEpisodeControlsProvider(playbackKey, null)
            VideoPlaybackMediaBridge.setPlaybackMetadataProvider(playbackKey, null)
            coordinator.updateEpisodePicker(playbackKey, null)
        }
    }

    val isVideoFullscreen = coordinator.fullscreenKey == playbackKey
    val showPlayerLoadingOverlay = !isVideoFullscreen &&
        !hasHandoffPlayer &&
        (!playerStreamReady || playerSurfaceLoading)
    val configuration = LocalConfiguration.current
    val hazeState = rememberHazeState()
    val collectionMenuBackdrop = rememberLayerBackdrop()
    var autoFullscreenByRotation by remember(playbackKey) { mutableStateOf(false) }
    var suppressAutoFullscreenUntilPortrait by remember(playbackKey) { mutableStateOf(false) }
    var closeAutoFullscreenAfterPortrait by remember(playbackKey) { mutableStateOf(false) }

    LightContentStatusBarEffect()

    LaunchedEffect(
        configuration.orientation,
        isVideoFullscreen,
        currentStream,
        streamMatchesTarget,
        currentVideo.isPortraitVideo,
        suppressAutoFullscreenUntilPortrait,
        closeAutoFullscreenAfterPortrait,
        playbackKey,
    ) {
        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            suppressAutoFullscreenUntilPortrait = false
        }
        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
            isVideoFullscreen &&
            autoFullscreenByRotation &&
            closeAutoFullscreenAfterPortrait
        ) {
            closeAutoFullscreenAfterPortrait = false
            autoFullscreenByRotation = false
            coordinator.closeFullscreen()
            return@LaunchedEffect
        }
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            !isVideoFullscreen &&
            currentStream != null &&
            streamMatchesTarget &&
            !currentVideo.isPortraitVideo &&
            !suppressAutoFullscreenUntilPortrait
        ) {
            autoFullscreenByRotation = true
            coordinator.openFullscreen(
                playbackKey,
                portraitVideo = knownPortraitVideoHint(
                    currentVideo.videoWidth,
                    currentVideo.videoHeight,
                ) ?: false,
                video = currentVideo,
                stream = currentStream,
            )
        } else if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
            isVideoFullscreen &&
            autoFullscreenByRotation
        ) {
            autoFullscreenByRotation = false
            suppressAutoFullscreenUntilPortrait = true
            closeAutoFullscreenAfterPortrait = false
            coordinator.closeFullscreen()
        }
    }

    DisposableEffect(autoFullscreenByRotation, isVideoFullscreen, playbackKey, context) {
        if (!autoFullscreenByRotation || !isVideoFullscreen) {
            return@DisposableEffect onDispose {}
        }
        val orientationListener = object : OrientationEventListener(context.applicationContext) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val uprightPortrait = orientation <= 25 || orientation >= 335 ||
                    orientation in 155..205
                if (uprightPortrait && coordinator.fullscreenKey == playbackKey) {
                    closeAutoFullscreenAfterPortrait = true
                    suppressAutoFullscreenUntilPortrait = true
                    coordinator.releaseFullscreenOrientationLock(playbackKey)
                }
            }
        }
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
        onDispose {
            orientationListener.disable()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
            .layerBackdrop(collectionMenuBackdrop),
    ) {
    Column(
        modifier = Modifier.fillMaxSize(),
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
                        .background(Color.Black)
                        .clipToBounds(),
                ) {
                    if (playerStreamReady && !isVideoFullscreen) {
                        val readyStream = currentStream ?: return@Box
                        key(playbackKey) {
                        BilibiliVideoSurface(
                            playbackKey = playbackKey,
                            contentPlaybackKey = contentPlaybackKey,
                            stream = readyStream,
                            isFullscreen = false,
                            coordinator = coordinator,
                            backdrop = controlBackdrop,
                            onFullscreen = {
                                autoFullscreenByRotation = false
                                closeAutoFullscreenAfterPortrait = false
                                suppressAutoFullscreenUntilPortrait = false
                                coordinator.openFullscreen(
                                    playbackKey,
                                    portraitVideo = knownPortraitVideoHint(
                                        historyVideo.videoWidth,
                                        historyVideo.videoHeight,
                                    ),
                                    video = historyVideo,
                                    stream = readyStream,
                                )
                            },
                            onCloseFullscreen = {
                                autoFullscreenByRotation = false
                                closeAutoFullscreenAfterPortrait = false
                                suppressAutoFullscreenUntilPortrait = false
                                coordinator.closeFullscreen()
                            },
                            modifier = Modifier.fillMaxSize(),
                            danmakuEnabled = true,
                            danmakuCid = readyStream.cid.takeIf { it > 0L }
                                ?: currentVideo.cid,
                            loadDanmaku = { cid ->
                                api.getDanmakuList(
                                    cid = cid,
                                    durationSeconds = displayDurationSeconds,
                                    credential = credential,
                                    referer = "https://www.bilibili.com/video/${currentVideo.bvid}",
                                )
                            },
                            playbackEnabled = playbackActive,
                            portraitVideo = currentVideo.isPortraitVideo,
                            videoShot = videoShot,
                            scrubPreviewAspectRatio = knownVideoAspectRatio(
                                currentVideo.videoWidth,
                                currentVideo.videoHeight,
                            ),
                            playbackMetadata = VideoPlaybackMetadata.fromVideo(historyVideo),
                            historyVideo = historyVideo,
                            onStreamSourceError = {
                                onStreamSourceError(historyVideo)
                            },
                            showLoadingIndicator = false,
                            autoPlayWhenReady = true,
                            onLoadingStateChange = { loading ->
                                if (playbackActiveState.value) {
                                    playerSurfaceLoading = loading
                                }
                            },
                        )
                        }
                    }
                    if (showPlayerLoadingOverlay) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            VideoPlayerLoadingIndicator()
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
            onMoreClick = { anchorBounds ->
                moreMenuAnchor = anchorBounds
                showMoreMenu = true
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
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
                                text = displayTitle,
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
                            BiliRichText(
                                text = currentVideo.description.ifBlank { "暂无简介" },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                onLinkClick = { target ->
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
                                            onOpenDescriptionVideo(
                                                BiliVideoItem(
                                                    bvid = target.bvid,
                                                    aid = target.aid,
                                                    title = "",
                                                    coverUrl = "",
                                                    authorName = currentVideo.authorName,
                                                    authorMid = currentVideo.authorMid,
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
                                },
                            )
                        }
                        item(key = "intro-actions") {
                            VideoDetailActionBar(
                                likeCount = currentVideo.likeCount,
                                coinCount = currentDetail?.coinCount ?: 0L,
                                favoriteCount = currentDetail?.favoriteCount ?: 0L,
                                shareCount = currentDetail?.shareCount ?: 0L,
                                liked = videoRelation.liked,
                                coined = videoRelation.coinCount > 0,
                                favorited = videoRelation.favorited,
                                enabled = !videoActionLoading,
                                onLikeClick = {
                                    val cred = credential ?: run {
                                        onLoginRequired()
                                        return@VideoDetailActionBar
                                    }
                                    if (videoActionLoading) return@VideoDetailActionBar
                                    scope.launch {
                                        videoActionLoading = true
                                        val targetLike = !videoRelation.liked
                                        runCatching {
                                            api.likeVideo(
                                                bvid = currentVideo.bvid,
                                                aid = currentVideo.aid,
                                                like = targetLike,
                                                credential = cred,
                                            ).getOrThrow()
                                            videoRelation = videoRelation.copy(liked = targetLike)
                                            detail = detail?.let { loaded ->
                                                val delta = if (targetLike) 1L else -1L
                                                loaded.copy(
                                                    video = loaded.video.copy(
                                                        likeCount = (loaded.video.likeCount + delta)
                                                            .coerceAtLeast(0L),
                                                    ),
                                                )
                                            }
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "点赞失败",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        videoActionLoading = false
                                    }
                                },
                                onTripleClick = {
                                    val cred = credential ?: run {
                                        onLoginRequired()
                                        return@VideoDetailActionBar
                                    }
                                    if (videoActionLoading) return@VideoDetailActionBar
                                    scope.launch {
                                        videoActionLoading = true
                                        runCatching {
                                            val result = api.tripleVideo(
                                                bvid = currentVideo.bvid,
                                                aid = currentVideo.aid,
                                                credential = cred,
                                            ).getOrThrow()
                                            videoRelation = BiliVideoRelation(
                                                liked = result.liked || videoRelation.liked,
                                                favorited = result.favorited || videoRelation.favorited,
                                                coinCount = when {
                                                    result.coined -> 2
                                                    videoRelation.coinCount > 0 -> videoRelation.coinCount
                                                    else -> 0
                                                },
                                            )
                                            api.getVideoDetail(currentVideo.bvid, cred)?.let { refreshed ->
                                                detail = api.hydrateVideoUgcSeason(refreshed, cred)
                                            }
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "一键三连失败",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        videoActionLoading = false
                                    }
                                },
                                onCoinClick = { anchorBounds ->
                                    if (credential == null) {
                                        onLoginRequired()
                                        return@VideoDetailActionBar
                                    }
                                    if (videoRelation.coinCount >= 2) {
                                        Toast.makeText(context, "已经投过币了", Toast.LENGTH_SHORT).show()
                                        return@VideoDetailActionBar
                                    }
                                    if (videoActionLoading) return@VideoDetailActionBar
                                    coinMenuAnchor = anchorBounds
                                    showCoinDialog = true
                                },
                                onFavoriteClick = {
                                    val cred = credential ?: run {
                                        onLoginRequired()
                                        return@VideoDetailActionBar
                                    }
                                    if (videoActionLoading) return@VideoDetailActionBar
                                    scope.launch {
                                        videoActionLoading = true
                                        val targetFavorite = !videoRelation.favorited
                                        runCatching {
                                            api.modifyVideoFavorite(
                                                bvid = currentVideo.bvid,
                                                aid = currentVideo.aid,
                                                add = targetFavorite,
                                                credential = cred,
                                            ).getOrThrow()
                                            videoRelation = videoRelation.copy(favorited = targetFavorite)
                                            detail = detail?.let { loaded ->
                                                val delta = if (targetFavorite) 1L else -1L
                                                loaded.copy(
                                                    favoriteCount = (loaded.favoriteCount + delta)
                                                        .coerceAtLeast(0L),
                                                )
                                            }
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "收藏失败",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        videoActionLoading = false
                                    }
                                },
                                onShareClick = {
                                    if (videoActionLoading) return@VideoDetailActionBar
                                    scope.launch {
                                        videoActionLoading = true
                                        runCatching {
                                            api.shareVideo(
                                                bvid = currentVideo.bvid,
                                                aid = currentVideo.aid,
                                                credential = credential,
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "https://www.bilibili.com/video/${currentVideo.bvid}",
                                                )
                                            }
                                            context.startActivity(
                                                Intent.createChooser(shareIntent, "分享视频"),
                                            )
                                            detail = detail?.copy(
                                                shareCount = (detail?.shareCount ?: 0L) + 1L,
                                            )
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "分享失败",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        videoActionLoading = false
                                    }
                                },
                            )
                        }
                        effectiveUgcSeason?.let { season ->
                            item(key = "intro-ugc-season") {
                                VideoDetailUgcSeasonSection(
                                    season = season,
                                    onGroupClick = { sectionId, anchorBounds ->
                                        collectionAnchor = anchorBounds
                                        collectionSheetState = VideoCollectionSheetState.UgcSeason(
                                            season = season,
                                            highlightSectionId = sectionId,
                                        )
                                        showCollectionSheet = true
                                    },
                                )
                            }
                        }
                        if (effectiveUgcSeason == null) {
                            if (effectivePages.size > 1) {
                                item(key = "intro-multi-part") {
                                    VideoDetailMultiPartSection(
                                        title = displayTitle,
                                        partCount = effectivePages.size,
                                        onClick = { anchorBounds ->
                                            collectionAnchor = anchorBounds
                                            collectionSheetState = VideoCollectionSheetState.MultiPart(effectivePages)
                                            showCollectionSheet = true
                                        },
                                    )
                                }
                            }
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
                                            videoAuthorMid = currentVideo.authorMid,
                                            onAuthorClick = onAuthorClick,
                                            onLinkClick = handleCommentLinkClick,
                                            onCommentImageClick = { pictures, index, bounds ->
                                                commentImageViewer = CommentImageViewerRequest(
                                                    images = pictures.map(BiliViewerImage::fromCommentPicture),
                                                    initialIndex = index,
                                                    sourceBoundsByIndex = mapOf(index to bounds),
                                                )
                                            },
                                        )
                                    }
                                    is CommentReplyEntry -> {
                                        VideoCommentRow(
                                            comment = entry.reply,
                                            depth = 1,
                                            videoAuthorMid = currentVideo.authorMid,
                                            onAuthorClick = onAuthorClick,
                                            onLinkClick = handleCommentLinkClick,
                                            onCommentImageClick = { pictures, index, bounds ->
                                                commentImageViewer = CommentImageViewerRequest(
                                                    images = pictures.map(BiliViewerImage::fromCommentPicture),
                                                    initialIndex = index,
                                                    sourceBoundsByIndex = mapOf(index to bounds),
                                                )
                                            },
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
    VideoCoinChoiceDialog(
        visible = showCoinDialog,
        anchorBoundsInRoot = coinMenuAnchor,
        canCoinTwo = videoRelation.coinCount == 0,
        onDismiss = { showCoinDialog = false },
        hazeState = hazeState,
            onCoinOne = {
                showCoinDialog = false
                val cred = credential ?: return@VideoCoinChoiceDialog
                scope.launch {
                    videoActionLoading = true
                    runCatching {
                        api.coinVideo(
                            bvid = currentVideo.bvid,
                            aid = currentVideo.aid,
                            multiply = 1,
                            credential = cred,
                        ).getOrThrow()
                        videoRelation = videoRelation.copy(
                            coinCount = (videoRelation.coinCount + 1).coerceAtMost(2),
                        )
                        detail = detail?.copy(
                            coinCount = (detail?.coinCount ?: 0L) + 1L,
                        )
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "投币失败", Toast.LENGTH_SHORT).show()
                    }
                    videoActionLoading = false
                }
            },
            onCoinTwo = {
                showCoinDialog = false
                val cred = credential ?: return@VideoCoinChoiceDialog
                scope.launch {
                    videoActionLoading = true
                    runCatching {
                        api.coinVideo(
                            bvid = currentVideo.bvid,
                            aid = currentVideo.aid,
                            multiply = 2,
                            credential = cred,
                        ).getOrThrow()
                        videoRelation = videoRelation.copy(coinCount = 2)
                        detail = detail?.copy(
                            coinCount = (detail?.coinCount ?: 0L) + 2L,
                        )
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "投币失败", Toast.LENGTH_SHORT).show()
                    }
                    videoActionLoading = false
                }
            },
        )
    VideoDetailMoreMenu(
        visible = showMoreMenu,
        anchorBoundsInRoot = moreMenuAnchor,
        hazeState = hazeState,
        onDismiss = { showMoreMenu = false },
        onOpenOfficialApp = {
            showMoreMenu = false
            coordinator.pauseForOverlay()
            BilibiliAppLauncher.openVideo(
                context = context,
                bvid = currentVideo.bvid,
                aid = currentVideo.aid,
            )
        },
    )
    collectionSheetState?.let { sheetState ->
        val sheetTitle = when (sheetState) {
            is VideoCollectionSheetState.MultiPart -> "分集 (${sheetState.pages.size})"
            is VideoCollectionSheetState.UgcSeason -> {
                val count = sheetUgcSeason?.episodeCount ?: sheetState.season.episodeCount
                "合集 ($count)"
            }
        }
        VideoDetailCollectionSheet(
            visible = showCollectionSheet,
            sheetTitle = sheetTitle,
            anchorBoundsInRoot = collectionAnchor,
            menuBackdrop = collectionMenuBackdrop,
            hazeState = hazeState,
            ugcSeason = when (sheetState) {
                is VideoCollectionSheetState.UgcSeason -> sheetUgcSeason ?: sheetState.season
                is VideoCollectionSheetState.MultiPart -> null
            },
            highlightSectionId = (sheetState as? VideoCollectionSheetState.UgcSeason)?.highlightSectionId,
            pages = when (sheetState) {
                is VideoCollectionSheetState.MultiPart -> sheetState.pages
                is VideoCollectionSheetState.UgcSeason -> emptyList()
            },
            currentBvid = currentVideo.bvid,
            currentCid = currentCid,
            authorName = currentVideo.authorName,
            authorMid = currentVideo.authorMid,
            onDismiss = { showCollectionSheet = false },
            onUgcEpisodeClick = ::switchToUgcEpisode,
            onPartClick = ::switchToPart,
        )
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
                imageVector = BiliVideoPlayCountIcon,
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
                imageVector = BiliVideoDanmakuCountIcon,
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
    videoAuthorMid: Long = 0L,
    onAuthorClick: (BiliUserProfile) -> Unit = {},
    onCommentImageClick: (List<BiliCommentPicture>, Int, Rect) -> Unit = { _, _, _ -> },
    onLinkClick: ((BiliLinkTarget) -> Unit)? = null,
) {
    val rowStart = CommentRowOuterStart + (depth * 24).dp
    val canOpenProfile = comment.authorMid > 0L
    val isVideoAuthor = videoAuthorMid > 0L && comment.authorMid == videoAuthorMid
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
            CommentAuthorHeaderRow(
                authorName = comment.authorName,
                level = comment.level,
                isVideoAuthor = isVideoAuthor,
                isPinned = comment.isPinned,
                onClick = openProfile,
                canOpenProfile = canOpenProfile,
            )
            if (shouldShowCommentText(comment.content, comment.pictures)) {
                BiliCommentText(
                    text = comment.content,
                    emoticons = comment.emoticons,
                    style = MaterialTheme.typography.bodyMedium,
                    onLinkClick = onLinkClick,
                )
            }
            if (comment.pictures.isNotEmpty()) {
                BiliCommentImageStrip(
                    pictures = comment.pictures,
                    onOpenViewer = { index, bounds ->
                        onCommentImageClick(comment.pictures, index, bounds)
                    },
                )
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
