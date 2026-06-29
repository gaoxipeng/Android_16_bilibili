package com.example.bilibili.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.data.BilibiliApiClient
import com.example.bilibili.data.BilibiliCredential
import com.example.bilibili.data.FeedLayoutStore
import com.example.bilibili.data.UserRelationTab
import com.example.bilibili.player.VideoPlaybackCoordinator

@Composable
fun MineScreen(
    loggedIn: Boolean,
    mid: Long,
    seedName: String,
    seedFace: String,
    api: BilibiliApiClient,
    credential: BilibiliCredential?,
    playUrls: Map<String, BiliPlayStream>,
    coordinator: VideoPlaybackCoordinator,
    onLoginClick: () -> Unit,
    onVideoClick: (BiliVideoItem) -> Unit,
    onEnsurePlayStream: (BiliVideoItem) -> Unit,
    onLoginRequired: () -> Unit,
    onOpenRelationList: (Long, String, String, String, UserRelationTab) -> Unit = { _, _, _, _, _ -> },
    contentPadding: PaddingValues,
    feedColumnCount: Int = FeedLayoutStore.COLUMN_COUNT_TWO,
    onFeedColumnCountChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!loggedIn) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("登录后查看个人主页", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onLoginClick) {
                    Text("打开哔哩哔哩登录")
                }
            }
        }
        return
    }

    UserProfileScreen(
        mid = mid,
        seedName = seedName,
        seedFace = seedFace,
        api = api,
        credential = credential,
        myMid = mid,
        playUrls = playUrls,
        coordinator = coordinator,
        onVideoClick = onVideoClick,
        onEnsurePlayStream = onEnsurePlayStream,
        onLoginRequired = onLoginRequired,
        onOpenRelationList = { name, face, sign, tab ->
            onOpenRelationList(mid, name, face, sign, tab)
        },
        contentPadding = contentPadding,
        enableSettings = true,
        cacheProfile = true,
        feedColumnCount = feedColumnCount,
        onFeedColumnCountChange = onFeedColumnCountChange,
        modifier = modifier,
    )
}
