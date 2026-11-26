package com.incardrive.home

import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Google's traffic along the trip, with Google's answers and the clock played here. */
class RouteTrafficTest {
    private var now = 1_000_000L
    private var answer: (RouteTraffic.Request) -> Pair<Int, String?> = { 200 to null }
    private val asked = ArrayList<RouteTraffic.Request>()
    private var told = 0
    private val prefs = MemoryPrefs()
    private var day = "2026-11-26"

    @Before
    fun setUp() {
        RouteTraffic.reset()
        RouteTraffic.clock = { now }
        RouteTraffic.today = { day }
        RouteTraffic.ask = { asked += it; answer(it) }
        RouteTraffic.background = { it.run() }
        RouteTraffic.onMain = { it.run() }
        RouteTraffic.changed = { told++ }
    }

    @After
    fun tearDown() {
        RouteTraffic.changed = null
        RouteTraffic.reset()
    }

    private fun request(body: String) = RouteTraffic.Request("KEY", "com.incardrive.home", "AB:CD", body)

    private fun follow(left: Int?, lat: Double = 33.585, lon: Double = -7.62) =
        RouteTraffic.follow(prefs, ::request, lat, lon, 0, left)

    // A straight trip north along a meridian: 0.01° of latitude is 1 112 m. Points every 0.01°, 10 km long.
    private val line = (0..9).map { 33.58 + it * 0.01 to -7.62 }
    private fun encode(points: List<Pair<Double, Double>>): String {
        val out = StringBuilder()
        fun put(value: Int) {
            var v = if (value < 0) (value shl 1).inv() else value shl 1
            while (v >= 0x20) { out.append(((0x20 or (v and 0x1f)) + 63).toChar()); v = v shr 5 }
            out.append((v + 63).toChar())
        }
        var lat = 0
        var lng = 0
        for ((a, b) in points) {
            val la = Math.round(a * 1e5).toInt()
            val ln = Math.round(b * 1e5).toInt()
            put(la - lat); put(ln - lng)
            lat = la; lng = ln
        }
        return out.toString()
    }

    private fun route(meters: Int = 10_000, seconds: Int = 1500, free: Int = 720) = """
        {"routes":[{"distanceMeters":$meters,"duration":"${seconds}s","staticDuration":"${free}s",
          "polyline":{"encodedPolyline":"${encode(line)}"},
          "travelAdvisory":{"speedReadingIntervals":[
            {"endPolylinePointIndex":2,"speed":"NORMAL"},
            {"startPolylinePointIndex":2,"endPolylinePointIndex":4,"speed":"SLOW"},
            {"startPolylinePointIndex":4,"endPolylinePointIndex":6,"speed":"TRAFFIC_JAM"},
            {"startPolylinePointIndex":6,"endPolylinePointIndex":9,"speed":"NORMAL"}]}}]}
    """.trimIndent()

