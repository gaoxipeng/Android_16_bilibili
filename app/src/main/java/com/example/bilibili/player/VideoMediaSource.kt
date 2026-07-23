package com.example.bilibili.player

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BilibiliEndpoints

fun buildVideoMediaSource(
    context: Context,
    stream: BiliPlayStream,
    playbackMetadata: VideoPlaybackMetadata? = null,
    referer: String = BilibiliEndpoints.HOME,
    origin: String = "https://www.bilibili.com",
): MediaSource {
    val factory = bilibiliDataSourceFactory(context, referer = referer, origin = origin)
    val mediaMetadata = playbackMetadata?.toMediaMetadata()
    val videoSource = createStreamMediaSource(factory, stream.videoUrl, mediaMetadata)
    val audioUrl = stream.audioUrl?.takeIf { it.isNotBlank() && it != stream.videoUrl }
    if (audioUrl == null) return videoSource
    val audioSource = ProgressiveMediaSource.Factory(factory)
        .createMediaSource(MediaItem.fromUri(audioUrl))
    return MergingMediaSource(videoSource, audioSource)
}

fun buildLiveMediaSource(
    context: Context,
    streamUrl: String,
    roomId: Long,
): MediaSource {
    val referer = "${BilibiliEndpoints.LIVE_HOME}$roomId"
    val factory = bilibiliDataSourceFactory(
        context = context,
        referer = referer,
        origin = BilibiliEndpoints.LIVE_HOME.trimEnd('/'),
    )
    return createStreamMediaSource(factory, streamUrl)
}

private fun createStreamMediaSource(
    factory: DefaultDataSource.Factory,
    url: String,
    mediaMetadata: androidx.media3.common.MediaMetadata? = null,
): MediaSource {
    val mediaItem = MediaItem.Builder()
        .setUri(url)
        .apply {
            if (mediaMetadata != null) {
                setMediaMetadata(mediaMetadata)
            }
        }
        .build()
    return when {
        url.contains(".mpd", ignoreCase = true) ->
            DashMediaSource.Factory(factory).createMediaSource(mediaItem)
        url.contains("m3u8", ignoreCase = true) ->
            HlsMediaSource.Factory(factory).createMediaSource(mediaItem)
        else ->
            ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
    }
}

private fun bilibiliDataSourceFactory(
    context: Context,
    referer: String = BilibiliEndpoints.HOME,
    origin: String = "https://www.bilibili.com",
): DefaultDataSource.Factory {
    val cookie = PlaybackCookieProvider.cookieHeader?.takeIf { it.isNotBlank() }
        ?: CookieManager.getInstance().getCookie(BilibiliEndpoints.HOME).orEmpty()
    val headers = buildMap {
        put("Accept", "*/*")
        put("Referer", referer)
        put("Origin", origin)
        put("User-Agent", BilibiliEndpoints.USER_AGENT)
        if (cookie.isNotBlank()) put("Cookie", cookie)
    }
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(BilibiliEndpoints.USER_AGENT)
        .setDefaultRequestProperties(headers)
        .setAllowCrossProtocolRedirects(true)
    return DefaultDataSource.Factory(context, httpFactory)
}

fun formatVideoTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×"

fun resolveVideoReferer(
    playbackMetadata: VideoPlaybackMetadata?,
    fallback: String = BilibiliEndpoints.HOME,
): String {
    val bvid = playbackMetadata?.bvid?.takeIf { it.isNotBlank() } ?: return fallback
    if (bvid.startsWith("pgc", ignoreCase = true)) return fallback
    return "https://www.bilibili.com/video/$bvid"
}

fun resolvePlaybackReferer(
    playbackKey: String,
    playbackMetadata: VideoPlaybackMetadata? = null,
    fallback: String = BilibiliEndpoints.HOME,
): String {
    val playbackId = playbackKey.substringAfter(':', playbackKey)
    if (playbackId.startsWith("pgc:")) {
        val epid = playbackId.removePrefix("pgc:")
        if (epid.isNotBlank()) {
            return "https://www.bilibili.com/bangumi/play/ep$epid"
        }
    }
    playbackMetadata?.bvid?.takeIf { it.isNotBlank() && !it.startsWith("pgc", ignoreCase = true) }
        ?.let { return "https://www.bilibili.com/video/$it" }
    if (playbackId.startsWith("BV")) {
        return "https://www.bilibili.com/video/${playbackId.substringBefore(":cid:")}"
    }
    return fallback
}

fun createExoPlayer(
    context: Context,
    stream: BiliPlayStream,
    startPositionMs: Long = 0L,
    playbackMetadata: VideoPlaybackMetadata? = null,
    referer: String = resolveVideoReferer(playbackMetadata),
    onReady: (ExoPlayer) -> Unit = {},
): ExoPlayer {
    val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
    return ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, true)
        .build()
        .apply {
            volume = 1f
            val startMs = startPositionMs.coerceAtLeast(0L)
            setMediaSource(
                buildVideoMediaSource(context, stream, playbackMetadata, referer = referer),
                startMs,
            )
            playWhenReady = true
            prepare()
            play()
            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            if (playWhenReady && !isPlaying) {
                                play()
                            }
                            onReady(this@apply)
                        }
                    }
                },
            )
        }
}
