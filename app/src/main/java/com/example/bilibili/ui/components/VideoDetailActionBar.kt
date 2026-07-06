package com.example.bilibili.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.R
import com.example.bilibili.ui.format.formatBiliCount
import com.example.bilibili.ui.theme.BiliBlue
import com.example.bilibili.ui.theme.BiliPink
import com.example.bilibili.ui.theme.BiliVideoActionInactive
import kotlin.math.hypot
import kotlinx.coroutines.delay

private val ActionIconSize = 24.dp
private val ActionRingSize = 34.dp
private val ActionTextSize = 10.sp
private val ActionLabelGap = 0.dp
private const val TripleHoldDurationMs = 2_000L
private const val TapMaxDurationMs = 320L
private const val RingVisibleThreshold = 0.02f

@Composable
fun VideoDetailActionBar(
    likeCount: Long,
    coinCount: Long,
    favoriteCount: Long,
    shareCount: Long,
    liked: Boolean,
    coined: Boolean,
    favorited: Boolean,
    enabled: Boolean,
    onLikeClick: () -> Unit,
    onTripleClick: () -> Unit,
    onCoinClick: (anchorBounds: Rect) -> Unit,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isLikeHolding by remember { mutableStateOf(false) }
    var tripleTriggered by remember { mutableStateOf(false) }
    val view = LocalView.current

    val displayProgress by animateFloatAsState(
        targetValue = if (isLikeHolding) holdProgress else 0f,
        animationSpec = if (isLikeHolding) snap() else tween(durationMillis = 280),
        label = "tripleHoldProgress",
    )

    LaunchedEffect(isLikeHolding) {
        if (!isLikeHolding) {
            holdProgress = 0f
            return@LaunchedEffect
        }
        tripleTriggered = false
        val startTime = System.currentTimeMillis()
        while (isLikeHolding) {
            val elapsed = System.currentTimeMillis() - startTime
            holdProgress = (elapsed.toFloat() / TripleHoldDurationMs).coerceIn(0f, 1f)
            if (holdProgress >= 1f) {
                tripleTriggered = true
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onTripleClick()
                isLikeHolding = false
                break
            }
            delay(16L)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoDetailLikeActionItem(
            label = formatBiliCount(likeCount),
            tint = if (liked) BiliBlue else BiliVideoActionInactive,
            enabled = enabled,
            ringProgress = displayProgress,
            onHoldStart = { isLikeHolding = true },
            onHoldEnd = { shortTap ->
                isLikeHolding = false
                if (shortTap && !tripleTriggered) {
                    onLikeClick()
                }
            },
        )
        VideoDetailActionItem(
            iconRes = R.drawable.ic_bili_coin,
            label = formatBiliCount(coinCount),
            tint = if (coined) BiliBlue else BiliVideoActionInactive,
            enabled = enabled,
            ringProgress = displayProgress,
            onBoundsClick = onCoinClick,
        )
        VideoDetailActionItem(
            iconRes = R.drawable.ic_bili_favorite,
            label = formatBiliCount(favoriteCount),
            tint = if (favorited) BiliBlue else BiliVideoActionInactive,
            enabled = enabled,
            ringProgress = displayProgress,
            onClick = onFavoriteClick,
        )
        VideoDetailActionItem(
            iconRes = R.drawable.ic_bili_share,
            label = formatBiliCount(shareCount),
            tint = BiliVideoActionInactive,
            enabled = enabled,
            onClick = onShareClick,
        )
    }
}

@Composable
private fun VideoDetailLikeActionItem(
    label: String,
    tint: Color,
    enabled: Boolean,
    ringProgress: Float = 0f,
    onHoldStart: () -> Unit,
    onHoldEnd: (shortTap: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ActionLabelGap),
    ) {
        Box(
            modifier = Modifier
                .size(ActionRingSize)
                .then(
                    if (enabled) {
                        Modifier.pointerInput(onHoldStart, onHoldEnd) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPosition = down.position
                                val startTime = System.currentTimeMillis()
                                onHoldStart()
                                var cancelled = false
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: run {
                                            onHoldEnd(false)
                                            break
                                        }
                                        if (!change.pressed) {
                                            val elapsed = System.currentTimeMillis() - startTime
                                            val moved = hypot(
                                                (change.position.x - downPosition.x).toDouble(),
                                                (change.position.y - downPosition.y).toDouble(),
                                            )
                                            onHoldEnd(elapsed <= TapMaxDurationMs && moved < 18.0)
                                            return@awaitEachGesture
                                        }
                                        if (change.positionChanged()) {
                                            val moved = hypot(
                                                (change.position.x - downPosition.x).toDouble(),
                                                (change.position.y - downPosition.y).toDouble(),
                                            )
                                            if (moved > 24.0) {
                                                cancelled = true
                                                break
                                            }
                                        }
                                    }
                                } finally {
                                    if (cancelled) {
                                        onHoldEnd(false)
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (ringProgress > RingVisibleThreshold) {
                TripleHoldProgressRing(progress = ringProgress)
            }
            Icon(
                painter = painterResource(R.drawable.ic_bili_like_filled),
                contentDescription = null,
                modifier = Modifier.size(ActionIconSize),
                tint = tint,
            )
        }
        Text(
            text = label,
            modifier = Modifier.offset(y = (-2).dp),
            color = if (tint == BiliBlue) BiliBlue else MaterialTheme.colorScheme.onSurface,
            fontSize = ActionTextSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VideoDetailActionItem(
    @DrawableRes iconRes: Int,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    ringProgress: Float = 0f,
    onBoundsClick: ((Rect) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var boundsInRoot by remember { mutableStateOf(Rect.Zero) }
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ActionLabelGap),
    ) {
        Box(
            modifier = Modifier
                .size(ActionRingSize)
                .onGloballyPositioned { coordinates ->
                    boundsInRoot = coordinates.boundsInRoot()
                }
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                if (onBoundsClick != null) {
                                    onBoundsClick(boundsInRoot)
                                } else {
                                    onClick()
                                }
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (ringProgress > RingVisibleThreshold) {
                TripleHoldProgressRing(progress = ringProgress)
            }
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(ActionIconSize),
                tint = tint,
            )
        }
        Text(
            text = label,
            modifier = Modifier.offset(y = (-2).dp),
            color = if (tint == BiliBlue) BiliBlue else MaterialTheme.colorScheme.onSurface,
            fontSize = ActionTextSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TripleHoldProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(ActionRingSize)) {
        val strokeWidth = 2.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f,
        )
        drawArc(
            color = BiliPink,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )
    }
}
