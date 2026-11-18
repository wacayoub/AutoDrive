package com.incardrive.home

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Kilometres, driving time and number of trips over some days. */
internal data class TripTotal(val meters: Double, val movingMs: Long, val trips: Int, val litres: Double = 0.0)

/**
 * The finished trips, newest first, kept on the head unit only (the last [KEEP], months of daily driving); each is what the trip meter
 * counted from the GPS. A trip shorter than 200 m (moving the car in the car park) is not kept.
 */
internal class TripLog(stored: String?) {
    var trips: List<Trip> = stored.orEmpty().lineSequence().mapNotNull { Trip.decode(it.trim()) }.toList()
        private set

    fun add(trip: Trip): Boolean {
        if (trip.meters < MIN_METERS || trip.startedAt == 0L) return false
        // The same trip saved twice (reset, then restart) is kept once.
        if (trips.any { it.startedAt == trip.startedAt }) {
            trips = trips.map { if (it.startedAt == trip.startedAt) trip else it }
            return true
        }
        trips = (listOf(trip) + trips).sortedByDescending { it.startedAt }.take(KEEP)
        return true
    }

    fun encode() = trips.joinToString("\n") { it.encode() }

    /** Today, this week (from Monday) and this month, the [current] trip included when it has started. */
    fun totals(current: Trip?, now: Long, zone: ZoneId = ZoneId.systemDefault(), usualL100: Float = Fuel.DEFAULT_L100):
        Triple<TripTotal, TripTotal, TripTotal> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val all = trips + listOfNotNull(current?.takeIf { it.startedAt > 0 && it.meters > 0 && trips.none { t -> t.startedAt == it.startedAt } })
        fun since(day: LocalDate): TripTotal {
            val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val chosen = all.filter { it.startedAt >= from }
            return total(chosen, usualL100)
        }
        return Triple(since(today), since(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))),
            since(today.withDayOfMonth(1)))
    }

    companion object {
        fun total(trips: List<Trip>, usualL100: Float) = TripTotal(trips.sumOf { it.meters }, trips.sumOf { it.movingMs },
            trips.size, trips.sumOf { Fuel.litres(it, usualL100) })

        /** [trips] by the day they started, newest day first, each day's trips newest first. */
        fun byDay(trips: List<Trip>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, List<Trip>>> =
            trips.sortedByDescending { it.startedAt }
                .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
                .toList()

        const val KEY = "trip_log"
        const val KEEP = 500
        const val MIN_METERS = 200.0
    }
}
