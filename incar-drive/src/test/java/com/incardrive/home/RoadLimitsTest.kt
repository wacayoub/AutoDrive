package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoadLimitsTest {
    @Test
    fun readsMaxspeedValues() {
        assertEquals(60, RoadLimits.parseMaxspeed("60"))
        assertEquals(48, RoadLimits.parseMaxspeed("30 mph"))
        assertEquals(60, RoadLimits.parseMaxspeed("MA:urban"))
        assertEquals(120, RoadLimits.parseMaxspeed("MA:motorway"))
        assertNull(RoadLimits.parseMaxspeed("none"))
        assertNull(RoadLimits.parseMaxspeed("60;80"))
        assertNull(RoadLimits.parseMaxspeed(null))
    }

    private val json = """
        {"elements":[
          {"type":"way","tags":{"highway":"primary","name":"Boulevard Zerktouni","maxspeed":"60"},
           "geometry":[{"lat":33.5800,"lon":-7.6200},{"lat":33.5900,"lon":-7.6200}]},
          {"type":"way","tags":{"highway":"residential","name":"Rue Ibnou Sina"},
           "geometry":[{"lat":33.5850,"lon":-7.6250},{"lat":33.5850,"lon":-7.6150}]},
          {"type":"way","tags":{"highway":"motorway","maxspeed":"120"},
           "geometry":[{"lat":33.6000,"lon":-7.7000},{"lat":33.6100,"lon":-7.7000}]}
        ]}
    """.trimIndent()

    @Test
    fun parsesRoadsWithTheirLimits() {
        val roads = RoadLimits.parse(json)
        assertEquals(3, roads.size)
        assertEquals(60, roads[0].limit)
        assertNull(roads[1].limit)
        assertEquals("Boulevard Zerktouni", roads[0].name)
    }

    @Test
    fun matchesTheRoadBeingDriven() {
        val roads = RoadLimits.parse(json)
        // Beside the boulevard, 10 m east of it.
        assertEquals("Boulevard Zerktouni", RoadLimits.match(roads, 33.5830, -7.61989, 0)?.name)
        assertNull(RoadLimits.match(roads, 33.5700, -7.6400, null))
    }

    @Test
    fun atACrossingTheHeadingDecides() {
        val roads = RoadLimits.parse(json)
        // At the crossing of both streets: heading north is the boulevard, heading east is the side street.
        assertEquals("Boulevard Zerktouni", RoadLimits.match(roads, 33.5850, -7.62003, 0)?.name)
        assertEquals("Rue Ibnou Sina", RoadLimits.match(roads, 33.58497, -7.6200, 90)?.name)
    }

    private val withCameras = """
        {"elements":[
          {"type":"node","lat":33.5900,"lon":-7.6200,"tags":{"highway":"speed_camera","maxspeed":"60"}},
          {"type":"node","lat":33.5700,"lon":-7.6200,"tags":{"highway":"speed_camera"}},
          {"type":"node","lat":33.5800,"lon":-7.6300,"tags":{"amenity":"cafe"}}
        ]}
    """.trimIndent()

    @Test
    fun readsSpeedCameras() {
        val cameras = RoadLimits.parseCameras(withCameras)
        assertEquals(2, cameras.size)
        assertEquals(60, cameras[0].limit)
    }

    @Test
    fun warnsOnlyForACameraAhead() {
        val cameras = RoadLimits.parseCameras(withCameras)
        // 300 m south of the first camera, driving north: it is ahead.
        val ahead = RoadLimits.cameraAhead(cameras, 33.5873, -7.6200, 0)
        assertEquals(cameras[0], ahead?.first)
        assertEquals(300.0, ahead!!.second.toDouble(), 10.0)
        // Driving south, away from it: no warning.
        assertNull(RoadLimits.cameraAhead(cameras, 33.5873, -7.6200, 180))
    }

    @Test
    fun aSquareSurvivesBeingKept() {
        val tile = listOf(Road(60, "Bd | Zerktouni", listOf(33.58 to -7.62, 33.581 to -7.621)), Road(null, null, listOf(33.5 to -7.6, 33.6 to -7.7))) to
            listOf(Camera(33.59, -7.63, 80), Camera(33.6, -7.64, null))
        val again = RoadLimits.decodeTile(RoadLimits.encodeTile(tile))
        assertEquals(2, again.first.size)
        assertEquals("Bd   Zerktouni", again.first[0].name)
        assertEquals(60, again.first[0].limit)
        assertNull(again.first[1].limit)
        assertEquals(tile.first[1].points, again.first[1].points)
        assertEquals(tile.second, again.second)
    }

    @Test
    fun theSquareUnderTheCarComesFirst() {
        // In the middle of a square: only that one; near its north edge: the one to the north as well.
        assertEquals(listOf(RoadLimits.key(33.57, -7.59)), RoadLimits.needed(33.57, -7.59))
        val nearEdge = RoadLimits.needed(33.5795, -7.59)
        assertEquals(RoadLimits.key(33.5795, -7.59), nearEdge[0])
        assertEquals(RoadLimits.key(33.5805, -7.59), nearEdge[1])
        assertEquals(9, RoadLimits.around(33.57, -7.59).size)
    }
}
