package com.example.bilibili.data

import android.content.Context

class BilibiliPlayerPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDanmakuVisible(): Boolean = prefs.getBoolean(KEY_DANMAKU_VISIBLE, true)

    fun setDanmakuVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_DANMAKU_VISIBLE, visible).apply()
    }

    private companion object {
        private const val PREFS_NAME = "bilibili_player_prefs"
        private const val KEY_DANMAKU_VISIBLE = "danmaku_visible"
    }
}
