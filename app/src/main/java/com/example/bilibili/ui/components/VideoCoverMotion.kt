package com.example.bilibili.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

internal object VideoCoverOrigins {
    private val bounds = mutableMapOf<String, Rect>()
    // Only retain the most recently tapped card; navigation owns its snapshot.
    private var snapshotId: String? = null
    private var snapshot: ImageBitmap? = null
    fun capture(id: String, image: ImageBitmap?) {
        snapshotId = id
        snapshot = image
    }
    fun takeSnapshot(id: String): ImageBitmap? {
        val result = snapshot.takeIf { snapshotId == id }
        snapshot = null
        snapshotId = null
        return result
    }
    fun update(id: String, rect: Rect) {
        bounds[id] = rect
        if (bounds.size > 128) bounds.remove(bounds.keys.first())
    }
    fun get(id: String): Rect? = bounds[id]
}

/** One live layer expands from the card and contracts along exactly the same path. */
@Composable
internal fun AnimatedVisibilityScope.VideoCoverMotion(
    source: Rect,
    sourceSnapshot: ImageBitmap?,
    onSwipeBack: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val progress by transition.animateFloat(
        transitionSpec = { tween(NavTransitionDurationMs) }, label = "video-cover-progress",
    ) { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
    var root by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars
        .getTop(density).toFloat()
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val back by rememberUpdatedState(onSwipeBack)
    val motionProgress = progress * (1f - drag.value)
    val returning = transition.targetState == androidx.compose.animation.EnterExitState.PostExit || drag.value > 0f
    Box(Modifier.fillMaxSize().onGloballyPositioned { root = it.boundsInRoot() }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // Leave the video surface's seek/volume/brightness gestures untouched.
                if (back == null || down.position.y < statusTop + size.width * 9f / 16f || drag.isRunning) return@awaitEachGesture
                var totalX = 0f
                var totalY = 0f
                var claimed = false
                var fraction = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    totalX += change.positionChange().x
                    totalY += change.positionChange().y
                    if (!claimed && abs(totalY) > viewConfiguration.touchSlop && abs(totalY) > abs(totalX)) break
                    if (!claimed && totalX > viewConfiguration.touchSlop && totalX > abs(totalY) * 1.2f) claimed = true
                    if (claimed) {
                        change.consume()
                        fraction = (totalX / (size.width * 0.8f)).coerceIn(0f, 1f)
                        scope.launch { drag.snapTo(fraction) }
                    }
                }
                if (claimed) scope.launch {
                    val commit = fraction > 0.24f
                    drag.animateTo(if (commit) 1f else 0f, tween(220))
                    if (commit) back?.invoke()
                }
            }
        }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val initialScale = source.width / root.width.coerceAtLeast(1f)
            val scale = initialScale + (1f - initialScale) * motionProgress
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            scaleX = scale
            scaleY = scale
            val clippedTop = statusTop * (1f - motionProgress)
            translationX = (source.left - root.left) * (1f - motionProgress)
            translationY = (source.top - root.top) * (1f - motionProgress) - clippedTop * scale
            val visibleHeight = source.height / scale.coerceAtLeast(0.01f) * (1f - motionProgress) +
                root.height * motionProgress
            val corner = with(density) { 8.dp.toPx() } * (1f - motionProgress) / scale.coerceAtLeast(0.01f)
            shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                    Rect(0f, clippedTop, size.width, clippedTop + visibleHeight),
                    androidx.compose.ui.geometry.CornerRadius(corner, corner),
                ))
            }
            clip = true
        }.drawWithContent {
            drawContent()
            if (returning && sourceSnapshot != null) {
                // Resolve the layout mismatch inside the same clipped moving layer.
                // The final half is the exact source card, with no final-frame swap.
                val handoff = ((1f - motionProgress) / 0.45f).coerceIn(0f, 1f)
                val top = statusTop * (1f - motionProgress)
                val height = size.width * sourceSnapshot.height / sourceSnapshot.width.toFloat()
                drawImage(
                    image = sourceSnapshot,
                    dstOffset = IntOffset(0, top.roundToInt()),
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
                    alpha = handoff,
                )
            }
        }) { content() }
    }
}
