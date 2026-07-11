package com.example.bilibili.data

import com.example.bilibili.util.BiliArticleUrl

data class BilibiliCredential(
    val dedeUserId: String,
    val sessdata: String,
    val biliJct: String,
    val buvid3: String,
    val buvid4: String = "",
    val accessKey: String = "",
    val refreshToken: String = "",
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
    val cachedAtMs: Long = 0L,
) {
    val hasAudio: Boolean get() = !audioUrl.isNullOrBlank()
}

data class BiliVideoShot(
    val images: List<String>,
    val indexSeconds: List<Int>,
    val tileColumns: Int,
    val tileRows: Int,
    val tileWidth: Int,
    val tileHeight: Int,
) {
    val tilesPerImage: Int get() = tileColumns * tileRows
    val totalTiles: Int get() = images.size * tilesPerImage
}

data class BiliDanmakuEmoticon(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
)

fun Map<String, BiliDanmakuEmoticon>.emoticonUrlMap(): Map<String, String> =
    mapValues { (_, spec) -> spec.url }

data class BiliDanmakuItem(
    val timeMs: Long,
    val mode: Int,
    val fontSize: Int,
    val colorArgb: Int,
    val content: String,
    val senderId: Long = 0L,
    val senderName: String = "",
    val emoticons: Map<String, BiliDanmakuEmoticon> = emptyMap(),
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
    val authorFace: String = "",
    val viewCount: Long,
    val danmakuCount: Long,
    val likeCount: Long,
    val durationSeconds: Int,
    val description: String = "",
    val cid: Long = 0L,
    val publishTimeSeconds: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val epid: Long = 0L,
    val playbackReferer: String = "",
) {
    val isPortraitVideo: Boolean
        get() = videoWidth > 0 && videoHeight > 0 && videoHeight > videoWidth

    fun isPgcPlayback(): Boolean = epid > 0L || bvid.startsWith("pgc")

    fun pgcEpid(): Long = when {
        epid > 0L -> epid
        bvid.startsWith("pgc:") -> bvid.removePrefix("pgc:").substringBefore('-').toLongOrNull() ?: 0L
        else -> 0L
    }

    fun playbackId(): String = when {
        epid > 0L -> "pgc:$epid"
        bvid.isNotBlank() && cid > 0L -> "$bvid:cid:$cid"
        bvid.isNotBlank() -> bvid
        aid > 0L -> "av:$aid"
        else -> title
    }
}

data class BiliLiveRoom(
    val roomId: Long,
    val title: String,
    val coverUrl: String,
    val userName: String,
    val userFace: String,
    val online: Long,
    val areaName: String = "",
    val isPortrait: Boolean? = null,
)

data class BiliLiveArea(
    val id: Long,
    val name: String,
    val parentId: Long = 0L,
)

data class BiliLiveAreaGroup(
    val parent: BiliLiveArea,
    val children: List<BiliLiveArea> = emptyList(),
)

data class BiliLiveRoomPage(
    val rooms: List<BiliLiveRoom>,
    val page: Int,
    val hasMore: Boolean,
)

data class BiliLiveQuality(
    val qn: Int,
    val label: String,
)

data class BiliLivePlayResult(
    val roomId: Long,
    val realRoomId: Long,
    val anchorUid: Long,
    val liveStatus: Int,
    val streamUrl: String,
    val currentQn: Int,
    val qualities: List<BiliLiveQuality>,
) {
    val isLive: Boolean get() = liveStatus == 1
}

data class BiliLiveOnlineGoldRank(
    val onlineNum: Long = 0L,
    val users: List<BiliLiveRankUser> = emptyList(),
)

data class BiliLiveRankUser(
    val uid: Long,
    val face: String,
    val uname: String,
    val rank: Int,
    val guardLevel: Int = 0,
)

data class BiliLiveRoomDetail(
    val roomId: Long,
    val title: String,
    val coverUrl: String,
    val userName: String,
    val userFace: String,
    val anchorUid: Long,
    val online: Long,
    val areaName: String,
    val parentAreaName: String,
    val liveStatus: Int,
    val description: String = "",
    val isPortrait: Boolean? = null,
) {
    val isLive: Boolean get() = liveStatus == 1
}

data class BiliLiveDanmuHost(
    val host: String,
    val wssPort: Int,
    val wsPort: Int,
)

data class BiliLiveDanmuInfo(
    val token: String,
    val hosts: List<BiliLiveDanmuHost>,
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
    val topPhotos: List<String> = emptyList(),
    val ipLocation: String? = null,
) {
    val displayTopPhotos: List<String>
        get() = topPhotos.ifEmpty { listOfNotNull(topPhoto.takeIf { it.isNotBlank() }) }
}

