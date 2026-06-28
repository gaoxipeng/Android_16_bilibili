package com.example.bilibili.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ObserveListNearEnd(
    listState: LazyListState,
    enabled: Boolean = true,
    onNearEnd: () -> Unit,
) {
    LaunchedEffect(listState, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 4) {
                    onNearEnd()
                }
            }
    }
}
