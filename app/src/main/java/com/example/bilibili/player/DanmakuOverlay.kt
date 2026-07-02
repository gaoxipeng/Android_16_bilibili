package com.example.bilibili.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.bilibili.player.VideoPlaybackCoordinator.DanmakuTimelineSnapshot
import kotlin.math.abs

private const val FixedDanmakuDurationMs = 4_000L
private const val ScrollBaseDurationMs = 7_000L
private const val ScrollPerCharDurationMs = 120L
private const val SpawnLookaheadMs = 120L
private const val SpawnBacklogSkipMs = 900L
private const val MaxSpawnInspectionsPerFrame = 80
private const val DANMAKU_TRACK_COUNT = 18
private const val FIXED_DANMAKU_ROW_COUNT = 8
private const val TRACK_GAP_SEC = 0.15f
private val ScrollDanmakuGapDp = 28.dp
internal val DanmakuTrackLineHeightDp = 26.dp
internal val DanmakuCompactTrackLineHeightDp = 20.dp

private fun passesDensityGate(item: BiliDanmakuItem, items: List<BiliDanmakuItem>): Boolean {
    if (items.size <= 1_200) return true
    val keepRatio = when {
        items.size > 8_000 -> 0.45f
        items.size > 4_000 -> 0.55f
        items.size > 2_000 -> 0.68f
        else -> 0.82f
    }
    return abs(item.id % 100) < (keepRatio * 100f).toInt()
}

private fun scrollTrackEntryBlockSec(
    durationSec: Float,
    textWidthPx: Float,
    screenWidthPx: Float,
    gapPx: Float,
): Float {
    if (screenWidthPx <= 0f) {
        return (durationSec * 0.28f + TRACK_GAP_SEC).coerceIn(0.35f, durationSec * 0.6f)
    }
    return (durationSec * (textWidthPx + gapPx) / (screenWidthPx + textWidthPx) + TRACK_GAP_SEC)
        .coerceIn(0.35f, durationSec * 0.85f)
}

