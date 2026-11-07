package com.example.bilibili.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

internal fun normalizeBottomBarGlassAmount(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

internal fun bottomBarGlassSurfaceColor(isLightTheme: Boolean, frostAmount: Float): Color =
    (if (isLightTheme) Color.White else Color(0xFF121212))
        .copy(alpha = 0.90f * normalizeBottomBarGlassAmount(frostAmount))

internal fun BackdropEffectScope.bottomBarGlassEffects(frostAmount: Float, glassAmount: Float) {
    val frost = normalizeBottomBarGlassAmount(frostAmount)
    val glass = normalizeBottomBarGlassAmount(glassAmount)
    val blurRadius = 24f * frost
    val refractionHeight = 24f * glass
    if (blurRadius > 0f) {
        blur(blurRadius * density, TileMode.Clamp)
        padding = blurRadius * density * 2f + refractionHeight * density
    }
    if (glass > 0f) lens(refractionHeight * density, 40f * glass * density)
}
