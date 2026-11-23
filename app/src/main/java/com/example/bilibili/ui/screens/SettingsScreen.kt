package com.example.bilibili.ui.screens

import com.example.bilibili.ui.BottomTabSettings
import com.example.bilibili.ui.LocalBottomTabSettings
import com.example.bilibili.ui.MainTab
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.Dp
import com.example.bilibili.R
import com.example.bilibili.data.AppearanceMode
import com.example.bilibili.data.BiliDanmakuEmoticon
import com.example.bilibili.data.FeedLayoutStore
import com.example.bilibili.data.StoredBilibiliAccount
import com.example.bilibili.ui.components.RemoteImage
import com.example.bilibili.ui.liquidglass.BottomBarFeedOverlapReserve
import com.example.bilibili.ui.liquidglass.SettingsLiquidSlider
import com.example.bilibili.ui.liquidglass.SettingsLiquidToggle
import com.example.bilibili.ui.theme.isAppLightTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SettingsBottomBarInset = 20.dp
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
    autoHideBarsOnScroll: Boolean = true,
    onAutoHideBarsOnScrollChange: (Boolean) -> Unit = {},
    syncedEmoticonCount: Int = 0,
    syncedEmoticons: Map<String, BiliDanmakuEmoticon> = emptyMap(),
    syncingEmoticons: Boolean = false,
    onSyncEmoticons: () -> Unit = {},
    bottomBarFrostAmount: Float = 0f,
    onBottomBarFrostAmountChange: (Float) -> Unit = {},
    onBottomBarFrostAmountCommit: () -> Unit = {},
    bottomBarGlassAmount: Float = 1f,
    onBottomBarGlassAmountChange: (Float) -> Unit = {},
    onBottomBarGlassAmountCommit: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    handleSystemBack: Boolean = true,
) {
    BackHandler(enabled = handleSystemBack, onBack = onBack)
    val context = androidx.compose.ui.platform.LocalContext.current
    val appVersionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    var feedLayoutExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    val feedLayoutStore = remember(context) { FeedLayoutStore(context) }
    var showFullVideoTitles by remember { mutableStateOf(feedLayoutStore.readShowFullTitle()) }

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
                    SettingsAutoHideBarsCard(
                        enabled = autoHideBarsOnScroll,
                        onEnabledChange = onAutoHideBarsOnScrollChange,
                    )
                }
                item {
                    LocalBottomTabSettings.current?.let { SettingsBottomTabsCard(it) }
                }
                item {
                    SettingsEmoticonSyncCard(
                        emoticonCount = syncedEmoticonCount,
                        emoticons = syncedEmoticons,
                        syncing = syncingEmoticons,
                        onSync = onSyncEmoticons,
                    )
                }
                item {
                    SettingsFullTitlesCard(
                        enabled = showFullVideoTitles,
                        onEnabledChange = {
                            showFullVideoTitles = it
                            feedLayoutStore.writeShowFullTitle(it)
                        },
                    )
                }
                item {
                    SettingsBottomBarGlassCard(
                        frostAmount = bottomBarFrostAmount,
                        onFrostChange = onBottomBarFrostAmountChange,
                        onFrostCommit = onBottomBarFrostAmountCommit,
                        glassAmount = bottomBarGlassAmount,
                        onGlassChange = onBottomBarGlassAmountChange,
                        onGlassCommit = onBottomBarGlassAmountCommit,
                    )
                }
                item {
                    SettingsPlaybackCard(
                        backgroundPlaybackEnabled = backgroundPlaybackEnabled,
                        onBackgroundPlaybackChange = onBackgroundPlaybackChange,
                    )
                }
                item {
                    SettingsAboutCard(versionName = appVersionName)
                }
            }
    }
}

