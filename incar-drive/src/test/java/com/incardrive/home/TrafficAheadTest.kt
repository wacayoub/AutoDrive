package com.incardrive.home

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Every case of TomTom's traffic on the « Route » card, with TomTom's answers and the clock played here. */
class TrafficAheadTest {
    private var now = 1_000_000L
    private val asked = ArrayList<Pair<String, Pair<Double, Double>>>()
    private var answer: (Pair<Double, Double>) -> Pair<Int, TrafficAhead.Reading?> = { 200 to null }
    private var told = 0
    private val here = 33.585 to -7.62
    private val ahead = 33.5895 to -7.62
    private val far = 33.5985 to -7.62

    private fun json(current: Int, free: Int, closed: Boolean = false) =
        TrafficAhead.parse("""{"flowSegmentData":{"currentSpeed":$current,"freeFlowSpeed":$free,"roadClosure":$closed}}""")

    @Before
    fun setUp() {
        TrafficAhead.reset()
        TrafficAhead.clock = { now }
        TrafficAhead.ask = { key, point -> asked += key to point; answer(point) }
        TrafficAhead.background = { it.run() }
        TrafficAhead.onMain = { it.run() }
        TrafficAhead.changed = { told++ }
    }

    @After
    fun tearDown() {
        TrafficAhead.changed = null
        TrafficAhead.reset()
    }

    private fun drive(key: String? = "KEY", ahead: Pair<Double, Double>? = this.ahead) =
        TrafficAhead.follow(key, here, listOfNotNull(ahead?.let { it to 500 }))
    private fun label() = TrafficAhead.label(TrafficAhead.reading, TrafficAhead.refused)

    @Test
    fun withoutAKeyNothingIsAskedNorShown() {
        drive(null)
        drive("   ")
        assertTrue(asked.isEmpty())
        assertNull(label())
    }

    @Test
    fun aFreeRoadAskedEveryMinuteAndAHalfShowsNothing() {
        answer = { 200 to json(58, 60) }
        drive()
        assertEquals(2, asked.size)
        assertEquals(Flow.FLUID, TrafficAhead.flow)
        assertNull(label())
        // Every GPS fix in the next minute and a half: no new question.
        now += 60_000
        drive()
        assertEquals(2, asked.size)
        now += 31_000
        drive()
        assertEquals(4, asked.size)
    }

    @Test
    fun aJamAheadIsTold() {
        answer = { if (it == ahead) 200 to json(12, 60) else 200 to json(55, 60) }
        drive()
        assertEquals(Flow.JAM, TrafficAhead.flow)
        assertEquals("Bouchon à 500 m · 12 km/h", label())
        assertEquals(1, told)
    }

    @Test
    fun slowUnderTheCar() {
        answer = { if (it == here) 200 to json(30, 60) else 200 to json(58, 60) }
        drive()
        assertEquals("Ralenti · 30 km/h", label())
    }

    @Test
    fun aClosedRoadAhead() {
        answer = { if (it == ahead) 200 to json(0, 60, closed = true) else 200 to json(50, 60) }
        drive()
        assertEquals("Route fermée à 500 m", label())
    }

    @Test
    fun stoppedWithoutAHeadingOnlyTheRoadUnderTheCarIsAsked() {
        answer = { 200 to json(8, 50) }
        drive(ahead = null)
        assertEquals(listOf(here), asked.map { it.second })
        assertEquals("Bouchon · 8 km/h", label())
    }

    @Test
    fun aRefusedKeyIsSaidAndNotAskedAgainForTenMinutes() {
        answer = { 401 to null }
        drive()
        assertTrue(TrafficAhead.refused)
        assertEquals("Clé TomTom refusée", label())
        assertEquals(1, told)
        now += 5 * 60_000
        drive()
        assertEquals(2, asked.size)
        now += 6 * 60_000
        answer = { 200 to json(58, 60) }
        drive()
        assertEquals(4, asked.size)
        assertFalse(TrafficAhead.refused)
        assertNull(label())
    }

