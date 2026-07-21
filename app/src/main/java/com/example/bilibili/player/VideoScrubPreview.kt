package com.example.bilibili.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.data.BiliVideoShot
import kotlin.math.roundToInt

data class VideoShotTile(
    val imageUrl: String,
    val column: Int,
    val row: Int,
)

fun BiliVideoShot.locateTile(positionMs: Long, durationMs: Long): VideoShotTile? {
    if (images.isEmpty() || totalTiles <= 0) return null
    val thumbnailIndex = if (indexSeconds.isNotEmpty()) {
        // 与 Mac tile(at:) 一致：按秒向下取整，超出时间轴覆盖范围则不显示。
        val positionSec = (positionMs / 1000L).toInt().coerceAtLeast(0)
        val lastCoveredSecond = indexSeconds.last()
        if (positionSec > lastCoveredSecond) return null
        val index = indexSeconds.indexOfLast { it <= positionSec }.coerceAtLeast(0)
        if (index >= totalTiles) return null
        index
    } else if (durationMs > 0L) {
        ((positionMs.toDouble() / durationMs.toDouble()) * totalTiles)
            .roundToInt()
            .coerceIn(0, totalTiles - 1)
    } else {
        0
    }
    val imageIndex = thumbnailIndex / tilesPerImage
    val tileInImage = thumbnailIndex % tilesPerImage
    val imageUrl = images.getOrNull(imageIndex) ?: return null
    return VideoShotTile(
        imageUrl = imageUrl,
        column = tileInImage % tileColumns,
        row = tileInImage / tileColumns,
    )
}

@Composable
fun VideoScrubPreviewOverlay(
    visible: Boolean,
    positionMs: Long,
    durationMs: Long,
    progressFraction: Float,
    videoShot: BiliVideoShot?,
    modifier: Modifier = Modifier,
    previewWidth: Dp = 176.dp,
    previewAspectRatio: Float? = null,
    refererUrl: String = com.example.bilibili.data.BilibiliEndpoints.HOME,
) {
    if (!visible) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fraction = progressFraction.coerceIn(0f, 1f)
        val maxOffset = (maxWidth - previewWidth).coerceAtLeast(0.dp)
        val xOffset = maxOffset * fraction
        Column(
            modifier = Modifier
                .width(previewWidth)
                .offset(x = xOffset)
                .align(Alignment.BottomStart),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            videoShot?.locateTile(positionMs, durationMs)?.let { tile ->
                VideoShotTilePreview(
                    tile = tile,
                    videoShot = videoShot,
                    width = previewWidth,
                    previewAspectRatio = previewAspectRatio,
                    refererUrl = refererUrl,
                )
            }
            Text(
                text = "${formatVideoTime(positionMs)} / ${formatVideoTime(durationMs.coerceAtLeast(0L))}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun VideoShotTilePreview(
    tile: VideoShotTile,
    videoShot: BiliVideoShot,
    width: Dp,
    previewAspectRatio: Float?,
    refererUrl: String,
) {
    val metadataAspectRatio = videoShot.tileWidth.toFloat() / videoShot.tileHeight.toFloat().coerceAtLeast(1f)
    var tileBitmap by remember(tile.imageUrl, tile.column, tile.row) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    val aspectRatio = previewAspectRatio ?: tileBitmap?.let { bitmap ->
        bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    } ?: metadataAspectRatio
    val height = width / aspectRatio.coerceIn(0.5f, 2.4f)

    LaunchedEffect(tile, videoShot.tileColumns, videoShot.tileRows, refererUrl) {
        tileBitmap = VideoShotImageLoader.loadTile(tile, videoShot, refererUrl)
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        tileBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = if (previewAspectRatio != null) ContentScale.FillBounds else ContentScale.Crop,
                modifier = Modifier
                    .width(width)
                    .height(height),
            )
        }
    }
}
