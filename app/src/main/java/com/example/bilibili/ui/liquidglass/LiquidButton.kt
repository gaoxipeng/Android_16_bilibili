package com.example.bilibili.ui.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

internal val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
internal val LocalLiquidMenuBackdrop = staticCompositionLocalOf<Backdrop?> { null }

internal val LiquidMenuGlassBlurRadius = 24.dp
internal val LiquidMenuBorderWidth = 0.5.dp

internal fun liquidMenuBorderColor(isLightTheme: Boolean): Color =
    if (isLightTheme) Color(0x24000000) else Color(0x33FFFFFF)

internal fun BackdropEffectScope.liquidMenuGlassEffects() {
    vibrancy()
    blur(LiquidMenuGlassBlurRadius.toPx())
    lens(12f.dp.toPx(), 24f.dp.toPx())
}

@Composable
fun TintedLiquidCapsule(
    tint: Color,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    pill: Boolean = false,
    cornerRadius: Dp = 14.dp,
    blurRadius: Dp = 2.dp,
    enableEdgeHighlight: Boolean = true,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = if (pill) RoundedCornerShape(percent = 50) else RoundedCornerShape(cornerRadius)
    val borderModifier =
        if (borderWidth > 0.dp && borderColor.isSpecified) {
            Modifier.border(borderWidth, borderColor, shape)
        } else {
            Modifier
        }
    Box(
        modifier
            .graphicsLayer { clip = false }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(blurRadius.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                highlight = if (enableEdgeHighlight) {
                    { Highlight.Default }
                } else {
                    null
                },
                shadow = null,
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                },
            )
            .then(borderModifier),
        content = content,
    )
}

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    blurRadius: Dp = 2.dp,
    enableEdgeHighlight: Boolean = true,
    useMenuGlassStyle: Boolean = false,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    onDoubleClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val borderModifier =
        if (borderWidth > 0.dp && borderColor.isSpecified) {
            Modifier.border(borderWidth, borderColor, shape)
        } else {
            Modifier
        }
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val interactionModifier =
        if (onDoubleClick != null) {
            Modifier.pointerInput(onClick, onDoubleClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() },
                )
            }
        } else {
            Modifier.clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
        }

    Box(modifier.then(borderModifier)) {
        Row(
            Modifier
                .matchParentSize()
                .graphicsLayer { clip = false }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        if (useMenuGlassStyle) {
                            liquidMenuGlassEffects()
                        } else {
                            vibrancy()
                            blur(blurRadius.toPx())
                            lens(12f.dp.toPx(), 24f.dp.toPx())
                        }
                    },
                    highlight = if (enableEdgeHighlight) {
                        { Highlight.Default }
                    } else {
                        null
                    },
                    shadow = null,
                    layerBlock = if (isInteractive) {
                    {
                        val width = size.width
                        val height = size.height
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)
                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)
                        val maxDragScale = 4f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                (width / height).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                },
            )
            .then(interactionModifier)
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                },
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

fun liquidSurfaceColor(isLightTheme: Boolean): Color =
    if (isLightTheme) Color.White.copy(0.48f) else Color.White.copy(0.22f)

fun liquidMenuSurfaceColor(isLightTheme: Boolean): Color =
    if (isLightTheme) Color.White.copy(0.82f) else Color.White.copy(0.38f)

fun liquidSheetSurfaceColor(isLightTheme: Boolean): Color =
    if (isLightTheme) Color.White.copy(0.92f) else Color.White.copy(0.48f)

@Composable
fun SurfaceLiquidCapsule(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    pill: Boolean = false,
    cornerRadius: Dp = 22.dp,
    useMenuGlassStyle: Boolean = false,
    tint: Color = Color.Unspecified,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedBackdrop = backdrop ?: LocalLiquidMenuBackdrop.current
    val isLightTheme = !isSystemInDarkTheme()
    val surfaceColor = liquidMenuSurfaceColor(isLightTheme)
    val menuBorderColor = liquidMenuBorderColor(isLightTheme)
    val shape = if (pill) RoundedCornerShape(percent = 50) else RoundedCornerShape(cornerRadius)
    val borderModifier = when {
        borderWidth > 0.dp && borderColor.isSpecified -> {
            Modifier.border(borderWidth, borderColor, shape)
        }
        useMenuGlassStyle -> {
            Modifier.border(LiquidMenuBorderWidth, menuBorderColor, shape)
        }
        else -> Modifier
    }

    if (resolvedBackdrop != null) {
        Box(modifier.then(borderModifier)) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { clip = false }
                    .drawBackdrop(
                        backdrop = resolvedBackdrop,
                        shape = { shape },
                        effects = {
                            if (useMenuGlassStyle) {
                                liquidMenuGlassEffects()
                            } else {
                                vibrancy()
                                blur(2f.dp.toPx())
                                lens(12f.dp.toPx(), 24f.dp.toPx())
                            }
                        },
                        highlight = if (useMenuGlassStyle) null else ({ Highlight.Default }),
                        shadow = null,
                        onDrawSurface = {
                            if (tint.isSpecified) {
                                drawRect(tint, blendMode = BlendMode.Hue)
                                drawRect(tint.copy(alpha = 0.75f))
                            } else {
                                drawRect(surfaceColor)
                            }
                        },
                    ),
                content = content,
            )
        }
    } else {
        Box(
            modifier
                .clip(shape)
                .background(
                    if (tint.isSpecified) tint.copy(alpha = 0.82f) else surfaceColor,
                    shape,
                )
                .then(borderModifier),
            content = content,
        )
    }
}

