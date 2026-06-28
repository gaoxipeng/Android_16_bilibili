package com.example.bilibili.ui.screens



import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Button

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.material3.pulltorefresh.PullToRefreshState

import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import com.example.bilibili.data.BiliPlayStream

import com.example.bilibili.data.BiliVideoItem

import com.example.bilibili.player.VideoPlaybackCoordinator



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun FollowingScreen(

    loggedIn: Boolean,

    videos: List<BiliVideoItem>,

    playUrls: Map<String, BiliPlayStream>,

    loading: Boolean,

    error: String?,

    onLoginClick: () -> Unit,

    onRefresh: () -> Unit,

    onPullRefresh: () -> Unit = onRefresh,

    onVideoClick: (BiliVideoItem) -> Unit,

    onEnsurePlayStream: (BiliVideoItem) -> Unit,

    onAuthorClick: (Long) -> Unit = {},

    coordinator: VideoPlaybackCoordinator,

    contentPadding: PaddingValues,

    modifier: Modifier = Modifier,

    loadingMore: Boolean = false,

    hasMore: Boolean = false,

    onLoadMore: () -> Unit = {},

    showSearchBar: Boolean = false,

    onSearchClick: () -> Unit = {},

    onSearchVisibleChange: (Boolean) -> Unit = {},

    pullRefreshState: PullToRefreshState = rememberPullToRefreshState(),

    showEmbeddedPullRefreshIndicator: Boolean = true,

) {

    if (!loggedIn) {

        Box(

            modifier = modifier

                .fillMaxSize()

                .padding(contentPadding),

            contentAlignment = Alignment.Center,

        ) {

            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Text("登录后查看关注 UP 的最新视频", style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(12.dp))

                Button(onClick = onLoginClick) {

                    Text("打开哔哩哔哩登录")

                }

            }

        }

        return

    }



    HomeScreen(

        videos = videos,

        playUrls = playUrls,

        loading = loading,

        loadingMore = loadingMore,

        hasMore = hasMore,

        error = error,

        onRefresh = onRefresh,

        onPullRefresh = onPullRefresh,

        onLoadMore = onLoadMore,

        onVideoClick = onVideoClick,

        onEnsurePlayStream = onEnsurePlayStream,

        onAuthorClick = onAuthorClick,

        coordinator = coordinator,

        contentPadding = contentPadding,

        modifier = modifier,

        emptyHint = "暂无关注视频，先去关注几个 UP 主吧",

        showSearchBar = showSearchBar,

        onSearchClick = onSearchClick,

        onSearchVisibleChange = onSearchVisibleChange,

        pullRefreshState = pullRefreshState,

        showEmbeddedPullRefreshIndicator = showEmbeddedPullRefreshIndicator,

    )

}

