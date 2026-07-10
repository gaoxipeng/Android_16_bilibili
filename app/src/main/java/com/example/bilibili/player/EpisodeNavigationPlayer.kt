package com.example.bilibili.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * 仅向 MediaSession 暴露切集命令；播放/暂停/进度仍直接委托给 ExoPlayer。
 * 单条 MediaItem 时 ExoPlayer 不会提供 SEEK_TO_NEXT，通知栏会因此隐藏切集按钮。
 */
internal class EpisodeNavigationPlayer(
    player: ExoPlayer,
    private val controlsProvider: () -> MediaEpisodeControls,
) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (!controlsProvider().isMultiEpisode) return base
        return base.buildUpon()
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .build()
    }
}
