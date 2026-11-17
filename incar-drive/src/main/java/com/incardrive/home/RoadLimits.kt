package com.incardrive.home

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A road around the car: its speed limit (null when the map has none) and its shape. */
internal data class Road(val limit: Int?, val name: String?, val points: List<Pair<Double, Double>>)

/** A fixed speed camera from OpenStreetMap (`highway=speed_camera`), with its limit when tagged. */
internal data class Camera(val latitude: Double, val longitude: Double, val limit: Int?)

/**
 * The legal speed limit of the road the car is on, from OpenStreetMap's `maxspeed` tags through the public Overpass
 * API. Roads within 800 m are fetched once and matched locally to each GPS fix; they are fetched again after 450 m.
 * Only the position is sent, with no identifier. Roads without a limit in the map give no limit, rather than a guess,
 * except the Moroccan default tags (urban 60, rural 100, motorway 120).
 */
internal class RoadLimits {
    private var roads: List<Road> = emptyList()
    private var cameras: List<Camera> = emptyList()
    private var center: Pair<Double, Double>? = null
    private var lastAttempt = 0L
    private var failures = 0

    /** True when the roads around [latitude], [longitude] should be fetched now. */
    fun needsRefresh(latitude: Double, longitude: Double, now: Long): Boolean {
        // After a failure (no network, or the free server is busy) wait longer each time, up to five minutes.
        val wait = if (failures == 0) RETRY_MS else (RETRY_MS shl (failures - 1).coerceAtMost(4)).coerceAtMost(300_000L)
        if (now - lastAttempt < wait) return false
        val from = center ?: return true
        return TripMeter.distanceMeters(from.first, from.second, latitude, longitude) > REFRESH_METERS
    }

    /** Blocking: call off the main thread. */
    fun refresh(latitude: Double, longitude: Double, now: Long) {
        lastAttempt = now
        // The public Overpass servers are often busy: try each in turn.
        var result: Result<Pair<List<Road>, List<Camera>>> = Result.failure(IOException("No server"))
        for (server in SERVERS) {
            result = runCatching { download(server, query(latitude, longitude)).let { parse(it) to parseCameras(it) } }
            if (result.isSuccess) break
            android.util.Log.d(TAG, "$server: ${result.exceptionOrNull()?.message}")
        }
        val fetched = result.getOrNull()
        if (fetched == null) {
            failures++
            android.util.Log.w(TAG, "Road limits unavailable (attempt $failures): ${result.exceptionOrNull()?.message}")
            return
        }
        failures = 0
        android.util.Log.i(TAG, "${fetched.first.size} roads around the car, ${fetched.first.count { it.limit != null }} with a limit, " +
            "${fetched.second.size} speed cameras")
        roads = fetched.first
        cameras = fetched.second
        center = latitude to longitude
    }

    /** The limit of the road the car is on, or null when unknown. */
    fun limitAt(latitude: Double, longitude: Double, bearing: Int?): Road? = match(roads, latitude, longitude, bearing)

    /** The nearest speed camera ahead of the car, and its distance in metres. */
    /** The speed cameras found around the car, for the map. */
    fun cameraList(): List<Camera> = cameras

    fun cameraAhead(latitude: Double, longitude: Double, bearing: Int?): Pair<Camera, Int>? =
        cameraAhead(cameras, latitude, longitude, bearing)

    private fun query(latitude: Double, longitude: Double) =
        "[out:json][timeout:30];(way(around:$RADIUS,$latitude,$longitude)[highway~\"^($ROAD_TYPES)$\"];" +
            "node(around:$CAMERA_RADIUS,$latitude,$longitude)[highway=speed_camera];);out tags geom;"

