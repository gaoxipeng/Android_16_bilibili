package com.example.bilibili.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.example.bilibili.ui.components.ActionFrostedCard
import com.example.bilibili.ui.components.actionMenuSurfaceColor
import com.example.bilibili.ui.components.ImageActionMenuBlurRadius
import com.example.bilibili.ui.theme.BiliPink
import com.example.bilibili.ui.theme.isAppLightTheme
import com.kyant.backdrop.Backdrop

private val EpisodePickerPanelPadding = 10.dp
private val EpisodePickerRowHeight = 46.dp
private val EpisodePickerMaxListHeight = 320.dp

@Composable
private fun episodePickerDetailColor(selected: Boolean): Color {
    if (selected) return BiliPink.copy(alpha = 0.85f)
    return if (isAppLightTheme()) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    }
}

@Composable
fun FullscreenEpisodePickerOverlay(
    visible: Boolean,
    pickerState: VideoEpisodePickerState?,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    if (!visible || pickerState == null) return

    BackHandler(onBack = onDismiss)

    val onDismissState = rememberUpdatedState(onDismiss)
    val listState = rememberLazyListState()
    val selectedIndex = pickerState.entries.indexOfFirst { it.selected }.coerceAtLeast(0)

    LaunchedEffect(pickerState.entries, selectedIndex) {
        if (pickerState.entries.isNotEmpty()) {
            listState.scrollToItem(selectedIndex.coerceIn(0, pickerState.entries.lastIndex))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var moved = false
                        val slop = viewConfiguration.touchSlop
                        val start = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.positionChange() != Offset.Zero) {
                                if (!moved) {
                                    moved = (change.position - start).getDistance() > slop
                                }
                                change.consume()
                            }
                            if (event.changes.all { !it.pressed }) {
                                if (!moved) {
                                    onDismissState.value()
                                }
                                break
                            }
                        }
                    }
                },
        )
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
                .zIndex(1f),
        ) {
            ActionFrostedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.92f),
                backdrop = backdrop,
                effectBlurRadius = ImageActionMenuBlurRadius,
                effectContainerColor = actionMenuSurfaceColor(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(EpisodePickerPanelPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = pickerState.sheetTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = EpisodePickerMaxListHeight),
                    ) {
                        items(
                            items = pickerState.entries,
                            key = { it.index },
                        ) { entry ->
                            EpisodePickerRow(
                                entry = entry,
                                onClick = {
                                    pickerState.onEntrySelected(entry.index)
                                    onDismissState.value()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodePickerRow(
    entry: VideoEpisodePickerEntry,
    onClick: () -> Unit,
) {
    val titleColor = if (entry.selected) BiliPink else MaterialTheme.colorScheme.onSurface
    val indexBackground = if (entry.selected) {
        BiliPink.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EpisodePickerRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 26.dp, height = 20.dp)
                .background(indexBackground, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (entry.index + 1).toString(),
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.selected) FontWeight.SemiBold else FontWeight.Normal,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.detail.isNotBlank()) {
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = episodePickerDetailColor(entry.selected),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
