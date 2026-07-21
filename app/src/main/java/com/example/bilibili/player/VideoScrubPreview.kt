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

/** 对齐 Mac VideoScrubPreviewLayout：竖屏按真实画面比例定框，避免 160×90 元数据把竖屏压扁。 */
private object VideoScrubPreviewLayout {
    val maxLandscapeWidth = 176.dp
    val maxPortraitHeight = 180.dp
    val minPortraitWidth = 72.dp

    fun aspectRatio(
        videoShot: BiliVideoShot?,
        playerAspectRatio: Float?,
    ): Float {
        val player = playerAspectRatio?.takeIf { it.isFinite() && it > 0f }
        if (player != null) {
            // videoshot 元数据常为 160×90；竖屏用真实播放比例。
            if (player < 1f) return player
            val shot = videoShot?.takeIf { it.tileWidth > 0 && it.tileHeight > 0 }
            if (shot != null) {
                return shot.tileWidth.toFloat() / shot.tileHeight.toFloat().coerceAtLeast(1f)
            }
            return player
        }
        val shot = videoShot?.takeIf { it.tileWidth > 0 && it.tileHeight > 0 }
        if (shot != null) {
            return shot.tileWidth.toFloat() / shot.tileHeight.toFloat().coerceAtLeast(1f)
        }
        return 16f / 9f
    }

    fun size(aspectRatio: Float): Pair<Dp, Dp> {
        val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        return if (ratio < 1f) {
            val height = maxPortraitHeight
            val width = (height * ratio).coerceAtLeast(minPortraitWidth)
            width to height
        } else {
            val width = maxLandscapeWidth
            width to (width / ratio)
        }
    }
}

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
    previewAspectRatio: Float? = null,
    refererUrl: String = com.example.bilibili.data.BilibiliEndpoints.HOME,
) {
    if (!visible) return

    val resolvedAspect = remember(videoShot, previewAspectRatio) {
        VideoScrubPreviewLayout.aspectRatio(videoShot, previewAspectRatio)
    }
    val (previewWidth, previewHeight) = remember(resolvedAspect) {
        VideoScrubPreviewLayout.size(resolvedAspect)
    }

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
                    height = previewHeight,
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
    height: Dp,
    refererUrl: String,
) {
    var tileBitmap by remember(tile.imageUrl, tile.column, tile.row) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

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
                // 与 Mac .fill 一致：等比铺满并裁掉雪碧格里的左右黑边，避免 FillBounds 压扁。
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(width)
                    .height(height),
            )
        }
    }
}
