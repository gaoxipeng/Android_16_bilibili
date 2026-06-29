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
    var peekPlaybackKey by mutableStateOf<String?>(null)
    var pendingPeekHandoffKey by mutableStateOf<String?>(null)
    val positions = mutableStateMapOf<String, Long>()
    var danmakuVisible by mutableStateOf(initialDanmakuVisible)
        private set
    var danmakuSettings by mutableStateOf(initialDanmakuSettings)
        private set
    private val danmakuCache = mutableMapOf<Long, List<BiliDanmakuItem>>()

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

    fun requestInlinePlayback(key: String) {
        pausePeek()
        pauseInlineOnly()
        activeKey = key
        fullscreenKey = null
    }

    fun openFullscreen(key: String) {
        pausePeek()
        pauseInlineOnly()
        activeKey = key
        fullscreenKey = key
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
        peekPlaybackKey = null
        pendingPeekHandoffKey = null
        releaseHandoffPlayer()
    }

    fun closeFullscreen() {
        fullscreenKey = null
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

    fun stashPlayer(key: String, player: ExoPlayer) {
        if (handoffKey != key) {
            releaseHandoffPlayer()
        }
        handoffKey = key
        savePlaybackPosition(key, player.currentPosition)
        handoffPlayer = player.apply {
            playWhenReady = false
            pause()
        }
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
