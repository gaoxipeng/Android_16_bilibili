package com.example.bilibili.data

data class BilibiliCredential(
    val dedeUserId: String,
    val sessdata: String,
    val biliJct: String,
    val buvid3: String,
    val buvid4: String = "",
) {
    fun toCookieHeader(): String = buildList {
        add("SESSDATA=${sessdata}")
        add("bili_jct=${biliJct}")
        add("DedeUserID=${dedeUserId}")
        add("DedeUserID__ckMd5=")
        if (buvid3.isNotBlank()) add("buvid3=${buvid3}")
        if (buvid4.isNotBlank()) add("buvid4=${buvid4}")
    }.joinToString("; ")
}

data class StoredBilibiliAccount(
    val uid: String,
    val name: String,
    val face: String?,
    val credential: BilibiliCredential,
)

data class BiliPlayStream(
    val videoUrl: String,
    val audioUrl: String? = null,
    val aid: Long = 0L,
    val cid: Long = 0L,
) {
    val hasAudio: Boolean get() = !audioUrl.isNullOrBlank()
}

data class BiliDanmakuItem(
    val timeMs: Long,
    val mode: Int,
    val fontSize: Int,
    val colorArgb: Int,
    val content: String,
) {
    val id: Int = (timeMs xor (content.hashCode().toLong()) xor mode.toLong()).toInt()
}

enum class BiliDanmakuMode(val value: Int) {
    Scroll(1),
    Bottom(4),
    Top(5),
    ReverseScroll(6),
    ;

    companion object {
        fun from(value: Int): BiliDanmakuMode? = entries.firstOrNull { it.value == value }
    }
}

data class BiliVideoItem(
    val bvid: String,
    val aid: Long,
    val title: String,
    val coverUrl: String,
    val authorName: String,
    val authorMid: Long,
    val viewCount: Long,
    val danmakuCount: Long,
    val likeCount: Long,
    val durationSeconds: Int,
    val description: String = "",
    val cid: Long = 0L,
)

data class BiliLiveRoom(
    val roomId: Long,
    val title: String,
    val coverUrl: String,
    val userName: String,
    val userFace: String,
    val online: Long,
    val areaName: String = "",
)

data class BiliUserProfile(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String,
    val level: Int,
    val following: Long = 0,
    val follower: Long = 0,
    val likes: Long = 0,
    val videoCount: Long = 0,
    val topPhoto: String = "",
)

data class BiliVideoDetail(
    val video: BiliVideoItem,
    val publishTimeSeconds: Long = 0L,
    val onlineCount: Long = 0L,
    val replyCount: Long = 0L,
)

data class BiliAuthorRelation(
    val following: Boolean = false,
    val followerMe: Boolean = false,
)

data class BiliAuthorCard(
    val profile: BiliUserProfile,
    val relation: BiliAuthorRelation = BiliAuthorRelation(),
)

enum class BiliCommentSort(val mode: Int, val headerLabel: String, val toggleLabel: String) {
    Hot(3, "热门评论", "按热度"),
    Time(2, "最新评论", "按时间"),
}

data class BiliCommentPage(
    val comments: List<BiliCommentItem>,
    val nextCursor: String? = null,
    val isEnd: Boolean = true,
    val totalCount: Long = 0L,
)

data class BiliCommentReplyPage(
    val replies: List<BiliCommentItem>,
    val totalCount: Long = 0L,
    val page: Int = 1,
    val isEnd: Boolean = true,
)

data class BiliCommentPicture(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
)

data class BiliCommentItem(
    val id: Long,
    val authorMid: Long,
    val authorName: String,
    val authorFace: String,
    val level: Int,
    val content: String,
    val likeCount: Long,
    val replyCount: Long,
    val publishTimeSeconds: Long,
    val ipLocation: String? = null,
    val emoticons: Map<String, String> = emptyMap(),
    val pictures: List<BiliCommentPicture> = emptyList(),
    val replies: List<BiliCommentItem> = emptyList(),
)

data class BiliPlayInfo(
    val url: String,
    val quality: Int,
)

data class BiliDynamicLink(
    val title: String,
    val url: String,
    val coverUrl: String = "",
    val desc: String = "",
)

data class BiliDynamicOrigin(
    val authorName: String,
    val text: String = "",
    val video: BiliVideoItem? = null,
    val imageUrls: List<String> = emptyList(),
    val link: BiliDynamicLink? = null,
)

data class BiliDynamicItem(
    val id: String,
    val text: String,
    val publishTimeSeconds: Long,
    val video: BiliVideoItem? = null,
    val imageUrls: List<String> = emptyList(),
    val link: BiliDynamicLink? = null,
    val origin: BiliDynamicOrigin? = null,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    val repostCount: Long = 0L,
)

data class BiliDynamicFeedPage(
    val items: List<BiliDynamicItem>,
    val nextOffset: String? = null,
    val hasMore: Boolean = false,
)

data class BiliUserVideoPage(
    val videos: List<BiliVideoItem>,
    val hasMore: Boolean = false,
)

data class BiliHomeRecommendPage(
    val videos: List<BiliVideoItem>,
    val nextFreshIdx: Int,
    val nextFetchRow: Int,
    val lastShowList: String,
    val hasMore: Boolean,
)

data class BiliFollowingFeedPage(
    val videos: List<BiliVideoItem>,
    val nextOffset: String?,
    val hasMore: Boolean,
)

data class BiliHotSearchItem(
    val keyword: String,
    val showName: String,
    val rank: Int = 0,
)

data class BiliSearchUserItem(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String,
    val fans: Long,
    val level: Int,
)

data class BiliHistoryCursor(
    val max: Long,
    val viewAt: Long,
    val business: String,
    val ps: Int,
) {
    val hasMore: Boolean get() = ps > 0 && max > 0L
}

data class BiliHistoryItem(
    val kid: String,
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
    val coverUrl: String,
    val authorName: String,
    val authorMid: Long,
    val viewAtSeconds: Long,
    val progressSeconds: Int,
    val durationSeconds: Int,
)

data class BiliHistoryPage(
    val items: List<BiliHistoryItem>,
    val cursor: BiliHistoryCursor?,
)

data class BiliSearchResultPage<T>(
    val items: List<T>,
    val page: Int = 1,
    val hasMore: Boolean = false,
) {
    companion object {
        fun <T> empty(): BiliSearchResultPage<T> = BiliSearchResultPage(emptyList(), 1, false)
    }
}
