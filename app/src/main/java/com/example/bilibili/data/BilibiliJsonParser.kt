package com.example.bilibili.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

object BilibiliJsonParser {
    fun parseVideosFromFeed(json: JSONObject): List<BiliVideoItem> {
        val data = json.optJSONObject("data") ?: return emptyList()
        val items = data.optJSONArray("item")
            ?: data.optJSONArray("list")
            ?: return emptyList()
        return items.toVideoList()
    }

    fun parseHomeRecommendPage(
        json: JSONObject,
        freshIdx: Int,
        fetchRow: Int,
    ): BiliHomeRecommendPage {
        val videos = parseVideosFromFeed(json)
        return BiliHomeRecommendPage(
            videos = videos,
            nextFreshIdx = freshIdx + 1,
            nextFetchRow = if (videos.isEmpty()) fetchRow else fetchRow + videos.size,
            lastShowList = buildHomeRecommendShowList(videos),
            hasMore = videos.isNotEmpty(),
        )
    }

    private fun buildHomeRecommendShowList(videos: List<BiliVideoItem>): String =
        videos.mapNotNull { video ->
            video.aid.takeIf { it > 0L }?.toString()
        }.joinToString("_")

    fun parseVideoView(json: JSONObject): BiliVideoItem? = parseVideoDetail(json)?.video

    fun parseVideoDetail(json: JSONObject): BiliVideoDetail? {
        val data = json.optJSONObject("data") ?: return null
        val owner = data.optJSONObject("owner") ?: JSONObject()
        val stat = data.optJSONObject("stat") ?: JSONObject()
        val pages = data.optJSONArray("pages")
        val parsedPages = parseVideoPages(pages)
        val cid = parsedPages.firstOrNull()?.cid
            ?: pages?.optJSONObject(0)?.optLong("cid")
            ?: 0L
        val dimension = data.optJSONObject("dimension")
        val redirectUrl = data.optString("redirect_url")
        val redirectEpid = parsePgcEpidFromUri(redirectUrl) ?: 0L
        val bvid = data.optString("bvid")
        if (bvid.isBlank()) return null
        val video = BiliVideoItem(
            bvid = bvid,
            aid = data.optLong("aid"),
            title = data.optString("title"),
            coverUrl = normalizeImageUrl(data.optString("pic")),
            authorName = owner.optString("name"),
            authorMid = owner.optLong("mid"),
            authorFace = normalizeImageUrl(owner.optString("face")),
            viewCount = stat.optLong("view"),
            danmakuCount = stat.optLong("danmaku"),
            likeCount = stat.optLong("like"),
            durationSeconds = data.optLong("duration").toInt(),
            description = data.optString("desc"),
            cid = cid,
            videoWidth = dimension?.optLong("width")?.toInt() ?: 0,
            videoHeight = dimension?.optLong("height")?.toInt() ?: 0,
            epid = redirectEpid,
            playbackReferer = redirectUrl,
        )
        return BiliVideoDetail(
            video = video,
            publishTimeSeconds = data.optLong("pubdate"),
            replyCount = stat.optLong("reply"),
            coinCount = stat.optLong("coin"),
            favoriteCount = stat.optLong("favorite"),
            shareCount = stat.optLong("share"),
            ugcSeason = parseUgcSeason(data) ?: parseUgcSeasonFromSeasonId(data),
            pages = parsedPages,
            userRelation = parseVideoReqUser(data),
        )
    }

    private fun parseVideoPages(pages: org.json.JSONArray?): List<BiliVideoPage> {
        if (pages == null || pages.length() <= 1) return emptyList()
        return buildList {
            for (index in 0 until pages.length()) {
                val pageObj = pages.optJSONObject(index) ?: continue
                val cid = pageObj.optLong("cid")
                if (cid <= 0L) continue
                add(
                    BiliVideoPage(
                        page = pageObj.optInt("page", index + 1),
                        cid = cid,
                        title = pageObj.optString("part"),
                        durationSeconds = pageObj.optLong("duration").toInt(),
                        epid = pageObj.optLong("ep_id")
                            .takeIf { it > 0L }
                            ?: pageObj.optLong("id").takeIf { it > 0L }
                            ?: 0L,
                    ),
                )
            }
        }
    }

    private fun parseUgcSeasonFromSeasonId(data: JSONObject): BiliUgcSeason? {
        if (!data.optBoolean("is_season_display", false)) return null
        val seasonId = data.optLong("season_id")
        if (seasonId <= 0L) return null
        val ugcSeason = data.optJSONObject("ugc_season")
        val title = ugcSeason?.optString("title").orEmpty().trim()
        if (title.isBlank()) return null
        val apiEpCount = ugcSeason?.optInt("ep_count") ?: 0
        if (apiEpCount <= 1) return null
        return BiliUgcSeason(
            id = seasonId,
            title = title,
            mid = ugcSeason?.optLong("mid") ?: data.optJSONObject("owner")?.optLong("mid") ?: 0L,
            coverUrl = normalizeImageUrl(ugcSeason?.optString("cover").orEmpty()),
            sections = emptyList(),
            apiEpCount = apiEpCount,
        )
    }

    private fun parseUgcSeason(data: JSONObject): BiliUgcSeason? {
        val season = data.optJSONObject("ugc_season") ?: return null
        val id = season.optLong("id")
        val title = season.optString("title").trim()
        val apiEpCount = season.optInt("ep_count")
        if (id <= 0L || title.isBlank()) return null
        val sectionsArray = season.optJSONArray("sections")
        val sections = if (sectionsArray != null) {
            buildList {
                for (index in 0 until sectionsArray.length()) {
                    val sectionObj = sectionsArray.optJSONObject(index) ?: continue
                    val sectionId = sectionObj.optLong("id").takeIf { it > 0L }
                        ?: sectionObj.optLong("section_id")
                    val episodes = parseUgcSeasonEpisodes(sectionObj.optJSONArray("episodes"))
                    if (episodes.isNotEmpty()) {
                        add(
                            BiliUgcSeasonSection(
                                id = sectionId,
                                title = sectionObj.optString("title"),
                                episodes = episodes,
                            ),
                        )
                    }
                }
            }
        } else {
            emptyList()
        }
        val parsedCount = sections.sumOf { it.episodes.size }
        if (parsedCount <= 1 && apiEpCount <= 1) return null
        return BiliUgcSeason(
            id = id,
            title = title,
            mid = season.optLong("mid"),
            coverUrl = normalizeImageUrl(season.optString("cover")),
            sections = sections,
            apiEpCount = apiEpCount,
        )
    }

    fun parseUgcSeasonArchives(json: JSONObject): BiliUgcSeason? {
        val data = json.optJSONObject("data") ?: return null
        val meta = data.optJSONObject("meta") ?: return null
        val seasonId = meta.optLong("season_id")
        val title = meta.optString("name").trim()
        if (seasonId <= 0L || title.isBlank()) return null
        val archives = data.optJSONArray("archives") ?: return null
        val episodes = buildList {
            for (index in 0 until archives.length()) {
                val archive = archives.optJSONObject(index) ?: continue
                val bvid = archive.optString("bvid")
                val aid = archive.optLong("aid")
                if (bvid.isBlank() && aid <= 0L) continue
                val cid = archive.optLong("cid").takeIf { it > 0L }
                    ?: archive.optJSONObject("page")?.optLong("cid")?.takeIf { it > 0L }
                    ?: 0L
                add(
                    BiliUgcSeasonEpisode(
                        id = aid,
                        aid = aid,
                        bvid = bvid,
                        cid = cid,
                        title = archive.optString("title"),
                        coverUrl = normalizeImageUrl(archive.optString("pic")),
                        durationSeconds = archive.optLong("duration").toInt(),
                    ),
                )
            }
        }
        if (episodes.isEmpty()) return null
        val page = data.optJSONObject("page")
        return BiliUgcSeason(
            id = seasonId,
            title = title.removePrefix("合集·").trim(),
            mid = meta.optLong("mid"),
            coverUrl = normalizeImageUrl(meta.optString("cover")),
            sections = listOf(
                BiliUgcSeasonSection(
                    id = 0L,
                    title = "",
                    episodes = episodes,
                ),
            ),
            apiEpCount = page?.optInt("total")?.takeIf { it > 0 } ?: episodes.size,
        )
    }

    fun mergeUgcSeasonArchives(base: BiliUgcSeason, page: BiliUgcSeason): BiliUgcSeason {
        val mergedEpisodes = (base.sections.flatMap { it.episodes } + page.sections.flatMap { it.episodes })
            .distinctBy { episode ->
                when {
                    episode.bvid.isNotBlank() -> "bvid:${episode.bvid}"
                    episode.aid > 0L -> "aid:${episode.aid}"
                    else -> "id:${episode.id}"
                }
            }
        return base.withHydratedEpisodes(mergedEpisodes).copy(
            title = base.title.ifBlank { page.title },
            mid = base.mid.takeIf { it > 0L } ?: page.mid,
            coverUrl = base.coverUrl.ifBlank { page.coverUrl },
            apiEpCount = maxOf(base.apiEpCount, page.apiEpCount, mergedEpisodes.size),
        )
    }

