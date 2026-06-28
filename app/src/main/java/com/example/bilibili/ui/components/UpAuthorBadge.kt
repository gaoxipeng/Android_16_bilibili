package com.example.bilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UpAuthorBadge(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .size(width = 18.dp, height = 16.dp)
            .background(
                color.copy(alpha = 0.14f),
                RoundedCornerShape(3.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "UP",
            style = TextStyle(
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                lineHeight = 8.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}
