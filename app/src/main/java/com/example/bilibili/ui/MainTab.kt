package com.example.bilibili.ui

import androidx.compose.runtime.compositionLocalOf

internal enum class MainTab(val label: String) {
    Home("首页"),
    Following("关注"),
    Live("直播"),
    History("历史"),
    Mine("我的"),
    ;

    val isRequired: Boolean get() = this == Home || this == Mine
}

internal data class BottomTabSettings(
    val hiddenTabs: Set<String>,
    val onVisibilityChange: (MainTab, Boolean) -> Unit,
)

internal val LocalBottomTabSettings = compositionLocalOf<BottomTabSettings?> { null }
