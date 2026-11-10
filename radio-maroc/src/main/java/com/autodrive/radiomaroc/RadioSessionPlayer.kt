package com.autodrive.radiomaroc

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
internal class RadioSessionPlayer(private val engine: Player) : ForwardingSimpleBasePlayer(engine) {
    private var presentation: Pair<String, MediaMetadata>? = null

    fun present(mediaId: String?, metadata: MediaMetadata?) {
        val update = if (mediaId != null && metadata != null) mediaId to metadata else null
        if (presentation == update) return
        presentation = update
        invalidateState()
    }

    override fun getState(): State {
        val state = super.getState()
        val presentation = presentation?.takeIf { it.first == engine.currentMediaItem?.mediaId } ?: return state
        return state.buildUpon()
            .setPlaylist(state.timeline, state.currentTracks, presentation.second)
            .build()
    }
}
