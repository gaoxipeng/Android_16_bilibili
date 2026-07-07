package com.example.bilibili.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

private const val WatchHistoryTickMs = 250L
private const val WatchHistoryReportIntervalTicks = 20

@Composable
internal fun WatchHistoryEffect(
    stream: BiliPlayStream,
    player: ExoPlayer,
    video: BiliVideoItem? = null,
) {
    val credential = LocalBilibiliCredential.current
    val reporter = LocalWatchHistoryReporter.current
    val streamState = rememberUpdatedState(stream)
    val videoState = rememberUpdatedState(video)
    val credentialState = rememberUpdatedState(credential)
    val reporterState = rememberUpdatedState(reporter)

    LaunchedEffect(player, stream.aid, stream.cid, video?.aid, video?.cid, video?.bvid) {
        suspend fun reportNow() {
            val cred = credentialState.value ?: return
            val historyReporter = reporterState.value ?: return
            val latestStream = streamState.value
            val latestVideo = videoState.value
            val aid = latestStream.aid.takeIf { it > 0L } ?: latestVideo?.aid ?: 0L
            val cid = latestStream.cid.takeIf { it > 0L } ?: latestVideo?.cid ?: 0L
            if (aid <= 0L || cid <= 0L) return
            val progressSeconds = player.currentPosition.coerceAtLeast(0L) / 1000L
            historyReporter.reportIfNeeded(
                aid = aid,
                cid = cid,
                progressSeconds = progressSeconds,
                credential = cred,
            )
        }

        val currentStream = streamState.value
        val currentVideo = videoState.value
        val aid = currentStream.aid.takeIf { it > 0L } ?: currentVideo?.aid ?: 0L
        val cid = currentStream.cid.takeIf { it > 0L } ?: currentVideo?.cid ?: 0L
        if (aid <= 0L || cid <= 0L) return@LaunchedEffect

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
