package com.example.bilibili.data

data class BiliViewerImage(
    val id: String,
    val thumbnailUrl: String,
    val largeUrl: String,
    val downloadUrls: List<String> = emptyList(),
    val width: Int? = null,
    val height: Int? = null,
) {
    val isGif: Boolean
        get() = listOf(largeUrl, thumbnailUrl)
            .plus(downloadUrls)
            .any { it.contains(".gif", ignoreCase = true) }

    companion object {
        fun fromUrl(url: String): BiliViewerImage {
            val candidates = BiliImageUrlResolver.fullscreenCandidatesFromUrl(url)
            val displayUrl = candidates.firstOrNull()?.takeIf { it.isNotBlank() } ?: url
            return BiliViewerImage(
                id = url,
                thumbnailUrl = url,
                largeUrl = displayUrl,
                downloadUrls = candidates,
            )
        }

        fun fromUrls(urls: List<String>): List<BiliViewerImage> =
            urls.filter { it.isNotBlank() }.map(::fromUrl)

        fun fromCommentPicture(picture: BiliCommentPicture): BiliViewerImage {
            val largeCandidates = BiliImageUrlResolver.fullscreenCandidatesFromUrl(picture.url)
            val largeUrl = largeCandidates.firstOrNull()?.takeIf { it.isNotBlank() } ?: picture.url
            val thumbnailUrl = BiliImageUrlResolver.commentThumbnailUrl(picture.url)
            return BiliViewerImage(
                id = picture.url,
                thumbnailUrl = thumbnailUrl,
                largeUrl = largeUrl,
                downloadUrls = BiliImageUrlResolver.commentThumbnailFallbackUrls(picture.url),
                width = picture.width.takeIf { it > 0 },
                height = picture.height.takeIf { it > 0 },
            )
        }

        fun profileCoverImages(urls: List<String>): List<BiliViewerImage> =
            urls.filter { it.isNotBlank() }.map { url ->
                val candidates = BiliImageUrlResolver.fullscreenCandidatesFromUrl(url)
                val displayUrl = candidates.firstOrNull()?.takeIf { it.isNotBlank() } ?: url
                BiliViewerImage(
                    id = url,
                    thumbnailUrl = displayUrl,
                    largeUrl = displayUrl,
                    downloadUrls = candidates,
                )
            }
    }
}
