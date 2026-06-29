package com.example.bilibili.ui.components.imageviewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliViewerImage
import com.example.bilibili.ui.components.RemoteImage

@Composable
fun BiliImageGrid(
    images: List<BiliViewerImage>,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxDisplayCount: Int = 9,
) {
    if (images.isEmpty()) return

    val displayImages = images.take(maxDisplayCount)
    var viewerOpen by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf(0) }
    var thumbnailBoundsByIndex by remember { mutableStateOf<Map<Int, Rect>>(emptyMap()) }
    var viewerSourceBoundsByIndex by remember { mutableStateOf<Map<Int, Rect>>(emptyMap()) }
    var viewerAnimateOpenFromSource by remember { mutableStateOf(true) }

    fun openImageViewer(index: Int, sourceBounds: Rect?) {
        viewerIndex = index
        if (sourceBounds != null) {
            thumbnailBoundsByIndex = thumbnailBoundsByIndex + (index to sourceBounds)
        }
        viewerSourceBoundsByIndex = thumbnailBoundsByIndex
        viewerAnimateOpenFromSource = sourceBounds != null
        viewerOpen = true
    }

    val gridColumns = when (displayImages.size) {
        1 -> 1
        2 -> 2
        3 -> 3
        4 -> 2
        else -> 3
    }
    val rows = displayImages.chunked(gridColumns)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { image ->
                    val cellIndex = displayImages.indexOf(image)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    openImageViewer(
                                        cellIndex,
                                        thumbnailBoundsByIndex[cellIndex],
                                    )
                                },
                            )
                            .onGloballyPositioned { coordinates ->
                                thumbnailBoundsByIndex =
                                    thumbnailBoundsByIndex + (cellIndex to coordinates.boundsInRoot())
                            },
                    ) {
                        RemoteImage(
                            url = image.thumbnailUrl,
                            fallbackUrls = image.downloadUrls.drop(1),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = contentScale,
                        )
                    }
                }
                repeat(gridColumns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (viewerOpen) {
        BiliFullscreenImageViewer(
            images = displayImages,
            initialIndex = viewerIndex,
            sourceBoundsByIndex = viewerSourceBoundsByIndex,
            animateOpenFromSource = viewerAnimateOpenFromSource,
            onDismiss = {
                viewerOpen = false
                viewerSourceBoundsByIndex = emptyMap()
                viewerAnimateOpenFromSource = true
            },
        )
    }
}
