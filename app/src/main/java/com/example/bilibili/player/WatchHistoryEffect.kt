package com.example.bilibili.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.bilibili.data.BiliPlayStream
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

private const val WatchHistoryTickMs = 250L
private const val WatchHistoryReportIntervalTicks = 20

@Composable
internal fun WatchHistoryEffect(
    stream: BiliPlayStream,
    player: ExoPlayer,
) {
    val credential = LocalBilibiliCredential.current
    val reporter = LocalWatchHistoryReporter.current
    val streamState = rememberUpdatedState(stream)
    val credentialState = rememberUpdatedState(credential)
    val reporterState = rememberUpdatedState(reporter)

    LaunchedEffect(player, stream.aid, stream.cid) {
        val currentStream = streamState.value
        if (currentStream.aid <= 0L || currentStream.cid <= 0L) return@LaunchedEffect

        suspend fun reportNow() {
            val cred = credentialState.value ?: return
            val historyReporter = reporterState.value ?: return
            val latestStream = streamState.value
            if (latestStream.aid <= 0L || latestStream.cid <= 0L) return
            val progressSeconds = player.currentPosition.coerceAtLeast(0L) / 1000L
            historyReporter.reportIfNeeded(
                aid = latestStream.aid,
                cid = latestStream.cid,
                progressSeconds = progressSeconds,
                credential = cred,
            )
        }

        var ticksSinceReport = 0
        try {
            reportNow()
            while (isActive) {
                val playing = player.isPlaying
                val buffering = player.playbackState == Player.STATE_BUFFERING
                if (playing && !buffering) {
                    if (ticksSinceReport >= WatchHistoryReportIntervalTicks) {
                        reportNow()
                        ticksSinceReport = 0
                    } else {
                        ticksSinceReport++
                    }
                } else if (ticksSinceReport > 0) {
                    reportNow()
                    ticksSinceReport = 0
                }
                delay(WatchHistoryTickMs)
            }
        } finally {
            reportNow()
        }
    }
}
