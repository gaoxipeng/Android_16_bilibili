package com.example.bilibili.data

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val FullscreenDefaultMaxDecodeDim = 4096
private const val FullscreenLongImageMaxDecodeDim = 8192
private const val FullscreenLongImageOriginalMaxPixels = 32_000_000
private const val FullscreenBitmapCacheMaxBytes = 160 * 1024 * 1024
private const val RemoteBytesCacheMaxTotal = 32 * 1024 * 1024
private const val RemoteBytesMaxCachedEntry = 8 * 1024 * 1024

private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

object BiliImageBitmapLoader {
    fun fullscreenCandidates(image: BiliViewerImage): List<String> =
        BiliImageUrlResolver.fullscreenCandidates(image)

    fun isFullscreenQuality(bitmap: Bitmap, image: BiliViewerImage): Boolean {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        val expectedWidth = image.width ?: 0
        val expectedHeight = image.height ?: 0
        val decodeDim = fullscreenDecodeDim(image)
        if (expectedWidth > 0 && expectedHeight > 0) {
            val expectedMax = maxOf(expectedWidth, expectedHeight)
            val targetMax = min((expectedMax * 0.85f).roundToInt(), decodeDim)
            return maxDim >= targetMax
        }
        return maxDim >= (decodeDim * 0.75f).roundToInt()
    }

    fun fullscreenDecodeDim(image: BiliViewerImage): Int {
        val width = image.width ?: 0
        val height = image.height ?: 0
        if (width <= 0 || height <= 0) return FullscreenDefaultMaxDecodeDim
        val longSide = maxOf(width, height)
        val shortSide = min(width, height).coerceAtLeast(1)
        return if (longSide.toFloat() / shortSide.toFloat() >= 3f) {
            val pixelCount = width.toLong() * height.toLong()
            if (pixelCount <= FullscreenLongImageOriginalMaxPixels) {
                longSide
            } else {
                FullscreenLongImageMaxDecodeDim
            }
        } else {
            FullscreenDefaultMaxDecodeDim
        }
    }

    fun resolvePreviewBitmap(image: BiliViewerImage): Bitmap? {
        FullscreenBitmapCache.get(fullscreenCandidates(image))
            ?.takeIfDrawable()
            ?.let { return it }
        previewUrlCandidates(image).forEach { url ->
            decodeCachedRemoteBitmap(url, maxDecodeDim = 960)?.takeIfDrawable()?.let { return it }
        }
        return null
    }

    private fun previewUrlCandidates(image: BiliViewerImage): List<String> =
        listOfNotNull(
            image.thumbnailUrl.takeIf { it.isNotBlank() },
            image.largeUrl.takeIf { it.isNotBlank() },
        ).distinct()

    suspend fun loadPreviewBitmap(image: BiliViewerImage): Bitmap? {
        resolvePreviewBitmap(image)?.let { return it }
        for (url in previewUrlCandidates(image)) {
            runCatching {
                loadRemoteBitmap(
                    url = url,
                    maxDecodeDim = 960,
                    connectTimeoutMs = 5_000,
                    readTimeoutMs = 8_000,
                )
            }.getOrNull()?.let { return it }
        }
        return null
    }

    fun loadFullscreenBitmap(image: BiliViewerImage): Bitmap? {
        val candidates = fullscreenCandidates(image)
        val decodeDim = fullscreenDecodeDim(image)
        candidates.forEach { url ->
            FullscreenBitmapCache.get(url)?.takeIf { isFullscreenQuality(it, image) }?.let { return it }
        }
        candidates.forEach { url ->
            runCatching {
                val bytes = fetchRemoteBytes(
                    url = url,
                    connectTimeoutMs = 10_000,
                    readTimeoutMs = 20_000,
                    maxReadBytes = remoteReadLimitForDecodeDim(decodeDim),
                )
                decodeBitmapFromBytes(bytes, decodeDim)
            }.getOrNull()?.let { bitmap ->
                FullscreenBitmapCache.put(url, bitmap)
                return bitmap
            }
        }
        return null
    }

    fun getCachedFullscreen(image: BiliViewerImage): Bitmap? =
        FullscreenBitmapCache.get(fullscreenCandidates(image))?.takeIfDrawable()

