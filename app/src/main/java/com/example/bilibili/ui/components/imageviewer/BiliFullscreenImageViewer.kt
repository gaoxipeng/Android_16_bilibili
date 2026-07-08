package com.example.bilibili.ui.components.imageviewer

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.compose.runtime.CompositionLocalProvider
import coil.compose.AsyncImage
import com.example.bilibili.data.BiliImageBitmapLoader
import com.example.bilibili.data.BiliImageSaveHelper
import com.example.bilibili.data.BiliViewerImage
import com.example.bilibili.ui.components.ActionFrostedCard
import com.example.bilibili.ui.components.ActionMenuReveal
import com.example.bilibili.ui.components.ActionMenuRow
import com.example.bilibili.ui.components.actionMenuSurfaceColor
import com.example.bilibili.ui.components.ActionMenuThreeRowHeight
import com.example.bilibili.ui.components.ActionMenuTwoRowHeight
import com.example.bilibili.ui.components.ImageActionMenuBlurRadius
import com.example.bilibili.ui.components.rememberActionMenuWidth
import com.example.bilibili.ui.components.calculateActionMenuOffsetFromPointPx
import com.example.bilibili.ui.components.computeActionMenuOriginInMenu
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val FullscreenDefaultMaxZoomScale = 8f
private const val FullscreenDynamicMaxZoomScale = 80f
private const val FullscreenFillZoomHeadroom = 1.15f
private const val FullscreenPixelPerfectZoomHeadroom = 2f
private const val MediaLongPressTimeoutMillis = 500L
private val ThumbnailMorphCornerRadius = 4.dp

