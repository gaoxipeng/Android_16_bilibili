package com.example.bilibili.data

import android.content.Context

class BottomBarDisplaySettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readAutoHideOnScroll(): Boolean = prefs.getBoolean(KEY_AUTO_HIDE_ON_SCROLL, true)

    fun writeAutoHideOnScroll(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIDE_ON_SCROLL, enabled).apply()
    }

    fun readBottomBarFrostAmount(): Float =
        normalizeBottomBarGlassAmount(prefs.getFloat(KEY_BOTTOM_BAR_FROST, 0f))

    fun writeBottomBarFrostAmount(value: Float) {
        prefs.edit().putFloat(KEY_BOTTOM_BAR_FROST, normalizeBottomBarGlassAmount(value)).apply()
    }

    fun readBottomBarGlassAmount(): Float =
        normalizeBottomBarGlassAmount(prefs.getFloat(KEY_BOTTOM_BAR_GLASS, 1f))

    fun writeBottomBarGlassAmount(value: Float) {
        prefs.edit().putFloat(KEY_BOTTOM_BAR_GLASS, normalizeBottomBarGlassAmount(value)).apply()
    }

    private companion object {
        const val PREFS_NAME = "bilibili_app_prefs"
        const val KEY_AUTO_HIDE_ON_SCROLL = "bottom_bar_auto_hide_on_scroll"
        const val KEY_BOTTOM_BAR_FROST = "bottom_bar_frost_amount"
        const val KEY_BOTTOM_BAR_GLASS = "bottom_bar_glass_amount"
    }
}
