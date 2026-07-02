package com.example.bilibili.util

object BiliArticleUrl {
    private val cvPattern = Regex("""(?:/read/)?cv(\d+)""", RegexOption.IGNORE_CASE)
    private val opusPattern = Regex("""/opus/(\d+)""", RegexOption.IGNORE_CASE)

    fun extractCvId(url: String): Long? =
        cvPattern.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }

    fun extractOpusId(url: String): Long? =
        opusPattern.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }

    fun isArticleLikeUrl(url: String): Boolean =
        extractCvId(url) != null || extractOpusId(url) != null ||
            url.contains("b23.tv", ignoreCase = true) ||
            url.contains("/read/", ignoreCase = true)

    fun buildMobileOpusUrl(opusId: Long): String =
        "https://m.bilibili.com/opus/$opusId?plat_id=5&share_from=article&share_medium=android&share_plat=android"

    fun resolveMobileOpusUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        extractOpusId(trimmed)?.let { return buildMobileOpusUrl(it) }
        return null
    }
}
