package com.autodrive.launcher

import kotlin.math.roundToInt

internal data class SpeedReading(val kmh: Int? = null, val stationary: Boolean = false)

internal class SpeedFilter {
    private var lastSample = -1L
    private var filtered = 0.0
    private var stationarySamples = 0
    private var stationarySince = -1L
    private var validPreviousSample = false
    private var reading = SpeedReading()

    fun accept(speed: Float?, accuracy: Float?, sampleMs: Long, nowMs: Long) {
        if (sampleMs <= lastSample || nowMs - sampleMs !in 0..MAX_AGE_MS) return
        val continuous = validPreviousSample && sampleMs - lastSample <= MAX_AGE_MS
        lastSample = sampleMs
        if (speed == null || !speed.isFinite() || speed < 0f ||
            accuracy == null || !accuracy.isFinite() || accuracy !in 0f..2f
        ) {
            stationarySamples = 0
            stationarySince = -1L
            validPreviousSample = false
            reading = SpeedReading()
            return
        }

        val nearZero = speed <= STATIONARY_THRESHOLD && accuracy <= 0.5f
        if (nearZero) {
            if (!continuous || stationarySamples == 0) {
                stationarySince = sampleMs
                stationarySamples = 0
            }
            stationarySamples++
        } else {
            stationarySamples = 0
            stationarySince = -1L
        }

        val stationary = stationarySamples >= 3 && sampleMs - stationarySince >= 2_000
        filtered = if (continuous && !reading.stationary) filtered * 0.6 + speed * 0.4 else speed.toDouble()
        val measured = (filtered * 3.6).roundToInt()
        reading = SpeedReading(
            kmh = when {
                stationary -> 0
                measured > 0 && speed > STATIONARY_THRESHOLD -> measured
                else -> null
            },
            stationary = stationary
        )
        validPreviousSample = true
    }

    fun current(nowMs: Long): SpeedReading =
        if (nowMs - lastSample in 0..MAX_AGE_MS) reading else SpeedReading()

    fun reset() {
        lastSample = -1
        filtered = 0.0
        stationarySamples = 0
        stationarySince = -1
        validPreviousSample = false
        reading = SpeedReading()
    }

    private companion object {
        const val MAX_AGE_MS = 4_000L
        const val STATIONARY_THRESHOLD = 0.3f
    }
}
