package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelTest {
    @Test fun aTownTripUsesLessThanTheUsualConsumption() {
        // 20 km at 45 km/h, the car using 4.5 L/100 km: 0.9 L at the usual rate, 10 % less in town flow.
        assertEquals(.81, Fuel.litres(20_000.0, 45, 4.5f), .001)
    }

    @Test fun fastRoadsAndJamsUseMore() {
        assertTrue(Fuel.litres(50_000.0, 120, 4.5f) > Fuel.litres(50_000.0, 85, 4.5f))
        assertTrue(Fuel.litres(10_000.0, 10, 4.5f) > Fuel.litres(10_000.0, 45, 4.5f))
    }

    @Test fun aShortTripCountsTheColdEngine() {
        assertEquals(3_000 / 100_000.0 * 4.5 * .9 * 1.15, Fuel.litres(3_000.0, 40, 4.5f), 1e-9)
    }

    @Test fun nothingDrivenIsNothingUsed() {
        assertEquals(0.0, Fuel.litres(0.0, 50, 4.5f), 0.0)
        assertEquals(.45, Fuel.litres(10_000.0, null, 4.5f), 1e-9)
    }
}
