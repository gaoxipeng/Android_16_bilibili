package com.example.bilibili.player

import com.example.bilibili.data.BiliUgcSeason
import com.example.bilibili.data.BiliUgcSeasonEpisode
import com.example.bilibili.data.BiliVideoPage

data class MediaEpisodeControls(
    val isMultiEpisode: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val onPrevious: (() -> Unit)? = null,
    val onNext: (() -> Unit)? = null,
) {
    companion object {
        val EMPTY = MediaEpisodeControls()
    }
}

fun resolveMediaEpisodeControls(
    pages: List<BiliVideoPage>,
    ugcSeason: BiliUgcSeason?,
    currentCid: Long,
    currentBvid: String,
    activePartPage: Int? = null,
    onSwitchPart: (BiliVideoPage) -> Unit,
    onSwitchEpisode: (BiliUgcSeasonEpisode) -> Unit,
): MediaEpisodeControls {
    if (ugcSeason != null) {
        val episodes = ugcSeason.sections.flatMap { it.episodes }
        if (episodes.size > 1) {
            return resolveFromEpisodes(
                episodes = episodes,
                currentCid = currentCid,
                currentBvid = currentBvid,
                onPrevious = { episode -> onSwitchEpisode(episode) },
                onNext = { episode -> onSwitchEpisode(episode) },
            )
        }
    }
    if (pages.size > 1) {
        return resolveFromPages(
            pages = pages,
            currentCid = currentCid,
            currentBvid = currentBvid,
            activePartPage = activePartPage,
            onPrevious = { page -> onSwitchPart(page) },
            onNext = { page -> onSwitchPart(page) },
        )
    }
    return MediaEpisodeControls.EMPTY
}

private fun resolveFromPages(
    pages: List<BiliVideoPage>,
    currentCid: Long,
    currentBvid: String,
    activePartPage: Int?,
    onPrevious: (BiliVideoPage) -> Unit,
    onNext: (BiliVideoPage) -> Unit,
): MediaEpisodeControls {
    val index = findPageIndex(
        pages = pages,
        currentCid = currentCid,
        currentBvid = currentBvid,
        activePartPage = activePartPage,
    )
    val previous = index.takeIf { it >= 0 }?.let { pages.getOrNull(it - 1) }
    val next = index.takeIf { it >= 0 }?.let { pages.getOrNull(it + 1) }
    return MediaEpisodeControls(
        isMultiEpisode = true,
        hasPrevious = previous != null,
        hasNext = next != null,
        onPrevious = previous?.let { page -> { onPrevious(page) } },
        onNext = next?.let { page -> { onNext(page) } },
    )
}

private fun resolveFromEpisodes(
    episodes: List<BiliUgcSeasonEpisode>,
    currentCid: Long,
    currentBvid: String,
    onPrevious: (BiliUgcSeasonEpisode) -> Unit,
    onNext: (BiliUgcSeasonEpisode) -> Unit,
): MediaEpisodeControls {
    val index = findEpisodeIndex(
        episodes = episodes,
        currentCid = currentCid,
        currentBvid = currentBvid,
    )
    val previous = index.takeIf { it >= 0 }?.let { episodes.getOrNull(it - 1) }
    val next = index.takeIf { it >= 0 }?.let { episodes.getOrNull(it + 1) }
    return MediaEpisodeControls(
        isMultiEpisode = true,
        hasPrevious = previous != null,
        hasNext = next != null,
        onPrevious = previous?.let { episode -> { onPrevious(episode) } },
        onNext = next?.let { episode -> { onNext(episode) } },
    )
}

private fun findPageIndex(
    pages: List<BiliVideoPage>,
    currentCid: Long,
    currentBvid: String,
    activePartPage: Int?,
): Int {
    if (activePartPage != null) {
        val byPage = pages.indexOfFirst { it.page == activePartPage }
        if (byPage >= 0) return byPage
    }
    val strictMatch = pages.indexOfFirst { page ->
        page.cid == currentCid &&
            (page.bvid.isBlank() || currentBvid.isBlank() || page.bvid == currentBvid)
    }
    if (strictMatch >= 0) return strictMatch
    if (currentCid > 0L) {
        val byCid = pages.indexOfFirst { it.cid == currentCid }
        if (byCid >= 0) return byCid
    }
    return -1
}

private fun findEpisodeIndex(
    episodes: List<BiliUgcSeasonEpisode>,
    currentCid: Long,
    currentBvid: String,
): Int {
    val strictMatch = episodes.indexOfFirst { episode ->
        episode.cid == currentCid &&
            (episode.bvid.isBlank() || currentBvid.isBlank() || episode.bvid == currentBvid)
    }
    if (strictMatch >= 0) return strictMatch
    if (currentCid > 0L) {
        val byCid = episodes.indexOfFirst { it.cid == currentCid }
        if (byCid >= 0) return byCid
    }
    if (currentBvid.isNotBlank()) {
        val byBvid = episodes.indexOfFirst { it.bvid == currentBvid }
        if (byBvid >= 0) return byBvid
    }
    return -1
}
