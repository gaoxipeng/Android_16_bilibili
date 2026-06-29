package com.example.bilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.data.BiliCommentSort
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.theme.BiliPink
import kotlin.math.roundToInt

private val VideoDetailTabBarHeight = 36.dp
private val VideoDetailTabIndicatorHeight = 2.dp

enum class VideoDetailTab(val label: String) {
    Intro("简介"),
    Comments("评论"),
}

@Composable
fun VideoDetailTabBar(
    scrollPosition: Float,
    commentCount: Long,
    commentSort: BiliCommentSort,
    onCommentSortToggle: () -> Unit,
    onTabSelected: (VideoDetailTab) -> Unit,
    onMoreClick: (anchorBounds: Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = BiliPink
    val density = LocalDensity.current
    var tabWidths by remember { mutableStateOf(List(VideoDetailTab.entries.size) { 0.dp }) }
    var tabOffsets by remember { mutableStateOf(List(VideoDetailTab.entries.size) { 0.dp }) }
    val layoutReady = tabWidths.all { it > 0.dp }
    val showCommentSort = scrollPosition.roundToInt() == VideoDetailTab.Comments.ordinal
    val showMoreMenu = !showCommentSort

    val indicatorWidth = remember(scrollPosition, tabWidths) {
        val left = scrollPosition.toInt().coerceIn(0, VideoDetailTab.entries.lastIndex)
        val right = (left + 1).coerceAtMost(VideoDetailTab.entries.lastIndex)
        val fraction = scrollPosition - left
        tabWidths[left] + (tabWidths[right] - tabWidths[left]) * fraction
    }
    val indicatorOffset = remember(scrollPosition, tabOffsets) {
        val left = scrollPosition.toInt().coerceIn(0, VideoDetailTab.entries.lastIndex)
        val right = (left + 1).coerceAtMost(VideoDetailTab.entries.lastIndex)
        val fraction = scrollPosition - left
        tabOffsets[left] + (tabOffsets[right] - tabOffsets[left]) * fraction
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(VideoDetailTabBarHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoDetailTab.entries.forEachIndexed { index, tab ->
                    val selected = scrollPosition.roundToInt()
                        .coerceIn(0, VideoDetailTab.entries.lastIndex) == index
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val width = with(density) { coordinates.size.width.toDp() }
                                val offset = with(density) { coordinates.positionInParent().x.toDp() }
                                if (tabWidths.getOrNull(index) != width || tabOffsets.getOrNull(index) != offset) {
                                    tabWidths = tabWidths.toMutableList().also { it[index] = width }
                                    tabOffsets = tabOffsets.toMutableList().also { it[index] = offset }
                                }
                            }
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when (tab) {
                                VideoDetailTab.Intro -> tab.label
                                VideoDetailTab.Comments -> {
                                    if (commentCount > 0L) {
                                        "${tab.label} ${formatBiliCount(commentCount)}"
                                    } else {
                                        tab.label
                                    }
                                }
                            },
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (layoutReady) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = indicatorOffset)
                        .width(indicatorWidth)
                        .height(VideoDetailTabIndicatorHeight)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent),
                )
            }
        }

        Box(
            modifier = Modifier.size(width = 72.dp, height = VideoDetailTabBarHeight),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (showCommentSort) {
                BiliCommentSortToggle(
                    selected = commentSort,
                    onToggle = onCommentSortToggle,
                )
            } else if (showMoreMenu) {
                VideoDetailMoreChevron(onClick = onMoreClick)
            }
        }
    }
}

@Composable
private fun VideoDetailMoreChevron(
    onClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                boundsInRoot = coordinates.boundsInRoot()
            }
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onClick(boundsInRoot) },
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = "更多",
            tint = metaColor,
            modifier = Modifier.size(20.dp),
        )
    }
}
