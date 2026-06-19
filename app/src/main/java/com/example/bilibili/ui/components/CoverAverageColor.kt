package com.example.bilibili.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberCoverAverageColor(
    coverUrl: String,
    fallback: Color = Color(0xFF3A3A3C),
): Color {
    val context = LocalContext.current
    var color by remember(coverUrl) { mutableStateOf(fallback) }

    LaunchedEffect(coverUrl) {
        val average = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .size(64)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap = (result as? SuccessResult)?.drawable
                    ?.let { it as? BitmapDrawable }
                    ?.bitmap
                bitmap?.averageColor()
            }.getOrNull()
        }
        if (average != null) {
            color = average
        }
    }

    return color
}

internal fun Color.contrastingOverlayTextColor(): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return if (luminance > 0.62f) Color(0xFF1A1A1A) else Color.White
}

private fun Bitmap.averageColor(): Color {
    if (width <= 0 || height <= 0) {
        return Color.Gray
    }

    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)

    var r = 0L
    var g = 0L
    var b = 0L
    for (pixel in pixels) {
        r += (pixel shr 16) and 0xFF
        g += (pixel shr 8) and 0xFF
        b += pixel and 0xFF
    }
    val count = pixels.size
    return Color(
        red = r.toFloat() / count / 255f,
        green = g.toFloat() / count / 255f,
        blue = b.toFloat() / count / 255f,
    )
}
