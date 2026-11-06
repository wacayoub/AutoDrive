package com.autodrive.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RadioAuditTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val hitUrl = "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3"
    private val marsUrl = "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3"

    @Test fun actualButtonsUseWholeCatalogueAndWrapAfterRapidChanges() = fixture {
        withActivity { activity ->
            fun views() = descendants(activity.window.decorView)
            fun click(description: String) = views().single { it.isShown && it.contentDescription == description }.performClick()
            fun title(name: String) = views().any { it.contentDescription == "Station choisie : $name" }
            waitFor { views().any { it.contentDescription == "Écouter Mars test" } && views().any { it.contentDescription == "Station suivante" && it.isEnabled } }
            instrumentation.runOnMainSync {
                assertEquals(1, RadioAndPhoneStore(activity).stations.size)
                assertTrue(views().any { it is RadioLogoView })
                click("Station suivante")
                assertTrue(title("Mars test"))
            }
            waitFor(25_000) { views().filterIsInstance<TextView>().any { it.text.toString().startsWith("En lecture ·") } }
            instrumentation.runOnMainSync {
                click("Station précédente"); assertTrue(title("HIT test"))
                click("Station précédente"); assertTrue(title("Erreur test"))
                click("Station suivante"); assertTrue(title("HIT test"))
                repeat(3) { click("Station suivante") }
                assertTrue(title("HIT test"))
            }
            waitFor(25_000) { views().filterIsInstance<TextView>().any { it.text.toString().startsWith("En lecture ·") } }
            instrumentation.runOnMainSync {
                click("Mettre la radio en pause")
                click("Afficher les radios favorites")
                assertFalse(views().single { it.contentDescription == "Station suivante" }.isEnabled)
                assertTrue(views().filterIsInstance<TextView>().any { it.text.toString().contains("Ajoute des favoris") })
                click("Afficher les radios du Maroc")
            }
            waitFor { views().any { it.isShown && it.contentDescription == "Ajouter Mars test aux favoris" } }
            instrumentation.runOnMainSync {
                click("Ajouter HIT test aux favoris")
                click("Ajouter Mars test aux favoris")
                click("Afficher les radios favorites")
                assertTrue(views().single { it.contentDescription == "Station suivante" }.isEnabled)
                click("Station suivante"); assertTrue(title("Mars test"))
                click("Station suivante"); assertTrue(title("HIT test"))
                click("Mettre la radio en pause")
                click("Ouvrir la liste complète des radios")
                assertTrue(views().any { it.contentDescription == "Rechercher une radio" })
                (views().single { it.contentDescription == "Rechercher une radio" } as android.widget.EditText).setText("hit")
            }
            waitFor {
                val list = views().filterIsInstance<android.widget.AbsListView>().single()
                list.count == 1 && list.firstVisiblePosition == 0
            }
        }
    }

    @Test fun pauseDuringConnectionStopsTheRequestInsteadOfRestartingIt() = fixture {
        withActivity { activity ->
            lateinit var player: InternetRadio
            instrumentation.runOnMainSync {
                player = InternetRadio(activity) {}
                player.play(RadioStation("Connexion de test", "https://example.invalid/loading.mp3"))
                assertTrue(player.requestedPlayback)
                player.toggle()
                assertFalse(player.requestedPlayback)
                assertEquals("En pause", player.status)
            }
            SystemClock.sleep(300)
            instrumentation.runOnMainSync {
                assertFalse(player.requestedPlayback)
                player.close()
            }
        }
    }

    @Test fun realLogoLoadsBoundedAndMissingLogoUsesFallback() {
        lateinit var artwork: RadioArtwork
        var bitmap: Bitmap? = null
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            artwork = RadioArtwork(context)
            artwork.load("https://hitradio.ma/small-logo-hitradio.png") { bitmap = it; loaded.countDown() }
        }
        try {
            assertTrue(loaded.await(12, TimeUnit.SECONDS)); assertNotNull(bitmap)
            assertTrue(maxOf(bitmap!!.width, bitmap!!.height) <= 128)
            val missing = CountDownLatch(1)
            instrumentation.runOnMainSync { artwork.load("https://example.invalid/no-logo.png") { assertNull(it); missing.countDown() } }
            assertTrue(missing.await(12, TimeUnit.SECONDS))
        } finally { instrumentation.runOnMainSync { artwork.close() } }
    }

    @Test fun oldCacheNeedsUpgradeAndDuplicateStreamKeepsAvailableLogo() = fixture {
        val cache = File(context.filesDir, "morocco-radio-directory.json")
        cache.writeText("""[{"name":"Ancienne radio","url":"https://radio.example/live"}]""")
        val directory = MoroccoRadioDirectory(context)
        assertEquals(1, directory.cached().size)
        assertTrue(directory.needsRefresh())
        val rows = MoroccoRadioDirectory.parse("""[
            {"name":"Radio","countrycode":"MA","lastcheckok":1,"url_resolved":"https://radio.example/live","favicon":""},
            {"name":"Doublon","countrycode":"MA","lastcheckok":1,"url_resolved":"https://radio.example/live","favicon":"https://radio.example/logo.png"}
        ]""")
        assertEquals(1, rows.size)
        assertEquals("https://radio.example/logo.png", rows.single().logoUrl)
    }

    private fun fixture(block: () -> Unit) {
        val cache = File(context.filesDir, "morocco-radio-directory.json")
        val bytes = cache.takeIf { it.exists() }?.readBytes()
        val prefs = context.getSharedPreferences("radio_phone", 0)
        val old = prefs.all.toMap()
        val launcher = context.getSharedPreferences("launcher", 0)
        val oldTab = launcher.getString("tab", null)
        cache.writeText("""[
          {"name":"HIT test","url":"$hitUrl","logo":"https://hitradio.ma/small-logo-hitradio.png"},
          {"name":"Mars test","url":"$marsUrl","logo":""},
          {"name":"Erreur test","url":"https://example.invalid/live.mp3","logo":""}
        ]""")
        prefs.edit().clear().putString("stations", """[{"name":"HIT test","url":"$hitUrl"}]""")
            .putString("selected", hitUrl).commit()
        launcher.edit().putString("tab", "RADIO").commit()
        try { block() } finally {
            if (bytes == null) cache.delete() else cache.writeBytes(bytes)
            prefs.edit().clear().apply { old.forEach { (k, v) -> if (v is Boolean) putBoolean(k, v) else putString(k, v as? String) } }.commit()
            launcher.edit().putString("tab", oldTab).commit()
        }
    }
    private fun withActivity(block: (WebDashboardActivity) -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        try { block(activity) } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    private fun waitFor(timeout: Long = 5000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        var ready = false
        while (!ready && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { ready = condition() }
            if (!ready) SystemClock.sleep(100)
        }
        assertTrue("Condition was not reached within $timeout ms", ready)
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
