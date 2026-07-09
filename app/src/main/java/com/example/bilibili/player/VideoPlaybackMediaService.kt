package com.example.bilibili.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.bilibili.R

class VideoPlaybackMediaService : MediaSessionService() {
    private var foregroundStarted = false

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        activeService = this
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(MEDIA_PLAYBACK_CHANNEL_ID)
                .setChannelName(R.string.media_playback_channel_name)
                .build(),
        )
        promoteToForegroundIfNeeded()
        VideoPlaybackMediaBridge.getSession()?.let(::attachSession)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForegroundIfNeeded()
        VideoPlaybackMediaBridge.getSession()?.let(::attachSession)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        VideoPlaybackMediaBridge.getSession()

    override fun onDestroy() {
        foregroundStarted = false
        activeService = null
        super.onDestroy()
    }

    private fun promoteToForegroundIfNeeded() {
        if (foregroundStarted) return
        startForeground(
            NOTIFICATION_ID,
            buildForegroundNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        foregroundStarted = true
    }

    private fun buildForegroundNotification(): Notification {
        ensureNotificationChannel()
        return NotificationCompat.Builder(this, MEDIA_PLAYBACK_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.media_playback_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(MEDIA_PLAYBACK_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            MEDIA_PLAYBACK_CHANNEL_ID,
            getString(R.string.media_playback_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.media_playback_channel_name)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val MEDIA_PLAYBACK_CHANNEL_ID = "bilibili_media_playback"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var activeService: VideoPlaybackMediaService? = null

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
