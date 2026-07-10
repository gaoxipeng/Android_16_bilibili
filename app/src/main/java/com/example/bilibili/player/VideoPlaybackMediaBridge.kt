package com.example.bilibili.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.bilibili.MainActivity

object VideoPlaybackMediaBridge {
    const val EXTRA_OPEN_BVID = "open_bvid"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var session: MediaSession? = null

    @Volatile
    private var sessionPlayer: ExoPlayer? = null

    @Volatile
    private var navigationPlayer: EpisodeNavigationPlayer? = null

    @Volatile
    private var sessionPlaybackKey: String? = null

    @Volatile
    private var episodeControls: MediaEpisodeControls = MediaEpisodeControls.EMPTY

    @Volatile
    private var publishedLayoutSignature: String? = null

    @Volatile
    private var publishedPlayerSignature: String? = null

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
        val initialControls = resolveEpisodeControls()
        episodeControls = initialControls
        publishedLayoutSignature = null
        publishedPlayerSignature = null
        val pendingIntent = PendingIntent.getActivity(
            context,
            playbackKey.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_BVID, metadata.bvid)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val wrappedPlayer = EpisodeNavigationPlayer(player).also { wrapped ->
            wrapped.updateControls(initialControls)
            wrapped.updateMediaMetadata(metadata.toMediaMetadata())
            navigationPlayer = wrapped
        }
        session = MediaSession.Builder(context, wrappedPlayer)
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
        val signature = "${metadata.title}:${metadata.artworkUrl}:${metadata.bvid}"
        if (signature == publishedMetadataSignature) return
        publishedMetadataSignature = signature
        cachedPlaybackMetadata = metadata
        val mediaMetadata = metadata.toMediaMetadata()

        navigationPlayer?.updateMediaMetadata(mediaMetadata)
        applyPlayerMediaMetadata(player, metadata)

        val context = appContext ?: return
        val currentSession = session ?: return
        val pendingIntent = PendingIntent.getActivity(
            context,
            playbackKey.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_BVID, metadata.bvid)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        currentSession.setSessionActivity(pendingIntent)
    }

    private fun applyPlayerMediaMetadata(player: ExoPlayer, metadata: VideoPlaybackMetadata) {
        val mediaMetadata = metadata.toMediaMetadata()
        val index = player.currentMediaItemIndex
        if (index !in 0 until player.mediaItemCount) return
        val item = player.getMediaItemAt(index)
        if (item.mediaMetadata == mediaMetadata) return
        player.replaceMediaItem(
            index,
            item.buildUpon().setMediaMetadata(mediaMetadata).build(),
        )
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

        val playerSignature = "${controls.isMultiEpisode}:${controls.hasPrevious}:${controls.hasNext}"
        if (playerSignature != publishedPlayerSignature) {
            publishedPlayerSignature = playerSignature
            navigationPlayer?.updateControls(controls)
        }

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
        publishedPlayerSignature = null
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
        navigationPlayer = null
        sessionPlaybackKey = null
        episodeControls = MediaEpisodeControls.EMPTY
        publishedLayoutSignature = null
        publishedPlayerSignature = null
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
            ContextCompat.startForegroundService(
                context,
                Intent(context, VideoPlaybackMediaService::class.java),
            )
        }
    }

    private fun stopServiceIfNeeded() {
        val context = appContext ?: return
        runCatching {
            context.stopService(Intent(context, VideoPlaybackMediaService::class.java))
        }
    }
}
