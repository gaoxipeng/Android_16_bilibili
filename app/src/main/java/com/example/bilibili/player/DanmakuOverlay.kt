package com.example.bilibili.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.data.BiliDanmakuItem
import com.example.bilibili.data.BiliDanmakuMode
import kotlin.math.abs

private const val FixedDanmakuDurationMs = 4_000L
private const val ScrollBaseDurationMs = 7_000L
private const val ScrollPerCharDurationMs = 120L

internal fun danmakuColor(argb: Int): Color {
    val value = argb and 0xFFFFFF
    return Color(
        red = ((value shr 16) and 0xFF) / 255f,
        green = ((value shr 8) and 0xFF) / 255f,
        blue = (value and 0xFF) / 255f,
    )
}

internal fun danmakuFontSize(fontSize: Int, videoHeightDp: Float): TextUnit {
    val scale = (videoHeightDp / 210f).coerceIn(0.75f, 1.35f)
    val size = fontSize * 0.52f * scale
    return size.coerceIn(12f, 20f).sp
}

internal fun danmakuScrollDurationMs(item: BiliDanmakuItem): Long =
    (ScrollBaseDurationMs + item.content.length * ScrollPerCharDurationMs)
        .coerceIn(5_000L, 14_000L)

private data class ActiveDanmaku(
    val item: BiliDanmakuItem,
    val track: Int,
    val startTimeMs: Long,
    val layout: TextLayoutResult,
    val textWidthPx: Float,
    val lineHeightPx: Float,
)

