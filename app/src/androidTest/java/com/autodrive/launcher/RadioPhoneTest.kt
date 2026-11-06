package com.autodrive.launcher

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioPhoneTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun stationAndPhonePreferencesSurviveNewStoreAndRemoval() = withPreferences {
        val store = RadioAndPhoneStore(context)
        val station = RadioStation("Test utilisateur", "https://example.invalid/radio.mp3", true)
        assertTrue(store.saveStation(station))
        assertTrue(RadioAndPhoneStore(context).stations.contains(station))
        assertFalse(store.saveStation(station.copy(url = "http://example.invalid/live")))
        store.selectedUrl = station.url
        assertEquals(station.url, RadioAndPhoneStore(context).selectedUrl)
        store.removeStation(station.url)
        assertNull(store.selectedUrl)
        assertFalse(store.stations.contains(station))
        assertTrue(store.savePhoneFavorite(PhoneFavorite("Test", "+212 (6) 12345678")))
        assertEquals(PhoneFavorite("Test", "+212612345678"), RadioAndPhoneStore(context).phoneFavorites.single())
        assertTrue(store.savePhoneFavorite(PhoneFavorite("Renommé", "+212612345678")))
        assertEquals(1, store.phoneFavorites.size)
        for (i in 1..5) assertTrue(store.savePhoneFavorite(PhoneFavorite("Contact $i", "12345$i")))
        assertFalse(store.savePhoneFavorite(PhoneFavorite("Limite", "9999999")))
        store.removePhoneFavorite("+212612345678")
        assertEquals(5, RadioAndPhoneStore(context).phoneFavorites.size)
    }

    @Test fun moroccoDirectoryFiltersUnsafeEntriesAndCachesRealResults() {
        val sample = """[
            {"name":"Valide","countrycode":"MA","lastcheckok":1,"url_resolved":"https://radio.example:8102/live"},
            {"name":"Doublon","countrycode":"MA","lastcheckok":1,"url_resolved":"https://radio.example:8102/live"},
            {"name":"Autre pays","countrycode":"FR","lastcheckok":1,"url_resolved":"https://radio.example/fr"},
            {"name":"Indisponible","countrycode":"MA","lastcheckok":0,"url_resolved":"https://radio.example/off"},
            {"name":"Non sécurisé","countrycode":"MA","lastcheckok":1,"url_resolved":"http://radio.example/live"}
        ]"""
        assertEquals(listOf(RadioStation("Valide", "https://radio.example:8102/live")), MoroccoRadioDirectory.parse(sample))
        val directory = MoroccoRadioDirectory(context)
        val actual = directory.fetch()
        assertTrue("Actual Morocco directory must contain compatible entries", actual.isNotEmpty())
        assertEquals(actual, MoroccoRadioDirectory(context).cached())
        assertTrue(actual.all { RadioStation.validUrl(it.url) != null })
    }

    @Test fun phoneKeypadFavoritesAndMissingSelectedAppBehaveHonestly() = withPreferences {
        withActivity { activity ->
            instrumentation.runOnMainSync {
                val links = AppLinks(activity)
                assertNull(links.dialIntent("12345")) // No OEM BT Phone on this AOSP emulator; no automatic SIM fallback.
                val dialer = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:12345")).resolveActivity(activity.packageManager)!!
                val dialerApp = links.apps().first { it.component.packageName == dialer.packageName }
                links.saveAssociation(AppLinks.PHONE, dialerApp.component)
                assertEquals("+212612345678", links.dialIntent("+212 (6)12 34 56 78")?.data?.schemeSpecificPart)
                links.saveAssociation(AppLinks.PHONE, ComponentName("com.autodrive.missing", "com.autodrive.missing.Phone"))
                assertNull(links.dialIntent("123456"))
                assertFalse(links.openPhone())
                links.saveAssociation(AppLinks.PHONE, null)
                val store = RadioAndPhoneStore(activity)
                assertTrue(store.savePhoneFavorite(PhoneFavorite("Famille test", "+212612345678")))
                descendants(activity.window.decorView).single { it.contentDescription == "Phone" }.performClick()
                val panel = descendants(activity.window.decorView).filterIsInstance<PhonePanel>().single()
                assertEquals("", panel.number.text.toString())
                descendants(panel).single { it.contentDescription == "1" }.performClick()
                descendants(panel).single { it.contentDescription == "2" }.performClick()
                assertEquals("12", panel.number.text.toString())
                descendants(panel).single { it.contentDescription == "Effacer un chiffre" }.performClick()
                assertEquals("1", panel.number.text.toString())
                descendants(panel).single { it.contentDescription == "0" }.performLongClick()
                assertEquals("1+", panel.number.text.toString())
                descendants(panel).single { it.contentDescription == "Favori Famille test" }.performClick()
                assertEquals("+212612345678", panel.number.text.toString())
                assertEquals("+212612345678", RadioAndPhoneStore(activity).phoneDraft)
                descendants(activity.window.decorView).single { it.contentDescription == "Back" }.performClick()
                assertEquals(4, descendants(activity.window.decorView).count { it.contentDescription in listOf("Phone", "Media", "Apps", "Settings") })
            }
        }
    }

    @Test fun realHttpsAudioPlaysPausesAndReleasesThenShowsARealNetworkError() = withPreferences {
        withActivity { activity ->
            lateinit var radio: InternetRadio
            val playing = CountDownLatch(1)
            val store = RadioAndPhoneStore(context)
            val live = store.stations.single()
            instrumentation.runOnMainSync {
                radio = InternetRadio(activity) { if (radio.playing) playing.countDown() }
                radio.play(live)
            }
            try {
                assertTrue("Official HIT RADIO stream failed to reach real playback", playing.await(25, TimeUnit.SECONDS))
                instrumentation.runOnMainSync {
                    assertTrue(radio.playing)
                    assertEquals("En lecture · Internet", radio.status)
                    radio.toggle()
                }
                SystemClock.sleep(250)
                instrumentation.runOnMainSync {
                    assertFalse(radio.playing)
                    assertEquals("En pause", radio.status)
                    radio.close()
                    assertEquals("Prêt à écouter", radio.status)
                    radio.play(RadioStation("Erreur réseau de test", "https://example.invalid/no-radio.mp3"))
                }
                var failed = false
                repeat(80) {
                    if (!failed) {
                        SystemClock.sleep(250)
                        instrumentation.runOnMainSync { failed = radio.status.startsWith("Flux indisponible") }
                    }
                }
                assertTrue("DNS failure must surface as an actual error", failed)
            } finally { instrumentation.runOnMainSync { radio.close() } }
        }
    }

    private fun withPreferences(block: () -> Unit) {
        val prefs = context.getSharedPreferences("radio_phone", 0)
        val old = prefs.all.toMap()
        val launcher = context.getSharedPreferences("launcher", 0)
        val oldPhone = launcher.getString("app_PHONE", null)
        val oldTab = launcher.getString("tab", null)
        prefs.edit().clear().commit()
        launcher.edit().remove("app_PHONE").putString("tab", "RADIO").commit()
        try { block() } finally {
            prefs.edit().clear().apply { old.forEach { (k, v) -> if (v is Boolean) putBoolean(k, v) else putString(k, v as? String) } }.commit()
            launcher.edit().putString("app_PHONE", oldPhone).putString("tab", oldTab).commit()
        }
    }
    private fun withActivity(block: (WebDashboardActivity) -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        try { block(activity) } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
