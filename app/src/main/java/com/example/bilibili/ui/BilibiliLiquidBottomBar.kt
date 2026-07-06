package com.example.bilibili.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bilibili.ui.liquidglass.LiquidBottomTab
import com.example.bilibili.ui.liquidglass.LiquidBottomTabs
import com.example.bilibili.ui.liquidglass.LiquidGlassConfig
import com.example.bilibili.ui.liquidglass.LocalLiquidBottomTabBackdropRow
import com.example.bilibili.ui.liquidglass.SurfaceLiquidIconButton
import com.example.bilibili.ui.theme.TabAccentDark
import com.example.bilibili.ui.theme.TabAccentLight
import com.kyant.backdrop.Backdrop
import kotlin.math.abs

@Composable
internal fun BilibiliLiquidBottomBar(
    selectedTab: MainTab,
    onTabClick: (MainTab) -> Unit,
    expanded: Boolean,
    backdrop: Backdrop,
    onExpandRequest: () -> Unit,
    onCollapsedTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) TabAccentLight else TabAccentDark
    val unselectedColor = if (isLightTheme) Color(0xFF1F1F1F) else Color.White.copy(0.72f)
    val collapsedSize = 64.dp
    val barHeight = 64.dp
    val animationOverflow = 12.dp
    val tabs = MainTab.entries
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    var tabsMounted by remember { mutableStateOf(expanded) }
    if (expanded) tabsMounted = true

    val widthSpring = spring<Dp>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 980f)
    val contentSpring = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 1050f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomStart,
    ) {
        val fullBarWidth = maxWidth
        val transition = updateTransition(targetState = expanded, label = "bottom-bar-expansion")
        val isMorphing = transition.isRunning

        val barWidth by transition.animateDp(transitionSpec = { widthSpring }, label = "bar-width") { expandedState ->
            if (expandedState) fullBarWidth else collapsedSize
        }
        val expandedAlpha by transition.animateFloat(transitionSpec = { contentSpring }, label = "expanded-alpha") { expandedState ->
            if (expandedState) 1f else 0f
        }
        val collapsedAlpha = (1f - expandedAlpha).coerceIn(0f, 1f)
        val revealAlpha = expandedAlpha.coerceIn(0f, 1f)
        val bounceOvershoot = (revealAlpha - 1f).coerceAtLeast(0f)
        val morphT = 1f - abs(revealAlpha - 0.5f) * 2f
        val morphScaleX = 1f + morphT * 0.06f + bounceOvershoot * 0.1f
        val morphScaleY = 1f - morphT * 0.02f + bounceOvershoot * 0.06f
        val collapsedOnTop = revealAlpha < 0.5f

        BoxWithConstraints(Modifier.width(fullBarWidth).height(barHeight + animationOverflow)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(barWidth)
                    .height(barHeight)
                    .graphicsLayer {
                        clip = false
                        scaleX = morphScaleX
                        scaleY = morphScaleY
                        transformOrigin = TransformOrigin(0f, 1f)
                    },
            ) {
                if (tabsMounted && (revealAlpha > 0.01f || isMorphing)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (collapsedOnTop) 0f else 2f)
                            .graphicsLayer {
                                alpha = revealAlpha
                                scaleX = 0.92f + revealAlpha * 0.08f + bounceOvershoot * 0.03f
                                scaleY = 0.92f + revealAlpha * 0.08f + bounceOvershoot * 0.03f
                            },
                    ) {
                        LiquidBottomTabs(
                            selectedTabIndex = { selectedIndex },
                            onTabSelected = { index -> onTabClick(tabs[index]) },
                            backdrop = backdrop,
                            tabsCount = tabs.size,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                val isBackdropRow = LocalLiquidBottomTabBackdropRow.current
                                val isSelected = index == selectedIndex
                                val tabColor = when {
                                    isBackdropRow -> accentColor
                                    isSelected -> accentColor
                                    else -> unselectedColor
                                }
                                val tabAlpha = if (
                                    !isBackdropRow &&
                                    isSelected &&
                                    LiquidGlassConfig.enableLensEffects
                                ) {
                                    0f
                                } else {
                                    1f
                                }
                                LiquidBottomTab(onClick = { onTabClick(tab) }) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .graphicsLayer { alpha = tabAlpha },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BilibiliTabIcon(tab = tab, color = tabColor)
                                    }
                                    Text(
                                        text = tab.label,
                                        fontSize = 12.sp,
                                        color = tabColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.graphicsLayer { alpha = tabAlpha },
                                    )
                                }
                            }
                        }
                    }
                }

                if (collapsedAlpha > 0.01f || isMorphing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (collapsedOnTop) 2f else 0f)
                            .graphicsLayer {
                                alpha = collapsedAlpha
                                scaleX = 0.9f + collapsedAlpha * 0.1f
                                scaleY = 0.9f + collapsedAlpha * 0.1f
                            },
                    ) {
                        SurfaceLiquidIconButton(
                            onClick = onExpandRequest,
                            onDoubleClick = onCollapsedTap,
                            backdrop = backdrop,
                            isInteractive = collapsedOnTop && collapsedAlpha > 0.5f,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            BilibiliTabIcon(tab = selectedTab, color = accentColor, size = 24.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BilibiliTabIcon(
    tab: MainTab,
    color: Color,
    size: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val icon: ImageVector = when (tab) {
        MainTab.Home -> BilibiliHomeIcon
        MainTab.Following -> BilibiliFollowingIcon
        MainTab.Live -> BilibiliLiveIcon
        MainTab.History -> BilibiliHistoryIcon
        MainTab.Mine -> BilibiliMineIcon
    }
    Icon(
        imageVector = icon,
        contentDescription = tab.label,
        modifier = Modifier.size(size),
        tint = color,
    )
}

private val BilibiliLiveIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BilibiliLive",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f,
    ).apply {
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString("M493.789998 1023.53089C254.799718 1023.53089 85.536259 856.015932 85.536259 625.384341c0-122.523033 72.584129-254.513561 75.782607-259.92965a32.11272 32.11272 0 0 1 31.728902-16.162976c12.879205 1.705855 23.156981 11.088057 26.69663 23.540799 0.255878 0.682342 19.87321 74.887033 45.631621 115.955491 17.271782 27.720143 34.970027 47.16689 54.80059 60.81373-13.433608-58.724057-23.626091-147.129991-6.951359-237.668243C358.771578 63.305131 552.514055 3.642853 560.787452 1.21201a31.814195 31.814195 0 0 1 39.831713 36.93176c-0.213232 1.705855-32.325952 175.95894 35.481784 324.410968 6.183724 13.518901 14.712999 29.084827 23.967262 44.906632a422.199104 422.199104 0 0 1 13.135083-66.698929c24.990775-88.022116 89.344154-118.130456 92.116168-119.281909a31.64361 31.64361 0 0 1 32.496537 4.008759c9.382202 7.249884 13.732132 19.276161 11.64246 31.046561-0.341171 2.217611-9.382202 62.178413 41.196398 147.12999 45.674267 76.678181 58.681411 126.403853 58.68141 221.931731C909.123036 856.143872 734.614073 1023.53089 493.789998 1023.53089zM187.247861 475.269104a407.699336 407.699336 0 0 0-28.10396 147.129991c0 187.814632 138.43013 324.069797 334.774036 324.069797 197.879176 0 341.469517-136.255165 341.469518-324.112443 0-81.198696-9.936605-118.684859-48.275696-183.294116-25.587824-43.072838-38.381737-81.795746-44.181643-112.714367a144.272684 144.272684 0 0 0-15.864451 36.803821c-18.721758 66.10188-13.902718 143.718281-13.902718 144.571208a31.259792 31.259792 0 0 1-20.47026 31.259792 30.278926 30.278926 0 0 1-35.225905-11.301289c-2.430843-3.241124-56.975556-81.454575-81.028111-134.336078-49.896258-109.217364-49.213916-227.475759-44.181643-295.539373-50.5786 29.767169-128.408232 96.423452-153.526947 231.868336-24.308433 131.990528 22.943749 269.951548 23.412859 271.23094a31.430378 31.430378 0 0 1-5.586675 31.728902 30.278926 30.278926 0 0 1-30.278925 9.723373c-3.752881-0.93822-96.551391-23.668738-152.460788-112.970245a376.951301 376.951301 0 0 1-26.568691-54.118249z").toNodes(),
        )
    }.build()
}

