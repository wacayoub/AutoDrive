package com.incardrive.home

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.Executors

/** A stretch of road from [from] to [to] metres ahead of the car, in its traffic colour. */
internal data class FlowStretch(val from: Int, val to: Int, val flow: Flow)

/**
 * Google's own traffic along the whole trip, with the driver's Google key (Réglages › Trafic Google): when a
 * destination is handed to Google Maps from Incar Drive, the same trip is asked of Google's Routes API every two
 * minutes, with its traffic stretch by stretch (normal, slow, jam), the same data Google Maps colours its route line
 * with. Shown on the « Route » card and its A to B bar only: Google's terms forbid showing it on another map.
 *
 * Google may choose a slightly different road than Maps did: the trip's length is checked against the distance Maps
 * gives, and a trip that does not match is not shown.
 */
internal object RouteTraffic {
    const val KEY = "google_routes_key"
    private const val DESTINATION = "route_destination"
    private const val DESTINATION_AT = "route_destination_at"
    private const val COUNT_DAY = "route_count_day"
    private const val COUNT = "route_count"
    private const val ASKED_AT = "route_asked_at"
    private const val BLOCKED_UNTIL = "route_blocked_until"
    private const val EVERY_MS = 120_000L
    /** Never more a day, under Google's 5 000 free a month whatever the driving. */
    const val DAILY_MAX = 150
    private const val FRESH_MS = 6 * 60_000L

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "route-traffic").apply { isDaemon = true } }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    // Replaced by the unit tests.
    internal var clock: () -> Long = System::currentTimeMillis
    internal var ask: (Request) -> Pair<Int, String?> = ::post
    internal var background: (Runnable) -> Unit = { worker.execute(it) }
    internal var onMain: (Runnable) -> Unit = { main.post(it) }
    internal var today: () -> String = { LocalDate.now().toString() }

    /** One question to Google: the key, the app's own name and signature for a key kept to Incar Drive, the body. */
    data class Request(val key: String, val packageName: String, val certificate: String?, val body: String)

    /** Google's answer: the trip's length and time (with and without traffic), and its stretches from the start. */
    data class Route(val meters: Int, val seconds: Int, val freeSeconds: Int, val stretches: List<FlowStretch>)

    private var route: Route? = null
    private var routeAt = 0L
    /** Where Maps said the destination was (metres left) when Google answered, to check both trips are the same. */
    private var leftAtAnswer: Int? = null
    /** Metres the GPS has driven since Google answered, to move the stretches as the car goes. */
    private var driven = 0.0
    private var lastFix: Pair<Double, Double>? = null
    @Volatile private var asking = false
    private var askedAt = 0L
    private var blockedUntil = 0L
    private var navSeen = false
    private var navGoneAt = 0L

    /** True after Google refused the key (wrong key, API not enabled, or the app's signature not allowed). */
    var refused = false
        private set

    /** Told on the main thread when what is shown changes. */
    var changed: (() -> Unit)? = null

    /** Remembers [destination] (an address, a place or « latitude,longitude ») as the trip now guided by Maps. */
    fun start(prefs: SharedPreferences, destination: String) {
        prefs.edit().putString(DESTINATION, destination.trim()).putLong(DESTINATION_AT, clock()).apply()
        route = null
        leftAtAnswer = null
        askedAt = 0L
        navSeen = false
        navGoneAt = 0L
        changed?.invoke()
    }

    fun stop(prefs: SharedPreferences) {
        prefs.edit().remove(DESTINATION).remove(DESTINATION_AT).apply()
        if (route != null) { route = null; changed?.invoke() }
    }

    fun destination(prefs: SharedPreferences): String? = prefs.getString(DESTINATION, null)

    /**
     * With each GPS fix while the home screen is shown: asks Google every two minutes while a destination picked in
     * Incar Drive is guided. [mapsLeft] is the distance left Maps gives (null when it guides nothing); the trip ends
     * three minutes after Maps stops guiding, or after 20 minutes if guidance never started.
     */
    fun follow(prefs: SharedPreferences, request: (String) -> Request?, latitude: Double, longitude: Double, heading: Int?,
               mapsLeft: Int?) {
        val destination = destination(prefs) ?: return
        val now = clock()
        lastFix?.let { driven += TripMeter.distanceMeters(it.first, it.second, latitude, longitude) }
        lastFix = latitude to longitude
        if (mapsLeft != null) { navSeen = true; navGoneAt = 0L }
        else if (navSeen) {
            if (navGoneAt == 0L) navGoneAt = now
            if (now - navGoneAt > 3 * 60_000L) { stop(prefs); return }
        } else if (now - prefs.getLong(DESTINATION_AT, now) > 20 * 60_000L) { stop(prefs); return }
        if (mapsLeft != null && mapsLeft < 150) { stop(prefs); return }
        // Kept on the head unit too: a restart of the home screen does not ask again sooner.
        val lastAsk = maxOf(askedAt, prefs.getLong(ASKED_AT, 0L).takeIf { it <= now } ?: 0L)
        val blocked = maxOf(blockedUntil, prefs.getLong(BLOCKED_UNTIL, 0L).takeIf { it - now <= 10 * 60_000L } ?: 0L)
        if (asking || now - lastAsk < EVERY_MS || now < blocked) return
        val day = today()
        val count = if (prefs.getString(COUNT_DAY, null) == day) prefs.getInt(COUNT, 0) else 0
        if (count >= DAILY_MAX) return
        val body = body(destination, latitude, longitude, heading)
        val question = request(body) ?: return
        asking = true
        askedAt = now
        // Google's trip starts where the car is now: what it drives while Google answers still counts.
        val drivenAtAsk = driven
        prefs.edit().putString(COUNT_DAY, day).putInt(COUNT, count + 1).putLong(ASKED_AT, now).apply()
        background(Runnable {
            val (status, json) = runCatching { ask(question) }.getOrDefault(0 to null)
            val answer = if (status == 200 && json != null) runCatching { parse(json) }.getOrNull() else null
            onMain(Runnable {
                asking = false
                if (destination(prefs) != destination) return@Runnable
                val wasRefused = refused
                when {
                    // A wrong key, the Routes API not enabled, or the app not allowed by the key's restriction.
                    keyRefused(status, json) -> { refused = true; blockedUntil = clock() + 10 * 60_000L }
                    // A destination Google cannot find, a spent allowance, or Google down: wait before asking again.
                    status == 400 || status == 429 || status >= 500 -> blockedUntil = clock() + 10 * 60_000L
                    status == 200 -> refused = false
                }
                if (blockedUntil > clock()) prefs.edit().putLong(BLOCKED_UNTIL, blockedUntil).apply()
                if (answer != null) {
                    route = answer
                    routeAt = clock()
                    leftAtAnswer = mapsLeft
                    driven = (driven - drivenAtAsk).coerceAtLeast(0.0)
                }
                if (answer != null || refused != wasRefused) changed?.invoke()
            })
        })
    }

    /**
     * The stretches ahead of the car now, nearest first, or null when there is no fresh Google trip matching Maps':
     * the stretches are moved by how far the car has driven since Google answered.
     */
    fun ahead(mapsLeft: Int?): List<FlowStretch>? {
        val trip = shownRoute(mapsLeft) ?: return null
        val gone = driven.toInt()
        return trip.stretches.mapNotNull {
            val from = it.from - gone
            val to = it.to - gone
            if (to <= 0) null else FlowStretch(from.coerceAtLeast(0), to, it.flow)
        }
    }

    /** The trip's delay against an empty road, in minutes, when Google's trip is shown. */
    fun delayMinutes(mapsLeft: Int?): Int? = shownRoute(mapsLeft)?.let { ((it.seconds - it.freeSeconds) / 60).coerceAtLeast(0) }

    private fun shownRoute(mapsLeft: Int?): Route? {
        val trip = route ?: return null
        if (clock() - routeAt > FRESH_MS) return null
        val before = leftAtAnswer
        // Google's trip length against Maps' at the same moment: another road when they differ much.
        if (before != null && !matches(trip.meters, before)) return null
        if (mapsLeft == null && before != null) return null
        return trip
    }

    /** Google's « no » to the key itself: 401, 403, or 400 with the reason « API_KEY_INVALID ». */
    fun keyRefused(status: Int, json: String?): Boolean =
        status == 401 || status == 403 || (status == 400 && json?.contains("API_KEY_INVALID") == true)

    /** Whether Google's trip and Maps' are the same road, judged by their length. */
    fun matches(googleMeters: Int, mapsMeters: Int): Boolean =
        kotlin.math.abs(googleMeters - mapsMeters) <= maxOf(800, (mapsMeters * .15).toInt())

    /**
     * In words for the « Route » card: the nearest slow or jammed stretch, how long it is, and the delay of the whole
     * trip: « Bouchon à 2,3 km · sur 1,1 km · +13 min ». Null when the road ahead is free and on time.
     */
    fun label(stretches: List<FlowStretch>?, delayMinutes: Int?): String? {
        stretches ?: return null
        val delay = delayMinutes?.takeIf { it >= 2 }?.let { "+$it min" }
        val bad = stretches.firstOrNull { it.flow != Flow.FLUID }
            ?: return delay?.let { "Trajet fluide devant · $it" }
        // The jam goes on over the next stretches of the same kind or worse.
        var end = bad.to
        for (next in stretches.dropWhile { it !== bad }.drop(1)) {
            if (next.flow == Flow.FLUID || next.from > end + 50) break
            end = next.to
        }
        val word = if (stretches.any { it.flow == Flow.JAM && it.from >= bad.from && it.from < end }) "Bouchon" else "Ralenti"
        val where = if (bad.from < 100) "" else " à " + distance(bad.from)
        return listOfNotNull(word + where, "sur " + distance(end - bad.from), delay).joinToString(" · ")
    }

    private fun distance(meters: Int): String =
        if (meters < 1000) "${(meters / 50) * 50} m"
        else String.format(Locale.FRANCE, "%.1f", meters / 1000.0).removeSuffix(",0") + " km"

    /** The question: from the car (with its heading) to the destination, by car, with the traffic along the line. */
    fun body(destination: String, latitude: Double, longitude: Double, heading: Int?): String {
        val origin = JSONObject().put("location", JSONObject()
            .put("latLng", JSONObject().put("latitude", latitude).put("longitude", longitude))
            .apply { if (heading != null) put("heading", ((heading % 360) + 360) % 360) })
        val coordinates = COORDINATES.matchEntire(destination.trim())
        val target = if (coordinates != null) JSONObject().put("location", JSONObject().put("latLng", JSONObject()
            .put("latitude", coordinates.groupValues[1].toDouble()).put("longitude", coordinates.groupValues[2].toDouble())))
            else JSONObject().put("address", destination.trim())
        return JSONObject()
            .put("origin", origin)
            .put("destination", target)
            .put("travelMode", "DRIVE")
            .put("routingPreference", "TRAFFIC_AWARE")
            .put("extraComputations", JSONArray().put("TRAFFIC_ON_POLYLINE"))
            .put("languageCode", "fr")
            .put("units", "METRIC")
            .toString()
    }

    /** Google's answer read: its first trip, with each speed interval turned into metres along the line. */
    fun parse(json: String): Route? {
        val trip = JSONObject(json).optJSONArray("routes")?.optJSONObject(0) ?: return null
        val meters = trip.optInt("distanceMeters", -1).takeIf { it > 0 } ?: return null
        fun seconds(name: String) = trip.optString(name).removeSuffix("s").toDoubleOrNull()?.toInt()
        val duration = seconds("duration") ?: return null
        val free = seconds("staticDuration") ?: duration
        val points = decode(trip.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty())
        val along = DoubleArray(points.size)
        for (i in 1 until points.size) {
            along[i] = along[i - 1] + TripMeter.distanceMeters(points[i - 1].first, points[i - 1].second, points[i].first, points[i].second)
        }
        // The line's own length can differ a little from Google's distance: scaled to it.
        val scale = if (points.size > 1 && along.last() > 0) meters / along.last() else 1.0
        val intervals = trip.optJSONObject("travelAdvisory")?.optJSONArray("speedReadingIntervals")
        val stretches = ArrayList<FlowStretch>()
        if (intervals != null && points.size > 1) for (i in 0 until intervals.length()) {
            val interval = intervals.optJSONObject(i) ?: continue
            val start = interval.optInt("startPolylinePointIndex", 0).coerceIn(0, points.size - 1)
            val end = interval.optInt("endPolylinePointIndex", points.size - 1).coerceIn(start, points.size - 1)
            val flow = when (interval.optString("speed")) {
                "NORMAL" -> Flow.FLUID
                "SLOW" -> Flow.SLOW
                "TRAFFIC_JAM" -> Flow.JAM
                else -> continue
            }
            val from = (along[start] * scale).toInt()
            val to = (along[end] * scale).toInt()
            if (to > from) stretches += FlowStretch(from, to, flow)
        }
        return Route(meters, duration, free, stretches.sortedBy { it.from })
    }

    /** Google's encoded polyline (its documented format) into latitude, longitude points. */
    fun decode(encoded: String): List<Pair<Double, Double>> {
        val points = ArrayList<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0
        fun next(): Int? {
            var result = 0
            var shift = 0
            while (index < encoded.length) {
                val b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
                if (b < 0x20) return if (result and 1 != 0) (result shr 1).inv() else result shr 1
            }
            return null
        }
        while (index < encoded.length) {
            lat += next() ?: break
            lng += next() ?: break
            points += lat / 1e5 to lng / 1e5
        }
        return points
    }

    /** The request for Google, with the app's name and signing certificate so a key kept to Incar Drive accepts it. */
    fun request(context: Context, key: String, body: String) = Request(key.trim(), context.packageName, certificate(context), body)

    /** SHA-1 of the app's signing certificate, as Google's Android key restriction asks it (« AB:CD:… »). */
    fun certificate(context: Context): String? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signer = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
        MessageDigest.getInstance("SHA-1").digest(signer.toByteArray()).joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    private fun post(request: Request): Pair<Int, String?> {
        val connection = URL("https://routes.googleapis.com/directions/v2:computeRoutes").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Goog-Api-Key", request.key)
            connection.setRequestProperty("X-Goog-FieldMask", FIELDS)
            connection.setRequestProperty("X-Android-Package", request.packageName)
            request.certificate?.let { connection.setRequestProperty("X-Android-Cert", it.replace(":", "")) }
            connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status == 200) connection.inputStream else connection.errorStream
            return status to stream?.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    /** Back to the start, for the unit tests. */
    internal fun reset() {
        route = null; routeAt = 0L; leftAtAnswer = null; driven = 0.0; lastFix = null; asking = false; askedAt = 0L; blockedUntil = 0L
        navSeen = false; navGoneAt = 0L; refused = false
    }

    private const val FIELDS = "routes.distanceMeters,routes.duration,routes.staticDuration,routes.polyline.encodedPolyline," +
        "routes.travelAdvisory.speedReadingIntervals"
    private val COORDINATES = Regex("""(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""")
}
