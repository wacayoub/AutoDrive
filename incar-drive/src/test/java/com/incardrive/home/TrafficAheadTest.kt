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

    private fun drive(key: String? = "KEY", ahead: Pair<Double, Double>? = this.ahead) = TrafficAhead.follow(key, here, ahead)
    private fun label() = TrafficAhead.label(TrafficAhead.reading, TrafficAhead.refused)

    @Test
    fun withoutAKeyNothingIsAskedNorShown() {
        drive(null)
        drive("   ")
        assertTrue(asked.isEmpty())
        assertNull(label())
    }

    @Test
    fun aFreeRoadAskedOnceAMinuteShowsNothing() {
        answer = { 200 to json(58, 60) }
        drive()
        assertEquals(2, asked.size)
        assertEquals(Flow.FLUID, TrafficAhead.flow)
        assertNull(label())
        // Every GPS fix in the next minute: no new question.
        now += 30_000
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
    fun oddAnswersAreIgnored() {
        assertNull(TrafficAhead.parse("""{"flowSegmentData":{"currentSpeed":30}}"""))
        assertNull(TrafficAhead.parse("""{"flowSegmentData":{"currentSpeed":30,"freeFlowSpeed":0}}"""))
        assertEquals(Flow.FLUID, TrafficAhead.parse("""{"flowSegmentData":{"currentSpeed":90,"freeFlowSpeed":60}}""")?.flow)
        assertNull(TrafficAhead.parse("""{"detailedError":{"code":"Unauthorized"}}"""))
    }
}
