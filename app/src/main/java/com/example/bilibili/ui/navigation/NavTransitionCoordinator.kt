package com.example.bilibili.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class NavTransitionCoordinator {
    var activeTransitions by mutableIntStateOf(0)
    val isTransitioning: Boolean
        get() = activeTransitions > 0
    private val skipHandlers = mutableListOf<() -> Unit>()

    fun onTransitionStart() {
        activeTransitions++
    }

    fun onTransitionEnd() {
        activeTransitions = (activeTransitions - 1).coerceAtLeast(0)
    }

    fun registerSkipHandler(handler: () -> Unit): () -> Unit {
        skipHandlers.add(handler)
        return { skipHandlers.remove(handler) }
    }

    fun skipAllTransitions() {
        skipHandlers.toList().forEach { it.invoke() }
    }
}

val LocalNavTransitionCoordinator = staticCompositionLocalOf { NavTransitionCoordinator() }
