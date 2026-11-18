package com.incardrive.home

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One trip: distance, time spent moving, top speed, when it began and when the car last moved (epoch ms). */
internal data class Trip(
    val meters: Double = 0.0,
    val movingMs: Long = 0,
    val maxKmh: Int = 0,
    val startedAt: Long = 0,
    val lastMoveAt: Long = 0,
) {
    /** Average speed while moving, once there is a minute of driving to average. */
    val averageKmh: Int? get() = if (movingMs < 60_000) null else (meters / 1000 / (movingMs / 3_600_000.0)).toInt()

    fun encode() = "$meters;$movingMs;$maxKmh;$startedAt;$lastMoveAt"

    companion object {
        fun decode(value: String?): Trip? = value?.split(';')?.takeIf { it.size == 5 }?.let {
            runCatching { Trip(it[0].toDouble(), it[1].toLong(), it[2].toInt(), it[3].toLong(), it[4].toLong()) }.getOrNull()
        }
    }
}

/**
 * Adds GPS fixes up into a trip. Only fixes accurate to 30 m count; a step is added once the car has moved further
 * than the fix's own uncertainty, so a parked car does not drift kilometres. After two hours without moving, the
 * next movement starts a new trip.
 */
internal class TripMeter(restored: Trip?) {
    var trip: Trip = restored ?: Trip()
        private set
    private var anchor: Fix? = null
    private var previous: Fix? = null

    private data class Fix(val at: Long, val latitude: Double, val longitude: Double)

    /** Called with each trip that ends, before the next one starts. */
    var onFinished: ((Trip) -> Unit)? = null

    /** A new trip: it starts at [now], or, with 0, at the car's first movement. */
    fun reset(now: Long) {
        trip.takeIf { it.meters > 0 }?.let { onFinished?.invoke(it) }
        trip = Trip(startedAt = now, lastMoveAt = now)
        anchor = null
        previous = null
    }

    fun accept(now: Long, latitude: Double, longitude: Double, accuracyMeters: Float?, speedKmh: Int?) {
        if (accuracyMeters == null || accuracyMeters > MAX_ACCURACY) return
        val moving = (speedKmh ?: 0) >= MOVING_KMH
        if (moving && trip.lastMoveAt > 0 && now - trip.lastMoveAt > NEW_TRIP_AFTER_MS) reset(now)
        // A trip waiting to start begins when the car first moves.
        if (trip.startedAt == 0L) {
            if (!moving) return
            trip = trip.copy(startedAt = now, lastMoveAt = now)
        }
        val fix = Fix(now, latitude, longitude)
        val last = previous
        previous = fix
        if (moving && last != null && now - last.at in 1..MAX_GAP_MS) {
            trip = trip.copy(movingMs = trip.movingMs + (now - last.at), lastMoveAt = now)
        }
        if (speedKmh != null && speedKmh > trip.maxKmh) trip = trip.copy(maxKmh = speedKmh)
        val from = anchor ?: run { anchor = fix; return }
        val step = distanceMeters(from.latitude, from.longitude, latitude, longitude)
        val seconds = (now - from.at) / 1000.0
        if (step < max(MIN_STEP, accuracyMeters.toDouble())) return
        anchor = fix
        // A jump faster than 250 km/h is a bad fix, not driving.
        if (seconds <= 0 || step / seconds > 70) return
        trip = trip.copy(meters = trip.meters + step, lastMoveAt = now)
    }

    companion object {
        const val MAX_ACCURACY = 30f
        const val MOVING_KMH = 5
        const val MIN_STEP = 8.0
        const val MAX_GAP_MS = 5_000L
        const val NEW_TRIP_AFTER_MS = 2 * 3_600_000L

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val h = sin((p2 - p1) / 2).pow(2) + cos(p1) * cos(p2) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
            return 2 * 6_371_000.0 * asin(sqrt(h))
        }
    }
}
