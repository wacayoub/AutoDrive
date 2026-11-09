package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BroadcastProgrammesTest {
    @Test fun usesOnlyTheExplicitCurrentProgrammeFromSnrt() {
        val current = """
            <div><h2 class="program-title-sm">Libre antenne</h2>
            <div class="genre-first"><span class="badge badge-danger">En direct</span></div>
            <p class="mt-3">20H15 - 22H00</p></div>
        """.trimIndent()
        val next = """
            <div><h2 class="program-title-sm">Journal</h2>
            <div class="genre-first"><span class="badge badge-light">Suivant</span></div></div>
        """.trimIndent()
        assertEquals(BroadcastProgramme("Libre antenne", "Programme SNRT Live", "20H15 - 22H00"),
            BroadcastProgrammes.snrt(next + current))
        assertNull(BroadcastProgrammes.snrt(next))
        assertNull(BroadcastProgrammes.snrt(current + current))
    }

    @Test fun usesTheRadio2mTitleOnlyWhenPublishedAndKeepsStreamAttribution() {
        assertEquals(BroadcastProgramme("Kif Elhal", "Programme Radio 2M"),
            BroadcastProgrammes.radio2m("""{"success":true,"data":{"data":{"emmision":{"name":"Kif Elhal"}}}}"""))
        assertNull(BroadcastProgrammes.radio2m("""{"success":true,"data":{"data":{"emmision":null}}}"""))
        val station = RadioStation("Radio Exemple", "https://example.org/stream")
        assertNull(BroadcastProgrammes.fromStream(station, station.name))
        assertEquals(BroadcastProgramme("Titre du flux", "Info du flux"),
            BroadcastProgrammes.fromStream(station, "  Titre du flux  "))
    }
}
