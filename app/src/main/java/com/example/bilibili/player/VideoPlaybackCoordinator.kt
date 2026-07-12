package com.example.bilibili.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import com.example.bilibili.data.BiliDanmakuItem
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.DanmakuSettings
import java.util.Collections
import java.util.IdentityHashMap

class VideoPlaybackCoordinator(
    initialDanmakuVisible: Boolean = true,
    initialDanmakuSettings: DanmakuSettings = DanmakuSettings(),
    private val persistDanmakuVisible: ((Boolean) -> Unit)? = null,
    private val persistDanmakuSettings: ((DanmakuSettings) -> Unit)? = null,
    private val readPersistedPosition: (String) -> Long = { 0L },
    private val writePersistedPosition: (String, Long) -> Unit = { _, _ -> },
) {
    var activeKey by mutableStateOf<String?>(null)
    var fullscreenKey by mutableStateOf<String?>(null)
    var fullscreenPortraitVideo by mutableStateOf<Boolean?>(null)
    var fullscreenOrientationLocked by mutableStateOf(true)
        private set
    var fullscreenVideo by mutableStateOf<BiliVideoItem?>(null)
    var fullscreenStream by mutableStateOf<BiliPlayStream?>(null)
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
    private var handoffPositionKey: String? = null
    private val releasedPlayers =
        Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap<ExoPlayer, Boolean>()))
    @Volatile
    var playbackStopping: Boolean = false
        private set
    private var detailHandoffPreserveKey: String? = null
    private val inlinePauseHandlers = mutableMapOf<String, () -> Unit>()
    private val fullscreenPauseHandlers = mutableMapOf<String, () -> Unit>()
    private val handoffPrepareHandlers = mutableSetOf<(String) -> Unit>()
    private val keepScreenOnOwners = mutableSetOf<String>()
    var keepScreenOnRequested by mutableStateOf(false)
        private set
    private var episodePickerPlaybackKey: String? = null
    var episodePickerState by mutableStateOf<VideoEpisodePickerState?>(null)
        private set

    fun updateEpisodePicker(playbackKey: String, state: VideoEpisodePickerState?) {
        if (state == null) {
            if (episodePickerPlaybackKey == playbackKey) {
                episodePickerPlaybackKey = null
                episodePickerState = null
            }
            return
        }
        episodePickerPlaybackKey = playbackKey
        episodePickerState = state
    }

    fun episodePickerFor(playbackKey: String): VideoEpisodePickerState? {
        if (playbackKey != episodePickerPlaybackKey) return null
        return episodePickerState
    }

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
        if (fullscreenKey == key) {
            activeKey = key
            return
        }
        pauseInlineOnly(exceptKey = key)
        pauseFullscreenOnly()
        activeKey = key
        fullscreenKey = null
        fullscreenPortraitVideo = null
        fullscreenVideo = null
        fullscreenStream = null
        fullscreenOrientationLocked = true
    }

    fun openFullscreen(
        key: String,
        portraitVideo: Boolean? = null,
        video: BiliVideoItem? = null,
        stream: BiliPlayStream? = null,
    ) {
        activeKey = key
        fullscreenKey = key
        fullscreenPortraitVideo = portraitVideo
        fullscreenVideo = video
        fullscreenStream = stream
        fullscreenOrientationLocked = true
        prepareFullscreenHandoff(key)
    }

    fun updateFullscreenMedia(
        key: String,
        video: BiliVideoItem,
        stream: BiliPlayStream,
    ) {
        if (fullscreenKey != key) return
        fullscreenVideo = video
        fullscreenStream = stream
    }

    fun hasHandoffPlayer(key: String): Boolean = handoffKey == key

    fun requestDetailHandoffPreserve(detailPlaybackKey: String) {
        detailHandoffPreserveKey = detailPlaybackKey
    }

    fun shouldPreserveDetailHandoff(playbackKey: String): Boolean {
        val preserveKey = detailHandoffPreserveKey ?: return false
        if (preserveKey == playbackKey) return true
        val preserveId = preserveKey.substringAfter(':', preserveKey)
        val targetId = playbackKey.substringAfter(':', playbackKey)
        val preserveBvid = preserveId.substringBefore(":cid:")
        val targetBvid = targetId.substringBefore(":cid:")
        return preserveBvid.isNotBlank() && preserveBvid == targetBvid
    }

    fun clearDetailHandoffPreserve() {
        detailHandoffPreserveKey = null
    }

    fun handoffPlaybackKeyForVideo(video: BiliVideoItem, ownerId: String = "detail"): String? {
        val primary = videoPlaybackKey(video.playbackId(), ownerId)
        if (hasHandoffPlayer(primary)) return primary
        val bvid = video.bvid
        if (bvid.isBlank()) return null
        val currentHandoff = handoffKey ?: return null
        val handoffId = currentHandoff.substringAfter(':', currentHandoff)
        if (handoffId == bvid || handoffId.startsWith("$bvid:cid:")) {
            return currentHandoff
        }
        return null
    }

    fun pauseForOverlay() {
        pauseAll()
    }

    fun pauseAll() {
        inlinePauseHandlers.values.toList().forEach { handler ->
            runCatching { handler() }
        }
        fullscreenPauseHandlers.values.toList().forEach { handler ->
            runCatching { handler() }
        }
    }

    fun stopPlayback() {
        playbackStopping = true
        keepScreenOnOwners.clear()
        keepScreenOnRequested = false
        pauseAll()
        handoffKey?.let { key ->
            handoffPlayer?.let { player ->
                savePlaybackPosition(handoffPositionKey ?: key, player.currentPosition)
            }
        }
        activeKey = null
        fullscreenKey = null
        fullscreenPortraitVideo = null
        fullscreenVideo = null
        fullscreenStream = null
        fullscreenOrientationLocked = true
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
        fullscreenVideo = null
        fullscreenStream = null
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

    fun getPlaybackPosition(playbackKey: String): Long {
        val key = playbackPositionKey(playbackKey)
        positions[key]?.let { return it }
        val persisted = readPersistedPosition(key).coerceAtLeast(0L)
        if (persisted > 0L) {
            positions[key] = persisted
        }
        return persisted
    }

    fun savePlaybackPosition(playbackKey: String, positionMs: Long) {
        val key = playbackPositionKey(playbackKey)
        val ms = positionMs.coerceAtLeast(0L)
        positions[key] = ms
        writePersistedPosition(key, ms)
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

    fun stashPlayer(
        key: String,
        player: ExoPlayer,
        keepPlaying: Boolean = false,
        positionKey: String = key,
    ) {
        if (handoffKey != key) {
            releaseHandoffPlayer()
        }
        handoffKey = key
        handoffPositionKey = positionKey
        savePlaybackPosition(positionKey, player.currentPosition)
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
        handoffPositionKey = null
        return handoffPlayer.also { handoffPlayer = null }
    }

    fun releaseHandoffPlayer() {
        handoffKey = null
        handoffPositionKey = null
        val player = handoffPlayer
        handoffPlayer = null
        releasePlayerOnce(player)
    }
}

fun videoPlaybackKey(bvid: String, ownerId: String = "feed"): String = "$ownerId:$bvid"

fun playbackPositionKey(playbackKey: String): String =
    playbackKey.substringAfter(':', missingDelimiterValue = playbackKey)
