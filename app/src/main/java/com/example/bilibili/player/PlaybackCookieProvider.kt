package com.example.bilibili.player

object PlaybackCookieProvider {
    @Volatile
    var cookieHeader: String? = null

    fun update(cookie: String?) {
        cookieHeader = cookie?.takeIf { it.isNotBlank() }
    }
}
