package com.example.bilibili.data

import android.content.Context
import org.json.JSONObject
import java.io.File

class UserProfileSnapshotStore(context: Context) {
    private val file = File(context.filesDir, "user_profile_snapshots.json")

    @Synchronized
    fun read(mid: Long): BiliUserProfile? {
        if (mid <= 0L || !file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            root.optJSONObject(mid.toString())?.toUserProfile()
        }.getOrNull()
    }

    @Synchronized
    fun write(profile: BiliUserProfile) {
        if (profile.mid <= 0L || !hasMeaningfulProfile(profile)) return
        val root = runCatching {
            if (file.exists()) {
                JSONObject(file.readText(Charsets.UTF_8))
            } else {
                JSONObject()
            }
        }.getOrElse { JSONObject() }
        root.put(profile.mid.toString(), profile.toJson())
        file.parentFile?.mkdirs()
        file.writeText(root.toString(), Charsets.UTF_8)
    }

    private fun hasMeaningfulProfile(profile: BiliUserProfile): Boolean =
        profile.following > 0L ||
            profile.follower > 0L ||
            profile.likes > 0L ||
            profile.videoCount > 0L ||
            profile.level > 0

    private fun BiliUserProfile.toJson(): JSONObject =
        JSONObject()
            .put("mid", mid)
            .put("name", name)
            .put("face", face)
            .put("sign", sign)
            .put("level", level)
            .put("following", following)
            .put("follower", follower)
            .put("likes", likes)
            .put("video_count", videoCount)
            .put("top_photo", topPhoto)
            .put("ip_location", ipLocation ?: "")

    private fun JSONObject.toUserProfile(): BiliUserProfile {
        val mid = optLong("mid")
        return BiliUserProfile(
            mid = mid,
            name = optString("name"),
            face = optString("face"),
            sign = optString("sign"),
            level = optInt("level"),
            following = optLong("following"),
            follower = optLong("follower"),
            likes = optLong("likes"),
            videoCount = optLong("video_count"),
            topPhoto = optString("top_photo"),
            ipLocation = optString("ip_location").takeIf { it.isNotBlank() },
        )
    }
}
