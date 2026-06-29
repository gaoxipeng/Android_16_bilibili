package com.example.bilibili.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.bilibili.ui.liquidglass.liquidSheetSurfaceColor
import com.example.bilibili.data.BiliUgcSeason
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BiliVideoPage
import com.example.bilibili.ui.theme.BiliPink
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first

@Composable
fun VideoDetailCollectionSheet(
    visible: Boolean,
    sheetTitle: String,
    hazeState: HazeState,
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
    BackHandler(enabled = visible, onBack = onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(1000f),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
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
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                val sheetHeight = maxHeight * 0.55f
                val headerHeight = 44.dp
                val listHeight = sheetHeight - headerHeight - ActionMenuCardInset * 2
                val listState = rememberLazyListState()
                val density = LocalDensity.current

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
                    val listHeightPx = with(density) { listHeight.roundToPx() }
                    val itemHeightPx = with(density) { 58.dp.roundToPx() }
                    val scrollOffset = -(listHeightPx / 2 - itemHeightPx / 2)
                    listState.animateScrollToItem(
                        index = targetIndex,
                        scrollOffset = scrollOffset,
                    )
                }

                val isLightTheme = !isSystemInDarkTheme()

                ActionFrostedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetHeight),
                    hazeState = hazeState,
                    effectBlurRadius = ActionSheetBlurRadius,
                    effectContainerColor = liquidSheetSurfaceColor(isLightTheme),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = sheetTitle,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(listHeight),
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
                                                horizontal = 8.dp,
                                                vertical = 8.dp,
                                            ),
                                            fontSize = 13.sp,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 26.dp, height = 20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (selected) {
                        BiliPink.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { "未命名" },
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (durationSeconds > 0) {
                Text(
                    text = formatCollectionSheetDuration(durationSeconds),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
