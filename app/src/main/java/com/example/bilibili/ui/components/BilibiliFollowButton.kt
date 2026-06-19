package com.example.bilibili.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.ui.theme.BiliPink

@Composable
fun BilibiliFollowButton(
    following: Boolean,
    followerMe: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val mutual = following && followerMe
    val label = when {
        compact && !following -> "+关注"
        compact && mutual -> "互关"
        compact && following -> "已关注"
        !following -> "+关注"
        mutual -> "互相关注"
        else -> "已关注"
    }
    Surface(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (following) Color(0xFFF0F0F0) else BiliPink,
        border = if (following) BorderStroke(1.dp, Color(0xFFD9D9D9)) else null,
    ) {
        Box(
            modifier = Modifier
                .padding(
                    horizontal = if (mutual) 10.dp else 14.dp,
                    vertical = 6.dp,
                )
                .heightIn(min = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = if (following) BiliPink else Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = if (mutual) 12.sp else MaterialTheme.typography.labelMedium.fontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = if (following) Color(0xFF636363) else Color.White,
                )
            }
        }
    }
}
