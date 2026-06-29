package com.example.bilibili.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bilibili.R
import com.example.bilibili.data.FeedLayoutStore
import com.example.bilibili.ui.liquidglass.BottomBarFeedOverlapReserve

private const val AppVersionName = "0.0.1"

private val SettingsBottomBarInset = 96.dp
private val SettingsPageBackground = Color.White
private val SettingsCardBackground = Color(0xFFF5F5F5)
private val SettingsAboutIconSize = 58.dp
private val SettingsAboutIconCornerRadius = 13.dp
private const val SettingsAboutIconCropScale = 1.14f

private data class HelpSection(
    val title: String,
    val items: List<String>,
)

private val appHelpSections = listOf(
    HelpSection(
        title = "开始使用",
        items = listOf(
            "本应用通过哔哩哔哩网页登录，首次使用请到「我的 → 设置」完成登录。",
            "登录后可浏览首页推荐、关注动态、排行榜，以及查看观看历史与个人主页。",
            "首页、关注、排行与历史页均支持下拉刷新；滚动到底部会自动加载更多。",
        ),
    ),
    HelpSection(
        title = "底部导航",
        items = listOf(
            "底部共有五个入口：首页、关注、排行、历史、我的。",
            "向下滚动列表时，底部栏会收起到左侧小胶囊；点击小胶囊可再次展开。",
            "再次点击当前选中的首页、关注、排行或历史，会回到顶部并刷新内容。",
            "未登录时访问关注、历史等页面，会提示先完成登录。",
        ),
    ),
    HelpSection(
        title = "视频播放",
        items = listOf(
            "点击视频卡片进入详情页播放；信息流内长按封面可进入小窗预览。",
            "详情页支持弹幕开关、倍速播放与全屏观看。",
            "长按控制栏「弹」可打开弹幕设置，调节显示区域、不透明度、字号与速度。",
            "从简介或评论区进入 UP 主主页时，当前视频会自动暂停；返回后恢复播放。",
        ),
    ),
    HelpSection(
        title = "搜索与用户",
        items = listOf(
            "点击底部搜索入口，可搜索视频与用户。",
            "点击 UP 主头像或昵称进入个人主页，查看投稿与动态。",
            "个人主页点击「关注」「粉丝」可查看列表，并可直接关注或取消关注。",
            "个人主页投稿卡片显示视频发布时间；可在设置中切换单列或双列布局。",
        ),
    ),
    HelpSection(
        title = "历史与设置",
        items = listOf(
            "「历史」页展示观看记录，按日期分组展示，支持删除单条或整组记录。",
            "「我的」页为个人主页，右上角齿轮进入设置。",
            "设置中可切换信息流单列/双列布局；弹幕偏好修改后全局生效。",
            "后台播放声音：关闭后，应用切到后台时会暂停视频；浮窗播放时切换页面不会暂停。",
        ),
    ),
)

@Composable
fun SettingsScreen(
    feedColumnCount: Int,
    onFeedColumnCountChange: (Int) -> Unit,
    backgroundPlaybackEnabled: Boolean = false,
    onBackgroundPlaybackChange: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var feedLayoutExpanded by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    BackHandler {
        if (showHelp) {
            showHelp = false
        } else {
            onBack()
        }
    }

    SettingsPageShell(
        title = if (showHelp) "使用说明" else "设置",
        modifier = modifier,
    ) {
        if (showHelp) {
            SettingsHelpContent()
        } else {
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
                item {
                    SettingsPlaybackCard(
                        backgroundPlaybackEnabled = backgroundPlaybackEnabled,
                        onBackgroundPlaybackChange = onBackgroundPlaybackChange,
                    )
                }
                item {
                    SettingsHelpEntryCard(onOpen = { showHelp = true })
                }
                item {
                    SettingsAboutCard(versionName = AppVersionName)
                }
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
            .background(SettingsPageBackground),
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
private fun SettingsAboutAppIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(SettingsAboutIconCornerRadius))
            .background(Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_preview),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = SettingsAboutIconCropScale
                    scaleY = SettingsAboutIconCropScale
                },
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun SettingsPlaybackCard(
    backgroundPlaybackEnabled: Boolean,
    onBackgroundPlaybackChange: (Boolean) -> Unit,
) {
    SettingsPlainCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "后台播放声音",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (backgroundPlaybackEnabled) {
                        "返回桌面时继续播放视频声音"
                    } else {
                        "返回桌面时自动暂停视频"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = backgroundPlaybackEnabled,
                onCheckedChange = onBackgroundPlaybackChange,
            )
        }
    }
}

@Composable
private fun SettingsHelpEntryCard(onOpen: () -> Unit) {
    SettingsPlainCard(onClick = onOpen) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "使用说明",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "了解底部导航、视频播放、搜索与用户等功能",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsAboutCard(versionName: String) {
    SettingsPlainCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "哔哩哔哩 · 版本 $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsAboutAppIcon(
                modifier = Modifier.size(SettingsAboutIconSize),
            )
        }
    }
}

@Composable
private fun SettingsHelpContent() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 4.dp,
            end = 16.dp,
            bottom = SettingsBottomBarInset + BottomBarFeedOverlapReserve,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "本页汇总了当前版本的主要功能与交互方式。部分能力依赖哔哩哔哩网页接口，若官方调整页面，个别入口可能暂时不可用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        appHelpSections.forEach { section ->
            item {
                SettingsHelpSectionCard(section = section)
            }
        }
    }
}

@Composable
private fun SettingsHelpSectionCard(section: HelpSection) {
    SettingsPlainCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            section.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = item,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
        else -> "当前为两列模式，视频卡片瀑布流展示"
    }

    SettingsPlainCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
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
    backgroundColor: Color = SettingsCardBackground,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
