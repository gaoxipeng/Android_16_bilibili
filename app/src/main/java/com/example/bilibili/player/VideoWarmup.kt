package com.example.bilibili.player

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.bilibili.data.BiliPlayStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Small RAM-only prefixes: never starts a player, audio focus or media notification. */
internal object VideoWarmup {
    private val prefixes = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {}
    private const val BUDGET = 24 * 1024 * 1024

    @Synchronized private fun get(url: String) = prefixes[url]
    @Synchronized private fun put(url: String, bytes: ByteArray) {
        prefixes[url] = bytes
        while (prefixes.values.sumOf { it.size } > BUDGET) {
            prefixes.remove(prefixes.keys.first())
        }
    }

    suspend fun preload(stream: BiliPlayStream, factory: DataSource.Factory) = withContext(Dispatchers.IO) {
        listOfNotNull(stream.videoUrl to 2 * 1024 * 1024, stream.audioUrl?.let { it to 256 * 1024 })
            .forEach { (url, limit) ->
                ensureActive()
                if (get(url) != null || url.contains("m3u8") || url.contains(".mpd")) return@forEach
                val source = factory.createDataSource()
                try {
                    source.open(DataSpec.Builder().setUri(url).setLength(limit.toLong()).build())
                    val output = ByteArrayOutputStream(limit)
                    val buffer = ByteArray(16 * 1024)
                    while (output.size() < limit) {
                        ensureActive()
                        val count = source.read(buffer, 0, minOf(buffer.size, limit - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    if (output.size() > 0) put(url, output.toByteArray())
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Speculative work must never prevent normal playback.
                } finally { runCatching { source.close() } }
            }
    }

    fun factory(upstream: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val network = upstream.createDataSource()
        object : DataSource by network {
            private var cached: ByteArray? = null
            private var request: DataSpec? = null
            private var position = 0
            private var remaining = 0L
            private var networkOpen = false
            override fun open(dataSpec: DataSpec): Long {
                request = dataSpec
                cached = get(dataSpec.uri.toString())?.takeIf { dataSpec.position < it.size }
                position = dataSpec.position.toInt()
                remaining = dataSpec.length
                if (cached == null) {
                    networkOpen = true
                    return network.open(dataSpec)
                }
                return dataSpec.length
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                if (remaining == 0L) return -1
                val prefix = cached
                if (prefix != null && position < prefix.size) {
                    val count = minOf(length, prefix.size - position,
                        if (remaining < 0) Int.MAX_VALUE else remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    prefix.copyInto(buffer, offset, position, position + count)
                    position += count
                    if (remaining > 0) remaining -= count
                    return count
                }
                if (!networkOpen) {
                    val original = checkNotNull(request)
                    network.open(original.subrange(position.toLong() - original.position))
                    networkOpen = true
                }
                return network.read(buffer, offset, length)
            }
            override fun getUri() = request?.uri
            override fun close() {
                if (networkOpen) network.close()
                networkOpen = false
                cached = null
                request = null
            }
        }
    }
}
