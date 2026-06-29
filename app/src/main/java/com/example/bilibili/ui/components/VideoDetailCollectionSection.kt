package com.example.bilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.data.BiliUgcSeason
import com.example.bilibili.ui.theme.BiliPink

@Composable
fun VideoDetailUgcSeasonSection(
    season: BiliUgcSeason,
    onGroupClick: (sectionId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        season.displayGroups().forEach { group ->
            VideoDetailCollectionChip(
                title = group.title,
                countLabel = "${
                    if (group.sectionId == null) {
                        season.episodeCount
                    } else {
                        season.sections.firstOrNull { it.id == group.sectionId }?.episodes?.size
                            ?: season.episodeCount
                    }
                }个视频",
                onClick = { onGroupClick(group.sectionId) },
            )
        }
    }
}

@Composable
fun VideoDetailMultiPartSection(
    title: String,
    partCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VideoDetailCollectionChip(
        title = title,
        countLabel = "${partCount}P",
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun VideoDetailCollectionChip(
    title: String,
    countLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipBackground = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(chipBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "合集",
            color = BiliPink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = countLabel,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
