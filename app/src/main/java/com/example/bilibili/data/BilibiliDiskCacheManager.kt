package com.example.bilibili.data

import android.content.Context
import android.os.SystemClock
import java.io.File

/** Keeps all app-owned, recoverable disk cache data below a single budget. */
object BilibiliDiskCacheManager {
    const val MAX_CACHE_BYTES: Long = 1L * 1024L * 1024L * 1024L

    private const val MIN_CHECK_INTERVAL_MS = 60_000L
    private val lock = Any()
    @Volatile
    private var lastCheckElapsedMs = 0L

    fun enforce(context: Context) {
        enforceInternal(context, force = true)
    }

    fun maybeEnforce(context: Context) {
        enforceInternal(context, force = false)
    }

    private fun enforceInternal(context: Context, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (!force && now - lastCheckElapsedMs < MIN_CHECK_INTERVAL_MS) return
            lastCheckElapsedMs = now
            trim(context.applicationContext)
        }
    }

    private fun trim(context: Context) {
        var files = collectCacheFiles(context)
        var totalBytes = files.sumOf(File::length)
        if (totalBytes <= MAX_CACHE_BYTES) return

        // Playback positions are recoverable metadata. Remove them first so a large
        // SharedPreferences file cannot prevent the actual binary cache from being trimmed.
        BilibiliPlayerPreferences(context).clearPlaybackPositions()
        files = collectCacheFiles(context)
        totalBytes = files.sumOf(File::length)
        if (totalBytes <= MAX_CACHE_BYTES) return

        val protectedFiles = setOf(playbackPreferencesFile(context).absoluteFile)
        files.filterNot { it.absoluteFile in protectedFiles }
            .sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.absolutePath })
            .forEach { file ->
                if (totalBytes <= MAX_CACHE_BYTES) return@forEach
                val size = file.length()
                if (file.delete()) totalBytes -= size
            }
    }

    private fun collectCacheFiles(context: Context): List<File> {
        val files = linkedSetOf<File>()

        fun collect(file: File) {
            if (file.isFile) {
                files += file
            } else if (file.isDirectory) {
                file.listFiles()?.forEach(::collect)
            }
        }

        context.cacheDir.listFiles()?.forEach(::collect)
        context.dataDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(WEBVIEW_DATA_DIR_PREFIX) }
            ?.forEach { root -> collectWebViewCacheDirs(root, ::collect) }
        listOf(
            File(context.filesDir, HOME_FEED_CACHE_FILE),
            File(context.filesDir, USER_PROFILE_SNAPSHOTS_FILE),
            playbackPreferencesFile(context),
        ).forEach(::collect)
        return files.toList()
    }

    private fun collectWebViewCacheDirs(root: File, collect: (File) -> Unit) {
        if (root.name in WEBVIEW_CACHE_DIR_NAMES) {
            collect(root)
            return
        }
        root.listFiles()
            ?.filter(File::isDirectory)
            ?.forEach { child -> collectWebViewCacheDirs(child, collect) }
    }

    private fun playbackPreferencesFile(context: Context): File =
        File(context.applicationInfo.dataDir, "shared_prefs/$PLAYER_PREFERENCES_FILE")

    private const val HOME_FEED_CACHE_FILE = "home_feed_cache.json"
    private const val USER_PROFILE_SNAPSHOTS_FILE = "user_profile_snapshots.json"
    private const val PLAYER_PREFERENCES_FILE = "bilibili_player_prefs.xml"
    private const val WEBVIEW_DATA_DIR_PREFIX = "app_webview"
    private val WEBVIEW_CACHE_DIR_NAMES = setOf("Cache", "Code Cache", "GPUCache", "CacheStorage")
}
