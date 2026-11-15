package com.autodrive.radiomaroc

import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemNightTest {
    // Morocco keeps UTC+1 outside Ramadan; a fixed offset keeps the test independent of the JVM time zone data.
    private val casablanca = ZoneOffset.ofHours(1)
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, casablanca)

    @Test fun sunsetToSunriseFollowsTheSeasons() {
        // 15 November in Casablanca: sunrise about 08:05, sunset about 18:25 (UTC+1).
        assertFalse(SystemNight.isDaylight(at(11, 15, 7, 0)))
        assertTrue(SystemNight.isDaylight(at(11, 15, 8, 15)))
        assertTrue(SystemNight.isDaylight(at(11, 15, 17, 45)))
        assertFalse(SystemNight.isDaylight(at(11, 15, 18, 45)))
        assertFalse(SystemNight.isDaylight(at(11, 15, 23, 0)))
        // Midsummer days are much longer: still light at 20:00.
        assertTrue(SystemNight.isDaylight(at(6, 21, 20, 0)))
        assertFalse(SystemNight.isDaylight(at(6, 21, 21, 30)))
    }

    @Test fun customNightWindowsMayCrossMidnight() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(7, 0)
        assertTrue(SystemNight.inWindow(LocalTime.of(23, 30), start, end))
        assertTrue(SystemNight.inWindow(LocalTime.of(6, 59), start, end))
        assertFalse(SystemNight.inWindow(LocalTime.of(7, 0), start, end))
        assertFalse(SystemNight.inWindow(LocalTime.of(12, 0), start, end))
        assertTrue(SystemNight.inWindow(LocalTime.of(13, 0), LocalTime.of(12, 0), LocalTime.of(14, 0)))
    }
}
