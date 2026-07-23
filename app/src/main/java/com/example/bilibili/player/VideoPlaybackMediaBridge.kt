package com.example.bilibili.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.bilibili.MainActivity
import com.example.bilibili.data.BiliVideoItem

object VideoPlaybackMediaBridge {
    const val EXTRA_OPEN_BVID = "open_bvid"
    const val EXTRA_OPEN_CID = "open_cid"
    const val EXTRA_OPEN_AID = "open_aid"
    const val EXTRA_OPEN_EPID = "open_epid"
    const val EXTRA_OPEN_TITLE = "open_title"
    const val EXTRA_OPEN_ARTIST = "open_artist"
    const val EXTRA_OPEN_COVER = "open_cover"
    const val EXTRA_OPEN_AUTHOR_MID = "open_author_mid"
    const val EXTRA_OPEN_DURATION = "open_duration"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var session: MediaSession? = null

    @Volatile
    private var sessionPlayer: ExoPlayer? = null

    @Volatile
    private var sessionNavigationPlayer: EpisodeNavigationPlayer? = null

    @Volatile
    private var sessionPlaybackKey: String? = null

    @Volatile
    private var episodeControls: MediaEpisodeControls = MediaEpisodeControls.EMPTY

    @Volatile
    private var publishedLayoutSignature: String? = null

    @Volatile
    private var episodeControlsProviderPlaybackKey: String? = null

    @Volatile
    private var episodeControlsProvider: (() -> MediaEpisodeControls)? = null

    @Volatile
    private var playbackMetadataProviderPlaybackKey: String? = null

    @Volatile
    private var playbackMetadataProvider: (() -> VideoPlaybackMetadata)? = null

    @Volatile
    private var cachedPlaybackMetadata: VideoPlaybackMetadata? = null

