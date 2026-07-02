package com.example.bilibili.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay

private const val ExitHintAutoDismissMillis = 2000L
internal val PressAgainExitConfirmWindowMillis = ExitHintAutoDismissMillis
private const val ExitHintLabel = "再按一次退出程序"

@Composable
fun PressAgainExitHintOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    zIndex: Float = 95f,
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(ExitHintAutoDismissMillis)
            onDismiss()
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(zIndex),
        contentAlignment = Alignment.TopCenter,
    ) {
        val menuWidth = rememberActionMenuWidth(
            labels = listOf(ExitHintLabel),
            maxWidth = maxWidth - 28.dp,
        )
        val menuHeight = ActionMenuOneRowHeight
        val density = LocalDensity.current
        val menuWidthPx = with(density) { menuWidth.toPx() }
        val originInMenu = Offset(menuWidthPx / 2f, 0f)

        ActionMenuReveal(
            visible = visible,
            menuWidth = menuWidth,
            menuHeight = menuHeight,
            originInMenu = originInMenu,
            modifier = Modifier
                .padding(top = topInset + 10.dp)
                .width(menuWidth)
                .height(menuHeight),
        ) {
            ActionFrostedCard(
                modifier = Modifier.fillMaxSize(),
                backdrop = backdrop,
                effectContainerColor = ActionMenuDestructiveColor,
            ) {
                ActionMenuRow(
                    label = ExitHintLabel,
                    destructive = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}
