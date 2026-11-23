package com.example.bilibili.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.DisposableEffect
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
import com.example.bilibili.ui.liquidglass.LocalLiquidMenuBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import androidx.compose.ui.graphics.RectangleShape

internal object VideoCoverOrigins {
    var hiddenId by mutableStateOf<String?>(null)
    private val bounds = mutableMapOf<String, Rect>()
    private val radii = mutableMapOf<String, Float>()
    // Only retain the most recently tapped card; navigation owns its snapshot.
    private var snapshotId: String? = null
    private var snapshot: ImageBitmap? = null
    fun capture(id: String, image: ImageBitmap?) {
        snapshotId = id
        snapshot = image
        hiddenId = id
    }
    fun takeSnapshot(id: String): ImageBitmap? {
        val result = snapshot.takeIf { snapshotId == id }
        snapshot = null
        snapshotId = null
        return result
    }
    fun reveal() { hiddenId = null }
    fun update(id: String, rect: Rect, radiusDp: Float) {
        bounds[id] = rect
        radii[id] = radiusDp
        if (bounds.size > 128) bounds.remove(bounds.keys.first())
    }
    fun get(id: String): Rect? = bounds[id]
    fun radius(rect: Rect): Float = bounds.entries.firstOrNull { it.value == rect }?.let { radii[it.key] } ?: 8f
}

/** One live layer expands from the card and contracts along exactly the same path. */
@Composable
internal fun AnimatedVisibilityScope.VideoCoverMotion(
    source: Rect,
    sourceSnapshot: ImageBitmap?,
    onSwipeBack: (() -> Unit)?,
    onSystemBack: (() -> Unit)?,
    visualOnly: Boolean = false,
    revealFeedVideo: Boolean = true,
    content: @Composable () -> Unit,
) {
    val progress by transition.animateFloat(
        transitionSpec = { tween(if (targetState == androidx.compose.animation.EnterExitState.PostExit) 250 else 200, easing = CubicBezierEasing(0.20f, 0.68f, 0.30f, 1f)) }, label = "video-cover-progress",
    ) { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
    var root by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars
        .getTop(density).toFloat()
    val drag = remember { Animatable(0f) }
    val settle = remember { Animatable(0f) }
    var committed by remember { mutableStateOf(false) }
    var swiping by remember { mutableStateOf(false) }
    val sourceRadius = remember(source) { VideoCoverOrigins.radius(source) }
    DisposableEffect(source, revealFeedVideo) {
        onDispose { if (revealFeedVideo) VideoCoverOrigins.reveal() }
    }
    val scope = rememberCoroutineScope()
    val back by rememberUpdatedState(onSwipeBack)
    fun finishReturn(onCommit: () -> Unit) {
        if (committed) return
        committed = true
        scope.launch {
            settle.animateTo(1f, tween(250, easing = CubicBezierEasing(0.20f, 0.68f, 0.30f, 1f)))
            onCommit()
        }
    }
    androidx.activity.compose.BackHandler(enabled = onSystemBack != null) {
        onSystemBack?.let(::finishReturn)
    }
    val motionProgress = if (committed) 1f - settle.value else progress
    val returning = visualOnly || committed ||
        transition.targetState == androidx.compose.animation.EnterExitState.PostExit
    val background = MaterialTheme.colorScheme.background
    val pageBackdrop = LocalLiquidMenuBackdrop.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { root = it.boundsInRoot() }
        .then(if (visualOnly) Modifier else Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val backAction = back
                // Leave the video surface's seek/volume/brightness gestures untouched.
                if (backAction == null || committed || down.position.y < statusTop + size.width * 9f / 16f || drag.isRunning) return@awaitEachGesture
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
                    if (!claimed && totalX > viewConfiguration.touchSlop && totalX > abs(totalY) * 1.2f) { claimed = true; swiping = true }
                    if (claimed) {
                        change.consume()
                        fraction = (totalX / (size.width * 0.8f)).coerceIn(0f, 1f)
                        scope.launch { drag.snapTo(fraction) }
                    }
                }
                if (claimed) scope.launch {
                    val commit = fraction > 0.24f
                    if (commit) {
                        finishReturn(backAction)
                    } else {
                        drag.animateTo(0f, tween(180, easing = CubicBezierEasing(0.20f, 0.68f, 0.30f, 1f)))
                        swiping = false
                    }
                }
            }
        })) {
        if (!returning && progress < 0.999f && pageBackdrop != null) {
            Box(Modifier.fillMaxSize().drawBackdrop(backdrop = pageBackdrop, shape = { RectangleShape }, effects = {
                blur((12.dp.toPx() * kotlin.math.sin(progress.coerceIn(0f, 1f) * Math.PI.toFloat())).coerceAtLeast(0f))
            }))
        }
        if (returning || drag.value > 0f) {
            Box(Modifier.fillMaxSize().background(background.copy(alpha = (motionProgress * (1f - drag.value)).coerceIn(0f, 1f))))
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            val initialScale = source.width / root.width.coerceAtLeast(1f)
            val swipeScale = 1f - 0.08f * drag.value
            val scale = (initialScale + (1f - initialScale) * motionProgress) * (1f - (1f - swipeScale) * motionProgress)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            scaleX = scale
            scaleY = scale
            val clippedTop = statusTop * (1f - motionProgress)
            translationX = (source.left - root.left) * (1f - motionProgress) + (root.width * 0.8f * drag.value + root.width * (1f - swipeScale) / 2f) * motionProgress
            translationY = (source.top - root.top) * (1f - motionProgress) - clippedTop * scale + root.height * (1f - swipeScale) / 2f * motionProgress
            val visibleHeight = source.height / scale.coerceAtLeast(0.01f) * (1f - motionProgress) +
                root.height * motionProgress
            val corner = with(density) {
                val target = sourceRadius.dp.toPx()
                val page = if (swiping || returning) 46.dp.toPx() else 0f
                (target * (1f - motionProgress) + page * motionProgress) / scale.coerceAtLeast(0.01f)
            }
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