    private fun parseUgcSeasonEpisodes(array: org.json.JSONArray?): List<BiliUgcSeasonEpisode> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val episode = array.optJSONObject(index) ?: continue
                val arc = episode.optJSONObject("arc")
                val bvid = episode.optString("bvid").ifBlank { arc?.optString("bvid").orEmpty() }
                val aid = episode.optLong("aid").takeIf { it > 0L } ?: arc?.optLong("aid") ?: 0L
                if (bvid.isBlank() && aid <= 0L) continue
                val cid = episode.optLong("cid").takeIf { it > 0L }
                    ?: episode.optJSONObject("page")?.optLong("cid")
                    ?: arc?.optJSONArray("pages")?.optJSONObject(0)?.optLong("cid")
                    ?: 0L
                add(
                    BiliUgcSeasonEpisode(
                        id = episode.optLong("id"),
                        aid = aid,
                        bvid = bvid,
                        cid = cid,
                        title = episode.optString("title").ifBlank { arc?.optString("title").orEmpty() },
                        coverUrl = normalizeImageUrl(
                            episode.optString("cover").ifBlank { arc?.optString("pic").orEmpty() },
                        ),
                        durationSeconds = arc?.optLong("duration")?.toInt()
                            ?: episode.optLong("duration").toInt(),
                    ),
                )
            }
        }
    }

    fun parseVideoReqUser(data: JSONObject): BiliVideoRelation {
        val reqUser = data.optJSONObject("req_user") ?: return BiliVideoRelation()
        return BiliVideoRelation(
            liked = reqUser.optInt("like") == 1 || reqUser.optBoolean("like"),
            favorited = reqUser.optInt("favorite") == 1 || reqUser.optBoolean("favorite"),
            coinCount = reqUser.optInt("coin").coerceAtLeast(0),
        )
    }

    fun parseVideoArchiveRelation(json: JSONObject): BiliVideoRelation {
        val data = json.optJSONObject("data") ?: return BiliVideoRelation()
        return BiliVideoRelation(
            liked = data.optInt("like") == 1 || data.optBoolean("like"),
            favorited = data.optInt("favorite") == 1 || data.optBoolean("favorite"),
            coinCount = data.optInt("coin").coerceAtLeast(0),
        )
    }

    fun parseHasLike(json: JSONObject): Boolean {
        return when (val data = json.opt("data")) {
            is Number -> data.toInt() == 1
            is Boolean -> data
            is JSONObject -> data.optInt("like") == 1 || data.optBoolean("like")
            else -> false
        }
    }

    fun parseVideoFavoured(json: JSONObject): Boolean {
        val data = json.optJSONObject("data") ?: return false
        return data.optBoolean("favoured") || data.optInt("favoured") == 1
    }

    fun mergeVideoRelations(vararg relations: BiliVideoRelation): BiliVideoRelation {
        if (relations.isEmpty()) return BiliVideoRelation()
        return BiliVideoRelation(
            liked = relations.any { it.liked },
            favorited = relations.any { it.favorited },
            coinCount = relations.maxOf { it.coinCount },
        )
    }

    fun parseVideoTripleResult(json: JSONObject): BiliVideoTripleResult {
        val data = json.optJSONObject("data") ?: return BiliVideoTripleResult()
        return BiliVideoTripleResult(
            liked = data.optInt("like") == 1 || data.optBoolean("like"),
            coined = data.optInt("coin") == 1 || data.optBoolean("coin"),
            favorited = data.optInt("fav") == 1 || data.optBoolean("fav"),
        )
    }

    fun parseDefaultFavoriteFolderId(json: JSONObject): Long? {
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: return null
        val folders = buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val id = item.optLong("id").takeIf { it > 0L }
                    ?: item.optLong("media_id").takeIf { it > 0L }
                    ?: continue
                add(item to id)
            }
        }
        if (folders.isEmpty()) return null
        return folders.firstOrNull { (item, _) -> item.optString("title") == "默认收藏夹" }?.second
            ?: folders.maxByOrNull { (item, _) -> item.optInt("media_count") }?.second
            ?: folders.first().second
    }

    fun parseFavoriteVideoPage(json: JSONObject, page: Int, pageSize: Int): BiliFavoriteVideoPage {
        val data = json.optJSONObject("data")
            ?: return BiliFavoriteVideoPage(emptyList(), page, false)
        val medias = data.optJSONArray("medias") ?: org.json.JSONArray()
        val videos = buildList(medias.length()) {
            for (index in 0 until medias.length()) {
                parseFavoriteMedia(medias.optJSONObject(index) ?: continue)?.let(::add)
            }
        }
        val totalCount = data.optJSONObject("info")?.optInt("media_count") ?: videos.size
        val hasMore = data.optBoolean("has_more") ||
            (totalCount > 0 && page * pageSize < totalCount)
        return BiliFavoriteVideoPage(
            videos = videos,
            page = page,
            hasMore = hasMore,
        )
    }

    private fun parseFavoriteMedia(item: JSONObject): BiliVideoItem? {
        val mediaType = item.optInt("type")
        if (mediaType != 0 && mediaType != 2) return null
        var bvid = item.optString("bvid").ifBlank { item.optString("bv_id") }
        if (bvid.isBlank()) {
            bvid = extractBvidFromUrl(item.optString("link"))
        }
        if (bvid.isBlank()) return null
        val upper = item.optJSONObject("upper") ?: JSONObject()
        val cntInfo = item.optJSONObject("cnt_info") ?: JSONObject()
        return BiliVideoItem(
            bvid = bvid,
            aid = item.optLong("id"),
            title = item.optString("title"),
            coverUrl = normalizeImageUrl(item.optString("cover")),
            authorName = upper.optString("name"),
            authorMid = upper.optLong("mid"),
            authorFace = normalizeImageUrl(upper.optString("face")),
            viewCount = cntInfo.optLong("play"),
            danmakuCount = cntInfo.optLong("danmaku"),
            likeCount = 0L,
            durationSeconds = item.optInt("duration"),
        )
    }

    fun isPlausibleIpLocation(value: String): Boolean {
        if (value.isBlank() || value == "未知") return false
        if (value.contains("浏览") || value.contains("播放") || value.contains("弹幕")) return false
        if (value.contains("转发") || value.contains("评论") || value.contains("赞")) return false
        if (value.contains("IP属地")) return false
        if (value.any { it.isDigit() }) return false
        if (value.length !in 2..12) return false
        return value.any { it in '\u4e00'..'\u9fff' }
    }

    fun normalizeIpLocation(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "未知") return null
        var value = raw
            .removePrefix("IP属地：")
            .removePrefix("IP属地:")
            .trim()
        if (value.contains("IP属地")) {
            value = value
                .substringAfterLast("IP属地：")
                .substringAfterLast("IP属地:")
                .trim()
        }
        if (value.isBlank() || value == "未知") return null
        return value.takeIf { isPlausibleIpLocation(it) }
    }

    fun parseAuthorIpLocation(author: org.json.JSONObject?): String? {
        if (author == null) return null
        return normalizeIpLocation(
            author.optString("pub_location_text").takeIf { it.isNotBlank() }
                ?: author.optString("ptime_location_text").takeIf { it.isNotBlank() },
        )
    }

    fun parseDynamicDetail(json: JSONObject): BiliDynamicItem? {
        val item = json.optJSONObject("data")?.optJSONObject("item") ?: return null
        return parseSpaceDynamicItem(item)
    }

    fun parseArticleDetail(json: JSONObject): BiliArticleDetail? {
        val data = json.optJSONObject("data") ?: return null
        val cvId = data.optLong("id").takeIf { it > 0L }
            ?: data.optString("id").toLongOrNull()?.takeIf { it > 0L }
            ?: return null
        val content = data.optString("content")
        if (content.isBlank()) return null
        val author = data.optJSONObject("author")
        val stats = data.optJSONObject("stats")
        return BiliArticleDetail(
            cvId = cvId,
            title = data.optString("title"),
            htmlContent = content,
            summary = data.optString("summary"),
            authorName = author?.optString("name").orEmpty(),
            authorMid = author?.optLong("mid") ?: 0L,
            authorFace = normalizeImageUrl(author?.optString("face").orEmpty()),
            publishTimeSeconds = data.optLong("publish_time").takeIf { it > 0L }
                ?: data.optLong("ctime"),
            viewCount = stats?.optLong("view") ?: 0L,
            likeCount = stats?.optLong("like") ?: 0L,
            commentCount = stats?.optLong("reply") ?: 0L,
        )
    }

    fun parseOpusArticleCvId(json: JSONObject): Long? {
        val item = json.optJSONObject("data")?.optJSONObject("item") ?: return null
        val basic = item.optJSONObject("basic") ?: return null
        if (basic.optInt("comment_type", 0) != 12) return null
        return basic.optString("rid_str").toLongOrNull()?.takeIf { it > 0L }
    }

    fun parseOnlineCount(json: JSONObject): Long {
        val total = json.optJSONObject("data")?.optJSONObject("total")
            ?: json.optJSONObject("data")
            ?: return 0L
        return total.optLong("total")
            .takeIf { it > 0L }
            ?: total.optLong("count")
    }

    fun parseUserNavnum(json: JSONObject): Long {
        val data = json.optJSONObject("data") ?: return 0L
        return data.optLong("video")
            .takeIf { it > 0L }
            ?: data.optJSONObject("archive")?.optLong("count")
            ?: 0L
    }

    fun parseUserRelation(json: JSONObject): BiliAuthorRelation {
        val attribute = json.optJSONObject("data")?.optInt("attribute") ?: 0
        return relationFromAttribute(attribute)
    }

    fun relationFromAttribute(attribute: Int): BiliAuthorRelation =
        BiliAuthorRelation(
            following = attribute == 2 || attribute == 6,
            followerMe = attribute == 6,
        )

    fun parseRelationUserItem(json: JSONObject): BiliRelationUser? {
        val mid = json.optLong("mid")
        if (mid <= 0L) return null
        val ipRaw = json.optString("location").takeIf { it.isNotBlank() }
            ?: json.optJSONObject("res")?.optString("location")
            ?: json.optJSONObject("user")?.optString("location")
        return BiliRelationUser(
            mid = mid,
            name = json.optString("uname"),
            face = normalizeImageUrl(json.optString("face")),
            sign = json.optString("sign"),
            relation = relationFromAttribute(json.optInt("attribute")),
            fanCount = sequenceOf(
                json.optLong("fans"),
                json.optLong("follower"),
                json.optJSONObject("official")?.optLong("fans") ?: 0L,
            ).firstOrNull { it > 0L } ?: 0L,
            ipLocation = normalizeIpLocation(ipRaw),
        )
    }

    fun parseRelationUserPage(json: JSONObject, pageSize: Int): BiliRelationUserPage {
        val code = json.optInt("code")
        if (code != 0) {
            val message = json.optString("message").takeIf { it.isNotBlank() && it != "0" }
                ?: when (code) {
                    22115, 22118 -> "由于该用户隐私设置，列表不可见"
                    -101 -> "登录后查看"
                    else -> "加载失败"
                }
            return BiliRelationUserPage(users = emptyList(), errorMessage = message)
        }
        val data = json.optJSONObject("data")
            ?: return BiliRelationUserPage(users = emptyList())
        val list = data.optJSONArray("list") ?: org.json.JSONArray()
        val users = buildList(list.length()) {
            for (index in 0 until list.length()) {
                list.optJSONObject(index)?.let(::parseRelationUserItem)?.let(::add)
            }
        }
        return BiliRelationUserPage(
            users = users,
            hasMore = users.size >= pageSize,
            total = data.optLong("total"),
        )
    }

    fun parseUserCardRelation(json: JSONObject): BiliAuthorRelation? {
        val data = json.optJSONObject("data") ?: return null
        if (!data.has("following")) return null
        return BiliAuthorRelation(following = data.optBoolean("following"))
    }

    /** @deprecated 该接口返回的是关注/粉丝数量，不是与当前用户的关系 */
    fun parseRelationStat(json: JSONObject): BiliAuthorRelation {
        val data = json.optJSONObject("data") ?: return BiliAuthorRelation()
        return BiliAuthorRelation(
            following = data.optInt("following") == 1,
            followerMe = data.optInt("follower") == 1,
        )
    }

    fun parseCommentPage(json: JSONObject): BiliCommentPage {
        val data = json.optJSONObject("data") ?: return BiliCommentPage(emptyList())
        val cursor = data.optJSONObject("cursor")
        val pinned = parsePinnedCommentItems(data)
        val pinnedIds = pinned.map { it.id }.toSet()
        val replies = data.optJSONArray("replies") ?: JSONArray()
        val regular = buildList(replies.length()) {
            for (index in 0 until replies.length()) {
                replies.optJSONObject(index)
                    ?.let { parseCommentItem(it, includeInlineReplies = true) }
                    ?.takeIf { it.id !in pinnedIds }
                    ?.let(::add)
            }
        }
        val comments = pinned + regular
        val paginationReply = parseCommentPaginationReply(cursor)
        val nextCursor = parseCommentNextCursor(cursor, paginationReply)
        val totalCount = cursor?.optLong("all_count")
            ?: data.optJSONObject("page")?.optLong("acount")
            ?: 0L
        val isEnd = resolveCommentPageIsEnd(
            cursor = cursor,
            nextCursor = nextCursor,
            pageCommentCount = comments.size,
            totalCount = totalCount,
        )
        return BiliCommentPage(
            comments = comments,
            nextCursor = nextCursor,
            isEnd = isEnd,
            totalCount = totalCount,
        )
    }

    private fun parseCommentPaginationReply(cursor: JSONObject?): JSONObject? {
        if (cursor == null) return null
        cursor.optJSONObject("pagination_reply")?.let { return it }
        val raw = cursor.optString("pagination_reply")
        if (raw.isNotBlank() && raw.startsWith("{")) {
            runCatching { JSONObject(raw) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun parseCommentNextCursor(
        cursor: JSONObject?,
        paginationReply: JSONObject?,
    ): String? {
        paginationReply?.optString("next_offset")?.takeIf { it.isNotBlank() }?.let { return it }
        paginationReply?.optJSONObject("next_offset")?.takeIf { it.length() > 0 }?.let {
            return it.toString()
        }
        val legacyNext = cursor?.optLong("next")?.takeIf { it > 0L } ?: return null
        val mode = cursor?.optInt("mode") ?: 3
        return when (mode) {
            2 -> JSONObject()
                .put("type", 1)
                .put("direction", 1)
                .put("data", JSONObject().put("cursor", legacyNext))
                .toString()
            3 -> JSONObject()
                .put("type", 3)
                .put("direction", 1)
                .put("Data", JSONObject().put("cursor", legacyNext))
                .toString()
            else -> JSONObject()
                .put("type", 1)
                .put("direction", 1)
                .put("data", JSONObject().put("cursor", legacyNext))
                .toString()
        }
    }

    private fun resolveCommentPageIsEnd(
        cursor: JSONObject?,
        nextCursor: String?,
        pageCommentCount: Int,
        totalCount: Long,
    ): Boolean {
        if (!nextCursor.isNullOrBlank()) {
            if (totalCount > 0L && pageCommentCount.toLong() >= totalCount) return true
            if (cursor?.has("is_end") == true && cursor.optBoolean("is_end")) {
                if (totalCount > 0L && pageCommentCount.toLong() < totalCount) return false
                return true
            }
            return false
        }
        if (totalCount > 0L && pageCommentCount.toLong() < totalCount) return false
        if (cursor?.has("is_end") == true) return cursor.optBoolean("is_end")
        return pageCommentCount == 0
    }

    private fun JSONObject.parseEmoteMap(): Map<String, String> {
        val emoteObject = optJSONObject("emote") ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        val keys = emoteObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val url = emoteObject.optJSONObject(key)?.optString("url").orEmpty()
            if (url.isNotBlank()) {
                result[key] = normalizeImageUrl(url)
            }
        }
        return result
    }

    private fun JSONObject.parseCommentPictures(): List<BiliCommentPicture> {
        val pictures = optJSONArray("pictures") ?: return emptyList()
        return buildList(pictures.length()) {
            for (index in 0 until pictures.length()) {
                val item = pictures.optJSONObject(index) ?: continue
                val url = normalizeImageUrl(
                    item.optString("img_src")
                        .ifBlank { item.optString("src") }
                        .ifBlank { item.optString("img_url") },
                )
                if (url.isNotBlank()) {
                    add(
                        BiliCommentPicture(
                            url = url,
                            width = item.optInt("img_width"),
                            height = item.optInt("img_height"),
                        ),
                    )
                }
            }
        }
    }

    fun parseCommentReplyPage(json: JSONObject): BiliCommentReplyPage {
        val data = json.optJSONObject("data") ?: return BiliCommentReplyPage(emptyList())
        val replies = data.optJSONArray("replies") ?: JSONArray()
        val pageInfo = data.optJSONObject("page")
        val totalCount = pageInfo?.optLong("count") ?: 0L
        val currentPage = pageInfo?.optInt("num") ?: 1
        val pageSize = pageInfo?.optInt("size") ?: 20
        val items = buildList(replies.length()) {
            for (index in 0 until replies.length()) {
                replies.optJSONObject(index)
                    ?.let { parseCommentItem(it, nested = true, includeInlineReplies = false) }
                    ?.let(::add)
            }
        }
        val isEnd = items.isEmpty() || currentPage * pageSize >= totalCount
        return BiliCommentReplyPage(
            replies = items,
            totalCount = totalCount,
            page = currentPage,
            isEnd = isEnd,
        )
    }

    private fun parsePinnedCommentItems(data: JSONObject): List<BiliCommentItem> {
        val result = mutableListOf<BiliCommentItem>()
        val seen = mutableSetOf<Long>()

        fun append(raw: JSONObject?) {
            val item = raw?.let {
                parseCommentItem(it, includeInlineReplies = true, isPinned = true)
            } ?: return
            if (seen.add(item.id)) {
                result += item
            }
        }

        data.optJSONObject("top")?.let { top ->
            append(top.optJSONObject("upper"))
            append(top.optJSONObject("admin"))
            append(top.optJSONObject("vote"))
        }
        data.optJSONObject("upper")?.let { upper ->
            append(upper.optJSONObject("top"))
        }
        data.optJSONArray("top_replies")?.let { topReplies ->
            for (index in 0 until topReplies.length()) {
                append(topReplies.optJSONObject(index))
            }
        }
        return result
    }

    private fun parseCommentItem(
        json: JSONObject,
        nested: Boolean = false,
        includeInlineReplies: Boolean = false,
        isPinned: Boolean = false,
    ): BiliCommentItem? {
        val member = json.optJSONObject("member") ?: return null
        val content = json.optJSONObject("content") ?: JSONObject()
        val message = content.optString("message")
        val pictures = content.parseCommentPictures()
        val emoticons = content.parseEmoteMap()
        if (message.isBlank() && pictures.isEmpty() && emoticons.isEmpty() && !includeInlineReplies) {
            return null
        }
        val nestedReplies = json.optJSONArray("replies") ?: JSONArray()
        return BiliCommentItem(
            id = json.optLong("rpid"),
            authorMid = member.optLong("mid"),
            authorName = member.optString("uname"),
            authorFace = normalizeImageUrl(member.optString("avatar")),
            level = member.optJSONObject("level_info")?.optInt("current_level") ?: 0,
            content = message,
            likeCount = json.optLong("like"),
            replyCount = json.optLong("count"),
            publishTimeSeconds = json.optLong("ctime"),
            ipLocation = normalizeIpLocation(
                json.optJSONObject("reply_control")?.optString("location"),
            ),
            emoticons = emoticons,
            pictures = pictures,
            replies = if (includeInlineReplies) {
                buildList(nestedReplies.length()) {
                    for (index in 0 until nestedReplies.length()) {
                        nestedReplies.optJSONObject(index)
                            ?.let { parseCommentItem(it, nested = true, includeInlineReplies = false) }
                            ?.let(::add)
                    }
                }
            } else {
                emptyList()
            },
            isPinned = isPinned,
        )
    }

    fun parseVideoShot(json: JSONObject): BiliVideoShot? {
        val data = json.optJSONObject("data") ?: return null
        val images = data.optJSONArray("image")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    normalizeImageUrl(array.optString(index)).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        } ?: emptyList()
        if (images.isEmpty()) return null
        val indexSeconds = data.optJSONArray("index")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    add(array.optInt(index))
                }
            }
        } ?: emptyList()
        return BiliVideoShot(
            images = images,
            indexSeconds = indexSeconds,
            tileColumns = data.optInt("img_x_len", 10).coerceAtLeast(1),
            tileRows = data.optInt("img_y_len", 10).coerceAtLeast(1),
            tileWidth = data.optInt("img_x_size", 160).coerceAtLeast(1),
            tileHeight = data.optInt("img_y_size", 90).coerceAtLeast(1),
        )
    }

    fun parsePlayUrl(json: JSONObject): BiliPlayStream? {
        val data = json.optJSONObject("data") ?: return null
        val dash = data.optJSONObject("dash")
        if (dash != null) {
            val videoUrl = dash.pickStreamUrl("video") ?: return null
            val audioUrl = dash.pickStreamUrl("audio")
            return BiliPlayStream(videoUrl = videoUrl, audioUrl = audioUrl)
        }
        val durl = data.optJSONArray("durl")?.optJSONObject(0)?.optString("url")
        return durl?.takeIf { it.isNotBlank() }?.let { BiliPlayStream(videoUrl = it) }
    }

    private fun JSONObject.pickStreamUrl(type: String): String? {
        val stream = optJSONArray(type)?.optJSONObject(0) ?: return null
        return stream.optJSONArray("backup_url")?.optString(0)?.takeIf { it.isNotBlank() }
            ?: stream.optString("baseUrl").takeIf { it.isNotBlank() }
    }

    fun parseMyInfo(json: JSONObject): BiliUserProfile? {
        val data = json.optJSONObject("data") ?: return null
        return BiliUserProfile(
            mid = data.optLong("mid"),
            name = data.optString("name"),
            face = normalizeImageUrl(data.optString("face")),
            sign = data.optString("sign"),
            level = data.optInt("level"),
        )
    }

    fun parseUserWallet(json: JSONObject): BiliUserWallet? {
        val data = json.optJSONObject("data") ?: return null
        if (!data.optBoolean("isLogin", data.optInt("mid") > 0)) return null
        val wallet = data.optJSONObject("wallet")
        val bcoinBalance = wallet?.optDouble("bcoin_balance")
            ?: data.optDouble("bcoin_balance")
        val coinCount = data.optLong("money")
        return BiliUserWallet(
            bcoinBalance = bcoinBalance,
            coinCount = coinCount.coerceAtLeast(0L),
        )
    }

    fun parseUserInfo(json: JSONObject): BiliUserProfile? {
        val data = json.optJSONObject("data") ?: return null
        val levelInfo = data.optJSONObject("level_info")
        val follower = sequenceOf(
            data.optLong("follower"),
            data.optLong("fans"),
            data.optJSONObject("relation")?.optJSONObject("stat")?.optLong("follower") ?: 0L,
        ).firstOrNull { it > 0L } ?: 0L
        val following = sequenceOf(
            data.optLong("following"),
            data.optLong("attention"),
            data.optLong("friend"),
        ).firstOrNull { it > 0L } ?: 0L
        val likes = sequenceOf(
            data.optJSONObject("likes")?.optLong("total_liked") ?: 0L,
            data.optLong("like_num"),
        ).firstOrNull { it > 0L } ?: 0L
        return BiliUserProfile(
            mid = data.optLong("mid"),
            name = data.optString("name"),
            face = normalizeImageUrl(data.optString("face")),
            sign = data.optString("sign"),
            level = data.optInt("level").takeIf { it > 0 }
                ?: levelInfo?.optInt("current_level")
                ?: 0,
            following = following,
            follower = follower,
            likes = likes,
            topPhoto = parseUserTopPhotos(data).firstOrNull().orEmpty(),
            topPhotos = parseUserTopPhotos(data),
        )
    }

    fun parseUserCardProfile(json: JSONObject): BiliUserProfile? {
        val data = json.optJSONObject("data") ?: return null
        val card = data.optJSONObject("card") ?: return null
        val levelInfo = card.optJSONObject("level_info")
        val mid = card.optString("mid").toLongOrNull() ?: card.optLong("mid")
        if (mid <= 0L) return null
        return BiliUserProfile(
            mid = mid,
            name = card.optString("name"),
            face = normalizeImageUrl(card.optString("face")),
            sign = card.optString("sign").ifBlank { card.optString("Sign") },
            level = levelInfo?.optInt("current_level") ?: 0,
            following = card.optLong("attention"),
            follower = card.optLong("fans"),
            topPhoto = parseUserTopPhotos(card).firstOrNull().orEmpty(),
            topPhotos = parseUserTopPhotos(card),
        )
    }

    fun parseUserUpstatLikes(json: JSONObject): Long {
        val data = json.optJSONObject("data") ?: return 0L
        return data.optLong("likes").takeIf { it > 0L } ?: 0L
    }

    fun parseUserCardFollower(json: JSONObject): Long {
        val profile = parseUserCardProfile(json)
        if (profile != null && profile.follower > 0L) return profile.follower
        val data = json.optJSONObject("data") ?: return 0L
        return data.optLong("follower")
            .takeIf { it > 0L }
            ?: data.optJSONObject("card")?.optLong("fans")
            ?: 0L
    }

    fun parseUserVideos(json: JSONObject): List<BiliVideoItem> =
        parseUserVideoPage(json).videos

    fun parseUserVideoPage(json: JSONObject): BiliUserVideoPage {
        val list = json.optJSONObject("data")
            ?.optJSONObject("list")
            ?.optJSONArray("vlist")
            ?: return BiliUserVideoPage(emptyList())
        val videos = buildList(list.length()) {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                add(
                    BiliVideoItem(
                        bvid = item.optString("bvid"),
                        aid = item.optLong("aid"),
                        title = item.optString("title"),
                        coverUrl = normalizeImageUrl(item.optString("pic")),
                        authorName = item.optString("author"),
                        authorMid = item.optLong("mid"),
                        viewCount = item.optLong("play"),
                        danmakuCount = item.optLong("video_review"),
                        likeCount = 0L,
                        durationSeconds = parseUserVideoDuration(item),
                        description = item.optString("description"),
                        publishTimeSeconds = item.optLong("created").takeIf { it > 0L }
                            ?: item.optLong("pubdate"),
                    ),
                )
            }
        }
        return BiliUserVideoPage(
            videos = videos,
            hasMore = videos.size >= 30,
        )
    }

    fun parseSpaceDynamicFeed(json: JSONObject): BiliDynamicFeedPage {
        val data = json.optJSONObject("data") ?: return BiliDynamicFeedPage(emptyList())
        val items = data.optJSONArray("items") ?: return BiliDynamicFeedPage(emptyList())
        val offset = data.optString("offset").takeIf { it.isNotBlank() }
        val hasMore = data.optBoolean("has_more", offset != null)
        val parsed = buildList {
            for (index in 0 until items.length()) {
                parseSpaceDynamicItem(items.optJSONObject(index) ?: continue)?.let(::add)
            }
        }
        return BiliDynamicFeedPage(
            items = parsed,
            nextOffset = offset,
            hasMore = hasMore,
        )
    }

    private fun parseSpaceDynamicItem(item: JSONObject): BiliDynamicItem? {
        val id = item.optString("id_str").ifBlank { item.optString("id") }
        if (id.isBlank()) return null
        val modules = item.optJSONObject("modules") ?: return null
        val origItem = item.optJSONObject("orig")?.takeIf { it.optJSONObject("modules") != null }
        val isForward = item.optString("type") == "DYNAMIC_TYPE_FORWARD" && origItem != null ||
            origItem != null
        val stat = modules.optJSONObject("module_stat")
        val meta = parseDynamicItemMeta(item, modules)
        val publishTime = modules.optJSONObject("module_author")?.optLong("pub_ts")
            ?.takeIf { it > 0L }
            ?: item.optLong("pub_ts")

        if (isForward) {
            val forwardRich = parseDynamicDesc(modules)
            val origin = origItem?.optJSONObject("modules")?.let { origModules ->
                parseDynamicBody(origItem, origModules).let { body ->
                    BiliDynamicOrigin(
                        authorName = parseModuleAuthorName(origModules),
                        text = body.text,
                        emoticons = body.emoticons,
                        video = body.video,
                        imageUrls = body.imageUrls,
                        link = body.link,
                    )
                }
            }
            return BiliDynamicItem(
                id = id,
                text = forwardRich.text,
                emoticons = forwardRich.emoticons,
                publishTimeSeconds = publishTime,
                origin = origin,
                authorMid = meta.authorMid,
                authorName = meta.authorName,
                authorFace = meta.authorFace,
                authorLevel = meta.authorLevel,
                ipLocation = meta.ipLocation,
                commentOid = meta.commentOid,
                commentType = meta.commentType,
                dynamicType = meta.dynamicType,
                likeCount = parseDynamicStatCount(stat, "like"),
                commentCount = parseDynamicStatCount(stat, "comment"),
                repostCount = parseDynamicStatCount(stat, "forward"),
            )
        }

        val body = parseDynamicBody(item, modules)
        return BiliDynamicItem(
            id = id,
            text = body.text,
            emoticons = body.emoticons,
            publishTimeSeconds = publishTime,
            video = body.video,
            imageUrls = body.imageUrls,
            link = body.link,
            authorMid = meta.authorMid,
            authorName = meta.authorName,
            authorFace = meta.authorFace,
            authorLevel = meta.authorLevel,
            ipLocation = meta.ipLocation,
            commentOid = meta.commentOid,
            commentType = meta.commentType,
            dynamicType = meta.dynamicType,
            likeCount = parseDynamicStatCount(stat, "like"),
            commentCount = parseDynamicStatCount(stat, "comment"),
            repostCount = parseDynamicStatCount(stat, "forward"),
        )
    }

    private data class DynamicItemMeta(
        val authorMid: Long,
        val authorName: String,
        val authorFace: String,
        val authorLevel: Int,
        val ipLocation: String?,
        val commentOid: Long,
        val commentType: Int,
        val dynamicType: String,
    )

    private fun parseDynamicItemMeta(item: JSONObject, modules: JSONObject): DynamicItemMeta {
        val author = modules.optJSONObject("module_author")
        val dynamicType = item.optString("type")
        val basic = item.optJSONObject("basic")
        var commentType = basic?.optInt("comment_type", 0) ?: 0
        var commentOid = basic?.optString("comment_id_str")?.toLongOrNull()?.takeIf { it > 0L }
            ?: basic?.optString("rid_str")?.toLongOrNull()?.takeIf { it > 0L }
            ?: 0L
        if (commentType <= 0) {
            commentType = fallbackDynamicCommentType(dynamicType)
        }
        if (commentOid <= 0L) {
            commentOid = fallbackDynamicCommentOid(item, dynamicType)
        }
        return DynamicItemMeta(
            authorMid = author?.optLong("mid") ?: 0L,
            authorName = parseModuleAuthorName(modules),
            authorFace = normalizeImageUrl(author?.optString("face").orEmpty()),
            authorLevel = author?.optJSONObject("badge")?.optInt("level")
                ?: author?.optInt("level")
                ?: 0,
            ipLocation = parseAuthorIpLocation(author),
            commentOid = commentOid,
            commentType = commentType,
            dynamicType = dynamicType,
        )
    }

    private fun fallbackDynamicCommentType(dynamicType: String): Int = when (dynamicType) {
        "DYNAMIC_TYPE_WORD", "DYNAMIC_TYPE_FORWARD", "DYNAMIC_TYPE_LIVE_RCMD", "DYNAMIC_TYPE_OPUS" -> 17
        "DYNAMIC_TYPE_DRAW" -> 11
        "DYNAMIC_TYPE_AV", "DYNAMIC_TYPE_UGC_SEASON" -> 1
        "DYNAMIC_TYPE_ARTICLE" -> 12
        else -> 0
    }

    private fun fallbackDynamicCommentOid(item: JSONObject, dynamicType: String): Long {
        val id = item.optString("id_str").ifBlank { item.optString("id") }.toLongOrNull() ?: 0L
        val rid = item.optJSONObject("basic")?.optString("rid_str")?.toLongOrNull()?.takeIf { it > 0L }
        return when (dynamicType) {
            "DYNAMIC_TYPE_WORD", "DYNAMIC_TYPE_FORWARD", "DYNAMIC_TYPE_LIVE_RCMD", "DYNAMIC_TYPE_OPUS" -> id
            "DYNAMIC_TYPE_DRAW" -> rid ?: id
            else -> rid ?: id
        }
    }

    private data class DynamicBody(
        val text: String = "",
        val emoticons: Map<String, String> = emptyMap(),
        val video: BiliVideoItem? = null,
        val imageUrls: List<String> = emptyList(),
        val link: BiliDynamicLink? = null,
    )

    private data class DynamicRichText(
        val text: String = "",
        val emoticons: Map<String, String> = emptyMap(),
    )

    private fun parseDynamicBody(item: JSONObject, modules: JSONObject): DynamicBody {
        val moduleDynamic = modules.optJSONObject("module_dynamic")
        val descRich = parseDynamicDesc(modules)
        var text = descRich.text
        var emoticons = descRich.emoticons.toMutableMap()
        var video: BiliVideoItem? = null
        var imageUrls = parseRichTextImages(moduleDynamic?.optJSONObject("desc"))
        var link: BiliDynamicLink? = null

        moduleDynamic?.optJSONObject("major")?.let { major ->
            val parsed = parseDynamicMajor(major, modules)
            if (parsed.text.isNotBlank() && !isDuplicateDynamicText(text, parsed.text)) {
                text = mergeDynamicText(text, parsed.text)
            }
            emoticons.putAll(parsed.emoticons)
            video = parsed.video
            imageUrls = parsed.imageUrls
            link = parsed.link
        }

        parseDynamicAdditional(moduleDynamic, modules)?.let { additional ->
            if (additional.text.isNotBlank() && !isDuplicateDynamicText(text, additional.text)) {
                text = mergeDynamicText(text, additional.text)
            }
            emoticons.putAll(additional.emoticons)
            if (video == null) video = additional.video
            if (link == null) link = additional.link
            if (imageUrls.isEmpty()) imageUrls = additional.imageUrls
        }

        if (video == null && imageUrls.isEmpty() && link == null) {
            parseDynamicItemFallback(item, modules)?.let { fallback ->
                if (fallback.text.isNotBlank() && !isDuplicateDynamicText(text, fallback.text)) {
                    text = mergeDynamicText(text, fallback.text)
                }
                emoticons.putAll(fallback.emoticons)
                if (imageUrls.isEmpty()) imageUrls = fallback.imageUrls
                if (link == null) link = fallback.link
            }
        }

        if (video == null) {
            parseDynamicBasicVideo(item, modules)?.let { basicVideo ->
                video = basicVideo
            }
        }

        if (video != null) {
            link = null
        } else if (link != null && !shouldShowDynamicLink(text, link, imageUrls)) {
            link = null
        }

        return DynamicBody(
            text = text,
            emoticons = emoticons,
            video = video,
            imageUrls = imageUrls,
            link = link,
        )
    }

    private fun parseDynamicItemFallback(item: JSONObject, modules: JSONObject): DynamicBody? {
        val dynamicType = item.optString("type")
        val moduleDynamic = modules.optJSONObject("module_dynamic")
        val major = moduleDynamic?.optJSONObject("major")

        moduleDynamic?.optJSONObject("additional")?.optJSONObject("ugc")?.let { ugc ->
            parseAdditionalUgcVideo(ugc, modules)?.let { return DynamicBody(video = it) }
        }

        if (major != null && major.length() > 0) {
            val inferred = parseDynamicMajor(major, modules)
            if (inferred.video != null || inferred.imageUrls.isNotEmpty() || inferred.link != null) {
                return inferred
            }
        }

        return when (dynamicType) {
            "DYNAMIC_TYPE_AV" -> {
                major?.optJSONObject("archive")?.let {
                    DynamicBody(video = parseDynamicArchiveVideo(major, modules))
                }
            }
            "DYNAMIC_TYPE_DRAW" -> major?.let { DynamicBody(imageUrls = parseDynamicDrawImages(it)) }
            else -> null
        }
    }

    private fun parseDynamicBasicVideo(item: JSONObject, modules: JSONObject): BiliVideoItem? {
        val basic = item.optJSONObject("basic") ?: return null
        if (basic.optInt("comment_type", -1) != 1) return null
        val aid = basic.optString("rid_str").toLongOrNull()?.takeIf { it > 0L } ?: return null

        val moduleDynamic = modules.optJSONObject("module_dynamic")
        val major = moduleDynamic?.optJSONObject("major")
        major?.optJSONObject("archive")?.let { archive ->
            parseDynamicArchiveVideo(major, modules)?.let { return it }
        }
        moduleDynamic?.optJSONObject("additional")?.optJSONObject("ugc")?.let { ugc ->
            parseAdditionalUgcVideo(ugc, modules)?.let { return it }
        }

        val author = modules.optJSONObject("module_author")
        return BiliVideoItem(
            bvid = "av$aid",
            aid = aid,
            title = "",
            coverUrl = "",
            authorName = author?.optString("name").orEmpty(),
            authorMid = author?.optLong("mid") ?: 0L,
            authorFace = normalizeImageUrl(author?.optString("face").orEmpty()),
            viewCount = 0L,
            danmakuCount = 0L,
            likeCount = 0L,
            durationSeconds = 0,
        )
    }

    private fun inferDynamicMajorType(major: JSONObject): String {
        major.optString("type").takeIf { it.isNotBlank() }?.let { return it }
        return when {
            major.has("archive") -> "MAJOR_TYPE_ARCHIVE"
            major.has("draw") -> "MAJOR_TYPE_DRAW"
            major.has("opus") -> "MAJOR_TYPE_OPUS"
            major.has("article") -> "MAJOR_TYPE_ARTICLE"
            major.has("common") -> "MAJOR_TYPE_COMMON"
            major.has("live") -> "MAJOR_TYPE_LIVE"
            major.has("live_rcmd") -> "MAJOR_TYPE_LIVE_RCMD"
            major.has("music") -> "MAJOR_TYPE_MUSIC"
            major.has("none") -> "MAJOR_TYPE_NONE"
            else -> ""
        }
    }

    private fun parseDynamicMajor(major: JSONObject, modules: JSONObject): DynamicBody {
        val majorType = inferDynamicMajorType(major)
        var text = ""
        var emoticons = mutableMapOf<String, String>()
        var video: BiliVideoItem? = null
        var imageUrls = emptyList<String>()
        var link: BiliDynamicLink? = null

        when (majorType) {
            "MAJOR_TYPE_ARCHIVE" -> {
                video = parseDynamicArchiveVideo(major, modules)
                if (video == null) {
                    link = parseDynamicArchiveLink(major)
                }
            }
            "MAJOR_TYPE_DRAW" -> imageUrls = parseDynamicDrawImages(major)
            "MAJOR_TYPE_OPUS" -> {
                val opus = major.optJSONObject("opus")
                val summaryRich = parseDynamicRichText(opus?.optJSONObject("summary"))
                if (summaryRich.text.isNotBlank()) {
                    text = summaryRich.text
                }
                emoticons.putAll(summaryRich.emoticons)
                imageUrls = parseDynamicOpusImages(major)
                if (imageUrls.isEmpty()) {
                    imageUrls = parseRichTextImages(opus?.optJSONObject("summary"))
                }
                val opusTitle = opus?.optString("title").orEmpty()
                if (text.isBlank() && opusTitle.isNotBlank()) {
                    text = opusTitle
                }
                if (imageUrls.isEmpty()) {
                    link = parseDynamicLink(major, majorType)
                }
                val jumpUrl = normalizeJumpUrl(opus?.optString("jump_url").orEmpty())
                val bvid = extractBvidFromUrl(jumpUrl)
                if (bvid.isNotBlank()) {
                    val author = modules.optJSONObject("module_author")
                    video = BiliVideoItem(
                        bvid = bvid,
                        aid = 0L,
                        title = opus?.optString("title").orEmpty(),
                        coverUrl = imageUrls.firstOrNull().orEmpty(),
                        authorName = author?.optString("name").orEmpty(),
                        authorMid = author?.optLong("mid") ?: 0L,
                        viewCount = 0L,
                        danmakuCount = 0L,
                        likeCount = 0L,
                        durationSeconds = 0,
                    )
                    link = null
                }
            }
            "MAJOR_TYPE_LIVE" -> link = parseDynamicLiveLink(major.optJSONObject("live"))
            "MAJOR_TYPE_LIVE_RCMD" -> {
                val liveContent = major.optJSONObject("live_rcmd")?.optString("content").orEmpty()
                val liveJson = runCatching { JSONObject(liveContent) }.getOrNull()
                link = parseDynamicLiveLink(liveJson)
            }
            "MAJOR_TYPE_MUSIC" -> link = parseDynamicMusicLink(major.optJSONObject("music"))
            "MAJOR_TYPE_NONE" -> {
                text = major.optJSONObject("none")?.optString("tips").orEmpty()
            }
            else -> link = parseDynamicLink(major, majorType)
        }

        return DynamicBody(
            text = text,
            emoticons = emoticons,
            video = video,
            imageUrls = imageUrls,
            link = link,
        )
    }

    private fun parseDynamicAdditional(
        moduleDynamic: JSONObject?,
        modules: JSONObject,
    ): DynamicBody? {
        val additional = moduleDynamic?.optJSONObject("additional") ?: return null
        additional.optJSONObject("ugc")?.let { ugc ->
            return DynamicBody(video = parseAdditionalUgcVideo(ugc, modules))
        }
        return when (additional.optString("type")) {
            "ADDITIONAL_TYPE_UGC" -> {
                val ugc = additional.optJSONObject("ugc") ?: return null
                DynamicBody(video = parseAdditionalUgcVideo(ugc, modules))
            }
            "ADDITIONAL_TYPE_COMMON" -> {
                val common = additional.optJSONObject("common") ?: return null
                val desc = listOf(
                    common.optString("desc1"),
                    common.optString("desc2"),
                ).filter { it.isNotBlank() }.joinToString(" · ")
                DynamicBody(
                    link = BiliDynamicLink(
                        title = common.optString("title"),
                        url = normalizeJumpUrl(common.optString("jump_url")),
                        coverUrl = normalizeImageUrl(common.optString("cover")),
                        desc = desc,
                    ),
                )
            }
            else -> null
        }
    }

    private fun parseDynamicDesc(modules: JSONObject): DynamicRichText {
        val moduleDynamic = modules.optJSONObject("module_dynamic")
        parseDynamicRichText(moduleDynamic?.optJSONObject("desc"))
            .takeIf { it.text.isNotBlank() || it.emoticons.isNotEmpty() }
            ?.let { return it }
        parseDynamicRichText(modules.optJSONObject("module_desc")?.optJSONObject("text"))
            .takeIf { it.text.isNotBlank() || it.emoticons.isNotEmpty() }
            ?.let { return it }
        return DynamicRichText()
    }

    private fun parseDynamicRichText(obj: JSONObject?): DynamicRichText {
        if (obj == null) return DynamicRichText()
        val emoticons = linkedMapOf<String, String>()
        emoticons.putAll(obj.parseEmoteMap())

        val nodes = obj.optJSONArray("rich_text_nodes")
        if (nodes != null && nodes.length() > 0) {
            val textBuilder = StringBuilder()
            for (index in 0 until nodes.length()) {
                val node = nodes.optJSONObject(index) ?: continue
                when (node.optString("type")) {
                    "RICH_TEXT_NODE_TYPE_EMOJI" -> {
                        val phrase = node.optString("text")
                            .ifBlank { node.optString("orig_text") }
                            .ifBlank { node.optJSONObject("emoji")?.optString("text").orEmpty() }
                        val url = normalizeImageUrl(
                            node.optJSONObject("emoji")?.optString("icon_url").orEmpty(),
                        )
                        if (phrase.isNotBlank() && url.isNotBlank()) {
                            emoticons[phrase] = url
                        }
                        if (phrase.isNotBlank()) {
                            textBuilder.append(phrase)
                        }
                    }
                    else -> {
                        textBuilder.append(
                            node.optString("text").ifBlank { node.optString("orig_text") },
                        )
                    }
                }
            }
            return DynamicRichText(textBuilder.toString().trim(), emoticons)
        }

        val plainText = obj.optString("text").trim()
        return DynamicRichText(plainText, emoticons)
    }

    private fun parseRichTextImages(obj: JSONObject?): List<String> {
        val nodes = obj?.optJSONArray("rich_text_nodes") ?: return emptyList()
        return buildList {
            for (index in 0 until nodes.length()) {
                val node = nodes.optJSONObject(index) ?: continue
                when (node.optString("type")) {
                    "RICH_TEXT_NODE_TYPE_IMAGE" -> {
                        normalizeImageUrl(
                            node.optString("url").ifBlank { node.optString("src") },
                        ).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
        }
    }

    private fun mergeDynamicText(vararg parts: String): String =
        parts.filter { it.isNotBlank() }.joinToString("\n").trim()

    private fun isDuplicateDynamicText(primary: String, secondary: String): Boolean {
        val a = primary.trim()
        val b = secondary.trim()
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        val compactA = a.replace(Regex("\\s+"), "")
        val compactB = b.replace(Regex("\\s+"), "")
        if (compactA == compactB) return true
        return compactA.contains(compactB) || compactB.contains(compactA)
    }

    private fun shouldShowDynamicLink(
        text: String,
        link: BiliDynamicLink,
        imageUrls: List<String>,
    ): Boolean {
        val linkText = listOf(link.title, link.desc).filter { it.isNotBlank() }.joinToString("\n")
        if (linkText.isBlank() && link.coverUrl.isBlank()) return false
        if (imageUrls.isNotEmpty()) {
            if (text.isNotBlank()) {
                if (link.desc.isNotBlank() && isDuplicateDynamicText(text, link.desc)) return false
                if (linkText.isNotBlank() && isDuplicateDynamicText(text, linkText)) return false
            }
            if (link.coverUrl.isNotBlank() && link.title.isBlank()) return false
        }
        if (text.isBlank()) return true
        if (linkText.isBlank()) return link.coverUrl.isNotBlank() && imageUrls.isEmpty()
        return !isDuplicateDynamicText(text, linkText)
    }

    private fun parseModuleAuthorName(modules: JSONObject): String {
        val author = modules.optJSONObject("module_author") ?: return ""
        return author.optString("name").ifBlank { author.optString("uname") }
    }

    private fun parseDynamicLink(major: JSONObject, majorType: String): BiliDynamicLink? {
        return when (majorType) {
            "MAJOR_TYPE_ARTICLE" -> {
                val article = major.optJSONObject("article") ?: return null
                val cvId = article.optLong("id")
                val url = normalizeJumpUrl(article.optString("jump_url")).ifBlank {
                    if (cvId > 0L) "https://www.bilibili.com/read/cv$cvId" else ""
                }
                if (url.isBlank()) return null
                BiliDynamicLink(
                    title = article.optString("title"),
                    url = url,
                    coverUrl = parseArticleCover(article),
                    desc = article.optString("desc"),
                    cvId = cvId,
                )
            }
            "MAJOR_TYPE_COMMON" -> {
                val common = major.optJSONObject("common") ?: return null
                val url = normalizeJumpUrl(common.optString("jump_url"))
                if (url.isBlank()) return null
                BiliDynamicLink(
                    title = common.optString("title"),
                    url = url,
                    coverUrl = normalizeImageUrl(common.optString("cover")),
                    desc = common.optString("desc"),
                )
            }
            "MAJOR_TYPE_OPUS" -> {
                val opus = major.optJSONObject("opus") ?: return null
                val url = normalizeJumpUrl(opus.optString("jump_url"))
                if (url.isBlank()) return null
                BiliDynamicLink(
                    title = opus.optString("title"),
                    url = url,
                    coverUrl = parseDynamicOpusImages(major).firstOrNull().orEmpty(),
                    desc = opus.optJSONObject("summary")?.optString("text").orEmpty(),
                )
            }
            else -> null
        }
    }

    private fun parseArticleCover(article: JSONObject): String {
        val covers = article.optJSONArray("covers") ?: return ""
        if (covers.length() <= 0) return ""
        val first = covers.opt(0)
        return when (first) {
            is String -> normalizeImageUrl(first)
            is JSONObject -> normalizeImageUrl(first.optString("url").ifBlank { first.optString("src") })
            else -> ""
        }
    }

    private fun normalizeJumpUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        else -> url
    }

    private fun parseDynamicStatCount(stat: JSONObject?, key: String): Long {
        if (stat == null) return 0L
        val nested = stat.optJSONObject(key)
        return when {
            nested != null -> nested.optLong("count").takeIf { it > 0L }
                ?: nested.optString("count").filter { it.isDigit() }.toLongOrNull()
                ?: 0L
            else -> stat.optLong(key).takeIf { it > 0L }
                ?: stat.optString(key).filter { it.isDigit() }.toLongOrNull()
                ?: 0L
        }
    }

    private fun parseDynamicArchiveVideo(
        major: JSONObject,
        modules: JSONObject,
    ): BiliVideoItem? {
        val archive = major.optJSONObject("archive") ?: return null
        var bvid = archive.optString("bvid")
        if (bvid.isBlank()) {
            bvid = extractBvidFromUrl(normalizeJumpUrl(archive.optString("jump_url")))
        }
        val aid = archive.optLong("aid").takeIf { it > 0L }
            ?: archive.optString("aid").toLongOrNull()
            ?: 0L
        if (bvid.isBlank() && aid <= 0L) return null
        val author = modules.optJSONObject("module_author")
        val stat = archive.optJSONObject("stat") ?: JSONObject()
        return BiliVideoItem(
            bvid = bvid.ifBlank { "av$aid" },
            aid = aid,
            title = archive.optString("title"),
            coverUrl = normalizeImageUrl(archive.optString("cover")),
            authorName = author?.optString("name").orEmpty(),
            authorMid = author?.optLong("mid") ?: 0L,
            authorFace = normalizeImageUrl(author?.optString("face").orEmpty()),
            viewCount = parseCount(stat.optString("play")),
            danmakuCount = parseCount(stat.optString("danmaku")),
            likeCount = 0L,
            durationSeconds = parseDurationText(archive.optString("duration_text")),
            description = archive.optString("desc"),
        )
    }

    private fun parseDynamicArchiveLink(major: JSONObject): BiliDynamicLink? {
        val archive = major.optJSONObject("archive") ?: return null
        val url = normalizeJumpUrl(archive.optString("jump_url")).ifBlank {
            extractBvidFromUrl(archive.optString("bvid")).takeIf { it.isNotBlank() }
                ?.let { bvid -> "https://www.bilibili.com/video/$bvid" }
                .orEmpty()
        }
        if (url.isBlank()) return null
        return BiliDynamicLink(
            title = archive.optString("title"),
            url = url,
            coverUrl = normalizeImageUrl(archive.optString("cover")),
            desc = archive.optString("desc"),
        )
    }

    private fun parseAdditionalUgcVideo(
        ugc: JSONObject,
        modules: JSONObject,
    ): BiliVideoItem? {
        val jumpUrl = normalizeJumpUrl(
            ugc.optString("jump_url")
                .ifBlank { ugc.optString("uri") },
        )
        var bvid = extractBvidFromUrl(jumpUrl)
        val aid = ugc.optString("id_str").toLongOrNull()
            ?: Regex("""av(\d+)""", RegexOption.IGNORE_CASE).find(jumpUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: 0L
        if (bvid.isBlank() && aid <= 0L) return null
        val author = modules.optJSONObject("module_author")
        val descSecond = ugc.optString("desc_second")
            .ifBlank { ugc.optString("desc_text_2") }
        return BiliVideoItem(
            bvid = bvid.ifBlank { "av$aid" },
            aid = aid,
            title = ugc.optString("title"),
            coverUrl = normalizeImageUrl(ugc.optString("cover")),
            authorName = author?.optString("name").orEmpty(),
            authorMid = author?.optLong("mid") ?: 0L,
            authorFace = normalizeImageUrl(author?.optString("face").orEmpty()),
            viewCount = parseCount(descSecond),
            danmakuCount = 0L,
            likeCount = 0L,
            durationSeconds = parseDurationText(
                ugc.optString("duration").ifBlank { ugc.optString("duration_text") },
            ),
            description = descSecond,
        )
    }

    private fun parseDynamicLiveLink(live: JSONObject?): BiliDynamicLink? {
        if (live == null) return null
        val url = normalizeJumpUrl(live.optString("jump_url"))
        if (url.isBlank()) return null
        return BiliDynamicLink(
            title = live.optString("title"),
            url = url,
            coverUrl = normalizeImageUrl(live.optString("cover")),
            desc = listOf(
                live.optString("desc_first"),
                live.optString("desc_second"),
            ).filter { it.isNotBlank() }.joinToString(" · "),
        )
    }

    private fun parseDynamicMusicLink(music: JSONObject?): BiliDynamicLink? {
        if (music == null) return null
        val url = normalizeJumpUrl(music.optString("jump_url"))
        if (url.isBlank()) return null
        return BiliDynamicLink(
            title = music.optString("title"),
            url = url,
            coverUrl = normalizeImageUrl(music.optString("cover")),
            desc = music.optString("label"),
        )
    }

    private fun extractBvidFromUrl(url: String): String {
        if (url.isBlank()) return ""
        return Regex("""BV[\w]+""", RegexOption.IGNORE_CASE).find(url)?.value.orEmpty()
    }

    private fun parseDynamicDrawImages(major: JSONObject): List<String> {
        val draw = major.optJSONObject("draw") ?: return emptyList()
        val items = draw.optJSONArray("items") ?: return emptyList()
        return buildList(items.length()) {
            for (index in 0 until items.length()) {
                when (val entry = items.opt(index)) {
                    is JSONObject -> {
                        normalizeImageUrl(
                            entry.optString("src").ifBlank { entry.optString("url") },
                        ).takeIf { it.isNotBlank() }?.let(::add)
                    }
                    is String -> normalizeImageUrl(entry).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }

    private fun parseDynamicOpusImages(major: JSONObject): List<String> {
        val opus = major.optJSONObject("opus") ?: return emptyList()
        val pics = opus.optJSONArray("pics") ?: return emptyList()
        return buildList(pics.length()) {
            for (index in 0 until pics.length()) {
                when (val entry = pics.opt(index)) {
                    is JSONObject -> {
                        normalizeImageUrl(
                            entry.optString("url").ifBlank { entry.optString("src") },
                        ).takeIf { it.isNotBlank() }?.let(::add)
                    }
                    is String -> normalizeImageUrl(entry).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }

    /** 优先小分区（area_v2_name / area_name），无则回退大分区。 */
    private fun JSONObject.liveAreaDisplayName(): String {
        val subName = optString("area_v2_name").ifBlank { optString("area_name") }
        if (subName.isNotBlank()) return subName
        return optString("area_v2_parent_name").ifBlank { optString("parent_area_name") }
    }

    private fun JSONObject.livePortraitHint(): Boolean? {
        fun fieldBoolean(name: String): Boolean? {
            if (!has(name)) return null
            val raw = opt(name)
            return when (raw) {
                is Boolean -> raw
                is Number -> raw.toInt() == 1
                is String -> raw == "1" || raw.equals("true", ignoreCase = true)
                else -> null
            }
        }
        return fieldBoolean("is_portrait")
            ?: fieldBoolean("is_vertical")
            ?: fieldBoolean("vertical")
            ?: fieldBoolean("live_screen_type")?.takeIf { it }
            ?: fieldBoolean("screen_type")?.takeIf { it }
    }

    fun parseLiveRoomAreaDisplayName(json: JSONObject): String {
        val data = json.optJSONObject("data") ?: return ""
        val roomInfo = data.optJSONObject("room_info") ?: data
        return roomInfo.liveAreaDisplayName()
    }

    fun parseLiveRoomInfoBrief(json: JSONObject): Triple<String, Long, Boolean?> {
        val data = json.optJSONObject("data") ?: return Triple("", 0L, null)
        val roomInfo = data.optJSONObject("room_info")
        val cover = normalizeImageUrl(
            data.optString("user_cover")
                .ifBlank { data.optString("keyframe") }
                .ifBlank { data.optString("cover") }
                .ifBlank { roomInfo?.optString("user_cover").orEmpty() }
                .ifBlank { roomInfo?.optString("keyframe").orEmpty() }
                .ifBlank { roomInfo?.optString("cover").orEmpty() },
        )
        return Triple(
            cover,
            data.optLong("online").takeIf { it > 0L } ?: roomInfo?.optLong("online") ?: 0L,
            data.livePortraitHint() ?: roomInfo?.livePortraitHint(),
        )
    }

    fun parseLiveRoomDetail(json: JSONObject): BiliLiveRoomDetail? {
        val data = json.optJSONObject("data") ?: return null
        val roomInfo = data.optJSONObject("room_info") ?: return null
        val anchorInfo = data.optJSONObject("anchor_info")?.optJSONObject("base_info")
        val roomId = roomInfo.optLong("room_id").takeIf { it > 0L } ?: return null
        return BiliLiveRoomDetail(
            roomId = roomId,
            title = roomInfo.optString("title"),
            coverUrl = normalizeImageUrl(
                roomInfo.optString("cover")
                    .ifBlank { roomInfo.optString("keyframe") },
            ),
            userName = anchorInfo?.optString("uname")
                ?: roomInfo.optString("uname"),
            userFace = normalizeImageUrl(
                anchorInfo?.optString("face")
                    ?: roomInfo.optString("face"),
            ),
            anchorUid = roomInfo.optLong("uid"),
            online = roomInfo.optLong("online"),
            areaName = roomInfo.optString("area_name"),
            parentAreaName = roomInfo.optString("parent_area_name"),
            liveStatus = roomInfo.optInt("live_status"),
            description = roomInfo.optString("description"),
            isPortrait = roomInfo.livePortraitHint() ?: data.livePortraitHint(),
        )
    }

    fun parseLivePlayInfo(
        json: JSONObject,
        qn: Int,
    ): BiliLivePlayResult? {
        val data = json.optJSONObject("data") ?: return null
        val roomId = data.optLong("room_id").takeIf { it > 0L } ?: return null
        val playUrlInfo = data.optJSONObject("playurl_info") ?: return null
        val playUrl = playUrlInfo.optJSONObject("playurl") ?: return null
        val qualities = buildList {
            playUrl.optJSONArray("g_qn_desc")?.let { qnDesc ->
                for (index in 0 until qnDesc.length()) {
                    val item = qnDesc.optJSONObject(index) ?: continue
                    val qualityQn = item.optInt("qn")
                    val desc = item.optString("desc")
                    if (qualityQn > 0 && desc.isNotBlank()) {
                        add(BiliLiveQuality(qn = qualityQn, label = desc))
                    }
                }
            }
        }
        val streams = playUrl.optJSONArray("stream") ?: return null
        data class LiveStreamCandidate(
            val qnMatchPriority: Int,
            val codecPriority: Int,
            val protocolPriority: Int,
            val formatPriority: Int,
            val url: String,
        )

        val candidates = buildList {
            for (streamIndex in 0 until streams.length()) {
                val stream = streams.optJSONObject(streamIndex) ?: continue
                val protocolName = stream.optString("protocol_name")
                val protocolPriority = when (protocolName) {
                    "http_stream" -> 0
                    "http_hls" -> 1
                    else -> 2
                }
                val formats = stream.optJSONArray("format") ?: continue
                for (formatIndex in 0 until formats.length()) {
                    val format = formats.optJSONObject(formatIndex) ?: continue
                    val formatName = format.optString("format_name")
                    val formatPriority = when (formatName) {
                        "flv" -> 0
                        "ts" -> 1
                        "fmp4" -> 2
                        else -> 3
                    }
                    val codecs = format.optJSONArray("codec") ?: continue
                    for (codecIndex in 0 until codecs.length()) {
                        val codec = codecs.optJSONObject(codecIndex) ?: continue
                        val acceptQn = buildList {
                            codec.optJSONArray("accept_qn")?.let { qnArray ->
                                for (qnIndex in 0 until qnArray.length()) {
                                    add(qnArray.optInt(qnIndex))
                                }
                            }
                        }
                        if (acceptQn.isEmpty()) continue
                        val codecName = codec.optString("codec_name")
                        val codecPriority = when {
                            codecName.equals("avc", ignoreCase = true) -> 0
                            codecName.equals("hevc", ignoreCase = true) -> 1
                            else -> continue
                        }
                        val qnMatchPriority = if (acceptQn.contains(qn)) {
                            0
                        } else {
                            1 + acceptQn.minOf { kotlin.math.abs(it - qn) }
                        }
                        val baseUrl = codec.optString("base_url")
                        val urlInfo = codec.optJSONArray("url_info") ?: continue
                        for (urlIndex in 0 until urlInfo.length()) {
                            val info = urlInfo.optJSONObject(urlIndex) ?: continue
                            val host = info.optString("host").trimEnd('/')
                            val extra = info.optString("extra")
                            if (host.isBlank() || baseUrl.isBlank()) continue
                            val url = buildString {
                                append(host)
                                if (!baseUrl.startsWith("/")) append('/')
                                append(baseUrl)
                                if (extra.isNotBlank()) {
                                    if (baseUrl.contains('?')) append('&') else append('?')
                                    append(extra)
                                }
                            }
                            add(
                                LiveStreamCandidate(
                                    qnMatchPriority = qnMatchPriority,
                                    codecPriority = codecPriority,
                                    protocolPriority = protocolPriority,
                                    formatPriority = formatPriority,
                                    url = url,
                                ),
                            )
                        }
                    }
                }
            }
        }
        val best = candidates.minWithOrNull(
            compareBy<LiveStreamCandidate> { it.codecPriority }
                .thenBy { it.qnMatchPriority }
                .thenBy { it.protocolPriority }
                .thenBy { it.formatPriority },
        ) ?: return null
        val deliveredQn = playUrlInfo.optJSONObject("expected_quality")?.optInt("qn")
            ?.takeIf { it > 0 } ?: qn
        return BiliLivePlayResult(
            roomId = roomId,
            realRoomId = roomId,
            anchorUid = data.optLong("uid"),
            liveStatus = data.optInt("live_status"),
            streamUrl = best.url,
            currentQn = deliveredQn,
            qualities = qualities,
        )
    }

    fun parseLiveDanmuInfo(json: JSONObject): BiliLiveDanmuInfo? {
        val data = json.optJSONObject("data") ?: return null
        val token = data.optString("token")
        val hostList = data.optJSONArray("host_list") ?: return null
        val hosts = buildList {
            for (index in 0 until hostList.length()) {
                val item = hostList.optJSONObject(index) ?: continue
                val host = item.optString("host")
                if (host.isBlank()) continue
                add(
                    BiliLiveDanmuHost(
                        host = host,
                        wssPort = item.optInt("wss_port", 443),
                        wsPort = item.optInt("ws_port", 2244),
                    ),
                )
            }
        }
        if (token.isBlank() || hosts.isEmpty()) return null
        return BiliLiveDanmuInfo(token = token, hosts = hosts)
    }

    fun parseLiveDanmakuMessage(payload: org.json.JSONArray): BiliDanmakuItem? {
        if (payload.length() < 2) return null
        val meta = payload.optJSONArray(0) ?: return null
        val content = payload.optString(1)
        val emoticons = parseLiveDanmakuEmoticons(payload, meta, content)
        if (content.isBlank() && emoticons.isEmpty()) return null
        val displayContent = content.ifBlank { emoticons.keys.firstOrNull().orEmpty() }
        if (displayContent.isBlank()) return null
        val userInfo = payload.optJSONArray(2)
        val mode = meta.optInt(1, BiliDanmakuMode.Scroll.value)
        val fontSize = meta.optInt(2, 25)
        val color = meta.optInt(3, 0xFFFFFF)
        return BiliDanmakuItem(
            timeMs = System.currentTimeMillis(),
            mode = mode,
            fontSize = fontSize,
            colorArgb = color or 0xFF000000.toInt(),
            content = displayContent,
            senderId = userInfo?.optLong(0) ?: 0L,
            senderName = userInfo?.optString(1).orEmpty(),
            emoticons = emoticons,
        )
    }

    private fun parseLiveDanmakuEmoticons(
        payload: org.json.JSONArray,
        meta: org.json.JSONArray,
        content: String,
    ): Map<String, BiliDanmakuEmoticon> {
        val result = linkedMapOf<String, BiliDanmakuEmoticon>()

        fun metaJsonAt(index: Int): JSONObject? {
            if (index >= meta.length()) return null
            return when (val raw = meta.opt(index)) {
                is JSONObject -> raw.takeIf { it.length() > 0 }
                is String -> if (raw.isBlank() || raw == "{}") {
                    null
                } else {
                    runCatching { JSONObject(raw) }.getOrNull()?.takeIf { it.length() > 0 }
                }
                else -> null
            }
        }

        fun jsonObjectFrom(raw: Any?): JSONObject? =
            when (raw) {
                is JSONObject -> raw.takeIf { it.length() > 0 }
                is String -> if (raw.isBlank() || raw == "{}") {
                    null
                } else {
                    runCatching { JSONObject(raw) }.getOrNull()?.takeIf { it.length() > 0 }
                }
                else -> null
            }

        fun putEmoticon(phrase: String, url: String, width: Int = 0, height: Int = 0) {
            if (phrase.isBlank() || url.isBlank()) return
            result[phrase] = BiliDanmakuEmoticon(
                url = normalizeSpaceImageUrl(url),
                width = width,
                height = height,
            )
        }

        fun JSONObject.emoticonUrl(): String =
            optString("url")
                .ifBlank { optString("gif_url") }
                .ifBlank { optString("icon_url") }
                .ifBlank { optString("emoticon_url") }
                .ifBlank { optString("webp_url") }
                .ifBlank { optString("bulge_url") }
                .ifBlank { optString("jump_url") }
                .ifBlank { optString("cover") }

        fun phraseCandidates(vararg rawPhrases: String): List<String> =
            rawPhrases
                .flatMap { raw ->
                    val phrase = raw.trim()
                    if (phrase.isBlank()) {
                        emptyList()
                    } else {
                        listOf(
                            phrase,
                            phrase.removeSurrounding("[", "]"),
                            if (phrase.startsWith("[") && phrase.endsWith("]")) phrase else "[$phrase]",
                        )
                    }
                }
                .filter { it.isNotBlank() }
                .distinct()

        fun putEmoticonForPhrases(
            phrases: List<String>,
            url: String,
            width: Int = 0,
            height: Int = 0,
        ) {
            phrases.forEach { phrase ->
                putEmoticon(phrase, url, width, height)
            }
        }

        fun addEmotsObject(emots: JSONObject?) {
            if (emots == null) return
            val keys = emots.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val raw = emots.opt(key)) {
                    is JSONObject -> {
                        val url = raw.emoticonUrl()
                        if (url.isNotBlank()) {
                            putEmoticonForPhrases(
                                phrases = phraseCandidates(
                                    key,
                                    raw.optString("text"),
                                    raw.optString("emoji"),
                                    raw.optString("name"),
                                    raw.optString("emoticon_unique"),
                                ),
                                url = url,
                                width = raw.optInt("width"),
                                height = raw.optInt("height"),
                            )
                        }
                    }
                    is String -> {
                        if (raw.startsWith("http") || raw.startsWith("//")) {
                            putEmoticonForPhrases(phraseCandidates(key), raw)
                        }
                    }
                }
            }
        }

        fun addEmoticonObject(emoticon: JSONObject?, phrase: String = content) {
            if (emoticon == null || emoticon.length() == 0) return
            val url = emoticon.emoticonUrl()
            if (url.isBlank()) return
            val unique = when (val raw = emoticon.opt("emoticon_unique")) {
                is String -> raw
                is JSONObject -> raw.optString("text")
                else -> ""
            }
            val text = emoticon.optString("text")
                .ifBlank { phrase }
                .ifBlank { unique }
            putEmoticonForPhrases(
                phrases = phraseCandidates(
                    text,
                    phrase,
                    unique,
                    emoticon.optString("emoji"),
                    emoticon.optString("name"),
                ),
                url = url,
                width = emoticon.optInt("width"),
                height = emoticon.optInt("height"),
            )
        }

        fun addEmoticonArray(array: org.json.JSONArray?) {
            if (array == null) return
            for (index in 0 until array.length()) {
                addEmoticonObject(array.optJSONObject(index), content)
            }
        }

        fun scanExtraObject(obj: JSONObject?) {
            if (obj == null || obj.length() == 0) return
            addEmotsObject(obj.optJSONObject("emots"))
            addEmotsObject(obj.optJSONObject("emoticons"))
            addEmotsObject(obj.optJSONObject("emote"))
            addEmoticonObject(obj.optJSONObject("emoticon"), obj.optString("content").ifBlank { content })
            addEmoticonObject(obj.optJSONObject("emoji"), obj.optString("content").ifBlank { content })
            addEmoticonArray(obj.optJSONArray("emoticon_list"))
            addEmoticonArray(obj.optJSONArray("emotes"))
            addEmoticonArray(obj.optJSONArray("emojis"))
            addEmoticonObject(obj, obj.optString("content").ifBlank { content })
        }

        metaJsonAt(12)?.let(::addEmotsObject)
        metaJsonAt(13)?.let { addEmoticonObject(it, content) }
        metaJsonAt(14)?.let(::addEmotsObject)
        metaJsonAt(15)?.let { data ->
            scanExtraObject(data)
            val extra = data.optString("extra")
            if (extra.isNotBlank()) {
                runCatching { JSONObject(extra) }.getOrNull()?.let { extraJson ->
                    scanExtraObject(extraJson)
                    val extraContent = extraJson.optString("content").ifBlank { content }
                    if (extraJson.optString("emoticon_unique").isNotBlank()) {
                        metaJsonAt(13)?.let { emoticonMeta ->
                            val url = emoticonMeta.emoticonUrl()
                            if (url.isNotBlank() && extraContent.isNotBlank()) {
                                putEmoticonForPhrases(
                                    phrases = phraseCandidates(extraContent),
                                    url = url,
                                    width = emoticonMeta.optInt("width"),
                                    height = emoticonMeta.optInt("height"),
                                )
                            }
                        }
                    }
                }
            }
        }
        for (index in 0 until meta.length()) {
            scanExtraObject(metaJsonAt(index))
        }
        for (index in 0 until payload.length()) {
            when (val raw = payload.opt(index)) {
                is JSONObject -> scanExtraObject(raw)
                is org.json.JSONArray -> {
                    for (childIndex in 0 until raw.length()) {
                        jsonObjectFrom(raw.opt(childIndex))?.let(::scanExtraObject)
                    }
                }
                is String -> jsonObjectFrom(raw)?.let(::scanExtraObject)
            }
        }
        return result
    }

    fun parseLiveEmoticonMap(json: JSONObject): Map<String, BiliDanmakuEmoticon> {
        val data = json.optJSONObject("data") ?: json
        val result = linkedMapOf<String, BiliDanmakuEmoticon>()

        fun phraseCandidates(vararg rawPhrases: String): List<String> =
            rawPhrases
                .flatMap { raw ->
                    val phrase = raw.trim()
                    if (phrase.isBlank()) {
                        emptyList()
                    } else {
                        listOf(
                            phrase,
                            phrase.removeSurrounding("[", "]"),
                            if (phrase.startsWith("[") && phrase.endsWith("]")) phrase else "[$phrase]",
                        )
                    }
                }
                .filter { it.isNotBlank() }
                .distinct()

        fun JSONObject.emoticonUrl(): String =
            optString("url")
                .ifBlank { optString("gif_url") }
                .ifBlank { optString("icon_url") }
                .ifBlank { optString("emoticon_url") }
                .ifBlank { optString("webp_url") }
                .ifBlank { optString("bulge_url") }
                .ifBlank { optString("cover") }

        fun putEmoticon(phrase: String, spec: BiliDanmakuEmoticon) {
            if (phrase.isBlank() || spec.url.isBlank()) return
            phraseCandidates(phrase).forEach { candidate ->
                result[candidate] = spec
            }
        }

        lateinit var scanEmoticonObject: (JSONObject?, String) -> Unit
        lateinit var scanEmoticonArray: (org.json.JSONArray?) -> Unit
        lateinit var scanEmoticonMap: (JSONObject?) -> Unit

        scanEmoticonObject = scanObject@{ obj, fallbackPhrase ->
            if (obj == null || obj.length() == 0) return@scanObject
            val url = obj.emoticonUrl()
            if (url.isNotBlank()) {
                val unique = when (val raw = obj.opt("emoticon_unique")) {
                    is String -> raw
                    is JSONObject -> raw.optString("text")
                    else -> ""
                }
                val spec = BiliDanmakuEmoticon(
                    url = normalizeSpaceImageUrl(url),
                    width = obj.optInt("width")
                        .takeIf { it > 0 } ?: obj.optInt("emoticon_width"),
                    height = obj.optInt("height")
                        .takeIf { it > 0 } ?: obj.optInt("emoticon_height"),
                )
                phraseCandidates(
                    fallbackPhrase,
                    obj.optString("text"),
                    obj.optString("emoji"),
                    obj.optString("name"),
                    obj.optString("descript"),
                    obj.optString("emoticon_id"),
                    unique,
                ).forEach { putEmoticon(it, spec) }
            }

            scanEmoticonArray(obj.optJSONArray("emoticons"))
            scanEmoticonArray(obj.optJSONArray("emoticon_list"))
            scanEmoticonArray(obj.optJSONArray("emotes"))
            scanEmoticonArray(obj.optJSONArray("emoji_list"))
            scanEmoticonMap(obj.optJSONObject("emoticons"))
            scanEmoticonMap(obj.optJSONObject("emote"))
        }

        scanEmoticonArray = scanArray@{ array ->
            if (array == null) return@scanArray
            for (index in 0 until array.length()) {
                when (val raw = array.opt(index)) {
                    is JSONObject -> scanEmoticonObject(raw, "")
                    is org.json.JSONArray -> scanEmoticonArray(raw)
                }
            }
        }

        scanEmoticonMap = scanMap@{ obj ->
            if (obj == null) return@scanMap
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val raw = obj.opt(key)) {
                    is JSONObject -> scanEmoticonObject(raw, key)
                    is String -> {
                        if (raw.startsWith("http") || raw.startsWith("//") || raw.startsWith("bfs/")) {
                            putEmoticon(
                                key,
                                BiliDanmakuEmoticon(url = normalizeSpaceImageUrl(raw)),
                            )
                        }
                    }
                    is org.json.JSONArray -> scanEmoticonArray(raw)
                }
            }
        }

        scanEmoticonObject(data, "")
        scanEmoticonArray(data.optJSONArray("data"))
        scanEmoticonArray(data.optJSONArray("list"))
        scanEmoticonArray(data.optJSONArray("packages"))
        scanEmoticonArray(data.optJSONArray("emoticons"))
        scanEmoticonMap(data.optJSONObject("emoticons"))
        return result
    }

    fun parseLiveOnlineGoldRank(json: JSONObject): BiliLiveOnlineGoldRank {
        val data = json.optJSONObject("data") ?: return BiliLiveOnlineGoldRank()
        val onlineNum = data.optLong("onlineNum").takeIf { it > 0L }
            ?: data.optLong("online_num")
        val users = parseLiveOnlineRankUsers(
            data.optJSONArray("OnlineRankItem") ?: data.optJSONArray("list"),
        )
        return BiliLiveOnlineGoldRank(
            onlineNum = onlineNum,
            users = users,
        )
    }

    fun parseLiveOnlineRankList(json: JSONObject): List<BiliLiveRankUser> {
        val data = json.optJSONObject("data") ?: json
        return parseLiveOnlineRankUsers(
            data.optJSONArray("list") ?: data.optJSONArray("OnlineRankItem"),
        )
    }

    private fun parseLiveOnlineRankUsers(list: org.json.JSONArray?): List<BiliLiveRankUser> {
        if (list == null) return emptyList()
        return buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val uid = item.optLong("uid")
                val rank = item.optInt("userRank").takeIf { it > 0 }
                    ?: item.optInt("rank", index + 1)
                val face = normalizeImageUrl(
                    item.optString("face")
                        .ifBlank {
                            item.optJSONObject("uinfo")
                                ?.optJSONObject("base")
                                ?.optString("face")
                                .orEmpty()
                        },
                )
                val uname = item.optString("uname")
                    .ifBlank { item.optString("name") }
                    .ifBlank {
                        item.optJSONObject("uinfo")
                            ?.optJSONObject("base")
                            ?.optString("name")
                            .orEmpty()
                    }
                if (uid <= 0L || face.isBlank() || uname.isBlank()) continue
                add(
                    BiliLiveRankUser(
                        uid = uid,
                        face = face,
                        uname = uname,
                        rank = rank,
                        guardLevel = item.optInt("guard_level"),
                    ),
                )
            }
        }.sortedBy { it.rank }.take(3)
    }

    fun parseLiveRoomPage(json: JSONObject): BiliLiveRoomPage {
        val data = json.optJSONObject("data")
        val list = data?.optJSONArray("list") ?: org.json.JSONArray()
        val page = data?.optInt("page", 1) ?: 1
        val hasMore = data?.optInt("has_more", 0) == 1 ||
            data?.optBoolean("has_more", false) == true
        return BiliLiveRoomPage(
            rooms = parseLiveRoomItems(list),
            page = page,
            hasMore = hasMore,
        )
    }

    fun parseLiveAreaGroups(json: JSONObject): List<BiliLiveAreaGroup> {
        val data = json.optJSONArray("data") ?: return emptyList()
        return buildList(data.length()) {
            for (index in 0 until data.length()) {
                val parentJson = data.optJSONObject(index) ?: continue
                val parentId = parentJson.optLong("id")
                val parentName = parentJson.optString("name")
                if (parentId <= 0L || parentName.isBlank()) continue
                val children = buildList {
                    parentJson.optJSONArray("list")?.let { list ->
                        for (childIndex in 0 until list.length()) {
                            val child = list.optJSONObject(childIndex) ?: continue
                            val childId = child.optLong("id")
                            val childName = child.optString("name")
                            if (childId > 0L && childName.isNotBlank()) {
                                add(
                                    BiliLiveArea(
                                        id = childId,
                                        name = childName,
                                        parentId = parentId,
                                    ),
                                )
                            }
                        }
                    }
                }
                add(
                    BiliLiveAreaGroup(
                        parent = BiliLiveArea(id = parentId, name = parentName),
                        children = children,
                    ),
                )
            }
        }
    }

    fun parseLiveRecommendList(json: JSONObject): BiliLiveRoomPage {
        val list = json.optJSONObject("data")?.optJSONArray("recommend_room_list")
            ?: org.json.JSONArray()
        val rooms = buildList(list.length()) {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val roomId = item.optLong("roomid").takeIf { it > 0L } ?: continue
                add(
                    BiliLiveRoom(
                        roomId = roomId,
                        title = item.optString("title"),
                        coverUrl = normalizeImageUrl(item.optString("cover")),
                        userName = item.optString("uname"),
                        userFace = normalizeImageUrl(item.optString("face")),
                        online = item.optLong("online"),
                        areaName = item.optString("area_v2_parent_name")
                            .ifBlank { item.optString("area_v2_name") },
                        isPortrait = item.livePortraitHint(),
                    ),
                )
            }
        }
        return BiliLiveRoomPage(
            rooms = rooms,
            page = 1,
            hasMore = false,
        )
    }

    fun parseLiveFollowing(json: JSONObject): List<BiliLiveRoom> {
        val data = json.optJSONObject("data") ?: return emptyList()
        val rooms = data.optJSONArray("rooms")
            ?: data.optJSONArray("list")
            ?: return emptyList()
        return parseLiveRoomItems(rooms)
    }

    private fun parseLiveRoomItems(list: org.json.JSONArray): List<BiliLiveRoom> {
        return buildList(list.length()) {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val roomId = item.optLong("roomid").takeIf { it > 0L }
                    ?: item.optLong("room_id").takeIf { it > 0L }
                    ?: continue
                val roomInfo = item.optJSONObject("room_info")
                val areaSource = roomInfo ?: item
                add(
                    BiliLiveRoom(
                        roomId = roomId,
                        title = item.optString("title")
                            .ifBlank { roomInfo?.optString("title").orEmpty() },
                        coverUrl = normalizeImageUrl(
                            item.optString("cover")
                                .ifBlank { item.optString("user_cover") }
                                .ifBlank { item.optString("cover_from_user") },
                        ),
                        userName = item.optString("uname")
                            .ifBlank { item.optString("username") },
                        userFace = normalizeImageUrl(item.optString("face")),
                        online = item.optLong("online"),
                        areaName = areaSource.liveAreaDisplayName()
                            .ifBlank { item.liveAreaDisplayName() },
                        isPortrait = item.livePortraitHint() ?: roomInfo?.livePortraitHint(),
                    ),
                )
            }
        }
    }

    fun parseLiveAreaList(json: JSONObject): List<BiliLiveRoom> =
        parseLiveRoomPage(json).rooms

    fun parseWatchHistoryPage(json: JSONObject): BiliHistoryPage {
        val data = json.optJSONObject("data") ?: return BiliHistoryPage(emptyList(), null)
        val cursor = data.optJSONObject("cursor")?.let { cursorJson ->
            BiliHistoryCursor(
                max = cursorJson.optLong("max"),
                viewAt = cursorJson.optLong("view_at"),
                business = cursorJson.optString("business"),
                ps = cursorJson.optInt("ps"),
            )
        }
        val list = data.optJSONArray("list") ?: org.json.JSONArray()
        val items = buildList(list.length()) {
            for (index in 0 until list.length()) {
                parseHistoryItem(list.optJSONObject(index) ?: continue)?.let(::add)
            }
        }
        return BiliHistoryPage(items = items, cursor = cursor)
    }

    private fun parseHistoryItem(item: JSONObject): BiliHistoryItem? {
        val history = item.optJSONObject("history")
        val businessRaw = history?.optString("business").orEmpty()
            .ifBlank { item.optString("business") }
        if (businessRaw != "archive" && businessRaw != "pgc") return null

        val business = BiliHistoryBusiness.from(businessRaw)
        val bvid = item.optString("bvid").ifBlank { history?.optString("bvid").orEmpty() }
        val webUri = item.optString("uri")
            .ifBlank { history?.optString("uri").orEmpty() }
        val kidValue: Long = item.optLong("kid").takeIf { it > 0L }
            ?: history?.optLong("kid")?.takeIf { it > 0L }
            ?: 0L
        val fieldEpid = history?.optLong("epid")?.takeIf { it > 0L }
            ?: history?.optLong("ep_id")?.takeIf { it > 0L }
            ?: item.optLong("epid").takeIf { it > 0L }
            ?: item.optLong("ep_id").takeIf { it > 0L }
        val epid = fieldEpid
            ?: kidValue.takeIf { business == BiliHistoryBusiness.Pgc && it > 0L }
            ?: parsePgcEpidFromUri(webUri)
            ?: 0L
        val historyCid = history?.optLong("cid") ?: 0L
        val aid = item.optLong("aid").takeIf { it > 0L }
            ?: history?.optLong("oid")?.takeIf { it > 0L }
            ?: 0L
        val cid: Long = item.optLong("cid").takeIf { it > 0L } ?: historyCid

        if (business == BiliHistoryBusiness.Archive && bvid.isBlank()) return null
        if (business == BiliHistoryBusiness.Pgc && epid <= 0L && cid <= 0L) return null

        val primaryTitle = item.optString("title").ifBlank { item.optString("show_title") }
        val episodeTitle = item.optString("show_title").ifBlank { item.optString("long_title") }
        val displayTitle = when {
            business == BiliHistoryBusiness.Pgc &&
                episodeTitle.isNotBlank() &&
                episodeTitle != primaryTitle &&
                primaryTitle.isNotBlank() -> "$primaryTitle · $episodeTitle"
            primaryTitle.isNotBlank() -> primaryTitle
            else -> episodeTitle
        }
        if (displayTitle.isBlank()) return null

        val cover = item.optString("cover").ifBlank {
            item.optJSONArray("covers")?.optString(0).orEmpty()
        }
        val author = item.optJSONObject("author")
            ?: item.optJSONObject("owner")
            ?: item.optJSONObject("upper")
        val authorName = item.optString("author_name")
            .ifBlank { item.optString("name") }
            .ifBlank { author?.optString("name").orEmpty() }
            .ifBlank { author?.optString("uname").orEmpty() }
        val authorMid = item.optLong("author_mid").takeIf { it > 0L }
            ?: author?.optLong("mid")?.takeIf { it > 0L }
            ?: 0L
        val authorFace = normalizeImageUrl(
            item.optString("author_face")
                .ifBlank { item.optString("author_icon") }
                .ifBlank { author?.optString("face").orEmpty() }
                .ifBlank { author?.optString("avatar").orEmpty() },
        )
        val badge = item.optString("badge")
        val kid = when {
            kidValue > 0L && business == BiliHistoryBusiness.Archive -> "archive_$kidValue"
            kidValue > 0L && business == BiliHistoryBusiness.Pgc -> "pgc_$kidValue"
            business == BiliHistoryBusiness.Pgc && epid > 0L -> "pgc_$epid"
            aid > 0L -> "archive_$aid"
            else -> ""
        }
        val durationSeconds = item.optInt("duration").takeIf { it > 0 }
            ?: parseDurationText(item.optString("duration_text")).takeIf { it > 0 }
            ?: 0

        return BiliHistoryItem(
            kid = kid,
            business = business,
            bvid = bvid,
            aid = aid,
            cid = cid,
            epid = epid,
            page = history?.optInt("page") ?: 0,
            partTitle = history?.optString("part").orEmpty(),
            title = displayTitle,
            coverUrl = normalizeImageUrl(cover),
            authorName = authorName,
            authorMid = authorMid,
            authorFace = authorFace,
            badge = badge,
            webUri = webUri,
            viewAtSeconds = item.optLong("view_at"),
            progressSeconds = item.optInt("progress").coerceAtLeast(0),
            durationSeconds = durationSeconds,
        )
    }

    private fun parsePgcEpidFromUri(uri: String): Long? {
        if (uri.isBlank()) return null
        val marker = "ep"
        var start = uri.indexOf(marker)
        while (start >= 0) {
            val digitStart = start + marker.length
            if (digitStart < uri.length && uri[digitStart].isDigit()) {
                var digitEnd = digitStart
                while (digitEnd < uri.length && uri[digitEnd].isDigit()) {
                    digitEnd++
                }
                return uri.substring(digitStart, digitEnd).toLongOrNull()?.takeIf { it > 0L }
            }
            start = uri.indexOf(marker, startIndex = start + marker.length)
        }
        return null
    }

    fun parseSearchVideoPage(json: JSONObject): BiliSearchResultPage<BiliVideoItem> {
        val data = json.optJSONObject("data") ?: return BiliSearchResultPage.empty()
        val page = data.optInt("page", 1).coerceAtLeast(1)
        val numPages = data.optInt("numPages", page).coerceAtLeast(page)
        val result = data.optJSONArray("result") ?: return BiliSearchResultPage.empty()
        val items = buildList(result.length()) {
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                val bvid = item.optString("bvid")
                if (bvid.isBlank()) continue
                add(
                    BiliVideoItem(
                        bvid = bvid,
                        aid = item.optLong("aid"),
                        title = stripSearchHighlight(item.optString("title")),
                        coverUrl = normalizeImageUrl(item.optString("pic")),
                        authorName = item.optString("author"),
                        authorMid = item.optLong("mid"),
                        authorFace = normalizeImageUrl(
                            item.optString("upic")
                                .ifBlank { item.optString("author_face") },
                        ),
                        viewCount = item.optLong("play"),
                        danmakuCount = item.optLong("video_review"),
                        likeCount = 0L,
                        durationSeconds = parseDurationText(item.optString("duration")),
                        description = stripSearchHighlight(item.optString("description")),
                    ),
                )
            }
        }
        return BiliSearchResultPage(items = items, page = page, hasMore = page < numPages)
    }

    fun parseSearchUserPage(json: JSONObject): BiliSearchResultPage<BiliSearchUserItem> {
        val data = json.optJSONObject("data") ?: return BiliSearchResultPage.empty()
        val page = data.optInt("page", 1).coerceAtLeast(1)
        val numPages = data.optInt("numPages", page).coerceAtLeast(page)
        val result = data.optJSONArray("result") ?: return BiliSearchResultPage.empty()
        val items = buildList(result.length()) {
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                val mid = item.optLong("mid")
                if (mid <= 0L) continue
                add(
                    BiliSearchUserItem(
                        mid = mid,
                        name = stripSearchHighlight(item.optString("uname")),
                        face = normalizeImageUrl(item.optString("upic")),
                        sign = item.optString("usign"),
                        fans = item.optLong("fans"),
                        level = item.optInt("level"),
                    ),
                )
            }
        }
        return BiliSearchResultPage(items = items, page = page, hasMore = page < numPages)
    }

    fun parseSearchHotWords(json: JSONObject): List<BiliHotSearchItem> {
        val list = json.optJSONArray("list") ?: return emptyList()
        return buildList(list.length()) {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val keyword = item.optString("keyword").ifBlank { item.optString("show_name") }
                if (keyword.isBlank()) continue
                add(
                    BiliHotSearchItem(
                        keyword = keyword,
                        showName = item.optString("show_name").ifBlank { keyword },
                        rank = item.optInt("pos", index + 1),
                    ),
                )
            }
        }
    }

    fun parseSearchSuggest(json: JSONObject): List<String> {
        val tags = json.optJSONObject("result")?.optJSONArray("tag") ?: return emptyList()
        return buildList(tags.length()) {
            for (index in 0 until tags.length()) {
                val item = tags.optJSONObject(index) ?: continue
                val term = item.optString("term").ifBlank { item.optString("value") }
                if (term.isNotBlank()) add(stripSearchHighlight(term))
            }
        }.distinct()
    }

    private fun stripSearchHighlight(raw: String): String =
        raw.replace(Regex("<[^>]+>"), "").trim()

    fun parseFollowingVideoFeed(json: JSONObject): BiliFollowingFeedPage {
        val data = json.optJSONObject("data")
            ?: return BiliFollowingFeedPage(emptyList(), nextOffset = null, hasMore = false)
        val items = data.optJSONArray("items") ?: return BiliFollowingFeedPage(emptyList(), nextOffset = null, hasMore = false)
        val seen = mutableSetOf<String>()
        val videos = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                parseDynamicVideoItem(item)?.let { video ->
                    if (seen.add(video.bvid)) add(video)
                }
            }
        }
        val nextOffset = data.optString("offset").takeIf { it.isNotBlank() }
        val hasMore = data.optBoolean("has_more", nextOffset != null && videos.isNotEmpty())
        return BiliFollowingFeedPage(
            videos = videos,
            nextOffset = nextOffset,
            hasMore = hasMore,
        )
    }

    private fun parseDynamicVideoItem(item: JSONObject): BiliVideoItem? {
        val modules = item.optJSONObject("modules") ?: return null
        val author = modules.optJSONObject("module_author")
        val major = modules.optJSONObject("module_dynamic")?.optJSONObject("major") ?: return null
        if (major.optString("type") != "MAJOR_TYPE_ARCHIVE") return null
        val archive = major.optJSONObject("archive") ?: return null
        val bvid = archive.optString("bvid")
        if (bvid.isBlank()) return null
        val stat = archive.optJSONObject("stat") ?: JSONObject()
        return BiliVideoItem(
            bvid = bvid,
            aid = archive.optString("aid").toLongOrNull() ?: 0L,
            title = archive.optString("title"),
            coverUrl = normalizeImageUrl(archive.optString("cover")),
            authorName = author?.optString("name").orEmpty(),
            authorMid = author?.optLong("mid") ?: 0L,
            authorFace = normalizeImageUrl(author?.optString("face").orEmpty()),
            viewCount = parseCount(stat.optString("play")),
            danmakuCount = parseCount(stat.optString("danmaku")),
            likeCount = 0L,
            durationSeconds = parseDurationText(archive.optString("duration_text")),
            description = archive.optString("desc"),
        )
    }

    private fun parseCount(raw: String): Long = raw.filter { it.isDigit() }.toLongOrNull() ?: 0L

    private fun parseUserVideoDuration(item: JSONObject): Int {
        val lengthText = item.optString("length")
        if (lengthText.isNotBlank()) {
            if (lengthText.contains(":")) {
                return parseDurationText(lengthText)
            }
            lengthText.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        }
        return item.optLong("duration").toInt().takeIf { it > 0 } ?: 0
    }

    private fun parseDurationText(text: String): Int {
        if (text.isBlank()) return 0
        val parts = text.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }

    private fun JSONArray.toVideoList(): List<BiliVideoItem> {
        val seen = mutableSetOf<String>()
        return buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val owner = item.optJSONObject("owner") ?: JSONObject()
                val stat = item.optJSONObject("stat") ?: JSONObject()
                val rcmdReason = item.optJSONObject("rcmd_reason")
                val bvid = item.optString("bvid")
                if (bvid.isBlank() || !seen.add(bvid)) continue
                add(
                    BiliVideoItem(
                        bvid = bvid,
                        aid = item.optLong("aid"),
                        title = item.optString("title"),
                        coverUrl = normalizeImageUrl(item.optString("pic")),
                        authorName = owner.optString("name"),
                        authorMid = owner.optLong("mid"),
                        authorFace = normalizeImageUrl(owner.optString("face")),
                        viewCount = stat.optLong("view"),
                        danmakuCount = stat.optLong("danmaku"),
                        likeCount = stat.optLong("like"),
                        durationSeconds = item.optLong("duration").toInt(),
                        description = rcmdReason?.optString("content").orEmpty(),
                    ),
                )
            }
        }
    }

    fun parseUserTopPhotoList(json: JSONObject, activeTopPhoto: String = ""): List<String> {
        if (!json.optBoolean("status")) return emptyList()
        val array = json.optJSONArray("data") ?: return emptyList()
        val activePath = topPhotoPathKey(activeTopPhoto)
        val owned = linkedMapOf<String, Int>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optInt("is_disable") == 1) continue
            val url = normalizeSpaceImageUrl(item.optString("l_img"))
            if (url.isBlank()) continue
            val had = item.optInt("had")
            val matchesActive = activePath.isNotBlank() && topPhotoPathKey(url) == activePath
            if (had == 1 || matchesActive) {
                owned.putIfAbsent(url, item.optInt("sort_num"))
            }
        }
        return owned.entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    fun parseUserSpaceSettingsTopPhoto(json: JSONObject): String {
        if (!json.optBoolean("status")) return ""
        val toutu = json.optJSONObject("data")?.optJSONObject("toutu") ?: return ""
        val large = toutu.optString("l_img")
        if (large.isNotBlank()) return normalizeSpaceImageUrl(large)
        return normalizeSpaceImageUrl(toutu.optString("s_img"))
    }

    fun mergeUserTopPhotos(vararg groups: List<String>): List<String> =
        groups.flatMap { it }
            .map(::normalizeSpaceImageUrl)
            .filter { it.isNotBlank() }
            .distinct()

    private fun parseUserTopPhotos(source: JSONObject): List<String> {
        val values = linkedSetOf<String>()
        values.addAll(parseDelimitedTopPhotos(source.optString("top_photo")))
        source.optJSONArray("top_photos")?.let { array ->
            for (index in 0 until array.length()) {
                val url = normalizeSpaceImageUrl(array.optString(index))
                if (url.isNotBlank()) values.add(url)
            }
        }
        val space = source.optJSONObject("space")
        if (space != null) {
            val large = normalizeSpaceImageUrl(space.optString("l_img"))
            if (large.isNotBlank()) values.add(large)
            val small = normalizeSpaceImageUrl(space.optString("s_img"))
            if (small.isNotBlank()) values.add(small)
        }
        return values.toList()
    }

    private fun parseDelimitedTopPhotos(raw: String): List<String> =
        raw.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map(::normalizeSpaceImageUrl)
            .filter { it.isNotBlank() }

    private fun topPhotoPathKey(url: String): String =
        normalizeSpaceImageUrl(url)
            .substringAfter("/bfs/", "")
            .substringBefore('?')

    private fun normalizeSpaceImageUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("bfs/")) return normalizeImageUrl("https://i0.hdslb.com/$trimmed")
        return normalizeImageUrl(trimmed)
    }

    private fun normalizeImageUrl(url: String): String =
        when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://${url.removePrefix("http://")}"
            else -> url
        }

    fun parseSearchBangumiPage(json: JSONObject): BiliSearchResultPage<BiliSearchBangumi> {
        val data = json.optJSONObject("data") ?: return BiliSearchResultPage.empty()
        val page = data.optInt("page", 1).coerceAtLeast(1)
        val numPages = data.optInt("numPages", page).coerceAtLeast(page)
        val result = data.optJSONArray("result") ?: return BiliSearchResultPage.empty()
        val items = buildList(result.length()) {
            for (index in 0 until result.length()) {
                parseSearchBangumiItem(result.optJSONObject(index) ?: continue)?.let(::add)
            }
        }
        return BiliSearchResultPage(items = items, page = page, hasMore = page < numPages)
    }

    fun parseSearchAllPgcMedia(json: JSONObject): List<BiliSearchBangumi> {
        val data = json.optJSONObject("data") ?: return emptyList()
        val groups = data.optJSONArray("result") ?: return emptyList()
        return buildList {
            for (index in 0 until groups.length()) {
                val group = groups.optJSONObject(index) ?: continue
                val resultType = group.optString("result_type")
                if (resultType != "media_bangumi" && resultType != "media_ft") continue
                val list = group.optJSONArray("data") ?: continue
                for (itemIndex in 0 until list.length()) {
                    parseSearchBangumiItem(list.optJSONObject(itemIndex) ?: continue)?.let(::add)
                }
            }
        }
    }

    fun parsePgcSeasonFirstEpid(json: JSONObject): Long {
        val result = json.optJSONObject("result")
            ?: json.optJSONObject("data")?.optJSONObject("result")
            ?: json.optJSONObject("data")
            ?: return 0L
        val episodes = result.optJSONArray("episodes") ?: return 0L
        for (index in 0 until episodes.length()) {
            val episode = episodes.optJSONObject(index) ?: continue
            val epid = episode.optLong("ep_id")
                .takeIf { it > 0L }
                ?: episode.optLong("id").takeIf { it > 0L }
                ?: episode.optLong("epid").takeIf { it > 0L }
                ?: 0L
            if (epid > 0L) return epid
        }
        return 0L
    }

    fun parsePgcEpisodeContext(json: JSONObject, epid: Long): BiliPGCEpisodeContext? {
        if (epid <= 0L) return null
        val result = json.optJSONObject("result")
            ?: json.optJSONObject("data")?.optJSONObject("result")
            ?: json.optJSONObject("data")
            ?: return null
        val episodes = result.optJSONArray("episodes") ?: return null
        var episode: JSONObject? = null
        for (index in 0 until episodes.length()) {
            val candidate = episodes.optJSONObject(index) ?: continue
            val candidateEpid = candidate.optLong("id")
                .takeIf { it > 0L }
                ?: candidate.optLong("ep_id").takeIf { it > 0L }
                ?: 0L
            if (candidateEpid == epid) {
                episode = candidate
                break
            }
        }
        val resolvedEpisode = episode ?: return null
        val seasonId = result.optLong("season_id")
        val seasonTitle = result.optString("title").ifBlank { result.optString("season_title") }
        val episodeTitle = resolvedEpisode.optString("title")
        val longTitle = resolvedEpisode.optString("long_title")
        val aid = resolvedEpisode.optLong("aid")
        val bvid = resolvedEpisode.optString("bvid")
        val cid = resolvedEpisode.optLong("cid")
        if (aid <= 0L || bvid.isBlank() || cid <= 0L) return null

        val styles = result.optJSONArray("styles")?.let { array ->
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotBlank()) add(value)
                }
            }.joinToString(" / ")
        }.orEmpty().ifBlank { result.optString("styles") }

        val areas = result.optJSONArray("areas")?.let { array ->
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val area = array.optJSONObject(index) ?: continue
                    val name = area.optString("name").trim()
                    if (name.isNotBlank()) add(name)
                }
            }.joinToString(" / ")
        }.orEmpty().ifBlank { result.optString("areas") }

        val pages = buildList(episodes.length()) {
            for (index in 0 until episodes.length()) {
                val pageEpisode = episodes.optJSONObject(index) ?: continue
                val pageEpid = pageEpisode.optLong("id")
                    .takeIf { it > 0L }
                    ?: pageEpisode.optLong("ep_id").takeIf { it > 0L }
                    ?: 0L
                val pageCid = pageEpisode.optLong("cid")
                if (pageEpid <= 0L || pageCid <= 0L) continue
                val pageLongTitle = pageEpisode.optString("long_title")
                val pageShortTitle = pageEpisode.optString("title")
                val title = pageLongTitle.ifBlank { pageShortTitle }.ifBlank { "第${index + 1}话" }
                val durationMs = pageEpisode.optLong("duration")
                val durationSeconds = if (durationMs > 0L) (durationMs / 1000L).toInt() else 0
                add(
                    BiliVideoPage(
                        page = index + 1,
                        cid = pageCid,
                        title = title,
                        durationSeconds = durationSeconds,
                        epid = pageEpid,
                    ),
                )
            }
        }

        val durationMs = resolvedEpisode.optLong("duration")
        val durationSeconds = if (durationMs > 0L) {
            (durationMs / 1000L).toInt()
        } else {
            resolvedEpisode.optInt("duration")
        }

        return BiliPGCEpisodeContext(
            epid = epid,
            seasonId = seasonId,
            seasonTitle = seasonTitle,
            episodeTitle = episodeTitle,
            longTitle = longTitle,
            aid = aid,
            bvid = bvid,
            cid = cid,
            coverUrl = normalizeImageUrl(
                resolvedEpisode.optString("cover").ifBlank { result.optString("cover") },
            ),
            durationSeconds = durationSeconds,
            evaluate = result.optString("evaluate"),
            styles = styles,
            areas = areas,
            pages = pages,
        )
    }

    private fun parseSearchBangumiItem(item: JSONObject): BiliSearchBangumi? {
        val itemType = item.optString("type")
        if (itemType.isNotBlank() && itemType != "media_bangumi" && itemType != "media_ft") {
            return null
        }
        val seasonId = item.optLong("season_id").takeIf { it > 0L }
            ?: item.optLong("pgc_season_id").takeIf { it > 0L }
            ?: 0L
        if (seasonId <= 0L) return null
        val title = stripSearchHighlight(item.optString("title"))
        if (title.isBlank()) return null

        val eps = item.optJSONArray("eps")
        var firstEpid = 0L
        if (eps != null) {
            for (index in 0 until eps.length()) {
                val ep = eps.optJSONObject(index) ?: continue
                val epid = ep.optLong("id").takeIf { it > 0L }
                    ?: ep.optLong("epid").takeIf { it > 0L }
                    ?: 0L
                if (epid > 0L) {
                    firstEpid = epid
                    break
                }
            }
        }
        if (firstEpid <= 0L) {
            val firstEp = item.optJSONObject("first_ep")
            if (firstEp != null) {
                firstEpid = firstEp.optLong("id").takeIf { it > 0L }
                    ?: firstEp.optLong("ep_id").takeIf { it > 0L }
                    ?: firstEp.optLong("epid").takeIf { it > 0L }
                    ?: 0L
            }
        }
        if (firstEpid <= 0L) {
            firstEpid = epidFromBangumiSearchItem(item, eps)
        }

        return BiliSearchBangumi(
            seasonId = seasonId,
            mediaId = item.optLong("media_id"),
            title = title,
            subtitle = stripSearchHighlight(item.optString("org_title")),
            coverUrl = normalizeImageUrl(item.optString("cover")),
            areas = stripSearchHighlight(item.optString("areas")),
            styles = stripSearchHighlight(item.optString("styles")),
            badge = membershipBadgeFromSearchItem(item),
            categoryName = pgcCategoryName(item),
            indexShow = stripSearchHighlight(
                item.optString("index_show").ifBlank { item.optString("fix_pubtime_str") },
            ),
            webUrl = normalizeImageUrl(
                item.optString("goto_url").ifBlank { item.optString("url") },
            ),
            firstEpid = firstEpid,
        )
    }

    private fun membershipBadgeFromSearchItem(item: JSONObject): String {
        val angleTitle = stripSearchHighlight(item.optString("angle_title"))
        if (angleTitle.isNotBlank()) return angleTitle
        val displayInfo = item.optJSONArray("display_info")
        if (displayInfo != null) {
            for (index in 0 until displayInfo.length()) {
                val text = stripSearchHighlight(displayInfo.optJSONObject(index)?.optString("text").orEmpty())
                if (text.isNotBlank()) return text
            }
        }
        val badges = item.optJSONArray("badges")
        if (badges != null) {
            for (index in 0 until badges.length()) {
                val text = stripSearchHighlight(badges.optJSONObject(index)?.optString("text").orEmpty())
                if (text.isNotBlank()) return text
            }
        }
        return ""
    }

    private fun pgcCategoryName(item: JSONObject): String {
        val seasonTypeName = stripSearchHighlight(item.optString("season_type_name"))
        if (seasonTypeName.isNotBlank()) return seasonTypeName
        return when (item.optInt("media_type").takeIf { it > 0 } ?: item.optInt("season_type")) {
            1 -> "番剧"
            2 -> "电影"
            3 -> "纪录片"
            4 -> "国创"
            5 -> "电视剧"
            7 -> "综艺"
            else -> "影视"
        }
    }

    private fun epidFromBangumiSearchItem(item: JSONObject, eps: org.json.JSONArray?): Long {
        val hitEpids = item.optString("hit_epids")
        if (hitEpids.isNotBlank()) {
            val first = hitEpids.split(',').firstOrNull()?.trim().orEmpty()
            first.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
        }
        val urlCandidates = buildList {
            add(item.optString("goto_url"))
            add(item.optString("url"))
            if (eps != null) {
                for (index in 0 until eps.length()) {
                    add(eps.optJSONObject(index)?.optString("url").orEmpty())
                }
            }
        }
        for (candidate in urlCandidates) {
            if (candidate.isBlank()) continue
            epidFromBangumiPlayUrl(candidate)?.let { return it }
        }
        return 0L
    }

    private fun epidFromBangumiPlayUrl(url: String): Long? {
        val match = Regex("""/ep(\d+)""").find(url) ?: return null
        return match.groupValues.getOrNull(1)?.toLongOrNull()
    }
}
