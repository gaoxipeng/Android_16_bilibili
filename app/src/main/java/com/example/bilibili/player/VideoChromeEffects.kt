package com.example.bilibili.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

@Composable
fun StatusBarIconsEffect(darkIcons: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val window = activity.window
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    DisposableEffect(activity) {
        val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
        onDispose {
            insetsController.isAppearanceLightStatusBars = previousLightStatusBars
        }
    }
    SideEffect {
        insetsController.isAppearanceLightStatusBars = darkIcons
    }
}

@Composable
fun LightContentStatusBarEffect(enabled: Boolean = true) {
    val context = LocalContext.current
    val activity = context as? Activity
    val orientation = LocalConfiguration.current.orientation
    val window = activity?.window
    val insetsController = window?.let {
        WindowCompat.getInsetsController(it, it.decorView)
    }
    DisposableEffect(enabled, activity) {
        if (!enabled || insetsController == null) return@DisposableEffect onDispose {}
        val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
        onDispose {
            insetsController.isAppearanceLightStatusBars = previousLightStatusBars
        }
    }
    SideEffect {
        if (enabled && insetsController != null) {
            insetsController.isAppearanceLightStatusBars = false
        }
    }
    LaunchedEffect(enabled, activity, orientation) {
        if (!enabled || insetsController == null) return@LaunchedEffect
        insetsController.isAppearanceLightStatusBars = false
        delay(120)
        insetsController.isAppearanceLightStatusBars = false
        delay(300)
        insetsController.isAppearanceLightStatusBars = false
    }
}

@Composable
fun ImmersiveVideoChromeEffect(enabled: Boolean) {
    LightContentStatusBarEffect(enabled = enabled)
    val context = LocalContext.current
    val activity = context as? Activity
    val orientation = LocalConfiguration.current.orientation
    DisposableEffect(enabled, activity) {
        if (!enabled || activity == null) return@DisposableEffect onDispose {}
        val window = activity.window
        val previousCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode
        } else {
            null
        }
        onDispose {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            // MainActivity uses enableEdgeToEdge(); restoring decor fitting leaves status bar hidden.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && previousCutoutMode != null) {
                val attributes = window.attributes
                attributes.layoutInDisplayCutoutMode = previousCutoutMode
                window.attributes = attributes
            }
        }
    }

    SideEffect {
        if (activity == null) return@SideEffect
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val attributes = window.attributes
                attributes.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = attributes
            }
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        // Re-apply after orientation changes while fullscreen is active.
        @Suppress("UNUSED_EXPRESSION")
        orientation
    }
}

@Composable
fun FullscreenOrientationEffect(
    enabled: Boolean,
    portraitVideo: Boolean?,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    DisposableEffect(enabled, portraitVideo, activity) {
        if (!enabled || activity == null || portraitVideo == null) return@DisposableEffect onDispose {}
        val previous = activity.requestedOrientation
        activity.requestedOrientation = if (portraitVideo) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            activity.requestedOrientation = previous
        }
    }
}
