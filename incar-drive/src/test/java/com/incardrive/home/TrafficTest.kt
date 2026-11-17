package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrafficTest {
    @Test fun thePaceOfTheTripGivesTheLevel() {
        assertEquals(Flow.FLUID, Traffic.ofTrip("12 min", "9 km"))      // 45 km/h
        assertEquals(Flow.SLOW, Traffic.ofTrip("36 min", "14 km"))      // 23 km/h
        assertEquals(Flow.JAM, Traffic.ofTrip("40 min", "8 km"))        // 12 km/h
        assertEquals(Flow.FLUID, Traffic.ofTrip("1 h 05 min", "95 km"))
        assertEquals(Flow.SLOW, Traffic.ofTrip("1 h", "20,5 km"))
    }

    @Test fun aShortOrUnreadableTripSaysNothing() {
        assertNull(Traffic.ofTrip("2 min", "600 m"))
        assertNull(Traffic.ofTrip(null, "5 km"))
        assertNull(Traffic.ofTrip("10 min", null))
        assertNull(Traffic.ofTrip("bientôt", "5 km"))
    }

    @Test fun theCarsOwnSpeedAgainstTheLimit() {
        fun meter(vararg speeds: Int) = Traffic.Meter().apply { speeds.forEachIndexed { i, kmh -> add(i * 5_000L, kmh) } }
        assertEquals(Flow.FLUID, meter(48, 52, 50, 47, 49, 51, 50, 50).flow(60))
        assertEquals(Flow.SLOW, meter(30, 20, 25, 35, 28, 22, 30, 26).flow(60))
        assertEquals(Flow.JAM, meter(0, 5, 0, 8, 12, 0, 6, 4).flow(60))
        // Without a known limit, a town road of 50 is assumed.
        assertEquals(Flow.FLUID, meter(40, 42, 38, 41, 40, 39, 40, 41).flow(null))
    }

    @Test fun tooLittleDrivingOrAParkedCarSaysNothing() {
        assertNull(Traffic.Meter().apply { add(0, 20); add(5_000, 20) }.flow(50))
        assertNull(Traffic.Meter().apply { repeat(10) { add(it * 5_000L, 0) } }.flow(50))
    }

    @Test fun oldSpeedsAreForgotten() {
        val meter = Traffic.Meter()
        repeat(10) { meter.add(it * 5_000L, 5) }                 // a jam…
        repeat(14) { meter.add(50_000L + it * 5_000L, 55) }      // …then a clear road for over a minute
        assertEquals(Flow.FLUID, meter.flow(60))
    }
}
