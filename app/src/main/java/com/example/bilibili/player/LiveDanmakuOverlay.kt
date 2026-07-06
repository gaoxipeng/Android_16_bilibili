package com.example.bilibili.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliDanmakuItem
import com.example.bilibili.data.DanmakuSettings
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

private const val LIVE_SCROLL_DURATION_MS = 8_000L
private const val LIVE_TRACK_COUNT = 14
private const val LIVE_TRACK_GAP_SEC = 0.15f
private val LiveScrollDanmakuGapDp = 28.dp

private data class LiveActiveDanmaku(
    val item: BiliDanmakuItem,
    val track: Int,
    val startMs: Long,
    val layout: TextLayoutResult,
    val textWidthPx: Float,
    val lineHeightPx: Float,
    val emoticonUrls: List<String> = emptyList(),
)

private fun DrawScope.drawDanmakuLayout(
    layout: TextLayoutResult,
    topLeft: Offset,
    emoticonUrls: List<String>,
    emoticonBitmaps: Map<String, ImageBitmap>,
    alpha: Float,
) {
    drawText(
        textLayoutResult = layout,
        topLeft = topLeft,
        alpha = alpha,
    )
    if (emoticonUrls.isEmpty() || emoticonBitmaps.isEmpty()) return
    layout.placeholderRects.forEachIndexed { index, rect ->
        val bounds = rect ?: return@forEachIndexed
        val url = emoticonUrls.getOrNull(index) ?: return@forEachIndexed
        val bitmap = emoticonBitmaps[url] ?: return@forEachIndexed
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(
                (topLeft.x + bounds.left).roundToInt(),
                (topLeft.y + bounds.top).roundToInt(),
            ),
            dstSize = IntSize(
                bounds.width.roundToInt().coerceAtLeast(1),
                bounds.height.roundToInt().coerceAtLeast(1),
            ),
            alpha = alpha,
        )
    }
}

private fun liveScrollTrackEntryBlockSec(
    durationSec: Float,
    textWidthPx: Float,
    screenWidthPx: Float,
    gapPx: Float,
): Float {
    if (screenWidthPx <= 0f) {
        return (durationSec * 0.28f + LIVE_TRACK_GAP_SEC).coerceIn(0.35f, durationSec * 0.6f)
    }
    return (durationSec * (textWidthPx + gapPx) / (screenWidthPx + textWidthPx) + LIVE_TRACK_GAP_SEC)
        .coerceIn(0.35f, durationSec * 0.85f)
}

private fun canSpawnLiveDanmakuOnTrack(
    track: Int,
    activeDanmaku: List<LiveActiveDanmaku>,
    nowMs: Long,
    screenWidthPx: Float,
    gapPx: Float,
    scrollDurationMs: Long,
): Boolean {
    for (active in activeDanmaku) {
        if (active.track != track) continue
        val elapsed = nowMs - active.startMs
        if (elapsed < 0L || elapsed >= scrollDurationMs) continue
        val leftPx = screenWidthPx - (screenWidthPx + active.textWidthPx) *
            (elapsed.toFloat() / scrollDurationMs.coerceAtLeast(1L))
        val existingRightPx = leftPx + active.textWidthPx
        if (existingRightPx + gapPx <= screenWidthPx) continue
        return false
    }
    return true
}

