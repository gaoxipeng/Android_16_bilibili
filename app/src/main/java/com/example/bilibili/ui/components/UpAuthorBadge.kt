package com.example.bilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VideoFeedAuthorAvatar(
    faceUrl: String,
    authorName: String,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    if (faceUrl.isNotBlank()) {
        RemoteImage(
            url = faceUrl,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        return
    }

    val initial = authorName.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (initial.isNotEmpty()) {
            Text(
                text = initial,
                style = TextStyle(
                    fontSize = (size.value * 0.45f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = (size.value * 0.45f).sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }
    }
}
