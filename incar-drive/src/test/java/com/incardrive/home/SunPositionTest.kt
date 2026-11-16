package com.incardrive.home

import com.autodrive.radiomaroc.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.Month

class SunPositionTest {
    // Casablanca.
    private fun sun(utc: String) = SunPosition.at(Instant.parse(utc), 33.57, -7.59)

    @Test
    fun noonInSummerIsHighInTheSouth() {
        val noon = sun("2026-06-21T12:35:00Z")
        assertEquals(79.9, noon.elevation, 1.0)
        assertEquals(180.0, noon.azimuth, 15.0)
    }

    @Test
    fun morningIsEastAndEveningWest() {
        val morning = sun("2026-11-16T08:30:00Z")
        val evening = sun("2026-11-16T16:30:00Z")
        assertTrue(morning.morning && morning.azimuth in 100.0..140.0)
        assertTrue(!evening.morning && evening.azimuth in 220.0..260.0)
    }

    @Test
    fun midnightIsBelowTheHorizon() {
        assertTrue(sun("2026-11-16T00:30:00Z").elevation < -40)
    }

    @Test
    fun sunriseNearSevenInNovember() {
        // Casablanca, 16 November: sunrise about 07:25 UTC.
        assertTrue(sun("2026-11-16T07:00:00Z").elevation < 0)
        assertTrue(sun("2026-11-16T07:50:00Z").elevation > 0)
    }

    @Test
    fun theSkyChangesThroughTheDay() {
        val noon = SkyPalette.of(WeatherCondition.CLEAR, Month.NOVEMBER, false, sun("2026-11-16T12:40:00Z"))
        val golden = SkyPalette.of(WeatherCondition.CLEAR, Month.NOVEMBER, false, sun("2026-11-16T17:00:00Z"))
        val dusk = SkyPalette.of(WeatherCondition.CLEAR, Month.NOVEMBER, false, sun("2026-11-16T17:40:00Z"))
        assertNotEquals(noon.bottom, golden.bottom)
        assertNotEquals(golden.bottom, dusk.bottom)
        assertTrue(noon.sunY < golden.sunY)
        assertTrue(golden.sunX > noon.sunX)
    }
}