    @Test
    fun aNewKeyIsTriedAtOnce() {
        answer = { 403 to null }
        drive("BAD")
        assertTrue(TrafficAhead.refused)
        answer = { 200 to json(20, 60) }
        now += 1_000
        drive("GOOD")
        assertEquals("GOOD", asked.last().first)
        assertFalse(TrafficAhead.refused)
        assertEquals("Bouchon · 20 km/h", label())
    }

    @Test
    fun aSpentAllowanceWaitsTenMinutesWithoutSayingTheKeyIsWrong() {
        answer = { 429 to null }
        drive()
        assertFalse(TrafficAhead.refused)
        assertNull(label())
        now += 9 * 60_000
        drive()
        assertEquals(2, asked.size)
        now += 2 * 60_000
        drive()
        assertEquals(4, asked.size)
    }

    @Test
    fun withoutNetworkTheLastTrafficStaysThreeMinutesThenGoes() {
        answer = { 200 to json(12, 60) }
        drive()
        assertEquals(Flow.JAM, TrafficAhead.flow)
        answer = { throw java.io.IOException("no network") }
        now += 61_000
        drive()
        assertEquals(Flow.JAM, TrafficAhead.flow)
        now += 61_000
        drive()
        assertEquals(Flow.JAM, TrafficAhead.flow)
        now += 61_000
        drive()
        assertNull(TrafficAhead.flow)
        assertNull(label())
        assertEquals(2, told)
    }

    @Test
    fun anOldJamIsClearedEvenWhenNotAsked() {
        answer = { 200 to json(12, 60) }
        drive()
        // TomTom refuses for a while (allowance spent): nothing new, and after three minutes the jam is gone.
        answer = { 429 to null }
        now += 61_000
        drive()
        now += 3 * 60_000
        drive()
        assertNull(TrafficAhead.flow)
        assertEquals(2, told)
    }

    @Test
    fun removingTheKeyClearsAtOnce() {
        answer = { 200 to json(12, 60) }
        drive()
        drive(null)
        assertNull(TrafficAhead.flow)
        assertNull(label())
        assertEquals(2, told)
    }

    @Test
    fun aPointOffTheRoadsGivesNothingFromIt() {
        // TomTom answers 400 for a point too far from any road it knows: the other point still counts.
        answer = { if (it == ahead) 400 to null else 200 to json(28, 60) }
        drive()
        assertEquals("Ralenti · 28 km/h", label())
    }

    @Test
    fun theSameTrafficAgainIsNotRedrawn() {
        answer = { 200 to json(12, 60) }
        drive()
        now += 61_000
        drive()
        assertEquals(1, told)
    }

    @Test
    fun theKeyIsSentTrimmed() {
        answer = { 200 to json(58, 60) }
        drive("  KEY \n")
        assertEquals("KEY", asked.first().first)
    }

    @Test
    fun aJamFurtherAheadIsToldInKilometres() {
        answer = { if (it == far) 200 to json(10, 60) else 200 to json(55, 60) }
        TrafficAhead.follow("KEY", here, listOf(ahead to 500, far to 1500))
        assertEquals(3, asked.size)
        assertEquals("Bouchon à 1,5 km · 10 km/h", label())
        assertEquals("Ralenti à 1 km · 40 km/h", TrafficAhead.label(TrafficAhead.Reading(Flow.SLOW, 40, 60, meters = 1000)))
    }

    @Test
    fun theWorseOfTomTomAndMapsColoursTheRoad() {
        assertEquals(Flow.JAM, TrafficAhead.worst(listOf(Flow.FLUID, Flow.JAM)))
        assertEquals(Flow.SLOW, TrafficAhead.worst(listOf(null, Flow.SLOW)))
        assertNull(TrafficAhead.worst(listOf(null, null)))
    }

    /** One GPS fix a second for [seconds] at [kmh]. */
    private fun crawl(seconds: Int, kmh: Int, limit: Int? = 100) = repeat(seconds) { now += 1_000; TrafficAhead.drive(kmh, limit) }

