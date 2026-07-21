package com.example.bilibili.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bilibili.R
import com.example.bilibili.data.AppearanceMode
import com.example.bilibili.data.FeedLayoutStore
import com.example.bilibili.data.StoredBilibiliAccount
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.liquidglass.BottomBarFeedOverlapReserve
import com.example.bilibili.ui.theme.isAppLightTheme
import kotlinx.coroutines.launch

private const val AppVersionName = "20260721"

private val SettingsBottomBarInset = 96.dp
private val SettingsAboutIconSize = 58.dp
private val SettingsAboutIconCornerRadius = 13.dp
private const val SettingsAboutIconCropScale = 1.14f

private val SettingsPageBackgroundLight = Color(0xFFF6F6F7)
private val SettingsBadgeBackgroundLight = Color(0xFFE6E6E6)

@Composable
private fun settingsPageBackground(): Color =
    if (isAppLightTheme()) SettingsPageBackgroundLight else MaterialTheme.colorScheme.background

@Composable
private fun settingsCardBackground(): Color =
    if (isAppLightTheme()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow

@Composable
private fun settingsBadgeBackground(): Color =
    if (isAppLightTheme()) SettingsBadgeBackgroundLight else MaterialTheme.colorScheme.surfaceContainerHighest

private data class HelpSection(
    val title: String,
    val items: List<String>,
)

private val appHelpSections = listOf(
    HelpSection(
        title = "开始使用",
        items = listOf(
            "本应用通过哔哩哔哩网页登录，首次使用请到「我的 → 设置」完成登录。",
            "登录成功、下拉刷新后会在顶部显示胶囊提示（如「登录成功」「更新了 x 条视频」）。",
            "登录后可浏览首页推荐、关注动态、排行榜，以及查看观看历史与个人主页。",
            "首页、关注、历史页均支持下拉刷新；关注页内可切换「关注 / 排行」。",
        ),
    ),
    HelpSection(
        title = "底部导航",
        items = listOf(
            "底部共有五个入口：首页、关注、直播、历史、我的。",
            "关注页顶部可在「关注 / 排行」之间切换。",
            "直播页顶部可在「关注 / 推荐」之间切换，点击直播间可在应用内打开直播页。",
            "向下滚动列表时，底部栏会收起到左侧小胶囊；点击小胶囊可再次展开。",
            "再次点击当前选中的首页、关注或历史，会回到顶部并刷新内容。",
            "从首页进入视频详情、用户主页或其他 Tab 后返回，首页会保留原有滚动位置。",
            "首页上滑加载更多最多 10 次；下拉刷新后会重置并可继续加载。",
            "未登录时访问关注、历史等页面，会提示先完成登录。",
        ),
    ),
    HelpSection(
        title = "直播",
        items = listOf(
            "推荐页支持按分区筛选直播间；关注页展示你关注的主播正在直播的房间。",
            "直播间顶部展示主播头像、昵称、直播标题与在线人数；昵称与标题会尽量完整显示。",
            "点击左上角头像或昵称可进入主播个人主页；已登录且非本人直播间时，昵称旁可直接关注。",
            "右侧展示榜一、榜二、榜三观众头像；在线人数约每 5 秒自动刷新。",
            "支持飘屏弹幕与底部弹幕列表，弹幕可包含表情；长按播放器「弹」可设置弹幕样式。",
            "播放器控件约 5 秒后自动隐藏，点击画面可再次显示；支持全屏观看与手动刷新直播流。",
            "全屏时底部为弹幕输入框、弹幕开关与全屏按钮同一行；输入弹幕时控制栏不会自动隐藏。",
            "向右滑动可清屏隐藏界面，向左滑动或按返回键恢复；清屏时横屏直播保持原有画面比例。",
        ),
    ),
    HelpSection(
        title = "视频播放",
        items = listOf(
            "点击视频卡片进入详情页播放；信息流内长按封面可进入小窗预览。",
            "详情页支持弹幕开关、倍速播放（× 符号）与全屏观看。",
            "从详情页返回后再次播放同一视频，会尽量延续上次进度与画面，减少黑屏重载。",
            "全屏播放支持选集按钮，可快速切换分 P 或合集内其他视频。",
            "长按控制栏「弹」可在播放器内打开弹幕设置，调节显示区域、不透明度、字号与速度。",
            "弹幕偏好修改后全局生效，视频与直播共用同一套设置。",
            "从简介或评论区进入 UP 主主页时，当前视频会自动暂停；返回后恢复播放。",
            "简介页合集弹窗、通知栏与锁屏媒体控件均支持切换分 P / 合集集数。",
        ),
    ),
    HelpSection(
        title = "搜索与用户",
        items = listOf(
            "点击底部搜索入口，可搜索视频与用户。",
            "点击 UP 主头像或昵称进入个人主页，查看投稿与动态。",
            "个人主页会缓存上次浏览的资料，再次进入时先展示缓存并后台刷新。",
            "昵称旁显示等级徽章；点击「关注」「粉丝」可查看列表，并可直接关注或取消关注。",
            "个人主页投稿卡片显示视频发布时间；可在设置中切换单列或双列布局。",
        ),
    ),
    HelpSection(
        title = "历史与设置",
        items = listOf(
            "「历史」页展示观看记录，按日期分组展示，支持删除单条或整组记录。",
            "同一合集的多条历史记录会分别保留，点击可进入对应集数继续观看。",
            "「我的」页为个人主页，右上角齿轮进入设置。",
            "设置中可切换深色模式（浅色 / 深色 / 跟随系统）、信息流单列/双列布局。",
            "账号管理支持多账号：点击切换当前账号，左滑账号行可删除，「添加账号」通过网页登录保存新账号。",
            "弹幕偏好修改后全局生效；关闭「后台播放声音」后，应用切到后台时会暂停视频。",
        ),
    ),
)

@Composable
fun SettingsScreen(
    feedColumnCount: Int,
    onFeedColumnCountChange: (Int) -> Unit,
    backgroundPlaybackEnabled: Boolean = false,
    onBackgroundPlaybackChange: (Boolean) -> Unit = {},
    appearanceMode: AppearanceMode = AppearanceMode.System,
    onAppearanceModeChange: (AppearanceMode) -> Unit = {},
    storedAccounts: List<StoredBilibiliAccount> = emptyList(),
    activeAccountId: String? = null,
    onSwitchAccount: (String) -> Unit = {},
    onDeleteAccount: (String) -> Unit = {},
    onAddAccount: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var feedLayoutExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
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
                    SettingsAccountCard(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = it },
                        accounts = storedAccounts,
                        activeAccountId = activeAccountId,
                        onSwitchAccount = onSwitchAccount,
                        onDeleteAccount = onDeleteAccount,
                        onAddAccount = onAddAccount,
                    )
                }
                item {
                    SettingsAppearanceCard(
                        expanded = appearanceExpanded,
                        onExpandedChange = { appearanceExpanded = it },
                        mode = appearanceMode,
                        onModeChange = onAppearanceModeChange,
                    )
                }
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
            .background(settingsPageBackground()),
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
                            color = settingsBadgeBackground(),
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
    backgroundColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val resolvedBackground = backgroundColor ?: settingsCardBackground()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = resolvedBackground,
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