private val BilibiliHistoryIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BilibiliHistory",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f,
    ).apply {
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString("M511.73808594 93.19941406c-140.67070313 0-264.92695313 69.1453125-341.01035156 175.08955078v-70.47597656c0-21.66767578-17.61679688-39.22998047-39.346875-39.22998047s-39.346875 17.56318359-39.346875 39.22998047v156.91992188c0 21.66240234 17.61679688 39.22998047 39.346875 39.22998047h170.50341796c21.73007813 0 39.346875-17.56669922 39.346875-39.22998047 0-21.66767578-17.61679688-39.22998047-39.346875-39.22998047h-68.4421875c61.73085937-86.98798828 163.28496094-143.84267578 278.296875-143.84267578 188.32851562 0 341.00507812 152.22128906 341.00507813 339.99345703 0 187.76865234-152.6765625 339.99345703-341.00507813 339.99345703-158.73574219 0-291.74589844-108.29179688-329.75771484-254.70439453l-0.68291016 0.21269531c-5.3015625-15.41162109-19.54248047-26.65986328-36.8024414-26.65986328-21.73007813 0-39.346875 17.56318359-39.346875 39.22558594 0 3.86630859 1.16367187 7.36875 2.20605469 10.89316406l-0.40957032 0.12919922c0.25839844 0.95009766 0.73212891 1.8 0.99580078 2.74482422 0.28916016 0.76201172 0.48164063 1.52314453 0.81650391 2.2640625 49.92539062 175.47011719 210.98935547 304.35380859 402.98378906 304.35380859 231.79394531 0 419.69970703-187.35029297 419.69970703-418.45341797-0.00175781-231.10751953-187.90839844-418.45429688-419.70322265-418.45429687z m0 176.53183594c-21.73007813 0-39.346875 17.56318359-39.346875 39.22998047v235.37988281c0 21.66767578 17.61679688 39.22998047 39.346875 39.22998047h170.50253906c21.73007813 0 39.346875-17.56318359 39.346875-39.22558594 0-21.67119141-17.61679688-39.22998047-39.346875-39.22998047H551.08496094v-196.15429687c0-21.66767578-17.61767578-39.22998047-39.34775391-39.22998047z").toNodes(),
        )
    }.build()
}

