package com.example.bilibili.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem
import com.example.bilibili.ui.components.RemoteImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun InlineVideoCard(
    video: BiliVideoItem,
    playStream: BiliPlayStream?,
    coordinator: VideoPlaybackCoordinator,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    sharedBackdrop: LayerBackdrop? = null,
    roundAllCorners: Boolean = false,
    showDurationBadge: Boolean = true,
    enforceAspectRatio: Boolean = true,
    onEnsurePlayStream: (() -> Unit)? = null,
    currentPlayStream: () -> BiliPlayStream? = { playStream },
    onCardClick: (() -> Unit)? = null,
) {
    val playbackKey = videoPlaybackKey(video.playbackId())
    val inlinePlaying = coordinator.activeKey == playbackKey &&
        coordinator.fullscreenKey != playbackKey
    val coverBackdrop = sharedBackdrop ?: rememberLayerBackdrop()
    val clipShape = if (roundAllCorners) {
        RoundedCornerShape(cornerRadius)
    } else {
        RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
    }
    val ensurePlayStreamState = rememberUpdatedState(onEnsurePlayStream)
    val onCardClickState = rememberUpdatedState(onCardClick)

    Box(
        modifier = modifier
            .then(
                if (enforceAspectRatio) {
                    Modifier.aspectRatio(16f / 9f)
                } else {
                    Modifier.fillMaxSize()
                },
            )
            .clip(clipShape)
            .background(Color.Black)
            .then(
                if (!inlinePlaying && onCardClick != null) {
                    Modifier.clickable {
                        ensurePlayStreamState.value?.invoke()
                        onCardClickState.value?.invoke()
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        if (inlinePlaying && playStream != null) {
            BilibiliVideoSurface(
                playbackKey = playbackKey,
                stream = playStream,
                isFullscreen = false,
                coordinator = coordinator,
                backdrop = coverBackdrop,
                onFullscreen = {
                    coordinator.openFullscreen(
                        playbackKey,
                        portraitVideo = knownPortraitVideoHint(
                            video.videoWidth,
                            video.videoHeight,
                        ),
                        video = video,
                        stream = playStream,
                    )
                },
                onCloseFullscreen = { coordinator.closeFullscreen() },
                modifier = Modifier.fillMaxSize(),
                portraitVideo = video.isPortraitVideo,
                scrubPreviewAspectRatio = knownVideoAspectRatio(video.videoWidth, video.videoHeight),
                playbackMetadata = VideoPlaybackMetadata.fromVideo(video),
                historyVideo = video,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(coverBackdrop),
            ) {
                RemoteImage(
                    url = video.coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (showDurationBadge) {
                Text(
                    text = formatDuration(video.durationSeconds),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remain = seconds % 60
    return "%d:%02d".format(minutes, remain)
}
