package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMeterTest {
    // About 11.1 m of latitude per 0.0001°.
    private fun drive(meter: TripMeter, from: Long, seconds: Int, kmh: Int, startLat: Double = 33.5): Double {
        val metersPerSecond = kmh / 3.6
        var lat = startLat
        for (s in 0..seconds) {
            meter.accept(from + s * 1000L, lat, -7.6, 5f, kmh)
            lat += metersPerSecond / 111_195.0
        }
        return lat
    }

    @Test
    fun distanceOfOneDegreeOfLatitude() {
        assertEquals(111_195.0, TripMeter.distanceMeters(0.0, 0.0, 1.0, 0.0), 1.0)
    }

    @Test
    fun countsDistanceAndMovingTime() {
        val meter = TripMeter(null)
        drive(meter, 1_000_000L, 120, 60)
        val trip = meter.trip
        assertEquals(2_000.0, trip.meters, 40.0)
        assertEquals(120_000L, trip.movingMs)
        assertEquals(60.0, trip.averageKmh!!.toDouble(), 2.0)
        assertEquals(60, trip.maxKmh)
    }

    @Test
    fun parkedJitterAddsNothing() {
        val meter = TripMeter(null)
        for (s in 0..600) meter.accept(s * 1000L, 33.5 + (s % 3) * 0.00003, -7.6, 10f, 0)
        assertEquals(0.0, meter.trip.meters, 0.0)
        assertNull(meter.trip.averageKmh)
    }

    @Test
    fun inaccurateFixesAreIgnored() {
        val meter = TripMeter(null)
        meter.accept(0, 33.5, -7.6, 5f, 50)
        meter.accept(1000, 33.6, -7.6, 80f, 50)
        assertEquals(0.0, meter.trip.meters, 0.0)
    }

    @Test
    fun longStopStartsANewTrip() {
        val meter = TripMeter(null)
        val lat = drive(meter, 0, 60, 50)
        val firstStart = meter.trip.startedAt
        drive(meter, 3 * 3_600_000L, 10, 50, lat)
        assertTrue(meter.trip.startedAt > firstStart)
        assertTrue(meter.trip.meters < 200)
    }

    @Test
    fun aNewTripStartsWhenTheCarFirstMoves() {
        val meter = TripMeter(null)
        meter.reset(0)
        meter.accept(10_000, 33.5, -7.6, 5f, 0)
        assertEquals(0L, meter.trip.startedAt)
        meter.accept(70_000, 33.5, -7.6, 5f, 20)
        assertEquals(70_000L, meter.trip.startedAt)
    }

    @Test
    fun encodesAndDecodes() {
        val trip = Trip(1234.5, 60_000, 88, 10, 20)
        assertEquals(trip, Trip.decode(trip.encode()))
        assertNull(Trip.decode("broken"))
    }
}
