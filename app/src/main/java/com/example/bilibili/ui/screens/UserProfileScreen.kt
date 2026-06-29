package com.example.bilibili.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.bilibili.data.BiliUserVideoSort
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.data.FeedLayoutStore
import com.example.bilibili.data.UserProfileSessionCache
import com.example.bilibili.data.UserProfileUiState
import com.example.bilibili.data.UserRelationTab
import com.example.bilibili.player.StatusBarIconsEffect
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.ui.components.BiliCommentText
import com.example.bilibili.ui.components.BilibiliFollowButton
import com.example.bilibili.ui.components.BiliUserLevelIcon
import com.example.bilibili.ui.components.SlidingTextTabs
import com.example.bilibili.ui.components.ObserveStaggeredGridNearEnd
import com.example.bilibili.ui.components.ObserveListNearEnd
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.VideoFeedCard
import com.example.bilibili.ui.components.imageviewer.BiliFullscreenImageViewer
import com.example.bilibili.ui.components.imageviewer.BiliImageGrid
import com.example.bilibili.data.BiliViewerImage
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.format.formatBiliPublishTime
import com.example.bilibili.ui.format.formatVideoDurationLabel
import com.example.bilibili.ui.liquidglass.BottomBarFeedOverlapReserve
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

private fun mergeProfileOnRefresh(
    previous: BiliUserProfile,
    loaded: BiliUserProfile,
    videoCount: Long,
    seedName: String,
    seedFace: String,
): BiliUserProfile = loaded.copy(
    name = loaded.name.ifBlank { seedName }.ifBlank { previous.name },
    face = loaded.face.ifBlank { seedFace }.ifBlank { previous.face },
    sign = loaded.sign.ifBlank { previous.sign },
    level = loaded.level.takeIf { it > 0 } ?: previous.level,
    following = loaded.following.takeIf { it > 0L } ?: previous.following,
    follower = loaded.follower.takeIf { it > 0L } ?: previous.follower,
    likes = loaded.likes.takeIf { it > 0L } ?: previous.likes,
    videoCount = videoCount.takeIf { it > 0L }
        ?: loaded.videoCount.takeIf { it > 0L }
        ?: previous.videoCount,
    topPhoto = loaded.topPhoto.ifBlank { previous.topPhoto },
    topPhotos = loaded.topPhotos.ifEmpty { previous.topPhotos },
)

