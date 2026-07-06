package com.example.bilibili.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import com.example.bilibili.data.BiliDanmakuItem
import com.example.bilibili.data.DanmakuSettings
import java.util.Collections
import java.util.IdentityHashMap

class VideoPlaybackCoordinator(
    initialDanmakuVisible: Boolean = true,
    initialDanmakuSettings: DanmakuSettings = DanmakuSettings(),
    private val persistDanmakuVisible: ((Boolean) -> Unit)? = null,
    private val persistDanmakuSettings: ((DanmakuSettings) -> Unit)? = null,
) {
    var activeKey by mutableStateOf<String?>(null)
    var fullscreenKey by mutableStateOf<String?>(null)
    var fullscreenPortraitVideo by mutableStateOf<Boolean?>(null)
    var fullscreenOrientationLocked by mutableStateOf(true)
        private set
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
    private val videoShotsByCid = mutableMapOf<Long, com.example.bilibili.data.BiliVideoShot?>()
    private val videoAspectRatiosByKey = mutableMapOf<String, Float>()

    fun cacheVideoShot(cid: Long, shot: com.example.bilibili.data.BiliVideoShot?) {
        if (cid > 0L) {
            videoShotsByCid[cid] = shot
        }
    }

    fun cachedVideoShot(cid: Long): com.example.bilibili.data.BiliVideoShot? =
        videoShotsByCid[cid]

    fun cacheVideoAspectRatio(playbackKey: String, aspectRatio: Float?) {
        if (aspectRatio != null && aspectRatio > 0f) {
            videoAspectRatiosByKey[playbackPositionKey(playbackKey)] = aspectRatio
        }
    }

    fun cachedVideoAspectRatio(playbackKey: String): Float? =
        videoAspectRatiosByKey[playbackPositionKey(playbackKey)]

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
    private val releasedPlayers =
        Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap<ExoPlayer, Boolean>()))
    @Volatile
    var playbackStopping: Boolean = false
        private set
    private val inlinePauseHandlers = mutableMapOf<String, () -> Unit>()
    private val fullscreenPauseHandlers = mutableMapOf<String, () -> Unit>()
    private val peekPauseHandlers = mutableMapOf<String, () -> Unit>()
    private val handoffPrepareHandlers = mutableSetOf<(String) -> Unit>()
    private val keepScreenOnOwners = mutableSetOf<String>()
    var keepScreenOnRequested by mutableStateOf(false)
        private set

    fun setKeepScreenOn(playbackKey: String, enabled: Boolean) {
        if (enabled) {
            keepScreenOnOwners += playbackKey
        } else {
            keepScreenOnOwners -= playbackKey
        }
        keepScreenOnRequested = keepScreenOnOwners.isNotEmpty()
    }

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
        pauseInlineOnly(exceptKey = key)
        pauseFullscreenOnly()
        activeKey = key
        fullscreenKey = null
        fullscreenPortraitVideo = null
        fullscreenOrientationLocked = true
    }

    fun openFullscreen(key: String, portraitVideo: Boolean? = null) {
        pausePeek()
        activeKey = key
        fullscreenKey = key
        fullscreenPortraitVideo = portraitVideo
        fullscreenOrientationLocked = true
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
        pauseFullscreenOnly()
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
        pauseAll()
    }

    fun pauseAll() {
        peekPauseHandlers.values.forEach { it() }
        inlinePauseHandlers.values.forEach { it() }
        fullscreenPauseHandlers.values.forEach { it() }
    }

    fun stopPlayback() {
        playbackStopping = true
        keepScreenOnOwners.clear()
        keepScreenOnRequested = false
        pauseAll()
        handoffKey?.let { key ->
            handoffPlayer?.let { player ->
                savePlaybackPosition(key, player.currentPosition)
            }
        }
        activeKey = null
        fullscreenKey = null
        fullscreenPortraitVideo = null
        fullscreenOrientationLocked = true
        peekPlaybackKey = null
        pendingPeekHandoffKey = null
        VideoShotImageLoader.clearCaches()
        playbackStopping = false
    }

    fun releasePlayerOnce(player: ExoPlayer?) {
        player ?: return
        if (!releasedPlayers.add(player)) return
        runCatching {
            player.playWhenReady = false
            player.pause()
            player.release()
        }
    }

    fun clearReleasedPlayers() {
        releasedPlayers.clear()
    }

    fun closeFullscreen() {
        val key = fullscreenKey
        if (key != null) {
            prepareInlineHandoff(key)
        }
        fullscreenKey = null
        fullscreenPortraitVideo = null
        fullscreenOrientationLocked = true
    }

    fun updateFullscreenPortrait(portraitVideo: Boolean) {
        if (fullscreenKey == null) return
        fullscreenPortraitVideo = portraitVideo
    }

    fun releaseFullscreenOrientationLock(key: String) {
        if (fullscreenKey != key) return
        fullscreenOrientationLocked = false
    }

    fun getPlaybackPosition(playbackKey: String): Long =
        positions[playbackPositionKey(playbackKey)] ?: 0L

    fun savePlaybackPosition(playbackKey: String, positionMs: Long) {
        positions[playbackPositionKey(playbackKey)] = positionMs.coerceAtLeast(0L)
    }

    fun registerInlinePauseHandler(playbackKey: String, handler: () -> Unit) {
        inlinePauseHandlers[playbackKey] = handler
    }

    fun unregisterInlinePauseHandler(playbackKey: String) {
        inlinePauseHandlers.remove(playbackKey)
    }

    fun registerFullscreenPauseHandler(playbackKey: String, handler: () -> Unit) {
        fullscreenPauseHandlers[playbackKey] = handler
    }

    fun unregisterFullscreenPauseHandler(playbackKey: String) {
        fullscreenPauseHandlers.remove(playbackKey)
    }

    fun registerPeekPauseHandler(playbackKey: String, handler: () -> Unit) {
        peekPauseHandlers[playbackKey] = handler
    }

    fun unregisterPeekPauseHandler(playbackKey: String) {
        peekPauseHandlers.remove(playbackKey)
    }

    fun pausePeek(exceptKey: String? = null) {
        peekPauseHandlers.forEach { (key, handler) ->
            if (exceptKey != null && key == exceptKey) return@forEach
            handler()
        }
    }

    fun pauseInlineOnly(exceptKey: String? = null) {
        inlinePauseHandlers.forEach { (key, handler) ->
            if (exceptKey != null && key == exceptKey) return@forEach
            handler()
        }
    }

    fun pauseFullscreenOnly(exceptKey: String? = null) {
        fullscreenPauseHandlers.forEach { (key, handler) ->
            if (exceptKey != null && key == exceptKey) return@forEach
            handler()
        }
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
        val player = handoffPlayer
        handoffPlayer = null
        releasePlayerOnce(player)
    }
}

fun videoPlaybackKey(bvid: String, ownerId: String = "feed"): String = "$ownerId:$bvid"

fun playbackPositionKey(playbackKey: String): String =
    playbackKey.substringAfter(':', missingDelimiterValue = playbackKey)
