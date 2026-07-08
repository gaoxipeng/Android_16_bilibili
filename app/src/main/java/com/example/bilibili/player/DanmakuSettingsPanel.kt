package com.example.bilibili.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bilibili.ui.theme.isAppLightTheme
import com.example.bilibili.data.DanmakuSettings
import com.example.bilibili.data.DanmakuSpeedLevel
import com.example.bilibili.ui.components.ActionFrostedCard
import com.example.bilibili.ui.components.actionMenuSurfaceColor
import com.example.bilibili.ui.components.ImageActionMenuBlurRadius
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

private val DanmakuSettingsPanelWidth = 300.dp
private val DanmakuSettingsPanelCornerRadius = 22.dp
private val DanmakuSettingsPanelPadding = 10.dp
private val DanmakuSettingsRowSpacing = 6.dp
private val DanmakuSettingsLabelWidth = 58.dp

private val DanmakuSliderThumbRadius = 6.dp
private val DanmakuSliderThumbActiveRadius = 8.dp
private val DanmakuSliderTrackHeight = 2.5.dp
private val DanmakuSliderTrackAreaHeight = 24.dp
private val DanmakuSliderTickGap = 1.dp
private val DanmakuSliderTickAreaHeight = 5.dp
private val DanmakuSteppedSliderBlockHeight =
    DanmakuSliderTrackAreaHeight + DanmakuSliderTickGap + DanmakuSliderTickAreaHeight

private val DanmakuSliderActiveColor = Color(0xFFFF6699)
private val DanmakuSliderThumbColor = Color.White

@Composable
private fun danmakuSliderTrackColor(): Color =
    if (isAppLightTheme()) Color(0x26000000) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

@Composable
private fun danmakuSliderTickColor(): Color =
    if (isAppLightTheme()) Color(0x44000000) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)

@Composable
private fun danmakuSliderThumbBorderColor(): Color =
    if (isAppLightTheme()) Color(0x33000000) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

@Composable
private fun danmakuSettingValueColor(): Color =
    if (isAppLightTheme()) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

private data class SliderTrackMetrics(
    val startX: Float,
    val endX: Float,
    val centerY: Float,
) {
    val width: Float get() = (endX - startX).coerceAtLeast(0f)

    fun xForFraction(fraction: Float): Float =
        startX + width * fraction.coerceIn(0f, 1f)

    fun fractionForX(x: Float): Float =
        if (width <= 0f) 0f else ((x - startX) / width).coerceIn(0f, 1f)
}

