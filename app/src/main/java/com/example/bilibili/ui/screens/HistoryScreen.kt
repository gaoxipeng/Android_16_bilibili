package com.example.bilibili.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BiliHistoryCursor
import com.example.bilibili.data.BiliHistoryItem
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.ui.BindFeedTabReselectEffect
import com.example.bilibili.ui.LocalFeedTabReselectController
import com.example.bilibili.ui.MainTab
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.UpAuthorBadge
import com.example.bilibili.ui.components.VideoCoverBottomScrim
import com.example.bilibili.ui.components.VideoCoverOverlayText
import com.example.bilibili.ui.format.formatBiliHistorySectionLabel
import com.example.bilibili.ui.format.formatBiliHistoryViewTime
import com.example.bilibili.ui.format.formatHistoryDurationBadge
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private data class HistoryEntry(
    val item: BiliHistoryItem,
    val sectionLabel: String,
    val showSectionHeader: Boolean,
)

private enum class HistorySwipeAnchor { Closed, Open }

private val HistoryDeleteActionColor = Color(0xFFE35D5B)
private val HistoryDeleteActionWidth = 72.dp
private val HistorySectionHeaderHeight = 32.dp
private val HistoryCoverWidth = 136.dp
private val HistoryCoverHeight = 77.dp

private fun buildHistoryEntries(items: List<BiliHistoryItem>): List<HistoryEntry> {
    if (items.isEmpty()) return emptyList()
    val entries = mutableListOf<HistoryEntry>()
    var currentSection: String? = null
    items.forEach { item ->
        val section = formatBiliHistorySectionLabel(item.viewAtSeconds)
        val isFirstInSection = section != currentSection
        if (isFirstInSection) currentSection = section
        entries += HistoryEntry(
            item = item,
            sectionLabel = section,
            showSectionHeader = isFirstInSection,
        )
    }
    return entries
}