private val ProfileHeaderAvatarSize = 72.dp
private val ProfileHeaderAvatarInset = 3.dp
private val ProfileHeaderAvatarFrameSize = ProfileHeaderAvatarSize + ProfileHeaderAvatarInset * 2
private val ProfileHeaderCoverAspect = 2.55f
private val ProfileHeaderCardCoverOverlap = 22.dp
private val ProfileHeaderCardRadius = 16.dp
private val ProfileHeaderCardBorderWidth = 0.5.dp
private val ProfileHeaderCardBorderColor = Color(0xFFE8E8E8)
private val DynamicFeedPageBackground = Color(0xFFF5F5F5)
private val DynamicFeedCardBackground = Color.White
private val ProfileHeaderCardContentInset = 6.dp
private val ProfileCompactAvatarSize = 32.dp
private val ProfileCompactBarStartPadding = 16.dp
private val ProfileCompactBarContentSpacing = 10.dp

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
    onDynamicClick: (BiliDynamicItem) -> Unit = {},
    onEnsurePlayStream: (BiliVideoItem) -> Unit = {},
    onLoginRequired: () -> Unit,
    onOpenRelationList: (name: String, face: String, sign: String, tab: UserRelationTab) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    enableSettings: Boolean = false,
    cacheProfile: Boolean = false,
    feedColumnCount: Int = FeedLayoutStore.COLUMN_COUNT_TWO,
    onFeedColumnCountChange: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    val useSingleColumnPosts = feedColumnCount == FeedLayoutStore.COLUMN_COUNT_ONE

    val uiState = if (cacheProfile) {
        remember(mid) { UserProfileSessionCache.getOrCreate(mid, seedName, seedFace) }
    } else {
        remember(mid) { UserProfileUiState(mid, seedName, seedFace) }
    }

    val postsStaggeredGridState = rememberLazyStaggeredGridState()
    val postsListState = rememberLazyListState()
    val dynamicsListState = rememberLazyListState()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { UserProfileContentTab.entries.size },
    )
    val coroutineScope = rememberCoroutineScope()

    suspend fun loadProfile(resetLists: Boolean) {
        if (resetLists) {
            uiState.videosPage = 1
            uiState.videosHasMore = true
            uiState.dynamicsOffset = null
            uiState.dynamicsHasMore = true
            uiState.dynamicsLoaded = false
        }
        val previousProfile = uiState.profile
        coroutineScope {
            val profileDeferred = async {
                val loaded = api.getUserInfo(mid, credential) ?: return@async
                val videoCount = api.getUserNavnum(mid, credential)
                uiState.profile = mergeProfileOnRefresh(
                    previous = previousProfile,
                    loaded = loaded,
                    videoCount = videoCount,
                    seedName = seedName,
                    seedFace = seedFace,
                )
            }
            val relationDeferred = async {
                if (credential != null && myMid != mid) {
                    uiState.relation = api.getUserRelation(
                        mid = mid,
                        credential = credential,
                        referer = "https://space.bilibili.com/$mid",
                    )
                }
            }
            val videosDeferred = async {
                val page = api.getUserVideoPage(
                    mid = mid,
                    pn = 1,
                    order = uiState.videosOrder,
                    credential = credential,
                )
                uiState.videos = page.videos
                uiState.videosHasMore = page.hasMore
                uiState.videosPage = 1
            }
            profileDeferred.await()
            relationDeferred.await()
            videosDeferred.await()
        }
    }

    suspend fun loadDynamics(reset: Boolean) {
        if (reset) {
            uiState.dynamicsOffset = null
            uiState.dynamicsHasMore = true
        }
        if (!uiState.dynamicsHasMore && !reset) return
        val page = api.getUserSpaceDynamics(
            mid = mid,
            offset = if (reset) null else uiState.dynamicsOffset,
            credential = credential,
        )
        uiState.dynamics = if (reset) page.items else uiState.dynamics + page.items
        uiState.dynamicsOffset = page.nextOffset
        uiState.dynamicsHasMore = page.hasMore
        uiState.dynamicsLoaded = true
    }

    fun refresh(keepContent: Boolean = false) {
        scope.launch {
            if (keepContent) uiState.refreshing = true else uiState.loading = true
            uiState.loadError = null
            runCatching {
                loadProfile(resetLists = true)
                if (pagerState.currentPage == UserProfileContentTab.Dynamics.ordinal || uiState.dynamicsLoaded) {
                    loadDynamics(reset = true)
                }
            }.onFailure { uiState.loadError = it.message }
            uiState.loading = false
            uiState.refreshing = false
        }
    }

    fun loadMoreVideos() {
        if (uiState.videosLoadingMore || !uiState.videosHasMore) return
        scope.launch {
            uiState.videosLoadingMore = true
            runCatching {
                val nextPage = uiState.videosPage + 1
                val page = api.getUserVideoPage(
                    mid = mid,
                    pn = nextPage,
                    order = uiState.videosOrder,
                    credential = credential,
                )
                if (page.videos.isNotEmpty()) {
                    uiState.videos = uiState.videos + page.videos
                    uiState.videosPage = nextPage
                }
                uiState.videosHasMore = page.hasMore && page.videos.isNotEmpty()
            }
            uiState.videosLoadingMore = false
        }
    }

    fun reloadVideosForSort() {
        scope.launch {
            uiState.videosLoadingMore = true
            uiState.loadError = null
            runCatching {
                val page = api.getUserVideoPage(
                    mid = mid,
                    pn = 1,
                    order = uiState.videosOrder,
                    credential = credential,
                )
                uiState.videos = page.videos
                uiState.videosHasMore = page.hasMore
                uiState.videosPage = 1
                if (useSingleColumnPosts) {
                    postsListState.animateScrollToItem(0)
                } else {
                    postsStaggeredGridState.animateScrollToItem(0)
                }
            }.onFailure { uiState.loadError = it.message }
            uiState.videosLoadingMore = false
        }
    }

    fun loadMoreDynamics() {
        if (uiState.dynamicsLoadingMore || !uiState.dynamicsHasMore) return
        scope.launch {
            uiState.dynamicsLoadingMore = true
            runCatching { loadDynamics(reset = false) }
            uiState.dynamicsLoadingMore = false
        }
    }

    LaunchedEffect(mid, cacheProfile) {
        if (cacheProfile && uiState.loaded) {
            uiState.loading = false
            return@LaunchedEffect
        }
        uiState.loading = true
        uiState.loadError = null
        runCatching { loadProfile(resetLists = true) }
            .onFailure { uiState.loadError = it.message }
        uiState.loaded = true
        uiState.loading = false
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page == UserProfileContentTab.Dynamics.ordinal && !uiState.dynamicsLoaded && !uiState.loading) {
                    uiState.dynamicsLoadingMore = true
                    runCatching { loadDynamics(reset = true) }
                        .onFailure { uiState.loadError = it.message }
                    uiState.dynamicsLoadingMore = false
                }
            }
    }

    if (useSingleColumnPosts) {
        ObserveListNearEnd(postsListState) { loadMoreVideos() }
    } else {
        ObserveStaggeredGridNearEnd(postsStaggeredGridState) { loadMoreVideos() }
    }
    ObserveListNearEnd(dynamicsListState) { loadMoreDynamics() }

    val tabScrollPosition by remember {
        derivedStateOf {
            pagerState.currentPage + pagerState.currentPageOffsetFraction
        }
    }

    val density = LocalDensity.current
    val collapseThresholdPx = remember(density) { with(density) { 72.dp.roundToPx() } }
    val compactBarContentHeight = 48.dp
    val collapseProgress by remember(
        pagerState.currentPage,
        postsStaggeredGridState,
        postsListState,
        dynamicsListState,
        collapseThresholdPx,
        useSingleColumnPosts,
    ) {
        derivedStateOf {
            when (pagerState.currentPage) {
                UserProfileContentTab.Posts.ordinal -> when {
                    useSingleColumnPosts -> when {
                        postsListState.firstVisibleItemIndex > 0 -> 1f
                        else -> (postsListState.firstVisibleItemScrollOffset.toFloat() / collapseThresholdPx)
                            .coerceIn(0f, 1f)
                    }
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
        uiState.profileHeaderHeight > 0.dp ->
            (uiState.profileHeaderHeight * (1f - animatedCollapse)).coerceAtLeast(0.dp)
        animatedCollapse >= 1f -> 0.dp
        else -> Dp.Unspecified
    }
    val tabsOverlapOffset = ProfileHeaderCardCoverOverlap * (1f - animatedCollapse)
    val showFollowButton = myMid == null || myMid != mid
    val openSettings = if (enableSettings) {
        { showSettings = true }
    } else {
        null
    }
    var webReader by remember { mutableStateOf<BiliWebReaderState?>(null) }
    val profileListBottomPadding =
        contentPadding.calculateBottomPadding() + BottomBarFeedOverlapReserve

    StatusBarIconsEffect(darkIcons = true)

    if (showSettings) {
        SettingsScreen(
            feedColumnCount = feedColumnCount,
            onFeedColumnCountChange = onFeedColumnCountChange,
            onBack = { showSettings = false },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = uiState.refreshing,
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
                                        end = if (showFollowButton) 12.dp else if (enableSettings) 4.dp else 4.dp,
                                    )
                                    .graphicsLayer { alpha = animatedCollapse },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ProfileCompactBarContentSpacing),
                            ) {
                                RemoteImage(
                                    url = uiState.profile.face,
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
                                        name = uiState.profile.name.ifBlank { "UP主" },
                                        level = uiState.profile.level,
                                        nameStyle = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    )
                                }
                                if (showFollowButton) {
                                    BilibiliFollowButton(
                                        following = uiState.relation.following,
                                        followerMe = uiState.relation.followerMe,
                                        loading = uiState.followLoading,
                                        compact = true,
                                        onClick = {
                                            val cred = credential ?: run {
                                                onLoginRequired()
                                                return@BilibiliFollowButton
                                            }
                                            scope.launch {
                                                uiState.followLoading = true
                                                val previous = uiState.relation
                                                val targetFollow = !uiState.relation.following
                                                uiState.relation = uiState.relation.copy(following = targetFollow)
                                                runCatching {
                                                    api.modifyFollow(
                                                        mid = mid,
                                                        follow = targetFollow,
                                                        credential = cred,
                                                        referer = "https://space.bilibili.com/$mid",
                                                    ).getOrThrow()
                                                    uiState.relation = api.getUserRelation(
                                                        mid = mid,
                                                        credential = cred,
                                                        referer = "https://space.bilibili.com/$mid",
                                                    )
                                                }.onFailure {
                                                    uiState.relation = previous
                                                    Toast.makeText(
                                                        context,
                                                        it.message ?: "关注失败",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                                uiState.followLoading = false
                                            }
                                        },
                                    )
                                } else if (enableSettings) {
                                    IconButton(
                                        onClick = { showSettings = true },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = "设置",
                                            tint = MaterialTheme.colorScheme.onSurface,
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
                                    if (measured != uiState.profileHeaderHeight) {
                                        uiState.profileHeaderHeight = measured
                                    }
                                },
                        ) {
                            UserProfileHeader(
                                profile = uiState.profile,
                                loadError = uiState.loadError,
                                showFollowButton = showFollowButton,
                                relation = uiState.relation,
                                followLoading = uiState.followLoading,
                                onOpenSettings = openSettings,
                                onOpenRelationList = { tab ->
                                    onOpenRelationList(
                                        uiState.profile.name.ifBlank { seedName },
                                        uiState.profile.face.ifBlank { seedFace },
                                        uiState.profile.sign,
                                        tab,
                                    )
                                },
                                onFollowClick = {
                                    val cred = credential ?: run {
                                        onLoginRequired()
                                        return@UserProfileHeader
                                    }
                                    scope.launch {
                                        uiState.followLoading = true
                                        val previous = uiState.relation
                                        val targetFollow = !uiState.relation.following
                                        uiState.relation = uiState.relation.copy(following = targetFollow)
                                        runCatching {
                                            api.modifyFollow(
                                                mid = mid,
                                                follow = targetFollow,
                                                credential = cred,
                                                referer = "https://space.bilibili.com/$mid",
                                            ).getOrThrow()
                                            uiState.relation = api.getUserRelation(
                                                mid = mid,
                                                credential = cred,
                                                referer = "https://space.bilibili.com/$mid",
                                            )
                                        }.onFailure {
                                            uiState.relation = previous
                                            Toast.makeText(
                                                context,
                                                it.message ?: "关注失败",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        uiState.followLoading = false
                                    }
                                },
                                compactVisible = animatedCollapse < 0.5f,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .offset(y = -tabsOverlapOffset),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            UserProfileContentTabBar(
                                scrollPosition = tabScrollPosition,
                                postsSort = uiState.videosOrder,
                                onPostsSortToggle = {
                                    uiState.videosOrder = if (uiState.videosOrder == BiliUserVideoSort.LatestPublish) {
                                        BiliUserVideoSort.MostViews
                                    } else {
                                        BiliUserVideoSort.LatestPublish
                                    }
                                    reloadVideosForSort()
                                },
                                onTabSelected = { tab ->
                                    val sameTab = tab == UserProfileContentTab.entries[pagerState.currentPage]
                                    if (sameTab) {
                                        coroutineScope.launch {
                                            when (tab) {
                                                UserProfileContentTab.Posts -> if (useSingleColumnPosts) {
                                                    postsListState.animateScrollToItem(0)
                                                } else {
                                                    postsStaggeredGridState.animateScrollToItem(0)
                                                }
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

                            uiState.loadError?.let { error ->
                                Text(
                                    text = error,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        if (uiState.loading && uiState.videos.isEmpty() && uiState.dynamics.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.background),
                                beyondViewportPageCount = 0,
                            ) { page ->
                            when (UserProfileContentTab.entries[page]) {
                                UserProfileContentTab.Posts -> {
                                    if (useSingleColumnPosts) {
                                        LazyColumn(
                                            state = postsListState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(
                                                start = HomeFeedSingleColumnHorizontalPadding,
                                                end = HomeFeedSingleColumnHorizontalPadding,
                                                bottom = 24.dp + profileListBottomPadding,
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(HomeFeedSingleColumnSpacing),
                                        ) {
                                            if (uiState.videos.isEmpty()) {
                                                item(key = "posts-empty") {
                                                    ProfileEmptyState(
                                                        title = "暂无投稿",
                                                        body = "该 UP 主还没有发布视频。",
                                                    )
                                                }
                                            } else {
                                                items(uiState.videos, key = { it.bvid }) { video ->
                                                    VideoFeedCard(
                                                        video = video,
                                                        playStream = playUrls[video.bvid],
                                                        coordinator = coordinator,
                                                        onClick = { onVideoClick(video) },
                                                        onEnsurePlayStream = { onEnsurePlayStream(video) },
                                                        overlayMetaOnCover = true,
                                                        showAuthorInfo = false,
                                                    )
                                                }
                                                if (uiState.videosLoadingMore) {
                                                    item(key = "posts-loading") {
                                                        ProfileLoadingMoreIndicator()
                                                    }
                                                }
                                                if (!uiState.videosHasMore && uiState.videos.isNotEmpty()) {
                                                    item(key = "posts-end") {
                                                        ProfileListEndHint()
                                                    }
                                                }
                                            }
                                            item(key = "posts-scroll-fill") {
                                                Spacer(Modifier.fillParentMaxHeight())
                                            }
                                        }
                                    } else {
                                        LazyVerticalStaggeredGrid(
                                            columns = StaggeredGridCells.Fixed(2),
                                            state = postsStaggeredGridState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(
                                                start = HomeFeedGridHorizontalPadding,
                                                end = HomeFeedGridHorizontalPadding,
                                                bottom = 24.dp + profileListBottomPadding,
                                            ),
                                            horizontalArrangement = Arrangement.spacedBy(HomeFeedGridSpacing),
                                            verticalItemSpacing = HomeFeedGridSpacing,
                                        ) {
                                            if (uiState.videos.isEmpty()) {
                                                item(key = "posts-empty", span = StaggeredGridItemSpan.FullLine) {
                                                    ProfileEmptyState(
                                                        title = "暂无投稿",
                                                        body = "该 UP 主还没有发布视频。",
                                                    )
                                                }
                                            } else {
                                                items(uiState.videos, key = { it.bvid }) { video ->
                                                    VideoFeedCard(
                                                        video = video,
                                                        playStream = playUrls[video.bvid],
                                                        coordinator = coordinator,
                                                        onClick = { onVideoClick(video) },
                                                        onEnsurePlayStream = { onEnsurePlayStream(video) },
                                                        gridStyle = true,
                                                        showAuthorInfo = false,
                                                    )
                                                }
                                                if (uiState.videosLoadingMore) {
                                                    item(key = "posts-loading", span = StaggeredGridItemSpan.FullLine) {
                                                        ProfileLoadingMoreIndicator()
                                                    }
                                                }
                                                if (!uiState.videosHasMore && uiState.videos.isNotEmpty()) {
                                                    item(key = "posts-end", span = StaggeredGridItemSpan.FullLine) {
                                                        ProfileListEndHint()
                                                    }
                                                }
                                            }
                                            item(
                                                key = "posts-scroll-fill",
                                                span = StaggeredGridItemSpan.FullLine,
                                            ) {
                                                val fillHeight = uiState.profileHeaderHeight.coerceAtLeast(320.dp)
                                                Spacer(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .height(fillHeight),
                                                )
                                            }
                                        }
                                    }
                                }

                                UserProfileContentTab.Dynamics -> {
                                    LazyColumn(
                                        state = dynamicsListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(DynamicFeedPageBackground),
                                        contentPadding = PaddingValues(
                                            top = 8.dp,
                                            bottom = 24.dp + profileListBottomPadding,
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (uiState.dynamics.isEmpty()) {
                                            item(key = "dynamics-empty") {
                                                ProfileEmptyState(
                                                    title = if (uiState.dynamicsLoadingMore) "加载中" else "暂无动态",
                                                    body = when {
                                                        uiState.dynamicsLoadingMore -> "正在读取 TA 的动态…"
                                                        else -> "下拉刷新后会从动态接口重新加载。"
                                                    },
                                                )
                                            }
                                        } else {
                                            items(uiState.dynamics, key = { it.id }) { item ->
                                                DynamicFeedCard(
                                                    item = item,
                                                    onVideoClick = onVideoClick,
                                                    onDynamicClick = onDynamicClick,
                                                    onLinkClick = { link ->
                                                        webReader = BiliWebReaderState(
                                                            url = link.url,
                                                            title = link.title,
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .padding(horizontal = 12.dp),
                                                )
                                            }
                                            if (uiState.dynamicsLoadingMore) {
                                                item(key = "dynamics-loading") {
                                                    ProfileLoadingMoreIndicator()
                                                }
                                            }
                                            if (!uiState.dynamicsHasMore && uiState.dynamics.isNotEmpty()) {
                                                item(key = "dynamics-end") {
                                                    ProfileListEndHint()
                                                }
                                            }
                                        }
                                        item(key = "dynamics-scroll-fill") {
                                            Spacer(Modifier.fillParentMaxHeight())
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
    onOpenSettings: (() -> Unit)? = null,
    onOpenRelationList: ((UserRelationTab) -> Unit)? = null,
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
                    ProfileCoverBanner(
                        coverUrls = profile.displayTopPhotos,
                        modifier = Modifier.fillMaxSize(),
                        onOpenSettings = if (onOpenSettings != null && compactVisible) onOpenSettings else null,
                    )
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

                val profileCardShape = RoundedCornerShape(ProfileHeaderCardRadius)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = -ProfileHeaderCardCoverOverlap)
                        .zIndex(1f)
                        .border(
                            width = ProfileHeaderCardBorderWidth,
                            color = ProfileHeaderCardBorderColor,
                            shape = profileCardShape,
                        ),
                    shape = profileCardShape,
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
                                bottom = ProfileHeaderCardContentInset,
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
                            .padding(start = 12.dp, top = ProfileHeaderCardContentInset),
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
                            ProfileStatItem(
                                label = "关注",
                                value = profile.following,
                                onClick = onOpenRelationList?.let { { it(UserRelationTab.Following) } },
                            )
                            ProfileStatItem(
                                label = "粉丝",
                                value = profile.follower,
                                onClick = onOpenRelationList?.let { { it(UserRelationTab.Followers) } },
                            )
                            ProfileStatItem("获赞", profile.likes)
                            ProfileStatItem("投稿", profile.videoCount)
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
private fun ProfileCoverBanner(
    coverUrls: List<String>,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val coverImages = remember(coverUrls) { BiliViewerImage.profileCoverImages(coverUrls) }
    var viewerOpen by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf(0) }
    val coverPagerState = rememberPagerState(pageCount = { coverImages.size.coerceAtLeast(1) })
    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = modifier) {
        if (coverImages.isNotEmpty()) {
            if (coverImages.size == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            viewerIndex = 0
                            viewerOpen = true
                        },
                ) {
                    val image = coverImages[0]
                    RemoteImage(
                        url = image.largeUrl,
                        fallbackUrls = image.downloadUrls.drop(1),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            } else {
                HorizontalPager(
                    state = coverPagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val image = coverImages[page]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                viewerIndex = page
                                viewerOpen = true
                            },
                    ) {
                        RemoteImage(
                            url = image.largeUrl,
                            fallbackUrls = image.downloadUrls.drop(1),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    coverImages.indices.forEach { index ->
                        val selected = coverPagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (selected) 7.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.95f)
                                    else Color.White.copy(alpha = 0.45f),
                                ),
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarTopInset + 28.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.82f),
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        if (onOpenSettings != null) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 4.dp, end = 6.dp)
                    .size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (viewerOpen && coverImages.isNotEmpty()) {
        BiliFullscreenImageViewer(
            images = coverImages,
            initialIndex = viewerIndex.coerceIn(0, coverImages.lastIndex),
            onDismiss = { viewerOpen = false },
        )
    }
}

@Composable
private fun UserProfileContentTabBar(
    scrollPosition: Float,
    postsSort: BiliUserVideoSort,
    onPostsSortToggle: () -> Unit,
    onTabSelected: (UserProfileContentTab) -> Unit,
) {
    val tabs = UserProfileContentTab.entries
    val showPostsSort = scrollPosition.roundToInt() == UserProfileContentTab.Posts.ordinal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SlidingTextTabs(
            labels = tabs.map { it.label },
            scrollPosition = scrollPosition,
            onTabSelected = { index -> onTabSelected(tabs[index]) },
            modifier = Modifier.weight(1f),
        )
        if (showPostsSort) {
            ProfileVideoSortToggle(
                selected = postsSort,
                onToggle = onPostsSortToggle,
            )
        }
    }
}

@Composable
private fun ProfileVideoSortToggle(
    selected: BiliUserVideoSort,
    onToggle: () -> Unit,
) {
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle,
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProfileVideoSortLinesIcon(tint = metaColor)
        Text(
            text = selected.toggleLabel,
            fontSize = 11.sp,
            color = metaColor,
        )
    }
}

@Composable
private fun ProfileVideoSortLinesIcon(
    modifier: Modifier = Modifier,
    tint: Color,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .width(10.dp)
                    .height(1.2.dp)
                    .background(tint, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun DynamicFeedCard(
    item: BiliDynamicItem,
    onVideoClick: (BiliVideoItem) -> Unit,
    onDynamicClick: (BiliDynamicItem) -> Unit,
    onLinkClick: (BiliDynamicLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDetail = item.canOpenDetail
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DynamicFeedCardBackground)
            .then(
                if (openDetail) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onDynamicClick(item) },
                    )
                } else {
                    Modifier
                },
            )
            .padding(12.dp),
    ) {
        if (item.text.isNotBlank()) {
            BiliCommentText(
                text = item.text,
                emoticons = item.emoticons,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val hasBodyContent = item.video != null ||
            item.imageUrls.isNotEmpty() ||
            item.link != null
        if (item.origin != null) {
            DynamicOriginBlock(
                origin = item.origin,
                onVideoClick = onVideoClick,
                onLinkClick = onLinkClick,
                modifier = Modifier.padding(top = if (item.text.isNotBlank()) 10.dp else 0.dp),
            )
        } else if (hasBodyContent) {
            DynamicContentBlock(
                text = "",
                emoticons = emptyMap(),
                video = item.video,
                imageUrls = item.imageUrls,
                link = if (item.video != null) null else item.link,
                onVideoClick = onVideoClick,
                onLinkClick = onLinkClick,
                modifier = Modifier.padding(top = if (item.text.isNotBlank()) 10.dp else 0.dp),
            )
        } else if (item.text.isBlank()) {
            Text(
                text = "该动态暂无预览内容",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .background(DynamicFeedPageBackground)
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
            emoticons = origin.emoticons,
            video = origin.video,
            imageUrls = origin.imageUrls,
            link = if (origin.video != null) null else origin.link,
            onVideoClick = onVideoClick,
            onLinkClick = onLinkClick,
            modifier = Modifier.padding(top = if (origin.authorName.isNotBlank()) 6.dp else 0.dp),
        )
    }
}

@Composable
private fun DynamicContentBlock(
    text: String,
    emoticons: Map<String, String>,
    video: BiliVideoItem?,
    imageUrls: List<String>,
    link: BiliDynamicLink?,
    onVideoClick: (BiliVideoItem) -> Unit,
    onLinkClick: (BiliDynamicLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (text.isNotBlank()) {
            BiliCommentText(
                text = text,
                emoticons = emoticons,
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
            BiliImageGrid(
                images = remember(imageUrls) { BiliViewerImage.fromUrls(imageUrls) },
                modifier = Modifier.padding(
                    top = when {
                        text.isNotBlank() || video != null || link != null -> 10.dp
                        else -> 0.dp
                    },
                ),
            )
        }
    }
}

@Composable
private fun DynamicVideoCard(
    video: BiliVideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            RemoteImage(
                url = video.coverUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (video.durationSeconds > 0) {
                Text(
                    text = formatVideoDurationLabel(video.durationSeconds),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
        if (video.title.isNotBlank()) {
            Text(
                text = video.title,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val metaParts = buildList {
            if (video.viewCount > 0L) add("${formatBiliCount(video.viewCount)}播放")
            if (video.danmakuCount > 0L) add("${formatBiliCount(video.danmakuCount)}弹幕")
        }
        if (metaParts.isNotEmpty()) {
            Text(
                text = metaParts.joinToString(" · "),
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
        if (!item.ipLocation.isNullOrBlank()) {
            Text(
                text = "IP 属地：${item.ipLocation}",
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
        )
        if (level > 0) {
            BiliUserLevelIcon(level = level)
        }
    }
}

@Composable
private fun ProfileStatItem(
    label: String,
    value: Long,
    onClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        },
    ) {
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
