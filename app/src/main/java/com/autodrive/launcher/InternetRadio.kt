package com.autodrive.launcher

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

@OptIn(UnstableApi::class)
internal class InternetRadio(private val context: Context, private val changed: () -> Unit) {
    private var engine: ExoPlayer? = null
    var station: RadioStation? = null
        private set
    private var failed = false
    val playing get() = engine?.isPlaying == true
    val status: String get() = when {
        failed -> "Flux indisponible · réessayer"
        playing -> "En lecture · Internet"
        engine?.playbackState == Player.STATE_BUFFERING -> "Connexion / mise en mémoire…"
        engine?.playbackState == Player.STATE_READY && engine?.playWhenReady == true -> "Lecture suspendue"
        engine?.playbackState == Player.STATE_READY -> "En pause"
        else -> "Prêt à écouter"
    }
    val trackTitle get() = engine?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
    fun play(value: RadioStation) {
        if (RadioStation.validUrl(value.url) == null) return
        station = value
        failed = false
        val player = engine ?: ExoPlayer.Builder(context)
            .setTrackSelector(DefaultTrackSelector(context).apply {
                parameters = buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
            })
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).build().also {
                engine = it
                it.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = changed()
                    override fun onPlayerError(error: PlaybackException) { failed = true; changed() }
                })
            }
        player.setMediaItem(MediaItem.fromUri(value.url))
        player.prepare()
        player.play()
        changed()
    }
    fun toggle() {
        val player = engine
        if (player != null && !failed && player.playbackState == Player.STATE_READY) {
            if (player.playWhenReady) player.pause() else player.play()
        } else station?.let(::play)
    }
    fun close() {
        val old = engine
        engine = null
        failed = false
        old?.release()
        changed()
    }
}