    @Volatile
    private var publishedMetadataSignature: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val mediaSessionCallback by lazy {
        VideoPlaybackMediaSessionCallback(
            context = appContext ?: throw IllegalStateException("VideoPlaybackMediaBridge not initialized"),
            controlsProvider = ::resolveEpisodeControls,
            onEpisodeNavigationRequest = ::dispatchEpisodeNavigation,
        )
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun attach(
        playbackKey: String,
        player: ExoPlayer,
        metadata: VideoPlaybackMetadata,
    ) {
        val context = appContext ?: return
        if (sessionPlayer === player && session != null) {
            sessionPlaybackKey = playbackKey
            updatePlaybackMetadata(playbackKey, player, metadata)
            publishEpisodeControls(resolveEpisodeControls(), forceLayout = true)
            ensureServiceStarted(context)
            VideoPlaybackMediaService.attachCurrentSession(session)
            return
        }
        val keepControlsProvider = episodeControlsProviderPlaybackKey == playbackKey
        val keepMetadataProvider = playbackMetadataProviderPlaybackKey == playbackKey
        releaseSessionLocked(
            clearControlsProvider = !keepControlsProvider,
            clearMetadataProvider = !keepMetadataProvider,
        )
        sessionPlaybackKey = playbackKey
        sessionPlayer = player
        val navigationPlayer = EpisodeNavigationPlayer(player, ::resolveEpisodeControls)
        sessionNavigationPlayer = navigationPlayer
        val initialControls = resolveEpisodeControls()
        episodeControls = initialControls
        publishedLayoutSignature = null
        val pendingIntent = PendingIntent.getActivity(
            context,
            playbackKey.hashCode(),
            buildSessionActivityIntent(context, metadata),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(context, navigationPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(mediaSessionCallback)
            .setCustomLayout(
                VideoPlaybackMediaSessionCallback.buildEpisodeControlButtons(context, initialControls),
            )
            .build()
        updatePlaybackMetadata(playbackKey, player, metadata)
        publishEpisodeControls(initialControls, forceLayout = true)
        ensureServiceStarted(context)
        VideoPlaybackMediaService.attachCurrentSession(session)
    }

    @Synchronized
    fun updatePlaybackMetadata(
        playbackKey: String,
        player: ExoPlayer,
        metadata: VideoPlaybackMetadata,
    ) {
        if (sessionPlaybackKey != playbackKey) return
        val signature =
            "${metadata.title}:${metadata.artworkUrl}:${metadata.bvid}:${metadata.cid}:${metadata.epid}"
        if (signature == publishedMetadataSignature) return
        publishedMetadataSignature = signature
        cachedPlaybackMetadata = metadata
        applyPlayerMediaMetadata(player, metadata)

        val context = appContext ?: return
        val currentSession = session ?: return
        val pendingIntent = PendingIntent.getActivity(
            context,
            playbackKey.hashCode(),
            buildSessionActivityIntent(context, metadata),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        currentSession.setSessionActivity(pendingIntent)
    }

    private fun applyPlayerMediaMetadata(player: ExoPlayer, metadata: VideoPlaybackMetadata) {
        val index = player.currentMediaItemIndex
        if (index !in 0 until player.mediaItemCount) return
        val item = player.getMediaItemAt(index)
        val mediaMetadata = metadata.toMediaMetadata()
        if (item.mediaMetadata == mediaMetadata) return
        val position = player.currentPosition
        player.replaceMediaItem(
            index,
            item.buildUpon().setMediaMetadata(mediaMetadata).build(),
        )
        if (player.currentPosition != position) {
            player.seekTo(position)
        }
    }

    @Synchronized
    fun setPlaybackMetadataProvider(playbackKey: String, provider: (() -> VideoPlaybackMetadata)?) {
        if (provider == null) {
            if (playbackMetadataProviderPlaybackKey == playbackKey) {
                playbackMetadataProvider = null
                playbackMetadataProviderPlaybackKey = null
            }
            return
        }
        playbackMetadataProviderPlaybackKey = playbackKey
        playbackMetadataProvider = provider
    }

    @Synchronized
    fun refreshPlaybackMetadata(playbackKey: String) {
        if (sessionPlaybackKey != playbackKey) return
        val player = sessionPlayer ?: return
        updatePlaybackMetadata(playbackKey, player, resolvePlaybackMetadata())
    }

    fun pushEpisodeMetadata(playbackKey: String, metadata: VideoPlaybackMetadata) {
        mainHandler.post {
            val player = sessionPlayer ?: return@post
            updatePlaybackMetadata(playbackKey, player, metadata)
        }
    }

    @Synchronized
    private fun resolvePlaybackMetadata(): VideoPlaybackMetadata {
        val playbackKey = sessionPlaybackKey
        if (playbackKey != null && playbackKey == playbackMetadataProviderPlaybackKey) {
            playbackMetadataProvider?.invoke()?.let { return it }
        }
        return cachedPlaybackMetadata ?: VideoPlaybackMetadata(
            title = "哔哩哔哩视频",
            artist = "哔哩哔哩",
            artworkUrl = "",
            bvid = playbackKey?.substringAfter(':', "").orEmpty(),
        )
    }

    @Synchronized
    fun setEpisodeControlsProvider(playbackKey: String, provider: (() -> MediaEpisodeControls)?) {
        if (provider == null) {
            if (episodeControlsProviderPlaybackKey == playbackKey) {
                episodeControlsProvider = null
                episodeControlsProviderPlaybackKey = null
            }
            return
        }
        episodeControlsProviderPlaybackKey = playbackKey
        episodeControlsProvider = provider
    }

    @Synchronized
    fun refreshEpisodeControls(playbackKey: String) {
        if (sessionPlaybackKey != playbackKey) return
        publishEpisodeControls(resolveEpisodeControls())
    }

    fun dispatchEpisodeNavigation(previous: Boolean) {
        mainHandler.post {
            val controls = resolveEpisodeControls()
            if (previous) {
                controls.onPrevious?.invoke()
            } else {
                controls.onNext?.invoke()
            }
        }
    }

    @Synchronized
    private fun resolveEpisodeControls(): MediaEpisodeControls {
        val playbackKey = sessionPlaybackKey
        if (playbackKey == null || playbackKey != episodeControlsProviderPlaybackKey) {
            return episodeControls
        }
        return episodeControlsProvider?.invoke() ?: episodeControls
    }

    @Synchronized
    private fun publishEpisodeControls(
        controls: MediaEpisodeControls,
        forceLayout: Boolean = false,
    ) {
        episodeControls = controls

        val layoutSignature = controls.isMultiEpisode.toString()
        if (!forceLayout && layoutSignature == publishedLayoutSignature) return
        publishedLayoutSignature = layoutSignature

        val context = appContext ?: return
        val currentSession = session ?: return
        currentSession.setCustomLayout(
            VideoPlaybackMediaSessionCallback.buildEpisodeControlButtons(context, controls),
        )
        val playerCommands = VideoPlaybackMediaSessionCallback.buildAvailablePlayerCommands(controls)
        currentSession.connectedControllers.forEach { controller ->
            currentSession.setAvailableCommands(
                controller,
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                playerCommands,
            )
        }
    }

    @Synchronized
    fun clearEpisodeControls(playbackKey: String) {
        if (sessionPlaybackKey != playbackKey) return
        publishedLayoutSignature = null
        publishEpisodeControls(MediaEpisodeControls.EMPTY, forceLayout = true)
    }

    @Synchronized
    fun detach(playbackKey: String) {
        if (sessionPlaybackKey != playbackKey) return
        releaseSessionLocked()
        stopServiceIfNeeded()
    }

    @Synchronized
    fun detachAll() {
        if (session == null) return
        releaseSessionLocked()
        stopServiceIfNeeded()
    }

    @Synchronized
    fun getSession(): MediaSession? = session

    @Synchronized
    private fun releaseSessionLocked(
        clearControlsProvider: Boolean = true,
        clearMetadataProvider: Boolean = true,
    ) {
        session?.let { currentSession ->
            VideoPlaybackMediaService.detachCurrentSession(currentSession)
            currentSession.release()
        }
        session = null
        sessionPlayer = null
        sessionNavigationPlayer = null
        sessionPlaybackKey = null
        episodeControls = MediaEpisodeControls.EMPTY
        publishedLayoutSignature = null
        publishedMetadataSignature = null
        cachedPlaybackMetadata = null
        if (clearControlsProvider) {
            episodeControlsProvider = null
            episodeControlsProviderPlaybackKey = null
        }
        if (clearMetadataProvider) {
            playbackMetadataProvider = null
            playbackMetadataProviderPlaybackKey = null
        }
    }

    private fun ensureServiceStarted(context: Context) {
        if (VideoPlaybackMediaService.isActive()) return
        runCatching {
            // MediaSessionService promotes itself when playback actually requires a foreground
            // notification. Starting it as an FGS here races with short-lived BUFFERING/PAUSED
            // sessions: the session can be removed before Media3 posts its notification, after
            // which Android kills the app for not calling startForeground() in time.
            context.startService(Intent(context, VideoPlaybackMediaService::class.java))
        }
    }

    private fun stopServiceIfNeeded() {
        val context = appContext ?: return
        runCatching {
            context.stopService(Intent(context, VideoPlaybackMediaService::class.java))
        }
    }

    private fun buildSessionActivityIntent(
        context: Context,
        metadata: VideoPlaybackMetadata,
    ): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_OPEN_BVID, metadata.bvid)
            putExtra(EXTRA_OPEN_CID, metadata.cid)
            putExtra(EXTRA_OPEN_AID, metadata.aid)
            putExtra(EXTRA_OPEN_EPID, metadata.epid)
            putExtra(EXTRA_OPEN_TITLE, metadata.title)
            putExtra(EXTRA_OPEN_ARTIST, metadata.artist)
            putExtra(EXTRA_OPEN_COVER, metadata.artworkUrl)
            putExtra(EXTRA_OPEN_AUTHOR_MID, metadata.authorMid)
            putExtra(EXTRA_OPEN_DURATION, metadata.durationSeconds)
        }

    /**
     * Reads and clears media-notification open extras so configuration changes
     * do not reopen the same video repeatedly.
     */
    fun consumeOpenVideoExtra(intent: Intent?): BiliVideoItem? {
        if (intent == null) return null
        val bvid = intent.getStringExtra(EXTRA_OPEN_BVID).orEmpty()
        val epid = intent.getLongExtra(EXTRA_OPEN_EPID, 0L)
        if (bvid.isBlank() && epid <= 0L) return null
        val video = BiliVideoItem(
            bvid = bvid.ifBlank { if (epid > 0L) "pgc:$epid" else "" },
            aid = intent.getLongExtra(EXTRA_OPEN_AID, 0L),
            title = intent.getStringExtra(EXTRA_OPEN_TITLE).orEmpty(),
            coverUrl = intent.getStringExtra(EXTRA_OPEN_COVER).orEmpty(),
            authorName = intent.getStringExtra(EXTRA_OPEN_ARTIST).orEmpty(),
            authorMid = intent.getLongExtra(EXTRA_OPEN_AUTHOR_MID, 0L),
            viewCount = 0L,
            danmakuCount = 0L,
            likeCount = 0L,
            durationSeconds = intent.getIntExtra(EXTRA_OPEN_DURATION, 0),
            cid = intent.getLongExtra(EXTRA_OPEN_CID, 0L),
            epid = epid,
        )
        intent.removeExtra(EXTRA_OPEN_BVID)
        intent.removeExtra(EXTRA_OPEN_CID)
        intent.removeExtra(EXTRA_OPEN_AID)
        intent.removeExtra(EXTRA_OPEN_EPID)
        intent.removeExtra(EXTRA_OPEN_TITLE)
        intent.removeExtra(EXTRA_OPEN_ARTIST)
        intent.removeExtra(EXTRA_OPEN_COVER)
        intent.removeExtra(EXTRA_OPEN_AUTHOR_MID)
        intent.removeExtra(EXTRA_OPEN_DURATION)
        return video
    }
}
