package com.example.bilibili.player

import android.net.Uri
import androidx.media3.common.MediaMetadata
import com.example.bilibili.data.BiliVideoItem

data class VideoPlaybackMetadata(
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val bvid: String,
    val cid: Long = 0L,
    val aid: Long = 0L,
    val epid: Long = 0L,
    val authorMid: Long = 0L,
    val durationSeconds: Int = 0,
) {
    fun toMediaMetadata(): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(title.ifBlank { "哔哩哔哩视频" })
            .setArtist(artist.ifBlank { "哔哩哔哩" })
            .setAlbumTitle(artist.ifBlank { "哔哩哔哩" })
        val artwork = artworkUrl.takeIf { it.isNotBlank() }?.let(::normalizePlaybackArtworkUrl)
        if (artwork != null) {
            builder.setArtworkUri(Uri.parse(artwork))
        }
        return builder.build()
    }

    fun toSeedVideo(): BiliVideoItem =
        BiliVideoItem(
            bvid = bvid,
            aid = aid,
            title = title,
            coverUrl = artworkUrl,
            authorName = artist,
            authorMid = authorMid,
            viewCount = 0L,
            danmakuCount = 0L,
            likeCount = 0L,
            durationSeconds = durationSeconds,
            cid = cid,
            epid = epid,
        )

    companion object {
        fun fromVideo(video: BiliVideoItem): VideoPlaybackMetadata =
            VideoPlaybackMetadata(
                title = video.title,
                artist = video.authorName,
                artworkUrl = normalizePlaybackArtworkUrl(video.coverUrl),
                bvid = video.bvid,
                cid = video.cid,
                aid = video.aid,
                epid = video.epid,
                authorMid = video.authorMid,
                durationSeconds = video.durationSeconds,
            )
    }
}

internal fun normalizePlaybackArtworkUrl(url: String): String =
    if (url.startsWith("//")) "https:$url" else url
