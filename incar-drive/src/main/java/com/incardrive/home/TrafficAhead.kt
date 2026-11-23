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

    /** The worst traffic of the two points, null when unknown or older than three minutes. */
    var flow: Flow? = null
        get() = field?.takeIf { System.currentTimeMillis() - flowAt < FRESH_MS }
        private set

    /** Told on the main thread when [flow] changes. */
    var changed: (() -> Unit)? = null

    fun follow(key: String?, here: Pair<Double, Double>, ahead: Pair<Double, Double>?) {
        val now = System.currentTimeMillis()
        if (key.isNullOrBlank() || asking || now - askedAt < EVERY_MS || now < blockedUntil) return
        asking = true
        askedAt = now
        worker.execute {
            var status = 0
            val flows = listOfNotNull(here, ahead).map { point ->
                runCatching { ask(key, point).also { status = it.first }.second }.getOrNull()
            }
            main.post {
                asking = false
                if (status == 401 || status == 403 || status == 429) blockedUntil = System.currentTimeMillis() + 10 * 60_000
                val worst = worst(flows)
                if (worst != null) flowAt = System.currentTimeMillis()
                if (worst != null || System.currentTimeMillis() - flowAt >= FRESH_MS) {
                    val before = flow
                    flow = worst
                    if (before != flow) changed?.invoke()
                }
            }
        }
    }

    private fun ask(key: String, point: Pair<Double, Double>): Pair<Int, Flow?> {
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
    fun parse(json: String): Flow? {
        val data = JSONObject(json).optJSONObject("flowSegmentData") ?: return null
        if (data.optBoolean("roadClosure")) return Flow.JAM
        val current = data.optDouble("currentSpeed")
        val free = data.optDouble("freeFlowSpeed")
        if (current.isNaN() || free.isNaN() || free <= 0) return null
        val ratio = current / free
        return when {
            ratio < .4 -> Flow.JAM
            ratio < .75 -> Flow.SLOW
            else -> Flow.FLUID
        }
    }

    fun worst(flows: List<Flow?>): Flow? = flows.filterNotNull().maxByOrNull { it.ordinal }
}
