package com.example.bilibili.ui.liquidglass

import androidx.compose.ui.unit.dp

internal val BottomBarHeight = 64.dp
internal val BottomBarOuterBottomPadding = 24.dp
internal val BottomBarCapsuleBlurRadius = 24.dp
internal val BottomBarCapsuleLensRefraction = 12.dp

/** 列表内容需延伸到底栏重叠区，保证模糊采样覆盖整个大胶囊。 */
internal val BottomBarFeedOverlapReserve = BottomBarHeight + BottomBarOuterBottomPadding

/** 底栏下方额外 backdrop 采样延伸区（模糊半径 + 透镜折射）。 */
internal val BottomBarBackdropSampleExtension =
    BottomBarCapsuleBlurRadius + BottomBarCapsuleLensRefraction
