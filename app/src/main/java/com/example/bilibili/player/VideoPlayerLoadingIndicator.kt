package com.example.bilibili.player

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val VideoPlayerLoadingIndicatorSize: Dp = 28.dp
internal val VideoPlayerLoadingIndicatorStrokeWidth: Dp = 2.dp

@Composable
internal fun VideoPlayerLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    CircularProgressIndicator(
        color = Color.White,
        modifier = modifier.size(VideoPlayerLoadingIndicatorSize),
        strokeWidth = VideoPlayerLoadingIndicatorStrokeWidth,
    )
}
