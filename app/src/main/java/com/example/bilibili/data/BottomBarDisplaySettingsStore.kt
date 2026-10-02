package com.example.bilibili.data

import android.content.Context

class BottomBarDisplaySettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readAutoHideOnScroll(): Boolean = prefs.getBoolean(KEY_AUTO_HIDE_ON_SCROLL, true)

    fun writeAutoHideOnScroll(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIDE_ON_SCROLL, enabled).apply()
    }

    private companion object {
        const val PREFS_NAME = "bilibili_app_prefs"
        const val KEY_AUTO_HIDE_ON_SCROLL = "bottom_bar_auto_hide_on_scroll"
    }
}