    @Test
    fun crawlingBehindABusIsAJamEvenWhenTomTomSaysSlow() {
        // TomTom: 55 against 100 free under the car (orange); the car itself does 6 km/h for over a minute.
        answer = { 200 to json(55, 100) }
        drive(ahead = null)
        assertEquals("Ralenti · 55 km/h", TrafficAhead.label(TrafficAhead.shown))
        crawl(30, 6)
        // Half a minute: not yet.
        assertEquals("Ralenti · 55 km/h", TrafficAhead.label(TrafficAhead.shown))
        crawl(35, 6)
        assertEquals("Bouchon · 6 km/h", TrafficAhead.label(TrafficAhead.shown))
        // Moving freely again: within the minute and a half, back to TomTom's.
        crawl(95, 95)
        assertNull(TrafficAhead.ownReading)
    }

    @Test
    fun withoutAKeyTheRoadLimitIsTheFreeSpeed() {
        crawl(70, 50, limit = 100)
        assertEquals("Ralenti · 50 km/h", TrafficAhead.label(TrafficAhead.shown))
    }

    @Test
    fun aRedLightInTownIsNotAJam() {
        // A 60 km/h street: stopping a minute at a light says nothing.
        crawl(70, 0, limit = 60)
        assertNull(TrafficAhead.shown)
        // No known limit either: nothing.
        TrafficAhead.reset()
        crawl(70, 0, limit = null)
        assertNull(TrafficAhead.shown)
    }

    @Test
    fun noSpeedForgetsTheCrawl() {
        crawl(70, 5)
        assertEquals(Flow.JAM, TrafficAhead.shown?.flow)
        TrafficAhead.drive(null, 100)
        assertNull(TrafficAhead.shown)
    }

    @Test
    fun theRoadAheadStretchByStretch() {
        val points = listOf(33.5895 to -7.62 to 500, 33.594 to -7.62 to 1000, 33.603 to -7.62 to 2000, 33.612 to -7.62 to 3000)
        // Free near the car, slow from 1 km, jammed from 2 km, nothing known at 3 km.
        answer = { point ->
            when (points.firstOrNull { it.first == point }?.second) {
                1000 -> 200 to json(35, 60)
                2000 -> 200 to json(10, 60)
                3000 -> 400 to null
                else -> 200 to json(95, 100)
            }
        }
        TrafficAhead.follow("KEY", here, points)
        assertEquals(5, asked.size)
        assertEquals(listOf(0 to Flow.FLUID, 500 to Flow.FLUID, 1000 to Flow.SLOW, 2000 to Flow.JAM),
            TrafficAhead.stretches.map { it.meters to it.flow })
        assertEquals("Bouchon à 2 km · 10 km/h", label())
        // Each stretch ends where the next point was asked; 3 km goes on to 3.5 km.
        assertEquals(listOf(500, 1000, 2000, 3000, 3500), listOf(0, 500, 1000, 2000, 3000).map { TrafficAhead.end(it) })
        // Crawling under the car: the first stretch turns red, the others stay.
        repeat(70) { now += 1_000; TrafficAhead.drive(5, 100) }
        assertEquals(listOf(Flow.JAM, Flow.FLUID, Flow.SLOW, Flow.JAM), TrafficAhead.stretches.map { it.flow })
        // Three minutes without news: nothing left to draw.
        now += 3 * 60_000
        assertEquals(listOf(Flow.JAM), TrafficAhead.stretches.map { it.flow })
    }

    @Test
    fun oddAnswersAreIgnored() {
        assertNull(TrafficAhead.parse("""{"flowSegmentData":{"currentSpeed":30}}"""))
        assertNull(TrafficAhead.parse("""{"flowSegmentData":{"currentSpeed":30,"freeFlowSpeed":0}}"""))
        assertEquals(Flow.FLUID, TrafficAhead.parse("""{"flowSegmentData":{"currentSpeed":90,"freeFlowSpeed":60}}""")?.flow)
        assertNull(TrafficAhead.parse("""{"detailedError":{"code":"Unauthorized"}}"""))
    }
}
