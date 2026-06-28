package com.example.bilibili.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BiliAuthorRelation
import com.example.bilibili.data.BiliDynamicItem
import com.example.bilibili.data.BiliDynamicLink
import com.example.bilibili.data.BiliDynamicOrigin
import com.example.bilibili.ui.components.BiliWebReaderOverlay
import com.example.bilibili.ui.components.BiliWebReaderState
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliUserProfile
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.player.StatusBarIconsEffect
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.ui.components.BilibiliFollowButton
import com.example.bilibili.ui.components.BiliUserLevelIcon
import com.example.bilibili.ui.components.ObserveStaggeredGridNearEnd
import com.example.bilibili.ui.components.ObserveListNearEnd
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.VideoFeedCard
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.format.formatBiliPublishTime
import com.example.bilibili.ui.theme.BiliPink
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class UserProfileContentTab(val label: String) {
    Posts("投稿"),
    Dynamics("动态"),
}

private val ProfileHeaderAvatarSize = 72.dp
private val ProfileHeaderAvatarInset = 3.dp
private val ProfileHeaderAvatarFrameSize = ProfileHeaderAvatarSize + ProfileHeaderAvatarInset * 2
private val ProfileHeaderCoverAspect = 2.55f
private val ProfileHeaderCardCoverOverlap = 22.dp
private val ProfileHeaderCardRadius = 16.dp
private val ProfileHeaderStatsSpacing = 4.dp
private val ProfileCompactAvatarSize = 32.dp
private val ProfileCompactBarStartPadding = 16.dp
private val ProfileCompactBarContentSpacing = 10.dp
private val ProfileCompactLevelIconWidth = 28.dp
private val ProfileCompactLevelIconHeight = 18.dp