private fun assignLiveDanmakuTrack(
    activeDanmaku: List<LiveActiveDanmaku>,
    trackReleaseTimes: FloatArray,
    maxTracks: Int,
    nowMs: Long,
    durationSec: Float,
    textWidthPx: Float,
    scrollDurationMs: Long,
    screenWidthPx: Float,
    gapPx: Float,
): Int? {
    val trackCount = maxTracks.coerceIn(1, trackReleaseTimes.size)
    val nowSec = nowMs / 1000f
    val availableTracks = (0 until trackCount).filter { track ->
        trackReleaseTimes[track] <= nowSec &&
            canSpawnLiveDanmakuOnTrack(
                track = track,
                activeDanmaku = activeDanmaku,
                nowMs = nowMs,
                screenWidthPx = screenWidthPx,
                gapPx = gapPx,
                scrollDurationMs = scrollDurationMs,
            )
    }
    if (availableTracks.isEmpty()) return null

    val track = availableTracks[nowMs.mod(availableTracks.size.toLong()).toInt()]
    val entryBlockSec = liveScrollTrackEntryBlockSec(durationSec, textWidthPx, screenWidthPx, gapPx)
    trackReleaseTimes[track] = maxOf(trackReleaseTimes[track], nowSec) + entryBlockSec
    return track
}

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
    val emoticonUrls = remember(items) {
        items.flatMap { item -> item.emoticons.values.map { it.url } }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val emoticonBitmaps = rememberDanmakuEmoticonBitmaps(emoticonUrls)
    val activeDanmaku = remember { mutableStateListOf<LiveActiveDanmaku>() }
    val spawnedIds = remember { mutableSetOf<Int>() }
    val trackReleaseTimes = remember { FloatArray(LIVE_TRACK_COUNT) }
    var frameTick by remember { mutableIntStateOf(0) }
    val speedMultiplier = settings.speedLevel.durationMultiplier
    val scrollDurationMs = (LIVE_SCROLL_DURATION_MS / speedMultiplier.coerceAtLeast(0.1f))
        .toLong()
        .coerceIn(3_000L, 16_000L)

    LaunchedEffect(enabled) {
        if (!enabled) {
            activeDanmaku.clear()
            spawnedIds.clear()
            trackReleaseTimes.fill(0f)
            return@LaunchedEffect
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val scrollAreaHeightPx = heightPx *
            (settings.displayAreaPercent / 100f).coerceIn(0.1f, 1f)
        val trackLineHeightPx = with(density) { DanmakuTrackLineHeightDp.toPx() }
        val trackCount = (scrollAreaHeightPx / trackLineHeightPx)
            .toInt()
            .coerceIn(4, LIVE_TRACK_COUNT)
        val scrollGapPx = with(density) { LiveScrollDanmakuGapDp.toPx() }
        val videoHeightDp = maxHeight.value
        val maxTextWidth = widthPx.toInt().coerceAtLeast(1)
        val opacityFactor = (settings.opacityPercent / 100f).coerceIn(0.1f, 1f)
        val durationSec = scrollDurationMs / 1000f

        LaunchedEffect(items) {
            val currentIds = items.asSequence().map { it.id }.toSet()
            spawnedIds.retainAll(currentIds)
        }

        fun measureDanmaku(item: BiliDanmakuItem): LiveActiveDanmaku? {
            val fontSize = danmakuFontSize(
                fontSize = item.fontSize,
                videoHeightDp = videoHeightDp,
                fontSizePercent = settings.fontSizePercent,
            )
            val emojiSize = fontSize * 1.2f
            val drawableEmoticons = item.emoticons.filterValues { spec ->
                spec.url.isNotBlank() && emoticonBitmaps.containsKey(spec.url)
            }
            val measureText = buildDanmakuMeasureText(
                content = item.content,
                emoticons = drawableEmoticons,
                emojiSize = emojiSize,
                fontSize = fontSize,
            )
            val baseColor = danmakuColor(item.colorArgb)
            val layout = textMeasurer.measure(
                text = measureText.text,
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
                constraints = Constraints(maxWidth = maxTextWidth),
                placeholders = measureText.placeholders,
            )
            return LiveActiveDanmaku(
                item = item,
                track = -1,
                startMs = 0L,
                layout = layout,
                textWidthPx = layout.size.width.toFloat(),
                lineHeightPx = trackLineHeightPx,
                emoticonUrls = measureText.emoticonUrls,
            )
        }

        LaunchedEffect(
            enabled,
            items,
            widthPx,
            scrollAreaHeightPx,
            trackCount,
            scrollDurationMs,
            settings.fontSizePercent,
            settings.opacityPercent,
        ) {
            if (!enabled) return@LaunchedEffect
            while (true) {
                withFrameNanos {
                    frameTick++
                    val nowMs = System.currentTimeMillis()

                    activeDanmaku.removeAll { active ->
                        nowMs - active.startMs >= scrollDurationMs
                    }

                    var spawnedThisFrame = 0
                    for (item in items) {
                        if (spawnedThisFrame >= 6) break
                        if (item.id in spawnedIds) continue
                        val measured = measureDanmaku(item) ?: continue
                        val track = assignLiveDanmakuTrack(
                            activeDanmaku = activeDanmaku,
                            trackReleaseTimes = trackReleaseTimes,
                            maxTracks = trackCount,
                            nowMs = nowMs,
                            durationSec = durationSec,
                            textWidthPx = measured.textWidthPx,
                            scrollDurationMs = scrollDurationMs,
                            screenWidthPx = widthPx,
                            gapPx = scrollGapPx,
                        ) ?: break

                        spawnedIds += item.id
                        activeDanmaku += measured.copy(
                            track = track,
                            startMs = nowMs,
                        )
                        spawnedThisFrame++
                    }
                }
            }
        }

        val nowMs = System.currentTimeMillis()
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick
        val loadedEmoticonCount = emoticonBitmaps.size

        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val emoticonBitmapVersion = loadedEmoticonCount
            activeDanmaku.forEach { active ->
                val elapsed = nowMs - active.startMs
                val progress = (elapsed.toFloat() / scrollDurationMs).coerceIn(0f, 1f)
                val left = widthPx - (widthPx + active.textWidthPx) * progress
                val textHeightPx = active.layout.size.height.toFloat()
                val top = active.track * active.lineHeightPx +
                    (active.lineHeightPx - textHeightPx).coerceAtLeast(0f) / 2f
                drawDanmakuLayout(
                    layout = active.layout,
                    topLeft = Offset(left, top.coerceAtMost(scrollAreaHeightPx - textHeightPx)),
                    emoticonUrls = active.emoticonUrls,
                    emoticonBitmaps = emoticonBitmaps,
                    alpha = opacityFactor,
                )
            }
        }
    }
}
