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
    var wallet by mutableStateOf<BiliUserWallet?>(null)
}

object UserProfileSessionCache {
    private val cache = mutableMapOf<Long, UserProfileUiState>()
    private val profileSnapshots = mutableMapOf<Long, BiliUserProfile>()
    private var walletSnapshot: BiliUserWallet? = null
    private var walletSnapshotMid: Long = 0L

    fun hasProfileSnapshot(mid: Long): Boolean =
        profileSnapshots[mid]?.let(::hasMeaningfulProfile) == true

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

        val state = UserProfileUiState(mid, seedName, seedFace)
        profileSnapshots[mid]?.let { snapshot ->
            state.profile = snapshot
            state.loaded = true
            state.loading = false
        }
        if (walletSnapshotMid == mid) {
            state.wallet = walletSnapshot
        }
        cache[mid] = state
        return state
    }

    fun updateProfileSnapshot(mid: Long, profile: BiliUserProfile) {
        if (!hasMeaningfulProfile(profile)) return
        profileSnapshots[mid] = profile
        cache[mid]?.let {
            it.profile = profile
            it.loaded = true
            it.loading = false
        }
    }

    fun updateWalletSnapshot(mid: Long, wallet: BiliUserWallet?) {
        walletSnapshotMid = mid
        walletSnapshot = wallet
        cache[mid]?.wallet = wallet
    }

    fun clear(mid: Long? = null) {
        if (mid == null) {
            cache.clear()
            profileSnapshots.clear()
            walletSnapshot = null
            walletSnapshotMid = 0L
        } else {
            cache.remove(mid)
            profileSnapshots.remove(mid)
            if (walletSnapshotMid == mid) {
                walletSnapshot = null
                walletSnapshotMid = 0L
            }
        }
    }

    private fun hasMeaningfulProfile(profile: BiliUserProfile): Boolean =
        profile.following > 0L ||
            profile.follower > 0L ||
            profile.likes > 0L ||
            profile.videoCount > 0L ||
            profile.level > 0
}
