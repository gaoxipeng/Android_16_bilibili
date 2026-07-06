package com.example.bilibili.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.Player

internal fun shouldKeepScreenOnDuringPlayback(
    playbackEnabled: Boolean,
    playerHandedOff: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
): Boolean {
    if (!playbackEnabled || playerHandedOff) return false
    if (playbackState == Player.STATE_ENDED) return false
    return playWhenReady
}

@Composable
internal fun VideoPlaybackKeepScreenOnEffect(
    playbackKey: String,
    playbackEnabled: Boolean,
    playerHandedOff: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
    coordinator: VideoPlaybackCoordinator,
) {
    LaunchedEffect(
        playbackKey,
        playbackEnabled,
        playerHandedOff,
        playWhenReady,
        playbackState,
    ) {
        coordinator.setKeepScreenOn(
            playbackKey = playbackKey,
            enabled = shouldKeepScreenOnDuringPlayback(
                playbackEnabled = playbackEnabled,
                playerHandedOff = playerHandedOff,
                playWhenReady = playWhenReady,
                playbackState = playbackState,
            ),
        )
    }

    DisposableEffect(playbackKey) {
        onDispose {
            coordinator.setKeepScreenOn(playbackKey, false)
        }
    }
}

@Composable
fun PlaybackKeepScreenOnWindowEffect(coordinator: VideoPlaybackCoordinator) {
    val keepOn = coordinator.keepScreenOnRequested
    val view = LocalView.current
    DisposableEffect(view, keepOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepOn
        onDispose {
            view.keepScreenOn = previous
        }
    }
}