private val BilibiliFollowingIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BilibiliFollowing",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f,
    ).apply {
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString("M543.86133333 25.03466667l-0.064 316.74666666c47.56266667-54.64533333 117.62133333-89.19466667 195.76533334-89.19466666 143.27466667 0 259.41333333 116.13866667 259.41333333 259.41333333v31.86133333l-316.74666667-0.064c54.64533333 47.56266667 89.19466667 117.62133333 89.19466667 195.76533334 0 143.27466667-116.13866667 259.41333333-259.41333333 259.41333333h-31.86133334l0.02133334-316.704c-47.56266667 54.624-117.61066667 89.152-195.72266667 89.152-143.27466667 0-259.41333333-116.13866667-259.41333333-259.41333333v-31.86133334l316.74666666 0.064c-54.64533333-47.56266667-89.19466667-117.62133333-89.19466666-195.76533333 0-143.27466667 116.13866667-259.41333333 259.41333333-259.41333333h31.86133333z m0 521.41866666v386.16533334l5.33333334-0.88533334c87.28533333-16.78933333 153.94133333-91.57333333 158.28266666-182.688l0.224-9.48266666c0-95.37066667-68.21333333-174.79466667-158.50666666-192.17066667l-5.33333334-0.93866667z m-66.31466666-2.592H91.38133333l0.88533334 5.33333334c16.78933333 87.28533333 91.57333333 153.94133333 182.688 158.28266666l9.48266666 0.224c95.37066667 0 174.79466667-68.21333333 192.17066667-158.50666666l0.93866667-5.33333334z m262.00533333-227.56266666c-95.37066667 0-174.79466667 68.21333333-192.17066667 158.50666666l-0.93866666 5.33333334h386.16533333l-0.88533333-5.33333334c-16.78933333-87.28533333-91.57333333-153.94133333-182.688-158.28266666l-9.48266667-0.224z m-259.41333333-224.96l-5.33333334 0.93866666c-90.29333333 17.36533333-158.50666667 96.8-158.50666666 192.17066667l0.224 9.48266667c4.34133333 91.11466667 70.99733333 165.89866667 158.28266666 182.688l5.33333334 0.88533333V91.33866667z").toNodes(),
        )
    }.build()
}

