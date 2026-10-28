package com.example.bilibili.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Video details without a source-card rectangle (history, favourites, etc.). */
@Composable
internal fun VideoPageSwipeBack(onSwipeBack: (() -> Unit)?, content: @Composable () -> Unit) {
    val back by rememberUpdatedState(onSwipeBack)
    val drag = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val statusTop = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val backAction = back
            if (backAction == null || settling || down.position.y < statusTop + size.width * 9f / 16f) return@awaitEachGesture
            var dx = 0f
            var dy = 0f
            var claimed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                dx += change.positionChange().x
                dy += change.positionChange().y
                if (!claimed && (dx < -viewConfiguration.touchSlop ||
                    abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx))) break
                if (!claimed && dx > viewConfiguration.touchSlop && dx > abs(dy) * 1.2f) claimed = true
                if (claimed) {
                    change.consume()
                    val offset = dx.coerceIn(0f, size.width.toFloat())
                    scope.launch { drag.snapTo(offset) }
                }
            }
            if (claimed) {
                settling = true
                val width = size.width.toFloat()
                val commit = dx > width * 0.24f
                scope.launch {
                    drag.animateTo(if (commit) width else 0f, tween(220))
                    // Retain the offscreen position while the existing navigation exit
                    // completes; resetting to zero here would flash the departing page.
                    if (commit) backAction() else settling = false
                }
            }
        }
    }) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag.value }
            .background(MaterialTheme.colorScheme.background), content = { content() })
    }
}
