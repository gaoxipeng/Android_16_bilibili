package com.example.bilibili.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal val LocalFeedTabReselectController = staticCompositionLocalOf<FeedTabReselectController?> { null }
internal val LocalFeedTabForReselect = staticCompositionLocalOf<MainTab?> { null }

internal class FeedTabReselectHandler(
    val isAtTop: () -> Boolean,
    val scrollToTop: suspend () -> Unit,
    val refresh: () -> Unit,
)

internal class FeedTabReselectController {
    private val handlers = mutableMapOf<MainTab, FeedTabReselectHandler>()

    fun register(tab: MainTab, handler: FeedTabReselectHandler) {
        handlers[tab] = handler
    }

    fun unregister(tab: MainTab) {
        handlers.remove(tab)
    }

    fun handleReselect(tab: MainTab, scope: CoroutineScope) {
        val handler = handlers[tab] ?: return
        if (handler.isAtTop()) {
            handler.refresh()
        } else {
            scope.launch { handler.scrollToTop() }
        }
    }

    companion object {
        val FEED_RESELECT_TABS = setOf(
            MainTab.Home,
            MainTab.Following,
            MainTab.Live,
            MainTab.History,
        )
    }
}

@Composable
internal fun rememberFeedTabReselectController(): FeedTabReselectController =
    remember { FeedTabReselectController() }

internal fun LazyListState.isScrolledToTop(): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

internal fun LazyGridState.isScrolledToTop(): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

@Composable
internal fun BindFeedTabReselectEffect(
    tab: MainTab,
    controller: FeedTabReselectController,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onScrolledToTop: () -> Unit = {},
) {
    DisposableEffect(tab, controller, listState, onRefresh, onScrolledToTop) {
        controller.register(
            tab,
            FeedTabReselectHandler(
                isAtTop = { listState.isScrolledToTop() },
                scrollToTop = {
                    listState.animateScrollToItem(0)
                    onScrolledToTop()
                },
                refresh = onRefresh,
            ),
        )
        onDispose { controller.unregister(tab) }
    }
}

internal fun LazyStaggeredGridState.isScrolledToTop(): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

@Composable
internal fun BindFeedTabReselectEffect(
    tab: MainTab,
    controller: FeedTabReselectController,
    staggeredGridState: LazyStaggeredGridState,
    onRefresh: () -> Unit,
    onScrolledToTop: () -> Unit = {},
) {
    DisposableEffect(tab, controller, staggeredGridState, onRefresh, onScrolledToTop) {
        controller.register(
            tab,
            FeedTabReselectHandler(
                isAtTop = { staggeredGridState.isScrolledToTop() },
                scrollToTop = {
                    staggeredGridState.animateScrollToItem(0)
                    onScrolledToTop()
                },
                refresh = onRefresh,
            ),
        )
        onDispose { controller.unregister(tab) }
    }
}
