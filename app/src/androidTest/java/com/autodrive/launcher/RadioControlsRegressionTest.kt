package com.autodrive.launcher

import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioControlsRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val hit = "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3"
    private val mars = "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3"

    @Test fun starterStationsDecodeIndependentlyOfDirectory() {
        val activity = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        lateinit var radio: InternetRadio
        instrumentation.runOnMainSync { radio = InternetRadio(activity) {} }
        try {
            for (station in MoroccoRadioDirectory.starter) {
                instrumentation.runOnMainSync { radio.play(station) }
                await(25_000) { radio.playing && radio.station?.url == station.url }
            }
        } finally { instrumentation.runOnMainSync { radio.close(); activity.finish() } }
    }

    @Test fun fullSavedLibraryDoesNotBlockTouchOrSystemMediaCommands() {
        val prefs = context.getSharedPreferences("radio_phone", 0)
        val old = prefs.all.toMap()
        val launcher = context.getSharedPreferences("launcher", 0)
        val oldTab = launcher.getString("tab", null)
        val cache = File(context.filesDir, "morocco-radio-directory.json")
        val oldCache = cache.takeIf { it.exists() }?.readBytes()
        val saved = JSONArray((1..199).map { JSONObject().put("name", "Station sauvegardée $it").put("url", "https://example.invalid/saved/$it.mp3") } +
            JSONObject().put("name", "HIT RADIO").put("url", hit))
        prefs.edit().clear().putString("stations", saved.toString()).putString("selected", hit).commit()
        launcher.edit().putString("tab", "RADIO").commit()
        cache.writeText(JSONArray(listOf(JSONObject().put("name", "HIT RADIO").put("url", hit).put("logo", "https://hitradio.ma/small-logo-hitradio.png"),
            JSONObject().put("name", "Radio Mars").put("url", mars).put("logo", ""))).toString())
        val activity = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        fun views() = descendants(activity.window.decorView)
        fun hasTitle(name: String) = views().any { it.contentDescription == "Station choisie : $name" }
        try {
            await { views().any { it.contentDescription == "Station suivante" && it.isEnabled } }
            tap(activity.window.decorView, "Station suivante")
            await { hasTitle("Radio Mars") }
            assertEquals(200, RadioAndPhoneStore(context).stations.size)
            assertEquals(mars, RadioAndPhoneStore(context).lastStation?.url)
            await(25_000) { views().filterIsInstance<TextView>().any { it.text.startsWith("En lecture ·") } }
            lateinit var controller: MediaController
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL)
            try {
                controller = context.getSystemService(MediaSessionManager::class.java).getActiveSessions(null).single { it.packageName == context.packageName }
            } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
            assertTrue(controller.playbackState!!.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
            assertEquals("Radio Mars", controller.metadata!!.getString(android.media.MediaMetadata.METADATA_KEY_TITLE))
            // Public Android media-key dispatch, distinct from calling our view listener.
            val audio = context.getSystemService(AudioManager::class.java)
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            await { hasTitle("HIT RADIO") }
            controller.transportControls.pause()
            await { controller.playbackState?.state == PlaybackState.STATE_PAUSED }
            controller.transportControls.play()
            await(25_000) { controller.playbackState?.state == PlaybackState.STATE_PLAYING }
            tap(activity.window.decorView, "Station suivante")
            await { hasTitle("Radio Mars") }
            tap(activity.window.decorView, "Mettre la radio en pause")
            await { controller.playbackState?.state == PlaybackState.STATE_PAUSED }
            tap(activity.window.decorView, "Ouvrir la liste complète des radios")
            await { views().filterIsInstance<GridView>().isNotEmpty() }
            instrumentation.runOnMainSync {
                val grid = views().filterIsInstance<GridView>().single()
                assertEquals(if (activity.resources.configuration.screenWidthDp >= 600) 2 else 1, grid.numColumns)
                assertEquals(201, grid.count)
                assertTrue(views().any { it is RadioLogoView })
            }
            tap(activity.window.decorView, "Ajouter HIT RADIO aux favoris")
            await { RadioAndPhoneStore(context).stations.any { it.url == hit && it.favorite } }
            tap(activity.window.decorView, "Afficher les radios favorites")
            await { views().filterIsInstance<GridView>().single().count == 1 }
            instrumentation.runOnMainSync { assertFalse(views().single { it.contentDescription == "Station suivante" }.isEnabled) }
            tap(activity.window.decorView, "Afficher les radios du Maroc")
            await { views().filterIsInstance<GridView>().single().count == 201 }
            tap(activity.window.decorView, "Écouter HIT RADIO")
            await { hasTitle("HIT RADIO") }
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            try { context.openFileOutput("radio-grid-1.8.0.png", 0).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
            assertEquals(200, RadioAndPhoneStore(context).stations.size)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            if (oldCache == null) cache.delete() else cache.writeBytes(oldCache)
            prefs.edit().clear().apply { old.forEach { (k, v) -> if (v is Boolean) putBoolean(k, v) else putString(k, v as? String) } }.commit()
            launcher.edit().putString("tab", oldTab).commit()
        }
    }
    private fun tap(root: View, description: String) {
        instrumentation.waitForIdleSync()
        var x = 0f; var y = 0f
        instrumentation.runOnMainSync {
            val view = descendants(root).single { it.isShown && it.contentDescription == description }
            val loc = IntArray(2); view.getLocationOnScreen(loc)
            x = loc[0] + view.width / 2f; y = loc[1] + view.height / 2f
        }
        val time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0)
        val up = MotionEvent.obtain(time,time+60,MotionEvent.ACTION_UP,x,y,0)
        try { instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up) } finally { down.recycle(); up.recycle() }
        instrumentation.waitForIdleSync()
    }
    private fun await(timeout: Long = 5_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        do {
            var ready = false; instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Radio condition not reached")
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
