package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CabinRoadsTest {
    // A straight road running north-south through the car, drawn from south to north.
    private val northSouth = floatArrayOf(0f, -200f, 0f, -100f, 0f, 0f, 0f, 100f, 0f, 200f, 0f, 300f)

    @Test
    fun aheadFollowsTheHeading() {
        val north = CabinRoads.ahead(northSouth, heading = 5f)!!
        assertEquals(0f, north[1], .01f)
        assertEquals(300f, north.last(), .01f)
        val south = CabinRoads.ahead(northSouth, heading = 182f)!!
        assertEquals(-200f, south.last(), .01f)
    }

    @Test
    fun aheadIgnoresARoadFarAway() {
        val far = floatArrayOf(100f, -50f, 100f, 50f)
        assertNull(CabinRoads.ahead(far, heading = 0f))
    }

    @Test
    fun alongAndPart() {
        val line = floatArrayOf(0f, 0f, 0f, 100f, 100f, 100f)
        val point = CabinRoads.along(line, 150f)
        assertNotNull(point)
        assertEquals(50f, point!!.first, .01f)
        assertEquals(100f, point.second, .01f)
        assertNull(CabinRoads.along(line, 250f))
        val part = CabinRoads.part(line, 50f, 150f)
        assertEquals(floatArrayOf(0f, 50f, 0f, 100f, 50f, 100f).toList(), part.toList())
    }

    @Test
    fun localizeAndAngles() {
        val local = CabinRoads.localize(listOf(listOf(33.0 to -7.0, 33.001 to -7.0)), 33.0, -7.0)
        assertEquals(111.32f, local[0][3], .01f)
        assertEquals(-170f, CabinRoads.angle(190f), .01f)
        val (east, north) = CabinRoads.moved(90f, 10f, 2f)
        assertEquals(20f, east, .01f)
        assertEquals(0f, north, .01f)
    }
}