data class BiliUserWallet(
    val bcoinBalance: Double = 0.0,
    val coinCount: Long = 0L,
)

data class BiliVideoDetail(
    val video: BiliVideoItem,
    val publishTimeSeconds: Long = 0L,
    val onlineCount: Long = 0L,
    val replyCount: Long = 0L,
    val coinCount: Long = 0L,
    val favoriteCount: Long = 0L,
    val shareCount: Long = 0L,
    val ugcSeason: BiliUgcSeason? = null,
    val pages: List<BiliVideoPage> = emptyList(),
    val userRelation: BiliVideoRelation = BiliVideoRelation(),
    val seasonId: Long = 0L,
    val isSeasonDisplay: Boolean = false,
)

data class BiliVideoPage(
    val page: Int,
    val cid: Long,
    val title: String,
    val durationSeconds: Int = 0,
    val epid: Long = 0L,
    val bvid: String = "",
)

data class BiliUgcSeasonEpisode(
    val id: Long,
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val coverUrl: String = "",
    val durationSeconds: Int = 0,
) {
    fun toVideoItem(authorName: String, authorMid: Long): BiliVideoItem =
        BiliVideoItem(
            bvid = bvid,
            aid = aid,
            cid = cid,
            title = title,
            coverUrl = coverUrl,
            authorName = authorName,
            authorMid = authorMid,
            viewCount = 0,
            danmakuCount = 0,
            likeCount = 0,
            durationSeconds = durationSeconds,
        )
}

data class BiliUgcSeasonSection(
    val id: Long,
    val title: String,
    val episodes: List<BiliUgcSeasonEpisode>,
)

data class BiliUgcSeason(
    val id: Long,
    val title: String,
    val mid: Long = 0L,
    val coverUrl: String = "",
    val sections: List<BiliUgcSeasonSection>,
    val apiEpCount: Int = 0,
) {
    val episodeCount: Int
        get() {
            val parsed = sections.sumOf { it.episodes.size }
            return when {
                parsed > 0 -> parsed
                apiEpCount > 0 -> apiEpCount
                else -> 0
            }
        }

    val shouldDisplay: Boolean
        get() = episodeCount > 1 || apiEpCount > 1

    fun needsHydration(): Boolean {
        val parsed = sections.sumOf { it.episodes.size }
        if (apiEpCount <= 1) return parsed < 2
        return parsed < apiEpCount
    }

    fun displayGroups(): List<BiliUgcSeasonDisplayGroup> {
        val validSections = sections.filter { it.episodes.isNotEmpty() }
        if (validSections.size <= 1) {
            return listOf(BiliUgcSeasonDisplayGroup(title = title, sectionId = null))
        }
        return validSections.map { section ->
            BiliUgcSeasonDisplayGroup(
                title = section.title.ifBlank { title },
                sectionId = section.id,
            )
        }
    }

    fun sectionsToShow(highlightSectionId: Long?): List<BiliUgcSeasonSection> {
        val validSections = sections.filter { it.episodes.isNotEmpty() }
        if (highlightSectionId == null) return validSections
        return validSections.filter { it.id == highlightSectionId }.ifEmpty { validSections }
    }

    fun withHydratedEpisodes(episodes: List<BiliUgcSeasonEpisode>): BiliUgcSeason {
        if (episodes.isEmpty()) return this
        val sectionTitle = sections.firstOrNull()?.title.orEmpty()
        return copy(
            sections = listOf(
                BiliUgcSeasonSection(
                    id = sections.firstOrNull()?.id ?: 0L,
                    title = sectionTitle,
                    episodes = episodes,
                ),
            ),
        )
    }

    fun toVideoPages(): List<BiliVideoPage> {
        val episodes = sections.flatMap { it.episodes }
        if (episodes.size <= 1) return emptyList()
        return episodes.mapIndexedNotNull { index, episode ->
            val cid = episode.cid.takeIf { it > 0L } ?: return@mapIndexedNotNull null
            if (episode.bvid.isBlank()) return@mapIndexedNotNull null
            BiliVideoPage(
                page = index + 1,
                cid = cid,
                title = episode.title,
                durationSeconds = episode.durationSeconds,
                bvid = episode.bvid,
            )
        }
    }
}

data class BiliUgcSeasonDisplayGroup(
    val title: String,
    val sectionId: Long?,
)

