package com.example.bilibili.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.bilibili.data.BiliDynamicItem
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.UserRelationTab

sealed interface AppNavEntry {
    data object Search : AppNavEntry

    data class VideoDetail(
        val video: BiliVideoItem,
        val progressSeconds: Int = 0,
    ) : AppNavEntry

    data class UserProfile(
        val mid: Long,
        val name: String = "",
        val face: String = "",
    ) : AppNavEntry

    data class DynamicDetail(
        val item: BiliDynamicItem,
    ) : AppNavEntry

    data class ArticleDetail(
        val webUrl: String,
        val seedTitle: String = "",
    ) : AppNavEntry

    data class UserRelationList(
        val mid: Long,
        val name: String = "",
        val face: String = "",
        val sign: String = "",
        val initialTab: UserRelationTab,
    ) : AppNavEntry
}

internal fun AppNavEntry.stableKey(index: Int): String = when (this) {
    AppNavEntry.Search -> "search@$index"
    is AppNavEntry.VideoDetail -> {
        val identity = when {
            video.cid > 0L -> video.playbackId()
            video.aid > 0L -> "${video.bvid.ifBlank { "av:${video.aid}" }}:aid:${video.aid}"
            else -> video.playbackId()
        }
        "video:$identity@$index"
    }
    is AppNavEntry.UserProfile -> "profile:$mid@$index"
    is AppNavEntry.DynamicDetail -> "dynamic:${item.id}@$index"
    is AppNavEntry.ArticleDetail -> "article:${webUrl.hashCode()}@$index"
    is AppNavEntry.UserRelationList -> "relation-list:$mid@$index"
}

@Stable
class AppNavController(initial: List<AppNavEntry> = emptyList()) {
    var stack by mutableStateOf(initial)
        private set

    val top: AppNavEntry? get() = stack.lastOrNull()
    val hasOverlay: Boolean get() = stack.isNotEmpty()

    fun push(entry: AppNavEntry) {
        stack = when (entry) {
            AppNavEntry.Search -> if (stack.any { it is AppNavEntry.Search }) {
                stack
            } else {
                stack + entry
            }
            is AppNavEntry.VideoDetail -> when (stack.lastOrNull()) {
                is AppNavEntry.VideoDetail -> stack.dropLast(1) + entry
                else -> stack + entry
            }
            is AppNavEntry.UserProfile -> stack + entry
            is AppNavEntry.DynamicDetail -> stack + entry
            is AppNavEntry.ArticleDetail -> stack + entry
            is AppNavEntry.UserRelationList -> stack + entry
        }
    }

    fun pop(): AppNavEntry? {
        if (stack.isEmpty()) return null
        val removed = stack.last()
        stack = stack.dropLast(1)
        return removed
    }
}

fun List<AppNavEntry>.lastVideoDetail(): AppNavEntry.VideoDetail? =
    lastOrNull { it is AppNavEntry.VideoDetail } as? AppNavEntry.VideoDetail

fun List<AppNavEntry>.findVideoDetail(playbackId: String): BiliVideoItem? {
    lastOrNull { it is AppNavEntry.VideoDetail && it.video.playbackId() == playbackId }
        ?.let { return (it as AppNavEntry.VideoDetail).video }
    val bvid = playbackId.substringBefore(":cid:")
    return lastOrNull { it is AppNavEntry.VideoDetail && it.video.bvid == bvid }
        ?.let { (it as AppNavEntry.VideoDetail).video }
}
