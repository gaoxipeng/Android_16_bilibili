package com.example.bilibili.data

import android.content.Context

class BilibiliPlayerPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDanmakuVisible(): Boolean = prefs.getBoolean(KEY_DANMAKU_VISIBLE, true)

    fun setDanmakuVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_DANMAKU_VISIBLE, visible).apply()
    }

    fun readDanmakuSettings(): DanmakuSettings {
        val savedArea = prefs.getInt(
            KEY_DANMAKU_DISPLAY_AREA,
            DanmakuSettings.DISPLAY_AREA_OPTIONS.last(),
        )
        val displayArea = DanmakuSettings.DISPLAY_AREA_OPTIONS
            .firstOrNull { it == savedArea }
            ?: DanmakuSettings.DISPLAY_AREA_OPTIONS.last()
        return DanmakuSettings(
            displayAreaPercent = displayArea,
            opacityPercent = prefs.getInt(KEY_DANMAKU_OPACITY, 100).coerceIn(10, 100),
            fontSizePercent = prefs.getInt(KEY_DANMAKU_FONT_SIZE, 100).coerceIn(50, 170),
            speedLevel = DanmakuSpeedLevel.fromIndex(
                prefs.getInt(KEY_DANMAKU_SPEED, DanmakuSpeedLevel.Medium.ordinal),
            ),
        )
    }

    fun setDanmakuSettings(settings: DanmakuSettings) {
        prefs.edit()
            .putInt(KEY_DANMAKU_DISPLAY_AREA, settings.displayAreaPercent)
            .putInt(KEY_DANMAKU_OPACITY, settings.opacityPercent)
            .putInt(KEY_DANMAKU_FONT_SIZE, settings.fontSizePercent)
            .putInt(KEY_DANMAKU_SPEED, settings.speedLevel.ordinal)
            .apply()
    }

    fun readBackgroundPlaybackEnabled(): Boolean =
        prefs.getBoolean(KEY_BACKGROUND_PLAYBACK, false)

    fun writeBackgroundPlaybackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_PLAYBACK, enabled).apply()
    }

    private companion object {
        private const val PREFS_NAME = "bilibili_player_prefs"
        private const val KEY_BACKGROUND_PLAYBACK = "background_playback_enabled"
        private const val KEY_DANMAKU_VISIBLE = "danmaku_visible"
        private const val KEY_DANMAKU_DISPLAY_AREA = "danmaku_display_area"
        private const val KEY_DANMAKU_OPACITY = "danmaku_opacity"
        private const val KEY_DANMAKU_FONT_SIZE = "danmaku_font_size"
        private const val KEY_DANMAKU_SPEED = "danmaku_speed"
    }
}
