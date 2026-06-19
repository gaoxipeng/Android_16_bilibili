package com.example.bilibili.player

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential

val LocalBilibiliCredential = staticCompositionLocalOf<BilibiliCredential?> { null }

val LocalWatchHistoryReporter = staticCompositionLocalOf<WatchHistoryReporter?> { null }

class WatchHistoryReporter(
    private val api: BilibiliApiClient,
) {
    private var lastKey: String? = null
    private var lastProgressSeconds: Long = -1L
    private var lastReportAtMs: Long = 0L

    suspend fun reportIfNeeded(
        aid: Long,
        cid: Long,
        progressSeconds: Long,
        credential: BilibiliCredential?,
    ) {
        if (credential == null || aid <= 0L || cid <= 0L) return
        val key = "$aid:$cid"
        val now = System.currentTimeMillis()
        val progress = progressSeconds.coerceAtLeast(0L)
        val sameVideo = key == lastKey
        val progressDelta = kotlin.math.abs(progress - lastProgressSeconds)
        if (
            sameVideo &&
            progressDelta < 3L &&
            now - lastReportAtMs < 5_000L
        ) {
            return
        }
        api.reportWatchHistory(aid, cid, progress, credential)
        lastKey = key
        lastProgressSeconds = progress
        lastReportAtMs = now
    }
}
