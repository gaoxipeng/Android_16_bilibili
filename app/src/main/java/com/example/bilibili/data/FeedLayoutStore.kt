package com.example.bilibili.data

import android.content.Context

class FeedLayoutStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readColumnCount(): Int =
        prefs.getInt(KEY_COLUMN_COUNT, COLUMN_COUNT_TWO).coerceIn(COLUMN_COUNT_ONE, COLUMN_COUNT_TWO)

    fun writeColumnCount(count: Int) {
        prefs.edit()
            .putInt(KEY_COLUMN_COUNT, count.coerceIn(COLUMN_COUNT_ONE, COLUMN_COUNT_TWO))
            .apply()
    }

    companion object {
        const val COLUMN_COUNT_ONE = 1
        const val COLUMN_COUNT_TWO = 2

        private const val PREFS_NAME = "bilibili_feed_layout_prefs"
        private const val KEY_COLUMN_COUNT = "feed_column_count"
    }
}
