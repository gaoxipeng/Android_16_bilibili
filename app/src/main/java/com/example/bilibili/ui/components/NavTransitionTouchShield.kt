package com.example.bilibili.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.bilibili.ui.navigation.NavTransitionCoordinator

@Composable
fun NavTransitionTouchShield(
    coordinator: NavTransitionCoordinator,
    modifier: Modifier = Modifier,
) {
    if (!coordinator.isTransitioning) return
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(coordinator.isTransitioning) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    coordinator.skipAllTransitions()
                }
            },
    )
}
