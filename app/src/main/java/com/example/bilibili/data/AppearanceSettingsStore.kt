package com.example.bilibili.data

import android.content.Context

class AppearanceSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readAppearanceMode(): AppearanceMode =
        AppearanceMode.fromStorage(
            prefs.getString(KEY_APPEARANCE_MODE, AppearanceMode.System.storageValue),
        )

    fun writeAppearanceMode(mode: AppearanceMode) {
        prefs.edit().putString(KEY_APPEARANCE_MODE, mode.storageValue).apply()
    }

    private companion object {
        const val PREFS_NAME = "bilibili_app_prefs"
        const val KEY_APPEARANCE_MODE = "appearance_mode"
    }
}