@Composable
private fun SettingsBottomTabsCard(settings: BottomTabSettings) {
    var expanded by remember { mutableStateOf(false) }
    SettingsPlainCard {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("底部导航", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "选择要显示的标签。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsExpandIndicator(expanded = expanded, rotateOnExpand = true)
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    MainTab.entries.forEach { tab ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tab.label, modifier = Modifier.weight(1f))
                            if (tab.isRequired) {
                                Text(
                                    "固定显示",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                SettingsLiquidToggle(
                                    checked = tab.name !in settings.hiddenTabs,
                                    onCheckedChange = { settings.onVisibilityChange(tab, it) },
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
private fun SettingsFullTitlesCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    SettingsPlainCard {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("显示完整标题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (enabled) "视频标题完整显示" else "视频标题最多显示两行",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsLiquidToggle(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun SettingsEmoticonSyncCard(
    emoticonCount: Int,
    emoticons: Map<String, BiliDanmakuEmoticon>,
    syncing: Boolean,
    onSync: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sortedEmoticons = remember(emoticons) { emoticons.entries.sortedBy { it.key } }
    val subtitle = if (emoticonCount > 0) {
        "本地已缓存 $emoticonCount 个 B 站表情，点击展开查看"
    } else {
        "本地暂无表情缓存，同步后可查看评论和动态表情"
    }
    SettingsPlainCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("表情同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = when {
                                    syncing -> "同步中"
                                    emoticonCount > 0 -> "本地 $emoticonCount 个"
                                    else -> "未同步"
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (syncing) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else SettingsExpandIndicator(expanded = expanded, rotateOnExpand = true)
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sortedEmoticons.isEmpty()) {
                        Text("暂无本地表情", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        ) {
                            val gap = 6.dp
                            val minCellWidth = 44.dp
                            val maxCellWidth = 58.dp
                            val columns = ((maxWidth + gap) / (minCellWidth + gap)).toInt().coerceAtLeast(1)
                            val cellWidth = ((maxWidth - gap * (columns - 1)) / columns).coerceAtMost(maxCellWidth)
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
                                sortedEmoticons.chunked(columns).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        row.forEach { (phrase, spec) -> SettingsEmoticonTile(phrase, spec.url, cellWidth) }
                                        repeat((columns - row.size).coerceAtLeast(0)) { Spacer(Modifier.width(cellWidth)) }
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = onSync, enabled = !syncing, modifier = Modifier.fillMaxWidth()) {
                        if (syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (emoticonCount > 0) "更新表情" else "同步表情")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsEmoticonTile(phrase: String, url: String, width: Dp) {
    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(width), contentAlignment = Alignment.Center) {
            RemoteImage(url = url, modifier = Modifier.size(width), contentScale = ContentScale.Fit)
        }
        Text(
            text = phrase.removeSurrounding("[", "]"),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsBottomBarGlassCard(
    frostAmount: Float,
    onFrostChange: (Float) -> Unit,
    onFrostCommit: () -> Unit,
    glassAmount: Float,
    onGlassChange: (Float) -> Unit,
    onGlassCommit: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsPlainCard {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "底栏 Liquid Glass",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "调整底栏磨砂和折射强度",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsBottomBarGlassSlider("磨砂效果", frostAmount, onFrostChange, onFrostCommit, "透明", "磨砂")
                    SettingsBottomBarGlassSlider("玻璃效果", glassAmount, onGlassChange, onGlassCommit, "无折射", "强折射")
                }
            }
        }
    }
}

@Composable
private fun SettingsBottomBarGlassSlider(
    title: String,
    amount: Float,
    onAmountChange: (Float) -> Unit,
    onCommit: () -> Unit,
    leftLabel: String,
    rightLabel: String,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            "${(amount.coerceIn(0f, 1f) * 100f).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(leftLabel, Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        SettingsLiquidSlider(
            value = amount.coerceIn(0f, 1f),
            onValueChange = onAmountChange,
            onValueChangeFinished = onCommit,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Text(rightLabel, Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun SettingsAutoHideBarsCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
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
                    text = "自动隐藏底栏和搜索框",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (enabled) {
                        "滚动时，底栏和首页搜索框自动隐藏"
                    } else {
                        "滚动时，底栏和首页搜索框保持显示"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsLiquidToggle(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
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
            SettingsLiquidToggle(
                checked = backgroundPlaybackEnabled,
                onCheckedChange = onBackgroundPlaybackChange,
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
                    text = "版本 $versionName",
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
