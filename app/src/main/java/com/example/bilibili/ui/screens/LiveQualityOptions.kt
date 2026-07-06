package com.example.bilibili.ui.screens

import com.example.bilibili.data.BiliLiveQuality

internal const val LIVE_QUALITY_AUTO_ID = "auto"
internal const val LIVE_MAX_QN = 10_000

internal data class LiveQualityOption(
    val id: String,
    val qn: Int,
    val label: String,
    val isAuto: Boolean = false,
) {
    val requestQn: Int
        get() = if (isAuto) 250 else qn

    val capsuleLabel: String
        get() = when {
            isAuto -> "自动"
            id == "blue" -> "1080P"
            id == "super" -> "720P"
            id == "high" -> "480P"
            else -> label.substringBefore('(').trim()
        }
}

internal val STANDARD_LIVE_QUALITY_OPTIONS = listOf(
    LiveQualityOption(id = "blue", qn = 400, label = "1080P"),
    LiveQualityOption(id = "super", qn = 250, label = "720P 超清"),
    LiveQualityOption(id = "high", qn = 150, label = "480P 高清"),
    LiveQualityOption(LIVE_QUALITY_AUTO_ID, 250, "自动 (720P 超清)", isAuto = true),
)

internal fun resolveLiveQualityOptions(
    apiQualities: List<BiliLiveQuality>,
): List<LiveQualityOption> = STANDARD_LIVE_QUALITY_OPTIONS
