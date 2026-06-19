package com.example.bilibili.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private const val NavTransitionDurationMs = 280

private fun navStackEnterTransition() =
    slideInHorizontally(
        animationSpec = tween(NavTransitionDurationMs, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> fullWidth },
    ) + fadeIn(tween(NavTransitionDurationMs))

private fun navStackExitTransition() =
    slideOutHorizontally(
        animationSpec = tween(NavTransitionDurationMs, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> fullWidth },
    ) + fadeOut(tween(NavTransitionDurationMs))

@Composable
fun <T> NavAnimatedOverlay(
    target: T?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    var displayed by remember { mutableStateOf<T?>(null) }
    if (target != null) {
        displayed = target
    }
    val visible = target != null
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = navStackEnterTransition(),
        exit = navStackExitTransition(),
        label = "nav-stack-overlay",
    ) {
        displayed?.let { item -> content(item) }
    }
    if (!visible && displayed != null) {
        LaunchedEffect(displayed) {
            delay(NavTransitionDurationMs.toLong())
            displayed = null
        }
    }
}
