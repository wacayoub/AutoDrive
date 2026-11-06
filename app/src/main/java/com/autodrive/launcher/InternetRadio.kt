package com.autodrive.launcher

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
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
internal class InternetRadio(private val context: Context, private val step: (Int) -> Unit = {}, private val changed: () -> Unit) {
    private var engine: ExoPlayer? = null
    private var session: MediaSession? = null
    private var canSkip = false
    val sessionToken: MediaSession.Token? get() = session?.sessionToken
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
        if (session == null) session = MediaSession(context, "AutoDriveRadio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { station?.let(::play) }
                override fun onPause() { pause() }
                override fun onStop() { close() }
                override fun onSkipToNext() { if (canSkip) step(1) }
                override fun onSkipToPrevious() { if (canSkip) step(-1) }
            }, Handler(Looper.getMainLooper()))
            isActive = true
        }
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
                        publishSession(); changed()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        if (engine !== it) return
                        failure = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Flux indisponible · vérifier Internet"
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Format audio incompatible"
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                                val code = generateSequence(error.cause) { cause -> cause.cause }.take(8)
                                    .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                                    .firstOrNull()?.responseCode
                                "Flux indisponible${code?.let { " · HTTP $it" }.orEmpty()}"
                            }
                            else -> "Flux indisponible · réessayer"
                        }
                        publishSession(); changed()
                    }
                })
            }
        // The broadcaster's published Aswat alias redirects to HTTP. Its verified canonical TLS
        // endpoint is used explicitly; do not enable arbitrary HTTP downgrades for other streams.
        val playbackUrl = if (value.url == "https://broadcast.ice.infomaniak.ch/aswat-high.mp3")
            "https://aswat.ice.infomaniak.ch/aswat-high.mp3" else value.url
        player.setMediaItem(MediaItem.Builder().setUri(playbackUrl).setMediaId(value.url).build())
        player.prepare()
        player.play()
        publishSession(); changed()
    }
    fun setCanSkip(value: Boolean) { if (canSkip != value) { canSkip = value; publishSession() } }
    fun pause() { engine?.pause(); engine?.stop(); publishSession(); changed() }
    fun toggle() {
        val player = engine
        if (player != null && player.playWhenReady && failure == null) pause()
        else station?.let(::play) // Reconnect to live audio rather than resuming an old buffer.
    }
    private fun publishSession() {
        val state = when {
            failure != null -> PlaybackState.STATE_ERROR
            playing -> PlaybackState.STATE_PLAYING
            requestedPlayback -> PlaybackState.STATE_BUFFERING
            engine != null -> PlaybackState.STATE_PAUSED
            else -> PlaybackState.STATE_STOPPED
        }
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
            if (canSkip) (PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS) else 0L
        session?.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f)
            .apply { failure?.let { setErrorMessage(it) } }.build())
        session?.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, station?.name)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, trackTitle ?: "Radio Internet").build())
    }
    fun close() {
        val old = engine
        engine = null
        session?.release(); session = null
        failure = null
        old?.release()
        changed()
    }
}
