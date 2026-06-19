package com.example.bilibili.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.player.VideoPlaybackCoordinator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotScreen(
    videos: List<BiliVideoItem>,
    playUrls: Map<String, BiliPlayStream>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onVideoClick: (BiliVideoItem) -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
    onAuthorClick: (Long) -> Unit = {},
    coordinator: VideoPlaybackCoordinator,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    HomeScreen(
        videos = videos,
        playUrls = playUrls,
        loading = loading,
        error = error,
        onRefresh = onRefresh,
        onVideoClick = onVideoClick,
        onEnsurePlayStream = onEnsurePlayStream,
        onAuthorClick = onAuthorClick,
        coordinator = coordinator,
        contentPadding = contentPadding,
        modifier = modifier,
        emptyHint = "暂无排行内容",
    )
}
