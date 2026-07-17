package com.example.bilibili.player

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.bilibili.R

class VideoPlaybackMediaService : MediaSessionService() {
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        activeService = this
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(MEDIA_PLAYBACK_NOTIFICATION_ID)
                .setChannelId(MEDIA_PLAYBACK_CHANNEL_ID)
                .setChannelName(R.string.media_playback_channel_name)
                .build(),
        )
        VideoPlaybackMediaBridge.getSession()?.let(::attachSession)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        VideoPlaybackMediaBridge.getSession()?.let(::attachSession)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        VideoPlaybackMediaBridge.getSession()

    @OptIn(UnstableApi::class)
    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        val player = session.player
        val hasActivePlayback =
            player.playWhenReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED

        if (hasActivePlayback) {
            super.onUpdateNotification(session, startInForegroundRequired)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        getSystemService(NotificationManager::class.java)
            .cancel(MEDIA_PLAYBACK_NOTIFICATION_ID)
    }

    override fun onDestroy() {
        activeService = null
        super.onDestroy()
    }

    companion object {
        const val MEDIA_PLAYBACK_CHANNEL_ID = "bilibili_media_playback"
        private const val MEDIA_PLAYBACK_NOTIFICATION_ID = 1001

        @Volatile
        private var activeService: VideoPlaybackMediaService? = null

        fun isActive(): Boolean = activeService != null

        fun attachCurrentSession(session: MediaSession?) {
            val service = activeService ?: return
            if (session != null) {
                service.attachSession(session)
            }
        }

        fun detachCurrentSession(session: MediaSession?) {
            val service = activeService ?: return
            if (session != null && service.isSessionAdded(session)) {
                service.removeSession(session)
            }
        }
    }

    private fun attachSession(session: MediaSession) {
        if (!isSessionAdded(session)) {
            addSession(session)
        }
    }
}
