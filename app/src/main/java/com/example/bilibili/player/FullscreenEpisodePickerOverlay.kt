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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.example.bilibili.ui.theme.BiliPink
import com.kyant.backdrop.Backdrop

private val EpisodePickerPanelPadding = 10.dp
private val EpisodePickerRowHeight = 46.dp
private val EpisodePickerMaxListHeight = 320.dp

@Composable
private fun episodePickerDetailColor(selected: Boolean): Color {
    if (selected) return BiliPink.copy(alpha = 0.85f)
    return Color.White.copy(alpha = 0.82f)
}

@Composable
fun FullscreenEpisodePickerOverlay(
    visible: Boolean,
    pickerState: VideoEpisodePickerState?,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    anchorFromRight: Dp,
    bottomInset: Dp,
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
                .fillMaxSize()
                .zIndex(1f),
        ) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            val rowStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            val detailStyle = MaterialTheme.typography.labelSmall
            val contentWidth = with(density) {
                val heading = measurer.measure(pickerState.sheetTitle, titleStyle, maxLines = 1).size.width.toDp()
                val rows = pickerState.entries.maxOfOrNull { entry ->
                    val title = measurer.measure(entry.title, rowStyle, maxLines = 1).size.width
                    val detail = measurer.measure(entry.detail, detailStyle, maxLines = 1).size.width
                    maxOf(title, detail).toDp() + 44.dp
                } ?: 0.dp
                maxOf(heading, rows) + EpisodePickerPanelPadding * 2
            }
            val panelWidth = contentWidth.coerceIn(0.dp, (maxWidth - 16.dp).coerceAtLeast(0.dp))
            val rightInset = (anchorFromRight - panelWidth).coerceAtLeast(8.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = rightInset, bottom = bottomInset)
                    .width(panelWidth)
                    .heightIn(max = (maxHeight - bottomInset - 8.dp).coerceAtLeast(0.dp))
                    .videoPopupGlass(backdrop),
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
                        color = Color.White,
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
    val titleColor = if (entry.selected) BiliPink else Color.White
    val indexBackground = if (entry.selected) {
        BiliPink.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EpisodePickerRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
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
            )
            if (entry.detail.isNotBlank()) {
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = episodePickerDetailColor(entry.selected),
                )
            }
        }
    }
}
