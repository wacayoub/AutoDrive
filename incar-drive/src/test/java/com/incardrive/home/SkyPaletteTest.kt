package com.incardrive.home

import com.autodrive.radiomaroc.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Month

class SkyPaletteTest {
    @Test
    fun seasonsFollowTheMonths() {
        assertEquals(Season.WINTER, Season.of(Month.JANUARY))
        assertEquals(Season.SPRING, Season.of(Month.APRIL))
        assertEquals(Season.SUMMER, Season.of(Month.JULY))
        assertEquals(Season.AUTUMN, Season.of(Month.NOVEMBER))
        assertEquals(Season.WINTER, Season.of(Month.DECEMBER))
    }

    @Test
    fun eachSeasonHasItsOwnSky() {
        val skies = Month.entries.map { SkyPalette.of(WeatherCondition.CLEAR, it, night = false) }.toSet()
        assertEquals(4, skies.size)
    }

    @Test
    fun rainGreysTheSkyAndHidesTheSun() {
        val clear = SkyPalette.of(WeatherCondition.CLEAR, Month.JULY, night = false)
        val rain = SkyPalette.of(WeatherCondition.RAIN, Month.JULY, night = false)
        assertNotEquals(clear.bottom, rain.bottom)
        assertTrue((rain.glow ushr 24) < (clear.glow ushr 24))
    }

    @Test
    fun unknownWeatherKeepsTheClearSky() {
        assertEquals(SkyPalette.of(WeatherCondition.CLEAR, Month.MAY, true), SkyPalette.of(null, Month.MAY, true))
    }

    @Test
    fun mixesColours() {
        assertEquals(0xFF7F7F7F.toInt(), SkyPalette.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), .5f))
    }
}