@Composable
fun BiliFullscreenImageViewer(
    images: List<BiliViewerImage>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    sourceBoundsByIndex: Map<Int, Rect> = emptyMap(),
    animateOpenFromSource: Boolean = true,
    onCloseStart: (() -> Unit)? = null,
) {
    if (images.isEmpty()) return

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { images.size }, initialPage = initialIndex)
    val transitionProgress = remember(initialIndex) {
        Animatable(if (animateOpenFromSource && sourceBoundsByIndex[initialIndex] != null) 0f else 1f)
    }
    var transitionClosing by remember { mutableStateOf(false) }
    var dragDismissProgress by remember { mutableFloatStateOf(0f) }
    var closeStartBounds by remember { mutableStateOf<Rect?>(null) }
    var dismissBoundsProviders by remember { mutableStateOf<Map<Int, () -> Rect>>(emptyMap()) }

    fun dismissViewer(startBounds: Rect? = null) {
        if (!transitionClosing) {
            val closeBounds = sourceBoundsByIndex[pagerState.currentPage]
            if (closeBounds != null) {
                closeStartBounds = startBounds ?: dismissBoundsProviders[pagerState.currentPage]?.invoke()
                transitionClosing = true
                onCloseStart?.invoke()
                scope.launch {
                    transitionProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f),
                    )
                    onDismiss()
                }
            } else {
                onCloseStart?.invoke()
                onDismiss()
            }
        }
    }

    val pagerFling = PagerDefaults.flingBehavior(
        state = pagerState,
        decayAnimationSpec = exponentialDecay(frictionMultiplier = 0.82f),
    )
    var blockPagerScroll by remember { mutableStateOf(false) }

    LaunchedEffect(initialIndex, animateOpenFromSource) {
        if (animateOpenFromSource && sourceBoundsByIndex[initialIndex] != null) {
            transitionProgress.snapTo(0f)
            transitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f),
            )
        }
    }

    Dialog(
        onDismissRequest = { dismissViewer() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect {
            dialogWindow?.apply {
                setWindowAnimations(0)
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                setDimAmount(0f)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        BackHandler { dismissViewer() }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val morphPageIndex = if (transitionClosing) pagerState.currentPage else initialIndex
            val morphSourceBounds = when {
                transitionClosing -> closeStartBounds ?: sourceBoundsByIndex[pagerState.currentPage]
                pagerState.currentPage == initialIndex -> sourceBoundsByIndex[initialIndex]
                else -> null
            }
            val morphTargetBounds = if (transitionClosing && closeStartBounds != null) {
                sourceBoundsByIndex[pagerState.currentPage]
            } else {
                null
            }
            val morphImage = images.getOrNull(morphPageIndex)
            val imageAspect = morphImage?.let {
                val width = (it.width ?: 1).coerceAtLeast(1)
                val height = (it.height ?: 1).coerceAtLeast(1)
                width.toFloat() / height.toFloat()
            } ?: 1f
            val fitLayout = computeFitImageLayout(
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                imageAspect = imageAspect,
                scale = 1f,
            )
            val transition = transitionProgress.value.coerceIn(0f, 1f)
            val backdropAlpha = (1f - dragDismissProgress).coerceIn(0f, 1f)
            val morphWidth: Float
            val morphHeight: Float
            val morphCenterX: Float
            val morphCenterY: Float
            val uniformScale: Float
            if (morphSourceBounds != null) {
                val targetBounds = morphTargetBounds
                if (targetBounds != null) {
                    morphWidth = lerp(targetBounds.width, morphSourceBounds.width, transition)
                    morphHeight = lerp(targetBounds.height, morphSourceBounds.height, transition)
                    morphCenterX = lerp(targetBounds.center.x, morphSourceBounds.center.x, transition)
                    morphCenterY = lerp(targetBounds.center.y, morphSourceBounds.center.y, transition)
                } else {
                    morphWidth = lerp(morphSourceBounds.width, fitLayout.fitWidthPx, transition)
                    morphHeight = lerp(morphSourceBounds.height, fitLayout.fitHeightPx, transition)
                    morphCenterX = lerp(morphSourceBounds.center.x, containerWidthPx / 2f, transition)
                    morphCenterY = lerp(morphSourceBounds.center.y, containerHeightPx / 2f, transition)
                }
                uniformScale = maxOf(
                    morphWidth / fitLayout.fitWidthPx.coerceAtLeast(1f),
                    morphHeight / fitLayout.fitHeightPx.coerceAtLeast(1f),
                ).coerceIn(0.05f, 4f)
            } else {
                morphWidth = fitLayout.fitWidthPx
                morphHeight = fitLayout.fitHeightPx
                morphCenterX = containerWidthPx / 2f
                morphCenterY = containerHeightPx / 2f
                uniformScale = 1f
            }
            val activeMorph = morphSourceBounds != null && (transitionClosing || transition < 0.999f)
            val effectiveBackdropAlpha = when {
                activeMorph && transitionClosing -> (transition * backdropAlpha).coerceIn(0f, 1f)
                sourceBoundsByIndex.isEmpty() -> (transition * backdropAlpha).coerceIn(0f, 1f)
                else -> backdropAlpha
            }
            val morphLeft = morphCenterX - morphWidth / 2f
            val morphTop = morphCenterY - morphHeight / 2f
            val morphRight = morphCenterX + morphWidth / 2f
            val morphBottom = morphCenterY + morphHeight / 2f
            val morphCornerRadiusPx = with(density) {
                ThumbnailMorphCornerRadius.toPx() * (1f - transition).coerceIn(0f, 1f)
            }
            // 始终保留不透明黑底，避免 morph 挖洞区域或图片 letterbox 透出下层页面
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = effectiveBackdropAlpha)),
            )
            if (activeMorph) {
                MorphRevealScrim(
                    morphLeft = morphLeft,
                    morphTop = morphTop,
                    morphRight = morphRight,
                    morphBottom = morphBottom,
                    cornerRadiusPx = morphCornerRadiusPx,
                    scrimColor = Color.Black.copy(
                        alpha = if (transitionClosing) effectiveBackdropAlpha else backdropAlpha,
                    ),
                )
            }
            HorizontalPager(
                state = pagerState,
                flingBehavior = pagerFling,
                userScrollEnabled = !blockPagerScroll,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (activeMorph) {
                            Modifier.morphRevealClip(
                                morphLeft = morphLeft,
                                morphTop = morphTop,
                                morphRight = morphRight,
                                morphBottom = morphBottom,
                                cornerRadiusPx = morphCornerRadiusPx,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .graphicsLayer {
                        if (activeMorph) {
                            translationX = morphCenterX - containerWidthPx / 2f
                            translationY = morphCenterY - containerHeightPx / 2f
                            scaleX = uniformScale
                            scaleY = uniformScale
                            alpha = 1f
                            transformOrigin = TransformOrigin.Center
                        }
                    },
            ) { page ->
                val pageImage = images[page]
                ZoomableFullscreenImage(
                    image = pageImage,
                    allImages = images,
                    isActive = page == pagerState.currentPage,
                    onDismiss = { dismissViewer() },
                    onDismissFromBounds = { bounds -> dismissViewer(bounds) },
                    onDismissBoundsProvider = { provider ->
                        dismissBoundsProviders = dismissBoundsProviders + (page to provider)
                    },
                    hasMultipleImages = images.size > 1,
                    onBlockPagerScroll = { blockPagerScroll = it },
                    onDragDismissProgress = { dragDismissProgress = it },
                    onRequestPageChange = { delta ->
                        scope.launch {
                            val next = (pagerState.currentPage + delta).coerceIn(0, images.lastIndex)
                            if (next != pagerState.currentPage) {
                                pagerState.animateScrollToPage(next)
                            }
                        }
                    },
                )
            }
            if (images.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                        .graphicsLayer {
                            alpha = if (transitionClosing) 0f else transition.coerceIn(0f, 1f)
                        },
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            BiliImageSaveHintOverlay(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun MorphRevealScrim(
    morphLeft: Float,
    morphTop: Float,
    morphRight: Float,
    morphBottom: Float,
    cornerRadiusPx: Float,
    scrimColor: Color = Color.Black,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(
                RoundRect(
                    left = morphLeft,
                    top = morphTop,
                    right = morphRight,
                    bottom = morphBottom,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                ),
            )
        }
        drawPath(path, scrimColor)
    }
}

private fun Modifier.morphRevealClip(
    morphLeft: Float,
    morphTop: Float,
    morphRight: Float,
    morphBottom: Float,
    cornerRadiusPx: Float,
): Modifier = drawWithContent {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = morphLeft,
                top = morphTop,
                right = morphRight,
                bottom = morphBottom,
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
            ),
        )
    }
    drawContext.canvas.save()
    drawContext.canvas.clipPath(path)
    drawContent()
    drawContext.canvas.restore()
}

