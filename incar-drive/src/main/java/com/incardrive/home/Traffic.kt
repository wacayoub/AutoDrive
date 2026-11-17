package com.incardrive.home

import android.graphics.Color

/** How the traffic flows, in the three colours drivers know from their navigation app. */
internal enum class Flow(val color: Int) {
    FLUID(0xFF2FBF71.toInt()), SLOW(0xFFFF9F1A.toInt()), JAM(0xFFE5262E.toInt())
}

/**
 * Traffic levels without any traffic service or account, from what the head unit already has:
 * - the colour Google Maps gives its arrival time in its notification, when it gives one;
 * - else the pace of the rest of the trip (distance left over time left);
 * - on the road itself, the car's own average speed over the last minute against the road's limit.
 * These are estimates of the traffic the driver is in, not a traffic map.
 */
internal object Traffic {
    private val hours = Regex("""(?i)(\d{1,2})\s*h""")
    private val minutes = Regex("""(?i)(\d{1,3})\s*min""")
    private val distance = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(km|m)\b""")

    /** From a text colour of the navigation app: its green, orange or red; null for any other colour. */
    fun ofColor(color: Int): Flow? {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[1] < .35f || hsv[2] < .35f) return null
        return when (hsv[0]) {
            in 0f..18f, in 340f..360f -> Flow.JAM
            in 18f..50f -> Flow.SLOW
            in 80f..170f -> Flow.FLUID
            else -> null
        }
    }

    /**
     * From the trip left: « 36 min » for « 14 km » is a pace of 23 km/h. Under 16 km/h is a jam, under 28 slow, the
     * rest fluid; trips of under a kilometre say too little.
     */
    fun ofTrip(remaining: String?, left: String?): Flow? {
        val time = remaining ?: return null
        val total = (hours.find(time)?.groupValues?.get(1)?.toIntOrNull() ?: 0) * 60 +
            (minutes.find(time)?.groupValues?.get(1)?.toIntOrNull() ?: 0)
        val match = distance.find(left ?: return null) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val km = if (match.groupValues[2].equals("km", ignoreCase = true)) value else value / 1000
        if (total <= 0 || km < 1.0) return null
        val pace = km / (total / 60.0)
        return when {
            pace < 16 -> Flow.JAM
            pace < 28 -> Flow.SLOW
            else -> Flow.FLUID
        }
    }

    /** The car's own speed over the last minute, against the limit of the road it is on. */
    class Meter {
        private val samples = ArrayDeque<Pair<Long, Int>>()

        fun add(timeMs: Long, kmh: Int) {
            samples.addLast(timeMs to kmh)
            while (samples.isNotEmpty() && timeMs - samples.first().first > WINDOW_MS) samples.removeFirst()
        }

        fun clear() = samples.clear()

        /** Null until half a minute of driving is known, or when the car has not moved at all in that time (parked). */
        fun flow(limit: Int?): Flow? {
            if (samples.size < 5 || samples.last().first - samples.first().first < MIN_SPAN_MS) return null
            if (samples.all { it.second == 0 }) return null
            val average = samples.sumOf { it.second } / samples.size.toDouble()
            val ratio = average / (limit ?: DEFAULT_LIMIT).coerceAtLeast(30)
            return when {
                ratio < .3 -> Flow.JAM
                ratio < .6 -> Flow.SLOW
                else -> Flow.FLUID
            }
        }

        private companion object {
            const val WINDOW_MS = 60_000L
            const val MIN_SPAN_MS = 30_000L
            const val DEFAULT_LIMIT = 50
        }
    }
}
