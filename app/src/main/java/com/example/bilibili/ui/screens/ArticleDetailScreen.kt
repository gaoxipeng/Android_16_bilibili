package com.example.bilibili.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.bilibili.ui.components.BiliWebReaderOverlay
import com.example.bilibili.ui.components.BiliWebReaderState

@Composable
fun ArticleDetailScreen(
    webUrl: String,
    seedTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(webUrl, seedTitle) {
        BiliWebReaderState(
            url = webUrl,
            title = seedTitle,
        )
    }

    BiliWebReaderOverlay(
        state = state,
        onBack = onBack,
        modifier = modifier,
        showHeader = false,
    )
}