data class BiliVideoRelation(
    val liked: Boolean = false,
    val favorited: Boolean = false,
    val coinCount: Int = 0,
)

data class BiliVideoTripleResult(
    val liked: Boolean = false,
    val coined: Boolean = false,
    val favorited: Boolean = false,
)

data class BiliAuthorRelation(
    val following: Boolean = false,
    val followerMe: Boolean = false,
)

enum class UserRelationTab(val label: String) {
    Following("关注"),
    Followers("粉丝"),
}

data class BiliRelationUser(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String,
    val relation: BiliAuthorRelation,
    val fanCount: Long = 0L,
    val ipLocation: String? = null,
)

data class BiliRelationUserPage(
    val users: List<BiliRelationUser>,
    val hasMore: Boolean = false,
    val total: Long = 0L,
    val errorMessage: String? = null,
)

data class BiliAuthorCard(
    val profile: BiliUserProfile,
    val relation: BiliAuthorRelation = BiliAuthorRelation(),
)

enum class BiliCommentSort(val mode: Int, val headerLabel: String, val toggleLabel: String) {
    Hot(3, "热门评论", "按热度"),
    Time(2, "最新评论", "按时间"),
}

enum class BiliUserVideoSort(val order: String, val toggleLabel: String) {
    LatestPublish("pubdate", "最新发布"),
    MostViews("click", "播放最多"),
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
    val isPinned: Boolean = false,
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
    val cvId: Long = 0L,
)

data class BiliArticleDetail(
    val cvId: Long,
    val title: String,
    val htmlContent: String,
    val summary: String = "",
    val authorName: String = "",
    val authorMid: Long = 0L,
    val authorFace: String = "",
    val publishTimeSeconds: Long = 0L,
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
)

data class BiliDynamicOrigin(
    val authorName: String,
    val text: String = "",
    val emoticons: Map<String, String> = emptyMap(),
    val video: BiliVideoItem? = null,
    val imageUrls: List<String> = emptyList(),
    val link: BiliDynamicLink? = null,
)

data class BiliDynamicItem(
    val id: String,
    val text: String,
    val emoticons: Map<String, String> = emptyMap(),
    val publishTimeSeconds: Long,
    val video: BiliVideoItem? = null,
    val imageUrls: List<String> = emptyList(),
    val link: BiliDynamicLink? = null,
    val origin: BiliDynamicOrigin? = null,
    val authorMid: Long = 0L,
    val authorName: String = "",
    val authorFace: String = "",
    val authorLevel: Int = 0,
    val ipLocation: String? = null,
    val commentOid: Long = 0L,
    val commentType: Int = 0,
    val dynamicType: String = "",
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    val repostCount: Long = 0L,
) {
    val canOpenDetail: Boolean
        get() = commentOid > 0L && commentType > 0 && video == null && resolveArticleCvId() == null

    fun resolveArticleCvId(): Long? {
        link?.cvId?.takeIf { it > 0L }?.let { return it }
        BiliArticleUrl.extractCvId(link?.url.orEmpty())?.let { return it }
        if (dynamicType == "DYNAMIC_TYPE_ARTICLE" && commentType == 12 && commentOid > 0L) {
            return commentOid
        }
        return null
    }

    fun isArticleDynamic(): Boolean =
        resolveArticleCvId() != null ||
            dynamicType == "DYNAMIC_TYPE_ARTICLE" ||
            dynamicType == "DYNAMIC_TYPE_OPUS"

    fun resolveArticleMobileUrl(): String? {
        link?.url.orEmpty().trim().takeIf { it.isNotBlank() }?.let { url ->
            BiliArticleUrl.resolveMobileOpusUrl(url)?.let { return it }
        }
        if (!isArticleDynamic()) return null
        return id.toLongOrNull()
            ?.takeIf { it > 0L }
            ?.let(BiliArticleUrl::buildMobileOpusUrl)
    }
}

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

