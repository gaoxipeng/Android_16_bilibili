package com.example.bilibili.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val LightColorScheme = lightColorScheme(
    primary = BiliPink,
    secondary = BiliPinkDark,
    background = AppBackgroundLight,
    surface = AppSurfaceLight,
    surfaceContainerLowest = AppBackgroundLight,
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFF5F5F5),
    surfaceContainerHighest = Color(0xFFF7F7F7),
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF999999),
    outline = Color(0x80999999),
    outlineVariant = Color(0xFFE8E8E8),
    surfaceTint = Color.Transparent,
)

private val DarkColorScheme = darkColorScheme(
    primary = BiliPinkDark,
    secondary = BiliPink,
    background = AppBackgroundDark,
    surface = Color(0xFF1C1C1E),
    surfaceContainerLowest = AppBackgroundDark,
    surfaceContainerLow = Color(0xFF242426),
    surfaceContainer = Color(0xFF2C2C2E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    onSurface = Color(0xFFEAEAEA),
    onSurfaceVariant = TabMutedDark,
    outline = Color(0x66FFFFFF),
    outlineVariant = Color(0xFF3A3A3C),
    surfaceTint = Color.Transparent,
)

@Composable
fun isAppLightTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() > 0.5f

@Composable
fun BilibiliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
