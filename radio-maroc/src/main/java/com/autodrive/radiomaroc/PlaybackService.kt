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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
internal class PlaybackService : MediaSessionService() {
    companion object {
        const val NETWORK_UNAVAILABLE = "com.autodrive.radiomaroc.NETWORK_UNAVAILABLE"
    }

    private var session: MediaSession? = null
    private lateinit var connectivity: ConnectivityManager
    private var validatedNetwork: Network? = null
    private var networkUnavailable = false
    private var retryAfterOutage = false
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
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(network))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(3_000, 12_000, 900, 1_600).build())
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(recoveryListener)
        val reopen = PendingIntent.getActivity(this, 0, Intent(this, RadioActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(reopen)
            .setSessionExtras(networkExtras())
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(
                    mediaItems.mapNotNull { item ->
                        val station = Stations.all.firstOrNull { it.url == item.mediaId }
                        when {
                            station != null -> if (Stations.officialPlayer(station) == null) Stations.item(station) else null
                            controller.uid == applicationInfo.uid && controller.packageName == packageName -> Stations.directoryItem(item)
                            else -> null
                        }
                    }
                )
            }).build()
        connectivity.registerDefaultNetworkCallback(networkCallback, Handler(Looper.getMainLooper()))
    }

    private fun networkExtras() = Bundle().apply { putBoolean(NETWORK_UNAVAILABLE, networkUnavailable) }

    private fun setNetworkUnavailable(unavailable: Boolean) {
        val current = session ?: return
        if (networkUnavailable == unavailable) return
        networkUnavailable = unavailable
        current.setSessionExtras(networkExtras())
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
