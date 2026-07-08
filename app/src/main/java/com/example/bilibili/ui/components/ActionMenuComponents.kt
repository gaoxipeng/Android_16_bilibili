package com.example.bilibili.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bilibili.ui.liquidglass.LocalLiquidMenuBackdrop
import com.example.bilibili.ui.liquidglass.SurfaceLiquidMenuCard
import com.example.bilibili.ui.liquidglass.liquidMenuSurfaceColor
import com.example.bilibili.ui.theme.isAppLightTheme
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

internal val ActionMenuDestructiveColor = Color(0xFFE54848)

@Composable
internal fun ActionFrostedCard(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
    menuHeight: Dp? = null,
    effectBlurRadius: Dp = ActionMenuBlurRadius,
    effectContainerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedBackdrop = backdrop ?: LocalLiquidMenuBackdrop.current
    val isLightTheme = isAppLightTheme()
    val shape = RoundedCornerShape(ActionMenuCornerRadius)
    val borderColor = com.example.bilibili.ui.liquidglass.liquidMenuBorderColor(isLightTheme)
    val surfaceColor = effectContainerColor ?: liquidMenuSurfaceColor(isLightTheme)
    val menuHazeStyle = HazeMaterials.thick(
        containerColor = surfaceColor,
    )
    val cardModifier = modifier.then(
        if (menuHeight != null) Modifier.height(menuHeight) else Modifier,
    )

    if (hazeState != null) {
        Column(
            cardModifier
                .clip(shape)
                .hazeEffect(state = hazeState) {
                    style = menuHazeStyle
                    blurRadius = effectBlurRadius
                }
                .border(com.example.bilibili.ui.liquidglass.LiquidMenuBorderWidth, borderColor, shape)
                .padding(PaddingValues(ActionMenuCardInset)),
            horizontalAlignment = Alignment.Start,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ActionMenuItemGap),
            ) {
                content()
            }
        }
        return
    }

    SurfaceLiquidMenuCard(
        modifier = cardModifier,
        backdrop = resolvedBackdrop,
        cornerRadius = ActionMenuCornerRadius,
        blurRadius = effectBlurRadius,
        surfaceColor = surfaceColor,
        contentPadding = PaddingValues(ActionMenuCardInset),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ActionMenuItemGap),
            ) {
                content()
            }
        },
    )
}

@Composable
internal fun ActionMenuRow(
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val capsuleShape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ActionMenuCapsuleHeight)
            .clip(capsuleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ActionMenuCapsulePaddingHorizontal),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = actionMenuTextStyle(selected = selected),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                destructive -> Color.White
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun actionMenuTextStyle(selected: Boolean = false): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

@Composable
internal fun rememberActionMenuWidth(
    labels: List<String>,
    maxWidth: Dp,
): Dp {
    val textMeasurer = rememberTextMeasurer()
    val style = actionMenuTextStyle()
    val density = LocalDensity.current
    val horizontalPadding = ActionMenuCapsulePaddingHorizontal * 2 + ActionMenuCardInset * 2

    return remember(labels, maxWidth, style, density) {
        val maxTextWidthPx = labels.maxOfOrNull { label ->
            textMeasurer.measure(
                text = label,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            ).size.width
        } ?: 0
        val widthDp = with(density) {
            (maxTextWidthPx.toFloat() + horizontalPadding.toPx()).toDp()
        }
        minOf(widthDp, maxWidth)
    }
}

internal data class ActionMenuRequest(
    val anchorBoundsInRoot: Rect,
)

@Composable
internal fun ActionMenuOverlay(
    activeRequest: ActionMenuRequest?,
    menuVisible: Boolean,
    menuHeight: Dp,
    menuLabels: List<String>,
    onDismiss: () -> Unit,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
    menuRevealKey: Any? = null,
    menuContainerColor: Color? = null,
    zIndex: Float = 590f,
    useFeedCardAlignment: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var displayedRequest by remember { mutableStateOf<ActionMenuRequest?>(null) }

    LaunchedEffect(activeRequest, menuVisible) {
        if (activeRequest != null && menuVisible) {
            displayedRequest = activeRequest
        }
    }

    val request = if (menuVisible) {
        activeRequest ?: displayedRequest
    } else {
        displayedRequest
    } ?: return

    BackHandler(enabled = menuVisible) {
        onDismiss()
    }

    val density = LocalDensity.current
    val gapFromButton = 6.dp
    val screenMargin = 14.dp
    val anchor = request.anchorBoundsInRoot

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(zIndex),
    ) {
        val menuWidth = rememberActionMenuWidth(
            labels = menuLabels,
            maxWidth = maxWidth - screenMargin * 2,
        )
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val menuWidthPx = with(density) { menuWidth.toPx() }
        val menuHeightPx = with(density) { menuHeight.toPx() }
        val menuPlacement = if (useFeedCardAlignment) {
            calculateFeedCardActionMenuOffsetPx(
                anchorBounds = anchor,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                menuWidthPx = menuWidthPx,
                menuHeightPx = menuHeightPx,
                marginPx = with(density) { screenMargin.toPx() },
                gapPx = with(density) { gapFromButton.toPx() },
            )
        } else {
            calculateActionMenuOffsetFromAnchorPx(
                anchorBounds = anchor,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                menuWidthPx = menuWidthPx,
                menuHeightPx = menuHeightPx,
                marginPx = with(density) { screenMargin.toPx() },
                gapPx = with(density) { gapFromButton.toPx() },
            )
        }
        val originInMenu = computeActionMenuOriginInMenu(
            anchorInRoot = anchor.center,
            menuOffset = menuPlacement.offset,
            menuWidthPx = menuWidthPx,
            menuHeightPx = menuHeightPx,
        )

        if (menuVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            )
        }
        ActionMenuReveal(
            visible = menuVisible,
            revealKey = menuRevealKey,
            menuWidth = menuWidth,
            menuHeight = menuHeight,
            originInMenu = originInMenu,
            onExitComplete = { displayedRequest = null },
            modifier = Modifier
                .offset { menuPlacement.offset }
                .width(menuWidth)
                .height(menuHeight),
        ) {
            ActionFrostedCard(
                modifier = Modifier.fillMaxSize(),
                backdrop = backdrop,
                hazeState = hazeState,
                effectContainerColor = menuContainerColor,
            ) {
                content()
            }
        }
    }
}
