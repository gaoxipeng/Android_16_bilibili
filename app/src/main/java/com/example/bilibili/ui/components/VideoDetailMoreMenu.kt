package com.example.bilibili.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState

@Composable
fun VideoDetailMoreMenu(
    visible: Boolean,
    anchorBoundsInRoot: Rect,
    onDismiss: () -> Unit,
    onOpenOfficialApp: () -> Unit,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
) {
    ActionMenuOverlay(
        activeRequest = if (visible) ActionMenuRequest(anchorBoundsInRoot) else null,
        menuVisible = visible,
        menuHeight = ActionMenuOneRowHeight,
        menuLabels = listOf("跳转到哔哩哔哩客户端"),
        onDismiss = onDismiss,
        useFeedCardAlignment = false,
        backdrop = backdrop,
        hazeState = hazeState,
        zIndex = 1000f,
    ) {
        ActionMenuRow(
            label = "跳转到哔哩哔哩客户端",
            onClick = onOpenOfficialApp,
        )
    }
}
