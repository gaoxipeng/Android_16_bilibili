package com.example.bilibili.player

import android.net.Uri
import androidx.media3.common.MediaMetadata
import com.example.bilibili.data.BiliVideoItem

data class VideoPlaybackMetadata(
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val bvid: String,
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

    companion object {
        fun fromVideo(video: BiliVideoItem): VideoPlaybackMetadata =
            VideoPlaybackMetadata(
                title = video.title,
                artist = video.authorName,
                artworkUrl = normalizePlaybackArtworkUrl(video.coverUrl),
                bvid = video.bvid,
            )
    }
}

internal fun normalizePlaybackArtworkUrl(url: String): String =
    if (url.startsWith("//")) "https:$url" else url