@Composable
fun DanmakuSettingsOverlay(
    visible: Boolean,
    settings: DanmakuSettings,
    onSettingsChange: (DanmakuSettings) -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.92f,
    contentVerticalPadding: Dp = DanmakuSettingsPanelPadding,
    rowSpacing: Dp = DanmakuSettingsRowSpacing,
) {
    if (!visible) return

    BackHandler(onBack = onDismiss)

    val onDismissState by rememberUpdatedState(onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var moved = false
                        val slop = viewConfiguration.touchSlop
                        val start = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.positionChange() != Offset.Zero) {
                                if (!moved) {
                                    moved = (change.position - start).getDistance() > slop
                                }
                                change.consume()
                            }
                            if (event.changes.all { !it.pressed }) {
                                if (!moved) {
                                    onDismissState()
                                }
                                break
                            }
                        }
                    }
                },
        )
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = DanmakuSettingsPanelWidth)
                .fillMaxWidth(0.86f)
                .zIndex(1f),
        ) {
            ActionFrostedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * maxHeightFraction),
                backdrop = backdrop,
                effectBlurRadius = ImageActionMenuBlurRadius,
                effectContainerColor = actionMenuSurfaceColor(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = DanmakuSettingsPanelPadding,
                            vertical = contentVerticalPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(rowSpacing),
                ) {
                    DanmakuSettingRow(
                        title = "显示区域",
                        valueLabel = DanmakuSettings.displayAreaLabel(settings.displayAreaPercent),
                    ) {
                        DanmakuSteppedSlider(
                            stepCount = DanmakuSettings.DISPLAY_AREA_OPTIONS.size,
                            selectedIndex = settings.displayAreaIndex,
                            onSelectedIndexChange = { index ->
                                onSettingsChange(settings.withDisplayAreaIndex(index))
                            },
                        )
                    }
                    DanmakuSettingRow(
                        title = "不透明度",
                        valueLabel = "${settings.opacityPercent}%",
                    ) {
                        DanmakuContinuousSlider(
                            value = settings.opacityPercent.toFloat(),
                            valueRange = 10f..100f,
                            stepSize = 5f,
                            onValueChange = { value ->
                                onSettingsChange(settings.copy(opacityPercent = value.roundToInt()))
                            },
                        )
                    }
                    DanmakuSettingRow(
                        title = "弹幕字号",
                        valueLabel = "${settings.fontSizePercent}%",
                    ) {
                        DanmakuContinuousSlider(
                            value = settings.fontSizePercent.toFloat(),
                            valueRange = 50f..170f,
                            stepSize = 5f,
                            onValueChange = { value ->
                                onSettingsChange(settings.copy(fontSizePercent = value.roundToInt()))
                            },
                        )
                    }
                    DanmakuSettingRow(
                        title = "弹幕速度",
                        valueLabel = settings.speedLevel.label,
                    ) {
                        DanmakuSteppedSlider(
                            stepCount = DanmakuSpeedLevel.entries.size,
                            selectedIndex = settings.speedLevel.ordinal,
                            onSelectedIndexChange = { index ->
                                onSettingsChange(
                                    settings.copy(speedLevel = DanmakuSpeedLevel.fromIndex(index)),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DanmakuSettingRow(
    title: String,
    valueLabel: String,
    slider: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.width(DanmakuSettingsLabelWidth),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodySmall,
                color = danmakuSettingValueColor(),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            slider()
        }
    }
}

@Composable
private fun DanmakuSteppedSlider(
    stepCount: Int,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stepCount <= 1) return
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { DanmakuSliderThumbRadius.toPx() }
    val trackHeightPx = with(density) { DanmakuSliderTrackHeight.toPx() }
    val tickRadiusPx = with(density) { 2.dp.toPx() }
    val maxIndex = (stepCount - 1).coerceAtLeast(0)
    val clampedIndex = selectedIndex.coerceIn(0, maxIndex)
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    fun indexForFraction(fraction: Float): Int =
        (fraction * maxIndex).roundToInt().coerceIn(0, maxIndex)

    fun fractionForIndex(index: Int): Float =
        if (maxIndex == 0) 0f else index.toFloat() / maxIndex.toFloat()

    val displayFraction = dragFraction ?: fractionForIndex(clampedIndex)
    val tickColor = danmakuSliderTickColor()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(DanmakuSteppedSliderBlockHeight),
    ) {
        DanmakuSliderTrack(
            thumbRadius = DanmakuSliderThumbRadius,
            thumbActive = dragFraction != null,
            displayFraction = displayFraction,
            trackHeightPx = trackHeightPx,
            onTrackWidthChanged = { trackWidthPx = it },
            onPositionX = { x ->
                if (trackWidthPx <= 0f) return@DanmakuSliderTrack
                val metrics = trackMetrics(trackWidthPx, thumbRadiusPx * 2, thumbRadiusPx)
                val fraction = metrics.fractionForX(x)
                dragFraction = fraction
                onSelectedIndexChange(indexForFraction(fraction))
            },
            onGestureEnd = { dragFraction = null },
        )
        Spacer(Modifier.height(DanmakuSliderTickGap))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DanmakuSliderTickAreaHeight),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val metrics = trackMetrics(size.width, size.height, thumbRadiusPx)
                repeat(stepCount) { index ->
                    val active = index == clampedIndex
                    drawCircle(
                        color = if (active) DanmakuSliderActiveColor else tickColor,
                        radius = if (active) tickRadiusPx + 0.5f else tickRadiusPx,
                        center = Offset(
                            x = metrics.xForFraction(fractionForIndex(index)),
                            y = size.height / 2f,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DanmakuContinuousSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float = 0f,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val normalThumbRadiusPx = with(density) { DanmakuSliderThumbRadius.toPx() }
    val activeThumbRadiusPx = with(density) { DanmakuSliderThumbActiveRadius.toPx() }
    val trackHeightPx = with(density) { DanmakuSliderTrackHeight.toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    val clampedValue = value.coerceIn(valueRange)

    fun steppedValue(value: Float): Float {
        val clamped = value.coerceIn(valueRange)
        if (stepSize <= 0f) return clamped
        val steps = ((clamped - valueRange.start) / stepSize).roundToInt()
        return (valueRange.start + steps * stepSize).coerceIn(valueRange)
    }

    fun valueForFraction(fraction: Float): Float =
        steppedValue(
            valueRange.start + (valueRange.endInclusive - valueRange.start) * fraction.coerceIn(0f, 1f),
        )

    fun fractionForValue(v: Float): Float {
        val span = valueRange.endInclusive - valueRange.start
        return if (span <= 0f) 0f else ((v - valueRange.start) / span).coerceIn(0f, 1f)
    }

    val displayFraction = dragFraction ?: fractionForValue(clampedValue)
    val thumbRadius = if (isDragging) DanmakuSliderThumbActiveRadius else DanmakuSliderThumbRadius
    val thumbRadiusPx = if (isDragging) activeThumbRadiusPx else normalThumbRadiusPx

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(DanmakuSliderTrackAreaHeight),
    ) {
        DanmakuSliderTrack(
            thumbRadius = thumbRadius,
            thumbActive = isDragging,
            displayFraction = displayFraction,
            trackHeightPx = trackHeightPx,
            onTrackWidthChanged = { trackWidthPx = it },
            onPositionX = { x ->
                if (trackWidthPx <= 0f) return@DanmakuSliderTrack
                val metrics = trackMetrics(trackWidthPx, thumbRadiusPx * 2, thumbRadiusPx)
                val fraction = metrics.fractionForX(x)
                val newValue = valueForFraction(fraction)
                dragFraction = fractionForValue(newValue)
                onValueChange(newValue)
            },
            onGestureStart = { isDragging = true },
            onGestureEnd = {
                isDragging = false
                dragFraction = null
            },
        )
    }
}

@Composable
private fun DanmakuSliderTrack(
    thumbRadius: Dp,
    thumbActive: Boolean,
    displayFraction: Float,
    trackHeightPx: Float,
    onTrackWidthChanged: (Float) -> Unit,
    onPositionX: (Float) -> Unit,
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {},
) {
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val onGestureStartState by rememberUpdatedState(onGestureStart)
    val onPositionXState by rememberUpdatedState(onPositionX)
    val onGestureEndState by rememberUpdatedState(onGestureEnd)
    val trackColor = danmakuSliderTrackColor()
    val thumbBorderColor = danmakuSliderThumbBorderColor()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DanmakuSliderTrackAreaHeight),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .zIndex(0f),
        ) {
            val metrics = trackMetrics(size.width, size.height, thumbRadiusPx)
            val thumbX = metrics.xForFraction(displayFraction)
            drawCompactTrack(
                metrics = metrics,
                progressX = thumbX,
                trackHeightPx = trackHeightPx,
                trackColor = trackColor,
            )
            drawSliderThumb(
                center = Offset(thumbX, metrics.centerY),
                radius = thumbRadiusPx,
                emphasized = thumbActive,
                thumbBorderColor = thumbBorderColor,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .onSizeChanged { onTrackWidthChanged(it.width.toFloat()) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        onGestureStartState()
                        onPositionXState(down.position.x)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.pressed) {
                                    change.consume()
                                    onPositionXState(change.position.x)
                                }
                                if (event.changes.all { !it.pressed }) {
                                    break
                                }
                            }
                        } finally {
                            onGestureEndState()
                        }
                    }
                },
        )
    }
}

