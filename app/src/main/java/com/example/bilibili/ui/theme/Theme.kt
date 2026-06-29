package com.example.bilibili.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BiliPinkDark,
    secondary = BiliPink,
    background = AppBackgroundDark,
    surface = Color(0xFF1C1C1E),
)

private val LightColorScheme = lightColorScheme(
    primary = BiliPink,
    secondary = BiliPinkDark,
    background = AppBackgroundLight,
    surface = AppSurfaceLight,
    surfaceContainerLowest = AppBackgroundLight,
    surfaceTint = Color.Transparent,
)

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
