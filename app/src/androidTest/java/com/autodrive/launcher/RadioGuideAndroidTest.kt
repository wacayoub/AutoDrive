package com.autodrive.launcher

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioGuideAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun actualOfficialWeeklyGuideCachesAndDrivesProgrammeUi() = fixture {
        val repository = RadioGuideRepository(context)
        val fetched = repository.fetch(RadioGuideSource.ASWAT)
        assertEquals(7, fetched.programmes.map { it.day }.distinct().size)
        assertTrue(fetched.programmes.all { it.title.isNotBlank() })
        assertEquals(fetched, repository.cached(RadioGuideSource.ASWAT))
        val activity = start()
        fun views() = descendants(activity.window.decorView)
        try {
            val model = ViewModelProvider(activity)[RadioGuideViewModel::class.java]
            await { model.state.value.phase == RadioGuidePhase.READY }
            tap(activity.window.decorView, "Media") // The selected radio opens its full player, rather than switching to YouTube.
            await { views().any { it.contentDescription == "Programme" } }
            tap(activity.window.decorView, "Programme")
            await { views().filterIsInstance<ListView>().any { it.contentDescription == "Programme des émissions radio" && it.count > 0 } }
            instrumentation.runOnMainSync {
                assertTrue(views().any { it.contentDescription == "Voir la grille officielle" && it.isEnabled })
                assertTrue(views().filterIsInstance<TextView>().any { it.text.contains("Grille publiée") })
                assertTrue(views().any { it.contentDescription == "Écouter la radio" }) // Merely opening the guide doesn't autoplay.
            }
            var dayBefore = ""
            instrumentation.runOnMainSync { dayBefore = (views().single { it.contentDescription == "Revenir au programme d’aujourd’hui" } as TextView).text.toString() }
            tap(activity.window.decorView, "Programme du jour suivant")
            instrumentation.runOnMainSync { assertNotEquals(dayBefore, (views().single { it.contentDescription == "Revenir au programme d’aujourd’hui" } as TextView).text.toString()) }
            tap(activity.window.decorView, "Revenir au programme d’aujourd’hui")
            instrumentation.waitForIdleSync()
            SystemClock.sleep(250)
            capture("radio-programme-1.9.0.png")
            tap(activity.window.decorView, "Stations")
            tap(activity.window.decorView, "Rechercher une radio")
            await { activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
            instrumentation.runOnMainSync { (views().single { it.contentDescription == "Rechercher une radio" } as EditText).setText("aswat") }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER)
            await { activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false }
            tap(activity.window.decorView, "Rechercher une radio")
            await { activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
            tap(activity.window.decorView, "Programme")
            await { activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false }
            instrumentation.runOnMainSync {
                assertTrue(views().single { it.contentDescription == "Station suivante" }.isShown)
                assertTrue(views().single { it.contentDescription == "Écouter la radio" }.isShown)
            }
            tap(activity.window.decorView, "Station suivante") // Fixture queue: Aswat -> HIT, whose structured guide is unavailable.
            await { model.state.value.source == RadioGuideSource.HIT }
            instrumentation.runOnMainSync {
                assertEquals(0, views().filterIsInstance<ListView>().single { it.contentDescription == "Programme des émissions radio" }.count)
                assertTrue(views().filterIsInstance<TextView>().any { it.text.contains("Aucune grille horaire compatible") })
                assertFalse(views().single { it.contentDescription == "Actualiser le programme radio" }.isEnabled)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun staleProgrammeViewCannotHighlightAnInventedCurrentProgrammeAndBadCacheIsIgnored() = fixture {
        val now = Instant.now()
        val day = now.atZone(MoroccoRadioTime.zoneAt(now)).dayOfWeek
        val stale = RadioGuide(RadioGuideSource.ASWAT, now.minusSeconds(3 * 24 * 3600),
            listOf(RadioProgramme(day, 0, 1440, "Programme de test enregistré")))
        context.getSharedPreferences("launcher", 0).edit().putString("tab", "YOUTUBE").commit()
        val activity = start()
        try {
            instrumentation.runOnMainSync {
                val panel = RadioProgrammeView(activity, {}, {})
                activity.setContentView(android.widget.FrameLayout(activity).apply { addView(panel) })
                panel.render(RadioGuideState(RadioGuideSource.ASWAT, RadioGuidePhase.ERROR, stale, true, "Actualisation impossible"), now)
                assertTrue(descendants(panel).filterIsInstance<TextView>().any { it.text.contains("ancienne") })
                assertFalse(descendants(panel).filterIsInstance<TextView>().any { it.text == "Prévu en ce moment" })
            }
            File(context.filesDir, "radio-guides/ASWAT.json").apply { parentFile?.mkdirs(); writeText("{broken") }
            assertNull(RadioGuideRepository(context).cached(RadioGuideSource.ASWAT))
            // API 33 may have a pre-reform timezone database; the application must still show legal Moroccan GMT.
            val fixture = Instant.parse("2026-11-06T16:00:00Z")
            assertEquals(ZoneOffset.UTC, MoroccoRadioTime.zoneAt(fixture))
            assertEquals(fixture, MoroccoRadioTime.atLocal(LocalDate.of(2026, 11, 6), 16 * 60))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun fixture(block: () -> Unit) {
        val prefs = context.getSharedPreferences("radio_phone", 0); val old = prefs.all.toMap()
        val launcher = context.getSharedPreferences("launcher", 0); val oldTab = launcher.getString("tab", null)
        val cache = File(context.filesDir, "morocco-radio-directory.json"); val oldCache = cache.takeIf { it.exists() }?.readBytes()
        val guide = File(context.filesDir, "radio-guides/ASWAT.json"); val oldGuide = guide.takeIf { it.exists() }?.readBytes()
        prefs.edit().clear().commit()
        val aswat = MoroccoRadioDirectory.starter.single { RadioGuideSource.forStation(it) == RadioGuideSource.ASWAT }
        RadioAndPhoneStore(context).apply { lastStation = aswat; selectedUrl = aswat.url }
        launcher.edit().putString("tab", "RADIO").commit()
        cache.writeText("""[
          {"name":"Radio Aswat","url":"${aswat.url}","logo":""},
          {"name":"HIT RADIO","url":"https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3","logo":""}
        ]""")
        try { block() } finally {
            if (oldCache == null) cache.delete() else cache.writeBytes(oldCache)
            if (oldGuide == null) guide.delete() else guide.writeBytes(oldGuide)
            prefs.edit().clear().apply { old.forEach { (k, v) -> if (v is Boolean) putBoolean(k, v) else putString(k, v as? String) } }.commit()
            launcher.edit().putString("tab", oldTab).commit()
        }
    }
    private fun start() = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { context.openFileOutput(name, 0).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    private fun tap(root: View, description: String) {
        instrumentation.waitForIdleSync()
        var x = 0f; var y = 0f
        instrumentation.runOnMainSync {
            val view = descendants(root).single { it.isShown && it.contentDescription == description }
            val location = IntArray(2); view.getLocationOnScreen(location)
            x = location[0] + view.width / 2f; y = location[1] + view.height / 2f
        }
        val time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(time, time + 60, MotionEvent.ACTION_UP, x, y, 0)
        try { instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up) } finally { down.recycle(); up.recycle() }
        instrumentation.waitForIdleSync()
    }
    private fun await(condition: () -> Boolean) {
        repeat(80) { var done = false; instrumentation.runOnMainSync { done = condition() }; if (done) return; SystemClock.sleep(100) }
        fail("Programme UI did not become ready")
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