@Composable
fun DanmakuOverlay(
    items: List<BiliDanmakuItem>,
    positionMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float = 1f,
    topInset: Dp = 0.dp,
    bottomReserve: Dp = 46.dp,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled || items.isEmpty()) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val activeDanmaku = remember { mutableStateListOf<ActiveDanmaku>() }
    val spawnedIds = remember { mutableSetOf<Int>() }
    var nextIndex by remember { mutableIntStateOf(0) }
    val trackReleaseTimes = remember { FloatArray(DANMAKU_TRACK_COUNT) }
    var displayTimeMs by remember { mutableLongStateOf(positionMs) }
    var anchorPositionMs by remember { mutableLongStateOf(positionMs) }
    var anchorRealtimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(items) {
        activeDanmaku.clear()
        spawnedIds.clear()
        nextIndex = 0
        trackReleaseTimes.fill(0f)
    }

    LaunchedEffect(positionMs) {
        if (abs(positionMs - anchorPositionMs) > 1_500L) {
            activeDanmaku.clear()
            spawnedIds.clear()
            nextIndex = items.indexOfFirst { it.timeMs >= positionMs }.coerceAtLeast(0)
            trackReleaseTimes.fill(0f)
        }
        anchorPositionMs = positionMs
        anchorRealtimeMs = System.currentTimeMillis()
        if (!isPlaying) {
            displayTimeMs = positionMs
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val topInsetPx = with(density) { topInset.toPx() }
        val bottomReservePx = with(density) { bottomReserve.toPx() }
        val scrollAreaHeightPx = (heightPx - topInsetPx - bottomReservePx).coerceAtLeast(1f)
        val videoHeightDp = maxHeight.value
        val maxTextWidth = widthPx.toInt().coerceAtLeast(1)

        fun measureDanmaku(item: BiliDanmakuItem): ActiveDanmaku? {
            val fontSize = danmakuFontSize(item.fontSize, videoHeightDp)
            val textStyle = TextStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                color = danmakuColor(item.colorArgb),
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.72f),
                    offset = Offset(1f, 1f),
                    blurRadius = 2f,
                ),
            )
            val layout = textMeasurer.measure(
                text = item.content,
                style = textStyle,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxTextWidth),
            )
            return ActiveDanmaku(
                item = item,
                track = -1,
                startTimeMs = item.timeMs,
                layout = layout,
                textWidthPx = layout.size.width.toFloat(),
                lineHeightPx = fontSize.value * density.density * 1.35f,
            )
        }

        LaunchedEffect(isPlaying, enabled, items, widthPx, heightPx, playbackSpeed) {
            if (!enabled || items.isEmpty()) return@LaunchedEffect
            while (true) {
                withFrameNanos {
                    val now = System.currentTimeMillis()
                    displayTimeMs = if (isPlaying) {
                        anchorPositionMs +
                            ((now - anchorRealtimeMs) * playbackSpeed.coerceAtLeast(0.1f)).toLong()
                    } else {
                        anchorPositionMs
                    }

                    while (nextIndex < items.size && items[nextIndex].timeMs <= displayTimeMs + 120L) {
                        val item = items[nextIndex]
                        nextIndex++
                        if (item.id in spawnedIds) continue
                        spawnedIds += item.id
                        val measured = measureDanmaku(item) ?: continue
                        val mode = BiliDanmakuMode.from(item.mode) ?: BiliDanmakuMode.Scroll
                        val active = if (mode == BiliDanmakuMode.Bottom || mode == BiliDanmakuMode.Top) {
                            measured.copy(track = -1, startTimeMs = item.timeMs)
                        } else {
                            val durationSec = danmakuScrollDurationMs(item) / 1000f
                            val maxTracks = (scrollAreaHeightPx / measured.lineHeightPx)
                                .toInt()
                                .coerceIn(1, DANMAKU_TRACK_COUNT)
                            val track = assignDanmakuTrack(
                                trackReleaseTimes = trackReleaseTimes,
                                maxTracks = maxTracks,
                                currentTimeSec = item.timeMs / 1000f,
                                durationSec = durationSec,
                            )
                            measured.copy(track = track, startTimeMs = item.timeMs)
                        }
                        activeDanmaku += active
                    }

                    activeDanmaku.removeAll { active ->
                        val elapsed = displayTimeMs - active.startTimeMs
                        val mode = BiliDanmakuMode.from(active.item.mode) ?: BiliDanmakuMode.Scroll
                        when (mode) {
                            BiliDanmakuMode.Bottom, BiliDanmakuMode.Top -> elapsed > FixedDanmakuDurationMs
                            BiliDanmakuMode.ReverseScroll, BiliDanmakuMode.Scroll ->
                                elapsed > danmakuScrollDurationMs(active.item)
                        }
                    }

                    frameTick++
                }
            }
        }

        val tick = frameTick
        val timeMs = displayTimeMs

        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            @Suppress("UNUSED_VARIABLE")
            val currentTimeMs = timeMs

            activeDanmaku.forEach { active ->
                val item = active.item
                val elapsed = (currentTimeMs - active.startTimeMs).coerceAtLeast(0L)
                if (elapsed < 0L) return@forEach

                val mode = BiliDanmakuMode.from(item.mode) ?: BiliDanmakuMode.Scroll
                val textHeightPx = active.layout.size.height.toFloat()
                val yPx = when (mode) {
                    BiliDanmakuMode.Bottom -> {
                        (heightPx - bottomReservePx - textHeightPx - with(density) { 4.dp.toPx() })
                            .coerceAtLeast(topInsetPx)
                    }
                    BiliDanmakuMode.Top -> topInsetPx
                    else -> topInsetPx + active.track * active.lineHeightPx
                }
                val xPx = when (mode) {
                    BiliDanmakuMode.Bottom, BiliDanmakuMode.Top ->
                        (widthPx - active.textWidthPx) / 2f
                    BiliDanmakuMode.ReverseScroll -> {
                        val duration = danmakuScrollDurationMs(item).toFloat()
                        val progress = (elapsed / duration).coerceIn(0f, 1f)
                        -active.textWidthPx + (widthPx + active.textWidthPx) * progress
                    }
                    BiliDanmakuMode.Scroll -> {
                        val duration = danmakuScrollDurationMs(item).toFloat()
                        val progress = (elapsed / duration).coerceIn(0f, 1f)
                        widthPx - (widthPx + active.textWidthPx) * progress
                    }
                }
                drawText(
                    textLayoutResult = active.layout,
                    topLeft = Offset(xPx, yPx),
                )
            }
        }
    }
}

private const val DANMAKU_TRACK_COUNT = 18

private fun assignDanmakuTrack(
    trackReleaseTimes: FloatArray,
    maxTracks: Int,
    currentTimeSec: Float,
    durationSec: Float,
): Int {
    val trackCount = maxTracks.coerceIn(1, trackReleaseTimes.size)
    var bestTrack = 0
    var earliestRelease = trackReleaseTimes[0]
    for (index in 1 until trackCount) {
        if (trackReleaseTimes[index] < earliestRelease) {
            earliestRelease = trackReleaseTimes[index]
            bestTrack = index
        }
    }
    trackReleaseTimes[bestTrack] = currentTimeSec + durationSec
    return bestTrack
}
