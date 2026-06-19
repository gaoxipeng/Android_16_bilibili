package com.example.bilibili.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import com.example.bilibili.data.BiliPlayStream
import com.example.bilibili.data.BiliVideoItem

enum class VideoPeekDismissReason {
    Cancel,
    Release,
    PlaybackEnded,
    EnterFullscreen,
}

data class VideoPeekRequest(
    val video: BiliVideoItem,
    val playStream: BiliPlayStream,
    val anchorBounds: Rect,
    val expandFromAnchor: Boolean = false,
    val dockImmediately: Boolean = false,
    val onCancel: () -> Unit,
    val onRelease: () -> Unit,
    val onPlaybackEnded: () -> Unit,
    val onEnterFullscreenHandoffComplete: () -> Unit = {},
)

class VideoPeekController {
    var activeRequest by mutableStateOf<VideoPeekRequest?>(null)
    var pendingDismiss by mutableStateOf<VideoPeekDismissReason?>(null)
    var isFloating by mutableStateOf(false)
    var isFullscreenMode by mutableStateOf(false)

    fun open(request: VideoPeekRequest) {
        pendingDismiss = null
        isFloating = false
        isFullscreenMode = false
        activeRequest = request
    }

    fun openFloating(request: VideoPeekRequest) {
        pendingDismiss = null
        isFloating = true
        isFullscreenMode = false
        activeRequest = request
    }

    fun cancel() {
        if (activeRequest != null && pendingDismiss == null) {
            pendingDismiss = VideoPeekDismissReason.Cancel
        }
    }

    fun release() {
        if (activeRequest != null && pendingDismiss == null) {
            isFloating = true
        }
    }

    fun dismissForPlaybackEnded() {
        if (activeRequest != null && pendingDismiss == null) {
            pendingDismiss = VideoPeekDismissReason.PlaybackEnded
        }
    }

    fun enterFullscreen() {
        if (activeRequest != null && pendingDismiss == null && !isFullscreenMode) {
            pendingDismiss = VideoPeekDismissReason.EnterFullscreen
        }
    }

    fun completeDismiss() {
        val request = activeRequest
        val reason = pendingDismiss
        activeRequest = null
        pendingDismiss = null
        isFloating = false
        isFullscreenMode = false
        when (reason) {
            VideoPeekDismissReason.Cancel -> request?.onCancel?.invoke()
            VideoPeekDismissReason.Release -> request?.onRelease?.invoke()
            VideoPeekDismissReason.PlaybackEnded -> request?.onPlaybackEnded?.invoke()
            VideoPeekDismissReason.EnterFullscreen -> Unit
            null -> Unit
        }
    }

    fun completeFullscreenExpand() {
        if (pendingDismiss != VideoPeekDismissReason.EnterFullscreen) return
        pendingDismiss = null
        isFloating = false
        isFullscreenMode = true
        activeRequest?.onEnterFullscreenHandoffComplete?.invoke()
    }
}

val LocalVideoPeekController = staticCompositionLocalOf { VideoPeekController() }
