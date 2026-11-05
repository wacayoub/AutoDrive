package com.autodrive.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedFilterTest {
    private val filter = SpeedFilter()

    private fun accept(speed: Float?, time: Long, accuracy: Float? = .2f) {
        filter.accept(speed, accuracy, time, time)
    }

    @Test fun convertsAndSmoothsSpeedInKilometersPerHour() {
        accept(20f, 1_000)
        assertEquals(SpeedReading(72), filter.current(1_000))
        accept(10f, 2_000)
        assertEquals(SpeedReading(58), filter.current(2_000))
    }

    @Test fun requiresThreeAccurateNearZeroReadingsSpanningTwoSeconds() {
        accept(0.1f, 1_000)
        assertNull(filter.current(1_000).kmh)
        accept(0.3f, 1_100)
        accept(0.2f, 1_200)
        assertNull(filter.current(1_200).kmh)
        accept(0.2f, 3_000)
        assertEquals(SpeedReading(0, true), filter.current(3_000))
        accept(2f, 4_000)
        assertEquals(SpeedReading(7, false), filter.current(4_000))
    }

    @Test fun poorAccuracyAndMissingSpeedNeverBecomeStationary() {
        accept(0f, 1_000, .7f)
        accept(0f, 2_000, .7f)
        accept(0f, 3_000, .7f)
        assertEquals(SpeedReading(), filter.current(3_000))
        accept(12f, 4_000, null)
        assertEquals(SpeedReading(), filter.current(4_000))
        accept(null, 5_000)
        assertEquals(SpeedReading(), filter.current(5_000))
        accept(Float.NaN, 6_000)
        assertEquals(SpeedReading(), filter.current(6_000))
    }

    @Test fun staleFutureAndOutOfOrderReadingsAreIgnored() {
        filter.accept(13f, .2f, 1_000, 6_000)
        filter.accept(13f, .2f, 7_000, 6_000)
        assertNull(filter.current(6_000).kmh)
        accept(20f, 7_000)
        accept(5f, 6_000)
        assertEquals(SpeedReading(72), filter.current(7_000))
        assertNull(filter.current(11_001).kmh)
    }

    @Test fun invalidSampleBreaksStationarySequenceAndFilterHistory() {
        accept(0f, 1_000)
        accept(0f, 2_000)
        accept(null, 2_500)
        accept(0f, 3_000)
        assertFalse(filter.current(3_000).stationary)
        accept(null, 4_000)
        accept(30f, 5_000)
        assertEquals(SpeedReading(108), filter.current(5_000))
        filter.reset()
        assertEquals(SpeedReading(), filter.current(5_000))
    }

    @Test fun longGapRestartsStationarySequence() {
        accept(0f, 1_000)
        accept(0f, 2_000)
        accept(0f, 8_000)
        assertEquals(SpeedReading(), filter.current(8_000))
    }
}
