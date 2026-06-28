package com.example.bilibili.ui.screens



import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import com.example.bilibili.data.BiliPlayStream

import com.example.bilibili.data.BiliVideoItem

import com.example.bilibili.data.FeedLayoutStore

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

    showSearchBar: Boolean = false,

    onSearchClick: () -> Unit = {},

    onSearchVisibleChange: (Boolean) -> Unit = {},

    pullRefreshState: PullToRefreshState = rememberPullToRefreshState(),

    showEmbeddedPullRefreshIndicator: Boolean = true,

    feedColumnCount: Int = FeedLayoutStore.COLUMN_COUNT_TWO,

) {

    HomeScreen(

        videos = videos,

        playUrls = playUrls,

        loading = loading,

        error = error,

        onRefresh = onRefresh,

        onPullRefresh = onRefresh,

        onVideoClick = onVideoClick,

        onEnsurePlayStream = onEnsurePlayStream,

        onAuthorClick = onAuthorClick,

        coordinator = coordinator,

        contentPadding = contentPadding,

        modifier = modifier,

        emptyHint = "暂无排行内容",

        showSearchBar = showSearchBar,

        onSearchClick = onSearchClick,

        onSearchVisibleChange = onSearchVisibleChange,

        pullRefreshState = pullRefreshState,

        showEmbeddedPullRefreshIndicator = showEmbeddedPullRefreshIndicator,

        feedColumnCount = feedColumnCount,

    )

}