private fun scrollDanmakuLeftPx(
    screenWidthPx: Float,
    textWidthPx: Float,
    elapsedMs: Long,
    durationMs: Long,
): Float {
    val progress = (elapsedMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    return screenWidthPx - (screenWidthPx + textWidthPx) * progress
}

private fun reverseDanmakuLeftPx(
    screenWidthPx: Float,
    textWidthPx: Float,
    elapsedMs: Long,
    durationMs: Long,
): Float {
    val progress = (elapsedMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    return -textWidthPx + (screenWidthPx + textWidthPx) * progress
}

private fun canSpawnScrollOnTrack(
    track: Int,
    activeDanmaku: List<ActiveDanmaku>,
    displayTimeMs: Long,
    screenWidthPx: Float,
    gapPx: Float,
    speedMultiplier: Float,
    reverse: Boolean,
): Boolean {
    for (active in activeDanmaku) {
        if (active.track != track) continue
        val mode = BiliDanmakuMode.from(active.item.mode) ?: continue
        val isReverse = mode == BiliDanmakuMode.ReverseScroll
        if (mode != BiliDanmakuMode.Scroll && !isReverse) continue
        if (reverse != isReverse) continue

        val elapsed = displayTimeMs - active.animStartDisplayTimeMs
        if (elapsed < 0L) continue
        val durationMs = danmakuScrollDurationMs(active.item, speedMultiplier)
        if (elapsed >= durationMs) continue

        if (reverse) {
            val leftPx = reverseDanmakuLeftPx(
                screenWidthPx = screenWidthPx,
                textWidthPx = active.textWidthPx,
                elapsedMs = elapsed,
                durationMs = durationMs,
            )
            if (leftPx < gapPx) return false
        } else {
            val leftPx = scrollDanmakuLeftPx(
                screenWidthPx = screenWidthPx,
                textWidthPx = active.textWidthPx,
                elapsedMs = elapsed,
                durationMs = durationMs,
            )
            val existingRightPx = leftPx + active.textWidthPx
            if (existingRightPx + gapPx <= screenWidthPx) continue
            return false
        }
    }
    return true
}

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
    val topFixedReleaseTimes: FloatArray,
    val bottomFixedReleaseTimes: FloatArray,
    val scrollAreaHeightPx: Float,
    val screenWidthPx: Float,
    val scrollGapPx: Float,
    val trackLineHeightPx: Float,
    val speedMultiplier: Float,
    val measureDanmaku: (BiliDanmakuItem) -> ActiveDanmaku?,
) {
    var nextIndex: Int = 0

    fun resetTimeline(positionMs: Long) {
        activeDanmaku.clear()
        spawnedIds.clear()
        trackReleaseTimes.fill(0f)
        topFixedReleaseTimes.fill(0f)
        bottomFixedReleaseTimes.fill(0f)
        nextIndex = spawnIndexForPosition(items, positionMs)
    }

    fun spawnDue(displayTimeMs: Long, maxInspections: Int = MaxSpawnInspectionsPerFrame) {
        if (
            nextIndex < items.size &&
            items[nextIndex].timeMs < displayTimeMs - SpawnBacklogSkipMs
        ) {
            nextIndex = spawnIndexForPosition(items, displayTimeMs - SpawnLookaheadMs)
        }
        var inspected = 0
        while (
            nextIndex < items.size &&
            items[nextIndex].timeMs <= displayTimeMs + SpawnLookaheadMs &&
            inspected < maxInspections
        ) {
            val item = items[nextIndex]
            nextIndex++
            inspected++
            trySpawn(item, item.timeMs)
        }
    }

    private fun trySpawn(item: BiliDanmakuItem, animStartDisplayTimeMs: Long): Boolean {
        if (item.id in spawnedIds) return false
        if (!passesDensityGate(item, items)) return false
        val measured = measureDanmaku(item) ?: return false
        val mode = BiliDanmakuMode.from(item.mode) ?: BiliDanmakuMode.Scroll
        val currentTimeSec = animStartDisplayTimeMs / 1000f
        val active = when (mode) {
            BiliDanmakuMode.Bottom, BiliDanmakuMode.Top -> {
                val maxRows = fixedDanmakuMaxRows(scrollAreaHeightPx, measured.lineHeightPx)
                val releaseTimes = if (mode == BiliDanmakuMode.Top) {
                    topFixedReleaseTimes
                } else {
                    bottomFixedReleaseTimes
                }
                val durationSec = FixedDanmakuDurationMs * speedMultiplier / 1000f
                val row = assignFixedDanmakuRow(
                    rowReleaseTimes = releaseTimes,
                    maxRows = maxRows,
                    currentTimeSec = currentTimeSec,
                    durationSec = durationSec,
                ) ?: return false
                measured.copy(track = row, animStartDisplayTimeMs = animStartDisplayTimeMs)
            }
            BiliDanmakuMode.ReverseScroll, BiliDanmakuMode.Scroll -> {
                val durationSec = danmakuScrollDurationMs(item, speedMultiplier) / 1000f
                val maxTracks = (scrollAreaHeightPx / trackLineHeightPx)
                    .toInt()
                    .coerceIn(1, DANMAKU_TRACK_COUNT)
                val reverse = mode == BiliDanmakuMode.ReverseScroll
                val durationMs = danmakuScrollDurationMs(item, speedMultiplier)
                val track = assignDanmakuTrack(
                    activeDanmaku = activeDanmaku,
                    displayTimeMs = animStartDisplayTimeMs,
                    trackReleaseTimes = trackReleaseTimes,
                    maxTracks = maxTracks,
                    durationSec = durationSec,
                    textWidthPx = measured.textWidthPx,
                    durationMs = durationMs,
                    screenWidthPx = screenWidthPx,
                    gapPx = scrollGapPx,
                    speedMultiplier = speedMultiplier,
                    reverse = reverse,
                ) ?: return false
                measured.copy(track = track, animStartDisplayTimeMs = animStartDisplayTimeMs)
            }
        }
        spawnedIds += item.id
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
    trackLineHeight: Dp = DanmakuTrackLineHeightDp,
    playbackKey: String? = null,
    danmakuCid: Long = 0L,
    onTimelineSnapshot: ((DanmakuTimelineSnapshot) -> Unit)? = null,
    restoredTimeline: DanmakuTimelineSnapshot? = null,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val activeDanmaku = remember { mutableStateListOf<ActiveDanmaku>() }
    val spawnedIds = remember { mutableSetOf<Int>() }
    val trackReleaseTimes = remember { FloatArray(DANMAKU_TRACK_COUNT) }
    val topFixedReleaseTimes = remember { FloatArray(FIXED_DANMAKU_ROW_COUNT) }
    val bottomFixedReleaseTimes = remember { FloatArray(FIXED_DANMAKU_ROW_COUNT) }
    var displayTimeMs by remember { mutableLongStateOf(positionMs) }
    var anchorPositionMs by remember { mutableLongStateOf(positionMs) }
    var anchorRealtimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastObservedPositionMs by remember { mutableLongStateOf(positionMs) }
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

        val scrollGapPx = with(density) { ScrollDanmakuGapDp.toPx() }
        val trackLineHeightPx = with(density) { trackLineHeight.toPx() }

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
                lineHeightPx = trackLineHeightPx,
            )
        }

        val spawnContext = remember(items, scrollAreaHeightPx, widthPx, scrollGapPx, trackLineHeightPx, speedMultiplier, settings.fontSizePercent, settings.opacityPercent) {
            DanmakuSpawnContext(
                items = items,
                activeDanmaku = activeDanmaku,
                spawnedIds = spawnedIds,
                trackReleaseTimes = trackReleaseTimes,
                topFixedReleaseTimes = topFixedReleaseTimes,
                bottomFixedReleaseTimes = bottomFixedReleaseTimes,
                scrollAreaHeightPx = scrollAreaHeightPx,
                screenWidthPx = widthPx,
                scrollGapPx = scrollGapPx,
                trackLineHeightPx = trackLineHeightPx,
                speedMultiplier = speedMultiplier,
                measureDanmaku = { item -> measureDanmaku(item) },
            )
        }

        fun syncDisplayClock(currentPositionMs: Long, playing: Boolean) {
            anchorPositionMs = currentPositionMs
            anchorRealtimeMs = System.currentTimeMillis()
            lastObservedPositionMs = currentPositionMs
            if (!playing) {
                displayTimeMs = currentPositionMs
            }
        }

        fun reanchorDisplayClock(playing: Boolean) {
            anchorPositionMs = displayTimeMs
            anchorRealtimeMs = System.currentTimeMillis()
            if (!playing) {
                displayTimeMs = anchorPositionMs
            }
        }

        fun resetTimeline(currentPositionMs: Long, restoreSnapshot: DanmakuTimelineSnapshot? = null) {
            syncDisplayClock(currentPositionMs, isPlaying)
            spawnContext.resetTimeline(currentPositionMs)
            if (restoreSnapshot != null) {
                displayTimeMs = restoreSnapshot.displayTimeMs
                anchorPositionMs = restoreSnapshot.anchorPositionMs
                anchorRealtimeMs = restoreSnapshot.anchorRealtimeMs
                spawnedIds.clear()
                spawnedIds.addAll(restoreSnapshot.spawnedIds)
                spawnContext.nextIndex = restoreSnapshot.nextIndex
            } else {
                displayTimeMs = currentPositionMs
            }
        }

        DisposableEffect(playbackKey, danmakuCid, enabled) {
            onDispose {
                if (!enabled || playbackKey.isNullOrBlank() || danmakuCid <= 0L) return@onDispose
                onTimelineSnapshot?.invoke(
                    DanmakuTimelineSnapshot(
                        cid = danmakuCid,
                        displayTimeMs = displayTimeMs,
                        anchorPositionMs = anchorPositionMs,
                        anchorRealtimeMs = anchorRealtimeMs,
                        nextIndex = spawnContext.nextIndex,
                        spawnedIds = spawnedIds.toSet(),
                    ),
                )
            }
        }

        LaunchedEffect(enabled, items, restoredTimeline?.cid, restoredTimeline?.nextIndex) {
            if (!enabled || items.isEmpty()) {
                activeDanmaku.clear()
                spawnedIds.clear()
                trackReleaseTimes.fill(0f)
                topFixedReleaseTimes.fill(0f)
                bottomFixedReleaseTimes.fill(0f)
                return@LaunchedEffect
            }
            resetTimeline(positionMs, restoredTimeline)
        }

        LaunchedEffect(settings, enabled, trackLineHeight) {
            if (!enabled || items.isEmpty()) return@LaunchedEffect
            resetTimeline(positionMs, restoredTimeline)
        }

        LaunchedEffect(playbackSpeed, enabled) {
            if (!enabled || items.isEmpty()) return@LaunchedEffect
            reanchorDisplayClock(isPlaying)
        }

        LaunchedEffect(positionMs, enabled) {
            if (!enabled || items.isEmpty()) return@LaunchedEffect
            val positionJumped = abs(positionMs - lastObservedPositionMs) > 1_500L
            lastObservedPositionMs = positionMs
            if (positionJumped) {
                resetTimeline(positionMs)
            } else if (!isPlaying) {
                syncDisplayClock(positionMs, isPlaying)
            }
        }

        LaunchedEffect(
            isPlaying,
            enabled,
            items,
            widthPx,
            heightPx,
            playbackSpeed,
            scrollAreaHeightPx,
            speedMultiplier,
            settings.fontSizePercent,
            settings.opacityPercent,
            spawnContext,
        ) {
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
                val elapsed = currentTimeMs - active.animStartDisplayTimeMs
                if (elapsed < 0L) return@forEach

                val mode = BiliDanmakuMode.from(item.mode) ?: BiliDanmakuMode.Scroll
                val textHeightPx = active.layout.size.height.toFloat()
                val rowGapPx = active.lineHeightPx
                val fixedPaddingPx = with(density) { 4.dp.toPx() }
                val yPx = when (mode) {
                    BiliDanmakuMode.Bottom -> {
                        (heightPx - bottomReservePx - textHeightPx - fixedPaddingPx -
                            active.track * rowGapPx)
                            .coerceAtLeast(topInsetPx)
                    }
                    BiliDanmakuMode.Top -> topInsetPx + active.track * rowGapPx +
                        (rowGapPx - textHeightPx).coerceAtLeast(0f) / 2f
                    else -> topInsetPx + active.track * rowGapPx +
                        (rowGapPx - textHeightPx).coerceAtLeast(0f) / 2f
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

private fun fixedDanmakuMaxRows(scrollAreaHeightPx: Float, lineHeightPx: Float): Int {
    if (lineHeightPx <= 0f) return 1
    val rowsFromArea = (scrollAreaHeightPx * 0.35f / lineHeightPx).toInt()
    return rowsFromArea.coerceIn(1, FIXED_DANMAKU_ROW_COUNT)
}

private fun assignFixedDanmakuRow(
    rowReleaseTimes: FloatArray,
    maxRows: Int,
    currentTimeSec: Float,
    durationSec: Float,
): Int? {
    val rowCount = maxRows.coerceIn(1, rowReleaseTimes.size)
    val idleRows = (0 until rowCount).filter { rowReleaseTimes[it] <= currentTimeSec }
    if (idleRows.isNotEmpty()) {
        val row = idleRows[(currentTimeSec * 1000f).toLong().mod(idleRows.size.toLong()).toInt()]
        rowReleaseTimes[row] = currentTimeSec + durationSec + TRACK_GAP_SEC
        return row
    }
    var bestRow = 0
    var earliestRelease = rowReleaseTimes[0]
    for (index in 1 until rowCount) {
        if (rowReleaseTimes[index] < earliestRelease) {
            earliestRelease = rowReleaseTimes[index]
            bestRow = index
        }
    }
    if (earliestRelease - currentTimeSec > 0.2f) return null
    rowReleaseTimes[bestRow] = currentTimeSec + durationSec + TRACK_GAP_SEC
    return bestRow
}

private fun assignDanmakuTrack(
    activeDanmaku: List<ActiveDanmaku>,
    displayTimeMs: Long,
    trackReleaseTimes: FloatArray,
    maxTracks: Int,
    durationSec: Float,
    textWidthPx: Float,
    durationMs: Long,
    screenWidthPx: Float,
    gapPx: Float,
    speedMultiplier: Float,
    reverse: Boolean,
): Int? {
    val trackCount = maxTracks.coerceIn(1, trackReleaseTimes.size)
    val currentTimeSec = displayTimeMs / 1000f
    val availableTracks = (0 until trackCount).filter { track ->
        trackReleaseTimes[track] <= currentTimeSec &&
            canSpawnScrollOnTrack(
            track = track,
            activeDanmaku = activeDanmaku,
            displayTimeMs = displayTimeMs,
            screenWidthPx = screenWidthPx,
            gapPx = gapPx,
            speedMultiplier = speedMultiplier,
            reverse = reverse,
        )
    }
    if (availableTracks.isEmpty()) return null

    val track = availableTracks[displayTimeMs.mod(availableTracks.size.toLong()).toInt()]
    val entryBlockSec = scrollTrackEntryBlockSec(durationSec, textWidthPx, screenWidthPx, gapPx)
    trackReleaseTimes[track] = maxOf(trackReleaseTimes[track], currentTimeSec) + entryBlockSec
    return track
}
