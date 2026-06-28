package com.example.bilibili.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.player.InlineVideoCard
import com.example.bilibili.player.LocalVideoPeekController
import com.example.bilibili.player.VideoPlaybackCoordinator
import com.example.bilibili.player.videoPlaybackKey
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

internal val VideoFeedCardCorner = 8.dp
private val VideoFeedMetaPadding = 8.dp
private val VideoFeedCoverOverlayPadding = 6.dp
private val VideoFeedCoverOverlayColor = Color.White
private val VideoFeedCoverOverlayIconSize = 10.dp
private val VideoFeedCoverOverlayTextStyle
    @Composable get() = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
private val VideoFeedCoverScrimHeightFraction = 0.45f
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
        VideoFeedGridCard(
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
        onClick = onClick,
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
                        .clip(RoundedCornerShape(topStart = corner, topEnd = corner)),
                ) {
                    RemoteImage(
                        url = video.coverUrl,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    CoverBottomScrim(
                        tint = rememberCoverAverageColor(video.coverUrl),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(VideoFeedCoverScrimHeightFraction)
                            .align(Alignment.BottomCenter),
                    )
                    CoverOverlayText(
                        text = formatDuration(video.durationSeconds),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(VideoFeedCoverOverlayPadding),
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
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
                    Text(
                        text = video.authorName,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoFeedGridCard(
    video: BiliVideoItem,
    playStream: BiliPlayStream?,
    coordinator: VideoPlaybackCoordinator,
    onClick: () -> Unit,
    onEnsurePlayStream: (() -> Unit)?,
    onAuthorClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(VideoFeedCardCorner)
    val cardBackdrop = rememberLayerBackdrop()
    val coverTint = rememberCoverAverageColor(video.coverUrl)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val metaBackground = remember(coverTint, surfaceColor) {
        coverTint.softenedMetaBackground(surfaceColor)
    }
    val metaTextColor = remember(metaBackground) { metaBackground.contrastingOverlayTextColor() }
    val playbackKey = videoPlaybackKey(video.bvid)
    val videoPeekController = LocalVideoPeekController.current
    val isVideoPeekActive = videoPeekController.activeRequest?.video?.bvid == video.bvid
    val showCoverOverlay = !isVideoPeekActive &&
        coordinator.fullscreenKey != playbackKey &&
        coordinator.peekPlaybackKey != playbackKey

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                InlineVideoCard(
                    video = video,
                    playStream = playStream,
                    coordinator = coordinator,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = VideoFeedCardCorner,
                    sharedBackdrop = cardBackdrop,
                    roundAllCorners = false,
                    showDurationBadge = false,
                    enforceAspectRatio = false,
                    onEnsurePlayStream = onEnsurePlayStream,
                    currentPlayStream = { playStream },
                    onCardClick = onClick,
                )

                if (showCoverOverlay) {
                    CoverBottomScrim(
                        tint = coverTint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(VideoFeedCoverScrimHeightFraction)
                            .align(Alignment.BottomCenter),
                    )
                    CoverVideoStatsRow(
                        viewCount = video.viewCount,
                        danmakuCount = video.danmakuCount,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(VideoFeedCoverOverlayPadding),
                    )
                    CoverOverlayText(
                        text = formatDuration(video.durationSeconds),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(VideoFeedCoverOverlayPadding),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(metaBackground)
                    .padding(VideoFeedMetaPadding),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = metaTextColor,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clickable(enabled = video.authorMid > 0L) {
                            if (onAuthorClick != null) {
                                onAuthorClick(video.authorMid)
                            } else {
                                openAuthorSpace(context, video.authorMid)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UpAuthorBadge(color = VideoOverlayAuthorColor)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = video.authorName,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelMedium,
                        color = VideoOverlayAuthorColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverVideoStatsRow(
    viewCount: Long,
    danmakuCount: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoverOverlayStat(
            icon = BiliVideoPlayCountIcon,
            value = formatCount(viewCount),
        )
        CoverOverlayStat(
            icon = BiliVideoDanmakuCountIcon,
            value = formatCount(danmakuCount),
        )
    }
}

@Composable
private fun CoverOverlayStat(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(VideoFeedCoverOverlayIconSize),
            tint = VideoFeedCoverOverlayColor,
        )
        CoverOverlayText(text = value)
    }
}

@Composable
private fun CoverBottomScrim(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val scrimColor = remember(tint) { tint.coverBottomScrimColor() }
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.45f to scrimColor.copy(alpha = 0.28f),
                    1f to scrimColor.copy(alpha = 0.82f),
                ),
            ),
        ),
    )
}

@Composable
private fun CoverOverlayText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = VideoFeedCoverOverlayColor,
        style = VideoFeedCoverOverlayTextStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun Color.coverBottomScrimColor(): Color {
    val darken = 0.26f
    return Color(
        red = red * darken + 0.03f,
        green = green * darken + 0.03f,
        blue = blue * darken + 0.03f,
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