data class BiliSearchBangumi(
    val seasonId: Long,
    val mediaId: Long = 0L,
    val title: String,
    val subtitle: String = "",
    val coverUrl: String,
    val areas: String = "",
    val styles: String = "",
    val badge: String = "",
    val categoryName: String = "",
    val indexShow: String = "",
    val webUrl: String = "",
    val firstEpid: Long = 0L,
) {
    val metadataLine: String
        get() {
            val parts = listOf(styles, areas, indexShow).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isNotEmpty()) return parts.joinToString(" · ")
            if (categoryName.isNotBlank()) return categoryName
            return "影视"
        }

    val canPlayInApp: Boolean get() = firstEpid > 0L

    fun withFirstEpid(epid: Long): BiliSearchBangumi =
        if (epid <= 0L || firstEpid == epid) this else copy(firstEpid = epid)

    fun toVideoItem(): BiliVideoItem {
        val syntheticBvid = when {
            firstEpid > 0L -> "pgc:$firstEpid"
            seasonId > 0L -> "pgc-season:$seasonId"
            else -> ""
        }
        return BiliVideoItem(
            bvid = syntheticBvid,
            aid = 0L,
            title = title,
            coverUrl = coverUrl,
            authorName = metadataLine,
            authorMid = 0L,
            viewCount = 0L,
            danmakuCount = 0L,
            likeCount = 0L,
            durationSeconds = 0,
            description = subtitle,
            epid = firstEpid,
            playbackReferer = webUrl,
        )
    }

    companion object {
        fun categoryDisplayPriority(categoryName: String): Int = when (categoryName) {
            "番剧" -> 0
            "国创" -> 1
            "纪录片" -> 2
            "电影" -> 3
            "电视剧" -> 4
            "综艺" -> 5
            "影视" -> 6
            else -> 99
        }

        fun sortedForDisplay(items: List<BiliSearchBangumi>): List<BiliSearchBangumi> =
            items.withIndex().sortedWith { left, right ->
                val leftPriority = categoryDisplayPriority(left.value.categoryName)
                val rightPriority = categoryDisplayPriority(right.value.categoryName)
                if (leftPriority != rightPriority) {
                    leftPriority.compareTo(rightPriority)
                } else {
                    left.index.compareTo(right.index)
                }
            }.map { it.value }

        fun availableCategories(items: List<BiliSearchBangumi>): List<String> =
            items.map { it.categoryName }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedBy { categoryDisplayPriority(it) }
    }
}

data class BiliPGCEpisodeContext(
    val epid: Long,
    val seasonId: Long,
    val seasonTitle: String,
    val episodeTitle: String,
    val longTitle: String,
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val coverUrl: String,
    val durationSeconds: Int,
    val evaluate: String = "",
    val styles: String = "",
    val areas: String = "",
    val pages: List<BiliVideoPage> = emptyList(),
) {
    fun displayTitle(): String {
        val season = seasonTitle.trim()
        val episode = longTitle.ifBlank { episodeTitle }.trim()
        return when {
            season.isNotBlank() && episode.isNotBlank() && season != episode -> "$season · $episode"
            season.isNotBlank() -> season
            else -> episode
        }
    }

    fun metadataLine(): String {
        val parts = listOf(styles, areas).map { it.trim() }.filter { it.isNotEmpty() }
        return parts.joinToString(" · ")
    }
}

enum class BiliHistoryBusiness {
    Archive,
    Pgc,
    Unknown,
    ;

    companion object {
        fun from(raw: String): BiliHistoryBusiness = when (raw) {
            "archive" -> Archive
            "pgc" -> Pgc
            else -> Unknown
        }
    }
}

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
    val business: BiliHistoryBusiness = BiliHistoryBusiness.Archive,
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val epid: Long = 0L,
    val page: Int = 0,
    val partTitle: String = "",
    val title: String,
    val coverUrl: String,
    val authorName: String,
    val authorMid: Long,
    val authorFace: String = "",
    val badge: String = "",
    val webUri: String = "",
    val viewAtSeconds: Long,
    val progressSeconds: Int,
    val durationSeconds: Int,
) {
    fun displayAuthorName(): String =
        authorName.ifBlank { badge }.ifBlank {
            if (business == BiliHistoryBusiness.Pgc) "影视" else ""
        }

    fun toVideoItem(): BiliVideoItem {
        val syntheticBvid = when {
            bvid.isNotBlank() -> bvid
            epid > 0L -> "pgc:$epid"
            else -> ""
        }
        return BiliVideoItem(
            bvid = syntheticBvid,
            aid = aid,
            cid = cid,
            epid = epid,
            title = title,
            coverUrl = coverUrl,
            authorName = displayAuthorName(),
            authorMid = authorMid,
            authorFace = authorFace,
            viewCount = 0L,
            danmakuCount = 0L,
            likeCount = 0L,
            durationSeconds = durationSeconds,
            playbackReferer = webUri,
        )
    }
}

data class BiliHistoryPage(
    val items: List<BiliHistoryItem>,
    val cursor: BiliHistoryCursor?,
)

data class BiliFavoriteVideoPage(
    val videos: List<BiliVideoItem>,
    val page: Int,
    val hasMore: Boolean,
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
