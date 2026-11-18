package com.incardrive.home

/**
 * An estimate of the fuel a trip used, from its distance and average speed only: Incar Drive does not read the car.
 * The driver gives the car's usual consumption (what the car's own display shows over weeks, 4.5 L/100 km by default
 * for a Corolla Hybrid); each trip moves away from it the way a hybrid does: least in town traffic, where it drives on
 * electricity often, more in jams, on fast roads, and on short trips with a cold engine.
 */
internal object Fuel {
    const val DEFAULT_L100 = 4.5f

    /** How far a trip at [averageKmh] is from the usual consumption. */
    fun factor(averageKmh: Int?): Double = when {
        averageKmh == null -> 1.0
        averageKmh < 15 -> 1.15
        averageKmh < 35 -> .95
        averageKmh < 70 -> .9
        averageKmh < 95 -> 1.05
        averageKmh < 115 -> 1.2
        else -> 1.4
    }

    /** Litres for a trip of [meters] at [averageKmh], the car using [usualL100] L/100 km on average. */
    fun litres(meters: Double, averageKmh: Int?, usualL100: Float): Double {
        if (meters <= 0 || usualL100 <= 0) return 0.0
        // An engine that has not warmed up uses more over the first kilometres.
        val cold = if (meters < 5_000) 1.15 else 1.0
        return meters / 100_000.0 * usualL100 * factor(averageKmh) * cold
    }

    fun litres(trip: Trip, usualL100: Float) = litres(trip.meters, trip.averageKmh, usualL100)
}
