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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
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
import com.example.bilibili.ui.components.ActionMenuOneRowHeight
import com.example.bilibili.ui.components.ActionMenuOverlay
import com.example.bilibili.ui.components.ActionMenuRequest
import com.example.bilibili.ui.components.ActionMenuRow
import com.example.bilibili.ui.components.UpAuthorBadge
import com.example.bilibili.ui.format.formatBiliHistorySectionLabel
import com.example.bilibili.ui.format.formatBiliHistoryViewTime
import com.example.bilibili.ui.format.formatHistoryDurationBadge
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private data class HistoryEntry(
    val item: BiliHistoryItem,
    val sectionLabel: String,
    val showSectionHeader: Boolean,
)

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
    val inlineLabelVisible = listState.firstVisibleItemIndex == firstRowIndex &&
        listState.firstVisibleItemScrollOffset == 0
    return if (inlineLabelVisible) null else sectionLabel
}

internal data class HistoryActionMenuRequest(
    val kid: String,
    val anchorBoundsInRoot: Rect,
)

class HistoryMenuController {
    internal var request by mutableStateOf<HistoryActionMenuRequest?>(null)
    internal var visible by mutableStateOf(false)
    internal var onDelete: ((String) -> Unit)? = null

    fun open(
        kid: String,
        anchorBoundsInRoot: Rect,
        onDelete: (String) -> Unit,
    ) {
        request = HistoryActionMenuRequest(kid, anchorBoundsInRoot)
        this.onDelete = onDelete
        visible = true
    }

    fun dismiss() {
        visible = false
    }
}

@Composable
fun rememberHistoryMenuController(): HistoryMenuController = remember { HistoryMenuController() }

@Composable
fun HistoryMenuOverlay(
    controller: HistoryMenuController,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    ActionMenuOverlay(
        activeRequest = controller.request?.let {
            ActionMenuRequest(anchorBoundsInRoot = it.anchorBoundsInRoot)
        },
        menuVisible = controller.visible,
        menuHeight = ActionMenuOneRowHeight,
        onDismiss = { controller.dismiss() },
        backdrop = backdrop,
        zIndex = 50f,
        modifier = modifier,
    ) {
        ActionMenuRow(
            label = "删除记录",
            onClick = {
                val kid = controller.request?.kid ?: return@ActionMenuRow
                controller.dismiss()
                controller.onDelete?.invoke(kid)
            },
        )
    }
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
    menuController: HistoryMenuController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var items by remember { mutableStateOf<List<BiliHistoryItem>>(emptyList()) }
    var cursor by remember { mutableStateOf<BiliHistoryCursor?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadHistory(reset: Boolean) {
        val cred = credential ?: return
        if (reset) {
            loading = true
            error = null
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
        }
    }

    LaunchedEffect(loggedIn, credential?.dedeUserId) {
        if (loggedIn && credential != null) {
            loadHistory(reset = true)
        } else {
            items = emptyList()
            cursor = null
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
    val stickySection by remember(entries, listState) {
        derivedStateOf { resolveStickyHistorySectionLabel(listState, entries) }
    }
    val listTopInset = contentPadding.calculateTopPadding()
    val feedTabReselectController = LocalFeedTabReselectController.current

    if (feedTabReselectController != null) {
        BindFeedTabReselectEffect(
            tab = MainTab.History,
            controller = feedTabReselectController,
            listState = listState,
            onRefresh = {
                refreshing = true
                scope.launch { loadHistory(reset = true) }
            },
        )
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch { loadHistory(reset = true) }
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
                            val showInlineSection = entry.showSectionHeader &&
                                stickySection != entry.sectionLabel
                            if (showInlineSection) {
                                HistorySectionHeader(entry.sectionLabel)
                            }
                            HistoryItemRow(
                                item = entry.item,
                                onOpenMenu = { bounds ->
                                    menuController.open(entry.item.kid, bounds) { kid ->
                                        val cred = credential ?: return@open
                                        scope.launch {
                                            val deleted = api.deleteWatchHistory(kid, cred)
                                            if (deleted) {
                                                items = items.filterNot { it.kid == kid }
                                            } else {
                                                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    onVideoClick(entry.item.toVideoItem(), entry.item.progressSeconds)
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                            )
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

@Composable
private fun HistoryItemRow(
    item: BiliHistoryItem,
    onOpenMenu: (Rect) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        HistoryThumbnail(
            coverUrl = item.coverUrl,
            progressSeconds = item.progressSeconds,
            durationSeconds = item.durationSeconds,
            modifier = Modifier
                .width(128.dp)
                .height(72.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .height(72.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 20.dp)
                    .align(Alignment.CenterStart),
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        )
                        Spacer(Modifier.width(4.dp))
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
            var menuAnchor by remember { mutableStateOf(Rect.Zero) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .onGloballyPositioned { coordinates ->
                        menuAnchor = coordinates.boundsInRoot()
                    }
                    .clickable { onOpenMenu(menuAnchor) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(
                text = formatHistoryDurationBadge(progressSeconds, durationSeconds),
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
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
