package com.example.bilibili.data

import org.json.JSONArray
import org.json.JSONObject

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
        val cid = pages?.optJSONObject(0)?.optLong("cid") ?: 0L
        val bvid = data.optString("bvid")
        if (bvid.isBlank()) return null
        val video = BiliVideoItem(
            bvid = bvid,
            aid = data.optLong("aid"),
            title = data.optString("title"),
            coverUrl = normalizeImageUrl(data.optString("pic")),
            authorName = owner.optString("name"),
            authorMid = owner.optLong("mid"),
            viewCount = stat.optLong("view"),
            danmakuCount = stat.optLong("danmaku"),
            likeCount = stat.optLong("like"),
            durationSeconds = data.optLong("duration").toInt(),
            description = data.optString("desc"),
            cid = cid,
        )
        return BiliVideoDetail(
            video = video,
            publishTimeSeconds = data.optLong("pubdate"),
            replyCount = stat.optLong("reply"),
        )
    }

    fun normalizeIpLocation(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "未知") return null
        return raw
            .removePrefix("IP属地：")
            .removePrefix("IP属地:")
            .trim()
            .takeIf { it.isNotBlank() && it != "未知" }
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
        return BiliAuthorRelation(
            following = attribute == 2 || attribute == 6,
            followerMe = attribute == 6,
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
        val replies = data.optJSONArray("replies") ?: JSONArray()
        return BiliCommentPage(
            comments = buildList(replies.length()) {
                for (index in 0 until replies.length()) {
                    replies.optJSONObject(index)
                        ?.let { parseCommentItem(it, includeInlineReplies = true) }
                        ?.let(::add)
                }
            },
            nextCursor = cursor?.optJSONObject("pagination_reply")
                ?.optString("next_offset")
                ?.takeIf { it.isNotBlank() },
            isEnd = cursor?.optBoolean("is_end")
                ?: cursor?.optJSONObject("pagination_reply")?.optString("next_offset").isNullOrBlank(),
            totalCount = cursor?.optLong("all_count")
                ?: data.optJSONObject("page")?.optLong("acount")
                ?: 0L,
        )
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

    private fun parseCommentItem(
        json: JSONObject,
        nested: Boolean = false,
        includeInlineReplies: Boolean = false,
    ): BiliCommentItem? {
        val member = json.optJSONObject("member") ?: return null
        val content = json.optJSONObject("content") ?: JSONObject()
        val message = content.optString("message")
        val pictures = content.parseCommentPictures()
        if (message.isBlank() && pictures.isEmpty() && !nested) return null
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
            emoticons = content.parseEmoteMap(),
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
            topPhoto = parseUserTopPhoto(data),
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
            topPhoto = parseUserTopPhoto(card),
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
        val isForward = item.optString("type") == "DYNAMIC_TYPE_FORWARD" ||
            item.has("orig")
        val stat = modules.optJSONObject("module_stat")
        val publishTime = modules.optJSONObject("module_author")?.optLong("pub_ts")
            ?.takeIf { it > 0L }
            ?: item.optLong("pub_ts")

        if (isForward) {
            val forwardText = parseDynamicDesc(modules)
            val origin = item.optJSONObject("orig")
                ?.optJSONObject("modules")
                ?.let(::parseDynamicBody)
                ?.let { body ->
                    val authorName = item.optJSONObject("orig")
                        ?.optJSONObject("modules")
                        ?.let(::parseModuleAuthorName)
                        .orEmpty()
                    BiliDynamicOrigin(
                        authorName = authorName,
                        text = body.text,
                        video = body.video,
                        imageUrls = body.imageUrls,
                        link = body.link,
                    )
                }
            return BiliDynamicItem(
                id = id,
                text = forwardText,
                publishTimeSeconds = publishTime,
                origin = origin,
                likeCount = parseDynamicStatCount(stat, "like"),
                commentCount = parseDynamicStatCount(stat, "comment"),
                repostCount = parseDynamicStatCount(stat, "forward"),
            )
        }

        val body = parseDynamicBody(modules)
        return BiliDynamicItem(
            id = id,
            text = body.text,
            publishTimeSeconds = publishTime,
            video = body.video,
            imageUrls = body.imageUrls,
            link = body.link,
            likeCount = parseDynamicStatCount(stat, "like"),
            commentCount = parseDynamicStatCount(stat, "comment"),
            repostCount = parseDynamicStatCount(stat, "forward"),
        )
    }

    private data class DynamicBody(
        val text: String = "",
        val video: BiliVideoItem? = null,
        val imageUrls: List<String> = emptyList(),
        val link: BiliDynamicLink? = null,
    )

    private fun parseDynamicBody(modules: JSONObject): DynamicBody {
        val text = parseDynamicDesc(modules)
        val major = modules.optJSONObject("module_dynamic")?.optJSONObject("major")
        if (major == null) return DynamicBody(text = text)
        val majorType = major.optString("type")
        val video = if (majorType == "MAJOR_TYPE_ARCHIVE") {
            parseDynamicArchiveVideo(major, modules)
        } else {
            null
        }
        val imageUrls = when (majorType) {
            "MAJOR_TYPE_DRAW" -> parseDynamicDrawImages(major)
            "MAJOR_TYPE_OPUS" -> parseDynamicOpusImages(major)
            else -> emptyList()
        }
        val link = parseDynamicLink(major, majorType)
        return DynamicBody(
            text = text,
            video = video,
            imageUrls = imageUrls,
            link = link,
        )
    }

    private fun parseDynamicDesc(modules: JSONObject): String {
        val moduleDynamic = modules.optJSONObject("module_dynamic")
        return moduleDynamic?.optJSONObject("desc")
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
            ?: modules.optJSONObject("module_desc")
                ?.optJSONObject("text")
                ?.optString("text")
                .orEmpty()
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
        val bvid = archive.optString("bvid")
        if (bvid.isBlank()) return null
        val author = modules.optJSONObject("module_author")
        val stat = archive.optJSONObject("stat") ?: JSONObject()
        return BiliVideoItem(
            bvid = bvid,
            aid = archive.optString("aid").toLongOrNull() ?: 0L,
            title = archive.optString("title"),
            coverUrl = normalizeImageUrl(archive.optString("cover")),
            authorName = author?.optString("name").orEmpty(),
            authorMid = author?.optLong("mid") ?: 0L,
            viewCount = parseCount(stat.optString("play")),
            danmakuCount = parseCount(stat.optString("danmaku")),
            likeCount = 0L,
            durationSeconds = parseDurationText(archive.optString("duration_text")),
            description = archive.optString("desc"),
        )
    }

    private fun parseDynamicDrawImages(major: JSONObject): List<String> {
        val draw = major.optJSONObject("draw") ?: return emptyList()
        val items = draw.optJSONArray("items") ?: return emptyList()
        return buildList(items.length()) {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                normalizeImageUrl(item.optString("src")).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun parseDynamicOpusImages(major: JSONObject): List<String> {
        val opus = major.optJSONObject("opus") ?: return emptyList()
        val pics = opus.optJSONArray("pics") ?: return emptyList()
        return buildList(pics.length()) {
            for (index in 0 until pics.length()) {
                val item = pics.optJSONObject(index) ?: continue
                normalizeImageUrl(item.optString("url")).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    fun parseLiveHotRank(json: JSONObject): List<BiliLiveRoom> {
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
        return buildList(list.length()) {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                add(
                    BiliLiveRoom(
                        roomId = item.optLong("roomid"),
                        title = item.optString("title"),
                        coverUrl = normalizeImageUrl(
                            item.optString("cover").ifBlank { item.optString("user_cover") },
                        ),
                        userName = item.optString("uname"),
                        userFace = normalizeImageUrl(item.optString("face")),
                        online = item.optLong("online"),
                        areaName = item.optString("area_name"),
                    ),
                )
            }
        }
    }

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
        val history = item.optJSONObject("history") ?: return null
        if (history.optString("business") != "archive") return null
        val bvid = history.optString("bvid")
        if (bvid.isBlank()) return null
        val aid = history.optLong("oid")
        if (aid <= 0L) return null
        val title = item.optString("title").ifBlank { item.optString("show_title") }
        val cover = item.optString("cover").ifBlank {
            item.optJSONArray("covers")?.optString(0).orEmpty()
        }
        return BiliHistoryItem(
            kid = "archive_$aid",
            bvid = bvid,
            aid = aid,
            cid = history.optLong("cid"),
            title = title,
            coverUrl = normalizeImageUrl(cover),
            authorName = item.optString("author_name"),
            authorMid = item.optLong("author_mid"),
            viewAtSeconds = item.optLong("view_at"),
            progressSeconds = item.optInt("progress").coerceAtLeast(0),
            durationSeconds = item.optInt("duration").coerceAtLeast(0),
        )
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

    fun parseLiveAreaList(json: JSONObject): List<BiliLiveRoom> {
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
        return buildList(list.length()) {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                add(
                    BiliLiveRoom(
                        roomId = item.optLong("roomid"),
                        title = item.optString("title"),
                        coverUrl = normalizeImageUrl(item.optString("cover")),
                        userName = item.optString("uname"),
                        userFace = normalizeImageUrl(item.optString("face")),
                        online = item.optLong("online"),
                        areaName = item.optString("area_name"),
                    ),
                )
            }
        }
    }

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

    private fun parseUserTopPhoto(source: JSONObject): String {
        val direct = source.optString("top_photo")
        if (direct.isNotBlank()) return normalizeImageUrl(direct)
        val space = source.optJSONObject("space") ?: return ""
        val large = space.optString("l_img")
        if (large.isNotBlank()) return normalizeImageUrl(large)
        return normalizeImageUrl(space.optString("s_img"))
    }

    private fun normalizeImageUrl(url: String): String =
        when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://${url.removePrefix("http://")}"
            else -> url
        }
}
