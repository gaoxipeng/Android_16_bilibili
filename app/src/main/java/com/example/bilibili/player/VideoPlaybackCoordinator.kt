package com.example.bilibili.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import com.example.bilibili.data.BiliDanmakuItem
import com.example.bilibili.data.DanmakuSettings

class VideoPlaybackCoordinator(
    initialDanmakuVisible: Boolean = true,
    initialDanmakuSettings: DanmakuSettings = DanmakuSettings(),
    private val persistDanmakuVisible: ((Boolean) -> Unit)? = null,
    private val persistDanmakuSettings: ((DanmakuSettings) -> Unit)? = null,
) {
    var activeKey by mutableStateOf<String?>(null)
    var fullscreenKey by mutableStateOf<String?>(null)
    var fullscreenPortraitVideo by mutableStateOf<Boolean?>(null)
    var peekPlaybackKey by mutableStateOf<String?>(null)
    var pendingPeekHandoffKey by mutableStateOf<String?>(null)
    var pendingFullscreenHandoffKey by mutableStateOf<String?>(null)
    var pendingInlineHandoffKey by mutableStateOf<String?>(null)
    val positions = mutableStateMapOf<String, Long>()
    var danmakuVisible by mutableStateOf(initialDanmakuVisible)
        private set
    var danmakuSettings by mutableStateOf(initialDanmakuSettings)
        private set
    private val danmakuCache = mutableMapOf<Long, List<BiliDanmakuItem>>()
    private val danmakuTimelineCache = mutableMapOf<String, DanmakuTimelineSnapshot>()

    data class DanmakuTimelineSnapshot(
        val cid: Long,
        val displayTimeMs: Long,
        val anchorPositionMs: Long,
        val anchorRealtimeMs: Long,
        val nextIndex: Int,
        val spawnedIds: Set<Int>,
    )

    fun loadDanmakuTimeline(playbackKey: String, cid: Long): DanmakuTimelineSnapshot? {
        val snapshot = danmakuTimelineCache[playbackKey] ?: return null
        return snapshot.takeIf { it.cid == cid }
    }

    fun saveDanmakuTimeline(
        playbackKey: String,
        cid: Long,
        displayTimeMs: Long,
        anchorPositionMs: Long,
        anchorRealtimeMs: Long,
        nextIndex: Int,
        spawnedIds: Set<Int>,
    ) {
        if (cid <= 0L) return
        danmakuTimelineCache[playbackKey] = DanmakuTimelineSnapshot(
            cid = cid,
            displayTimeMs = displayTimeMs,
            anchorPositionMs = anchorPositionMs,
            anchorRealtimeMs = anchorRealtimeMs,
            nextIndex = nextIndex,
            spawnedIds = spawnedIds.toSet(),
        )
    }

    fun toggleDanmaku() {
        danmakuVisible = !danmakuVisible
        persistDanmakuVisible?.invoke(danmakuVisible)
    }

    fun updateDanmakuSettings(settings: DanmakuSettings) {
        danmakuSettings = settings
        persistDanmakuSettings?.invoke(settings)
    }

    suspend fun cachedDanmaku(
        cid: Long,
        loader: suspend () -> List<BiliDanmakuItem>,
    ): List<BiliDanmakuItem> {
        danmakuCache[cid]?.takeIf { it.isNotEmpty() }?.let { return it }
        val loaded = loader()
        if (loaded.isNotEmpty()) {
            danmakuCache[cid] = loaded
        }
        return loaded
    }

    private var handoffPlayer: ExoPlayer? = null
    private var handoffKey: String? = null
    private val inlinePauseHandlers = mutableSetOf<() -> Unit>()
    private val peekPauseHandlers = mutableSetOf<() -> Unit>()
    private val handoffPrepareHandlers = mutableSetOf<(String) -> Unit>()

    fun registerHandoffPrepareHandler(handler: (String) -> Unit) {
        handoffPrepareHandlers += handler
    }

    fun unregisterHandoffPrepareHandler(handler: (String) -> Unit) {
        handoffPrepareHandlers -= handler
    }

    private fun prepareHandoff(key: String, pendingSetter: (String?) -> Unit) {
        pendingSetter(key)
        handoffPrepareHandlers.forEach { handler -> handler(key) }
        pendingSetter(null)
    }

    fun prepareFullscreenHandoff(key: String) {
        prepareHandoff(key) { pendingFullscreenHandoffKey = it }
    }

    fun prepareInlineHandoff(key: String) {
        prepareHandoff(key) { pendingInlineHandoffKey = it }
    }

    fun requestInlinePlayback(key: String) {
        pausePeek()
        pauseInlineOnly()
        activeKey = key
        fullscreenKey = null
        fullscreenPortraitVideo = null
    }

    fun openFullscreen(key: String, portraitVideo: Boolean? = null) {
        pausePeek()
        activeKey = key
        fullscreenKey = key
        fullscreenPortraitVideo = portraitVideo
        prepareFullscreenHandoff(key)
    }

    fun beginPeekHandoff(key: String) {
        pendingPeekHandoffKey = key
    }

    fun cancelPeekHandoff(key: String) {
        if (pendingPeekHandoffKey == key) {
            pendingPeekHandoffKey = null
        }
        if (handoffKey == key) {
            releaseHandoffPlayer()
        }
    }

    fun hasHandoffPlayer(key: String): Boolean = handoffKey == key

    fun claimPeekPlayback(key: String) {
        pauseInlineOnly()
        activeKey = null
        fullscreenKey = null
        peekPlaybackKey = key
    }

    fun releasePeekPlayback(key: String) {
        if (peekPlaybackKey == key) {
            peekPlaybackKey = null
        }
    }

    fun pauseForOverlay() {
        pausePeek()
        pauseInlineOnly()
    }

    fun stopPlayback() {
        pausePeek()
        pauseInlineOnly()
        handoffKey?.let { key ->
            handoffPlayer?.let { player ->
                savePlaybackPosition(key, player.currentPosition)
            }
        }
        activeKey = null
        fullscreenKey = null
        fullscreenPortraitVideo = null
        peekPlaybackKey = null
        pendingPeekHandoffKey = null
        releaseHandoffPlayer()
    }

    fun closeFullscreen() {
        val key = fullscreenKey
        if (key != null) {
            prepareInlineHandoff(key)
        }
        fullscreenKey = null
        fullscreenPortraitVideo = null
    }

    fun updateFullscreenPortrait(portraitVideo: Boolean) {
        if (fullscreenKey == null) return
        fullscreenPortraitVideo = portraitVideo
    }

    fun getPlaybackPosition(playbackKey: String): Long =
        positions[playbackPositionKey(playbackKey)] ?: 0L

    fun savePlaybackPosition(playbackKey: String, positionMs: Long) {
        positions[playbackPositionKey(playbackKey)] = positionMs.coerceAtLeast(0L)
    }

    fun registerInlinePauseHandler(handler: () -> Unit) {
        inlinePauseHandlers += handler
    }

    fun unregisterInlinePauseHandler(handler: () -> Unit) {
        inlinePauseHandlers -= handler
    }

    fun registerPeekPauseHandler(handler: () -> Unit) {
        peekPauseHandlers += handler
    }

    fun unregisterPeekPauseHandler(handler: () -> Unit) {
        peekPauseHandlers -= handler
    }

    fun pausePeek() {
        peekPauseHandlers.forEach { it() }
    }

    fun pauseInlineOnly() {
        inlinePauseHandlers.forEach { it() }
    }

    fun stashPlayer(key: String, player: ExoPlayer, keepPlaying: Boolean = false) {
        if (handoffKey != key) {
            releaseHandoffPlayer()
        }
        handoffKey = key
        savePlaybackPosition(key, player.currentPosition)
        if (fullscreenKey == key) {
            portraitVideoFromPlayer(player)?.let { fullscreenPortraitVideo = it }
        }
        handoffPlayer = if (keepPlaying) {
            player
        } else {
            player.apply {
                playWhenReady = false
                pause()
            }
        }
    }

    private fun portraitVideoFromPlayer(player: ExoPlayer): Boolean? {
        val videoSize = player.videoSize
        if (videoSize.width <= 0 || videoSize.height <= 0) return null
        return isPortraitVideoSize(
            width = videoSize.width,
            height = videoSize.height,
            rotationDegrees = videoSize.unappliedRotationDegrees,
        )
    }

    fun consumeHandoffPlayer(key: String): ExoPlayer? {
        if (handoffKey != key) return null
        handoffKey = null
        if (pendingPeekHandoffKey == key) {
            pendingPeekHandoffKey = null
        }
        return handoffPlayer.also { handoffPlayer = null }
    }

    fun releaseHandoffPlayer() {
        handoffKey = null
        handoffPlayer?.release()
        handoffPlayer = null
    }
}

fun videoPlaybackKey(bvid: String, ownerId: String = "feed"): String = "$ownerId:$bvid"

fun playbackPositionKey(playbackKey: String): String =
    playbackKey.substringAfter(':', missingDelimiterValue = playbackKey)
