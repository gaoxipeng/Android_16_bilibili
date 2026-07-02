package com.example.bilibili.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliCommentPicture
import com.example.bilibili.data.BiliViewerImage

private val CommentImageGridSpacing = 4.dp
private val CommentImageMaxCellSize = 112.dp
private const val CommentImageGridColumns = 3

private fun commentImageGridColumns(count: Int): Int = when (count) {
    1 -> 1
    2 -> 2
    3 -> 3
    4 -> 2
    else -> CommentImageGridColumns
}

@Composable
fun BiliCommentImageStrip(
    pictures: List<BiliCommentPicture>,
    modifier: Modifier = Modifier,
    onOpenViewer: ((index: Int, bounds: Rect) -> Unit)? = null,
) {
    if (pictures.isEmpty()) return
    val boundsByIndex = remember(pictures) { mutableMapOf<Int, Rect>() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        val cellSize = remember(maxWidth) {
            val availableWidth = maxWidth - CommentImageGridSpacing * (CommentImageGridColumns - 1)
            (availableWidth / CommentImageGridColumns).coerceAtMost(CommentImageMaxCellSize)
        }
        val gridColumns = commentImageGridColumns(pictures.size)

        Column(verticalArrangement = Arrangement.spacedBy(CommentImageGridSpacing)) {
            pictures.chunked(gridColumns).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CommentImageGridSpacing),
                ) {
                    row.forEachIndexed { colIndex, picture ->
                        val index = rowIndex * gridColumns + colIndex
                        CommentImageCell(
                            picture = picture,
                            index = index,
                            cellSize = cellSize,
                            boundsByIndex = boundsByIndex,
                            onOpenViewer = onOpenViewer,
                        )
                    }
                    repeat(gridColumns - row.size) {
                        Spacer(Modifier.size(cellSize))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentImageCell(
    picture: BiliCommentPicture,
    index: Int,
    cellSize: Dp,
    boundsByIndex: MutableMap<Int, Rect>,
    onOpenViewer: ((index: Int, bounds: Rect) -> Unit)?,
) {
    val viewerImage = remember(picture.url, picture.width, picture.height) {
        BiliViewerImage.fromCommentPicture(picture)
    }
    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(RoundedCornerShape(6.dp))
            .onGloballyPositioned { coordinates ->
                if (coordinates.isAttached) {
                    boundsByIndex[index] = coordinates.boundsInRoot()
                }
            }
            .then(
                if (onOpenViewer != null) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember(index) { MutableInteractionSource() },
                        onClick = {
                            onOpenViewer(
                                index,
                                boundsByIndex[index] ?: Rect.Zero,
                            )
                        },
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        RemoteImage(
            url = viewerImage.thumbnailUrl,
            fallbackUrls = viewerImage.downloadUrls,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
