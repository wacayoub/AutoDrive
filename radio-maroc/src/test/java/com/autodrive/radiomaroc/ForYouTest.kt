package com.autodrive.radiomaroc

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.chrono.HijrahDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForYouTest {
    private fun at(date: LocalDate, time: String) = LocalDateTime.of(date, LocalTime.parse(time))
    private val tuesday = LocalDate.of(2026, 11, 17)

    private fun suggest(now: LocalDateTime, prayers: PrayerStatus? = null, herraz: Int? = null) = ForYou.suggestions(
        now, prayers, emptyList(), RadioCity.CASABLANCA, emptyList(), emptyList(), herraz,
    )

    @Test fun quranRadioLeadsJustBeforePrayer() {
        val now = at(tuesday, "12:05")
        val dhuhr = PrayerMoment(Prayer.DHUHR, at(tuesday, "12:20"))
        val first = suggest(now, PrayerStatus(tuesday, null, dhuhr)).first()
        assertEquals(Stations.quranRadio, first.station)
        assertEquals("Avant Dhuhr · 12:20", first.reason)
    }

    @Test fun fridayMorningOffersAlKahfFromHerraz() {
        val friday = LocalDate.of(2026, 11, 20)
        val kahf = suggest(at(friday, "09:00")).first { it.station == Stations.herrazStation }
        assertEquals(18, kahf.chapter)
    }

    @Test fun ramadanBringsTheIftarTime() {
        val ramadan = LocalDate.from(HijrahDate.of(1448, 9, 10))
        assertTrue(ForYou.isRamadan(at(ramadan, "12:00")))
        val maghrib = PrayerMoment(Prayer.MAGHRIB, at(ramadan, "18:40"))
        val list = ForYou.suggestions(at(ramadan, "15:00"), null, listOf(maghrib), RadioCity.RABAT, emptyList(), emptyList(), null)
        assertEquals("Ramadan · iftar à 18:40", list.first { it.station == Stations.quranRadio }.reason)
        assertFalse(ForYou.isRamadan(at(tuesday, "12:00")))
    }

    @Test fun nearbyResumeAndFillWithoutDuplicates() {
        val list = suggest(at(tuesday, "15:00"), herraz = 36)
        assertEquals("Reprendre · سورة يس", list.first { it.station == Stations.herrazStation }.reason)
        assertTrue(list.any { it.reason == "Près de vous · Casablanca" && it.station.name == "SNRT Radio Casablanca" })
        assertEquals(list.size, list.map { it.station.url }.distinct().size)
        assertTrue(list.size >= 8)
    }
}
