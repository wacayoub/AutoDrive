package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioStation
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationWebsitesTest {
    @Test fun readsOnlyThePublishersScheduleForTheRequestedDay() {
        val html = """
            <div class="home-tabs-holder"><ul class="tab-nav">
              <li><a href="#tabs-37">الإثنين</a></li>
              <li><a href="#tabs-38">الثلاثاء</a></li>
            </ul></div>
            <div id="tabs-37"><div class="outer">
              <time>07:00 <span>10:00</span></time><h2>مالين الصباح</h2>
            </div><div class="outer"><time>99:00 10:00</time><h2>Invalid</h2></div></div>
            <div id="tabs-38"><div class="outer">
              <time>20:00 <span>21:00</span></time><h2>أهل الفن</h2>
            </div></div>
        """.trimIndent()
        assertEquals("Grille du jour · الإثنين\n\n\u206607:00 – 10:00\u2069  ·  مالين الصباح",
            StationWebsites.aswatSchedule(html, DayOfWeek.MONDAY))
        assertNull(StationWebsites.aswatSchedule(html, DayOfWeek.SUNDAY))
    }

    @Test fun directoryEntriesCannotSelectAnOfficialWebsite() {
        val aswat = Stations.visible(StationTab.MOROCCO, emptySet(), "Aswat").single()
        assertEquals("https://radioaswat.ma/", StationWebsites.forStation(aswat))
        assertNull(StationWebsites.forStation(RadioStation(aswat.name, "https://audio.example/stream", countryCode = "MA")))
    }
}