@Composable
fun SurfaceLiquidTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop? = null,
    textColor: Color = Color.Unspecified,
    style: TextStyle? = null,
) {
    val resolvedBackdrop = backdrop ?: LocalLiquidMenuBackdrop.current
    val isLightTheme = !isSystemInDarkTheme()
    val resolvedTextColor = when {
        textColor.isSpecified -> textColor
        isLightTheme -> Color(0xFF1F1F1F)
        else -> Color.White
    }
    val textStyle = style ?: MaterialTheme.typography.labelMedium

    if (resolvedBackdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = resolvedBackdrop,
            modifier = modifier,
            isInteractive = enabled,
            surfaceColor = liquidSurfaceColor(isLightTheme),
        ) {
            Text(
                text = text,
                color = resolvedTextColor,
                style = textStyle,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        SurfaceLiquidCapsule(
            modifier = modifier.clickable(enabled = enabled, onClick = onClick),
            pill = true,
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp),
                color = resolvedTextColor,
                style = textStyle,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun TransparentLiquidCapsule(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    pill: Boolean = false,
    cornerRadius: Dp = 22.dp,
    blurRadius: Dp = 2.dp,
    enableEdgeHighlight: Boolean = true,
    useMenuGlassStyle: Boolean = false,
    surfaceColor: Color = Color.Unspecified,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = if (pill) RoundedCornerShape(percent = 50) else RoundedCornerShape(cornerRadius)
    val borderModifier =
        if (borderWidth > 0.dp && borderColor.isSpecified) {
            Modifier.border(borderWidth, borderColor, shape)
        } else {
            Modifier
        }
    Box(modifier.then(borderModifier)) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { clip = false }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        if (useMenuGlassStyle) {
                            liquidMenuGlassEffects()
                        } else {
                            vibrancy()
                            blur(blurRadius.toPx())
                            lens(12f.dp.toPx(), 24f.dp.toPx())
                        }
                    },
                    highlight = if (enableEdgeHighlight) {
                        { Highlight.Default }
                    } else {
                        null
                    },
                    shadow = null,
                    onDrawSurface = {
                        if (surfaceColor.isSpecified) {
                            drawRect(surfaceColor)
                        }
                    },
                ),
            content = content,
        )
    }
}

@Composable
fun TransparentLiquidTextButton(
    text: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textColor: Color = Color.White,
    style: TextStyle? = null,
    blurRadius: Dp = 2.dp,
    enableEdgeHighlight: Boolean = true,
    useMenuGlassStyle: Boolean = false,
    surfaceColor: Color = Color.Unspecified,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        isInteractive = enabled,
        blurRadius = blurRadius,
        enableEdgeHighlight = enableEdgeHighlight,
        useMenuGlassStyle = useMenuGlassStyle,
        surfaceColor = surfaceColor,
        borderWidth = borderWidth,
        borderColor = borderColor,
    ) {
        Text(
            text = text,
            color = textColor,
            style = style ?: MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SurfaceLiquidMenuCard(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    cornerRadius: Dp = 14.dp,
    blurRadius: Dp = LiquidMenuGlassBlurRadius,
    surfaceColor: Color? = null,
    useMenuGlassStyle: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedBackdrop = backdrop ?: LocalLiquidMenuBackdrop.current
    val isLightTheme = !isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)
    val resolvedSurfaceColor = surfaceColor ?: when {
        useMenuGlassStyle -> liquidMenuSurfaceColor(isLightTheme)
        else -> liquidSurfaceColor(isLightTheme)
    }
    val borderColor = liquidMenuBorderColor(isLightTheme)

    if (resolvedBackdrop != null) {
        Column(
            modifier
                .graphicsLayer { clip = false }
                .drawBackdrop(
                    backdrop = resolvedBackdrop,
                    shape = { shape },
                    effects = {
                        if (useMenuGlassStyle) {
                            liquidMenuGlassEffects()
                        } else {
                            vibrancy()
                            lens(12f.dp.toPx(), 24f.dp.toPx())
                            blur(blurRadius.toPx(), TileMode.Decal)
                        }
                    },
                    highlight = null,
                    shadow = null,
                    onDrawSurface = { drawRect(resolvedSurfaceColor) },
                )
                .border(LiquidMenuBorderWidth, borderColor, shape)
                .padding(contentPadding),
            horizontalAlignment = Alignment.Start,
            content = content,
        )
    } else {
        Column(
            modifier
                .clip(shape)
                .background(resolvedSurfaceColor, shape)
                .border(LiquidMenuBorderWidth, borderColor, shape)
                .padding(contentPadding),
            horizontalAlignment = Alignment.Start,
            content = content,
        )
    }
}

@Composable
fun SurfaceLiquidIconButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onDoubleClick: (() -> Unit)? = null,
    isInteractive: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        onDoubleClick = onDoubleClick,
        isInteractive = isInteractive,
        surfaceColor = liquidSurfaceColor(!isSystemInDarkTheme()),
        content = content,
    )
}

@Composable
fun TransparentLiquidIconButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onDoubleClick: (() -> Unit)? = null,
    isInteractive: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        onDoubleClick = onDoubleClick,
        isInteractive = isInteractive,
        content = content,
    )
}
