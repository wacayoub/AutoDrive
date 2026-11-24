package com.incardrive.home

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The traffic on the road just ahead of the car, from TomTom's flow service with the driver's own free key (Réglages ›
 * Trafic): once a minute, the speed driven now on the road under the car and [AHEAD_METERS] further along it,
 * against the speed driven there when the road is free. Google Maps keeps the red of its route line to itself, so this
 * is what colours the « Route » card. Only the two points are sent, with the key; nothing without a key.
 */
internal object TrafficAhead {
    const val AHEAD_METERS = 500.0
    private const val EVERY_MS = 60_000L
    private const val FRESH_MS = 180_000L

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "traffic-ahead").apply { isDaemon = true } }
    private val main by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var asking = false
    private var askedAt = 0L
    private var blockedUntil = 0L
    private var flowAt = 0L

    /** What TomTom measured at one point: its colour, the speed driven there now and when free, how far ahead. */
    data class Reading(val flow: Flow, val speed: Int?, val freeSpeed: Int?, val closed: Boolean = false, val meters: Int = 0)

    /** The worst traffic of the two points, null when unknown or older than three minutes. */
    var reading: Reading? = null
        get() = field?.takeIf { System.currentTimeMillis() - flowAt < FRESH_MS }
        private set

    val flow: Flow? get() = reading?.flow

    /** Told on the main thread when [flow] changes. */
    var changed: (() -> Unit)? = null

    fun follow(key: String?, here: Pair<Double, Double>, ahead: Pair<Double, Double>?) {
        val now = System.currentTimeMillis()
        if (key.isNullOrBlank() || asking || now - askedAt < EVERY_MS || now < blockedUntil) return
        asking = true
        askedAt = now
        worker.execute {
            var status = 0
            val readings = listOfNotNull(here to 0, ahead?.let { it to AHEAD_METERS.toInt() }).map { (point, meters) ->
                runCatching { ask(key, point).also { status = it.first }.second?.copy(meters = meters) }.getOrNull()
            }
            main.post {
                asking = false
                if (status == 401 || status == 403 || status == 429) blockedUntil = System.currentTimeMillis() + 10 * 60_000
                val worst = worstReading(readings)
                if (worst != null) flowAt = System.currentTimeMillis()
                if (worst != null || System.currentTimeMillis() - flowAt >= FRESH_MS) {
                    val before = reading
                    reading = worst
                    if (before != reading) changed?.invoke()
                }
            }
        }
    }

    private fun ask(key: String, point: Pair<Double, Double>): Pair<Int, Reading?> {
        val at = String.format(Locale.ROOT, "%.5f,%.5f", point.first, point.second)
        val url = URL("https://api.tomtom.com/traffic/services/4/flowSegmentData/relative0/10/json?point=$at&unit=KMPH&key=$key")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            val status = connection.responseCode
            if (status != 200) return status to null
            return status to parse(connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        } finally {
            connection.disconnect()
        }
    }

    /** Red when closed or under 40 % of the free speed, orange under 75 %, else green. */
    fun parse(json: String): Reading? {
        val data = JSONObject(json).optJSONObject("flowSegmentData") ?: return null
        val current = data.optDouble("currentSpeed").takeIf { !it.isNaN() }
        val free = data.optDouble("freeFlowSpeed").takeIf { !it.isNaN() && it > 0 }
        if (data.optBoolean("roadClosure")) return Reading(Flow.JAM, null, free?.toInt(), closed = true)
        if (current == null || free == null) return null
        val ratio = current / free
        val flow = when {
            ratio < .4 -> Flow.JAM
            ratio < .75 -> Flow.SLOW
            else -> Flow.FLUID
        }
        return Reading(flow, current.toInt(), free.toInt())
    }

    /** The worst of the points; between two alike, the nearer one. */
    fun worstReading(readings: List<Reading?>): Reading? =
        readings.filterNotNull().sortedBy { it.meters }.maxByOrNull { it.flow.ordinal }

    /**
     * In words for the « Route » card: « Bouchon · 18 km/h », « Ralenti à 500 m · 42 km/h », « Route fermée à 500 m »;
     * nothing while the road is free.
     */
    fun label(reading: Reading?): String? {
        if (reading == null || reading.flow == Flow.FLUID) return null
        val where = if (reading.meters > 0) " à ${reading.meters} m" else ""
        if (reading.closed) return "Route fermée$where"
        val word = if (reading.flow == Flow.JAM) "Bouchon" else "Ralenti"
        return listOfNotNull(word + where, reading.speed?.let { "$it km/h" }).joinToString(" · ")
    }
}
