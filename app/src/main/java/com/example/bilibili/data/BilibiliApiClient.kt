package com.example.bilibili.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.bilibili.util.BiliArticleUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BilibiliApiClient {
    var onAccessKeyUpdated: ((BilibiliCredential) -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val wbiMutex = Mutex()
    private val buvidMutex = Mutex()
    private var mixinKey: String? = null
    private var guestBuvid3: String? = null
    private var guestBuvid4: String? = null

    suspend fun getHomeRecommend(
        credential: BilibiliCredential? = null,
        freshIdx: Int = 1,
        fetchRow: Int = 1,
        lastShowList: String = "",
        pageSize: Int = 30,
    ): BiliHomeRecommendPage =
        withContext(Dispatchers.IO) {
            val params = buildMap {
                put("fresh_type", "4")
                put("ps", pageSize.coerceIn(1, 30).toString())
                put("fresh_idx", freshIdx.toString())
                put("fresh_idx_1h", freshIdx.toString())
                put("brush", freshIdx.toString())
                put("fetch_row", fetchRow.toString())
                put("web_location", "1430650")
                if (lastShowList.isNotBlank()) {
                    put("last_showlist", lastShowList)
                }
            }
            getWbiJsonOnCurrentThread(
                url = BilibiliEndpoints.HOME_RECOMMEND,
                params = params,
                credential = credential,
            ).let { json ->
                BilibiliJsonParser.parseHomeRecommendPage(json, freshIdx, fetchRow)
            }
        }

    suspend fun getFollowingVideos(
        credential: BilibiliCredential,
        offset: String? = null,
    ): BiliFollowingFeedPage =
        withContext(Dispatchers.IO) {
            val params = buildMap {
            put("timezone_offset", "-480")
            put("type", "video")
            put("platform", "web")
            put("gaia_source", "main_web")
            put("web_location", "333.1365")
                put("features", BilibiliEndpoints.DYNAMIC_FEED_FEATURES)
                if (!offset.isNullOrBlank()) {
                    put("offset", offset)
                }
            }
            getDynamicJson(
                url = BilibiliEndpoints.FOLLOWING_FEED,
                params = params,
                credential = credential,
                referer = "https://t.bilibili.com/",
            ).let(BilibiliJsonParser::parseFollowingVideoFeed)
        }

    suspend fun getSiteRanking(rid: Int = 0): List<BiliVideoItem> =
        withContext(Dispatchers.IO) {
            getWbiJsonOnCurrentThread(
                url = BilibiliEndpoints.RANKING_V2,
                params = mapOf(
                    "rid" to rid.toString(),
                    "type" to "all",
                    "web_location" to "333.934",
                ),
            ).let(BilibiliJsonParser::parseVideosFromFeed)
        }

    suspend fun getVideoView(bvid: String, credential: BilibiliCredential? = null): BiliVideoItem? =
        getVideoDetail(bvid, credential)?.video

    suspend fun getVideoDetail(bvid: String, credential: BilibiliCredential? = null): BiliVideoDetail? =
        withContext(Dispatchers.IO) {
            runCatching {
                getJson(
                    url = BilibiliEndpoints.VIDEO_VIEW,
                    params = mapOf("bvid" to bvid),
                    credential = credential,
                    referer = "https://www.bilibili.com/video/$bvid",
                ).let(BilibiliJsonParser::parseVideoDetail)
            }.getOrNull()
        }

    suspend fun getVideoDetailByAid(aid: Long, credential: BilibiliCredential? = null): BiliVideoDetail? =
        withContext(Dispatchers.IO) {
            if (aid <= 0L) return@withContext null
            runCatching {
                getJson(
                    url = BilibiliEndpoints.VIDEO_VIEW,
                    params = mapOf("aid" to aid.toString()),
                    credential = credential,
                    referer = "https://www.bilibili.com/video/av$aid",
                ).let(BilibiliJsonParser::parseVideoDetail)
            }.getOrNull()
        }

    suspend fun resolveVideoForPlayback(
        video: BiliVideoItem,
        credential: BilibiliCredential? = null,
    ): BiliVideoItem {
        if (video.isPgcPlayback()) {
            val epid = video.pgcEpid()
            if (epid > 0L) {
                val context = getPgcEpisodeContext(epid, credential)
                if (context != null) {
                    return mergePgcContextToVideo(context, video)
                }
            }
            return video
        }
        val detail = getVideoDetail(video.bvid, credential) ?: return video
        val targetCid = resolveTargetCid(video.cid, detail.pages)
            ?: detail.video.cid.takeIf { it > 0L }
            ?: detail.pages.firstOrNull()?.cid?.takeIf { it > 0L }
            ?: return video.copy(
                aid = video.aid.takeIf { it > 0L } ?: detail.video.aid,
            )
        val resolvedVideo = video.copy(
            cid = targetCid,
            aid = video.aid.takeIf { it > 0L } ?: detail.video.aid,
        )
        if (detail.pages.any { it.cid == targetCid }) return resolvedVideo
        if (detail.video.cid == targetCid) return resolvedVideo

        val season = hydrateVideoUgcSeason(detail, credential).ugcSeason ?: return resolvedVideo
        val episode = season.sections.asSequence()
            .flatMap { it.episodes.asSequence() }
            .firstOrNull { episode ->
                when {
                    video.cid > 0L -> episode.cid == video.cid
                    video.bvid.isNotBlank() -> episode.bvid == video.bvid
                    video.aid > 0L -> episode.aid == video.aid
                    else -> false
                }
            }
            ?: return resolvedVideo
        if (episode.bvid == resolvedVideo.bvid && episode.aid == resolvedVideo.aid) return resolvedVideo

        return episode.toVideoItem(resolvedVideo.authorName, resolvedVideo.authorMid).copy(
            title = episode.title.ifBlank { resolvedVideo.title },
            coverUrl = episode.coverUrl.ifBlank { resolvedVideo.coverUrl },
            durationSeconds = episode.durationSeconds.takeIf { it > 0 } ?: resolvedVideo.durationSeconds,
            cid = episode.cid.takeIf { it > 0L } ?: targetCid,
        )
    }

    suspend fun resolveHistoryVideo(
        item: BiliHistoryItem,
        credential: BilibiliCredential? = null,
    ): BiliVideoItem {
        if (item.business == BiliHistoryBusiness.Pgc) {
            val epid = item.epid.takeIf { it > 0L } ?: 0L
            if (epid > 0L) {
                val context = getPgcEpisodeContext(epid, credential)
                if (context != null) {
                    return mergePgcContextToVideo(context, item.toVideoItem())
                }
            }
            return item.toVideoItem()
        }
        val detail = getVideoDetail(item.bvid, credential)
        val targetCid = resolveTargetCid(
            explicitCid = item.cid,
            pages = detail?.pages.orEmpty(),
            partPage = item.page,
        )
        val base = item.toVideoItem().let { video ->
            if (targetCid != null) {
                video.copy(
                    cid = targetCid,
                    aid = video.aid.takeIf { it > 0L } ?: item.aid,
                )
            } else {
                video
            }
        }
        return resolveVideoForPlayback(base, credential)
    }

    suspend fun getPgcEpisodeContext(
        epid: Long,
        credential: BilibiliCredential? = null,
    ): BiliPGCEpisodeContext? = withContext(Dispatchers.IO) {
        if (epid <= 0L) return@withContext null
        runCatching {
            getJson(
                url = BilibiliEndpoints.PGC_SEASON,
                params = mapOf("ep_id" to epid.toString()),
                credential = credential,
                referer = "https://www.bilibili.com/bangumi/play/ep$epid",
            ).let { BilibiliJsonParser.parsePgcEpisodeContext(it, epid) }
        }.getOrNull()
    }

    suspend fun getPgcVideoDetail(
        epid: Long,
        credential: BilibiliCredential? = null,
    ): BiliVideoDetail? {
        val context = getPgcEpisodeContext(epid, credential) ?: return null
        val loaded = getVideoDetail(context.bvid, credential) ?: return null
        val mergedVideo = mergePgcContextToVideo(
            context,
            loaded.video.copy(
                epid = context.epid,
                playbackReferer = "https://www.bilibili.com/bangumi/play/ep${context.epid}",
            ),
        )
        return loaded.copy(
            video = mergedVideo,
            pages = context.pages.ifEmpty { loaded.pages },
        )
    }

    suspend fun getPgcPlayUrl(
        epid: Long,
        cid: Long = 0L,
        credential: BilibiliCredential? = null,
        referer: String = BilibiliEndpoints.HOME,
    ): BiliPlayStream? = withContext(Dispatchers.IO) {
        if (epid <= 0L && cid <= 0L) return@withContext null
        suspend fun request(fnval: String, useV2: Boolean): BiliPlayStream? = runCatching {
            val params = mutableMapOf(
                "qn" to "80",
                "fnval" to fnval,
                "fnver" to "0",
                "fourk" to "1",
                "drm_tech_type" to "2",
                "from_client" to "BROWSER",
            )
            if (epid > 0L) params["ep_id"] = epid.toString()
            if (cid > 0L) params["cid"] = cid.toString()
            val url = if (useV2) BilibiliEndpoints.PGC_PLAYURL_V2 else BilibiliEndpoints.PGC_PLAYURL
            getJson(url, params, credential, referer = referer)
                .let(BilibiliJsonParser::parsePlayUrl)
        }.getOrNull()

        val stream = request(fnval = "4048", useV2 = true)
            ?: request(fnval = "16", useV2 = false)
            ?: request(fnval = "1", useV2 = false)
        stream?.copy(cid = cid.takeIf { it > 0L } ?: stream.cid)
    }

    suspend fun pgcSeasonFirstEpid(
        seasonId: Long,
        credential: BilibiliCredential? = null,
    ): Long = withContext(Dispatchers.IO) {
        if (seasonId <= 0L) return@withContext 0L
        runCatching {
            getJson(
                url = BilibiliEndpoints.PGC_SEASON,
                params = mapOf("season_id" to seasonId.toString()),
                credential = credential,
                referer = "https://www.bilibili.com/bangumi/play/ss$seasonId",
            ).let(BilibiliJsonParser::parsePgcSeasonFirstEpid)
        }.getOrDefault(0L)
    }

    suspend fun enrichPinnedMediaItems(
        items: List<BiliSearchBangumi>,
        credential: BilibiliCredential? = null,
    ): List<BiliSearchBangumi> {
        if (items.isEmpty()) return items
        val enriched = items.toMutableList()
        items.withIndex()
            .filter { (_, item) -> item.firstEpid <= 0L && item.seasonId > 0L }
            .take(16)
            .forEach { (index, item) ->
                val epid = pgcSeasonFirstEpid(item.seasonId, credential)
                if (epid > 0L) {
                    enriched[index] = enriched[index].withFirstEpid(epid)
                }
            }
        return enriched
    }

    suspend fun searchPgcMedia(
        keyword: String,
        searchType: String,
        page: Int = 1,
        credential: BilibiliCredential? = null,
    ): BiliSearchResultPage<BiliSearchBangumi> = withContext(Dispatchers.IO) {
        val refererPath = if (searchType == "media_ft") "movie" else "bangumi"
        runCatching {
            getWbiJsonOnCurrentThread(
                url = BilibiliEndpoints.SEARCH_TYPE,
                params = mapOf(
                    "search_type" to searchType,
                    "keyword" to keyword,
                    "page" to page.coerceAtLeast(1).toString(),
                ),
                credential = credential,
                referer = "https://search.bilibili.com/$refererPath?keyword=${encode(keyword)}",
            ).let(BilibiliJsonParser::parseSearchBangumiPage)
        }.getOrDefault(BiliSearchResultPage.empty())
    }

    suspend fun searchAllPgcMedia(
        keyword: String,
        credential: BilibiliCredential? = null,
    ): List<BiliSearchBangumi> = withContext(Dispatchers.IO) {
        runCatching {
            getWbiJsonOnCurrentThread(
                url = BilibiliEndpoints.SEARCH_ALL,
                params = mapOf("keyword" to keyword),
                credential = credential,
                referer = "https://search.bilibili.com/all?keyword=${encode(keyword)}",
            ).let(BilibiliJsonParser::parseSearchAllPgcMedia)
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchPgcSearchPage(
        keyword: String,
        searchType: String,
        page: Int,
        credential: BilibiliCredential?,
    ): BiliSearchResultPage<BiliSearchBangumi> {
        searchPgcMedia(keyword, searchType, page, credential).takeIf { it.items.isNotEmpty() }
            ?.let { return it }
        delay(250)
        return searchPgcMedia(keyword, searchType, page, credential)
    }

    suspend fun searchPinnedMedia(
        keyword: String,
        page: Int = 1,
        credential: BilibiliCredential? = null,
    ): BiliSearchResultPage<BiliSearchBangumi> = withContext(Dispatchers.IO) {
        val resolvedPage = page.coerceAtLeast(1)
        val allResults = if (resolvedPage == 1) {
            searchAllPgcMedia(keyword, credential)
        } else {
            emptyList()
        }
        val bangumiPage = fetchPgcSearchPage(keyword, "media_bangumi", resolvedPage, credential)
        val ftPage = fetchPgcSearchPage(keyword, "media_ft", resolvedPage, credential)
        val merged = mutableListOf<BiliSearchBangumi>()
        val seen = mutableSetOf<Long>()
        for (item in allResults + bangumiPage.items + ftPage.items) {
            if (seen.add(item.seasonId)) {
                merged += item
            }
        }
        val sorted = BiliSearchBangumi.sortedForDisplay(merged)
        val enriched = enrichPinnedMediaItems(sorted, credential)
        BiliSearchResultPage(
            items = enriched,
            page = resolvedPage,
            hasMore = bangumiPage.hasMore || ftPage.hasMore,
        )
    }

    private fun mergePgcContextToVideo(
        context: BiliPGCEpisodeContext,
        base: BiliVideoItem,
    ): BiliVideoItem = base.copy(
        bvid = context.bvid,
        aid = context.aid,
        cid = context.cid,
        epid = context.epid,
        title = context.displayTitle().ifBlank { base.title },
        coverUrl = context.coverUrl.ifBlank { base.coverUrl },
        authorName = context.metadataLine().ifBlank { base.authorName },
        durationSeconds = context.durationSeconds.takeIf { it > 0 } ?: base.durationSeconds,
        playbackReferer = base.playbackReferer.ifBlank {
            "https://www.bilibili.com/bangumi/play/ep${context.epid}"
        },
    )

    private fun resolveTargetCid(
        explicitCid: Long,
        pages: List<BiliVideoPage>,
        partPage: Int = 0,
    ): Long? {
        explicitCid.takeIf { it > 0L }?.let { return it }
        if (partPage > 0) {
            pages.find { it.page == partPage }?.cid?.takeIf { it > 0L }?.let { return it }
            pages.getOrNull(partPage - 1)?.cid?.takeIf { it > 0L }?.let { return it }
        }
        return null
    }

    suspend fun getUgcSeasonArchives(
        mid: Long,
        seasonId: Long,
        credential: BilibiliCredential? = null,
        pageSize: Int = 30,
    ): BiliUgcSeason? = withContext(Dispatchers.IO) {
        if (mid <= 0L || seasonId <= 0L) return@withContext null
        runCatching {
            var merged: BiliUgcSeason? = null
            var pageNum = 1
            var totalPages = 1
            while (pageNum <= totalPages) {
                val page = getJson(
                    url = BilibiliEndpoints.UGC_SEASON_ARCHIVES,
                    params = mapOf(
                        "mid" to mid.toString(),
                        "season_id" to seasonId.toString(),
                        "page_num" to pageNum.toString(),
                        "page_size" to pageSize.coerceIn(1, 100).toString(),
                        "sort_reverse" to "false",
                    ),
                    credential = credential,
                    referer = "https://space.bilibili.com/$mid",
                ).let(BilibiliJsonParser::parseUgcSeasonArchives) ?: break
                merged = if (merged == null) page else BilibiliJsonParser.mergeUgcSeasonArchives(merged, page)
                val total = page.apiEpCount.coerceAtLeast(page.episodeCount)
                totalPages = ((total + pageSize - 1) / pageSize).coerceAtLeast(1)
                if (page.sections.sumOf { it.episodes.size } < pageSize) break
                pageNum += 1
            }
            merged
        }.getOrNull()
    }

    suspend fun hydrateVideoUgcSeason(
        detail: BiliVideoDetail,
        credential: BilibiliCredential? = null,
    ): BiliVideoDetail {
        val season = detail.ugcSeason ?: return detail
        if (!season.needsHydration()) return detail
        val mid = season.mid.takeIf { it > 0L } ?: detail.video.authorMid
        val hydrated = getUgcSeasonArchives(
            mid = mid,
            seasonId = season.id,
            credential = credential,
        ) ?: return detail
        val merged = BilibiliJsonParser.mergeUgcSeasonArchives(season, hydrated)
        return detail.copy(ugcSeason = merged)
    }

    suspend fun getVideoArchiveRelation(
        bvid: String,
        aid: Long,
        credential: BilibiliCredential? = null,
    ): BiliVideoRelation = withContext(Dispatchers.IO) {
        if (bvid.isBlank() && aid <= 0L) return@withContext BiliVideoRelation()
        val params = buildMap {
            if (bvid.isNotBlank()) put("bvid", bvid)
            if (aid > 0L) put("aid", aid.toString())
        }
        val referer = "https://www.bilibili.com/video/$bvid"
        coroutineScope {
            val relationDeferred = async {
                runCatching {
                    getJson(
                        url = BilibiliEndpoints.VIDEO_ARCHIVE_RELATION,
                        params = params,
                        credential = credential,
                        referer = referer,
                    ).let(BilibiliJsonParser::parseVideoArchiveRelation)
                }.getOrDefault(BiliVideoRelation())
            }
            if (credential == null) {
                return@coroutineScope relationDeferred.await()
            }
            val likeDeferred = async {
                runCatching {
                    getJson(
                        url = BilibiliEndpoints.VIDEO_HAS_LIKE,
                        params = params,
                        credential = credential,
                        referer = referer,
                    ).let(BilibiliJsonParser::parseHasLike)
                }.getOrDefault(false)
            }
            val favouredParams = when {
                aid > 0L -> mapOf("aid" to aid.toString())
                bvid.isNotBlank() -> mapOf("aid" to bvid)
                else -> emptyMap()
            }
            val favDeferred = async {
                if (favouredParams.isEmpty()) return@async false
                runCatching {
                    getJson(
                        url = BilibiliEndpoints.VIDEO_FAVOURED,
                        params = favouredParams,
                        credential = credential,
                        referer = referer,
                    ).let(BilibiliJsonParser::parseVideoFavoured)
                }.getOrDefault(false)
            }
            BilibiliJsonParser.mergeVideoRelations(
                relationDeferred.await(),
                BiliVideoRelation(
                    liked = likeDeferred.await(),
                    favorited = favDeferred.await(),
                ),
            )
        }
    }

    suspend fun likeVideo(
        bvid: String,
        aid: Long,
        like: Boolean,
        credential: BilibiliCredential,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (credential.biliJct.isBlank()) {
            return@withContext Result.failure(IllegalStateException("登录凭证无效，请重新登录"))
        }
        runCatching {
            postForm(
                url = BilibiliEndpoints.VIDEO_LIKE,
                form = buildMap {
                    if (bvid.isNotBlank()) put("bvid", bvid)
                    if (aid > 0L) put("aid", aid.toString())
                    put("like", if (like) "1" else "2")
                    put("csrf", credential.biliJct)
                    put("eab_x", "2")
                    put("ramval", "0")
                    put("source", "web_normal")
                    put("ga", "1")
                    put("dyn", "2")
                },
                credential = credential,
                referer = "https://www.bilibili.com/video/$bvid",
            )
            Unit
        }
    }

    suspend fun coinVideo(
        bvid: String,
        aid: Long,
        multiply: Int = 1,
        credential: BilibiliCredential,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (credential.biliJct.isBlank()) {
            return@withContext Result.failure(IllegalStateException("登录凭证无效，请重新登录"))
        }
        runCatching {
            postForm(
                url = BilibiliEndpoints.VIDEO_COIN,
                form = buildMap {
                    if (bvid.isNotBlank()) put("bvid", bvid)
                    if (aid > 0L) put("aid", aid.toString())
                    put("multiply", multiply.coerceIn(1, 2).toString())
                    put("select_like", "0")
                    put("csrf", credential.biliJct)
                    put("eab_x", "2")
                    put("ramval", "0")
                    put("source", "web_normal")
                    put("ga", "1")
                    put("dyn", "2")
                },
                credential = credential,
                referer = "https://www.bilibili.com/video/$bvid",
            )
            Unit
        }
    }

    suspend fun tripleVideo(
        bvid: String,
        aid: Long,
        credential: BilibiliCredential,
    ): Result<BiliVideoTripleResult> = withContext(Dispatchers.IO) {
        if (credential.biliJct.isBlank()) {
            return@withContext Result.failure(IllegalStateException("登录凭证无效，请重新登录"))
        }
        runCatching {
            postForm(
                url = BilibiliEndpoints.VIDEO_TRIPLE,
                form = buildMap {
                    if (bvid.isNotBlank()) put("bvid", bvid)
                    if (aid > 0L) put("aid", aid.toString())
                    put("csrf", credential.biliJct)
                    put("eab_x", "2")
                    put("ramval", "0")
                    put("source", "web_normal")
                    put("ga", "1")
                    put("dyn", "2")
                },
                credential = credential,
                referer = "https://www.bilibili.com/video/$bvid",
            ).let(BilibiliJsonParser::parseVideoTripleResult)
        }
    }

    suspend fun shareVideo(
        bvid: String,
        aid: Long,
        credential: BilibiliCredential? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (credential?.biliJct?.isNotBlank() == true) {
                postForm(
                    url = BilibiliEndpoints.VIDEO_SHARE,
                    form = buildMap {
                        if (bvid.isNotBlank()) put("bvid", bvid)
                        if (aid > 0L) put("aid", aid.toString())
                        put("csrf", credential.biliJct)
                        put("eab_x", "2")
                        put("ramval", "0")
                        put("source", "web_normal")
                        put("ga", "1")
                        put("dyn", "2")
                    },
                    credential = credential,
                    referer = "https://www.bilibili.com/video/$bvid",
                )
            }
            Unit
        }
    }

    suspend fun modifyVideoFavorite(
        bvid: String,
        aid: Long,
        add: Boolean,
        credential: BilibiliCredential,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (credential.biliJct.isBlank()) {
            return@withContext Result.failure(IllegalStateException("登录凭证无效，请重新登录"))
        }
        runCatching {
            val folderId = getDefaultFavoriteFolderId(credential)
                ?: error("未找到收藏夹")
            postForm(
                url = BilibiliEndpoints.FAV_RESOURCE_DEAL,
                form = buildMap {
                    put("rid", aid.toString())
                    put("type", "2")
                    if (add) {
                        put("add_media_ids", folderId.toString())
                        put("del_media_ids", "")
                    } else {
                        put("add_media_ids", "")
                        put("del_media_ids", folderId.toString())
                    }
                    put("csrf", credential.biliJct)
                },
                credential = credential,
                referer = "https://www.bilibili.com/video/$bvid",
            )
            Unit
        }
    }

    private suspend fun getDefaultFavoriteFolderId(credential: BilibiliCredential): Long? =
        runCatching {
            getJson(
                url = BilibiliEndpoints.FAV_FOLDER_LIST,
                params = mapOf(
                    "up_mid" to credential.dedeUserId,
                    "type" to "2",
                ),
                credential = credential,
                referer = BilibiliEndpoints.HOME,
            ).let(BilibiliJsonParser::parseDefaultFavoriteFolderId)
        }.getOrNull()

    suspend fun getFavoriteVideoPage(
        credential: BilibiliCredential,
        page: Int = 1,
        pageSize: Int = 20,
    ): BiliFavoriteVideoPage = withContext(Dispatchers.IO) {
        val folderId = getDefaultFavoriteFolderId(credential)
            ?: error("未找到收藏夹")
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 20)
        getJson(
            url = BilibiliEndpoints.FAV_RESOURCE_LIST,
            params = mapOf(
                "media_id" to folderId.toString(),
                "platform" to "web",
                "type" to "0",
                "order" to "mtime",
                "pn" to safePage.toString(),
                "ps" to safePageSize.toString(),
            ),
            credential = credential,
            referer = BilibiliEndpoints.HOME,
        ).let { BilibiliJsonParser.parseFavoriteVideoPage(it, safePage, safePageSize) }
    }

    suspend fun getVideoOnlineCount(
        bvid: String,
        aid: Long,
        cid: Long,
        credential: BilibiliCredential? = null,
    ): Long = withContext(Dispatchers.IO) {
        runCatching {
            getJson(
                url = BilibiliEndpoints.VIDEO_ONLINE,
                params = mapOf(
                    "bvid" to bvid,
                    "aid" to aid.toString(),
                    "cid" to cid.toString(),
                ),
                credential = credential,
            ).let(BilibiliJsonParser::parseOnlineCount)
        }.getOrDefault(0L)
    }

    suspend fun getDanmakuList(
        cid: Long,
        durationSeconds: Int = 0,
        credential: BilibiliCredential? = null,
        referer: String = BilibiliEndpoints.HOME,
    ): List<BiliDanmakuItem> = withContext(Dispatchers.IO) {
        if (cid <= 0L) return@withContext emptyList()
        val segmentCount = when {
            durationSeconds > 0 -> ((durationSeconds + 359) / 360).coerceIn(1, 40)
            else -> 12
        }
        val all = mutableListOf<BiliDanmakuItem>()
        for (segment in 1..segmentCount) {
            val parsed = runCatching {
                val bytes = getBytes(
                    url = BilibiliEndpoints.DANMAKU_SEG,
                    params = mapOf(
                        "type" to "1",
                        "oid" to cid.toString(),
                        "segment_index" to segment.toString(),
                    ),
                    credential = credential,
                    referer = referer,
                )
                BilibiliDanmakuParser.parseProtobufSeg(bytes)
            }.getOrDefault(emptyList())
            if (parsed.isEmpty()) break
            all += parsed
        }
        if (all.isNotEmpty()) {
            return@withContext all.sortedBy { it.timeMs }
        }
        runCatching {
            val bytes = getBytes(
                url = BilibiliEndpoints.DANMAKU_LIST,
                params = mapOf(
                    "oid" to cid.toString(),
                    "type" to "1",
                ),
                credential = credential,
                referer = referer,
            )
            BilibiliDanmakuParser.parseListSo(bytes)
        }.getOrElse {
            runCatching {
                val bytes = getBytes(
                    url = "${BilibiliEndpoints.DANMAKU_XML}/$cid.xml",
                    params = emptyMap(),
                    credential = credential,
                    referer = referer,
                )
                BilibiliDanmakuParser.parseListSo(bytes)
            }.getOrDefault(emptyList())
        }.sortedBy { it.timeMs }
    }

    suspend fun getUserNavnum(mid: Long, credential: BilibiliCredential? = null): Long =
        withContext(Dispatchers.IO) {
            runCatching {
                getJson(
                    url = BilibiliEndpoints.USER_NAVNUM,
                    params = mapOf("mid" to mid.toString()),
                    credential = credential,
                ).let(BilibiliJsonParser::parseUserNavnum)
            }.getOrDefault(0L)
        }

    suspend fun getUserRelation(
        mid: Long,
        credential: BilibiliCredential,
        referer: String = "https://space.bilibili.com/$mid",
    ): BiliAuthorRelation = withContext(Dispatchers.IO) {
        runCatching {
            getJson(
                url = BilibiliEndpoints.RELATION,
                params = mapOf("fid" to mid.toString()),
                credential = credential,
                referer = referer,
            ).let(BilibiliJsonParser::parseUserRelation)
        }.getOrDefault(BiliAuthorRelation())
    }

    suspend fun getRelationStat(
        mid: Long,
        credential: BilibiliCredential,
    ): BiliAuthorRelation = getUserRelation(mid, credential)

    suspend fun modifyFollow(
        mid: Long,
        follow: Boolean,
        credential: BilibiliCredential,
        referer: String = "https://space.bilibili.com/$mid",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (credential.biliJct.isBlank()) {
            return@withContext Result.failure(IllegalStateException("登录凭证无效，请重新登录"))
        }
        runCatching {
            postForm(
                url = BilibiliEndpoints.RELATION_MODIFY,
                form = mapOf(
                    "fid" to mid.toString(),
                    "act" to if (follow) "1" else "2",
                    "re_src" to "11",
                    "csrf" to credential.biliJct,
                ),
                credential = credential,
                referer = referer,
            )
            Unit
        }
    }

    suspend fun getUserRelationListPage(
        mid: Long,
        tab: UserRelationTab,
        page: Int,
        pageSize: Int = 20,
        credential: BilibiliCredential? = null,
    ): BiliRelationUserPage = withContext(Dispatchers.IO) {
        val url = when (tab) {
            UserRelationTab.Following -> BilibiliEndpoints.RELATION_FOLLOWINGS
            UserRelationTab.Followers -> BilibiliEndpoints.RELATION_FOLLOWERS
        }
        val params = buildMap {
            put("vmid", mid.toString())
            put("pn", page.toString())
            put("ps", pageSize.toString())
            if (tab == UserRelationTab.Following) {
                put("order_type", "")
            }
        }
        runCatching {
            getJson(
                url = url,
                params = params,
                credential = credential,
                referer = "https://space.bilibili.com/$mid",
            ).let { json -> BilibiliJsonParser.parseRelationUserPage(json, pageSize) }
        }.getOrElse { error ->
            BiliRelationUserPage(
                users = emptyList(),
                errorMessage = error.message ?: "加载失败",
            )
        }
    }

    suspend fun getVideoComments(
        aid: Long,
        sort: BiliCommentSort,
        paginationStr: String? = null,
        credential: BilibiliCredential? = null,
    ): BiliCommentPage = getSubjectComments(
        oid = aid,
        type = 1,
        sort = sort,
        paginationStr = paginationStr,
        credential = credential,
        referer = "https://www.bilibili.com/video/av$aid",
    )

    suspend fun getSubjectComments(
        oid: Long,
        type: Int,
        sort: BiliCommentSort,
        paginationStr: String? = null,
        credential: BilibiliCredential? = null,
        referer: String,
    ): BiliCommentPage = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "oid" to oid.toString(),
            "type" to type.toString(),
            "mode" to sort.mode.toString(),
            "plat" to "1",
            "web_location" to "1315875",
        )
        params["pagination_str"] = buildCommentPaginationStr(paginationStr)
        getWbiJsonOnCurrentThread(
            url = BilibiliEndpoints.VIDEO_COMMENTS,
            params = params,
            credential = credential,
            referer = referer,
        ).let(BilibiliJsonParser::parseCommentPage)
    }

    suspend fun getCommentReplies(
        oid: Long,
        type: Int,
        rootRpid: Long,
        referer: String,
        pn: Int = 1,
        ps: Int = 20,
        credential: BilibiliCredential? = null,
    ): BiliCommentReplyPage = withContext(Dispatchers.IO) {
        getJson(
            url = BilibiliEndpoints.VIDEO_COMMENT_REPLIES,
            params = mapOf(
                "type" to type.toString(),
                "oid" to oid.toString(),
                "root" to rootRpid.toString(),
                "pn" to pn.toString(),
                "ps" to ps.toString(),
                "web_location" to "1315875",
            ),
            credential = credential,
            referer = referer,
        ).let(BilibiliJsonParser::parseCommentReplyPage)
    }

    suspend fun getVideoCommentReplies(
        aid: Long,
        rootRpid: Long,
        bvid: String,
        pn: Int = 1,
        ps: Int = 20,
        credential: BilibiliCredential? = null,
    ): BiliCommentReplyPage = getCommentReplies(
        oid = aid,
        type = 1,
        rootRpid = rootRpid,
        referer = "https://www.bilibili.com/video/$bvid",
        pn = pn,
        ps = ps,
        credential = credential,
    )

    private fun buildCommentPaginationStr(nextOffset: String?): String =
        JSONObject().put("offset", nextOffset?.takeIf { it.isNotBlank() }.orEmpty()).toString()

    suspend fun reportWatchHistory(
        aid: Long,
        cid: Long,
        progressSeconds: Long,
        credential: BilibiliCredential,
    ): Boolean = withContext(Dispatchers.IO) {
        if (aid <= 0L || cid <= 0L) return@withContext false
        runCatching {
            postForm(
                url = BilibiliEndpoints.HISTORY_REPORT,
                form = buildMap {
                    put("aid", aid.toString())
                    put("cid", cid.toString())
                    put("progress", progressSeconds.coerceAtLeast(0L).toString())
                    put("platform", "android")
                    put("csrf", credential.biliJct)
                },
                credential = credential,
            )
            true
        }.getOrDefault(false)
    }

    private val videoShotCache = mutableMapOf<Long, BiliVideoShot?>()

    suspend fun getVideoShot(
        bvid: String,
        aid: Long,
        cid: Long,
        credential: BilibiliCredential? = null,
    ): BiliVideoShot? = withContext(Dispatchers.IO) {
        if (cid <= 0L) return@withContext null
        synchronized(videoShotCache) {
            if (cid in videoShotCache) return@withContext videoShotCache[cid]
        }
        val referer = if (bvid.isNotBlank()) {
            "https://www.bilibili.com/video/$bvid"
        } else {
            BilibiliEndpoints.HOME
        }
        suspend fun fetchShot(index: String?): BiliVideoShot? = runCatching {
            val params = buildMap {
                if (bvid.isNotBlank()) put("bvid", bvid)
                if (aid > 0L) put("aid", aid.toString())
                put("cid", cid.toString())
                if (index != null) put("index", index)
            }
            getJson(BilibiliEndpoints.VIDEO_SHOT, params, credential, referer = referer)
                .let(BilibiliJsonParser::parseVideoShot)
        }.getOrNull()
        val shot = fetchShot(index = "1") ?: fetchShot(index = null) ?: fetchShot(index = "2")
        synchronized(videoShotCache) {
            videoShotCache[cid] = shot
        }
        shot
    }

    suspend fun getPlayUrl(
        bvid: String,
        cid: Long,
        credential: BilibiliCredential? = null,
    ): BiliPlayStream? = withContext(Dispatchers.IO) {
        runCatching {
            val params = mutableMapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to "80",
                "fnval" to "4048",
                "fnver" to "0",
                "fourk" to "1",
                "otype" to "json",
                "platform" to "pc",
            )
            getWbiJsonOnCurrentThread(BilibiliEndpoints.VIDEO_PLAYURL, params, credential)
                .let(BilibiliJsonParser::parsePlayUrl)
        }.getOrNull()
    }

    suspend fun getMyInfo(credential: BilibiliCredential): BiliUserProfile? =
        withContext(Dispatchers.IO) {
            getJson(BilibiliEndpoints.MY_INFO, emptyMap(), credential)
                .let(BilibiliJsonParser::parseMyInfo)
        }

    suspend fun getUserWallet(credential: BilibiliCredential): BiliUserWallet? =
        withContext(Dispatchers.IO) {
            runCatching {
                getJson(BilibiliEndpoints.NAV, emptyMap(), credential)
                    .let(BilibiliJsonParser::parseUserWallet)
            }.getOrNull()
        }

    suspend fun getUserFollowerCount(mid: Long, credential: BilibiliCredential? = null): Long =
        withContext(Dispatchers.IO) {
            runCatching {
                getJson(
                    url = BilibiliEndpoints.USER_CARD,
                    params = mapOf("mid" to mid.toString(), "photo" to "false"),
                    credential = credential,
                    referer = "https://space.bilibili.com/$mid",
                ).let(BilibiliJsonParser::parseUserCardFollower)
            }.getOrDefault(0L)
        }

    suspend fun getUserInfo(mid: Long, credential: BilibiliCredential? = null): BiliUserProfile? =
        withContext(Dispatchers.IO) {
            val referer = "https://space.bilibili.com/$mid"
            val accProfile = runCatching {
                val params = mutableMapOf("mid" to mid.toString())
                WbiSigner.signWbi2(params)
                getWbiJsonOnCurrentThread(
                    url = BilibiliEndpoints.USER_INFO,
                    params = params,
                    credential = credential,
                    referer = referer,
                ).let(BilibiliJsonParser::parseUserInfo)
            }.getOrNull()
            val cardProfile = runCatching {
                getJson(
                    url = BilibiliEndpoints.USER_CARD,
                    params = mapOf("mid" to mid.toString(), "photo" to "true"),
                    credential = credential,
                    referer = referer,
                ).let(BilibiliJsonParser::parseUserCardProfile)
            }.getOrNull()
            val activeTopPhoto = accProfile?.topPhoto.orEmpty().ifBlank { cardProfile?.topPhoto.orEmpty() }
            val topPhotoList = runCatching {
                getSpaceAjaxJson(
                    url = BilibiliEndpoints.USER_TOP_PHOTO_LIST,
                    params = mapOf("mid" to mid.toString()),
                    credential = credential,
                    referer = referer,
                ).let { BilibiliJsonParser.parseUserTopPhotoList(it, activeTopPhoto) }
            }.getOrDefault(emptyList())
            val settingsTopPhoto = runCatching {
                getSpaceAjaxJson(
                    url = BilibiliEndpoints.USER_SPACE_SETTINGS,
                    params = mapOf("mid" to mid.toString()),
                    credential = credential,
                    referer = referer,
                ).let(BilibiliJsonParser::parseUserSpaceSettingsTopPhoto)
            }.getOrNull().orEmpty()
            val upstatLikes = runCatching {
                getJson(
                    url = BilibiliEndpoints.USER_UPSTAT,
                    params = mapOf("mid" to mid.toString()),
                    credential = credential,
                    referer = referer,
                ).let(BilibiliJsonParser::parseUserUpstatLikes)
            }.getOrDefault(0L)
            mergeUserProfiles(
                mid = mid,
                accProfile = accProfile,
                cardProfile = cardProfile,
                upstatLikes = upstatLikes,
                topPhotoList = topPhotoList,
                settingsTopPhoto = settingsTopPhoto,
            )
        }

    private fun mergeUserProfiles(
        mid: Long,
        accProfile: BiliUserProfile?,
        cardProfile: BiliUserProfile?,
        upstatLikes: Long,
        topPhotoList: List<String> = emptyList(),
        settingsTopPhoto: String = "",
    ): BiliUserProfile? {
        val base = accProfile ?: cardProfile ?: return null
        val card = cardProfile
        val mergedTopPhotos = BilibiliJsonParser.mergeUserTopPhotos(
            base.topPhotos,
            card?.topPhotos.orEmpty(),
            topPhotoList,
            listOfNotNull(settingsTopPhoto.takeIf { it.isNotBlank() }),
        )
        return base.copy(
            mid = mid,
            name = base.name.ifBlank { card?.name.orEmpty() },
            face = base.face.ifBlank { card?.face.orEmpty() },
            sign = base.sign.ifBlank { card?.sign.orEmpty() },
            level = base.level.takeIf { it > 0 } ?: card?.level ?: 0,
            following = base.following.takeIf { it > 0L } ?: card?.following ?: 0L,
            follower = base.follower.takeIf { it > 0L } ?: card?.follower ?: 0L,
            likes = base.likes.takeIf { it > 0L } ?: upstatLikes,
            topPhotos = mergedTopPhotos,
            topPhoto = mergedTopPhotos.firstOrNull()
                ?: base.topPhoto.ifBlank { card?.topPhoto.orEmpty() },
        )
    }

    suspend fun getUserVideos(
        mid: Long,
        pn: Int = 1,
        order: BiliUserVideoSort = BiliUserVideoSort.LatestPublish,
        credential: BilibiliCredential? = null,
    ): List<BiliVideoItem> = getUserVideoPage(mid, pn, order, credential).videos

    suspend fun getUserVideoPage(
        mid: Long,
        pn: Int = 1,
        order: BiliUserVideoSort = BiliUserVideoSort.LatestPublish,
        credential: BilibiliCredential? = null,
    ): BiliUserVideoPage = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "mid" to mid.toString(),
            "ps" to "30",
            "tid" to "0",
            "pn" to pn.toString(),
            "keyword" to "",
            "order" to order.order,
            "order_avoided" to "true",
            "platform" to "web",
        )
        WbiSigner.signWbi2(params)
        getWbiJsonOnCurrentThread(
            url = BilibiliEndpoints.USER_VIDEOS,
            params = params,
            credential = credential,
            referer = "https://space.bilibili.com/$mid",
        ).let(BilibiliJsonParser::parseUserVideoPage)
    }

    suspend fun getUserSpaceDynamics(
        mid: Long,
        offset: String? = null,
        credential: BilibiliCredential? = null,
    ): BiliDynamicFeedPage = withContext(Dispatchers.IO) {
        val params = buildMap {
            put("host_mid", mid.toString())
            put("timezone_offset", "-480")
            put("platform", "web")
            put("gaia_source", "main_web")
            put("features", BilibiliEndpoints.DYNAMIC_FEED_FEATURES)
            put("web_location", "333.1365")
            if (!offset.isNullOrBlank()) put("offset", offset)
        }
        getDynamicJson(
            url = BilibiliEndpoints.USER_SPACE_DYNAMIC,
            params = params,
            credential = credential,
            referer = "https://space.bilibili.com/$mid/dynamic",
        ).let(BilibiliJsonParser::parseSpaceDynamicFeed)
    }

    suspend fun exchangeAccessKey(credential: BilibiliCredential): BilibiliCredential =
        withContext(Dispatchers.IO) {
            if (credential.accessKey.isNotBlank()) return@withContext credential
            val updated = BiliAccessKeyExchange.exchange(credential) ?: return@withContext credential
            if (updated.accessKey.isNotBlank()) {
                onAccessKeyUpdated?.invoke(updated)
            }
            updated
        }

    suspend fun getArticle(
        cvId: Long,
        credential: BilibiliCredential? = null,
    ): BiliArticleDetail? = withContext(Dispatchers.IO) {
        if (cvId <= 0L) return@withContext null
        runCatching {
            getJson(
                url = BilibiliEndpoints.ARTICLE_VIEW,
                params = mapOf("id" to cvId.toString()),
                credential = credential,
                referer = "https://www.bilibili.com/read/cv$cvId",
            ).let(BilibiliJsonParser::parseArticleDetail)
        }.getOrNull()
    }

    suspend fun resolveArticleFromUrl(
        url: String,
        credential: BilibiliCredential? = null,
    ): BiliArticleDetail? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val resolvedUrl = resolveRedirectUrl(url)
        BiliArticleUrl.extractCvId(resolvedUrl)?.let { cvId ->
            return@withContext getArticle(cvId, credential)
        }
        val opusId = BiliArticleUrl.extractOpusId(resolvedUrl) ?: return@withContext null
        val cvId = runCatching {
            getJson(
                url = BilibiliEndpoints.OPUS_DETAIL,
                params = mapOf(
                    "id" to opusId.toString(),
                    "timezone_offset" to "-480",
                    "platform" to "web",
                    "features" to "htmlNewStyle,onlyfansAssetsV2,decorationCard",
                ),
                credential = credential,
                referer = "https://www.bilibili.com/opus/$opusId",
                extraHeaders = dynamicRequestHeaders(),
            ).let(BilibiliJsonParser::parseOpusArticleCvId)
        }.getOrNull() ?: return@withContext null
        getArticle(cvId, credential)
    }

    private suspend fun resolveRedirectUrl(url: String): String {
        if (!url.contains("b23.tv", ignoreCase = true)) return url
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", BilibiliEndpoints.USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        }.getOrDefault(url)
    }

    suspend fun getDynamicAuthorIpLocation(
        dynamicId: String,
        credential: BilibiliCredential?,
    ): String? = withContext(Dispatchers.IO) {
        if (dynamicId.isBlank() || credential == null) return@withContext null
        val resolvedCredential = exchangeAccessKey(credential)
        if (resolvedCredential.accessKey.isBlank()) return@withContext null
        BiliDynamicGrpcClient.fetchAuthorIpLocation(dynamicId, resolvedCredential)
    }

    suspend fun getDynamicDetail(
        dynamicId: String,
        credential: BilibiliCredential? = null,
    ): BiliDynamicItem? = withContext(Dispatchers.IO) {
        if (dynamicId.isBlank()) return@withContext null
        runCatching {
            val params = mutableMapOf(
                "id" to dynamicId,
                "timezone_offset" to "-480",
                "platform" to "web",
                "gaia_source" to "main_web",
                "features" to BilibiliEndpoints.DYNAMIC_DETAIL_FEATURES,
                "web_location" to "333.1368",
            )
            getWbiJsonOnCurrentThread(
                url = BilibiliEndpoints.DYNAMIC_DETAIL,
                params = params,
                credential = credential,
                referer = "https://t.bilibili.com/$dynamicId",
                extraHeaders = dynamicRequestHeaders(),
            ).let(BilibiliJsonParser::parseDynamicDetail)
        }.getOrNull()
    }

    suspend fun enrichDynamicIpLocations(
        items: List<BiliDynamicItem>,
        credential: BilibiliCredential? = null,
        webIpResolver: suspend (String) -> String? = { null },
    ): List<BiliDynamicItem> = withContext(Dispatchers.IO) {
        if (items.none { BilibiliJsonParser.normalizeIpLocation(it.ipLocation) == null }) {
            return@withContext items
        }
        val resolvedCredential = credential?.let { exchangeAccessKey(it) }
        val result = items.toMutableList()
        for ((index, item) in items.withIndex()) {
            val normalizedExisting = BilibiliJsonParser.normalizeIpLocation(item.ipLocation)
            if (normalizedExisting != null) {
                if (item.ipLocation != normalizedExisting) {
                    result[index] = result[index].copy(ipLocation = normalizedExisting)
                }
                continue
            }
            val ipLocation = runCatching {
                BilibiliJsonParser.normalizeIpLocation(
                    resolvedCredential?.let { getDynamicAuthorIpLocation(item.id, it) },
                )
                    ?: BilibiliJsonParser.normalizeIpLocation(
                        getDynamicDetail(item.id, resolvedCredential)?.ipLocation,
                    )
                    ?: BilibiliJsonParser.normalizeIpLocation(webIpResolver(item.id))
            }.getOrNull()
            if (ipLocation != null) {
                result[index] = result[index].copy(ipLocation = ipLocation)
            } else if (!item.ipLocation.isNullOrBlank()) {
                result[index] = result[index].copy(ipLocation = null)
            }
        }
        result
    }

    suspend fun searchVideos(
        keyword: String,
        page: Int = 1,
        credential: BilibiliCredential? = null,
    ): BiliSearchResultPage<BiliVideoItem> = withContext(Dispatchers.IO) {
        runCatching {
            getWbiJsonOnCurrentThread(
                url = BilibiliEndpoints.SEARCH_TYPE,
                params = mapOf(
                    "search_type" to "video",
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "order" to "totalrank",
                    "duration" to "0",
                    "tids" to "0",
                ),
                credential = credential,
                referer = "https://search.bilibili.com/video?keyword=${encode(keyword)}",
            ).let(BilibiliJsonParser::parseSearchVideoPage)
        }.getOrDefault(BiliSearchResultPage.empty())
    }

    suspend fun searchUsers(
        keyword: String,
        page: Int = 1,
        credential: BilibiliCredential? = null,
    ): BiliSearchResultPage<BiliSearchUserItem> = withContext(Dispatchers.IO) {
        runCatching {
            getWbiJsonOnCurrentThread(
                url = BilibiliEndpoints.SEARCH_TYPE,
                params = mapOf(
                    "search_type" to "bili_user",
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "order" to "0",
                    "user_type" to "0",
                ),
                credential = credential,
                referer = "https://search.bilibili.com/upuser?keyword=${encode(keyword)}",
            ).let(BilibiliJsonParser::parseSearchUserPage)
        }.getOrDefault(BiliSearchResultPage.empty())
    }

    suspend fun getSearchHotWords(limit: Int = 20): List<BiliHotSearchItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                getJson(
                    url = BilibiliEndpoints.SEARCH_HOTWORD,
                    params = mapOf("limit" to limit.toString()),
                    referer = "https://www.bilibili.com",
                ).let(BilibiliJsonParser::parseSearchHotWords)
            }.getOrDefault(emptyList())
        }

    suspend fun getSearchSuggest(term: String): List<String> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()
        runCatching {
            getJsonAllowNonZero(
                url = BilibiliEndpoints.SEARCH_SUGGEST,
                params = mapOf(
                    "term" to term,
                    "main_ver" to "v1",
                    "func" to "suggest",
                    "highlight" to "",
                    "suggest_type" to "accurate",
                    "sub_type" to "tag",
                    "userid" to "-1",
                ),
                referer = "https://search.bilibili.com",
            ).let(BilibiliJsonParser::parseSearchSuggest)
        }.getOrDefault(emptyList())
    }

    suspend fun getWatchHistory(
        credential: BilibiliCredential,
        max: Long = 0L,
        viewAt: Long = 0L,
        business: String = "",
        pageSize: Int = 20,
    ): BiliHistoryPage = withContext(Dispatchers.IO) {
        runCatching {
            getJson(
                url = BilibiliEndpoints.HISTORY_CURSOR,
                params = buildMap {
                    put("max", max.toString())
                    put("view_at", viewAt.toString())
                    put("business", business)
                    put("type", "archive")
                    put("ps", pageSize.coerceIn(1, 30).toString())
                },
                credential = credential,
                referer = "https://www.bilibili.com/account/history",
            ).let(BilibiliJsonParser::parseWatchHistoryPage)
        }.getOrDefault(BiliHistoryPage(emptyList(), null))
    }

    suspend fun deleteWatchHistory(
        kid: String,
        credential: BilibiliCredential,
    ): Boolean = withContext(Dispatchers.IO) {
        if (kid.isBlank() || credential.biliJct.isBlank()) return@withContext false
        runCatching {
            postForm(
                url = BilibiliEndpoints.HISTORY_DELETE,
                form = mapOf(
                    "kid" to kid,
                    "csrf" to credential.biliJct,
                ),
                credential = credential,
                referer = "https://www.bilibili.com/account/history",
            )
            true
        }.getOrDefault(false)
    }

    private suspend fun enrichLiveRoomDetails(rooms: List<BiliLiveRoom>): List<BiliLiveRoom> {
        if (rooms.isEmpty()) return rooms
        return coroutineScope {
            rooms.map { room ->
                async {
                    val needsCover = room.coverUrl.isBlank() || room.coverUrl.contains("/bfs/face/")
                    val needsArea = room.areaName.isBlank()
                    val needsPortrait = room.isPortrait == null
                    if (!needsCover && !needsArea && !needsPortrait) {
                        return@async room
                    }
                    runCatching {
                        val json = getJson(
                            url = BilibiliEndpoints.LIVE_ROOM_GET_INFO,
                            params = mapOf("room_id" to room.roomId.toString()),
                        )
                        val (cover, online, isPortrait) = BilibiliJsonParser.parseLiveRoomInfoBrief(json)
                        val areaName = BilibiliJsonParser.parseLiveRoomAreaDisplayName(json)
                            .ifBlank { room.areaName }
                        room.copy(
                            coverUrl = if (needsCover) cover.ifBlank { room.coverUrl } else room.coverUrl,
                            online = when {
                                room.online > 0L -> room.online
                                online > 0L -> online
                                else -> room.online
                            },
                            areaName = areaName,
                            isPortrait = room.isPortrait ?: isPortrait,
                        )
                    }.getOrDefault(room)
                }
            }.awaitAll()
        }
    }

    suspend fun getLiveAreaGroups(): List<BiliLiveAreaGroup> =
        withContext(Dispatchers.IO) {
            getJson(BilibiliEndpoints.LIVE_AREA_GET_LIST, emptyMap())
                .let(BilibiliJsonParser::parseLiveAreaGroups)
        }

    suspend fun getLiveRecommendList(
        credential: BilibiliCredential? = null,
    ): BiliLiveRoomPage =
        withContext(Dispatchers.IO) {
            val page = getJson(
                url = BilibiliEndpoints.LIVE_RECOMMEND_LIST,
                params = mapOf(
                    "platform" to "web",
                    "web_location" to "333.1007",
                ),
                credential = credential,
                referer = BilibiliEndpoints.LIVE_HOME,
            ).let(BilibiliJsonParser::parseLiveRecommendList)
            page.copy(rooms = enrichLiveRoomDetails(page.rooms))
        }

    suspend fun getLiveRoomList(
        parentAreaId: Long = 0L,
        areaId: Long = 0L,
        page: Int = 1,
        sortType: String = "",
        credential: BilibiliCredential? = null,
    ): BiliLiveRoomPage =
        withContext(Dispatchers.IO) {
            val params = mutableMapOf(
                "platform" to "web",
                "parent_area_id" to parentAreaId.toString(),
                "area_id" to areaId.toString(),
                "page" to page.toString(),
                "web_location" to "444.253",
            )
            if (sortType.isNotBlank()) {
                params["sort_type"] = sortType
            }
            BiliLiveWebIdResolver.resolve(client, parentAreaId, areaId)?.let { webId ->
                params["w_webid"] = webId
            }
            val referer =
                "${BilibiliEndpoints.LIVE_AREA_TAGS}?areaId=$areaId&parentAreaId=$parentAreaId"
            getWbiJsonOnCurrentThread(
                url = BilibiliEndpoints.LIVE_ROOM_LIST,
                params = params,
                credential = credential,
                referer = referer,
            ).let(BilibiliJsonParser::parseLiveRoomPage)
        }

    suspend fun getLiveFollowingRooms(
        credential: BilibiliCredential,
        needRecommend: Boolean = true,
    ): List<BiliLiveRoom> =
        withContext(Dispatchers.IO) {
            getJson(
                url = BilibiliEndpoints.LIVE_FOLLOWING,
                params = mapOf(
                    "need_recommend" to if (needRecommend) "1" else "0",
                    "filterRule" to "0",
                ),
                credential = credential,
                referer = BilibiliEndpoints.HOME,
            ).let(BilibiliJsonParser::parseLiveFollowing)
        }

    suspend fun getLiveAreaList(page: Int = 1): List<BiliLiveRoom> =
        getLiveRoomList(page = page).rooms

    suspend fun getLiveRoomDetail(
        roomId: Long,
        credential: BilibiliCredential? = null,
    ): BiliLiveRoomDetail? =
        withContext(Dispatchers.IO) {
            runCatching {
                getJson(
                    url = BilibiliEndpoints.LIVE_ROOM_INFO_BY_ROOM,
                    params = mapOf("room_id" to roomId.toString()),
                    credential = credential,
                    referer = liveRoomReferer(roomId),
                ).let(BilibiliJsonParser::parseLiveRoomDetail)
            }.getOrNull()
        }

    suspend fun getLiveOnlineGoldRank(
        roomId: Long,
        anchorUid: Long,
        credential: BilibiliCredential? = null,
    ): BiliLiveOnlineGoldRank =
        withContext(Dispatchers.IO) {
            if (anchorUid <= 0L) return@withContext BiliLiveOnlineGoldRank()
            runCatching {
                getJson(
                    url = BilibiliEndpoints.LIVE_ONLINE_GOLD_RANK,
                    params = mapOf(
                        "roomId" to roomId.toString(),
                        "ruid" to anchorUid.toString(),
                        "page" to "1",
                        "pageSize" to "3",
                    ),
                    credential = credential,
                    referer = liveRoomReferer(roomId),
                ).let(BilibiliJsonParser::parseLiveOnlineGoldRank)
            }.getOrDefault(BiliLiveOnlineGoldRank())
        }

    suspend fun getLivePlayInfo(
        roomId: Long,
        qn: Int = 10_000,
        credential: BilibiliCredential? = null,
    ): BiliLivePlayResult? =
        withContext(Dispatchers.IO) {
            runCatching {
                getJson(
                    url = BilibiliEndpoints.LIVE_ROOM_PLAY_INFO_V2,
                    params = mapOf(
                        "room_id" to roomId.toString(),
                        "platform" to "web",
                        "ptype" to "16",
                        "protocol" to "0,1",
                        "format" to "0,1,2",
                        "codec" to "0,1",
                        "qn" to qn.toString(),
                        "dolby" to "5",
                        "https_url_req" to "1",
                    ),
                    credential = credential,
                    referer = liveRoomReferer(roomId),
                ).let { json ->
                    BilibiliJsonParser.parseLivePlayInfo(json = json, qn = qn)
                }
            }.getOrNull()
        }

    suspend fun getLiveDanmuInfo(
        realRoomId: Long,
        credential: BilibiliCredential? = null,
    ): BiliLiveDanmuInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                getWbiJsonOnCurrentThread(
                    url = BilibiliEndpoints.LIVE_DANMU_INFO,
                    params = mapOf(
                        "id" to realRoomId.toString(),
                        "type" to "0",
                        "web_location" to "444.8",
                    ),
                    credential = credential,
                    referer = liveRoomReferer(realRoomId),
                ).let(BilibiliJsonParser::parseLiveDanmuInfo)
            }.getOrNull()
        }

    suspend fun sendLiveDanmaku(
        roomId: Long,
        message: String,
        credential: BilibiliCredential,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                postForm(
                    url = "https://api.live.bilibili.com/msg/send",
                    form = mapOf(
                        "roomid" to roomId.toString(),
                        "msg" to message,
                        "rnd" to (System.currentTimeMillis() / 1000L).toString(),
                        "color" to "16777215",
                        "fontsize" to "25",
                        "mode" to "1",
                        "bubble" to "0",
                        "csrf" to credential.biliJct,
                        "csrf_token" to credential.biliJct,
                    ),
                    credential = credential,
                    referer = liveRoomReferer(roomId),
                )
                true
            }.getOrDefault(false)
        }

    suspend fun getLiveRoomOnline(
        roomId: Long,
        credential: BilibiliCredential? = null,
    ): Long =
        withContext(Dispatchers.IO) {
            runCatching {
                getJson(
                    url = BilibiliEndpoints.LIVE_ROOM_GET_INFO,
                    params = mapOf("room_id" to roomId.toString()),
                    credential = credential,
                    referer = liveRoomReferer(roomId),
                ).let { BilibiliJsonParser.parseLiveRoomInfoBrief(it).second }
            }.getOrDefault(0L)
        }

    suspend fun sendLiveHeartbeat(realRoomId: Long, displayRoomId: Long) {
        withContext(Dispatchers.IO) {
            runCatching {
                val hb = java.util.Base64.getEncoder()
                    .encodeToString("60|$realRoomId|1|0".toByteArray())
                getJson(
                    url = BilibiliEndpoints.LIVE_HEARTBEAT_WEB,
                    params = mapOf("pf" to "web", "hb" to hb),
                    referer = liveRoomReferer(displayRoomId),
                )
            }
        }
    }

    private fun liveRoomReferer(roomId: Long): String =
        "${BilibiliEndpoints.LIVE_HOME}$roomId"

    private suspend fun getWbiJsonOnCurrentThread(
        url: String,
        params: Map<String, String>,
        credential: BilibiliCredential? = null,
        referer: String = BilibiliEndpoints.HOME,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JSONObject {
        val signed = WbiSigner.sign(params.toMutableMap(), ensureMixinKey(credential))
        return getJson(url, signed, credential, referer, extraHeaders)
    }

    private suspend fun ensureMixinKey(credential: BilibiliCredential?): String =
        wbiMutex.withLock {
            mixinKey?.let { return it }
            val nav = getJson(BilibiliEndpoints.NAV, emptyMap(), credential)
            val wbiImg = nav.optJSONObject("data")?.optJSONObject("wbi_img")
                ?: error("无法获取 WBI 密钥")
            val key = WbiSigner.deriveMixinKey(
                imgUrl = wbiImg.optString("img_url"),
                subUrl = wbiImg.optString("sub_url"),
            )
            mixinKey = key
            key
        }

    private suspend fun ensureGuestBuvid() {
        buvidMutex.withLock {
            if (!guestBuvid3.isNullOrBlank()) return@withLock
            runCatching {
                val request = Request.Builder()
                    .url(BilibiliEndpoints.BUVID_SPI)
                    .header("User-Agent", BilibiliEndpoints.USER_AGENT)
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@runCatching
                    val data = JSONObject(body).optJSONObject("data") ?: return@runCatching
                    guestBuvid3 = data.optString("b_3").takeIf { it.isNotBlank() }
                    guestBuvid4 = data.optString("b_4").takeIf { it.isNotBlank() }
                }
            }
        }
    }

    private suspend fun buildCookieHeader(credential: BilibiliCredential?): String? {
        ensureGuestBuvid()
        val parts = mutableListOf<String>()
        if (credential != null) {
            parts += credential.toCookieHeader()
            if (credential.buvid3.isBlank()) {
                guestBuvid3?.let { parts += "buvid3=$it" }
            }
            if (credential.buvid4.isBlank()) {
                guestBuvid4?.let { parts += "buvid4=$it" }
            }
        } else {
            guestBuvid3?.let { parts += "buvid3=$it" }
            guestBuvid4?.let { parts += "buvid4=$it" }
        }
        return parts.joinToString("; ").takeIf { it.isNotBlank() }
    }

    private suspend fun postForm(
        url: String,
        form: Map<String, String>,
        credential: BilibiliCredential,
        referer: String = BilibiliEndpoints.HOME,
    ): JSONObject {
        val body = form.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .header("User-Agent", BilibiliEndpoints.USER_AGENT)
            .header("Referer", referer)
            .header("Origin", "https://www.bilibili.com")
        buildCookieHeader(credential)?.let { cookie ->
            requestBuilder.header("Cookie", cookie)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val json = JSONObject(responseBody)
            if (json.optInt("code") != 0) {
                error(json.optString("message", "请求失败"))
            }
            return json
        }
    }

    private suspend fun getJsonAllowNonZero(
        url: String,
        params: Map<String, String>,
        credential: BilibiliCredential? = null,
        referer: String = BilibiliEndpoints.HOME,
    ): JSONObject {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val fullUrl = if (query.isBlank()) url else "$url?$query"
        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", BilibiliEndpoints.USER_AGENT)
            .header("Referer", referer)
        buildCookieHeader(credential)?.let { cookie ->
            requestBuilder.header("Cookie", cookie)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            return runCatching { JSONObject(body) }.getOrElse {
                error("响应解析失败")
            }
        }
    }

    private suspend fun getBytes(
        url: String,
        params: Map<String, String>,
        credential: BilibiliCredential? = null,
        referer: String = BilibiliEndpoints.HOME,
    ): ByteArray {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val fullUrl = if (query.isBlank()) url else "$url?$query"
        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", BilibiliEndpoints.USER_AGENT)
            .header("Referer", referer)
        buildCookieHeader(credential)?.let { cookie ->
            requestBuilder.header("Cookie", cookie)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            return body
        }
    }

    private suspend fun getSpaceAjaxJson(
        url: String,
        params: Map<String, String>,
        credential: BilibiliCredential? = null,
        referer: String,
    ): JSONObject {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val fullUrl = if (query.isBlank()) url else "$url?$query"
        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", BilibiliEndpoints.USER_AGENT)
            .header("Referer", referer)
        buildCookieHeader(credential)?.let { cookie ->
            requestBuilder.header("Cookie", cookie)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            val json = runCatching { JSONObject(body) }.getOrElse {
                error("响应解析失败")
            }
            if (!json.optBoolean("status")) {
                error(json.optString("data", "请求失败"))
            }
            return json
        }
    }

    private suspend fun getDynamicJson(
        url: String,
        params: Map<String, String>,
        credential: BilibiliCredential? = null,
        referer: String = BilibiliEndpoints.HOME,
    ): JSONObject = getJson(
        url = url,
        params = params,
        credential = credential,
        referer = referer,
        extraHeaders = dynamicRequestHeaders(),
    )

    private fun dynamicRequestHeaders(): Map<String, String> = mapOf(
        "x-bili-device-req-json" to """{"platform":"android","device":"phone","mobi_app":"android","build":8510300}""",
        "x-bili-web-req-json" to """{"spm_id":"333.1365"}""",
    )

    private suspend fun getJson(
        url: String,
        params: Map<String, String>,
        credential: BilibiliCredential? = null,
        referer: String = BilibiliEndpoints.HOME,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JSONObject {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val fullUrl = if (query.isBlank()) url else "$url?$query"
        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", BilibiliEndpoints.USER_AGENT)
            .header("Referer", referer)
        extraHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        buildCookieHeader(credential)?.let { cookie ->
            requestBuilder.header("Cookie", cookie)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    412 -> "请求被 B 站风控拦截，请稍后重试"
                    else -> "HTTP ${response.code}"
                }
                error(message)
            }
            val json = runCatching { JSONObject(body) }.getOrElse {
                error("响应解析失败")
            }
            if (json.optInt("code") != 0) {
                error(json.optString("message", "请求失败"))
            }
            return json
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    fun invalidateWbiCache() {
        mixinKey = null
    }
}
