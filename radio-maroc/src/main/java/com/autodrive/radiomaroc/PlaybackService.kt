package com.autodrive.radiomaroc

import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
internal class PlaybackService : MediaSessionService() {
    companion object {
        const val NETWORK_UNAVAILABLE = "com.autodrive.radiomaroc.NETWORK_UNAVAILABLE"
        const val STREAM_STATION = "com.autodrive.radiomaroc.STREAM_STATION"
        const val STREAM_TITLE = "com.autodrive.radiomaroc.STREAM_TITLE"
        const val LUXE_STATE = "com.autodrive.radiomaroc.LUXE_STATE"
        val LUXE_OBSERVE = SessionCommand("com.autodrive.radiomaroc.LUXE_OBSERVE", Bundle.EMPTY)
        val LUXE_PLAY = SessionCommand("com.autodrive.radiomaroc.LUXE_PLAY", Bundle.EMPTY)
        val LUXE_PAUSE = SessionCommand("com.autodrive.radiomaroc.LUXE_PAUSE", Bundle.EMPTY)
    }

    private var session: MediaSession? = null
    private lateinit var luxe: LuxeRadio
    private var luxeState = LuxeRadio.State.IDLE
    private var luxeProgramme: String? = null
    private val snrtAccess = SnrtAccess()
    private lateinit var connectivity: ConnectivityManager
    private var validatedNetwork: Network? = null
    private var networkUnavailable = false
    private var retryAfterOutage = false
    private var streamInfo: Pair<String, String>? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (validated) validatedNetwork = network
            setNetworkUnavailable(!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                (validatedNetwork == network && !validated))
        }

        override fun onLost(network: Network) {
            if (validatedNetwork == network) validatedNetwork = null
            setNetworkUnavailable(true)
        }
    }

    private val recoveryListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateStreamInfo(null)
        }

        override fun onMetadata(metadata: Metadata) {
            val icy = (0 until metadata.length()).firstNotNullOfOrNull { metadata[it] as? IcyInfo } ?: return
            val station = session?.player?.currentMediaItem?.mediaId ?: return
            val title = icy.title?.trim()?.takeIf { it.isNotEmpty() && it.length <= 160 }
            updateStreamInfo(title?.let { station to it })
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = session?.player?.currentMediaItem?.mediaId
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && Stations.snrt.any { it.url == id }) {
                snrtAccess.clear()
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.isPlaying) retryAfterOutage = false
            recoverPlayback(player)
        }
    }

    override fun onCreate() {
        super.onCreate()
        luxe = LuxeRadio(this) { state, programme ->
            luxeState = state
            luxeProgramme = programme
            session?.setSessionExtras(sessionExtras())
        }
        connectivity = getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivity.activeNetwork
        val capabilities = connectivity.getNetworkCapabilities(activeNetwork)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
            validatedNetwork = activeNetwork
        }
        networkUnavailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true

        val network = DefaultHttpDataSource.Factory()
            .setUserAgent("RadioMarocCoran/1.0.0")
            .setConnectTimeoutMs(7_000)
            .setReadTimeoutMs(10_000)
        val broadcastNetwork = ResolvingDataSource.Factory(network) { request ->
            val authorized = snrtAccess.authorize(request.uri)
            if (authorized != request.uri) request.withUri(authorized)
            else if (request.uri.scheme == "https" && request.uri.host == Stations.RADIO_2M_STREAM_HOST &&
                request.uri.path?.startsWith("/live/eds/radio_2m/") == true) request.withAdditionalHeaders(
                mapOf("Origin" to "https://radio2m.ma", "Referer" to "https://radio2m.ma/"),
            ) else request
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(broadcastNetwork))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(3_000, 12_000, 900, 1_600).build())
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(recoveryListener)
        val reopen = PendingIntent.getActivity(this, 0, Intent(this, RadioActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(reopen)
            .setSessionExtras(sessionExtras())
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    if (!isOwnController(controller)) return super.onConnect(session, controller)
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(LUXE_OBSERVE).add(LUXE_PLAY).add(LUXE_PAUSE).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands).build()
                }

                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    if (!isOwnController(controller)) return super.onCustomCommand(session, controller, customCommand, args)
                    when (customCommand) {
                        LUXE_OBSERVE -> luxe.observe()
                        LUXE_PLAY -> {
                            session.player.pause()
                            session.player.stop()
                            session.player.clearMediaItems()
                            luxe.play()
                        }
                        LUXE_PAUSE -> luxe.pause()
                        else -> return super.onCustomCommand(session, controller, customCommand, args)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
                    val resolved = mediaItems.mapNotNull { item ->
                        val station = Stations.all.firstOrNull { it.url == item.mediaId }
                        when {
                            station == Stations.luxe -> null
                            station != null -> Stations.item(station)
                            isOwnController(controller) -> Stations.directoryItem(item)
                            else -> null
                        }
                    }
                    if (resolved.isNotEmpty() && luxeState != LuxeRadio.State.IDLE) luxe.leave()
                    return Futures.immediateFuture(resolved)
                }
            }).build()
        connectivity.registerDefaultNetworkCallback(networkCallback, Handler(Looper.getMainLooper()))
    }

    private fun sessionExtras() = Bundle().apply {
        putBoolean(NETWORK_UNAVAILABLE, networkUnavailable)
        putString(LUXE_STATE, luxeState.name)
        val currentInfo = luxeProgramme?.let { Stations.luxe.url to it } ?: streamInfo
        currentInfo?.let { (station, title) ->
            putString(STREAM_STATION, station)
            putString(STREAM_TITLE, title)
        }
    }

    private fun isOwnController(controller: MediaSession.ControllerInfo): Boolean =
        controller.uid == applicationInfo.uid && controller.packageName == packageName

    private fun updateStreamInfo(info: Pair<String, String>?) {
        if (streamInfo == info) return
        streamInfo = info
        session?.setSessionExtras(sessionExtras())
    }

    private fun setNetworkUnavailable(unavailable: Boolean) {
        val current = session ?: return
        if (networkUnavailable == unavailable) return
        networkUnavailable = unavailable
        if (unavailable) snrtAccess.clear()
        current.setSessionExtras(sessionExtras())
        recoverPlayback(current.player)
    }

    private fun recoverPlayback(player: Player) {
        if (!player.playWhenReady || player.currentMediaItem == null || player.playbackState == Player.STATE_ENDED) {
            retryAfterOutage = false
            return
        }
        val error = player.playerError?.errorCode
        val networkError = error == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        if (networkUnavailable) {
            retryAfterOutage = error == null || networkError
        } else if (retryAfterOutage && networkError) {
            // Keep the current item's position and let the player retain its play/pause intent.
            retryAfterOutage = false
            player.prepare()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        connectivity.unregisterNetworkCallback(networkCallback)
        luxe.close()
        session?.let {
            it.player.removeListener(recoveryListener)
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
