package com.example.bilibili.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.example.bilibili.ui.navigation.LocalNavTransitionCoordinator
import kotlinx.coroutines.delay

const val NavTransitionDurationMs = 280

private const val NavOverlayTopZIndex = 590f
private const val NavOverlayExitZIndex = 595f
const val NavFullscreenZIndex = 600f

fun navStackEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(NavTransitionDurationMs, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> fullWidth },
    ) + fadeIn(tween(NavTransitionDurationMs))

fun navStackExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(NavTransitionDurationMs, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> fullWidth },
    ) + fadeOut(tween(NavTransitionDurationMs))

@Composable
fun <T> NavAnimatedOverlay(
    target: T?,
    modifier: Modifier = Modifier,
    stackTop: Boolean = false,
    layerBaseZIndex: Float = 0f,
    animationKey: Any? = null,
    visible: Boolean? = null,
    stackAnimated: Boolean = true,
    layerKey: String? = null,
    pendingEnterKey: String? = null,
    pendingExitKey: String? = null,
    exitSeed: T? = null,
    onClearPendingEnter: () -> Unit = {},
    onClearPendingExit: () -> Unit = {},
    enter: EnterTransition = navStackEnterTransition(),
    exit: ExitTransition = navStackExitTransition(),
    exitHoldMillis: Long = NavTransitionDurationMs.toLong(),
    onHidden: () -> Unit = {},
    content: @Composable (T) -> Unit,
) {
    val navTransitions = LocalNavTransitionCoordinator.current
    var displayed by remember { mutableStateOf<T?>(null) }
    if (target != null) {
        displayed = target
    } else if (exitSeed != null) {
        displayed = exitSeed
    }
    val overlayVisible = visible ?: (target != null)
    val pendingExit = layerKey != null && pendingExitKey == layerKey
    val avVisible = target != null || overlayVisible
    val transitionIdentity = animationKey ?: target
    val enteredKeys = remember { mutableStateMapOf<Any, Boolean>() }
    val shouldAnimateEnter = stackAnimated &&
        overlayVisible &&
        transitionIdentity != null &&
        enteredKeys[transitionIdentity] != true &&
        (layerKey == null || pendingEnterKey == null || pendingEnterKey == layerKey)
    val shouldAnimateExit = stackAnimated &&
        !overlayVisible &&
        target == null &&
        (layerKey == null || pendingExit)
    val effectiveEnter = if (shouldAnimateEnter) enter else EnterTransition.None
    val effectiveExit = if (shouldAnimateExit) exit else ExitTransition.None
    val effectiveExitHoldMillis = if (shouldAnimateExit) exitHoldMillis else 0L
    var skipAnimationToken by remember { mutableIntStateOf(0) }
    val transitionState = remember(skipAnimationToken, transitionIdentity) {
        val initiallyVisible = avVisible || (exitSeed != null && pendingExit)
        MutableTransitionState(initiallyVisible).apply { targetState = avVisible }
    }
    SideEffect {
        transitionState.targetState = avVisible
    }
    LaunchedEffect(overlayVisible, transitionIdentity, pendingEnterKey) {
        if (overlayVisible && transitionIdentity != null) {
            enteredKeys[transitionIdentity] = true
            if (layerKey != null && pendingEnterKey == layerKey) {
                onClearPendingEnter()
            }
        }
    }
    val isAnimating = !transitionState.isIdle
    val isExiting = isAnimating && !transitionState.targetState
    val zIndex = when {
        isExiting -> NavOverlayExitZIndex
        stackTop -> NavOverlayTopZIndex
        else -> layerBaseZIndex
    }

    DisposableEffect(Unit) {
        val unregister = navTransitions.registerSkipHandler { skipAnimationToken++ }
        onDispose { unregister() }
    }
    DisposableEffect(isAnimating) {
        if (isAnimating) {
            navTransitions.onTransitionStart()
            onDispose { navTransitions.onTransitionEnd() }
        } else {
            onDispose {}
        }
    }

    AnimatedVisibility(
        visibleState = transitionState,
        modifier = modifier.zIndex(zIndex),
        enter = effectiveEnter,
        exit = effectiveExit,
        label = "nav-stack-overlay",
    ) {
        displayed?.let { item ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeTouchEvents(),
            ) {
                content(item)
            }
        }
    }
    if (!avVisible && displayed != null) {
        LaunchedEffect(displayed, transitionState.isIdle, target) {
            if (!transitionState.isIdle || transitionState.targetState) return@LaunchedEffect
            if (effectiveExitHoldMillis > 0L) {
                delay(effectiveExitHoldMillis)
            }
            if (!transitionState.targetState && transitionState.isIdle && target == null) {
                displayed = null
                if (layerKey != null && pendingExitKey == layerKey) {
                    onClearPendingExit()
                }
                onHidden()
            }
        }
    }
}
