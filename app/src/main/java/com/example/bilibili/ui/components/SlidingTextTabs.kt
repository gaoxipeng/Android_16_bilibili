package com.example.bilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilibili.ui.theme.BiliPink
import kotlin.math.roundToInt

@Composable
fun SlidingTextTabs(
    labels: List<String>,
    scrollPosition: Float,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, top = 2.dp, bottom = 2.dp),
    tabSpacing: Dp = 22.dp,
    accent: Color = BiliPink,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (labels.isEmpty()) return

    val density = LocalDensity.current
    val indicatorWidth = 22.dp
    val tabCentersPx = remember(labels) { FloatArray(labels.size) { Float.NaN } }
    var layoutReady by remember(labels) { mutableStateOf(false) }
    val highlightedIndex = scrollPosition
        .roundToInt()
        .coerceIn(0, labels.lastIndex)

    val indicatorCenterPx = if (!layoutReady || labels.size == 1) {
        tabCentersPx.firstOrNull { !it.isNaN() } ?: 0f
    } else {
        val position = scrollPosition.coerceIn(0f, labels.lastIndex.toFloat())
        val left = position.toInt()
        val right = (left + 1).coerceAtMost(labels.lastIndex)
        val fraction = position - left
        tabCentersPx[left] + (tabCentersPx[right] - tabCentersPx[left]) * fraction
    }
    val indicatorOffset = with(density) { indicatorCenterPx.toDp() - indicatorWidth / 2 }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(tabSpacing),
            verticalAlignment = Alignment.Bottom,
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == highlightedIndex
                Column(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            tabCentersPx[index] = coords.positionInParent().x + coords.size.width / 2f
                            if (!layoutReady && tabCentersPx.all { !it.isNaN() }) {
                                layoutReady = true
                            }
                        }
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) accent else unselectedColor,
                    )
                    Spacer(Modifier.height(3.dp))
                }
            }
        }

        if (layoutReady) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent),
            )
        }
    }
}
