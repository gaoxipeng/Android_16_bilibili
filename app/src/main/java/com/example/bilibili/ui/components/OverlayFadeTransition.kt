package com.example.bilibili.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

object OverlayFadeTransition {
    const val EnterDurationMillis = 220
    const val ExitDurationMillis = 280

    val enter = fadeIn(tween(EnterDurationMillis))
    val exit = fadeOut(tween(ExitDurationMillis))
}