private fun trackMetrics(
    width: Float,
    height: Float,
    thumbRadiusPx: Float,
): SliderTrackMetrics = SliderTrackMetrics(
    startX = thumbRadiusPx,
    endX = width - thumbRadiusPx,
    centerY = height / 2f,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCompactTrack(
    metrics: SliderTrackMetrics,
    progressX: Float,
    trackHeightPx: Float,
    trackColor: Color,
) {
    drawLine(
        color = trackColor,
        start = Offset(metrics.startX, metrics.centerY),
        end = Offset(metrics.endX, metrics.centerY),
        strokeWidth = trackHeightPx,
        cap = StrokeCap.Round,
    )
    if (progressX > metrics.startX) {
        drawLine(
            color = DanmakuSliderActiveColor,
            start = Offset(metrics.startX, metrics.centerY),
            end = Offset(progressX, metrics.centerY),
            strokeWidth = trackHeightPx,
            cap = StrokeCap.Round,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSliderThumb(
    center: Offset,
    radius: Float,
    emphasized: Boolean = false,
    thumbBorderColor: Color,
) {
    if (emphasized) {
        drawCircle(
            color = DanmakuSliderActiveColor.copy(alpha = 0.18f),
            radius = radius + 3f,
            center = center,
        )
    }
    drawCircle(
        color = DanmakuSliderThumbColor,
        radius = radius,
        center = center,
    )
    drawCircle(
        color = thumbBorderColor,
        radius = radius,
        center = center,
        style = Stroke(width = 1f),
    )
}
