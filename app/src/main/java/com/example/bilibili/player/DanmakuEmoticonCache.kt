package com.example.bilibili.player

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.example.bilibili.data.BilibiliEndpoints
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val danmakuEmoticonBitmapCache = object : LruCache<String, ImageBitmap>(128) {}

@Composable
internal fun rememberDanmakuEmoticonBitmaps(urls: List<String>): Map<String, ImageBitmap> {
    val context = LocalContext.current
    val bitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }

    LaunchedEffect(urls) {
        urls.distinct().forEach { url ->
            if (url.isBlank() || url in bitmaps) return@forEach
            danmakuEmoticonBitmapCache.get(url)?.let {
                bitmaps[url] = it
                return@forEach
            }
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .addHeader("User-Agent", BilibiliEndpoints.USER_AGENT)
                        .addHeader("Referer", BilibiliEndpoints.LIVE_HOME)
                        .size(192)
                        .allowHardware(false)
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                        .build()
                    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
                        ?: return@runCatching null
                    drawable.toBitmap(
                        width = drawable.intrinsicWidth.coerceAtLeast(1),
                        height = drawable.intrinsicHeight.coerceAtLeast(1),
                        config = Bitmap.Config.ARGB_8888,
                    ).asImageBitmap()
                }.getOrNull()
            }
            if (bitmap != null) {
                danmakuEmoticonBitmapCache.put(url, bitmap)
                bitmaps[url] = bitmap
            }
        }
    }

    return bitmaps
}
