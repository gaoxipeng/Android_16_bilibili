package com.example.bilibili.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    private val mediaSessionCallback by lazy {
        VideoPlaybackMediaSessionCallback(
            context = appContext ?: throw IllegalStateException("VideoPlaybackMediaBridge not initialized"),
            controlsProvider = { episodeControls },
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
            ensureServiceStarted(context)
            VideoPlaybackMediaService.attachCurrentSession(session)
            return
        }
        releaseSessionLocked()
        sessionPlaybackKey = playbackKey
        sessionPlayer = player
        episodeControls = MediaEpisodeControls.EMPTY
        val pendingIntent = PendingIntent.getActivity(
            context,
            playbackKey.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_BVID, metadata.bvid)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val wrappedPlayer = EpisodeNavigationPlayer(player).also { navigationPlayer = it }
        session = MediaSession.Builder(context, wrappedPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(mediaSessionCallback)
            .setCustomLayout(
                VideoPlaybackMediaSessionCallback.buildEpisodeControlButtons(context, episodeControls),
            )
            .build()
        ensureServiceStarted(context)
        VideoPlaybackMediaService.attachCurrentSession(session)
    }

    @Synchronized
    fun updateEpisodeControls(controls: MediaEpisodeControls, playbackKey: String) {
        if (sessionPlaybackKey != playbackKey) return
        episodeControls = controls
        navigationPlayer?.updateControls(controls)
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
        updateEpisodeControls(MediaEpisodeControls.EMPTY, playbackKey)
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
    private fun releaseSessionLocked() {
        session?.let { currentSession ->
            VideoPlaybackMediaService.detachCurrentSession(currentSession)
            currentSession.release()
        }
        session = null
        sessionPlayer = null
        navigationPlayer = null
        sessionPlaybackKey = null
        episodeControls = MediaEpisodeControls.EMPTY
    }

    private fun ensureServiceStarted(context: Context) {
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
