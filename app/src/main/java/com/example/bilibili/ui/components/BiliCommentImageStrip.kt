package com.example.bilibili.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.BiliCommentPicture

@Composable
fun BiliCommentImageStrip(
    pictures: List<BiliCommentPicture>,
    modifier: Modifier = Modifier,
    onOpenViewer: ((index: Int, bounds: Rect) -> Unit)? = null,
) {
    if (pictures.isEmpty()) return
    var boundsByIndex by remember(pictures) { mutableStateOf<Map<Int, Rect>>(emptyMap()) }
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
                    .onGloballyPositioned { coordinates ->
                        boundsByIndex = boundsByIndex + (index to coordinates.boundsInRoot())
                    }
                    .then(
                        if (onOpenViewer != null) {
                            Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    boundsByIndex[index]?.let { bounds ->
                                        onOpenViewer(index, bounds)
                                    }
                                },
                            )
                        } else {
                            Modifier
                        },
                    ),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