private data class FitImageLayout(
    val fitWidthPx: Float,
    val fitHeightPx: Float,
    val maxPanX: Float,
    val maxPanY: Float,
)

private enum class FullscreenDragAxis {
    Horizontal,
    Vertical,
}

private fun computeFitImageLayout(
    containerWidthPx: Float,
    containerHeightPx: Float,
    imageAspect: Float,
    scale: Float,
): FitImageLayout {
    val containerAspect = containerWidthPx / containerHeightPx
    val fitWidthPx: Float
    val fitHeightPx: Float
    if (imageAspect > containerAspect) {
        fitWidthPx = containerWidthPx
        fitHeightPx = containerWidthPx / imageAspect
    } else {
        fitWidthPx = containerHeightPx * imageAspect
        fitHeightPx = containerHeightPx
    }
    val scaledWidth = fitWidthPx * scale
    val scaledHeight = fitHeightPx * scale
    val maxPanX = ((scaledWidth - containerWidthPx) / 2f).coerceAtLeast(0f)
    val maxPanY = ((scaledHeight - containerHeightPx) / 2f).coerceAtLeast(0f)
    return FitImageLayout(fitWidthPx, fitHeightPx, maxPanX, maxPanY)
}

private fun computeFullscreenMaxZoomScale(
    bitmapWidth: Int,
    bitmapHeight: Int,
    containerWidthPx: Float,
    containerHeightPx: Float,
    imageAspect: Float,
    rawFillScreenScale: Float,
    sourceWidth: Int = 0,
    sourceHeight: Int = 0,
): Float {
    val fitLayout = computeFitImageLayout(
        containerWidthPx = containerWidthPx,
        containerHeightPx = containerHeightPx,
        imageAspect = imageAspect,
        scale = 1f,
    )
    val targetWidth = maxOf(bitmapWidth, sourceWidth.coerceAtLeast(0))
    val targetHeight = maxOf(bitmapHeight, sourceHeight.coerceAtLeast(0))
    val pixelPerfectScale = maxOf(
        targetWidth / fitLayout.fitWidthPx.coerceAtLeast(1f),
        targetHeight / fitLayout.fitHeightPx.coerceAtLeast(1f),
    )
    return maxOf(
        FullscreenDefaultMaxZoomScale,
        rawFillScreenScale * FullscreenFillZoomHeadroom,
        pixelPerfectScale * FullscreenPixelPerfectZoomHeadroom,
    ).coerceAtMost(FullscreenDynamicMaxZoomScale)
}

private fun computeVisibleBlackBars(
    offsetY: Float,
    layout: FitImageLayout,
    scale: Float,
    containerHeightPx: Float,
): Pair<Float, Float> {
    val scaledHeight = layout.fitHeightPx * scale
    val centerY = containerHeightPx / 2f + offsetY
    val top = (centerY - scaledHeight / 2f).coerceAtLeast(0f)
    val bottom = (containerHeightPx - (centerY + scaledHeight / 2f)).coerceAtLeast(0f)
    return top to bottom
}

private suspend fun flingPanOffset(
    start: Float,
    initialVelocity: Float,
    min: Float,
    max: Float,
    decaySpec: DecayAnimationSpec<Float>,
    onUpdate: (Float) -> Unit,
) {
    if (max - min < 1f || abs(initialVelocity) < 4f) {
        onUpdate(start.coerceIn(min, max))
        return
    }
    val animationState = AnimationState(
        initialValue = start,
        initialVelocity = initialVelocity,
    )
    animationState.animateDecay(decaySpec) {
        onUpdate(value.coerceIn(min, max))
    }
    onUpdate(animationState.value.coerceIn(min, max))
}

