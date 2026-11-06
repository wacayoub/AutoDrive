package com.autodrive.launcher

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioCatalogueAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun twelvePublishedQuranStreamsActuallyDecodeAndSnrtPagesAreNotFedToExoplayer() {
        lateinit var radio: InternetRadio
        instrumentation.runOnMainSync { radio = InternetRadio(context) {} }
        try {
            val stations = RadioCatalogue.featured.filter { RadioCatalogue.officialPlayer(it) == null }
            assertEquals(12, stations.size)
            for (station in stations) {
                instrumentation.runOnMainSync { radio.play(station) }
                await(25_000) { radio.playing && radio.station?.url == station.url }
            }
            instrumentation.runOnMainSync {
                radio.play(RadioCatalogue.featured.first())
                assertFalse(radio.playing)
                assertFalse(radio.requestedPlayback)
                assertTrue(radio.status.contains("site officiel SNRT"))
            }
        } finally { instrumentation.runOnMainSync { radio.close() } }
    }

    @Test fun nativeCategoryFiltersArabicNamesSearchAndTransportStayConsistent() {
        val prefs = context.getSharedPreferences("radio_phone", 0)
        val before = prefs.all
        val launcher = context.getSharedPreferences("launcher", 0)
        val tab = launcher.getString("tab", null)
        val cache = File(context.filesDir, "morocco-radio-directory.json")
        val oldCache = cache.takeIf { it.exists() }?.readBytes()
        cache.writeText("""[{"name":"HIT RADIO","url":"${MoroccoRadioDirectory.starter.first().url}","logo":"","tags":"music,pop"},{"name":"Radio Mars","url":"https://radiomars.ice.infomaniak.ch/radiomars-128.mp3","logo":"","tags":"sports"},{"name":"Débats test","url":"https://example.invalid/talk.mp3","logo":"","tags":"news,talk"}]""")
        prefs.edit().clear().commit(); launcher.edit().putString("tab", "RADIO").commit()
        val activity = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        fun views() = descendants(activity.window.decorView)
        fun click(label: String) = views().single { it.isShown && it.contentDescription == label }.performClick()
        fun grid() = views().filterIsInstance<GridView>().single()
        try {
            instrumentation.runOnMainSync { click("Media"); click("Catalogue des radios"); click("Afficher les radios de musique") }
            await { grid().count == 1 }
            instrumentation.runOnMainSync {
                assertEquals("HIT RADIO", (grid().adapter.getItem(0) as RadioStation).name)
                click("Afficher les radios de sport")
                assertEquals("Radio Mars", (grid().adapter.getItem(0) as RadioStation).name)
                click("Afficher les radios d’information")
                assertEquals("Débats test", (grid().adapter.getItem(0) as RadioStation).name)
                click("Afficher les radios SNRT")
            }
            await { grid().count == 4 }
            instrumentation.runOnMainSync {
                assertEquals(RadioCategory.SNRT, RadioAndPhoneStore(context).radioCategory)
                assertTrue(views().filterIsInstance<TextView>().any { it.text == "SNRT · lecteur officiel" })
                click("Afficher les radios du Coran")
                assertEquals(13, grid().count)
            }
            await { views().any { it.isShown && it.contentDescription == "Écouter مشاري راشد العفاسي" } }
            instrumentation.runOnMainSync { click("Écouter مشاري راشد العفاسي") }
            await(25_000) { views().filterIsInstance<TextView>().any { it.text.toString().startsWith("En lecture ·") } }
            instrumentation.runOnMainSync {
                click("Station suivante")
                assertTrue(views().any { it.contentDescription == "Station choisie : ماهر المعيقلي" })
                click("Station précédente")
                assertTrue(views().any { it.contentDescription == "Station choisie : مشاري راشد العفاسي" })
                val search = views().single { it.contentDescription == "Rechercher une radio" } as EditText
                search.setText("Sudais")
                assertEquals(1, grid().count)
                assertEquals("عبد الرحمن السديس", (grid().adapter.getItem(0) as RadioStation).name)
                search.setText("السديس")
                assertEquals(1, grid().count)
                search.setText("")
                click("Afficher les radios favorites")
                assertEquals(0, grid().count)
                assertFalse(views().single { it.contentDescription == "Station suivante" }.isEnabled)
                click("Afficher les radios du Coran")
                assertEquals(13, grid().count)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            prefs.edit().clear().apply { before.forEach { (k,v) -> if (v is Boolean) putBoolean(k,v) else putString(k,v as? String) } }.commit()
            launcher.edit().putString("tab",tab).commit()
            if (oldCache == null) cache.delete() else cache.writeBytes(oldCache)
        }
    }
    private fun await(timeout: Long = 5_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        do {
            var ready = false; instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Catalogue or playback condition not reached")
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
