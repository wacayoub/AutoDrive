package com.autodrive.radiomaroc

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

internal class LuxeRadio(private val context: Context, private val onState: (State, String?) -> Unit) {
    enum class State { IDLE, CONNECTING, PLAYING, PAUSED, MISSING, SETUP, UNAVAILABLE }

    companion object {
        const val PACKAGE = "app.luxeradio.ma"
        private const val SERVICE = "ma.luxeradio.auto.AutoService"
        private const val RADIO_FOLDER = "radioTab"
        private const val LIVE_ID = "radioTab_live-streaming"
        private const val CERTIFICATE = "270e78e9fc529de6ee0c1946b84887278d6e7dad8d35571d42c709bb9bd85b10"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var browser: MediaBrowser? = null
    private var controller: MediaController? = null
    private var liveId: String? = null
    private var liveTitle: String? = null
    private var confirmedLive = false
    private var pendingPlay = false
    private var awaitingPlayback = false
    private var state = State.IDLE
    private var programme: String? = null
    private val timeout = Runnable { fail(State.UNAVAILABLE) }
    private val sessionCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = updatePlayback()
        override fun onMetadataChanged(metadata: MediaMetadata?) = updatePlayback()
        override fun onSessionDestroyed() = fail(State.UNAVAILABLE)
    }

    fun observe() {
        if (browser == null) connect()
    }

    fun play() {
        pendingPlay = true
        if (browser == null) connect() else requestPlayback()
    }

    fun pause() {
        pendingPlay = false
        if (awaitingPlayback || isLive()) controller?.transportControls?.pause()
        awaitingPlayback = false
        handler.removeCallbacks(timeout)
        setState(State.PAUSED)
    }

    fun leave() {
        pause()
        close()
        setState(State.IDLE)
    }

    fun close() {
        handler.removeCallbacks(timeout)
        controller?.unregisterCallback(sessionCallback)
        controller = null
        val oldBrowser = browser
        browser = null
        oldBrowser?.disconnect()
        liveId = null
        liveTitle = null
        confirmedLive = false
        pendingPlay = false
        awaitingPlayback = false
    }

    private fun connect() {
        val pm = context.packageManager
        try {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(PACKAGE, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            fail(State.MISSING)
            return
        }
        val certificate = CERTIFICATE.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (!pm.hasSigningCertificate(PACKAGE, certificate, PackageManager.CERT_INPUT_SHA256)) {
            fail(State.UNAVAILABLE)
            return
        }

        lateinit var connection: MediaBrowser
        connection = MediaBrowser(context, ComponentName(PACKAGE, SERVICE), object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                if (browser !== connection) return
                controller = MediaController(context, connection.sessionToken).also {
                    it.registerCallback(sessionCallback, handler)
                }
                subscribe(connection, connection.root)
            }

            override fun onConnectionFailed() {
                if (browser === connection) fail(State.UNAVAILABLE)
            }

            override fun onConnectionSuspended() = onConnectionFailed()
        }, null)
        browser = connection
        waitForConnection()
        connection.connect()
    }

    private fun subscribe(connection: MediaBrowser, id: String) {
        connection.subscribe(id, object : MediaBrowser.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String, children: List<MediaBrowser.MediaItem>) {
                if (browser !== connection) return
                connection.unsubscribe(parentId, this)
                val folder = children.firstOrNull { it.isBrowsable && it.mediaId == RADIO_FOLDER }
                if (parentId == connection.root && folder != null) {
                    subscribe(connection, RADIO_FOLDER)
                    return
                }
                val live = children.firstOrNull { it.isPlayable && it.mediaId == LIVE_ID }
                liveId = live?.mediaId
                liveTitle = live?.description?.title?.toString()
                when {
                    liveId == null -> fail(State.UNAVAILABLE)
                    pendingPlay -> requestPlayback()
                    else -> updatePlayback()
                }
            }

            override fun onError(parentId: String) {
                if (browser === connection) fail(State.SETUP)
            }
        })
    }

    private fun requestPlayback() {
        val id = liveId ?: run { waitForConnection(); return }
        val controls = controller?.transportControls ?: return
        pendingPlay = false
        awaitingPlayback = true
        waitForConnection()
        controls.playFromMediaId(id, null)
        updatePlayback()
    }

    private fun isLive(): Boolean {
        val current = controller ?: return false
        val metadata = current.metadata ?: return false
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (!id.isNullOrEmpty()) return id == LIVE_ID
        val actions = current.playbackState?.actions ?: return false
        if (actions and PlaybackState.ACTION_SEEK_TO != 0L || metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) > 0L) {
            confirmedLive = false
            return false
        }
        // The provider omits the ID from session metadata and uses the current programme as its live item title.
        return confirmedLive || liveTitle?.equals(metadata.description.title?.toString()) == true
    }

    private fun updatePlayback() {
        if (liveId == null || pendingPlay) return
        val playback = controller?.playbackState?.state
        when {
            playback == PlaybackState.STATE_ERROR -> fail(State.UNAVAILABLE)
            isLive() && playback == PlaybackState.STATE_PLAYING -> {
                confirmedLive = true
                awaitingPlayback = false
                handler.removeCallbacks(timeout)
                val description = controller?.metadata?.description
                val title = description?.title?.toString()?.takeUnless { it.equals("Luxe Radio", ignoreCase = true) }
                    ?: description?.subtitle?.toString()
                setState(State.PLAYING, title?.trim()?.takeIf { it.isNotEmpty() && it.length <= 160 })
            }
            playback == PlaybackState.STATE_BUFFERING && (awaitingPlayback || isLive()) -> waitForConnection()
            !awaitingPlayback -> {
                handler.removeCallbacks(timeout)
                setState(State.PAUSED)
            }
        }
    }

    private fun waitForConnection() {
        setState(State.CONNECTING)
        if (!handler.hasCallbacks(timeout)) handler.postDelayed(timeout, 15_000)
    }

    private fun fail(state: State) {
        close()
        setState(state)
    }

    private fun setState(value: State, title: String? = null) {
        if (state == value && programme == title) return
        state = value
        programme = title
        onState(value, title)
    }
}
