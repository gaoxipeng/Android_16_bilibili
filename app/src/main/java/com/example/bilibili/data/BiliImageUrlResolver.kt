package com.example.bilibili.data

object BiliImageUrlResolver {
    private val HdslbSizeSuffixPattern = Regex("""@[^/?#]+(?=$|[?#])""")

    private val LowQualitySizePattern = Regex(
        """@\d+w_\d+h(?:_\d+)?(?:\.(?:webp|jpg|jpeg|png|gif))?$""",
        RegexOption.IGNORE_CASE,
    )

    fun stripSizeSuffix(url: String): String {
        if (url.isBlank()) return url
        if (!url.contains("hdslb.com", ignoreCase = true)) return url
        var normalized = url.trim()
        while (HdslbSizeSuffixPattern.containsMatchIn(normalized)) {
            normalized = normalized.replace(HdslbSizeSuffixPattern, "")
        }
        return normalized
    }

    fun fullscreenCandidatesFromUrl(url: String): List<String> {
        if (url.isBlank()) return emptyList()
        val stripped = stripSizeSuffix(url)
        return buildList {
            add(stripped)
            if (stripped != url) add(url)
        }.distinct()
    }

    fun thumbnailCandidates(image: BiliViewerImage): List<String> =
        buildList {
            image.thumbnailUrl.takeIf { it.isNotBlank() }?.let(::add)
            image.largeUrl.takeIf { it.isNotBlank() }?.let(::add)
            image.downloadUrls.filter { it.isNotBlank() }.forEach(::add)
        }.distinct()

    fun fullscreenCandidates(image: BiliViewerImage): List<String> {
        val highQuality = (image.downloadUrls + listOf(image.largeUrl, image.thumbnailUrl))
            .filter { it.isNotBlank() }
            .map(::stripSizeSuffix)
            .distinct()
            .filterNot { LowQualitySizePattern.containsMatchIn(it) }
        if (highQuality.isNotEmpty()) return highQuality
        return listOfNotNull(
            stripSizeSuffix(image.largeUrl).takeIf { it.isNotBlank() },
            image.thumbnailUrl.takeIf { it.isNotBlank() },
        ).distinct()
    }

    fun saveCandidates(image: BiliViewerImage): List<String> = fullscreenCandidates(image)
}