    @Test
    fun decodesGooglesOwnExample() {
        // The example of Google's encoded polyline documentation.
        val points = RouteTraffic.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].first, 1e-9)
        assertEquals(-120.2, points[0].second, 1e-9)
        assertEquals(40.7, points[1].first, 1e-9)
        assertEquals(-126.453, points[2].second, 1e-9)
    }

    @Test
    fun readsTheTrafficAlongTheTripInMetres() {
        val trip = RouteTraffic.parse(route())!!
        assertEquals(10_000, trip.meters)
        assertEquals(1500, trip.seconds)
        assertEquals(720, trip.freeSeconds)
        assertEquals(listOf(Flow.FLUID, Flow.SLOW, Flow.JAM, Flow.FLUID), trip.stretches.map { it.flow })
        // Scaled to Google's 10 km: 2 of 9 intervals of the line ≈ 2 222 m.
        assertEquals(0, trip.stretches[0].from)
        assertEquals(2222.0, trip.stretches[1].from.toDouble(), 5.0)
        assertEquals(4444.0, trip.stretches[2].from.toDouble(), 5.0)
        assertEquals(10_000.0, trip.stretches[3].to.toDouble(), 5.0)
        assertNull(RouteTraffic.parse("""{"routes":[]}"""))
        assertNull(RouteTraffic.parse("{}"))
    }

    @Test
    fun theQuestionAsksTheTrafficOnTheLine() {
        val address = JSONObject(RouteTraffic.body("Gare Casa Port, Casablanca", 33.58, -7.62, 370))
        assertEquals("Gare Casa Port, Casablanca", address.getJSONObject("destination").getString("address"))
        assertEquals(10, address.getJSONObject("origin").getJSONObject("location").getInt("heading"))
        assertEquals("TRAFFIC_AWARE", address.getString("routingPreference"))
        assertEquals("TRAFFIC_ON_POLYLINE", address.getJSONArray("extraComputations").getString(0))
        val point = JSONObject(RouteTraffic.body("33.6, -7.5", 33.58, -7.62, null))
        assertEquals(33.6, point.getJSONObject("destination").getJSONObject("location").getJSONObject("latLng").getDouble("latitude"), 1e-9)
        assertFalse(point.getJSONObject("origin").getJSONObject("location").has("heading"))
    }

    @Test
    fun nothingWithoutADestinationPickedInIncarDrive() {
        follow(10_000)
        assertTrue(asked.isEmpty())
        assertNull(RouteTraffic.ahead(10_000))
    }

    @Test
    fun aTripPickedInIncarDriveShowsGooglesTraffic() {
        answer = { 200 to route() }
        RouteTraffic.start(prefs, "Gare Casa Port")
        follow(10_000)
        assertEquals(1, asked.size)
        assertEquals("com.incardrive.home", asked[0].packageName)
        val ahead = RouteTraffic.ahead(10_000)!!
        assertEquals(4, ahead.size)
        assertEquals(13, RouteTraffic.delayMinutes(10_000))
        // Slow from 2.2 km, then jammed up to 6.7 km: one « Bouchon » 2.2 km ahead, 4.4 km long.
        val words = RouteTraffic.label(ahead, RouteTraffic.delayMinutes(10_000))!!
        assertTrue(words, words.startsWith("Bouchon à 2,2 km · sur 4,"))
        assertTrue(words, words.endsWith(" km · +13 min"))
        // Two minutes, not before.
        now += 60_000
        follow(9_000)
        assertEquals(1, asked.size)
        now += 61_000
        follow(8_000)
        assertEquals(2, asked.size)
    }

    @Test
    fun theStretchesMoveWithTheCar() {
        answer = { 200 to route() }
        RouteTraffic.start(prefs, "Gare Casa Port")
        follow(10_000, lat = 33.58)
        // 1 112 m further north: the slow stretch is that much nearer.
        follow(9_000, lat = 33.59)
        val ahead = RouteTraffic.ahead(9_000)!!
        assertEquals(2222.0 - 1112.0, ahead.first { it.flow == Flow.SLOW }.from.toDouble(), 10.0)
        assertEquals(0, ahead.first().from)
    }

    @Test
    fun anotherRoadThanMapsIsNotShown() {
        // Google's trip is 10 km, Maps says 14 km left: not the same road.
        answer = { 200 to route() }
        RouteTraffic.start(prefs, "Gare Casa Port")
        follow(14_000)
        assertNull(RouteTraffic.ahead(14_000))
        assertTrue(RouteTraffic.matches(10_000, 10_900))
        assertFalse(RouteTraffic.matches(10_000, 12_500))
    }

    @Test
    fun aRefusedKeyIsSaidAndWaitsTenMinutes() {
        answer = { 403 to null }
        RouteTraffic.start(prefs, "Gare Casa Port")
        follow(10_000)
        assertTrue(RouteTraffic.refused)
        now += 3 * 60_000
        follow(9_000)
        assertEquals(1, asked.size)
        now += 8 * 60_000
        answer = { 200 to route() }
        follow(8_000)
        assertEquals(2, asked.size)
        assertFalse(RouteTraffic.refused)
    }

    @Test
    fun anAddressGoogleCannotFindIsNotARefusedKey() {
        answer = { 400 to """{"error":{"code":400,"status":"INVALID_ARGUMENT","details":[{"reason":"NOT_FOUND"}]}}""" }
        RouteTraffic.start(prefs, "Nulle part 99999")
        follow(10_000)
        assertFalse(RouteTraffic.refused)
        // Google's real answer to a wrong key (400 with API_KEY_INVALID) is one.
        assertTrue(RouteTraffic.keyRefused(400, """{"error":{"code":400,"message":"API key not valid.","details":[{"reason":"API_KEY_INVALID"}]}}"""))
        assertTrue(RouteTraffic.keyRefused(403, null))
        assertFalse(RouteTraffic.keyRefused(429, null))
    }

    @Test
    fun aRestartDoesNotAskSooner() {
        answer = { 200 to route() }
        RouteTraffic.start(prefs, "Gare Casa Port")
        follow(10_000)
        // The home screen restarts: everything in memory is gone, the head unit's record stays.
        RouteTraffic.reset()
        now += 30_000
        follow(10_000)
        assertEquals(1, asked.size)
        now += 91_000
        follow(10_000)
        assertEquals(2, asked.size)
    }

    @Test
    fun neverMoreThanTheDailyCap() {
        answer = { 200 to route() }
        RouteTraffic.start(prefs, "Gare Casa Port")
        repeat(RouteTraffic.DAILY_MAX + 20) { now += 121_000; follow(10_000) }
        assertEquals(RouteTraffic.DAILY_MAX, asked.size)
        // A new day: again.
        day = "2026-11-27"
        now += 121_000
        follow(10_000)
        assertEquals(RouteTraffic.DAILY_MAX + 1, asked.size)
    }

    @Test
    fun theTripEndsWhenMapsStopsGuidingOrOnArrival() {
        answer = { 200 to route() }
        RouteTraffic.start(prefs, "Gare Casa Port")
        follow(10_000)
        // Maps stops guiding for over three minutes: the trip is over.
        now += 60_000
        follow(null)
        now += 3 * 60_000 + 1
        follow(null)
        assertNull(RouteTraffic.destination(prefs))
        // Arrived: over at once.
        RouteTraffic.start(prefs, "Gare Casa Port")
        follow(100)
        assertNull(RouteTraffic.destination(prefs))
        // Guidance never started: given up after 20 minutes.
        RouteTraffic.start(prefs, "Gare Casa Port")
        now += 21 * 60_000
        follow(null)
        assertNull(RouteTraffic.destination(prefs))
    }

    @Test
    fun anOldAnswerIsNotShown() {
        answer = { 200 to route() }
        RouteTraffic.start(prefs, "Gare Casa Port")
        follow(10_000)
        answer = { 0 to null }
        now += 7 * 60_000
        assertNull(RouteTraffic.ahead(10_000))
    }

    @Test
    fun theWordsForTheRouteCard() {
        val free = listOf(FlowStretch(0, 5000, Flow.FLUID))
        assertNull(RouteTraffic.label(free, 1))
        assertEquals("Trajet fluide devant · +4 min", RouteTraffic.label(free, 4))
        val jamHere = listOf(FlowStretch(0, 800, Flow.JAM), FlowStretch(800, 5000, Flow.FLUID))
        assertEquals("Bouchon · sur 800 m", RouteTraffic.label(jamHere, 0))
        val slowThenJam = listOf(FlowStretch(0, 1200, Flow.FLUID), FlowStretch(1200, 2000, Flow.SLOW), FlowStretch(2000, 3100, Flow.JAM))
        assertEquals("Bouchon à 1,2 km · sur 1,9 km", RouteTraffic.label(slowThenJam, null))
    }

    /** SharedPreferences in memory, enough for these tests. */
    private class MemoryPrefs : SharedPreferences {
        val values = HashMap<String, Any?>()
        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
        override fun contains(key: String) = key in values
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = apply { values[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = this
            override fun putInt(key: String, value: Int) = apply { values[key] = value }
            override fun putLong(key: String, value: Long) = apply { values[key] = value }
            override fun putFloat(key: String, value: Float) = apply { values[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
            override fun remove(key: String) = apply { values.remove(key) }
            override fun clear() = apply { values.clear() }
            override fun commit() = true
            override fun apply() {}
        }
    }
}
