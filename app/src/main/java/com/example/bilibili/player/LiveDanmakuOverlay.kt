package com.example.bilibili.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliDanmakuItem
import com.example.bilibili.data.DanmakuSettings
import kotlin.math.max

private const val LIVE_SCROLL_DURATION_MS = 8_000L
private const val LIVE_TRACK_COUNT = 14

private data class LiveActiveDanmaku(
    val item: BiliDanmakuItem,
    val track: Int,
    val startMs: Long,
    val layout: TextLayoutResult,
    val textWidthPx: Float,
)

@Composable
fun LiveDanmakuOverlay(
    items: List<BiliDanmakuItem>,
    enabled: Boolean,
    settings: DanmakuSettings = DanmakuSettings(),
    modifier: Modifier = Modifier,
) {
    if (!enabled) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val activeDanmaku = remember { mutableStateListOf<LiveActiveDanmaku>() }
    val trackReleaseTimes = remember { FloatArray(LIVE_TRACK_COUNT) }
    var consumedCount by remember { mutableIntStateOf(0) }
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(items.size) {
        while (consumedCount < items.size) {
            val item = items[consumedCount]
            consumedCount++
            val nowSec = System.currentTimeMillis() / 1000f
            val track = trackReleaseTimes.indices.minByOrNull { trackReleaseTimes[it] } ?: 0
            if (trackReleaseTimes[track] > nowSec) continue
            val fontSize = danmakuFontSize(
                fontSize = item.fontSize,
                videoHeightDp = 220f,
                fontSizePercent = settings.fontSizePercent,
            )
            val opacityFactor = (settings.opacityPercent / 100f).coerceIn(0.1f, 1f)
            val baseColor = danmakuColor(item.colorArgb)
            val layout = textMeasurer.measure(
                text = item.content,
                style = TextStyle(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    color = baseColor.copy(alpha = baseColor.alpha * opacityFactor),
                    shadow = Shadow(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f * opacityFactor),
                        offset = Offset(1f, 1f),
                        blurRadius = 2f,
                    ),
                ),
                maxLines = 1,
            )
            val durationSec = LIVE_SCROLL_DURATION_MS / 1000f
            trackReleaseTimes[track] = nowSec + durationSec * 0.35f
            activeDanmaku += LiveActiveDanmaku(
                item = item,
                track = track,
                startMs = System.currentTimeMillis(),
                layout = layout,
                textWidthPx = layout.size.width.toFloat(),
            )
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            withFrameNanos { frameTick++ }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val trackLineHeightPx = with(density) { DanmakuTrackLineHeightDp.toPx() }
        val nowMs = System.currentTimeMillis()
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick

        activeDanmaku.removeAll { active ->
            nowMs - active.startMs >= LIVE_SCROLL_DURATION_MS
        }

        Canvas(Modifier.fillMaxSize()) {
            activeDanmaku.forEach { active ->
                val elapsed = nowMs - active.startMs
                val progress = (elapsed.toFloat() / LIVE_SCROLL_DURATION_MS).coerceIn(0f, 1f)
                val left = widthPx - (widthPx + active.textWidthPx) * progress
                val top = active.track * trackLineHeightPx + trackLineHeightPx * 0.1f
                drawText(
                    textLayoutResult = active.layout,
                    topLeft = Offset(left, top.coerceAtMost(max(0f, heightPx - trackLineHeightPx))),
                )
            }
        }
    }
}