@Composable
private fun MediaLongPressConfiguration(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val mediaConfig = remember(base) {
        object : androidx.compose.ui.platform.ViewConfiguration by base {
            override val longPressTimeoutMillis: Long = MediaLongPressTimeoutMillis
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides mediaConfig, content = content)
}

@Composable
private fun ZoomableFullscreenImage(
    image: BiliViewerImage,
    allImages: List<BiliViewerImage>,
    onDismiss: () -> Unit,
    onDismissFromBounds: (Rect) -> Unit = { onDismiss() },
    onDismissBoundsProvider: (((() -> Rect) -> Unit))? = null,
    hasMultipleImages: Boolean = false,
    isActive: Boolean = true,
    onBlockPagerScroll: (Boolean) -> Unit = {},
    onDragDismissProgress: (Float) -> Unit = {},
    onRequestPageChange: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    var scale by remember(image.largeUrl) { mutableFloatStateOf(1f) }
    var panOffsetX by remember(image.largeUrl) { mutableFloatStateOf(0f) }
    var panOffsetY by remember(image.largeUrl) { mutableFloatStateOf(0f) }
    var dismissTranslationY by remember(image.largeUrl) { mutableStateOf(0f) }
    var panInertiaJob by remember(image.largeUrl) { mutableStateOf<Job?>(null) }
    var zoomAnimationJob by remember(image.largeUrl) { mutableStateOf<Job?>(null) }
    val dismissSnapAnim = remember(image.largeUrl) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var fullscreenBitmap by remember(image.largeUrl) {
        mutableStateOf(
            BiliImageBitmapLoader.getCachedFullscreen(image)
                ?.takeIf { BiliImageBitmapLoader.isFullscreenQuality(it, image) },
        )
    }
    var previewBitmap by remember(image.largeUrl) {
        mutableStateOf(BiliImageBitmapLoader.resolvePreviewBitmap(image))
    }
    var fullscreenLoading by remember(image.largeUrl) { mutableStateOf(false) }
    var actionMenuOffset by remember(image.largeUrl) { mutableStateOf<Offset?>(null) }
    var actionMenuVisible by remember(image.largeUrl) { mutableStateOf(false) }

    LaunchedEffect(isActive, image.largeUrl) {
        if (!isActive) return@LaunchedEffect
        dismissTranslationY = 0f
        dismissSnapAnim.snapTo(0f)
        zoomAnimationJob?.cancel()
        zoomAnimationJob = null
        panOffsetX = 0f
        panOffsetY = 0f
        scale = 1f
        onBlockPagerScroll(false)
        onDragDismissProgress(0f)
        actionMenuOffset = null
        actionMenuVisible = false
        BiliImageBitmapLoader.getCachedFullscreen(image)?.let { cached ->
            if (BiliImageBitmapLoader.isFullscreenQuality(cached, image)) {
                fullscreenBitmap = cached
                return@LaunchedEffect
            }
        }
        if (previewBitmap == null) {
            previewBitmap = withContext(Dispatchers.IO) { BiliImageBitmapLoader.loadPreviewBitmap(image) }
        }
        if (fullscreenBitmap?.let { BiliImageBitmapLoader.isFullscreenQuality(it, image) } == true) {
            return@LaunchedEffect
        }
        fullscreenLoading = true
        val loaded = withContext(Dispatchers.IO) { BiliImageBitmapLoader.loadFullscreenBitmap(image) }
        fullscreenLoading = false
        loaded?.let { bitmap -> fullscreenBitmap = bitmap }
    }

    val hasFullscreenQuality = fullscreenBitmap?.let { BiliImageBitmapLoader.isFullscreenQuality(it, image) } == true
    val displayBitmap = fullscreenBitmap?.takeIf { hasFullscreenQuality }
        ?: previewBitmap
        ?: fullscreenBitmap
    val previewUrl = remember(image.thumbnailUrl, image.largeUrl) {
        image.thumbnailUrl.takeIf { it.isNotBlank() } ?: image.largeUrl
    }
    val showFullscreenLoading = fullscreenLoading &&
        !hasFullscreenQuality &&
        (displayBitmap != null || previewUrl.isNotBlank())

    LaunchedEffect(actionMenuVisible, actionMenuOffset) {
        if (!actionMenuVisible && actionMenuOffset != null) {
            delay(180)
            actionMenuOffset = null
        }
    }

    MediaLongPressConfiguration {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val containerWidthPx = with(density) { maxWidth.toPx() }
            val containerHeightPx = with(density) { maxHeight.toPx() }
            val blackBarThresholdPx = containerHeightPx / 3f
            val dismissReleaseThresholdPx = containerHeightPx * 0.14f
            val pageSwitchThresholdPx = 80f

            val containerAspect = remember(maxWidth, maxHeight) {
                val width = maxWidth.value.coerceAtLeast(1f)
                val height = maxHeight.value.coerceAtLeast(1f)
                width / height
            }
            val imageAspect = remember(displayBitmap?.width, displayBitmap?.height, image.width, image.height) {
                when {
                    displayBitmap != null ->
                        displayBitmap.width.toFloat() / displayBitmap.height.coerceAtLeast(1).toFloat()
                    image.width != null && image.height != null && image.width > 0 && image.height > 0 ->
                        image.width.toFloat() / image.height.toFloat()
                    else -> 1f
                }
            }
            val rawFillScreenScale = remember(containerAspect, imageAspect) {
                maxOf(imageAspect / containerAspect, containerAspect / imageAspect)
                    .coerceAtLeast(1f)
            }
            val maxZoomScale = remember(
                displayBitmap?.width,
                displayBitmap?.height,
                image.width,
                image.height,
                containerWidthPx,
                containerHeightPx,
                imageAspect,
                rawFillScreenScale,
            ) {
                computeFullscreenMaxZoomScale(
                    bitmapWidth = displayBitmap?.width ?: image.width ?: 1,
                    bitmapHeight = displayBitmap?.height ?: image.height ?: 1,
                    containerWidthPx = containerWidthPx,
                    containerHeightPx = containerHeightPx,
                    imageAspect = imageAspect,
                    rawFillScreenScale = rawFillScreenScale,
                    sourceWidth = image.width ?: 0,
                    sourceHeight = image.height ?: 0,
                )
            }
            val fillScreenScale = remember(rawFillScreenScale, maxZoomScale) {
                rawFillScreenScale.coerceIn(1.35f, maxZoomScale)
            }
            val latestScaleState = rememberUpdatedState(scale)
            val latestPanOffsetXState = rememberUpdatedState(panOffsetX)
            val latestPanOffsetYState = rememberUpdatedState(panOffsetY)
            val latestActionMenuVisibleState = rememberUpdatedState(actionMenuVisible)

            fun layoutFor(currentScale: Float) =
                computeFitImageLayout(containerWidthPx, containerHeightPx, imageAspect, currentScale)

            fun clampPan(x: Float, y: Float, layout: FitImageLayout): Pair<Float, Float> =
                x.coerceIn(-layout.maxPanX, layout.maxPanX) to y.coerceIn(-layout.maxPanY, layout.maxPanY)

            fun updatePagerScrollBlock(currentScale: Float, currentOffsetX: Float) {
                val layout = layoutFor(currentScale)
                val atHorizontalEdge = layout.maxPanX <= 1f ||
                    abs(currentOffsetX) >= layout.maxPanX - 4f
                onBlockPagerScroll(currentScale > 1.01f && layout.maxPanX > 1f && !atHorizontalEdge)
            }

            fun readDisplayedBounds(): Rect {
                val currentScale = latestScaleState.value
                val layout = layoutFor(currentScale)
                val dismissOffset = dismissTranslationY + dismissSnapAnim.value
                val displayedWidth = layout.fitWidthPx * currentScale
                val displayedHeight = layout.fitHeightPx * currentScale
                val centerX = containerWidthPx / 2f + latestPanOffsetXState.value
                val centerY = containerHeightPx / 2f + latestPanOffsetYState.value + dismissOffset
                return Rect(
                    left = centerX - displayedWidth / 2f,
                    top = centerY - displayedHeight / 2f,
                    right = centerX + displayedWidth / 2f,
                    bottom = centerY + displayedHeight / 2f,
                )
            }

            fun dismissFromCurrentBounds() {
                dismissTranslationY = 0f
                panOffsetX = 0f
                panOffsetY = 0f
                scale = 1f
                scope.launch { dismissSnapAnim.snapTo(0f) }
                onDismissFromBounds(readDisplayedBounds())
            }

            DisposableEffect(onDismissBoundsProvider) {
                onDismissBoundsProvider?.invoke(::readDisplayedBounds)
                onDispose { }
            }

            val pageMenuBackdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(pageMenuBackdrop),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(image.largeUrl, containerWidthPx, containerHeightPx, imageAspect, maxZoomScale) {
                                awaitEachGesture {
                                    var velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                                    val touchSlop = viewConfiguration.touchSlop
                                    var panningZoomed = false
                                    var pinching = false
                                    var postPinchPanOrigin: Offset? = null
                                    var dismissing = false
                                    var lockedAxis: FullscreenDragAxis? = null
                                    var horizontalPageDragAccum = 0f
                                    val boxCenterX = containerWidthPx / 2f
                                    val boxCenterY = containerHeightPx / 2f
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    panInertiaJob?.cancel()
                                    zoomAnimationJob?.cancel()
                                    zoomAnimationJob = null
                                    var gestureScale = latestScaleState.value
                                    var gesturePanOffsetX = latestPanOffsetXState.value
                                    var gesturePanOffsetY = latestPanOffsetYState.value
                                    var lastDistance = 0f
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break

                                        if (pressed.size >= 2) {
                                            if (!pinching) {
                                                pinching = true
                                                postPinchPanOrigin = null
                                                panningZoomed = false
                                                velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                                            }
                                            dismissTranslationY = 0f
                                            panInertiaJob?.cancel()
                                            val first = pressed[0].position
                                            val second = pressed[1].position
                                            val centroid = Offset((first.x + second.x) / 2f, (first.y + second.y) / 2f)
                                            val distance = hypot(first.x - second.x, first.y - second.y)
                                            if (lastDistance > 0f) {
                                                val oldScale = gestureScale.coerceAtLeast(0.01f)
                                                val newScale = (oldScale * (distance / lastDistance)).coerceIn(1f, maxZoomScale)
                                                gesturePanOffsetX = centroid.x - boxCenterX -
                                                    (centroid.x - boxCenterX - gesturePanOffsetX) * (newScale / oldScale)
                                                gesturePanOffsetY = centroid.y - boxCenterY -
                                                    (centroid.y - boxCenterY - gesturePanOffsetY) * (newScale / oldScale)
                                                val layout = layoutFor(newScale)
                                                val (cx, cy) = clampPan(gesturePanOffsetX, gesturePanOffsetY, layout)
                                                gesturePanOffsetX = cx
                                                gesturePanOffsetY = cy
                                                panOffsetX = gesturePanOffsetX
                                                panOffsetY = gesturePanOffsetY
                                                scale = newScale
                                                gestureScale = newScale
                                                updatePagerScrollBlock(newScale, gesturePanOffsetX)
                                            }
                                            lastDistance = distance
                                            event.changes.forEach { it.consume() }
                                        } else {
                                            lastDistance = 0f
                                            if (pinching) {
                                                pinching = false
                                                postPinchPanOrigin = pressed.first().position
                                                velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                                                lockedAxis = null
                                                event.changes.forEach { it.consume() }
                                                continue
                                            }
                                            val change = pressed.first()
                                            val panOrigin = postPinchPanOrigin ?: down.position
                                            val totalDrag = change.position - panOrigin
                                            val delta = change.position - change.previousPosition
                                            gestureScale = latestScaleState.value
                                            gesturePanOffsetX = latestPanOffsetXState.value
                                            gesturePanOffsetY = latestPanOffsetYState.value
                                            if (lockedAxis == null) {
                                                val absX = abs(totalDrag.x)
                                                val absY = abs(totalDrag.y)
                                                if (hypot(totalDrag.x, totalDrag.y) > touchSlop) {
                                                    lockedAxis = when {
                                                        absX > absY * 1.08f -> FullscreenDragAxis.Horizontal
                                                        absY > absX * 1.18f -> FullscreenDragAxis.Vertical
                                                        else -> null
                                                    }
                                                }
                                            }
                                            val layout = layoutFor(gestureScale)
                                            val (blackTop, blackBottom) = computeVisibleBlackBars(
                                                gesturePanOffsetY,
                                                layout,
                                                gestureScale,
                                                containerHeightPx,
                                            )
                                            val canDismissDown = blackTop >= blackBarThresholdPx && delta.y > 0
                                            val canDismissUp = blackBottom >= blackBarThresholdPx && delta.y < 0
                                            val canDismissVertically = lockedAxis == FullscreenDragAxis.Vertical &&
                                                (canDismissDown || canDismissUp ||
                                                    (
                                                        gestureScale <= 1.01f &&
                                                            abs(totalDrag.y) > 48f &&
                                                            abs(totalDrag.y) > abs(totalDrag.x) * 1.35f
                                                        ))

                                            updatePagerScrollBlock(gestureScale, gesturePanOffsetX)

                                            if (gestureScale <= 1.01f && lockedAxis == FullscreenDragAxis.Horizontal) {
                                                dismissTranslationY = 0f
                                                onBlockPagerScroll(false)
                                                continue
                                            }

                                            if (
                                                gestureScale > 1.01f &&
                                                hasMultipleImages &&
                                                abs(delta.x) > abs(delta.y) * 0.55f
                                            ) {
                                                val atLeftEdge = layout.maxPanX <= 1f || gesturePanOffsetX <= -layout.maxPanX + 4f
                                                val atRightEdge = layout.maxPanX <= 1f || gesturePanOffsetX >= layout.maxPanX - 4f
                                                val swipeToNext = atLeftEdge && delta.x < 0
                                                val swipeToPrev = atRightEdge && delta.x > 0
                                                if (swipeToNext || swipeToPrev) {
                                                    horizontalPageDragAccum += delta.x
                                                    if (horizontalPageDragAccum < -pageSwitchThresholdPx) {
                                                        onRequestPageChange(1)
                                                        horizontalPageDragAccum = 0f
                                                        change.consume()
                                                        break
                                                    } else if (horizontalPageDragAccum > pageSwitchThresholdPx) {
                                                        onRequestPageChange(-1)
                                                        horizontalPageDragAccum = 0f
                                                        change.consume()
                                                        break
                                                    }
                                                    change.consume()
                                                    continue
                                                }
                                            }

                                            if (canDismissVertically) {
                                                dismissing = true
                                                dismissTranslationY += delta.y
                                                val dragProgress = (abs(dismissTranslationY) / (containerHeightPx * 0.48f))
                                                    .coerceIn(0f, 1f)
                                                onDragDismissProgress(dragProgress)
                                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                                change.consume()
                                                continue
                                            }

                                            if (gestureScale > 1.01f) {
                                                if (!panningZoomed && hypot(totalDrag.x, totalDrag.y) > touchSlop) {
                                                    panningZoomed = true
                                                }
                                                if (panningZoomed) {
                                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                                    val (cx, cy) = clampPan(
                                                        gesturePanOffsetX + delta.x,
                                                        gesturePanOffsetY + delta.y,
                                                        layout,
                                                    )
                                                    gesturePanOffsetX = cx
                                                    gesturePanOffsetY = cy
                                                    panOffsetX = gesturePanOffsetX
                                                    panOffsetY = gesturePanOffsetY
                                                    updatePagerScrollBlock(gestureScale, gesturePanOffsetX)
                                                    change.consume()
                                                }
                                            } else {
                                                onBlockPagerScroll(false)
                                            }
                                        }
                                    }

                                    gestureScale = latestScaleState.value
                                    val dismissDistance = dismissTranslationY + dismissSnapAnim.value
                                    if (dismissing || dismissDistance != 0f) {
                                        val velocity = velocityTracker.calculateVelocity()
                                        if (
                                            abs(dismissDistance) > dismissReleaseThresholdPx ||
                                            abs(velocity.y) > 850f
                                        ) {
                                            val handoffScale = latestScaleState.value
                                            val handoffLayout = layoutFor(handoffScale)
                                            val handoffProgress = (abs(dismissDistance) / (containerHeightPx * 0.48f))
                                                .coerceIn(0f, 1f)
                                            val handoffDragScale =
                                                1f - 0.16f * FastOutSlowInEasing.transform(handoffProgress)
                                            val handoffWidth = handoffLayout.fitWidthPx * handoffScale * handoffDragScale
                                            val handoffHeight = handoffLayout.fitHeightPx * handoffScale * handoffDragScale
                                            val handoffCenterX = containerWidthPx / 2f + latestPanOffsetXState.value
                                            val handoffCenterY = containerHeightPx / 2f + latestPanOffsetYState.value +
                                                dismissDistance
                                            dismissTranslationY = 0f
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                            scale = 1f
                                            onDismissFromBounds(
                                                Rect(
                                                    left = handoffCenterX - handoffWidth / 2f,
                                                    top = handoffCenterY - handoffHeight / 2f,
                                                    right = handoffCenterX + handoffWidth / 2f,
                                                    bottom = handoffCenterY + handoffHeight / 2f,
                                                ),
                                            )
                                        } else {
                                            scope.launch {
                                                dismissSnapAnim.snapTo(dismissTranslationY)
                                                dismissTranslationY = 0f
                                                dismissSnapAnim.animateTo(0f, tween(220))
                                                onDragDismissProgress(0f)
                                            }
                                        }
                                        return@awaitEachGesture
                                    }

                                    gestureScale = latestScaleState.value
                                    gesturePanOffsetX = latestPanOffsetXState.value
                                    gesturePanOffsetY = latestPanOffsetYState.value
                                    updatePagerScrollBlock(gestureScale, gesturePanOffsetX)
                                    if (panningZoomed && gestureScale > 1.01f) {
                                        val velocity = velocityTracker.calculateVelocity()
                                        val layout = layoutFor(gestureScale)
                                        val decaySpec = exponentialDecay<Float>(
                                            frictionMultiplier = 0.42f,
                                            absVelocityThreshold = 0.5f,
                                        )
                                        val startX = gesturePanOffsetX
                                        val startY = gesturePanOffsetY
                                        panInertiaJob = scope.launch {
                                            coroutineScope {
                                                launch {
                                                    flingPanOffset(
                                                        start = startX,
                                                        initialVelocity = velocity.x,
                                                        min = -layout.maxPanX,
                                                        max = layout.maxPanX,
                                                        decaySpec = decaySpec,
                                                    ) { panOffsetX = it }
                                                }
                                                launch {
                                                    flingPanOffset(
                                                        start = startY,
                                                        initialVelocity = velocity.y,
                                                        min = -layout.maxPanY,
                                                        max = layout.maxPanY,
                                                        decaySpec = decaySpec,
                                                    ) { panOffsetY = it }
                                                }
                                            }
                                            updatePagerScrollBlock(gestureScale, latestPanOffsetXState.value)
                                        }
                                    }
                                }
                            }
                            .pointerInput(image.largeUrl, fillScreenScale) {
                                detectTapGestures(
                                    onTap = {
                                        if (latestActionMenuVisibleState.value) {
                                            actionMenuVisible = false
                                        } else {
                                            dismissFromCurrentBounds()
                                        }
                                    },
                                    onDoubleTap = {
                                        actionMenuVisible = false
                                        panInertiaJob?.cancel()
                                        zoomAnimationJob?.cancel()
                                        dismissTranslationY = 0f
                                        scope.launch { dismissSnapAnim.snapTo(0f) }
                                        onBlockPagerScroll(false)
                                        val currentScale = latestScaleState.value
                                        if (currentScale > 1f) {
                                            zoomAnimationJob = scope.launch {
                                                animate(
                                                    initialValue = currentScale,
                                                    targetValue = 1f,
                                                    animationSpec = tween(180),
                                                ) { value, _ -> scale = value }
                                                zoomAnimationJob = null
                                            }
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                        } else {
                                            zoomAnimationJob = scope.launch {
                                                animate(
                                                    initialValue = currentScale,
                                                    targetValue = fillScreenScale,
                                                    animationSpec = tween(180),
                                                ) { value, _ -> scale = value }
                                                zoomAnimationJob = null
                                            }
                                        }
                                    },
                                    onLongPress = { offset ->
                                        actionMenuOffset = offset
                                        actionMenuVisible = true
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val dismissOffsetY = dismissTranslationY + dismissSnapAnim.value
                        val dismissProgress = (abs(dismissOffsetY) / (containerHeightPx * 0.48f)).coerceIn(0f, 1f)
                        val dragScale = 1f - 0.16f * FastOutSlowInEasing.transform(dismissProgress)
                        val dismissAlpha = (1f - dismissProgress * 0.18f).coerceIn(0.82f, 1f)
                        val currentScale = scale
                        val imageModifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = currentScale * dragScale
                                scaleY = currentScale * dragScale
                                translationX = panOffsetX
                                translationY = panOffsetY + dismissOffsetY
                                alpha = dismissAlpha
                            }
                        Box(modifier = imageModifier) {
                            if (image.isGif) {
                                AsyncImage(
                                    model = image.downloadUrls.firstOrNull { it.isNotBlank() } ?: image.largeUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                if (!hasFullscreenQuality && previewUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = previewUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                displayBitmap?.let { bitmap ->
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                if (displayBitmap == null && previewUrl.isBlank() && fullscreenLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center),
                                        color = Color.White.copy(alpha = 0.85f),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                        if (showFullscreenLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 56.dp)
                                    .size(22.dp),
                                color = Color.White.copy(alpha = 0.85f),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }

                actionMenuOffset?.let { offset ->
                    FullscreenImageActionMenu(
                        image = image,
                        allImages = allImages,
                        pressOffset = offset,
                        visible = actionMenuVisible,
                        screenWidthPx = containerWidthPx,
                        screenHeightPx = containerHeightPx,
                        backdrop = pageMenuBackdrop,
                        onDismiss = { actionMenuVisible = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.FullscreenImageActionMenu(
    image: BiliViewerImage,
    allImages: List<BiliViewerImage>,
    pressOffset: Offset,
    visible: Boolean,
    screenWidthPx: Float,
    screenHeightPx: Float,
    backdrop: Backdrop? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val saveHint = LocalBiliImageSaveHint.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var saving by remember { mutableStateOf(false) }
    val images = allImages.ifEmpty { listOf(image) }
    val showSaveAll = images.size > 1
    val menuLabels = buildList {
        add("保存")
        if (showSaveAll) add("保存全部")
        add("分享")
    }
    val estimatedMenuHeight = if (showSaveAll) ActionMenuThreeRowHeight else ActionMenuTwoRowHeight
    val margin = 14.dp
    val gap = 10.dp
    val maxMenuWidth = with(density) { screenWidthPx.toDp() } - margin * 2
    val menuWidth = rememberActionMenuWidth(menuLabels, maxMenuWidth)
    val menuWidthPx = with(density) { menuWidth.toPx() }
    val menuHeightPx = with(density) { estimatedMenuHeight.toPx() }
    val menuPlacement = calculateActionMenuOffsetFromPointPx(
        pressOffset = pressOffset,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        menuWidthPx = menuWidthPx,
        menuHeightPx = menuHeightPx,
        marginPx = with(density) { margin.toPx() },
        gapPx = with(density) { gap.toPx() },
    )
    val originInMenu = computeActionMenuOriginInMenu(
        anchorInRoot = pressOffset,
        menuOffset = menuPlacement.offset,
        menuWidthPx = menuWidthPx,
        menuHeightPx = menuHeightPx,
    )

    ActionMenuReveal(
        visible = visible,
        menuWidth = menuWidth,
        menuHeight = estimatedMenuHeight,
        originInMenu = originInMenu,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { menuPlacement.offset }
            .width(menuWidth)
            .height(estimatedMenuHeight)
            .zIndex(20f),
    ) {
        ActionFrostedCard(
            modifier = Modifier.fillMaxSize(),
            backdrop = backdrop,
            effectBlurRadius = ImageActionMenuBlurRadius,
            effectContainerColor = actionMenuSurfaceColor(),
        ) {
            ActionMenuRow(
                label = "保存",
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        if (saveHint.saveOne(context, image)) {
                            onDismiss()
                        }
                        saving = false
                    }
                },
            )
            if (showSaveAll) {
                ActionMenuRow(
                    label = "保存全部",
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            val result = saveHint.saveAll(context, images)
                            if (result.saved > 0) {
                                onDismiss()
                            }
                            saving = false
                        }
                    },
                )
            }
            ActionMenuRow(
                label = "分享",
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        BiliImageSaveHelper.shareImage(context, image)
                            .onSuccess { onDismiss() }
                            .onFailure { error ->
                                Toast.makeText(
                                    context,
                                    error.message ?: "分享失败",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        saving = false
                    }
                },
            )
        }
    }
}
