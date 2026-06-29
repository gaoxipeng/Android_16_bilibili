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
import com.example.bilibili.data.DanmakuSettings
import kotlin.math.abs

private const val FixedDanmakuDurationMs = 4_000L
private const val ScrollBaseDurationMs = 7_000L
private const val ScrollPerCharDurationMs = 120L
private const val SpawnLookaheadMs = 120L
private const val ReopenPrimeMaxCount = 8
private const val ReopenPrimeMaxAheadMs = 12_000L

internal fun danmakuColor(argb: Int): Color {
    val value = argb and 0xFFFFFF
    return Color(
        red = ((value shr 16) and 0xFF) / 255f,
        green = ((value shr 8) and 0xFF) / 255f,
        blue = (value and 0xFF) / 255f,
    )
}

internal fun danmakuFontSize(
    fontSize: Int,
    videoHeightDp: Float,
    fontSizePercent: Int = 100,
): TextUnit {
    val scale = (videoHeightDp / 210f).coerceIn(0.75f, 1.35f)
    val size = fontSize * 0.52f * scale * (fontSizePercent / 100f)
    return size.coerceIn(10f, 28f).sp
}

internal fun danmakuScrollDurationMs(
    item: BiliDanmakuItem,
    speedMultiplier: Float = 1f,
): Long = ((ScrollBaseDurationMs + item.content.length * ScrollPerCharDurationMs)
    .coerceIn(5_000L, 14_000L) * speedMultiplier.coerceAtLeast(0.1f))
    .toLong()

private fun spawnIndexForPosition(items: List<BiliDanmakuItem>, positionMs: Long): Int {
    if (items.isEmpty()) return 0
    val index = items.indexOfFirst { it.timeMs >= positionMs }
    return if (index < 0) items.size else index
}

private data class ActiveDanmaku(
    val item: BiliDanmakuItem,
    val track: Int,
    val animStartDisplayTimeMs: Long,
    val layout: TextLayoutResult,
    val textWidthPx: Float,
    val lineHeightPx: Float,
)

private class DanmakuSpawnContext(
    val items: List<BiliDanmakuItem>,
    val activeDanmaku: MutableList<ActiveDanmaku>,
    val spawnedIds: MutableSet<Int>,
    val trackReleaseTimes: FloatArray,
    val scrollAreaHeightPx: Float,
    val speedMultiplier: Float,
    val measureDanmaku: (BiliDanmakuItem) -> ActiveDanmaku?,
) {
    var nextIndex: Int = 0

    fun resetTimeline(positionMs: Long) {
        activeDanmaku.clear()
        spawnedIds.clear()
        trackReleaseTimes.fill(0f)
        nextIndex = spawnIndexForPosition(items, positionMs)
    }

    fun primeUpcoming(positionMs: Long, displayTimeMs: Long, maxCount: Int = ReopenPrimeMaxCount) {
        var primed = 0
        while (nextIndex < items.size && primed < maxCount) {
            val item = items[nextIndex]
            if (item.timeMs > positionMs + ReopenPrimeMaxAheadMs) break
            if (trySpawn(item, displayTimeMs)) {
                primed++
            }
            nextIndex++
        }
    }

    fun spawnDue(displayTimeMs: Long) {
        while (nextIndex < items.size && items[nextIndex].timeMs <= displayTimeMs + SpawnLookaheadMs) {
            val item = items[nextIndex]
            nextIndex++
            trySpawn(item, displayTimeMs)
        }
    }

    private fun trySpawn(item: BiliDanmakuItem, animStartDisplayTimeMs: Long): Boolean {
        if (item.id in spawnedIds) return false
        spawnedIds += item.id
        val measured = measureDanmaku(item) ?: return false
        val mode = BiliDanmakuMode.from(item.mode) ?: BiliDanmakuMode.Scroll
        val active = if (mode == BiliDanmakuMode.Bottom || mode == BiliDanmakuMode.Top) {
            measured.copy(track = -1, animStartDisplayTimeMs = animStartDisplayTimeMs)
        } else {
            val durationSec = danmakuScrollDurationMs(item, speedMultiplier) / 1000f
            val maxTracks = (scrollAreaHeightPx / measured.lineHeightPx)
                .toInt()
                .coerceIn(1, DANMAKU_TRACK_COUNT)
            val track = assignDanmakuTrack(
                trackReleaseTimes = trackReleaseTimes,
                maxTracks = maxTracks,
                currentTimeSec = animStartDisplayTimeMs / 1000f,
                durationSec = durationSec,
            )
            measured.copy(track = track, animStartDisplayTimeMs = animStartDisplayTimeMs)
        }
        activeDanmaku += active
        return true
    }
}

