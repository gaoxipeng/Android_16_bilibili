package com.example.bilibili.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberShowFullVideoTitles(): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) { context.applicationContext.getSharedPreferences("bilibili_feed_layout_prefs", Context.MODE_PRIVATE) }
    var value by remember(prefs) { mutableStateOf(prefs.getBoolean("show_full_video_title", true)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "show_full_video_title") value = prefs.getBoolean(key, true)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        value = prefs.getBoolean("show_full_video_title", true)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

class FeedLayoutStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readColumnCount(): Int =
        prefs.getInt(KEY_COLUMN_COUNT, COLUMN_COUNT_TWO).coerceIn(COLUMN_COUNT_ONE, COLUMN_COUNT_TWO)

    fun writeColumnCount(count: Int) {
        prefs.edit()
            .putInt(KEY_COLUMN_COUNT, count.coerceIn(COLUMN_COUNT_ONE, COLUMN_COUNT_TWO))
            .apply()
    }

    fun readShowFullTitle(): Boolean = prefs.getBoolean(KEY_SHOW_FULL_TITLE, true)

    fun writeShowFullTitle(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FULL_TITLE, show).apply()
    }

    companion object {
        const val COLUMN_COUNT_ONE = 1
        const val COLUMN_COUNT_TWO = 2

        private const val PREFS_NAME = "bilibili_feed_layout_prefs"
        private const val KEY_COLUMN_COUNT = "feed_column_count"
        private const val KEY_SHOW_FULL_TITLE = "show_full_video_title"
    }
}
