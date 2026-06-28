package com.example.bilibili.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bilibili.data.FeedLayoutStore
import com.example.bilibili.ui.liquidglass.BottomBarFeedOverlapReserve

private val SettingsBottomBarInset = 96.dp
private val SettingsFeedLayoutCardBackground = Color(0xFFF7F7F8)
private val SettingsFeedLayoutExpandedBackground = Color(0xFFEFEFF1)

@Composable
fun SettingsScreen(
    feedColumnCount: Int,
    onFeedColumnCountChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var feedLayoutExpanded by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    SettingsPageShell(
        title = "设置",
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = SettingsBottomBarInset + BottomBarFeedOverlapReserve,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsFeedLayoutCard(
                    expanded = feedLayoutExpanded,
                    onExpandedChange = { feedLayoutExpanded = it },
                    columnCount = feedColumnCount,
                    onColumnCountChange = onFeedColumnCountChange,
                )
            }
        }
    }
}

@Composable
private fun SettingsPageShell(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 6.dp,
                    end = 16.dp,
                    bottom = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsFeedLayoutCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit,
) {
    val currentLabel = if (columnCount == FeedLayoutStore.COLUMN_COUNT_ONE) "一列" else "两列"
    val subtitle = when (columnCount) {
        FeedLayoutStore.COLUMN_COUNT_ONE -> "当前为一列模式，视频卡片全宽展示"
        else -> "当前为两列模式，首页与投稿以瀑布流展示"
    }

    SettingsPlainCard(
        backgroundColor = SettingsFeedLayoutCardBackground,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .background(SettingsFeedLayoutCardBackground)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "信息流布局",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = currentLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsExpandIndicator(expanded = expanded, rotateOnExpand = true)
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SettingsFeedLayoutExpandedBackground)
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FeedLayoutOption(
                        label = "两列",
                        description = "瀑布流双列卡片，信息更紧凑",
                        selected = columnCount == FeedLayoutStore.COLUMN_COUNT_TWO,
                        onClick = { onColumnCountChange(FeedLayoutStore.COLUMN_COUNT_TWO) },
                    )
                    FeedLayoutOption(
                        label = "一列",
                        description = "全宽大卡片，封面信息叠加展示",
                        selected = columnCount == FeedLayoutStore.COLUMN_COUNT_ONE,
                        onClick = { onColumnCountChange(FeedLayoutStore.COLUMN_COUNT_ONE) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedLayoutOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsPlainCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        color = backgroundColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
private fun SettingsExpandIndicator(
    expanded: Boolean = false,
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    rotateOnExpand: Boolean = false,
) {
    val rotation by animateFloatAsState(
        targetValue = if (rotateOnExpand && expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "settings_expand_chevron",
    )
    Icon(
        imageVector = Icons.Rounded.KeyboardArrowDown,
        contentDescription = null,
        modifier = modifier.graphicsLayer { rotationZ = rotation },
        tint = tint,
    )
}
