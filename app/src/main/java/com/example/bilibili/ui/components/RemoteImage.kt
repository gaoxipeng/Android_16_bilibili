package com.example.bilibili.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackUrls: List<String> = emptyList(),
) {
    var currentUrlIndex by remember(url, fallbackUrls) { mutableIntStateOf(0) }
    val urls = remember(url, fallbackUrls) {
        buildList {
            add(url)
            addAll(fallbackUrls.filter { it.isNotBlank() && it != url })
        }.distinct()
    }
    val model = urls.getOrElse(currentUrlIndex) { url }
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        onError = {
            if (currentUrlIndex < urls.lastIndex) {
                currentUrlIndex += 1
            }
        },
    )
}
