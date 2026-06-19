package com.example.bilibili.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliUserProfile
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.components.VideoFeedCard

@Composable
fun MineScreen(
    loggedIn: Boolean,
    profile: BiliUserProfile?,
    videos: List<BiliVideoItem>,
    playUrls: Map<String, BiliPlayStream>,
    loading: Boolean,
    error: String?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onRefresh: () -> Unit,
    onVideoClick: (BiliVideoItem) -> Unit,
    coordinator: VideoPlaybackCoordinator,
    contentPadding: PaddingValues,
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
    ) {
        item {
            ProfileHeader(
                profile = profile,
                loading = loading,
                error = error,
                onRefresh = onRefresh,
                onLogoutClick = onLogoutClick,
            )
        }
        items(videos, key = { it.bvid }) { video ->
            VideoFeedCard(
                video = video,
                playStream = playUrls[video.bvid],
                coordinator = coordinator,
                onClick = { onVideoClick(video) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: BiliUserProfile?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (loading && profile == null) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        profile?.let { user ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteImage(
                    url = user.face,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(user.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "LV${user.level}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = user.sign.ifBlank { "这个人很神秘，什么都没有写" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ProfileStat("关注", user.following)
                ProfileStat("粉丝", user.follower)
                ProfileStat("获赞", user.likes)
            }
        }
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onRefresh) { Text("刷新") }
            TextButton(onClick = onLogoutClick) { Text("退出登录") }
        }
        Text(
            text = "我的投稿",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ProfileStat(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatCount(value), style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatCount(count: Long): String = when {
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> count.toString()
}
