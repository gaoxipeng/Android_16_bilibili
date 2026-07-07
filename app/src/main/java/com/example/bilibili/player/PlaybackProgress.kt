package com.example.bilibili.player

fun resolveStoredProgressSeconds(
    coordinator: VideoPlaybackCoordinator,
    playbackId: String,
    serverProgressSeconds: Int = 0,
): Int {
    val localSeconds = listOf("feed", "detail").maxOf { ownerId ->
        coordinator.getPlaybackPosition(videoPlaybackKey(playbackId, ownerId = ownerId))
    } / 1000L
    return maxOf(localSeconds.toInt(), serverProgressSeconds.coerceAtLeast(0))
}

fun saveResolvedProgress(
    coordinator: VideoPlaybackCoordinator,
    playbackId: String,
    progressSeconds: Int,
    ownerId: String = "detail",
) {
    if (progressSeconds <= 0) return
    coordinator.savePlaybackPosition(
        videoPlaybackKey(playbackId, ownerId = ownerId),
        progressSeconds * 1000L,
    )
}