    private fun download(server: String, query: String): String {
        val url = URI("$server?data=" + URLEncoder.encode(query, "UTF-8")).toURL()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            // A busy public server can take 20 seconds or more to answer.
            connection.connectTimeout = 8_000
            connection.readTimeout = 35_000
            connection.setRequestProperty("User-Agent", "IncarDrive/1.0 (speed limit display)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Overpass ${connection.responseCode}")
            val out = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16_384)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > LIMIT_BYTES) throw IOException("Réponse trop volumineuse")
                }
            }
            return out.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val TAG = "IncarDriveRoads"
        val SERVERS = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter")
        const val RADIUS = 800
        const val CAMERA_RADIUS = 2000
        const val CAMERA_WARNING_METERS = 500
        const val REFRESH_METERS = 450.0
        const val RETRY_MS = 20_000L
        const val MATCH_METERS = 30.0
        const val LIMIT_BYTES = 3_000_000
        const val ROAD_TYPES = "motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|" +
            "tertiary|tertiary_link|unclassified|residential|living_street|road"

        /** km/h from an OSM maxspeed value: « 60 », « 30 mph », or a Moroccan default like « MA:urban ». */
        fun parseMaxspeed(value: String?): Int? {
            val text = value?.trim()?.lowercase() ?: return null
            when (text) {
                "ma:urban" -> return 60
                "ma:rural" -> return 100
                "ma:motorway" -> return 120
            }
            val number = Regex("""^(\d{1,3})\s*(mph)?$""").find(text) ?: return null
            val speed = number.groupValues[1].toInt()
            val kmh = if (number.groupValues[2] == "mph") (speed * 1.609).roundToInt() else speed
            return kmh.takeIf { it in 5..150 }
        }

        fun parseCameras(json: String): List<Camera> {
            val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
            return (0 until elements.length()).mapNotNull { index ->
                val node = elements.optJSONObject(index)?.takeIf { it.optString("type") == "node" } ?: return@mapNotNull null
                val tags = node.optJSONObject("tags")
                if (tags?.optString("highway") != "speed_camera") return@mapNotNull null
                Camera(node.optDouble("lat"), node.optDouble("lon"), parseMaxspeed(tags.optString("maxspeed").ifEmpty { null }))
                    .takeIf { !it.latitude.isNaN() && !it.longitude.isNaN() }
            }
        }

        /**
         * The closest camera within [CAMERA_WARNING_METERS] that lies ahead: within 35° of the heading when it is
         * known, otherwise within 200 m in any direction.
         */
        fun cameraAhead(cameras: List<Camera>, latitude: Double, longitude: Double, bearing: Int?): Pair<Camera, Int>? =
            cameras.mapNotNull { camera ->
                val meters = TripMeter.distanceMeters(latitude, longitude, camera.latitude, camera.longitude)
                if (meters > CAMERA_WARNING_METERS) return@mapNotNull null
                if (bearing == null) return@mapNotNull (camera to meters.toInt()).takeIf { meters <= 200 }
                val dy = camera.latitude - latitude
                val dx = (camera.longitude - longitude) * cos(Math.toRadians(latitude))
                val toward = (Math.toDegrees(atan2(dx, dy)) + 360) % 360
                var diff = abs(toward - bearing) % 360
                if (diff > 180) diff = 360 - diff
                (camera to meters.toInt()).takeIf { diff <= 35 || meters < 40 }
            }.minByOrNull { it.second }

        fun parse(json: String): List<Road> {
            val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
            return (0 until elements.length()).mapNotNull { index ->
                val way = elements.optJSONObject(index) ?: return@mapNotNull null
                val tags = way.optJSONObject("tags") ?: return@mapNotNull null
                val geometry = way.optJSONArray("geometry") ?: return@mapNotNull null
                val points = (0 until geometry.length()).mapNotNull { i ->
                    geometry.optJSONObject(i)?.let { it.optDouble("lat") to it.optDouble("lon") }
                }.filter { !it.first.isNaN() && !it.second.isNaN() }
                if (points.size < 2) return@mapNotNull null
                val limit = parseMaxspeed(tags.optString("maxspeed").ifEmpty { null })
                    ?: parseMaxspeed(tags.optString("maxspeed:type").ifEmpty { null })
                    ?: parseMaxspeed(tags.optString("source:maxspeed").ifEmpty { null })
                Road(limit, tags.optString("name:fr").ifEmpty { tags.optString("name") }.ifEmpty { null }, points)
            }
        }

        /**
         * The road nearest to the position within [MATCH_METERS]. When the car's heading is known, a road running
         * across it counts as further away, so a crossing street is not taken for the road being driven.
         */
        fun match(roads: List<Road>, latitude: Double, longitude: Double, bearing: Int?): Road? {
            val metersPerLat = 111_195.0
            val metersPerLon = metersPerLat * cos(Math.toRadians(latitude))
            var best: Road? = null
            var bestScore = Double.MAX_VALUE
            roads.forEach { road ->
                road.points.zipWithNext().forEach { (a, b) ->
                    val ax = (a.second - longitude) * metersPerLon
                    val ay = (a.first - latitude) * metersPerLat
                    val bx = (b.second - longitude) * metersPerLon
                    val by = (b.first - latitude) * metersPerLat
                    val dx = bx - ax
                    val dy = by - ay
                    val length = dx * dx + dy * dy
                    val t = if (length == 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
                    val distance = sqrt((ax + t * dx).let { it * it } + (ay + t * dy).let { it * it })
                    if (distance > MATCH_METERS) return@forEach
                    val penalty = bearing?.let {
                        val segment = (Math.toDegrees(atan2(dx, dy)) + 360) % 360
                        var diff = abs(segment - it) % 180
                        if (diff > 90) diff = 180 - diff
                        diff / 90 * 20
                    } ?: 0.0
                    if (distance + penalty < bestScore) {
                        bestScore = distance + penalty
                        best = road
                    }
                }
            }
            return best
        }
    }
}
