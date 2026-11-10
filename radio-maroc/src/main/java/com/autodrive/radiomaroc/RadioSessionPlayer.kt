package com.autodrive.radiomaroc

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
internal class RadioSessionPlayer(
    private val engine: Player,
    private val canChangeStation: () -> Boolean,
    private val changeStation: (Int) -> Unit,
) : ForwardingSimpleBasePlayer(engine) {
    private var presentation: Pair<String, MediaMetadata>? = null

    fun present(mediaId: String?, metadata: MediaMetadata?) {
        val update = if (mediaId != null && metadata != null) mediaId to metadata else null
        if (presentation == update) return
        presentation = update
        invalidateState()
    }

    override fun getState(): State {
        val state = super.getState()
        val commands = state.availableCommands.buildUpon()
        val canSkip = canChangeStation()
        for (command in stationCommands) {
            if (canSkip) commands.add(command) else commands.remove(command)
        }
        return state.buildUpon().setAvailableCommands(commands.build()).apply {
            presentation?.takeIf { it.first == engine.currentMediaItem?.mediaId }?.let {
                setPlaylist(state.timeline, state.currentTracks, it.second)
            }
        }.build()
    }

    fun refreshStationCommands() = invalidateState()

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (seekCommand !in stationCommands) return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        val previous = seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS ||
            seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        changeStation(if (previous) -1 else 1)
        return Futures.immediateVoidFuture()
    }

    private companion object {
        val stationCommands = intArrayOf(
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        )
    }
}
