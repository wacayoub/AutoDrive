package com.incardrive.home

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A road around the car: its speed limit (null when the map has none) and its shape. [oneway] is 1 when it may only
 * be driven in the direction of its points, -1 against them, 0 both ways: each side of a divided road is its own
 * one-way road, which is how the side the car is on is told apart from the other one.
 */
internal data class Road(val limit: Int?, val name: String?, val points: List<Pair<Double, Double>>, val oneway: Int = 0)

/** A fixed speed camera from OpenStreetMap (`highway=speed_camera`, or `enforcement=maxspeed`), with its limit when tagged. */
internal data class Camera(val latitude: Double, val longitude: Double, val limit: Int?)

/**
 * The legal speed limit of the road the car is on, and the fixed speed cameras, from OpenStreetMap's tags through the
 * public Overpass API. The map is fetched by squares of about 2 km and each square is kept on the head unit for
 * [KEEP_DAYS] days: on the usual trips nothing is fetched again, and the limits and cameras work without Internet or
 * when the free servers are busy (they often take 40 seconds, or do not answer). Only the square's corners are sent,
 * with no identifier. Roads without a limit in the map give no limit, rather than a guess, except the Moroccan
 * default tags (urban 60, rural 100, motorway 120) and a motorway, which is 120.
 */
internal class RoadLimits(private val folder: File? = null) {
    @Volatile private var roads: List<Road> = emptyList()
    @Volatile private var cameras: List<Camera> = emptyList()
    @Volatile private var zones: List<List<Pair<Double, Double>>> = emptyList()
    // The squares read, from the disk or the server, while the car is around them.
    private val tiles = HashMap<String, Pair<List<Road>, List<Camera>>>()
    // Where the roads around the car were last gathered from the squares.
    @Volatile private var gatheredAt: Pair<Double, Double>? = null
    @Volatile private var lastAttempt = 0L
    @Volatile private var failures = 0
    // The road matched last: kept while it still fits, so the car does not jump to a road running beside it.
    @Volatile private var lastRoad: Road? = null

    private fun waitMs() = if (failures == 0) 0L else (RETRY_MS shl (failures - 1).coerceAtMost(4)).coerceAtMost(300_000L)

    /** True when something should be done for [latitude], [longitude] now (read a square, fetch one, or gather). */
    fun needsRefresh(latitude: Double, longitude: Double, now: Long): Boolean {
        val from = gatheredAt ?: return true
        if (TripMeter.distanceMeters(from.first, from.second, latitude, longitude) > GATHER_METERS) return true
        val missing = needed(latitude, longitude).any { it !in tiles }
        return missing && now - lastAttempt >= waitMs()
    }

    /** Blocking: call off the main thread. */
    fun refresh(latitude: Double, longitude: Double, now: Long) {
        val wanted = needed(latitude, longitude)
        wanted.forEach { key -> if (key !in tiles) readTile(key)?.let { tiles[key] = it } }
        // What is already on the head unit is used at once, before waiting for a server that may take minutes.
        gather(latitude, longitude)
        // One square from the server at a time: the one under the car first, then the next one ahead.
        val missing = wanted.firstOrNull { it !in tiles }
        if (missing != null && now - lastAttempt >= waitMs()) {
            lastAttempt = now
            val fetched = fetch(missing)
            if (fetched == null) failures++ else {
                failures = 0
                tiles[missing] = fetched
                writeTile(missing, fetched)
                gather(latitude, longitude)
            }
        }
    }

    /** The roads near the car and the cameras of the squares around it, from the squares read so far. */
    private fun gather(latitude: Double, longitude: Double) {
        // Keep in memory only the squares around the car.
        val around = around(latitude, longitude).toSet()
        tiles.keys.retainAll(around)
        val near = tiles.values
        roads = near.flatMap { it.first }.filter { road ->
            road.points.any { TripMeter.distanceMeters(latitude, longitude, it.first, it.second) < NEAR_METERS }
        }
        cameras = near.flatMap { it.second }
        val all = near.flatMap { it.first }
        zones = cameras.map { cameraZone(all, it) }.filter { it.size >= 2 }
        gatheredAt = latitude to longitude
        android.util.Log.i(TAG, "${roads.size} roads around the car, ${roads.count { it.limit != null }} with a limit, " +
            "${cameras.size} speed cameras, ${tiles.size} squares")
    }

