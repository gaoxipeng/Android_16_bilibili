package com.example.bilibili.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.example.bilibili.data.BiliVideoShot
import com.example.bilibili.data.BilibiliEndpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object VideoShotImageLoader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val maxSpriteCacheBytes = (Runtime.getRuntime().maxMemory() / 16).toInt()
        .coerceIn(4 * 1024 * 1024, 10 * 1024 * 1024)
    private val maxTileCacheBytes = 3 * 1024 * 1024

    private val spriteCache = object : LruCache<String, Bitmap>(maxSpriteCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val tileCache = object : LruCache<String, Bitmap>(maxTileCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val loadMutex = Mutex()

    fun clearCaches() {
        tileCache.evictAll()
        spriteCache.evictAll()
    }

    suspend fun preloadSprite(url: String, refererUrl: String = BilibiliEndpoints.HOME) {
        loadSprite(url, refererUrl)
    }

    suspend fun loadTile(
        tile: VideoShotTile,
        videoShot: BiliVideoShot,
        refererUrl: String = BilibiliEndpoints.HOME,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val tileKey = "${tile.imageUrl}#${tile.column}x${tile.row}"
        tileCache.get(tileKey)?.let { return@withContext it }
        val sprite = loadSprite(tile.imageUrl, refererUrl) ?: return@withContext null
        val columns = videoShot.tileColumns.coerceAtLeast(1)
        val rows = videoShot.tileRows.coerceAtLeast(1)
        val tileWidth = sprite.width / columns
        val tileHeight = sprite.height / rows
        if (tileWidth <= 0 || tileHeight <= 0) return@withContext null
        val left = (tile.column * tileWidth).coerceIn(0, (sprite.width - 1).coerceAtLeast(0))
        val top = (tile.row * tileHeight).coerceIn(0, (sprite.height - 1).coerceAtLeast(0))
        val width = tileWidth.coerceAtMost(sprite.width - left)
        val height = tileHeight.coerceAtMost(sprite.height - top)
        if (width <= 0 || height <= 0) return@withContext null
        val cropped = try {
            Bitmap.createBitmap(sprite, left, top, width, height)
        } catch (error: OutOfMemoryError) {
            tileCache.evictAll()
            null
        } catch (error: IllegalArgumentException) {
            null
        } ?: return@withContext null
        tileCache.put(tileKey, cropped)
        cropped
    }

    private suspend fun loadSprite(
        url: String,
        refererUrl: String = BilibiliEndpoints.HOME,
    ): Bitmap? = withContext(Dispatchers.IO) {
        spriteCache.get(url)?.let { return@withContext it }
        loadMutex.withLock {
            spriteCache.get(url)?.let { return@withLock it }
            val bytes = downloadBytes(url, refererUrl) ?: return@withLock null
            val bitmap = try {
                decodeSprite(bytes)
            } catch (error: OutOfMemoryError) {
                spriteCache.evictAll()
                tileCache.evictAll()
                null
            } ?: return@withLock null
            spriteCache.put(url, bitmap)
            bitmap
        }
    }

    private fun downloadBytes(url: String, refererUrl: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("Referer", refererUrl)
            .header("User-Agent", BilibiliEndpoints.USER_AGENT)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            null
        }
    }

    private fun decodeSprite(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1600, 9000)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Int {
        var sampleSize = 1
        if (height > maxHeight || width > maxWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / sampleSize >= maxHeight || halfWidth / sampleSize >= maxWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize.coerceAtLeast(1)
    }
}
