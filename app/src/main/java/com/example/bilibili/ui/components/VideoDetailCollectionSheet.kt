package com.example.bilibili.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BiliUgcSeason
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BiliVideoPage
import com.example.bilibili.ui.liquidglass.LiquidMenuBorderWidth
import com.example.bilibili.ui.liquidglass.SurfaceLiquidMenuCard
import com.example.bilibili.ui.liquidglass.liquidMenuBorderColor
import com.example.bilibili.ui.theme.BiliPink
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private val CollectionPanelCompanionGap = 8.dp
private val CollectionPanelCornerRadius = 14.dp
private val CollectionPanelHorizontalMargin = 16.dp
private val CollectionPanelHeaderHeight = 38.dp
private val CollectionPanelMaxListHeight = 320.dp
private val CollectionPanelEpisodeRowHeight = 46.dp

private fun collectionPanelSurfaceColor(isLightTheme: Boolean) =
    if (isLightTheme) {
        Color.White.copy(alpha = 0.94f)
    } else {
        Color(0xFF1C1C1E).copy(alpha = 0.88f)
    }

@Composable
@OptIn(ExperimentalHazeMaterialsApi::class)
fun VideoDetailCollectionSheet(
    visible: Boolean,
    sheetTitle: String,
    anchorBoundsInRoot: Rect,
    menuBackdrop: Backdrop,
    hazeState: HazeState? = null,
    ugcSeason: BiliUgcSeason? = null,
    highlightSectionId: Long? = null,
    pages: List<BiliVideoPage> = emptyList(),
    currentBvid: String = "",
    currentCid: Long = 0L,
    authorName: String = "",
    authorMid: Long = 0L,
    onDismiss: () -> Unit,
    onUgcEpisodeClick: (BiliVideoItem) -> Unit = {},
    onPartClick: (BiliVideoPage) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var displayedAnchor by remember { mutableStateOf(Rect.Zero) }

    if (!visible && displayedAnchor == Rect.Zero) return

    BackHandler(enabled = visible, onBack = onDismiss)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(1000f),
    ) {
        val density = LocalDensity.current
        val marginPx = with(density) { CollectionPanelHorizontalMargin.toPx() }
        val gapPx = with(density) { CollectionPanelCompanionGap.toPx() }
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val fallbackAnchor = remember(screenWidthPx, screenHeightPx, marginPx) {
            val top = screenHeightPx * 0.38f
            val height = with(density) { 44.dp.toPx() }
            Rect(
                left = marginPx,
                top = top,
                right = screenWidthPx - marginPx,
                bottom = top + height,
            )
        }
        val anchor = when {
            anchorBoundsInRoot.width > 0f -> anchorBoundsInRoot
            displayedAnchor.width > 0f -> displayedAnchor
            visible -> fallbackAnchor
            else -> return@BoxWithConstraints
        }

        LaunchedEffect(visible, anchorBoundsInRoot) {
            if (visible && anchorBoundsInRoot.width > 0f) {
                displayedAnchor = anchorBoundsInRoot
            }
        }
        val panelWidthPx = screenWidthPx - marginPx * 2f
        val headerHeightPx = with(density) { CollectionPanelHeaderHeight.toPx() }
        val verticalPaddingPx = with(density) { 12.dp.toPx() }
        val maxListHeightPx = with(density) { CollectionPanelMaxListHeight.toPx() }
        val panelHeightPx = headerHeightPx + maxListHeightPx + verticalPaddingPx

        val hasSpaceBelow = anchor.bottom + gapPx + panelHeightPx <= screenHeightPx - marginPx
        val targetY = if (hasSpaceBelow) {
            anchor.bottom + gapPx
        } else {
            anchor.top - gapPx - panelHeightPx
        }
        val maxY = (screenHeightPx - panelHeightPx - marginPx).coerceAtLeast(marginPx)
        val panelOffsetY = targetY.coerceIn(marginPx, maxY).roundToInt()
        val panelOffsetX = marginPx.roundToInt()
        val panelWidth = with(density) { panelWidthPx.toDp() }
        val isLightTheme = !isSystemInDarkTheme()
        val panelSurfaceColor = collectionPanelSurfaceColor(isLightTheme)
        val panelShape = RoundedCornerShape(CollectionPanelCornerRadius)
        val panelBorderColor = liquidMenuBorderColor(isLightTheme)
        val panelHazeStyle = HazeMaterials.thick(containerColor = panelSurfaceColor)

        val listState = rememberLazyListState()

        LaunchedEffect(
            visible,
            ugcSeason,
            pages,
            currentBvid,
            currentCid,
            highlightSectionId,
        ) {
            if (!visible) return@LaunchedEffect
            val targetIndex = when {
                ugcSeason != null -> computeUgcSeasonEpisodeListIndex(
                    ugcSeason = ugcSeason,
                    highlightSectionId = highlightSectionId,
                    currentBvid = currentBvid,
                    currentCid = currentCid,
                )
                pages.isNotEmpty() -> pages.indexOfFirst { it.cid == currentCid }.takeIf { it >= 0 }
                else -> null
            } ?: return@LaunchedEffect
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it > targetIndex }
            val listHeightPx = maxListHeightPx
            val itemHeightPx = with(density) { CollectionPanelEpisodeRowHeight.roundToPx() }
            val scrollOffset = -(listHeightPx / 2 - itemHeightPx / 2).roundToInt()
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = scrollOffset,
            )
        }

        if (visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(120)) + slideInVertically(tween(140)) { it / 4 },
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .offset { IntOffset(panelOffsetX, panelOffsetY) }
                .width(panelWidth),
        ) {
            if (hazeState != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(panelShape)
                        .hazeEffect(state = hazeState) {
                            style = panelHazeStyle
                            blurRadius = ActionMenuBlurRadius
                        }
                        .border(LiquidMenuBorderWidth, panelBorderColor, panelShape)
                        .padding(vertical = 6.dp),
                ) {
                    CollectionSheetPanelContent(
                        sheetTitle = sheetTitle,
                        listState = listState,
                        ugcSeason = ugcSeason,
                        highlightSectionId = highlightSectionId,
                        pages = pages,
                        currentBvid = currentBvid,
                        currentCid = currentCid,
                        authorName = authorName,
                        authorMid = authorMid,
                        onDismiss = onDismiss,
                        onUgcEpisodeClick = onUgcEpisodeClick,
                        onPartClick = onPartClick,
                    )
                }
            } else {
                SurfaceLiquidMenuCard(
                    modifier = Modifier.fillMaxWidth(),
                    backdrop = menuBackdrop,
                    cornerRadius = CollectionPanelCornerRadius,
                    useMenuGlassStyle = true,
                    surfaceColor = panelSurfaceColor,
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    CollectionSheetPanelContent(
                        sheetTitle = sheetTitle,
                        listState = listState,
                        ugcSeason = ugcSeason,
                        highlightSectionId = highlightSectionId,
                        pages = pages,
                        currentBvid = currentBvid,
                        currentCid = currentCid,
                        authorName = authorName,
                        authorMid = authorMid,
                        onDismiss = onDismiss,
                        onUgcEpisodeClick = onUgcEpisodeClick,
                        onPartClick = onPartClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionSheetPanelContent(
    sheetTitle: String,
    listState: LazyListState,
    ugcSeason: BiliUgcSeason?,
    highlightSectionId: Long?,
    pages: List<BiliVideoPage>,
    currentBvid: String,
    currentCid: Long,
    authorName: String,
    authorMid: Long,
    onDismiss: () -> Unit,
    onUgcEpisodeClick: (BiliVideoItem) -> Unit,
    onPartClick: (BiliVideoPage) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CollectionPanelHeaderHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = sheetTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = CollectionPanelMaxListHeight),
    ) {
        if (ugcSeason != null) {
            val sections = ugcSeason.sectionsToShow(highlightSectionId)
            val showSectionHeaders = sections.size > 1
            sections.forEach { section ->
                if (showSectionHeaders && section.title.isNotBlank()) {
                    item(key = "section-header-${section.id}") {
                        Text(
                            text = section.title,
                            modifier = Modifier.padding(
                                horizontal = 14.dp,
                                vertical = 6.dp,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(
                    items = section.episodes,
                    key = { _, episode ->
                        "ugc-${section.id}-${episode.bvid}-${episode.id}"
                    },
                ) { index, episode ->
                    CollectionSheetEpisodeRow(
                        index = index + 1,
                        title = episode.title,
                        durationSeconds = episode.durationSeconds,
                        selected = episodeMatchesCurrent(
                            episodeBvid = episode.bvid,
                            episodeCid = episode.cid,
                            currentBvid = currentBvid,
                            currentCid = currentCid,
                        ),
                        onClick = {
                            onDismiss()
                            onUgcEpisodeClick(
                                episode.toVideoItem(authorName, authorMid),
                            )
                        },
                    )
                }
            }
        } else {
            itemsIndexed(
                items = pages,
                key = { _, page -> "part-${page.page}-${page.cid}" },
            ) { index, page ->
                CollectionSheetEpisodeRow(
                    index = page.page.takeIf { it > 0 } ?: (index + 1),
                    title = page.title.ifBlank { "P${index + 1}" },
                    durationSeconds = page.durationSeconds,
                    selected = page.cid == currentCid,
                    onClick = {
                        onDismiss()
                        onPartClick(page)
                    },
                )
            }
        }
    }
}

@Composable
private fun CollectionSheetEpisodeRow(
    index: Int,
    title: String,
    durationSeconds: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val titleColor = if (selected) BiliPink else MaterialTheme.colorScheme.onSurface
    val indexBackground = if (selected) {
        BiliPink.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CollectionPanelEpisodeRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 26.dp, height = 20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(indexBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                fontWeight = FontWeight.Medium,
                color = titleColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { "未命名" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (durationSeconds > 0) {
                Text(
                    text = formatCollectionSheetDuration(durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun episodeMatchesCurrent(
    episodeBvid: String,
    episodeCid: Long,
    currentBvid: String,
    currentCid: Long,
): Boolean {
    if (currentBvid.isNotBlank() && episodeBvid == currentBvid) return true
    return currentCid > 0L && episodeCid == currentCid
}

private fun computeUgcSeasonEpisodeListIndex(
    ugcSeason: BiliUgcSeason,
    highlightSectionId: Long?,
    currentBvid: String,
    currentCid: Long,
): Int? {
    val sections = ugcSeason.sectionsToShow(highlightSectionId)
    val showSectionHeaders = sections.size > 1
    var index = 0
    sections.forEach { section ->
        if (showSectionHeaders && section.title.isNotBlank()) {
            index++
        }
        section.episodes.forEach { episode ->
            if (episodeMatchesCurrent(
                    episodeBvid = episode.bvid,
                    episodeCid = episode.cid,
                    currentBvid = currentBvid,
                    currentCid = currentCid,
                )
            ) {
                return index
            }
            index++
        }
    }
    return null
}

private fun formatCollectionSheetDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainSeconds = seconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, remainSeconds)
    } else {
        String.format("%02d:%02d", minutes, remainSeconds)
    }
}
