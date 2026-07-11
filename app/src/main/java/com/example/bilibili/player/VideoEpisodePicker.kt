package com.example.bilibili.player

import com.example.bilibili.data.BiliUgcSeason
import com.example.bilibili.data.BiliUgcSeasonEpisode
import com.example.bilibili.data.BiliVideoPage

data class VideoEpisodePickerEntry(
    val index: Int,
    val title: String,
    val detail: String = "",
    val selected: Boolean = false,
)

data class VideoEpisodePickerState(
    val sheetTitle: String,
    val entries: List<VideoEpisodePickerEntry>,
    val onEntrySelected: (Int) -> Unit,
) {
    val isAvailable: Boolean get() = entries.size > 1
}

fun resolveVideoEpisodePickerState(
    pages: List<BiliVideoPage>,
    ugcSeason: BiliUgcSeason?,
    currentCid: Long,
    currentBvid: String,
    activePartPage: Int? = null,
    onSwitchPart: (BiliVideoPage) -> Unit,
    onSwitchEpisode: (BiliUgcSeasonEpisode) -> Unit,
): VideoEpisodePickerState? {
    if (ugcSeason != null) {
        val flatEpisodes = ugcSeason.sections.flatMap { section ->
            section.episodes.map { episode -> section.title to episode }
        }
        if (flatEpisodes.size > 1) {
            val entries = flatEpisodes.mapIndexed { index, (sectionTitle, episode) ->
                VideoEpisodePickerEntry(
                    index = index,
                    title = episode.title.ifBlank { "第${index + 1}集" },
                    detail = buildEpisodePickerDetail(
                        prefix = sectionTitle.takeIf { ugcSeason.sections.size > 1 && it.isNotBlank() },
                        durationSeconds = episode.durationSeconds,
                    ),
                    selected = episodeMatchesPickerCurrent(
                        episodeBvid = episode.bvid,
                        episodeCid = episode.cid,
                        currentBvid = currentBvid,
                        currentCid = currentCid,
                    ),
                )
            }
            return VideoEpisodePickerState(
                sheetTitle = ugcSeason.title.ifBlank { "选集" },
                entries = entries,
                onEntrySelected = { index ->
                    flatEpisodes.getOrNull(index)?.second?.let(onSwitchEpisode)
                },
            )
        }
    }
    if (pages.size > 1) {
        val entries = pages.mapIndexed { index, page ->
            VideoEpisodePickerEntry(
                index = index,
                title = page.title.ifBlank { "P${page.page}" },
                detail = formatEpisodePickerDuration(page.durationSeconds),
                selected = partMatchesPickerCurrent(
                    page = page,
                    currentBvid = currentBvid,
                    currentCid = currentCid,
                    activePartPage = activePartPage,
                ),
            )
        }
        return VideoEpisodePickerState(
            sheetTitle = "选集 (${pages.size})",
            entries = entries,
            onEntrySelected = { index -> pages.getOrNull(index)?.let(onSwitchPart) },
        )
    }
    return null
}

private fun buildEpisodePickerDetail(prefix: String?, durationSeconds: Int): String {
    val duration = formatEpisodePickerDuration(durationSeconds)
    return when {
        !prefix.isNullOrBlank() && duration.isNotBlank() -> "$prefix · $duration"
        !prefix.isNullOrBlank() -> prefix
        else -> duration
    }
}

private fun formatEpisodePickerDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val minutes = seconds / 60
    val remain = seconds % 60
    return "%d:%02d".format(minutes, remain)
}

private fun partMatchesPickerCurrent(
    page: BiliVideoPage,
    currentBvid: String,
    currentCid: Long,
    activePartPage: Int?,
): Boolean {
    if (activePartPage != null && page.page == activePartPage) return true
    if (page.cid > 0L && currentCid > 0L) {
        return page.cid == currentCid &&
            (page.bvid.isBlank() || currentBvid.isBlank() || page.bvid == currentBvid)
    }
    return page.bvid.isNotBlank() && page.bvid == currentBvid
}

private fun episodeMatchesPickerCurrent(
    episodeBvid: String,
    episodeCid: Long,
    currentBvid: String,
    currentCid: Long,
): Boolean {
    if (episodeCid > 0L && currentCid > 0L) {
        return episodeCid == currentCid &&
            (episodeBvid.isBlank() || currentBvid.isBlank() || episodeBvid == currentBvid)
    }
    return currentBvid.isNotBlank() && episodeBvid == currentBvid
}