    private fun fetch(key: String): Pair<List<Road>, List<Camera>>? {
        val (south, west) = corner(key)
        val query = query(south - MARGIN, west - MARGIN, south + TILE + MARGIN, west + TILE + MARGIN)
        var result: Result<Pair<List<Road>, List<Camera>>> = Result.failure(IOException("No server"))
        for (server in SERVERS) {
            result = runCatching { download(server, query).let { parse(it) to parseCameras(it) } }
            if (result.isSuccess) break
            android.util.Log.d(TAG, "$server: ${result.exceptionOrNull()?.message}")
        }
        if (result.isFailure) android.util.Log.w(TAG, "Square $key unavailable (attempt ${failures + 1}): ${result.exceptionOrNull()?.message}")
        return result.getOrNull()
    }

    private fun file(key: String) = folder?.let { File(it, "$key.txt") }

    private fun readTile(key: String): Pair<List<Road>, List<Camera>>? {
        val file = file(key)?.takeIf { it.isFile } ?: return null
        if (System.currentTimeMillis() - file.lastModified() > KEEP_DAYS * 86_400_000L) return null
        return runCatching { decodeTile(file.readText()) }.getOrNull()
    }

    private fun writeTile(key: String, tile: Pair<List<Road>, List<Camera>>) {
        val file = file(key) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".part")
            temp.writeText(encodeTile(tile))
            temp.renameTo(file)
        }
    }

    /** The limit of the road the car is on, or null when unknown. */
    fun limitAt(latitude: Double, longitude: Double, bearing: Int?): Road? =
        match(roads, latitude, longitude, bearing, lastRoad).also { lastRoad = it }

    /** The speed cameras found around the car, for the map. */
    fun cameraList(): List<Camera> = cameras

    /** The road [CAMERA_ZONE_METERS] before and after each camera, for the map to colour. */
    fun cameraZones(): List<List<Pair<Double, Double>>> = zones

    /** The nearest speed camera ahead of the car, and its distance in metres. */
    fun cameraAhead(latitude: Double, longitude: Double, bearing: Int?): Pair<Camera, Int>? =
        cameraAhead(cameras, latitude, longitude, bearing)

    private fun query(south: Double, west: Double, north: Double, east: Double) =
        "[out:json][timeout:55][bbox:$south,$west,$north,$east];(way[highway~\"^($ROAD_TYPES)$\"];" +
            "node[highway=speed_camera];node[enforcement~\"^(maxspeed|average_speed)$\"];);out tags geom;"

    private fun download(server: String, query: String): String {
        val url = URI("$server?data=" + URLEncoder.encode(query, "UTF-8")).toURL()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            // A busy public server can take 40 seconds or more to answer.
            connection.connectTimeout = 8_000
            connection.readTimeout = 60_000
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
        const val CAMERA_WARNING_METERS = 500
        /** A square of the map: 0.02° (about 2.2 × 1.9 km in Morocco), fetched with a small margin. */
        const val TILE = 0.02
        const val MARGIN = 0.001
        const val KEEP_DAYS = 45
        /** The squares within this distance of the car are read or fetched. */
        const val AHEAD_METERS = 700.0
        /** The roads this close to the car are matched; gathered again after [GATHER_METERS]. */
        const val NEAR_METERS = 1_500.0
        const val GATHER_METERS = 400.0
        const val RETRY_MS = 20_000L
        const val MATCH_METERS = 30.0
        /** A one-way road whose direction is further than this from the car's heading is the other carriageway. */
        const val WRONG_WAY_DEGREES = 100.0
        const val KEEP_BONUS_METERS = 6.0
        /** How far beside the car's path a camera ahead may stand: a roadside pole on a wide road. */
        const val CAMERA_SIDE_METERS = 40.0
        /** The stretch of road coloured on the map on each side of a camera. */
        const val CAMERA_ZONE_METERS = 100.0
        const val LIMIT_BYTES = 3_000_000
        const val ROAD_TYPES = "motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|" +
            "tertiary|tertiary_link|unclassified|residential|living_street|road"

        fun key(latitude: Double, longitude: Double) = "${floor(latitude / TILE).toInt()}_${floor(longitude / TILE).toInt()}"

        fun corner(key: String): Pair<Double, Double> {
            val (row, column) = key.split('_').map { it.toInt() }
            return row * TILE to column * TILE
        }

        /** The square under the car and its eight neighbours. */
        fun around(latitude: Double, longitude: Double): List<String> {
            val row = floor(latitude / TILE).toInt()
            val column = floor(longitude / TILE).toInt()
            return (-1..1).flatMap { dr -> (-1..1).map { dc -> "${row + dr}_${column + dc}" } }
        }

        /** The square under the car first, then the neighbours whose edge is within [AHEAD_METERS], nearest first. */
        fun needed(latitude: Double, longitude: Double): List<String> {
            val here = key(latitude, longitude)
            return listOf(here) + around(latitude, longitude).filter { it != here }.map { key ->
                val (south, west) = corner(key)
                val lat = latitude.coerceIn(south, south + TILE)
                val lon = longitude.coerceIn(west, west + TILE)
                key to TripMeter.distanceMeters(latitude, longitude, lat, lon)
            }.filter { it.second < AHEAD_METERS }.sortedBy { it.second }.map { it.first }
        }

        /** A square kept on the head unit, one line per road (R) or camera (C). */
        fun encodeTile(tile: Pair<List<Road>, List<Camera>>): String = buildString {
            tile.first.forEach { road ->
                append("R|").append(road.limit ?: "").append('|').append(road.name?.replace(Regex("[|\\n\\r]"), " ") ?: "").append('|')
                road.points.joinTo(this, ";") { "${it.first},${it.second}" }
                append('|').append(road.oneway).append('\n')
            }
            tile.second.forEach { append("C|${it.latitude}|${it.longitude}|${it.limit ?: ""}\n") }
        }

        fun decodeTile(text: String): Pair<List<Road>, List<Camera>> {
            val roads = mutableListOf<Road>()
            val cameras = mutableListOf<Camera>()
            text.lineSequence().forEach { line ->
                val parts = line.split('|')
                when (parts.firstOrNull()) {
                    "R" -> if (parts.size == 4 || parts.size == 5) {
                        val points = parts[3].split(';').mapNotNull { point ->
                            point.split(',').takeIf { it.size == 2 }?.let { (a, b) -> a.toDoubleOrNull()?.let { x -> b.toDoubleOrNull()?.let { x to it } } }
                        }
                        val oneway = parts.getOrNull(4)?.toIntOrNull()?.coerceIn(-1, 1) ?: 0
                        if (points.size >= 2) roads += Road(parts[1].toIntOrNull(), parts[2].ifEmpty { null }, points, oneway)
                    }
                    "C" -> if (parts.size == 4) {
                        val lat = parts[1].toDoubleOrNull()
                        val lon = parts[2].toDoubleOrNull()
                        if (lat != null && lon != null) cameras += Camera(lat, lon, parts[3].toIntOrNull())
                    }
                }
            }
            return roads to cameras
        }

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
                if (tags?.optString("highway") != "speed_camera" &&
                    tags?.optString("enforcement") !in setOf("maxspeed", "average_speed")) return@mapNotNull null
                Camera(node.optDouble("lat"), node.optDouble("lon"), parseMaxspeed(tags.optString("maxspeed").ifEmpty { null }))
                    .takeIf { !it.latitude.isNaN() && !it.longitude.isNaN() }
            }
        }

        /**
         * The closest camera within [CAMERA_WARNING_METERS] that lies ahead: within 35° of the heading (the road may
         * bend), or in front of the car and less than [CAMERA_SIDE_METERS] to the side of its path (a camera on a pole
         * at the roadside, seen at a wide angle when close). Without a heading, within 200 m in any direction.
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
                val side = meters * kotlin.math.sin(Math.toRadians(diff))
                (camera to meters.toInt()).takeIf { diff <= 35 || (diff < 90 && side <= CAMERA_SIDE_METERS) || meters < 40 }
            }.minByOrNull { it.second }

        /**
         * The road [meters] before and after [camera], as points from one end to the other: the road the camera
         * stands beside, followed on through the roads joined to its ends (OpenStreetMap cuts roads at junctions),
         * taking the one that goes on the straightest. Empty when no road passes within [CAMERA_SIDE_METERS].
         */
        fun cameraZone(roads: List<Road>, camera: Camera, meters: Double = CAMERA_ZONE_METERS): List<Pair<Double, Double>> {
            val metersPerLat = 111_195.0
            val metersPerLon = metersPerLat * cos(Math.toRadians(camera.latitude))
            var road: Road? = null
            var index = 0
            var at = camera.latitude to camera.longitude
            var nearest = CAMERA_SIDE_METERS
            roads.forEach { candidate ->
                candidate.points.zipWithNext().forEachIndexed { i, (a, b) ->
                    val ax = (a.second - camera.longitude) * metersPerLon
                    val ay = (a.first - camera.latitude) * metersPerLat
                    val dx = (b.second - a.second) * metersPerLon
                    val dy = (b.first - a.first) * metersPerLat
                    val length = dx * dx + dy * dy
                    val t = if (length == 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
                    val distance = sqrt((ax + t * dx).let { it * it } + (ay + t * dy).let { it * it })
                    if (distance < nearest) {
                        nearest = distance
                        road = candidate
                        index = i
                        at = (a.first + t * (b.first - a.first)) to (a.second + t * (b.second - a.second))
                    }
                }
            }
            val found = road ?: return emptyList()
            val before = walk(roads, found, index, -1, at, meters)
            val after = walk(roads, found, index + 1, 1, at, meters)
            return before.reversed() + listOf(at) + after
        }

        /** The points met going [meters] along [start] from [from], its point [index] first, then [step] by step. */
        private fun walk(roads: List<Road>, start: Road, index: Int, step: Int, from: Pair<Double, Double>,
                         meters: Double): List<Pair<Double, Double>> {
            val out = mutableListOf<Pair<Double, Double>>()
            val seen = mutableSetOf(start)
            var road = start
            var i = index
            var direction = step
            var last = from
            var previous = from
            var left = meters
            repeat(400) {
                if (i !in road.points.indices) {
                    // The end of this road: on along a road joined here (at its end or in its middle) that turns the least.
                    val heading = heading(previous, last)
                    val next = roads.filter { it !in seen }.flatMap { candidate ->
                        candidate.points.indices.filter { joined(candidate.points[it], last) }.flatMap { j ->
                            listOf(1, -1).filter { j + it in candidate.points.indices }.map { Triple(candidate, j + it, it) }
                        }
                    }.minByOrNull { (candidate, j, _) ->
                        var turn = abs(heading(last, candidate.points[j]) - heading) % 360
                        if (turn > 180) turn = 360 - turn
                        turn
                    } ?: return out
                    seen += next.first
                    road = next.first
                    i = next.second
                    direction = next.third
                    return@repeat
                }
                val point = road.points[i]
                val distance = TripMeter.distanceMeters(last.first, last.second, point.first, point.second)
                if (distance >= left) {
                    val f = left / distance
                    out += (last.first + f * (point.first - last.first)) to (last.second + f * (point.second - last.second))
                    return out
                }
                left -= distance
                if (distance > 0) { previous = last; last = point; out += point }
                i += direction
            }
            return out
        }

        private fun joined(a: Pair<Double, Double>, b: Pair<Double, Double>) =
            TripMeter.distanceMeters(a.first, a.second, b.first, b.second) < 2.0

        private fun heading(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val dx = (b.second - a.second) * cos(Math.toRadians(a.first))
            return (Math.toDegrees(atan2(dx, b.first - a.first)) + 360) % 360
        }

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
                    // A motorway with no limit in the map: 120, the Moroccan motorway limit.
                    ?: 120.takeIf { tags.optString("highway") == "motorway" }
                Road(limit, tags.optString("name:fr").ifEmpty { tags.optString("name") }.ifEmpty { null }, points, oneway(tags))
            }
        }

        /** 1, -1 or 0 from OSM's `oneway`; motorways and roundabouts are one-way unless tagged otherwise. */
        fun oneway(tags: JSONObject): Int = when (tags.optString("oneway").lowercase()) {
            "yes", "true", "1" -> 1
            "-1", "reverse" -> -1
            "no", "false", "0", "reversible", "alternating" -> 0
            else -> if (tags.optString("highway") in setOf("motorway", "motorway_link") ||
                tags.optString("junction") in setOf("roundabout", "circular")) 1 else 0
        }

        /**
         * The road nearest to the position within [MATCH_METERS]. When the car's heading is known, a road running
         * across it counts as further away, so a crossing street is not taken for the road being driven; a one-way
         * road going the other way is not taken at all, so on a divided road or beside a parallel one-way street the
         * side the car drives on is chosen. The [previous] road keeps a small advantage while it still fits, so a
         * road a few metres beside it (a service road, a slip road) does not take over at each GPS wobble.
         */
        fun match(roads: List<Road>, latitude: Double, longitude: Double, bearing: Int?, previous: Road? = null): Road? {
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
                    if (bearing != null && road.oneway != 0) {
                        val forward = (Math.toDegrees(atan2(dx, dy)) + if (road.oneway > 0) 360 else 540) % 360
                        var wrongWay = abs(forward - bearing) % 360
                        if (wrongWay > 180) wrongWay = 360 - wrongWay
                        if (wrongWay > WRONG_WAY_DEGREES) return@forEach
                    }
                    val penalty = bearing?.let {
                        val segment = (Math.toDegrees(atan2(dx, dy)) + 360) % 360
                        var diff = abs(segment - it) % 180
                        if (diff > 90) diff = 180 - diff
                        diff / 90 * 20
                    } ?: 0.0
                    val score = distance + penalty - if (previous != null && road == previous) KEEP_BONUS_METERS else 0.0
                    if (score < bestScore) {
                        bestScore = score
                        best = road
                    }
                }
            }
            return best
        }
    }
}
