package com.example.bilibili.player

fun isPortraitVideoSize(
    width: Int,
    height: Int,
    rotationDegrees: Int = 0,
): Boolean {
    if (width <= 0 || height <= 0) return false
    val rotated = rotationDegrees == 90 || rotationDegrees == 270
    val displayWidth = if (rotated) height else width
    val displayHeight = if (rotated) width else height
    return displayHeight > displayWidth
}

fun knownPortraitVideoHint(videoWidth: Int, videoHeight: Int): Boolean? =
    if (videoWidth > 0 && videoHeight > 0) videoHeight > videoWidth else null
