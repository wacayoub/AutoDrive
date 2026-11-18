package com.incardrive.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent

/**
 * Starts the last app that played, without a tap on Lecture: each time the car starts, and from the
 * media card's button. Documented ways only, best first:
 * 1. the app's session if it is still there: Play;
 * 2. its MediaBrowserService (Radio Maroc & Coran accepts Incar Drive, signed with the same key): connect, then Play,
 *    which makes the radio resume its last station;
 * 3. a « play » media button sent to the app's media button receiver, which apps such as Spotify answer by resuming.
 */
internal class MediaResume private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences(NavBubble.PREFS, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var browser: MediaBrowser? = null

    var enabled: Boolean
        get() = prefs.getBoolean(ENABLED, true)
        set(value) = prefs.edit().putBoolean(ENABLED, value).apply()

    val last: String? get() = prefs.getString(LAST, null)

    /** Called while something plays: that app becomes the one to resume. */
    fun remember(packageName: String) {
        if (packageName != last) prefs.edit().putString(LAST, packageName).apply()
    }

    /**
     * The car started: the head unit booted, or woke after its screen was off a while (contact off, the Incarlink goes
     * to sleep instead of shutting down). Called by the home screen and by the notification listener, which Android
     * starts at boot even when another app is in front; each start ([key]) is handled once. A few seconds later the
     * last app plays, unless something already plays; if it does not start (network or the app not ready yet), it
     * is asked again, three times at most.
     */
    fun onDriveStart(key: String = bootKey()) {
        if (!enabled || last == null) return
        synchronized(MediaResume) {
            if (prefs.getString(TRIGGER, null) == key) return
            prefs.edit().putString(TRIGGER, key).apply()
        }
        handler.removeCallbacks(attempt)
        tries = 0
        handler.postDelayed(attempt, START_DELAY_MS)
    }

    /** This boot of the head unit: Android's boot count, or the minute it booted when the box does not count. */
    fun bootKey(): String {
        val count = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
        if (count >= 0) return "boot:$count"
        return "booted:" + (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000
    }

    private var tries = 0
    private val attempt = object : Runnable {
        override fun run() {
            val app = last ?: return
            val live = sessions()
            // Playing, or on its way (the radio connecting to its stream): nothing more to do.
            if (live.any { it.playbackState?.state in STARTED }) return
            tries++
            play(app, live)
            if (tries < MAX_TRIES) handler.postDelayed(this, RETRY_MS)
        }
    }

    private fun sessions(): List<MediaController> = runCatching {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaAccessService::class.java))
    }.getOrDefault(emptyList())

    fun play(packageName: String, live: List<MediaController>): Boolean {
        live.firstOrNull { it.packageName == packageName }?.let { controller ->
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) return true
            return runCatching { controller.transportControls.play() }.isSuccess
        }
        browserService(packageName)?.let { service -> return connectAndPlay(service) }
        return mediaButton(packageName)
    }

    private fun browserService(packageName: String): ComponentName? = runCatching {
        context.packageManager.queryIntentServices(Intent("android.media.browse.MediaBrowserService").setPackage(packageName), 0)
            .firstOrNull()?.serviceInfo?.let { ComponentName(it.packageName, it.name) }
    }.getOrNull()

    private fun connectAndPlay(service: ComponentName): Boolean {
        browser?.disconnect()
        lateinit var created: MediaBrowser
        created = MediaBrowser(context, service, object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                runCatching { MediaController(context, created.sessionToken).transportControls.play() }
                // Keep the link a little while the app starts playing, then let go.
                handler.postDelayed({ if (browser === created) { created.disconnect(); browser = null } }, 15_000)
            }

            override fun onConnectionFailed() {
                if (browser === created) browser = null
                mediaButton(service.packageName)
            }
        }, null)
        browser = created
        return runCatching { created.connect() }.isSuccess
    }

    private fun mediaButton(packageName: String): Boolean {
        val receiver = runCatching {
            context.packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName), 0)
                .firstOrNull()?.activityInfo
        }.getOrNull() ?: return false
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(ComponentName(receiver.packageName, receiver.name))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)))
        }
        return true
    }


    companion object {
        const val ENABLED = "media_resume"
        const val LAST = "media_last_app"
        const val TRIGGER = "media_resume_start"
        const val START_DELAY_MS = 4_000L
        const val RETRY_MS = 9_000L
        const val MAX_TRIES = 3
        private val STARTED = setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING)
        /** Off at least this long, the screen coming back on is a new drive. */
        const val SLEEP_MS = 90_000L

        @Volatile private var shared: MediaResume? = null

        /** One for the whole app, so that the home screen and the listener share the pending start. */
        fun get(context: Context): MediaResume = shared ?: synchronized(this) {
            shared ?: MediaResume(context.applicationContext).also { shared = it }
        }
    }
}
