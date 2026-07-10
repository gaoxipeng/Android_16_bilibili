package com.example.bilibili.player

import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import java.util.IdentityHashMap

class EpisodeNavigationPlayer(
    player: Player,
) : ForwardingPlayer(player) {
    @Volatile
    var controls: MediaEpisodeControls = MediaEpisodeControls.EMPTY

    @Volatile
    private var mediaMetadataOverride: MediaMetadata? = null

    private val listenerWrappers = IdentityHashMap<Player.Listener, Player.Listener>()

    fun updateControls(newControls: MediaEpisodeControls) {
        val previousCommands = availableCommands
        controls = newControls
        val nextCommands = availableCommands
        if (previousCommands != nextCommands) {
            notifyListenerEvent(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)
        }
    }

    fun updateMediaMetadata(mediaMetadata: MediaMetadata) {
        mediaMetadataOverride = mediaMetadata
        listenerWrappers.keys.toList().forEach { listener ->
            listener.onMediaMetadataChanged(mediaMetadata)
            listener.onEvents(
                this,
                Player.Events(FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build()),
            )
        }
    }

    override fun getMediaMetadata(): MediaMetadata {
        return mediaMetadataOverride ?: super.getMediaMetadata()
    }

    private fun notifyListenerEvent(event: Int) {
        val eventSet = Player.Events(FlagSet.Builder().add(event).build())
        listenerWrappers.keys.toList().forEach { listener ->
            if (event == Player.EVENT_AVAILABLE_COMMANDS_CHANGED) {
                listener.onAvailableCommandsChanged(availableCommands)
            }
            listener.onEvents(this, eventSet)
        }
    }

    override fun addListener(listener: Player.Listener) {
        if (listenerWrappers.containsKey(listener)) return
        val wrapper = object : Player.Listener by listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                listener.onAvailableCommandsChanged(this@EpisodeNavigationPlayer.availableCommands)
            }
        }
        listenerWrappers[listener] = wrapper
        super.addListener(wrapper)
    }

    override fun removeListener(listener: Player.Listener) {
        val wrapper = listenerWrappers.remove(listener) ?: return
        super.removeListener(wrapper)
    }

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

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
                    add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    add(Player.COMMAND_SEEK_TO_NEXT)
                }
                add(Player.COMMAND_GET_METADATA)
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
