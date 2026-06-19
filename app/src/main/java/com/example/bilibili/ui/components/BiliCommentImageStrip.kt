package com.example.bilibili.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliCommentPicture

@Composable
fun BiliCommentImageStrip(
    pictures: List<BiliCommentPicture>,
    modifier: Modifier = Modifier,
    onImageClick: ((Int) -> Unit)? = null,
) {
    if (pictures.isEmpty()) return
    Row(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        pictures.forEachIndexed { index, picture ->
            RemoteImage(
                url = picture.url,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (onImageClick != null) {
                            Modifier.clickable { onImageClick(index) }
                        } else {
                            Modifier
                        },
                    ),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
