package com.example.bilibili.player

import androidx.media3.exoplayer.ExoPlayer

internal fun ExoPlayer.safeCurrentPosition(): Long =
    runCatching { currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)

internal fun ExoPlayer.runIfUsable(block: ExoPlayer.() -> Unit) {
    runCatching { block() }
}

internal fun ExoPlayer.safePause() {
    runIfUsable {
        playWhenReady = false
        pause()
    }
}

internal fun ExoPlayer.safePrepareAndPlay() {
    runIfUsable {
        if (playbackState == androidx.media3.common.Player.STATE_IDLE ||
            playbackState == androidx.media3.common.Player.STATE_ENDED
        ) {
            prepare()
        }
        playWhenReady = true
        if (!isPlaying) {
            play()
        }
    }
}
