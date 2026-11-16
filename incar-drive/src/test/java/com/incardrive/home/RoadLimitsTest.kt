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
}
