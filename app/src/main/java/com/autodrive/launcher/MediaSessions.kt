package com.autodrive.launcher

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService

class MediaAccessService : NotificationListenerService()

internal data class MediaState(
    val title: String? = null,
    val subtitle: String? = null,
    val artwork: Bitmap? = null,
    val status: String,
    val actions: Long = 0,
    val playing: Boolean = false,
    val durationMs: Long = 0,
    val positionMs: Long = -1,
    val favorites: List<Pair<String, String>> = emptyList()
) {
    fun supports(action: Long) = actions and action != 0L
}

internal class MediaSessions(context: Context, private val changed: () -> Unit) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val listenerComponent = ComponentName(context, MediaAccessService::class.java)
    private var controller: MediaController? = null
    private var available: List<MediaController> = emptyList()
    private var packageName: String? = null
    private var observing = false
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = changed()
        override fun onMetadataChanged(metadata: MediaMetadata?) = changed()
        override fun onSessionDestroyed() {
            available = available.filterNot { it.sessionToken == controller?.sessionToken }
            connect()
            changed()
        }
    }
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener {
        available = it.orEmpty()
        connect()
        changed()
    }

    fun hasAccess() = notifications.isNotificationListenerAccessGranted(listenerComponent)

    fun start() {
        if (observing || !hasAccess()) return
        try {
            manager.addOnActiveSessionsChangedListener(listener, listenerComponent)
            observing = true
            available = manager.getActiveSessions(listenerComponent)
            connect()
        } catch (_: SecurityException) {
            stop()
        }
    }

    fun stop() {
        if (observing) manager.removeOnActiveSessionsChangedListener(listener)
        observing = false
        available = emptyList()
        connect()
    }

    fun select(name: String?) {
        packageName = name
        connect()
    }

    private fun connect() {
        val next = available.firstOrNull { it.packageName == packageName }
        if (controller?.sessionToken == next?.sessionToken) return
        controller?.unregisterCallback(callback)
        controller = next
        next?.registerCallback(callback)
    }

    fun snapshot(): MediaState {
        if (!hasAccess()) return MediaState(status = "Media access is off")
        val current = controller ?: return MediaState(status = "No active media session")
        val metadata = current.metadata
        val playback = current.playbackState
        val playing = playback?.state == PlaybackState.STATE_PLAYING
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playback?.position ?: -1L
        val updatedAt = playback?.lastPositionUpdateTime ?: 0L
        val elapsed = (SystemClock.elapsedRealtime() - updatedAt).coerceAtLeast(0L)
        val progress = if (playing && position >= 0 && updatedAt > 0) {
            position + (elapsed * playback!!.playbackSpeed).toLong()
        } else position
        val status = when (playback?.state) {
            PlaybackState.STATE_PLAYING -> "Playing"
            PlaybackState.STATE_PAUSED -> "Paused"
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> "Buffering"
            PlaybackState.STATE_ERROR -> "Playback error · Check the player"
            PlaybackState.STATE_STOPPED -> "Stopped"
            else -> "Playback status unavailable"
        }
        val favorites = playback?.customActions.orEmpty().filter {
            // Render only actions the provider explicitly labels as favorites.
            val label = it.name.toString()
            label.contains("favorite", ignoreCase = true) || label.contains("favourite", ignoreCase = true)
        }.map { it.action to it.name.toString() }
        return MediaState(
            metadata?.description?.title?.toString(),
            metadata?.description?.subtitle?.toString(),
            metadata?.description?.iconBitmap,
            status, playback?.actions ?: 0, playing, duration,
            if (duration > 0 && progress >= 0) progress.coerceIn(0, duration) else -1,
            favorites
        )
    }

    fun command(action: Long): Boolean {
        val session = controller ?: return false
        val state = session.playbackState ?: return false
        if (!hasAccess() || state.actions and action == 0L) return false
        return runCatching {
            val controls = session.transportControls
            when (action) {
                PlaybackState.ACTION_PLAY -> controls.play()
                PlaybackState.ACTION_PAUSE -> controls.pause()
                PlaybackState.ACTION_PLAY_PAUSE ->
                    if (state.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
                PlaybackState.ACTION_SKIP_TO_NEXT -> controls.skipToNext()
                PlaybackState.ACTION_SKIP_TO_PREVIOUS -> controls.skipToPrevious()
                else -> return false
            }
        }.isSuccess
    }

    fun favorite(action: String): Boolean {
        val session = controller ?: return false
        val item = session.playbackState?.customActions?.firstOrNull { it.action == action } ?: return false
        if (!hasAccess()) return false
        return runCatching { session.transportControls.sendCustomAction(item, null) }.isSuccess
    }
}
