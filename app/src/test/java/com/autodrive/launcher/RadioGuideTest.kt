package com.autodrive.launcher

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RadioGuideTest {
    private val now = Instant.parse("2026-11-09T13:30:00Z") // Monday 13:30 GMT in Morocco after the September reform.
    private fun guide(vararg programmes: RadioProgramme) = RadioGuide(RadioGuideSource.ASWAT, now, programmes.toList())
    private fun programme(start: Int, end: Int, day: DayOfWeek = DayOfWeek.MONDAY, name: String = "Émission de test") = RadioProgramme(day, start, end, name)

    @Test fun currentMoroccoHourDoesNotDependOnAnOldAndroidTimezoneDatabase() {
        assertEquals(java.time.ZoneOffset.UTC, MoroccoRadioTime.zoneAt(Instant.parse("2026-11-06T16:00:00Z")))
        assertEquals(Instant.parse("2026-11-06T16:00:00Z"), MoroccoRadioTime.atLocal(LocalDate.of(2026, 11, 6), 16 * 60))
        assertEquals(java.time.ZoneOffset.UTC, MoroccoRadioTime.zoneAt(Instant.parse("2026-09-20T01:00:00Z")))
    }

    @Test fun parsesOnlyPublishedGridRowsAndDeduplicatesClones() {
        val html = """<div class="home-tabs-holder"><time>13:00 16:00</time></div><h2 id="grille">Grille</h2>
            <div class="home-tabs-holder"><ul class="tab-nav"><li><a href="#mon">الإثنين</a></li></ul>
            <div id="mon"><div class="home-grille-widget">
              <div class="outer"><time>13:00 <span>14:00</span></time><h2><a>Test &amp; infos</a></h2></div>
              <div class="outer"><time>13:00 <span>14:00</span></time><h2><a>Test &amp; infos</a></h2></div>
              <div class="outer"><time>23:00 <span>02:00</span></time><h2><a>Nuit de test</a></h2></div>
              <div class="outer"><time>24:30 <span>25:00</span></time><h2><a>Invalide</a></h2></div>
              <div class="outer"><time>00:00 <span>00:00</span></time><h2><a>Ambigu</a></h2></div>
              <div class="outer"><time>16:00</time><h2><a>Fin absente</a></h2></div>
            </div></div></div>"""
        val rows = RadioProgrammeParser.aswat(html)
        assertEquals(2, rows.size)
        assertEquals(programme(780, 840, name = "Test & infos"), rows.first())
        assertEquals(120, rows.last().endMinute)
        assertTrue(RadioProgrammeParser.aswat("<h1>Horaires non publiés</h1><time>13:00 14:00</time>").isEmpty())
    }
    @Test fun handlesWeekdayLabelsWithoutInventingUnknownDays() {
        assertEquals(DayOfWeek.MONDAY, RadioProgrammeParser.day("الإثنين"))
        assertEquals(DayOfWeek.WEDNESDAY, RadioProgrammeParser.day("الأربعاء"))
        assertEquals(DayOfWeek.SUNDAY, RadioProgrammeParser.day("الأحد"))
        assertEquals(DayOfWeek.FRIDAY, RadioProgrammeParser.day("Vendredi"))
        assertNull(RadioProgrammeParser.day("Prochainement"))
        assertEquals(1440, RadioProgrammeParser.minute("24:00"))
        assertNull(RadioProgrammeParser.minute("24:01")); assertNull(RadioProgrammeParser.minute("25:00"))
    }
    @Test fun midnightAndWeekRolloverUseMoroccoTime() {
        val instant = Instant.parse("2026-11-09T00:30:00Z")
        val data = RadioGuide(RadioGuideSource.ASWAT, instant, listOf(
            programme(1380, 120, DayOfWeek.SUNDAY), programme(180, 240)))
        assertEquals(Instant.parse("2026-11-08T23:00:00Z"), data.current(instant)?.start)
        assertEquals(Instant.parse("2026-11-09T02:00:00Z"), data.current(instant)?.end)
        assertEquals(Instant.parse("2026-11-09T03:00:00Z"), data.next(instant)?.start)
        assertNull(data.current(Instant.parse("2026-11-09T02:00:00Z"))) // End is exclusive; don't fill a gap.
    }
    @Test fun acceptsPublished24HourNotationWithoutAddingAnExtraDay() {
        val data = guide(programme(1380, 1440))
        val row = data.occurrences(LocalDate.of(2026, 11, 9)).single()
        assertEquals(3600, java.time.Duration.between(row.start, row.end).seconds)
        assertEquals(Instant.parse("2026-11-10T00:00:00Z"), row.end)
    }
    @Test fun gapsAndOverlapsDoNotFabricateACurrentProgramme() {
        assertNull(guide(programme(600, 720), programme(900, 1000)).current(now))
        assertNull(guide(programme(720, 840), programme(780, 900)).current(now))
        assertEquals(Instant.parse("2026-11-09T15:00:00Z"), guide(programme(900, 1000)).next(now)?.start)
    }
    @Test fun staleOrFutureCachesCannotClaimCurrentOrNext() {
        val fresh = guide(programme(780, 840))
        assertNotNull(fresh.current(now))
        assertNull(fresh.copy(fetchedAt = now.minusSeconds(24 * 3600 + 1)).current(now))
        assertNull(fresh.copy(fetchedAt = now.minusSeconds(24 * 3600 + 1)).next(now))
        assertNull(fresh.copy(fetchedAt = now.plusSeconds(301)).current(now))
    }
    @Test fun nextCanBeNextWeekAndProgressDescribesThePublishedTimeSlot() {
        val data = guide(programme(480, 540))
        assertEquals(Instant.parse("2026-11-16T08:00:00Z"), data.next(now)?.start)
        val row = guide(programme(780, 840)).current(now)!!
        assertEquals(50, row.progress(now)); assertEquals(0, row.progress(row.start)); assertEquals(100, row.progress(row.end))
    }
    @Test fun sourceMatchingDoesNotAttachMainStationGuideToGenreStreamsOrSimilarNames() {
        assertEquals(RadioGuideSource.ASWAT, RadioGuideSource.forStation(RadioStation("Libre", "https://aswat.ice.infomaniak.ch/aswat-high.mp3")))
        assertNull(RadioGuideSource.forStation(RadioStation("Radio Aswat", "https://example.org/aswat.mp3")))
        assertNull(RadioGuideSource.forStation(RadioStation("HIT", "https://hitradio-maroc.ice.infomaniak.ch/genre-rnb.mp3")))
        assertNull(RadioGuideSource.forStation(RadioStation("Aswat", "https://aswat.ice.infomaniak.ch:8000/aswat-high.mp3")))
    }
}
