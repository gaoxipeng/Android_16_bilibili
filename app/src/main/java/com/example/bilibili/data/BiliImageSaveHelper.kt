package com.example.bilibili.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object BiliImageSaveHelper {
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private const val PER_IMAGE_SAVE_TIMEOUT_MS = 35_000L
    private const val PER_IMAGE_DOWNLOAD_TIMEOUT_MS = 18_000L
    private const val PER_DOWNLOAD_CONNECT_TIMEOUT_MS = 8_000
    private const val PER_DOWNLOAD_READ_TIMEOUT_MS = 12_000
    private const val BATCH_PREFETCH_AHEAD = 2

    data class SaveAllProgress(
        val completed: Int,
        val total: Int,
        val activeIndex: Int,
    )

    data class SaveAllImagesResult(
        val saved: Int,
        val total: Int,
        val errors: List<String>,
    )

    suspend fun loadBytes(image: BiliViewerImage): ByteArray? = withContext(Dispatchers.IO) {
        BiliImageUrlResolver.saveCandidates(image).firstNotNullOfOrNull { url ->
            runCatching { downloadBytes(url) }.getOrNull()
        }
    }

    suspend fun saveImage(
        context: Context,
        image: BiliViewerImage,
        uniqueSuffix: String = "",
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(PER_IMAGE_SAVE_TIMEOUT_MS) {
                saveImageInternal(context, image, uniqueSuffix)
            }
        }.getOrElse { error ->
            Result.failure(error)
        }
    }

    private suspend fun saveImageInternal(
        context: Context,
        image: BiliViewerImage,
        uniqueSuffix: String,
        preloadedBytes: ByteArray? = null,
    ): Result<String> {
        val bytes = preloadedBytes ?: loadBytesWithTimeout(image)
            ?: return Result.failure(IllegalStateException("图片下载失败"))
        return saveStaticImage(context, image, bytes, uniqueSuffix)
    }

    private suspend fun loadBytesWithTimeout(
        image: BiliViewerImage,
        timeoutMs: Long = PER_IMAGE_DOWNLOAD_TIMEOUT_MS,
    ): ByteArray? = withTimeoutOrNull(timeoutMs) {
        loadBytes(image)
    }

    private fun saveStaticImage(
        context: Context,
        image: BiliViewerImage,
        bytes: ByteArray,
        uniqueSuffix: String,
    ): Result<String> {
        val isGif = image.isGif || looksLikeGif(bytes)
        val mime = if (isGif) "image/gif" else "image/jpeg"
        val ext = if (isGif) "gif" else "jpg"
        val displayName = "bilibili_${sanitize(image.id)}_${System.nanoTime()}${uniqueSuffix}.$ext"
        val uri = insertMedia(
            context = context,
            displayName = displayName,
            mimeType = mime,
            relativePath = "${Environment.DIRECTORY_PICTURES}/Bilibili",
        ) ?: return Result.failure(IllegalStateException("无法写入相册"))
        val writeResult = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                when {
                    isGif -> output.write(bytes)
                    looksLikeJpeg(bytes) -> output.write(bytes)
                    else -> {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?: throw IllegalStateException("图片解码失败")
                        if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, output)) {
                            throw IllegalStateException("图片保存失败")
                        }
                    }
                }
            } ?: throw IllegalStateException("无法打开输出流")
            finalizeMedia(context, uri)
        }
        if (writeResult.isFailure) {
            context.contentResolver.delete(uri, null, null)
            return Result.failure(
                writeResult.exceptionOrNull() ?: IllegalStateException("图片保存失败"),
            )
        }
        return Result.success(displayName)
    }

    suspend fun shareImage(context: Context, image: BiliViewerImage): Result<Unit> =
        withContext(Dispatchers.IO) {
            val bytes = loadBytes(image) ?: return@withContext Result.failure(
                IllegalStateException("图片下载失败"),
            )
            val isGif = image.isGif || looksLikeGif(bytes)
            val ext = if (isGif) "gif" else "jpg"
            val mime = if (isGif) "image/gif" else "image/jpeg"
            val file = File(context.cacheDir, "share_${sanitize(image.id)}_${System.currentTimeMillis()}.$ext")
            file.writeBytes(bytes)
            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "分享图片"))
            }
            Result.success(Unit)
        }

    suspend fun saveAllImages(
        context: Context,
        images: List<BiliViewerImage>,
        onProgress: (suspend (SaveAllProgress) -> Unit)? = null,
    ): SaveAllImagesResult = withContext(Dispatchers.IO) {
        if (images.isEmpty()) {
            return@withContext SaveAllImagesResult(saved = 0, total = 0, errors = listOf("没有可保存的图片"))
        }
        coroutineScope {
            var saved = 0
            val errors = mutableListOf<String>()
            val prefetchJobs = mutableMapOf<Int, kotlinx.coroutines.Deferred<ByteArray?>>()

            fun schedulePrefetch(index: Int) {
                if (index !in images.indices || prefetchJobs.containsKey(index)) return
                prefetchJobs[index] = async { loadBytesWithTimeout(images[index]) }
            }

            for (index in 0..minOf(BATCH_PREFETCH_AHEAD, images.lastIndex)) {
                schedulePrefetch(index)
            }

            images.forEachIndexed { index, image ->
                val activeIndex = index + 1
                onProgress?.invoke(
                    SaveAllProgress(completed = saved, total = images.size, activeIndex = activeIndex),
                )
                schedulePrefetch(index + BATCH_PREFETCH_AHEAD + 1)
                val prefetchJob = prefetchJobs.remove(index)
                val preloadedBytes = prefetchJob?.await()
                val result = runCatching {
                    withTimeout(PER_IMAGE_SAVE_TIMEOUT_MS) {
                        saveImageInternal(
                            context = context,
                            image = image,
                            uniqueSuffix = "_$index",
                            preloadedBytes = preloadedBytes,
                        )
                    }
                }.getOrElse { Result.failure(it) }
                val finalResult = if (result.isFailure) {
                    delay(350L)
                    runCatching {
                        withTimeout(PER_IMAGE_SAVE_TIMEOUT_MS) {
                            saveImageInternal(
                                context = context,
                                image = image,
                                uniqueSuffix = "_$index",
                                preloadedBytes = null,
                            )
                        }
                    }.getOrElse { Result.failure(it) }
                } else {
                    result
                }
                finalResult
                    .onSuccess {
                        saved += 1
                        onProgress?.invoke(
                            SaveAllProgress(
                                completed = saved,
                                total = images.size,
                                activeIndex = activeIndex,
                            ),
                        )
                    }
                    .onFailure { error ->
                        errors += error.message?.takeIf { it.isNotBlank() }
                            ?: "第 $activeIndex 张保存失败"
                    }
            }
            SaveAllImagesResult(saved = saved, total = images.size, errors = errors)
        }
    }

    private fun downloadBytes(url: String, maxBytes: Int = 32 * 1024 * 1024): ByteArray {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = PER_DOWNLOAD_CONNECT_TIMEOUT_MS
                    readTimeout = PER_DOWNLOAD_READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Referer", "https://www.bilibili.com/")
                }
                return connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    val output = ByteArrayOutputStream()
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        if (output.size() > maxBytes) {
                            throw IllegalStateException("文件体积过大")
                        }
                    }
                    output.toByteArray()
                }
            } catch (error: Exception) {
                lastError = error
                if (attempt < 1) {
                    Thread.sleep(200L)
                }
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    private fun insertMedia(
        context: Context,
        displayName: String,
        mimeType: String,
        relativePath: String,
    ): Uri? =
        context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        )

    private fun finalizeMedia(context: Context, uri: Uri) {
        context.contentResolver.update(
            uri,
            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            },
            null,
            null,
        )
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("""[^\w.-]"""), "_").ifBlank { "image" }

    private fun looksLikeGif(bytes: ByteArray): Boolean =
        bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte()

    private fun looksLikeJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
}
