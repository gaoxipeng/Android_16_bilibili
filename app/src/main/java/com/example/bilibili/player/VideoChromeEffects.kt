package com.example.bilibili.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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
    DisposableEffect(enabled, activity) {
        if (!enabled || activity == null) return@DisposableEffect onDispose {}
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
        insetsController.isAppearanceLightStatusBars = false
        onDispose {
            insetsController.isAppearanceLightStatusBars = previousLightStatusBars
        }
    }
}

@Composable
fun ImmersiveVideoChromeEffect(enabled: Boolean) {
    LightContentStatusBarEffect(enabled = enabled)
    val context = LocalContext.current
    val activity = context as? Activity
    DisposableEffect(enabled, activity) {
        if (!enabled || activity == null) return@DisposableEffect onDispose {}
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
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
