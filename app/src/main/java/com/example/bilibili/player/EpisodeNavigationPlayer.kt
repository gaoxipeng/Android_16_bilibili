package com.example.bilibili.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

class EpisodeNavigationPlayer(
    player: Player,
) : ForwardingPlayer(player) {
    @Volatile
    var controls: MediaEpisodeControls = MediaEpisodeControls.EMPTY

    private val availableCommandsListeners = LinkedHashSet<Player.Listener>()

    fun updateControls(newControls: MediaEpisodeControls) {
        val previousCommands = availableCommands
        controls = newControls
        val nextCommands = availableCommands
        if (previousCommands != nextCommands) {
            val event = Player.Events(
                FlagSet.Builder().add(Player.EVENT_AVAILABLE_COMMANDS_CHANGED).build(),
            )
            availableCommandsListeners.forEach { listener ->
                listener.onEvents(this, event)
            }
        }
    }

    override fun addListener(listener: Player.Listener) {
        availableCommandsListeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        availableCommandsListeners.remove(listener)
        super.removeListener(listener)
    }

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands()
            .buildUpon()
            .remove(Player.COMMAND_SEEK_BACK)
            .remove(Player.COMMAND_SEEK_FORWARD)
            .apply {
                remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                remove(Player.COMMAND_SEEK_TO_NEXT)
                remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                if (controls.isMultiEpisode) {
                    if (controls.hasPrevious) {
                        add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    }
                    if (controls.hasNext) {
                        add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        add(Player.COMMAND_SEEK_TO_NEXT)
                    }
                }
            }
            .build()
    }

    override fun seekToPrevious() {
        VideoPlaybackMediaBridge.dispatchEpisodeNavigation(previous = true)
    }

    override fun seekToPreviousMediaItem() {
        VideoPlaybackMediaBridge.dispatchEpisodeNavigation(previous = true)
    }

    override fun seekToNext() {
        VideoPlaybackMediaBridge.dispatchEpisodeNavigation(previous = false)
    }

    override fun seekToNextMediaItem() {
        VideoPlaybackMediaBridge.dispatchEpisodeNavigation(previous = false)
    }
}
