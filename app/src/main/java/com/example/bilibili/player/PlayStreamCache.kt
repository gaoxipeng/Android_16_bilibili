package com.example.bilibili.player

import android.net.Uri
import com.example.bilibili.data.BiliPlayStream

private const val PLAY_STREAM_MAX_AGE_MS = 2 * 60 * 60 * 1000L
private const val PLAY_STREAM_EXPIRY_SKEW_MS = 60_000L

fun BiliPlayStream.withCacheTimestamp(timestampMs: Long = System.currentTimeMillis()): BiliPlayStream =
    if (cachedAtMs == timestampMs) this else copy(cachedAtMs = timestampMs)

/** Stable identity for the playable media URLs (ignores last_play_* resume metadata). */
fun BiliPlayStream.mediaIdentity(): String =
    "$cid\u0001$videoUrl\u0001${audioUrl.orEmpty()}"

fun BiliPlayStream.isPlayStreamCacheStale(nowMs: Long = System.currentTimeMillis()): Boolean {
    parseUrlDeadlineMs(videoUrl)?.let { deadlineMs ->
        return nowMs >= deadlineMs - PLAY_STREAM_EXPIRY_SKEW_MS
    }
    parseUrlDeadlineMs(audioUrl.orEmpty())?.let { deadlineMs ->
        return nowMs >= deadlineMs - PLAY_STREAM_EXPIRY_SKEW_MS
    }
    if (cachedAtMs > 0L) {
        return nowMs - cachedAtMs >= PLAY_STREAM_MAX_AGE_MS
    }
    // Untimestamped streams have no reliable expiry signal; treat as stale so overnight
    // resumes always re-fetch instead of spinning on a dead CDN URL.
    return videoUrl.isNotBlank()
}

private fun parseUrlDeadlineMs(url: String): Long? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val deadlineSeconds = uri.getQueryParameter("deadline")?.toLongOrNull()
        ?: uri.getQueryParameter("expires")?.toLongOrNull()
    return deadlineSeconds?.times(1000L)
}
