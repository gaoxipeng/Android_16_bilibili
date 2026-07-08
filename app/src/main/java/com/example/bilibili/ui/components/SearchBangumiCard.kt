package com.example.bilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.data.BiliSearchBangumi
import com.example.bilibili.ui.theme.BiliPink

private val CoverBadgeCorner = 3.dp

@Composable
private fun VideoCoverCornerBadge(
    text: String,
    backgroundColor: Color,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 10.sp,
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(CoverBadgeCorner))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
fun SearchBangumiCard(
    bangumi: BiliSearchBangumi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VideoFeedCardCorner)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            RemoteImage(
                url = bangumi.coverUrl,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            )
            if (bangumi.categoryName.isNotBlank() || bangumi.badge.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (bangumi.categoryName.isNotBlank()) {
                        VideoCoverCornerBadge(
                            text = bangumi.categoryName,
                            backgroundColor = Color.Black.copy(alpha = 0.55f),
                        )
                    }
                    if (bangumi.badge.isNotBlank()) {
                        VideoCoverCornerBadge(
                            text = bangumi.badge,
                            backgroundColor = BiliPink.copy(alpha = 0.92f),
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = bangumi.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = bangumi.metadataLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
