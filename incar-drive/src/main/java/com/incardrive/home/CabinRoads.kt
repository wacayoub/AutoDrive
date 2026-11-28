package com.incardrive.home

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The geometry of the « Cabine » road view: the OpenStreetMap roads already kept on the head unit for the speed limits,
 * in metres east and north of the car, and the road the car is on from the car onwards, in its direction of travel.
 */
internal object CabinRoads {
    private const val METERS_PER_DEGREE = 111_320.0

    /** [roads] as flat (east, north) metre pairs around ([latitude], [longitude]). */
    fun localize(roads: List<List<Pair<Double, Double>>>, latitude: Double, longitude: Double): List<FloatArray> {
        val across = cos(Math.toRadians(latitude)) * METERS_PER_DEGREE
        return roads.filter { it.size >= 2 }.map { points ->
            FloatArray(points.size * 2).also { out ->
                points.forEachIndexed { i, (lat, lon) ->
                    out[i * 2] = ((lon - longitude) * across).toFloat()
                    out[i * 2 + 1] = ((lat - latitude) * METERS_PER_DEGREE).toFloat()
                }
            }
        }
    }

    /**
     * The road [line] from the point nearest the car (at 0, 0) onwards, in the direction closest to [heading] (degrees
     * clockwise from north), up to [reach] metres: flat (east, north) pairs starting at the car's place on the road.
     * Null when the line is too far from the car.
     */
    fun ahead(line: FloatArray, heading: Float, reach: Float = 600f, near: Float = 40f): FloatArray? {
        val count = line.size / 2
        if (count < 2) return null
        var best = -1
        var bestT = 0f
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until count - 1) {
            val ax = line[i * 2]; val ay = line[i * 2 + 1]
            val dx = line[i * 2 + 2] - ax; val dy = line[i * 2 + 3] - ay
            val length2 = dx * dx + dy * dy
            val t = if (length2 == 0f) 0f else (-(ax * dx + ay * dy) / length2).coerceIn(0f, 1f)
            val distance = hypot(ax + dx * t, ay + dy * t)
            if (distance < bestDistance) { bestDistance = distance; best = i; bestT = t }
        }
        if (best < 0 || bestDistance > near) return null
        // Forward along the line, or backward when the line was drawn the other way.
        val dx = line[best * 2 + 2] - line[best * 2]
        val dy = line[best * 2 + 3] - line[best * 2 + 1]
        val segmentHeading = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
        val forward = kotlin.math.abs(angle(segmentHeading - heading)) <= 90f
        val out = ArrayList<Float>()
        val startX = line[best * 2] + dx * bestT
        val startY = line[best * 2 + 1] + dy * bestT
        out += startX; out += startY
        var travelled = 0f
        var x = startX; var y = startY
        var index = if (forward) best + 1 else best
        while (index in 0 until count && travelled < reach) {
            val nx = line[index * 2]; val ny = line[index * 2 + 1]
            travelled += hypot(nx - x, ny - y)
            out += nx; out += ny
            x = nx; y = ny
            index += if (forward) 1 else -1
        }
        return if (out.size >= 4) out.toFloatArray() else null
    }

    /** The point [meters] along [line] (flat pairs), or null past its end. */
    fun along(line: FloatArray, meters: Float): Pair<Float, Float>? {
        var left = meters
        for (i in 0 until line.size / 2 - 1) {
            val ax = line[i * 2]; val ay = line[i * 2 + 1]
            val bx = line[i * 2 + 2]; val by = line[i * 2 + 3]
            val length = hypot(bx - ax, by - ay)
            if (left <= length) {
                val t = if (length == 0f) 0f else left / length
                return (ax + (bx - ax) * t) to (ay + (by - ay) * t)
            }
            left -= length
        }
        return null
    }

    /** [line] between [from] and [to] metres, as flat pairs (empty when outside it). */
    fun part(line: FloatArray, from: Float, to: Float): FloatArray {
        val out = ArrayList<Float>()
        var at = 0f
        for (i in 0 until line.size / 2 - 1) {
            val ax = line[i * 2]; val ay = line[i * 2 + 1]
            val bx = line[i * 2 + 2]; val by = line[i * 2 + 3]
            val length = hypot(bx - ax, by - ay)
            val end = at + length
            if (end >= from && at <= to && length > 0f) {
                val t0 = ((from - at) / length).coerceIn(0f, 1f)
                val t1 = ((to - at) / length).coerceIn(0f, 1f)
                if (out.isEmpty()) { out += ax + (bx - ax) * t0; out += ay + (by - ay) * t0 }
                out += ax + (bx - ax) * t1; out += ay + (by - ay) * t1
            }
            at = end
            if (at > to) break
        }
        return out.toFloatArray()
    }

    /** An angle brought into -180..180 degrees. */
    fun angle(degrees: Float): Float = ((degrees % 360f) + 540f) % 360f - 180f

    /** The (east, north) metres covered at [metersPerSecond] for [seconds] towards [heading]. */
    fun moved(heading: Float, metersPerSecond: Float, seconds: Float): Pair<Float, Float> {
        val radians = Math.toRadians(heading.toDouble())
        val distance = metersPerSecond * seconds
        return (sin(radians) * distance).toFloat() to (cos(radians) * distance).toFloat()
    }
}
