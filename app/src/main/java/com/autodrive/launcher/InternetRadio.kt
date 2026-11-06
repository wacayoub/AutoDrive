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
    private var failure: String? = null
    val playing get() = engine?.isPlaying == true
    val requestedPlayback get() = engine?.playWhenReady == true && failure == null
    val status: String get() = when {
        failure != null -> failure!!
        playing -> "En lecture · Internet"
        engine != null && engine?.playWhenReady == false -> "En pause"
        engine?.playbackState == Player.STATE_BUFFERING -> "Connexion / mise en mémoire…"
        engine?.playbackState == Player.STATE_READY && engine?.playWhenReady == true -> "Lecture suspendue"
        engine?.playbackState == Player.STATE_READY -> "En pause"
        else -> "Prêt à écouter"
    }
    val trackTitle get() = engine?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
    fun play(value: RadioStation) {
        if (RadioStation.validUrl(value.url) == null) return
        station = value
        failure = null
        val player = engine ?: ExoPlayer.Builder(context)
            .setTrackSelector(DefaultTrackSelector(context).apply {
                parameters = buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
            })
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).build().also {
                engine = it
                it.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (player !== engine) return
                        if (player.playbackState == Player.STATE_READY) failure = null
                        changed()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        if (engine !== it) return
                        failure = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Flux indisponible · vérifier Internet"
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Format audio incompatible"
                            else -> "Flux indisponible · réessayer"
                        }
                        changed()
                    }
                })
            }
        player.setMediaItem(MediaItem.Builder().setUri(value.url).setMediaId(value.url).build())
        player.prepare()
        player.play()
        changed()
    }
    fun toggle() {
        val player = engine
        if (player != null && player.playWhenReady && failure == null) { player.pause(); player.stop() }
        else station?.let(::play) // Reconnect to live audio rather than resuming an old buffer.
    }
    fun close() {
        val old = engine
        engine = null
        failure = null
        old?.release()
        changed()
    }
}
