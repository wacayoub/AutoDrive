package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TripLogTest {
    private val zone: ZoneId = ZoneOffset.ofHours(1)
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun shortTripsAreNotKept() {
        val log = TripLog(null)
        assertFalse(log.add(Trip(meters = 150.0, startedAt = at(18, 8))))
        assertTrue(log.add(Trip(meters = 5_000.0, startedAt = at(18, 8))))
        assertEquals(1, log.trips.size)
    }

    @Test fun theSameTripIsKeptOnce() {
        val log = TripLog(null)
        log.add(Trip(meters = 2_000.0, startedAt = at(18, 8)))
        log.add(Trip(meters = 3_000.0, startedAt = at(18, 8)))
        assertEquals(1, log.trips.size)
        assertEquals(3_000.0, log.trips[0].meters, .1)
    }

    @Test fun itSurvivesBeingStored() {
        val log = TripLog(null)
        log.add(Trip(1_234.5, 600_000, 87, at(17, 7), at(17, 8)))
        log.add(Trip(9_000.0, 900_000, 110, at(18, 7), at(18, 8)))
        val again = TripLog(log.encode())
        assertEquals(log.trips, again.trips)
        assertEquals(at(18, 7), again.trips[0].startedAt)
    }

    @Test fun totalsCountTodayTheWeekAndTheMonth() {
        // Wednesday 18 November 2026; the week began on Monday 16.
        val log = TripLog(null)
        log.add(Trip(10_000.0, 1_200_000, 90, at(18, 8), at(18, 9)))
        log.add(Trip(20_000.0, 1_800_000, 90, at(16, 8), at(16, 9)))
        log.add(Trip(40_000.0, 3_600_000, 90, at(14, 8), at(14, 9)))
        log.add(Trip(80_000.0, 3_600_000, 90, LocalDateTime.of(2026, 10, 30, 8, 0).atZone(zone).toInstant().toEpochMilli(), 0))
        val current = Trip(5_000.0, 600_000, 60, at(18, 17), at(18, 17))
        val (today, week, month) = log.totals(current, at(18, 18), zone)
        assertEquals(15_000.0, today.meters, .1)
        assertEquals(2, today.trips)
        assertEquals(35_000.0, week.meters, .1)
        assertEquals(75_000.0, month.meters, .1)
        assertEquals(4, month.trips)
        assertTrue(today.litres > 0)
    }

    @Test fun tripsAreGroupedByDay() {
        val trips = listOf(Trip(1_000.0, startedAt = at(17, 8)), Trip(1_000.0, startedAt = at(18, 17)),
            Trip(1_000.0, startedAt = at(18, 8)))
        val days = TripLog.byDay(trips, zone)
        assertEquals(2, days.size)
        assertEquals(18, days[0].first.dayOfMonth)
        assertEquals(listOf(at(18, 17), at(18, 8)), days[0].second.map { it.startedAt })
    }
}
