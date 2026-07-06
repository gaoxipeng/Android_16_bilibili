package com.example.bilibili.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal object BiliLiveWebIdResolver {
    private val mutex = Mutex()
    private val accessIdPattern = Regex(""""access_id"\s*:\s*"([^"]+)"""")
    private var cachedWebId: String? = null
    private var expiresAtMs: Long = 0L

    suspend fun resolve(
        client: OkHttpClient,
        parentAreaId: Long = 0L,
        areaId: Long = 0L,
    ): String? = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!cachedWebId.isNullOrBlank() && now < expiresAtMs) {
            return cachedWebId
        }
        val fetched = fetchWebId(client, parentAreaId, areaId) ?: return null
        cachedWebId = fetched
        expiresAtMs = now + 3_600_000L
        fetched
    }

    private suspend fun fetchWebId(
        client: OkHttpClient,
        parentAreaId: Long,
        areaId: Long,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val pageUrl =
                "${BilibiliEndpoints.LIVE_AREA_TAGS}?areaId=$areaId&parentAreaId=$parentAreaId"
            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", BilibiliEndpoints.USER_AGENT)
                .header("Referer", BilibiliEndpoints.LIVE_HOME)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string().orEmpty()
                accessIdPattern.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }
}
