package com.example.bilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.data.BiliCommentItem
import com.example.bilibili.data.BiliCommentPage
import com.example.bilibili.data.BiliCommentPicture
import com.example.bilibili.data.BiliCommentSort
import com.example.bilibili.data.BiliUserProfile
import com.example.bilibili.ui.format.formatBiliCommentTime
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.theme.BiliPink

val BiliCommentRowOuterStart = 18.dp
val BiliCommentAvatarSize = 34.dp
val BiliCommentAuthorFontSize = 13.sp

sealed interface BiliCommentListEntry {
    val stableKey: String
}

data class BiliCommentRootEntry(
    val comment: BiliCommentItem,
) : BiliCommentListEntry {
    override val stableKey: String = "root-${comment.id}"
}

data class BiliCommentReplyEntry(
    val rootId: Long,
    val reply: BiliCommentItem,
) : BiliCommentListEntry {
    override val stableKey: String = "reply-${rootId}-${reply.id}"
}

data class BiliCommentReplyFooterEntry(
    val rootId: Long,
    val remainingCount: Long,
    val isLoading: Boolean,
    val isFirstExpand: Boolean,
) : BiliCommentListEntry {
    override val stableKey: String = "reply-footer-$rootId"
}

fun resolveShownCommentReplies(
    comment: BiliCommentItem,
    loadedSubReplies: Map<Long, List<BiliCommentItem>>,
): List<BiliCommentItem> =
    (comment.replies + loadedSubReplies[comment.id].orEmpty()).distinctBy { it.id }

fun buildBiliCommentListEntries(
    comments: List<BiliCommentItem>,
    loadedSubReplies: Map<Long, List<BiliCommentItem>>,
    expandedCommentRoots: Set<Long>,
    subRepliesEnd: Set<Long>,
    subRepliesLoading: Set<Long>,
): List<BiliCommentListEntry> = buildList {
    for (comment in comments) {
        add(BiliCommentRootEntry(comment))
        if (comment.replyCount <= 0L) continue
        val shownReplies = resolveShownCommentReplies(comment, loadedSubReplies)
        shownReplies.forEach { reply ->
            add(BiliCommentReplyEntry(comment.id, reply))
        }
        val hasMoreReplies = comment.replyCount > shownReplies.size && comment.id !in subRepliesEnd
        if (hasMoreReplies) {
            val remainingCount = (comment.replyCount - shownReplies.size).coerceAtLeast(0L)
            val isFirstExpand = shownReplies.isEmpty() || comment.id !in expandedCommentRoots
            add(
                BiliCommentReplyFooterEntry(
                    rootId = comment.id,
                    remainingCount = remainingCount,
                    isLoading = comment.id in subRepliesLoading,
                    isFirstExpand = isFirstExpand,
                ),
            )
        }
    }
}

fun findAutoLoadSubReplyRootId(
    entries: List<BiliCommentListEntry>,
    layoutInfo: LazyListLayoutInfo,
): Long? {
    val total = layoutInfo.totalItemsCount
    if (total == 0) return null
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return null
    if (lastVisibleIndex < total - 2) return null
    for (index in lastVisibleIndex downTo (lastVisibleIndex - 2).coerceAtLeast(0)) {
        when (val entry = entries.getOrNull(index)) {
            is BiliCommentReplyFooterEntry -> {
                if (!entry.isLoading && !entry.isFirstExpand) return entry.rootId
            }
            is BiliCommentReplyEntry -> {
                val next = entries.getOrNull(index + 1)
                if (next is BiliCommentReplyFooterEntry && !next.isLoading && !next.isFirstExpand) {
                    return next.rootId
                }
            }
            else -> Unit
        }
    }
    return null
}

fun resolveCommentsEnd(
    page: BiliCommentPage,
    mergedCount: Int,
    previousCount: Int,
    expectedTotal: Long = 0L,
): Boolean {
    val total = page.totalCount.takeIf { it > 0L } ?: expectedTotal
    if (!page.nextCursor.isNullOrBlank()) {
        if (total > 0L && mergedCount.toLong() >= total) return true
        return false
    }
    if (page.comments.isEmpty() && previousCount > 0) return true
    if (mergedCount == previousCount && page.comments.isNotEmpty()) return true
    if (total > 0L && mergedCount.toLong() >= total) return true
    return page.isEnd
}

fun shouldShowCommentText(content: String, pictures: List<BiliCommentPicture>): Boolean {
    if (content.isBlank()) return false
    if (pictures.isEmpty()) return true
    val trimmed = content.trim()
    return trimmed != "[图片]" && trimmed != "图片评论"
}

@Composable
fun BiliCommentRow(
    comment: BiliCommentItem,
    depth: Int = 0,
    onAuthorClick: (BiliUserProfile) -> Unit = {},
    onCommentImageClick: (List<BiliCommentPicture>, Int, Rect) -> Unit = { _, _, _ -> },
) {
    val rowStart = BiliCommentRowOuterStart + (depth * 24).dp
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
                .size(BiliCommentAvatarSize)
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
                    fontSize = BiliCommentAuthorFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (comment.level > 0) {
                    BiliUserLevelIcon(level = comment.level)
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
fun BiliCommentLoadMoreRow(
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
            fontSize = BiliCommentAuthorFontSize,
            color = BiliPink,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

@Composable
fun BiliCommentReplyFooterRow(
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
                fontSize = BiliCommentAuthorFontSize,
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
                fontSize = BiliCommentAuthorFontSize,
                color = BiliPink,
                modifier = Modifier.clickable(onClick = onClick),
            )
        }
    }
}

@Composable
fun BiliCommentSortToggle(
    selected: BiliCommentSort,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
    Row(
        modifier = modifier
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
        BiliCommentSortLinesIcon(tint = metaColor)
        Text(
            text = selected.toggleLabel,
            fontSize = 11.sp,
            color = metaColor,
        )
    }
}

@Composable
private fun BiliCommentSortLinesIcon(
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
