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

    // A divided boulevard: the eastern carriageway goes north, the western one south, 20 m apart; a service road
    // (two-way) runs 12 m east of the northbound side.
    private val divided = listOf(
        Road(60, "Nord", listOf(33.580 to -7.6200, 33.590 to -7.6200), oneway = 1),
        Road(80, "Sud", listOf(33.590 to -7.62022, 33.580 to -7.62022), oneway = 1),
        Road(40, "Contre-allée", listOf(33.580 to -7.61987, 33.590 to -7.61987)),
    )

    @Test
    fun onADividedRoadTheSideBeingDrivenIsChosen() {
        // Between both carriageways, nearer the southbound one, but driving north: the northbound side.
        assertEquals("Nord", RoadLimits.match(divided, 33.585, -7.62015, 0)?.name)
        // The same place driving south: the southbound side.
        assertEquals("Sud", RoadLimits.match(divided, 33.585, -7.62005, 180)?.name)
    }

    @Test
    fun theRoadBeingDrivenIsKeptAgainstAParallelOne() {
        // Halfway between the northbound side and the service road: the previous road is kept.
        val halfway = -7.619935
        assertEquals("Nord", RoadLimits.match(divided, 33.585, halfway, 0, divided[0])?.name)
        assertEquals("Contre-allée", RoadLimits.match(divided, 33.585, halfway, 0, divided[2])?.name)
    }

    @Test
    fun aOneWayRoadKeepsItsDirectionWhenKept() {
        val again = RoadLimits.decodeTile(RoadLimits.encodeTile(divided to emptyList()))
        assertEquals(listOf(1, 1, 0), again.first.map { it.oneway })
        // A square kept by an older version, without the direction: two-way.
        assertEquals(0, RoadLimits.decodeTile("R|60|Bd|33.5,-7.6;33.6,-7.6").first.single().oneway)
    }

    @Test
    fun aRoadsideCameraCloseAheadIsAnnounced() {
        // 80 m ahead and 30 m to the side (a pole across a wide road): about 20° off the heading.
        val camera = Camera(33.58072, -7.61968, 60)
        assertEquals(camera, RoadLimits.cameraAhead(listOf(camera), 33.580, -7.6200, 0)?.first)
        // 60 m to the side and 50 m ahead: another street, not announced.
        assertNull(RoadLimits.cameraAhead(listOf(Camera(33.58045, -7.61935, null)), 33.580, -7.6200, 0))
    }

    @Test
    fun anEnforcementNodeIsACamera() {
        val json = """{"elements":[{"type":"node","lat":33.6,"lon":-7.6,"tags":{"enforcement":"maxspeed","maxspeed":"80"}}]}"""
        assertEquals(80, RoadLimits.parseCameras(json).single().limit)
    }

    @Test
    fun theCameraZoneRuns100MetresEachSideAcrossJunctions() {
        // A street going north, cut in two at a junction 50 m after the camera, with a side street there.
        val south = Road(60, "Bd", listOf(33.5800 to -7.6200, 33.5810 to -7.6200, 33.58145 to -7.6200))
        val north = Road(60, "Bd", listOf(33.58145 to -7.6200, 33.5830 to -7.6200))
        val side = Road(null, "Rue", listOf(33.58145 to -7.6200, 33.58145 to -7.6180))
        val camera = Camera(33.5810, -7.61985, 60)
        val zone = RoadLimits.cameraZone(listOf(side, south, north), camera)
        val first = zone.first()
        val last = zone.last()
        assertEquals(-7.6200, first.second, 1e-9)
        assertEquals(-7.6200, last.second, 1e-9)
        assertEquals(100.0, TripMeter.distanceMeters(33.5810, -7.6200, first.first, first.second), 0.5)
        assertEquals(100.0, TripMeter.distanceMeters(33.5810, -7.6200, last.first, last.second), 0.5)
        assertEquals(true, last.first > first.first)
        // A road joined in the middle of another one: on along the one that goes straight.
        val long = Road(60, "Av", listOf(33.5900 to -7.6200, 33.5910 to -7.6200, 33.5930 to -7.6200))
        val stub = Road(60, "Av", listOf(33.5890 to -7.6200, 33.5910 to -7.6200))
        val turned = RoadLimits.cameraZone(listOf(stub, long), Camera(33.5892, -7.6200, null))
        assertEquals(-7.6200, turned.last().second, 1e-9)
        assertEquals(100.0, TripMeter.distanceMeters(33.5892, -7.6200, turned.last().first, turned.last().second), 0.5)
        // No road near: nothing to colour.
        assertEquals(emptyList<Pair<Double, Double>>(), RoadLimits.cameraZone(listOf(side), Camera(33.5700, -7.6300, null)))
    }
}
