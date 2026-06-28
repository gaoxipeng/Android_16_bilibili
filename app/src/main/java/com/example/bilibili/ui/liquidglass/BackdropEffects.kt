package com.example.bilibili.ui.liquidglass

import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.BackdropEffectScope

fun BackdropEffectScope.ensureMinPadding(min: Dp) {
    val minPx = min.toPx()
    if (padding < minPx) {
        padding = minPx
    }
}
