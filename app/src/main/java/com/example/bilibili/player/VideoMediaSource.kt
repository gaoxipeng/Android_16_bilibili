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
    val factory = DefaultDataSource.Factory(context, androidx.media3.datasource.cache.CacheDataSource.Factory()
        .setCache(qualitySwitchCache(context))
        .setUpstreamDataSourceFactory(bilibiliDataSourceFactory(context, referer = referer, origin = origin))
        .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR))
    val mediaMetadata = playbackMetadata?.toMediaMetadata()
    val videoSource = createStreamMediaSource(factory, stream.videoUrl, mediaMetadata)
    val audioUrl = stream.audioUrl?.takeIf { it.isNotBlank() && it != stream.videoUrl }
    if (audioUrl == null) return videoSource
    val audioSource = ProgressiveMediaSource.Factory(factory)
        .createMediaSource(MediaItem.fromUri(audioUrl))
    return MergingMediaSource(videoSource, audioSource)
}

internal fun BiliPlayStream.orderedVideoQualities(): List<Pair<Int, String>> =
    listOf(quality to videoUrl) + qualityUrls.toSortedMap(compareByDescending { it })
        .filterValues { it != videoUrl }.map { it.key to it.value }

private var switchCache: androidx.media3.datasource.cache.SimpleCache? = null
@Synchronized
private fun qualitySwitchCache(context: Context): androidx.media3.datasource.cache.SimpleCache =
    switchCache ?: androidx.media3.datasource.cache.SimpleCache(
        java.io.File(context.applicationContext.cacheDir, "quality-switch-media"),
        androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
        androidx.media3.database.StandaloneDatabaseProvider(context.applicationContext),
    ).also { switchCache = it }

/** Prepare and run the replacement silently against the live player's clock. */
internal suspend fun prepareVideoQuality(
    context: Context, previous: ExoPlayer, stream: BiliPlayStream, quality: Int,
    referer: String, metadata: VideoPlaybackMetadata?, clock: ExoPlayer,
    onCandidate: (ExoPlayer?) -> Unit,
): ExoPlayer {
    val url = stream.qualityUrls[quality] ?: error("接口未返回该清晰度的视频地址")
    val candidate = ExoPlayer.Builder(context.applicationContext).build()
    var adopted = false
    var firstFrame = false
    val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() { firstFrame = true }
    }
    try {
        candidate.addListener(listener)
        candidate.volume = 0f
        candidate.playWhenReady = false
        candidate.playbackParameters = previous.playbackParameters
        candidate.setMediaSource(buildVideoMediaSource(context,
            stream.copy(videoUrl = url, quality = quality, qualityUrls = emptyMap(), videoDashManifest = null),
            metadata, referer), (clock.currentPosition + if (previous.isPlaying) 600L else 0L)
                .coerceAtMost(if (previous.duration > 0) (previous.duration - 100L).coerceAtLeast(0L) else Long.MAX_VALUE))
        onCandidate(candidate)
        candidate.prepare()
        kotlinx.coroutines.withTimeout(12_000L) {
            var runningAt: Long? = null
            var alignedFrames = 0
            var seekIssuedAt = android.os.SystemClock.elapsedRealtime()
            var leadMs = 600L
            while (true) {
                candidate.playerError?.let { throw it }
                val delta = candidate.currentPosition - clock.currentPosition
                val playing = previous.isPlaying
                val parameters = previous.playbackParameters
                if (candidate.playbackState == Player.STATE_READY && firstFrame) {
                    if (!previous.playWhenReady && kotlin.math.abs(delta) <= 100L) break
                    if (playing && runningAt == null && delta in -250L..100L) {
                        candidate.playWhenReady = true
                        runningAt = candidate.currentPosition
                    } else if (runningAt != null && candidate.isPlaying && playing &&
                        candidate.currentPosition > runningAt + 40L && kotlin.math.abs(delta) <= 45L) {
                        if (++alignedFrames >= 2) break
                    }
                    else if (delta < -350L || delta > 3500L || !previous.playWhenReady) {
                        val now = android.os.SystemClock.elapsedRealtime()
                        leadMs = (now - seekIssuedAt + 200L).coerceIn(600L, 2500L)
                        seekIssuedAt = now
                        candidate.pause()
                        runningAt = null
                        // Media3 reports the first frame for the output surface, not every seek.
                        candidate.seekTo((clock.currentPosition + if (playing) (leadMs * parameters.speed).toLong() else 0L)
                            .coerceAtMost(if (previous.duration > 0) (previous.duration - 100L).coerceAtLeast(0L) else Long.MAX_VALUE))
                    }
                } else {
                    alignedFrames = 0
                }
                if (kotlin.math.abs(delta) > 45L || !candidate.isPlaying || !playing) alignedFrames = 0
                val correction = if (playing && runningAt != null)
                    (1f - delta / 600f).coerceIn(0.8f, 1.25f) else 1f
                candidate.playbackParameters = parameters.withSpeed(parameters.speed * correction)
                kotlinx.coroutines.delay(20L)
            }
        }
        candidate.playbackParameters = previous.playbackParameters
        adopted = true
        return candidate
    } finally {
        candidate.removeListener(listener)
        if (!adopted) { onCandidate(null); candidate.release() }
    }
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

internal fun bilibiliDataSourceFactory(
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
    return DefaultDataSource.Factory(context, VideoWarmup.factory(httpFactory))
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