@Composable
fun DanmakuOverlay(
    items: List<BiliDanmakuItem>,
    positionMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float = 1f,
    topInset: Dp = 0.dp,
    bottomReserve: Dp = 46.dp,
    enabled: Boolean,
    settings: DanmakuSettings = DanmakuSettings(),
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val activeDanmaku = remember { mutableStateListOf<ActiveDanmaku>() }
    val spawnedIds = remember { mutableSetOf<Int>() }
    val trackReleaseTimes = remember { FloatArray(DANMAKU_TRACK_COUNT) }
    var displayTimeMs by remember { mutableLongStateOf(positionMs) }
    var anchorPositionMs by remember { mutableLongStateOf(positionMs) }
    var anchorRealtimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var frameTick by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val topInsetPx = with(density) { topInset.toPx() }
        val bottomReservePx = with(density) { bottomReserve.toPx() }
        val fullScrollAreaHeightPx = (heightPx - topInsetPx - bottomReservePx).coerceAtLeast(1f)
        val scrollAreaHeightPx = fullScrollAreaHeightPx *
            (settings.displayAreaPercent / 100f).coerceIn(0.1f, 1f)
        val opacityFactor = (settings.opacityPercent / 100f).coerceIn(0.1f, 1f)
        val speedMultiplier = settings.speedLevel.durationMultiplier
        val videoHeightDp = maxHeight.value
        val maxTextWidth = widthPx.toInt().coerceAtLeast(1)

        fun measureDanmaku(item: BiliDanmakuItem): ActiveDanmaku? {
            val fontSize = danmakuFontSize(
                fontSize = item.fontSize,
                videoHeightDp = videoHeightDp,
                fontSizePercent = settings.fontSizePercent,
            )
            val baseColor = danmakuColor(item.colorArgb)
            val textStyle = TextStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                color = baseColor.copy(alpha = baseColor.alpha * opacityFactor),
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.72f * opacityFactor),
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
                animStartDisplayTimeMs = 0L,
                layout = layout,
                textWidthPx = layout.size.width.toFloat(),
                lineHeightPx = fontSize.value * density.density * 1.35f,
            )
        }

        val spawnContext = remember(items, scrollAreaHeightPx, speedMultiplier, settings.fontSizePercent, settings.opacityPercent) {
            DanmakuSpawnContext(
                items = items,
                activeDanmaku = activeDanmaku,
                spawnedIds = spawnedIds,
                trackReleaseTimes = trackReleaseTimes,
                scrollAreaHeightPx = scrollAreaHeightPx,
                speedMultiplier = speedMultiplier,
                measureDanmaku = { item -> measureDanmaku(item) },
            )
        }

        fun syncDisplayClock(currentPositionMs: Long, playing: Boolean) {
            anchorPositionMs = currentPositionMs
            anchorRealtimeMs = System.currentTimeMillis()
            if (!playing) {
                displayTimeMs = currentPositionMs
            }
        }

        fun resetAndPrime(currentPositionMs: Long) {
            syncDisplayClock(currentPositionMs, isPlaying)
            displayTimeMs = currentPositionMs
            spawnContext.resetTimeline(currentPositionMs)
            spawnContext.primeUpcoming(currentPositionMs, currentPositionMs)
        }

        LaunchedEffect(enabled, items) {
            if (!enabled || items.isEmpty()) {
                activeDanmaku.clear()
                spawnedIds.clear()
                return@LaunchedEffect
            }
            resetAndPrime(positionMs)
        }

        LaunchedEffect(settings, enabled) {
            if (!enabled || items.isEmpty()) return@LaunchedEffect
            resetAndPrime(positionMs)
        }

        LaunchedEffect(positionMs, enabled) {
            if (!enabled || items.isEmpty()) return@LaunchedEffect
            if (abs(positionMs - anchorPositionMs) > 1_500L) {
                resetAndPrime(positionMs)
            } else {
                syncDisplayClock(positionMs, isPlaying)
            }
        }

        LaunchedEffect(isPlaying, enabled, items, widthPx, heightPx, playbackSpeed, scrollAreaHeightPx, speedMultiplier) {
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

                    spawnContext.spawnDue(displayTimeMs)

                    activeDanmaku.removeAll { active ->
                        val elapsed = displayTimeMs - active.animStartDisplayTimeMs
                        if (elapsed < 0L) return@removeAll false
                        val mode = BiliDanmakuMode.from(active.item.mode) ?: BiliDanmakuMode.Scroll
                        when (mode) {
                            BiliDanmakuMode.Bottom, BiliDanmakuMode.Top ->
                                elapsed > (FixedDanmakuDurationMs * speedMultiplier).toLong()
                            BiliDanmakuMode.ReverseScroll, BiliDanmakuMode.Scroll ->
                                elapsed > danmakuScrollDurationMs(active.item, speedMultiplier)
                        }
                    }

                    frameTick++
                }
            }
        }

        if (!enabled) return@BoxWithConstraints

        val tick = frameTick
        val timeMs = displayTimeMs

        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            @Suppress("UNUSED_VARIABLE")
            val currentTimeMs = timeMs

            activeDanmaku.forEach { active ->
                val item = active.item
                val elapsed = (currentTimeMs - active.animStartDisplayTimeMs).coerceAtLeast(0L)

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
                        val duration = danmakuScrollDurationMs(item, speedMultiplier).toFloat()
                        val progress = (elapsed / duration).coerceIn(0f, 1f)
                        -active.textWidthPx + (widthPx + active.textWidthPx) * progress
                    }
                    BiliDanmakuMode.Scroll -> {
                        val duration = danmakuScrollDurationMs(item, speedMultiplier).toFloat()
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
