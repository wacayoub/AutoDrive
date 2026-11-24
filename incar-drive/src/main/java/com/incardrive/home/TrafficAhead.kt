package com.incardrive.home

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The traffic on the road just ahead of the car, from TomTom's flow service with the driver's own free key (Réglages ›
 * Trafic): once a minute, the speed driven now on the road under the car and [AHEAD_METERS] further along it,
 * against the speed driven there when the road is free. Google Maps keeps the red of its route line to itself, so this
 * is what colours the « Route » card. Only the two points are sent, with the key; nothing without a key.
 */
internal object TrafficAhead {
    /** How far along the road ahead the traffic is asked, besides under the car. */
    val AHEAD_METERS = listOf(500, 1500)
    private const val EVERY_MS = 60_000L
    private const val FRESH_MS = 180_000L

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "traffic-ahead").apply { isDaemon = true } }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    // Replaced by the unit tests, which play TomTom's answers and the clock.
    internal var clock: () -> Long = System::currentTimeMillis
    internal var ask: (String, Pair<Double, Double>) -> Pair<Int, Reading?> = ::request
    internal var background: (Runnable) -> Unit = { worker.execute(it) }
    internal var onMain: (Runnable) -> Unit = { main.post(it) }

    @Volatile private var asking = false
    private var askedAt = 0L
    private var blockedUntil = 0L
    private var flowAt = 0L
    private var lastKey: String? = null

    /** What TomTom measured at one point: its colour, the speed driven there now and when free, how far ahead. */
    data class Reading(val flow: Flow, val speed: Int?, val freeSpeed: Int?, val closed: Boolean = false, val meters: Int = 0)

    /** The worst traffic of the two points, null when unknown or older than three minutes. */
    val reading: Reading? get() = current?.takeIf { clock() - flowAt < FRESH_MS }
    private var current: Reading? = null

    val flow: Flow? get() = reading?.flow

    /** True after TomTom refused the key (401 or 403), until a new key is typed or it is accepted again. */
    var refused = false
        private set

    /** Told on the main thread when [reading] changes. */
    var changed: (() -> Unit)? = null

    /** Called with each GPS fix: asks TomTom at most once a minute, and clears what is gone or too old. */
    fun follow(key: String?, here: Pair<Double, Double>, ahead: List<Pair<Pair<Double, Double>, Int>>) {
        val now = clock()
        val typed = key?.trim()?.takeIf { it.isNotEmpty() }
        // A new key (or none): a refusal or a spent allowance of the old one no longer holds.
        if (typed != lastKey) {
            lastKey = typed
            blockedUntil = 0L
            askedAt = 0L
            refused = false
        }
        // Too old, or the key removed: nothing shown rather than a stale jam.
        if (typed == null || now - flowAt >= FRESH_MS) clear()
        if (typed == null || asking || now - askedAt < EVERY_MS || now < blockedUntil) return
        asking = true
        askedAt = now
        background(Runnable {
            var status = 0
            val readings = (listOf(here to 0) + ahead).map { (point, meters) ->
                runCatching { ask(typed, point).also { status = it.first }.second?.copy(meters = meters) }.getOrNull()
            }
            onMain(Runnable {
                asking = false
                if (typed != lastKey) return@Runnable
                val wasRefused = refused
                when (status) {
                    401, 403 -> { refused = true; blockedUntil = clock() + 10 * 60_000 }
                    429 -> blockedUntil = clock() + 10 * 60_000
                    200 -> refused = false
                }
                val before = current
                val worst = worstReading(readings)
                if (worst != null) {
                    flowAt = clock()
                    current = worst
                } else if (clock() - flowAt >= FRESH_MS) current = null
                if (current != before || refused != wasRefused) changed?.invoke()
            })
        })
    }

    private fun clear() {
        if (current == null) return
        current = null
        changed?.invoke()
    }

    /** Back to the start, for the unit tests. */
    internal fun reset() {
        asking = false; askedAt = 0L; blockedUntil = 0L; flowAt = 0L; lastKey = null; refused = false; current = null
    }

    private fun request(key: String, point: Pair<Double, Double>): Pair<Int, Reading?> {
        val at = String.format(Locale.ROOT, "%.5f,%.5f", point.first, point.second)
        val url = URL("https://api.tomtom.com/traffic/services/4/flowSegmentData/relative0/10/json?point=$at&unit=KMPH&key=" +
            URLEncoder.encode(key, "UTF-8"))
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

    fun worst(flows: List<Flow?>): Flow? = flows.filterNotNull().maxByOrNull { it.ordinal }

    /** The worst of the points; between two alike, the nearer one. */
    fun worstReading(readings: List<Reading?>): Reading? =
        readings.filterNotNull().sortedBy { it.meters }.maxByOrNull { it.flow.ordinal }

    /**
     * In words for the « Route » card: « Bouchon · 18 km/h », « Ralenti à 500 m · 42 km/h », « Route fermée à 500 m »;
     * nothing while the road is free; « Clé TomTom refusée » when TomTom said no and nothing is known.
     */
    fun label(reading: Reading?, refused: Boolean = false): String? {
        if (reading == null) return "Clé TomTom refusée".takeIf { refused }
        if (reading.flow == Flow.FLUID) return null
        val where = when {
            reading.meters >= 1000 -> " à " + String.format(Locale.FRANCE, "%.1f", reading.meters / 1000.0).removeSuffix(",0") + " km"
            reading.meters > 0 -> " à ${reading.meters} m"
            else -> ""
        }
        if (reading.closed) return "Route fermée$where"
        val word = if (reading.flow == Flow.JAM) "Bouchon" else "Ralenti"
        return listOfNotNull(word + where, reading.speed?.let { "$it km/h" }).joinToString(" · ")
    }
}