private val BilibiliHomeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BilibiliHome",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f,
    ).apply {
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString("M85.333333 350.933333l42.389334 500.053334c1.429333 40.213333 34.56 72.704 74.538666 72.704h618.282667c40.192 0 73.088-32.704 74.56-74.88l0.128-2.261334 43.349333-493.653333v-1.493333c0-26.624-14.741333-51.413333-38.208-64.213334L558.848 98.581333a95.786667 95.786667 0 0 0-93.696-0.042666l-341.781333 189.866666A73.386667 73.386667 0 0 0 85.333333 350.933333z m735.210667 658.090667H202.261333c-86.208 0-156.757333-69.162667-159.701333-152.917333L0.149333 356.138667 0 352.512a158.549333 158.549333 0 0 1 82.218667-138.837333L423.808 23.893333a181.12 181.12 0 0 1 176.298667 0l341.333333 188.501334a158.698667 158.698667 0 0 1 82.474667 139.008v3.349333l-0.149334 3.733333-43.434666 494.485334c-3.626667 87.04-73.472 156.053333-159.786667 156.053333z").toNodes(),
        )
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString("M715.648 946.261333H626.346667V625.962667c0-12.288-6.698667-18.986667-8.938667-18.986667H404.266667c-2.24 0-8.938667 6.698667-8.938667 18.986667v320.298666h-89.28V625.962667c0-60.288 43.52-108.266667 98.218667-108.266667h213.162666c53.568 0 98.218667 49.109333 98.218667 108.266667v320.298666z").toNodes(),
        )
    }.build()
}

private val BilibiliMineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BilibiliMine",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f,
    ).apply {
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString("M306.005333 117.632L444.330667 256h135.296l138.368-138.325333a42.666667 42.666667 0 0 1 60.373333 60.373333L700.330667 256H789.333333A149.333333 149.333333 0 0 1 938.666667 405.333333v341.333334a149.333333 149.333333 0 0 1-149.333334 149.333333h-554.666666A149.333333 149.333333 0 0 1 85.333333 746.666667v-341.333334A149.333333 149.333333 0 0 1 234.666667 256h88.96L245.632 177.962667a42.666667 42.666667 0 0 1 60.373333-60.373334zM789.333333 341.333333h-554.666666a64 64 0 0 0-63.701334 57.856L170.666667 405.333333v341.333334a64 64 0 0 0 57.856 63.701333L234.666667 810.666667h554.666666a64 64 0 0 0 63.701334-57.856L853.333333 746.666667v-341.333334A64 64 0 0 0 789.333333 341.333333zM341.333333 469.333333a42.666667 42.666667 0 0 1 42.666667 42.666667v85.333333a42.666667 42.666667 0 0 1-85.333333 0v-85.333333a42.666667 42.666667 0 0 1 42.666666-42.666667z m341.333334 0a42.666667 42.666667 0 0 1 42.666666 42.666667v85.333333a42.666667 42.666667 0 0 1-85.333333 0v-85.333333a42.666667 42.666667 0 0 1 42.666667-42.666667z").toNodes(),
        )
    }.build()
}
