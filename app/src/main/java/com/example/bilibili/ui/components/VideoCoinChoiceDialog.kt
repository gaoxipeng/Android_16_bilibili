package com.example.bilibili.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect

@Composable
fun VideoCoinChoiceDialog(
    visible: Boolean,
    anchorBoundsInRoot: Rect,
    canCoinTwo: Boolean,
    onDismiss: () -> Unit,
    onCoinOne: () -> Unit,
    onCoinTwo: () -> Unit,
) {
    val menuHeight = if (canCoinTwo) ActionMenuTwoRowHeight else ActionMenuOneRowHeight
    ActionMenuOverlay(
        activeRequest = if (visible) ActionMenuRequest(anchorBoundsInRoot) else null,
        menuVisible = visible,
        menuHeight = menuHeight,
        onDismiss = onDismiss,
        useFeedCardAlignment = false,
        zIndex = 1000f,
    ) {
        ActionMenuRow(
            label = if (canCoinTwo) "1 硬币" else "再投 1 硬币",
            onClick = onCoinOne,
        )
        if (canCoinTwo) {
            ActionMenuRow(
                label = "2 硬币",
                onClick = onCoinTwo,
            )
        }
    }
}
