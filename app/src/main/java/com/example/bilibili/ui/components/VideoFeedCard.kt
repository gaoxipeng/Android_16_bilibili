package com.example.bilibili.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.player.InlineVideoCard
import com.example.bilibili.player.LocalVideoPeekController
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.player.videoPlaybackKey
import com.example.bilibili.ui.liquidglass.TintedLiquidCapsule
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private val VideoOverlayLiquidBlurRadius = 14.dp
private val VideoOverlayAuthorColor = Color(0xFFFFA640)

@Composable
fun VideoFeedCard(
    video: BiliVideoItem,
    playStream: BiliPlayStream?,
    coordinator: VideoPlaybackCoordinator,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    overlayMetaOnCover: Boolean = false,
    onEnsurePlayStream: (() -> Unit)? = null,
    onAuthorClick: ((Long) -> Unit)? = null,
) {
    if (overlayMetaOnCover) {
        VideoFeedOverlayCard(
            video = video,
            playStream = playStream,
            coordinator = coordinator,
            onClick = onClick,
            onEnsurePlayStream = onEnsurePlayStream,
            onAuthorClick = onAuthorClick,
            modifier = modifier,
        )
        return
    }

    val corner = if (compact) 10.dp else 16.dp
    val contentPadding = if (compact) 8.dp else 12.dp
    val titleStyle = if (compact) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.titleMedium
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            if (playStream != null) {
                InlineVideoCard(
                    video = video,
                    playStream = playStream,
                    coordinator = coordinator,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = corner,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
                        .clickable(onClick = onClick),
                ) {
                    RemoteImage(
                        url = video.coverUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                    DurationBadge(
                        seconds = video.durationSeconds,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    )
                }
            }
            Column(
                Modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = contentPadding, vertical = contentPadding),
            ) {
                Text(
                    text = video.title,
                    style = titleStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (compact) {
                    Text(
                        text = video.authorName,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatCount(video.viewCount)}播放",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = video.authorName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${formatCount(video.viewCount)}播放 · ${formatCount(video.danmakuCount)}弹幕",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (video.description.isNotBlank()) {
                        Text(
                            text = video.description,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoFeedOverlayCard(
    video: BiliVideoItem,
    playStream: BiliPlayStream?,
    coordinator: VideoPlaybackCoordinator,
    onClick: () -> Unit,
    onEnsurePlayStream: (() -> Unit)?,
    onAuthorClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val corner = 14.dp
    val shape = RoundedCornerShape(corner)
    val cardBackdrop = rememberLayerBackdrop()
    val coverTint = rememberCoverAverageColor(video.coverUrl)
    val overlayTextColor = remember(coverTint) { coverTint.contrastingOverlayTextColor() }
    val overlaySubtextColor = overlayTextColor.copy(alpha = 0.88f)
    val playbackKey = videoPlaybackKey(video.bvid)
    val videoPeekController = LocalVideoPeekController.current
    val isVideoPeekActive = videoPeekController.activeRequest?.video?.bvid == video.bvid
    val showMetaOverlay = !isVideoPeekActive &&
        coordinator.activeKey != playbackKey &&
        coordinator.fullscreenKey != playbackKey &&
        coordinator.peekPlaybackKey != playbackKey

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape),
    ) {
        InlineVideoCard(
            video = video,
            playStream = playStream,
            coordinator = coordinator,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = corner,
            sharedBackdrop = cardBackdrop,
            roundAllCorners = true,
            showDurationBadge = false,
            enforceAspectRatio = false,
            onEnsurePlayStream = onEnsurePlayStream,
            currentPlayStream = { playStream },
            onCardClick = onClick,
        )

        AnimatedVisibility(
            visible = showMetaOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
        ) {
            DurationLiquidCapsule(
                seconds = video.durationSeconds,
                backdrop = cardBackdrop,
                tint = coverTint,
                textColor = overlayTextColor,
            )
        }

        AnimatedVisibility(
            visible = showMetaOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            VideoMetaLiquidCapsule(
                backdrop = cardBackdrop,
                tint = coverTint,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = video.title,
                    color = overlayTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = video.authorName,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = video.authorMid > 0L) {
                                if (onAuthorClick != null) {
                                    onAuthorClick(video.authorMid)
                                } else {
                                    openAuthorSpace(context, video.authorMid)
                                }
                            },
                        color = VideoOverlayAuthorColor,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatCount(video.viewCount)}播放",
                        modifier = Modifier.padding(start = 8.dp),
                        color = overlaySubtextColor,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationLiquidCapsule(
    seconds: Int,
    backdrop: Backdrop,
    tint: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    TintedLiquidCapsule(
        modifier = modifier,
        backdrop = backdrop,
        tint = tint,
        pill = true,
        blurRadius = VideoOverlayLiquidBlurRadius,
    ) {
        Text(
            text = formatDuration(seconds),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun VideoMetaLiquidCapsule(
    backdrop: Backdrop,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    TintedLiquidCapsule(
        modifier = modifier,
        backdrop = backdrop,
        tint = tint,
        cornerRadius = 14.dp,
        blurRadius = VideoOverlayLiquidBlurRadius,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            content = content,
        )
    }
}

@Composable
private fun DurationBadge(seconds: Int, modifier: Modifier = Modifier) {
    Text(
        text = formatDuration(seconds),
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
    )
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remain = seconds % 60
    return "%d:%02d".format(minutes, remain)
}

private fun formatCount(count: Long): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> count.toString()
}

private fun openAuthorSpace(context: android.content.Context, authorMid: Long) {
    if (authorMid <= 0L) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://space.bilibili.com/$authorMid"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