private fun resolveStickyHistorySectionLabel(
    listState: LazyListState,
    entries: List<HistoryEntry>,
    sectionHeaderHeightPx: Int,
): String? {
    if (entries.isEmpty()) return null
    val topRowIndex = listState.layoutInfo.visibleItemsInfo
        .mapNotNull { info -> info.index.takeIf { it in entries.indices } }
        .minOrNull() ?: return null
    val sectionLabel = entries[topRowIndex].sectionLabel
    val firstRowIndex = entries.indexOfFirst { entry ->
        entry.sectionLabel == sectionLabel && entry.showSectionHeader
    }
    if (firstRowIndex < 0) return null
    val firstVisible = listState.firstVisibleItemIndex
    val scrollOffset = listState.firstVisibleItemScrollOffset
    val showSticky = when {
        firstVisible > firstRowIndex -> true
        firstVisible == firstRowIndex -> scrollOffset >= sectionHeaderHeightPx
        else -> false
    }
    return if (showSticky) sectionLabel else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    loggedIn: Boolean,
    onLoginClick: () -> Unit,
    onVideoClick: (BiliVideoItem, progressSeconds: Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    pullRefreshState: PullToRefreshState = rememberPullToRefreshState(),
    showEmbeddedPullRefreshIndicator: Boolean = false,
    onPullRefreshingChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val sectionHeaderHeightPx = remember(density) {
        with(density) { HistorySectionHeaderHeight.roundToPx() }
    }

    var items by remember { mutableStateOf<List<BiliHistoryItem>>(emptyList()) }
    var cursor by remember { mutableStateOf<BiliHistoryCursor?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var openedSwipeKid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshing) {
        onPullRefreshingChange(refreshing)
    }

    suspend fun loadHistory(reset: Boolean, scrollToTopWhenDone: Boolean = false) {
        val cred = credential ?: return
        if (reset) {
            loading = true
            error = null
            openedSwipeKid = null
        } else {
            if (loadingMore || cursor?.hasMore != true) return
            loadingMore = true
        }
        try {
            val page = api.getWatchHistory(
                credential = cred,
                max = if (reset) 0L else cursor?.max ?: 0L,
                viewAt = if (reset) 0L else cursor?.viewAt ?: 0L,
                business = if (reset) "" else cursor?.business.orEmpty(),
            )
            if (reset) {
                items = page.items
            } else {
                items = (items + page.items).distinctBy { it.kid }
            }
            cursor = page.cursor
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (reset) {
                error = e.message ?: "加载失败"
                items = emptyList()
            }
        } finally {
            loading = false
            loadingMore = false
            refreshing = false
            if (scrollToTopWhenDone) {
                listState.animateScrollToItem(0)
            }
        }
    }

    fun triggerPullRefresh() {
        refreshing = true
        scope.launch { loadHistory(reset = true, scrollToTopWhenDone = true) }
    }

    LaunchedEffect(loggedIn, credential?.dedeUserId) {
        if (loggedIn && credential != null) {
            loadHistory(reset = true)
        } else {
            items = emptyList()
            cursor = null
            openedSwipeKid = null
        }
    }

    LaunchedEffect(listState, cursor?.hasMore, loggedIn) {
        if (!loggedIn) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            if (total <= 0 || last < total - 4) null else last
        }
            .distinctUntilChanged()
            .filterNotNull()
            .collect { loadHistory(reset = false) }
    }

    if (!loggedIn) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("登录后查看观看历史", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onLoginClick) {
                    Text("打开哔哩哔哩登录")
                }
            }
        }
        return
    }

    val entries = remember(items) { buildHistoryEntries(items) }
    val stickySection by remember(entries, listState, sectionHeaderHeightPx) {
        derivedStateOf {
            resolveStickyHistorySectionLabel(listState, entries, sectionHeaderHeightPx)
        }
    }
    val listTopInset = contentPadding.calculateTopPadding()
    val feedTabReselectController = LocalFeedTabReselectController.current

    if (feedTabReselectController != null) {
        BindFeedTabReselectEffect(
            tab = MainTab.History,
            controller = feedTabReselectController,
            listState = listState,
            onRefresh = ::triggerPullRefresh,
        )
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = ::triggerPullRefresh,
        state = pullRefreshState,
        indicator = {
            if (showEmbeddedPullRefreshIndicator) {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(2f),
                    isRefreshing = refreshing,
                    state = pullRefreshState,
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            loading && items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null && items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
            items.isEmpty() && !loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "暂无观看历史",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = listTopInset,
                            bottom = contentPadding.calculateBottomPadding() + 88.dp,
                        ),
                    ) {
                        items(
                            items = entries,
                            key = { it.item.kid },
                        ) { entry ->
                            Column(
                                modifier = Modifier.animateItem(
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                ),
                            ) {
                                if (entry.showSectionHeader) {
                                    HistorySectionHeader(entry.sectionLabel)
                                }
                                HistorySwipeItemRow(
                                    item = entry.item,
                                    openedSwipeKid = openedSwipeKid,
                                    onOpenedSwipeKidChange = { openedSwipeKid = it },
                                    onClick = {
                                        onVideoClick(entry.item.toVideoItem(), entry.item.progressSeconds)
                                    },
                                    onDelete = {
                                        val cred = credential ?: return@HistorySwipeItemRow false
                                        val deleted = api.deleteWatchHistory(entry.item.kid, cred)
                                        if (deleted) {
                                            items = items.filterNot { it.kid == entry.item.kid }
                                            if (openedSwipeKid == entry.item.kid) {
                                                openedSwipeKid = null
                                            }
                                        } else {
                                            Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                                        }
                                        deleted
                                    },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                                )
                            }
                        }
                        if (loadingMore) {
                            item(key = "history-loading-more") {
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
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(listTopInset)
                            .background(Color.White)
                            .zIndex(1f),
                    )
                    stickySection?.let { label ->
                        HistoryStickySectionHeader(
                            label = label,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = listTopInset)
                                .zIndex(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 2.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun HistoryStickySectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    HistorySectionHeader(
        label = label,
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistorySwipeItemRow(
    item: BiliHistoryItem,
    openedSwipeKid: String?,
    onOpenedSwipeKidChange: (String?) -> Unit,
    onClick: () -> Unit,
    onDelete: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val deleteActionWidthPx = remember(density) {
        with(density) { HistoryDeleteActionWidth.toPx() }
    }
    val scope = rememberCoroutineScope()
    var rowWidthPx by remember(item.kid) { mutableFloatStateOf(0f) }
    var deleting by remember(item.kid) { mutableStateOf(false) }
    val dismissOffset = remember(item.kid) { Animatable(0f) }
    val dragState = remember(item.kid, deleteActionWidthPx, density) {
        AnchoredDraggableState(
            initialValue = HistorySwipeAnchor.Closed,
            anchors = DraggableAnchors {
                HistorySwipeAnchor.Closed at 0f
                HistorySwipeAnchor.Open at -deleteActionWidthPx
            },
            positionalThreshold = { distance -> distance * 0.35f },
            velocityThreshold = { with(density) { 120.dp.toPx() } },
            snapAnimationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMedium,
            ),
            decayAnimationSpec = exponentialDecay(),
        )
    }
    val currentOpenedSwipeKid by rememberUpdatedState(openedSwipeKid)
    LaunchedEffect(openedSwipeKid, item.kid) {
        if (openedSwipeKid != item.kid && dragState.currentValue != HistorySwipeAnchor.Closed) {
            dragState.animateTo(HistorySwipeAnchor.Closed)
        }
    }
    LaunchedEffect(dragState, item.kid) {
        snapshotFlow { dragState.currentValue }
            .distinctUntilChanged()
            .collect { value ->
                when (value) {
                    HistorySwipeAnchor.Open -> {
                        if (currentOpenedSwipeKid != item.kid) {
                            onOpenedSwipeKidChange(item.kid)
                        }
                    }
                    HistorySwipeAnchor.Closed -> {
                        if (currentOpenedSwipeKid == item.kid) {
                            onOpenedSwipeKidChange(null)
                        }
                    }
                }
            }
    }
    LaunchedEffect(dragState, item.kid) {
        snapshotFlow {
            dragState.requireOffset() to dragState.isAnimationRunning
        }
            .distinctUntilChanged()
            .collect { (offset, animating) ->
                if (
                    !animating &&
                    offset < 0f &&
                    currentOpenedSwipeKid != null &&
                    currentOpenedSwipeKid != item.kid
                ) {
                    onOpenedSwipeKidChange(item.kid)
                }
            }
    }
    val swipeOffsetPx = if (deleting) dismissOffset.value else dragState.requireOffset()
    val isRevealed = dragState.currentValue == HistorySwipeAnchor.Open
    val canOpenVideo = !deleting && !isRevealed && swipeOffsetPx == 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .onSizeChanged { rowWidthPx = it.width.toFloat() },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(HistoryDeleteActionWidth)
                .fillMaxHeight()
                .background(HistoryDeleteActionColor)
                .clickable(
                    enabled = !deleting,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (rowWidthPx <= 0f) return@clickable
                        scope.launch {
                            deleting = true
                            dismissOffset.snapTo(dragState.requireOffset())
                            dismissOffset.animateTo(
                                targetValue = -rowWidthPx,
                                animationSpec = tween(durationMillis = 260),
                            )
                            val deleted = onDelete()
                            if (!deleted) {
                                dismissOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 200),
                                )
                                deleting = false
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipeOffsetPx
                    clip = true
                }
                .anchoredDraggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = !deleting,
                )
                .background(Color.White)
                .clickable(
                    enabled = canOpenVideo,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            HistoryThumbnail(
                coverUrl = item.coverUrl,
                progressSeconds = item.progressSeconds,
                durationSeconds = item.durationSeconds,
                modifier = Modifier
                    .width(HistoryCoverWidth)
                    .height(HistoryCoverHeight),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .height(HistoryCoverHeight),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.title.ifBlank { "未命名视频" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (item.authorName.isNotBlank()) {
                        Row(
                            modifier = Modifier.height(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UpAuthorBadge()
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = item.authorName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = formatBiliHistoryViewTime(item.viewAtSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryThumbnail(
    coverUrl: String,
    progressSeconds: Int,
    durationSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val progressFraction = when {
        durationSeconds <= 0 -> 0f
        progressSeconds <= 0 -> 0f
        progressSeconds >= durationSeconds -> 1f
        else -> progressSeconds.toFloat() / durationSeconds.toFloat()
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(6.dp)),
    ) {
        RemoteImage(
            url = coverUrl,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        VideoCoverBottomScrim(
            coverUrl = coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter),
        )
        if (progressFraction in 0.001f..0.999f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxSize()
                        .background(Color(0xFF00AEEC)),
                )
            }
        }
        VideoCoverOverlayText(
            text = formatHistoryDurationBadge(progressSeconds, durationSeconds),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp),
        )
    }
}

private fun BiliHistoryItem.toVideoItem(): BiliVideoItem =
    BiliVideoItem(
        bvid = bvid,
        aid = aid,
        cid = cid,
        title = title,
        coverUrl = coverUrl,
        authorName = authorName,
        authorMid = authorMid,
        viewCount = 0L,
        danmakuCount = 0L,
        likeCount = 0L,
        durationSeconds = durationSeconds,
    )
