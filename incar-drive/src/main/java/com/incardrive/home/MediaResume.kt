package com.incardrive.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent

/**
 * Starts the last app that played, without a tap on Lecture: when the head unit starts with the car, and from the
 * media card's button. Documented ways only, best first:
 * 1. the app's session if it is still there: Play;
 * 2. its MediaBrowserService (Radio Maroc & Coran accepts Incar Drive, signed with the same key): connect, then Play,
 *    which makes the radio resume its last station;
 * 3. a « play » media button sent to the app's media button receiver, which apps such as Spotify answer by resuming.
 */
internal class MediaResume(private val context: Context) {
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
     * Once per start of the head unit (Android's boot count), a few seconds after the home screen appears, plays the
     * last app unless something already plays.
     */
    fun onHomeShown(alreadyPlaying: () -> Boolean, live: () -> List<MediaController>) {
        if (!enabled) return
        val boot = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
        if (boot < 0 || boot == prefs.getInt(BOOT, -1)) return
        prefs.edit().putInt(BOOT, boot).apply()
        handler.postDelayed({ if (!alreadyPlaying()) last?.let { play(it, live()) } }, START_DELAY_MS)
    }

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

    fun release() {
        browser?.disconnect()
        browser = null
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        const val ENABLED = "media_resume"
        const val LAST = "media_last_app"
        const val BOOT = "media_resume_boot"
        const val START_DELAY_MS = 4_000L
    }
}
