package com.example.bilibili.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedHomeFeed(
    val videos: List<BiliVideoItem>,
    val freshIdx: Int,
    val fetchRow: Int,
    val lastShowList: String,
    val hasMore: Boolean,
)

class BilibiliHomeFeedStore(context: Context) {
    private val file = File(context.filesDir, "home_feed_cache.json")

    @Synchronized
    fun read(): CachedHomeFeed? {
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            CachedHomeFeed(
                videos = root.optJSONArray("videos").toVideoItems(),
                freshIdx = root.optInt("fresh_idx", 1),
                fetchRow = root.optInt("fetch_row", 1),
                lastShowList = root.optString("last_show_list", ""),
                hasMore = root.optBoolean("has_more", true),
            )
        }.getOrNull()
    }

    @Synchronized
    fun save(feed: CachedHomeFeed) {
        val root = JSONObject()
            .put("fresh_idx", feed.freshIdx)
            .put("fetch_row", feed.fetchRow)
            .put("last_show_list", feed.lastShowList)
            .put("has_more", feed.hasMore)
            .put("videos", feed.videos.toJsonArray())
        file.parentFile?.mkdirs()
        file.writeText(root.toString(), Charsets.UTF_8)
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    private fun JSONArray?.toVideoItems(): List<BiliVideoItem> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val bvid = item.optString("bvid").takeIf { it.isNotBlank() } ?: continue
                add(
                    BiliVideoItem(
                        bvid = bvid,
                        aid = item.optLong("aid"),
                        title = item.optString("title"),
                        coverUrl = item.optString("cover_url"),
                        authorName = item.optString("author_name"),
                        authorMid = item.optLong("author_mid"),
                        authorFace = item.optString("author_face"),
                        viewCount = item.optLong("view_count"),
                        danmakuCount = item.optLong("danmaku_count"),
                        likeCount = item.optLong("like_count"),
                        durationSeconds = item.optInt("duration_seconds"),
                        description = item.optString("description"),
                        cid = item.optLong("cid"),
                    ),
                )
            }
        }
    }

    private fun List<BiliVideoItem>.toJsonArray(): JSONArray =
        JSONArray().also { array ->
            forEach { video ->
                array.put(
                    JSONObject()
                        .put("bvid", video.bvid)
                        .put("aid", video.aid)
                        .put("title", video.title)
                        .put("cover_url", video.coverUrl)
                        .put("author_name", video.authorName)
                        .put("author_mid", video.authorMid)
                        .put("author_face", video.authorFace)
                        .put("view_count", video.viewCount)
                        .put("danmaku_count", video.danmakuCount)
                        .put("like_count", video.likeCount)
                        .put("duration_seconds", video.durationSeconds)
                        .put("description", video.description)
                        .put("cid", video.cid),
                )
            }
        }
}
