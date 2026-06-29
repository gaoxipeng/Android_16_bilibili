package com.example.bilibili.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Stable
class UserProfileUiState(
    val mid: Long,
    seedName: String,
    seedFace: String,
) {
    var profile by mutableStateOf(
        BiliUserProfile(
            mid = mid,
            name = seedName,
            face = seedFace,
            sign = "",
            level = 0,
        ),
    )
    var relation by mutableStateOf(BiliAuthorRelation())
    var videos by mutableStateOf<List<BiliVideoItem>>(emptyList())
    var dynamics by mutableStateOf<List<BiliDynamicItem>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var loadError by mutableStateOf<String?>(null)
    var followLoading by mutableStateOf(false)

    var videosPage by mutableStateOf(1)
    var videosHasMore by mutableStateOf(true)
    var videosLoadingMore by mutableStateOf(false)
    var videosOrder by mutableStateOf(BiliUserVideoSort.LatestPublish)

    var dynamicsOffset by mutableStateOf<String?>(null)
    var dynamicsHasMore by mutableStateOf(true)
    var dynamicsLoadingMore by mutableStateOf(false)
    var dynamicsLoaded by mutableStateOf(false)

    var loaded by mutableStateOf(false)
    var profileHeaderHeight by mutableStateOf(0.dp)
}

object UserProfileSessionCache {
    private val cache = mutableMapOf<Long, UserProfileUiState>()

    fun getOrCreate(mid: Long, seedName: String, seedFace: String): UserProfileUiState {
        cache[mid]?.let { existing ->
            if (!existing.loaded) {
                if (seedName.isNotBlank()) {
                    existing.profile = existing.profile.copy(name = seedName)
                }
                if (seedFace.isNotBlank()) {
                    existing.profile = existing.profile.copy(face = seedFace)
                }
            }
            return existing
        }
        return UserProfileUiState(mid, seedName, seedFace).also { cache[mid] = it }
    }

    fun clear(mid: Long? = null) {
        if (mid == null) {
            cache.clear()
        } else {
            cache.remove(mid)
        }
    }
}