@Composable
private fun SettingsAppearanceCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    mode: AppearanceMode,
    onModeChange: (AppearanceMode) -> Unit,
) {
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
                            text = "深色模式",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            color = settingsBadgeBackground(),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = mode.label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        text = mode.description,
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
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppearanceMode.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onModeChange(option) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RadioButton(
                                selected = mode == option,
                                onClick = { onModeChange(option) },
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (mode == option) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsAccountCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    accounts: List<StoredBilibiliAccount>,
    activeAccountId: String?,
    onSwitchAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
) {
    val activeAccount = accounts.firstOrNull { it.uid == activeAccountId }
    val subtitle = when {
        activeAccount != null -> activeAccount.name.ifBlank { "B站用户" }
        accounts.isNotEmpty() -> "已保存 ${accounts.size} 个账号，点击展开切换"
        else -> "登录哔哩哔哩以查看关注、历史与个人主页"
    }
    val status = when {
        accounts.isNotEmpty() -> "${accounts.size} 个账号"
        else -> "未登录"
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
                            text = "账号管理",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            color = settingsBadgeBackground(),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = status,
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
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (accounts.isEmpty()) {
                        Text(
                            text = "暂无已保存账号",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    } else {
                        accounts.forEach { account ->
                            SettingsAccountRow(
                                account = account,
                                isActive = account.uid == activeAccountId,
                                onSwitchAccount = onSwitchAccount,
                                onDeleteAccount = onDeleteAccount,
                            )
                        }
                    }
                    TextButton(
                        onClick = onAddAccount,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("添加账号")
                    }
                }
            }
        }
    }
}

private enum class SettingsAccountSwipeAnchor { Closed, Open }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsAccountRow(
    account: StoredBilibiliAccount,
    isActive: Boolean,
    onSwitchAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
) {
    val density = LocalDensity.current
    val deleteActionWidth = 72.dp
    val deleteActionWidthPx = remember(density) { with(density) { deleteActionWidth.toPx() } }
    val rowShape = RoundedCornerShape(8.dp)
    val rowBackground = if (isActive) {
        lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer, 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val dragState = remember(account.uid, deleteActionWidthPx) {
        AnchoredDraggableState(
            initialValue = SettingsAccountSwipeAnchor.Closed,
            anchors = DraggableAnchors {
                SettingsAccountSwipeAnchor.Closed at 0f
                SettingsAccountSwipeAnchor.Open at -deleteActionWidthPx
            },
        )
    }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = dragState,
        positionalThreshold = { distance -> distance * 0.35f },
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMedium,
        ),
    )
    val scope = rememberCoroutineScope()
    val swipeOffsetPx = dragState.requireOffset()
    val isRevealed = dragState.currentValue == SettingsAccountSwipeAnchor.Open
    val canSwitchAccount = !isActive && !isRevealed && swipeOffsetPx == 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .clip(rowShape),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(deleteActionWidth)
                .fillMaxHeight()
                .background(Color(0xFFE35D5B))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        onDeleteAccount(account.uid)
                        scope.launch { dragState.animateTo(SettingsAccountSwipeAnchor.Closed) }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipeOffsetPx
                    clip = true
                    shape = rowShape
                }
                .anchoredDraggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    flingBehavior = flingBehavior,
                )
                .background(rowBackground, rowShape)
                .clickable(
                    enabled = canSwitchAccount,
                    onClick = { onSwitchAccount(account.uid) },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RemoteImage(
                url = account.face.orEmpty(),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = account.name.ifBlank { "B站用户" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isActive) {
                    Text(
                        text = "当前",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
