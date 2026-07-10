package com.example.bilibili.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import com.example.bilibili.R
import com.google.common.collect.ImmutableList

internal class VideoPlaybackMediaSessionCallback(
    private val context: Context,
    private val controlsProvider: () -> MediaEpisodeControls,
) : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val controls = controlsProvider()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(buildAvailablePlayerCommands(controls))
            .setCustomLayout(buildEpisodeControlButtons(context, controls))
            .build()
    }

    companion object {
        fun buildAvailablePlayerCommands(controls: MediaEpisodeControls): Player.Commands {
            return MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                .buildUpon()
                .remove(Player.COMMAND_SEEK_BACK)
                .remove(Player.COMMAND_SEEK_FORWARD)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .apply {
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

        fun buildEpisodeControlButtons(
            context: Context,
            controls: MediaEpisodeControls,
        ): ImmutableList<CommandButton> {
            if (!controls.isMultiEpisode) {
                return ImmutableList.of()
            }
            return ImmutableList.of(
                CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .setDisplayName(context.getString(R.string.media_previous_episode))
                    .setEnabled(controls.hasPrevious)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .setDisplayName(context.getString(R.string.media_next_episode))
                    .setEnabled(controls.hasNext)
                    .build(),
            )
        }
    }
}
