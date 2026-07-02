package com.example.bilibili.player

import androidx.annotation.OptIn
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
                .setChannelId(MEDIA_PLAYBACK_CHANNEL_ID)
                .setChannelName(R.string.media_playback_channel_name)
                .build(),
        )
        VideoPlaybackMediaBridge.getSession()?.let(::attachSession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        VideoPlaybackMediaBridge.getSession()

    override fun onDestroy() {
        activeService = null
        super.onDestroy()
    }

    companion object {
        const val MEDIA_PLAYBACK_CHANNEL_ID = "bilibili_media_playback"

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