@Composable
fun UserProfileScreen(
    mid: Long,
    seedName: String,
    seedFace: String,
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    myMid: Long?,
    playUrls: Map<String, BiliPlayStream>,
    coordinator: VideoPlaybackCoordinator,
    onVideoClick: (BiliVideoItem) -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit = {},
    onLoginRequired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var profile by remember(mid) {
        mutableStateOf(
            BiliUserProfile(
                mid = mid,
                name = seedName,
                face = seedFace,
                sign = "",
                level = 0,
            ),
        )
    }
    var relation by remember(mid) { mutableStateOf(BiliAuthorRelation()) }
    var videos by remember(mid) { mutableStateOf<List<BiliVideoItem>>(emptyList()) }
    var dynamics by remember(mid) { mutableStateOf<List<BiliDynamicItem>>(emptyList()) }
    var loading by remember(mid) { mutableStateOf(true) }
    var refreshing by remember(mid) { mutableStateOf(false) }
    var loadError by remember(mid) { mutableStateOf<String?>(null) }
    var followLoading by remember(mid) { mutableStateOf(false) }

    var videosPage by remember(mid) { mutableStateOf(1) }
    var videosHasMore by remember(mid) { mutableStateOf(true) }
    var videosLoadingMore by remember(mid) { mutableStateOf(false) }

    var dynamicsOffset by remember(mid) { mutableStateOf<String?>(null) }
    var dynamicsHasMore by remember(mid) { mutableStateOf(true) }
    var dynamicsLoadingMore by remember(mid) { mutableStateOf(false) }
    var dynamicsLoaded by remember(mid) { mutableStateOf(false) }

    val postsStaggeredGridState = rememberLazyStaggeredGridState()
    val dynamicsListState = rememberLazyListState()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { UserProfileContentTab.entries.size },
    )
    val coroutineScope = rememberCoroutineScope()

    suspend fun loadProfile(resetLists: Boolean) {
        if (resetLists) {
            videosPage = 1
            videosHasMore = true
            dynamicsOffset = null
            dynamicsHasMore = true
            dynamicsLoaded = false
        }
        coroutineScope {
            val profileDeferred = async {
                api.getUserInfo(mid, credential)?.let { loaded ->
                    profile = loaded.copy(
                        name = loaded.name.ifBlank { seedName },
                        face = loaded.face.ifBlank { seedFace },
                    )
                    val videoCount = api.getUserNavnum(mid, credential)
                    if (videoCount > 0L) {
                        profile = profile.copy(videoCount = videoCount)
                    }
                }
            }
            val relationDeferred = async {
                if (credential != null && myMid != mid) {
                    relation = api.getUserRelation(
                        mid = mid,
                        credential = credential,
                        referer = "https://space.bilibili.com/$mid",
                    )
                }
            }
            val videosDeferred = async {
                val page = api.getUserVideoPage(mid, pn = 1, credential = credential)
                videos = page.videos
                videosHasMore = page.hasMore
                videosPage = 1
            }
            profileDeferred.await()
            relationDeferred.await()
            videosDeferred.await()
        }
    }

    suspend fun loadDynamics(reset: Boolean) {
        if (reset) {
            dynamicsOffset = null
            dynamicsHasMore = true
        }
        if (!dynamicsHasMore && !reset) return
        val page = api.getUserSpaceDynamics(
            mid = mid,
            offset = if (reset) null else dynamicsOffset,
            credential = credential,
        )
        dynamics = if (reset) page.items else dynamics + page.items
        dynamicsOffset = page.nextOffset
        dynamicsHasMore = page.hasMore
        dynamicsLoaded = true
    }

    fun refresh(keepContent: Boolean = false) {
        scope.launch {
            if (keepContent) refreshing = true else loading = true
            loadError = null
            runCatching {
                loadProfile(resetLists = true)
                if (pagerState.currentPage == UserProfileContentTab.Dynamics.ordinal || dynamicsLoaded) {
                    loadDynamics(reset = true)
                }
            }.onFailure { loadError = it.message }
            loading = false
            refreshing = false
        }
    }

    fun loadMoreVideos() {
        if (videosLoadingMore || !videosHasMore) return
        scope.launch {
            videosLoadingMore = true
            runCatching {
                val nextPage = videosPage + 1
                val page = api.getUserVideoPage(mid, pn = nextPage, credential = credential)
                if (page.videos.isNotEmpty()) {
                    videos = videos + page.videos
                    videosPage = nextPage
                }
                videosHasMore = page.hasMore && page.videos.isNotEmpty()
            }
            videosLoadingMore = false
        }
    }

    fun loadMoreDynamics() {
        if (dynamicsLoadingMore || !dynamicsHasMore) return
        scope.launch {
            dynamicsLoadingMore = true
            runCatching { loadDynamics(reset = false) }
            dynamicsLoadingMore = false
        }
    }

    LaunchedEffect(mid) {
        loading = true
        loadError = null
        runCatching { loadProfile(resetLists = true) }
            .onFailure { loadError = it.message }
        loading = false
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page == UserProfileContentTab.Dynamics.ordinal && !dynamicsLoaded && !loading) {
                    dynamicsLoadingMore = true
                    runCatching { loadDynamics(reset = true) }
                        .onFailure { loadError = it.message }
                    dynamicsLoadingMore = false
                }
            }
    }

    ObserveStaggeredGridNearEnd(postsStaggeredGridState) { loadMoreVideos() }
    ObserveListNearEnd(dynamicsListState) { loadMoreDynamics() }

    val tabScrollPosition by remember {
        derivedStateOf {
            pagerState.currentPage + pagerState.currentPageOffsetFraction
        }
    }

    val density = LocalDensity.current
    val collapseThresholdPx = remember(density) { with(density) { 72.dp.roundToPx() } }
    val compactBarContentHeight = 48.dp
    var profileHeaderHeight by remember(mid) { mutableStateOf(0.dp) }
    val collapseProgress by remember(pagerState.currentPage, postsStaggeredGridState, dynamicsListState, collapseThresholdPx) {
        derivedStateOf {
            when (pagerState.currentPage) {
                UserProfileContentTab.Posts.ordinal -> when {
                    postsStaggeredGridState.firstVisibleItemIndex > 0 -> 1f
                    else -> (postsStaggeredGridState.firstVisibleItemScrollOffset.toFloat() / collapseThresholdPx)
                        .coerceIn(0f, 1f)
                }
                else -> when {
                    dynamicsListState.firstVisibleItemIndex > 0 -> 1f
                    else -> (dynamicsListState.firstVisibleItemScrollOffset.toFloat() / collapseThresholdPx)
                        .coerceIn(0f, 1f)
                }
            }
        }
    }
    val animatedCollapseAnim = remember { Animatable(0f) }
    var collapseAnimationReady by remember { mutableStateOf(false) }
    LaunchedEffect(collapseProgress) {
        if (!collapseAnimationReady) {
            animatedCollapseAnim.snapTo(collapseProgress)
            collapseAnimationReady = true
        } else {
            animatedCollapseAnim.animateTo(
                collapseProgress,
                animationSpec = tween(durationMillis = 220),
            )
        }
    }
    val animatedCollapse = animatedCollapseAnim.value
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val compactBarHeight = (topInset + compactBarContentHeight) * animatedCollapse
    val profileHeaderSlotHeight = when {
        profileHeaderHeight > 0.dp ->
            (profileHeaderHeight * (1f - animatedCollapse)).coerceAtLeast(0.dp)
        animatedCollapse >= 1f -> 0.dp
        else -> Dp.Unspecified
    }
    val showFollowButton = myMid == null || myMid != mid
    var webReader by remember { mutableStateOf<BiliWebReaderState?>(null) }
    val compactHeaderVisible = animatedCollapse > 0.01f

    StatusBarIconsEffect(darkIcons = compactHeaderVisible)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refresh(keepContent = true) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(compactBarHeight)
                            .graphicsLayer { clip = true },
                    ) {
                        if (compactHeaderVisible) {
                            Column(Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(topInset)
                                        .background(Color.White),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(compactBarContentHeight)
                                        .background(Color.White)
                                        .padding(
                                            start = ProfileCompactBarStartPadding,
                                            end = if (showFollowButton) 12.dp else 4.dp,
                                        )
                                        .graphicsLayer { alpha = animatedCollapse },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ProfileCompactBarContentSpacing),
                                ) {
                                RemoteImage(
                                    url = profile.face,
                                    modifier = Modifier
                                        .size(ProfileCompactAvatarSize)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    ProfileAuthorNameRow(
                                        name = profile.name.ifBlank { "UP主" },
                                        level = profile.level,
                                        nameStyle = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        levelIconWidth = ProfileCompactLevelIconWidth,
                                        levelIconHeight = ProfileCompactLevelIconHeight,
                                    )
                                }
                                if (showFollowButton) {
                                    BilibiliFollowButton(
                                        following = relation.following,
                                        followerMe = relation.followerMe,
                                        loading = followLoading,
                                        compact = true,
                                        onClick = {
                                            val cred = credential ?: run {
                                                onLoginRequired()
                                                return@BilibiliFollowButton
                                            }
                                            scope.launch {
                                                followLoading = true
                                                val previous = relation
                                                val targetFollow = !relation.following
                                                relation = relation.copy(following = targetFollow)
                                                runCatching {
                                                    api.modifyFollow(
                                                        mid = mid,
                                                        follow = targetFollow,
                                                        credential = cred,
                                                        referer = "https://space.bilibili.com/$mid",
                                                    ).getOrThrow()
                                                    relation = api.getUserRelation(
                                                        mid = mid,
                                                        credential = cred,
                                                        referer = "https://space.bilibili.com/$mid",
                                                    )
                                                }.onFailure {
                                                    relation = previous
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
                            }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (profileHeaderSlotHeight != Dp.Unspecified) {
                                    Modifier.height(profileHeaderSlotHeight)
                                } else {
                                    Modifier
                                },
                            )
                            .clipToBounds(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(unbounded = true)
                                .onGloballyPositioned { coordinates ->
                                    if (coordinates.size.height <= 0) return@onGloballyPositioned
                                    val measured = with(density) {
                                        coordinates.size.height.toDp()
                                    }
                                    if (measured != profileHeaderHeight) {
                                        profileHeaderHeight = measured
                                    }
                                },
                        ) {
                            UserProfileHeader(
                                profile = profile,
                                loadError = loadError,
                                showFollowButton = showFollowButton,
                                relation = relation,
                                followLoading = followLoading,
                                onFollowClick = {
                                    val cred = credential ?: run {
                                        onLoginRequired()
                                        return@UserProfileHeader
                                    }
                                    scope.launch {
                                        followLoading = true
                                        val previous = relation
                                        val targetFollow = !relation.following
                                        relation = relation.copy(following = targetFollow)
                                        runCatching {
                                            api.modifyFollow(
                                                mid = mid,
                                                follow = targetFollow,
                                                credential = cred,
                                                referer = "https://space.bilibili.com/$mid",
                                            ).getOrThrow()
                                            relation = api.getUserRelation(
                                                mid = mid,
                                                credential = cred,
                                                referer = "https://space.bilibili.com/$mid",
                                            )
                                        }.onFailure {
                                            relation = previous
                                            Toast.makeText(
                                                context,
                                                it.message ?: "关注失败",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        followLoading = false
                                    }
                                },
                                compactVisible = animatedCollapse < 0.5f,
                            )
                        }
                    }

                    UserProfileContentTabs(
                        scrollPosition = tabScrollPosition,
                        onTabSelected = { tab ->
                            val sameTab = tab == UserProfileContentTab.entries[pagerState.currentPage]
                            if (sameTab) {
                                coroutineScope.launch {
                                    when (tab) {
                                        UserProfileContentTab.Posts ->
                                            postsStaggeredGridState.animateScrollToItem(0)
                                        UserProfileContentTab.Dynamics ->
                                            dynamicsListState.animateScrollToItem(0)
                                    }
                                }
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        UserProfileContentTab.entries.indexOf(tab),
                                    )
                                }
                            }
                        },
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                    )

                    loadError?.let { error ->
                        Text(
                            text = error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (loading && videos.isEmpty() && dynamics.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            beyondViewportPageCount = 0,
                        ) { page ->
                            when (UserProfileContentTab.entries[page]) {
                                UserProfileContentTab.Posts -> {
                                    LazyVerticalStaggeredGrid(
                                        columns = StaggeredGridCells.Fixed(2),
                                        state = postsStaggeredGridState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = HomeFeedGridHorizontalPadding,
                                            end = HomeFeedGridHorizontalPadding,
                                            bottom = 24.dp,
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(HomeFeedGridSpacing),
                                        verticalItemSpacing = HomeFeedGridSpacing,
                                    ) {
                                        if (videos.isEmpty()) {
                                            item(key = "posts-empty", span = StaggeredGridItemSpan.FullLine) {
                                                ProfileEmptyState(
                                                    title = "暂无投稿",
                                                    body = "该 UP 主还没有发布视频。",
                                                )
                                            }
                                        } else {
                                            items(videos, key = { it.bvid }) { video ->
                                                VideoFeedCard(
                                                    video = video,
                                                    playStream = playUrls[video.bvid],
                                                    coordinator = coordinator,
                                                    onClick = { onVideoClick(video) },
                                                    onEnsurePlayStream = { onEnsurePlayStream(video) },
                                                    overlayMetaOnCover = true,
                                                )
                                            }
                                            if (videosLoadingMore) {
                                                item(key = "posts-loading", span = StaggeredGridItemSpan.FullLine) {
                                                    ProfileLoadingMoreIndicator()
                                                }
                                            }
                                            if (!videosHasMore && videos.isNotEmpty()) {
                                                item(key = "posts-end", span = StaggeredGridItemSpan.FullLine) {
                                                    ProfileListEndHint()
                                                }
                                            }
                                        }
                                    }
                                }

                                UserProfileContentTab.Dynamics -> {
                                    LazyColumn(
                                        state = dynamicsListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 24.dp),
                                    ) {
                                        if (dynamics.isEmpty()) {
                                            item(key = "dynamics-empty") {
                                                ProfileEmptyState(
                                                    title = if (dynamicsLoadingMore) "加载中" else "暂无动态",
                                                    body = when {
                                                        dynamicsLoadingMore -> "正在读取 TA 的动态…"
                                                        else -> "下拉刷新后会从动态接口重新加载。"
                                                    },
                                                )
                                            }
                                        } else {
                                            items(dynamics, key = { it.id }) { item ->
                                                DynamicFeedCard(
                                                    item = item,
                                                    onVideoClick = onVideoClick,
                                                    onLinkClick = { link ->
                                                        webReader = BiliWebReaderState(
                                                            url = link.url,
                                                            title = link.title,
                                                        )
                                                    },
                                                    modifier = Modifier.padding(
                                                        horizontal = 16.dp,
                                                        vertical = 8.dp,
                                                    ),
                                                )
                                            }
                                            if (dynamicsLoadingMore) {
                                                item(key = "dynamics-loading") {
                                                    ProfileLoadingMoreIndicator()
                                                }
                                            }
                                            if (!dynamicsHasMore && dynamics.isNotEmpty()) {
                                                item(key = "dynamics-end") {
                                                    ProfileListEndHint()
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

        if (compactHeaderVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(topInset)
                    .background(Color.White),
            )
        }

        BiliWebReaderOverlay(
            state = webReader,
            onBack = { webReader = null },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun UserProfileHeader(
    profile: BiliUserProfile,
    loadError: String?,
    showFollowButton: Boolean,
    relation: BiliAuthorRelation,
    followLoading: Boolean,
    onFollowClick: () -> Unit,
    compactVisible: Boolean,
) {
    val avatarExposeAboveCard = ProfileHeaderAvatarFrameSize / 3f

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(unbounded = true),
        ) {
            val coverHeight = maxWidth / ProfileHeaderCoverAspect

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(coverHeight)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    BiliPink.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ),
                        ),
                ) {
                    if (profile.topPhoto.isNotBlank()) {
                        RemoteImage(
                            url = profile.topPhoto,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    if (compactVisible && showFollowButton) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(end = 12.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BilibiliFollowButton(
                                following = relation.following,
                                followerMe = relation.followerMe,
                                loading = followLoading,
                                onClick = onFollowClick,
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = -ProfileHeaderCardCoverOverlap)
                        .zIndex(1f),
                    shape = RoundedCornerShape(topStart = ProfileHeaderCardRadius, topEnd = ProfileHeaderCardRadius),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = ProfileHeaderStatsSpacing,
                            ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.width(ProfileHeaderAvatarFrameSize),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(Modifier.height(ProfileHeaderAvatarFrameSize - avatarExposeAboveCard))
                            }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, top = 6.dp),
                    ) {
                                ProfileAuthorNameRow(
                                    name = profile.name.ifBlank { "UP主" },
                                    level = profile.level,
                                    nameStyle = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Text(
                                    text = profile.sign.ifBlank { "这个人很神秘，什么都没有写" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            ProfileStatItem("关注", profile.following)
                            ProfileStatItem("粉丝", profile.follower)
                            ProfileStatItem("获赞", profile.likes)
                            if (profile.videoCount > 0L) {
                                ProfileStatItem("投稿", profile.videoCount)
                            }
                        }

                        loadError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            if (profile.face.isNotBlank()) {
                RemoteImage(
                    url = profile.face,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp)
                        .size(ProfileHeaderAvatarFrameSize)
                        .offset(
                            y = coverHeight - ProfileHeaderCardCoverOverlap - avatarExposeAboveCard,
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(ProfileHeaderAvatarInset)
                        .clip(CircleShape)
                        .zIndex(2f),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp)
                        .size(ProfileHeaderAvatarFrameSize)
                        .offset(
                            y = coverHeight - ProfileHeaderCardCoverOverlap - avatarExposeAboveCard,
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .zIndex(2f),
                )
            }
        }
    }
}

@Composable
private fun UserProfileContentTabs(
    scrollPosition: Float,
    onTabSelected: (UserProfileContentTab) -> Unit,
) {
    val tabs = UserProfileContentTab.entries
    val accent = BiliPink
    val density = LocalDensity.current
    val indicatorWidth = 22.dp
    val tabCentersPx = remember { FloatArray(tabs.size) { Float.NaN } }
    var layoutReady by remember { mutableStateOf(false) }
    val highlightedIndex = scrollPosition
        .roundToInt()
        .coerceIn(0, tabs.lastIndex)

    val indicatorCenterPx = if (!layoutReady || tabs.size == 1) {
        tabCentersPx.firstOrNull { !it.isNaN() } ?: 0f
    } else {
        val position = scrollPosition.coerceIn(0f, tabs.lastIndex.toFloat())
        val left = position.toInt()
        val right = (left + 1).coerceAtMost(tabs.lastIndex)
        val fraction = position - left
        tabCentersPx[left] + (tabCentersPx[right] - tabCentersPx[left]) * fraction
    }
    val indicatorOffset = with(density) { indicatorCenterPx.toDp() - indicatorWidth / 2 }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, bottom = 2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.Top,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == highlightedIndex
                Column(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            tabCentersPx[index] = coords.positionInParent().x + coords.size.width / 2f
                            if (!layoutReady && tabCentersPx.all { !it.isNaN() }) {
                                layoutReady = true
                            }
                        }
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tab.label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                }
            }
        }

        if (layoutReady) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent),
            )
        }
    }
}

@Composable
private fun DynamicFeedCard(
    item: BiliDynamicItem,
    onVideoClick: (BiliVideoItem) -> Unit,
    onLinkClick: (BiliDynamicLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(12.dp),
    ) {
        if (item.text.isNotBlank()) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.origin != null) {
            DynamicOriginBlock(
                origin = item.origin,
                onVideoClick = onVideoClick,
                onLinkClick = onLinkClick,
                modifier = Modifier.padding(top = if (item.text.isNotBlank()) 10.dp else 0.dp),
            )
        } else {
            DynamicContentBlock(
                text = "",
                video = item.video,
                imageUrls = item.imageUrls,
                link = item.link,
                onVideoClick = onVideoClick,
                onLinkClick = onLinkClick,
                modifier = Modifier.padding(top = if (item.text.isNotBlank()) 10.dp else 0.dp),
            )
        }
        DynamicFeedMetaRow(item = item)
    }
}

@Composable
private fun DynamicOriginBlock(
    origin: BiliDynamicOrigin,
    onVideoClick: (BiliVideoItem) -> Unit,
    onLinkClick: (BiliDynamicLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
    ) {
        if (origin.authorName.isNotBlank()) {
            Text(
                text = "@${origin.authorName}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DynamicContentBlock(
            text = origin.text,
            video = origin.video,
            imageUrls = origin.imageUrls,
            link = origin.link,
            onVideoClick = onVideoClick,
            onLinkClick = onLinkClick,
            modifier = Modifier.padding(top = if (origin.authorName.isNotBlank()) 6.dp else 0.dp),
        )
    }
}

@Composable
private fun DynamicContentBlock(
    text: String,
    video: BiliVideoItem?,
    imageUrls: List<String>,
    link: BiliDynamicLink?,
    onVideoClick: (BiliVideoItem) -> Unit,
    onLinkClick: (BiliDynamicLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        video?.let { videoItem ->
            DynamicVideoCard(
                video = videoItem,
                onClick = { onVideoClick(videoItem) },
                modifier = Modifier.padding(
                    top = if (text.isNotBlank()) 10.dp else 0.dp,
                ),
            )
        }
        link?.let { linkItem ->
            DynamicLinkCard(
                link = linkItem,
                onClick = { onLinkClick(linkItem) },
                modifier = Modifier.padding(
                    top = when {
                        text.isNotBlank() || video != null -> 10.dp
                        else -> 0.dp
                    },
                ),
            )
        }
        if (imageUrls.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = when {
                            text.isNotBlank() || video != null || link != null -> 10.dp
                            else -> 0.dp
                        },
                    ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                imageUrls.take(3).forEach { url ->
                    RemoteImage(
                        url = url,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicVideoCard(
    video: BiliVideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = video.coverUrl,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatBiliCount(video.viewCount)}播放",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DynamicLinkCard(
    link: BiliDynamicLink,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (link.coverUrl.isNotBlank()) {
            RemoteImage(
                url = link.coverUrl,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = link.title.ifBlank { "查看链接" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (link.desc.isNotBlank()) {
                Text(
                    text = link.desc,
                    style = MaterialTheme.typography.labelSmall,
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
private fun DynamicFeedMetaRow(item: BiliDynamicItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.publishTimeSeconds > 0L) {
            Text(
                text = formatBiliPublishTime(item.publishTimeSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.likeCount > 0L) {
            Text(
                text = "${formatBiliCount(item.likeCount)}赞",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.commentCount > 0L) {
            Text(
                text = "${formatBiliCount(item.commentCount)}评论",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.repostCount > 0L) {
            Text(
                text = "${formatBiliCount(item.repostCount)}转发",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileAuthorNameRow(
    name: String,
    level: Int,
    nameStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    levelIconWidth: Dp = 34.dp,
    levelIconHeight: Dp = 22.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = nameStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (level > 0) {
            BiliUserLevelIcon(
                level = level,
                width = levelIconWidth,
                height = levelIconHeight,
            )
        }
    }
}

@Composable
private fun ProfileStatItem(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatBiliCount(value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileEmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ProfileLoadingMoreIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun ProfileListEndHint() {
    Text(
        text = "已经到底了",
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