    private fun remoteReadLimitForDecodeDim(maxDecodeDim: Int): Int = when {
        maxDecodeDim <= 960 -> 4 * 1024 * 1024
        maxDecodeDim <= FullscreenDefaultMaxDecodeDim -> 16 * 1024 * 1024
        else -> 48 * 1024 * 1024
    }

    private fun decodeBitmapFromBytes(bytes: ByteArray, maxDecodeDim: Int): Bitmap? =
        BitmapExifOrientation.decodeSampledBitmap(bytes, maxDecodeDim)

    private fun Bitmap?.takeIfDrawable(): Bitmap? = this?.takeIf { !it.isRecycled }

    private fun loadRemoteBitmap(
        url: String,
        maxDecodeDim: Int,
        connectTimeoutMs: Int = 8_000,
        readTimeoutMs: Int = 8_000,
    ): Bitmap? {
        decodeCachedRemoteBitmap(url, maxDecodeDim)?.let { return it }
        val bytes = fetchRemoteBytes(
            url = url,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            maxReadBytes = remoteReadLimitForDecodeDim(maxDecodeDim),
        )
        return decodeBitmapFromBytes(bytes, maxDecodeDim)
    }

    private fun decodeCachedRemoteBitmap(url: String, maxDecodeDim: Int): Bitmap? {
        val bytes = RemoteBytesCache.get(url) ?: return null
        return decodeBitmapFromBytes(bytes, maxDecodeDim)
    }

    private fun fetchRemoteBytes(
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        maxReadBytes: Int = RemoteBytesMaxCachedEntry,
    ): ByteArray {
        RemoteBytesCache.get(url)?.takeIf { it.size <= maxReadBytes }?.let { return it }
        val bytes = URL(url).openConnection().apply {
            (this as HttpURLConnection).connectTimeout = connectTimeoutMs
            this.readTimeout = readTimeoutMs
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Referer", "https://www.bilibili.com/")
        }.inputStream.use { input ->
            readRemoteBytesLimited(input, maxReadBytes)
        }
        RemoteBytesCache.put(url, bytes)
        return bytes
    }

    private fun readRemoteBytesLimited(input: java.io.InputStream, maxReadBytes: Int): ByteArray {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            if (output.size() > maxReadBytes) {
                throw IllegalStateException("文件体积过大")
            }
        }
        return output.toByteArray()
    }
}

private object FullscreenBitmapCache {
    private val cache = BitmapByteCache(FullscreenBitmapCacheMaxBytes)

    fun get(url: String): Bitmap? = cache.get(url)

    fun get(urls: List<String>): Bitmap? = cache.get(urls)

    fun put(url: String, bitmap: Bitmap) {
        cache.put(url, bitmap)
    }
}

private object RemoteBytesCache {
    private var currentBytes = 0
    private val entries = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean {
            if (eldest == null || currentBytes <= RemoteBytesCacheMaxTotal) return false
            currentBytes -= eldest.value.size
            return true
        }
    }

    @Synchronized
    fun get(url: String): ByteArray? = entries[url]

    @Synchronized
    fun put(url: String, bytes: ByteArray) {
        if (bytes.size > RemoteBytesMaxCachedEntry) return
        entries.remove(url)?.let { removed ->
            currentBytes -= removed.size
        }
        entries[url] = bytes
        currentBytes += bytes.size
        while (currentBytes > RemoteBytesCacheMaxTotal && entries.isNotEmpty()) {
            val eldest = entries.entries.firstOrNull() ?: break
            entries.remove(eldest.key)
            currentBytes -= eldest.value.size
        }
    }
}

private class BitmapByteCache(private val maxBytes: Int) {
    private var currentBytes = 0
    private val entries = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            if (eldest == null || currentBytes <= maxBytes) return false
            currentBytes = (currentBytes - eldest.value.allocationByteCount).coerceAtLeast(0)
            return true
        }
    }

    @Synchronized
    fun get(key: String): Bitmap? {
        val bitmap = entries[key] ?: return null
        if (bitmap.isRecycled) {
            entries.remove(key)
            return null
        }
        return bitmap
    }

    @Synchronized
    fun get(keys: List<String>): Bitmap? {
        keys.forEach { key ->
            get(key)?.let { return it }
        }
        return null
    }

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        entries.remove(key)?.let { removed ->
            currentBytes = (currentBytes - removed.allocationByteCount).coerceAtLeast(0)
        }
        entries[key] = bitmap
        currentBytes += bitmap.allocationByteCount
    }
}
