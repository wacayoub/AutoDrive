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
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
internal class PlaybackService : MediaSessionService() {
    companion object {
        const val NETWORK_UNAVAILABLE = "com.autodrive.radiomaroc.NETWORK_UNAVAILABLE"
        const val STREAM_STATION = "com.autodrive.radiomaroc.STREAM_STATION"
        const val STREAM_TITLE = "com.autodrive.radiomaroc.STREAM_TITLE"
    }

    private var session: MediaSession? = null
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

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.isPlaying) retryAfterOutage = false
            recoverPlayback(player)
        }
    }

    override fun onCreate() {
        super.onCreate()
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
            val origin = when {
                request.uri.scheme != "https" -> null
                request.uri.host == Stations.RADIO_2M_STREAM_HOST &&
                    request.uri.path?.startsWith("/live/eds/radio_2m/") == true -> "https://radio2m.ma"
                request.uri.host == Stations.SNRT_STREAM_HOST -> "https://snrtlive.ma"
                else -> null
            }
            if (origin == null) request else request.withAdditionalHeaders(
                mapOf("Origin" to origin, "Referer" to "$origin/"),
            )
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
                override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(
                    mediaItems.mapNotNull { item ->
                        val station = Stations.all.firstOrNull { it.url == item.mediaId }
                        when {
                            station != null -> Stations.item(station)
                            controller.uid == applicationInfo.uid && controller.packageName == packageName -> Stations.directoryItem(item)
                            else -> null
                        }
                    }
                )
            }).build()
        connectivity.registerDefaultNetworkCallback(networkCallback, Handler(Looper.getMainLooper()))
    }

    private fun sessionExtras() = Bundle().apply {
        putBoolean(NETWORK_UNAVAILABLE, networkUnavailable)
        streamInfo?.let { (station, title) ->
            putString(STREAM_STATION, station)
            putString(STREAM_TITLE, title)
        }
    }

    private fun updateStreamInfo(info: Pair<String, String>?) {
        if (streamInfo == info) return
        streamInfo = info
        session?.setSessionExtras(sessionExtras())
    }

    private fun setNetworkUnavailable(unavailable: Boolean) {
        val current = session ?: return
        if (networkUnavailable == unavailable) return
        networkUnavailable = unavailable
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
        session?.let {
            it.player.removeListener(recoveryListener)
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
