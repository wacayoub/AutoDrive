package com.autodrive.radiomaroc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationDirectoryTest {
    @Test fun keepsPlayableHttpsAndPagesByRawResponseCount() {
        val good = """{"countrycode":"FR","lastcheckok":1,"name":"Info","url_resolved":"https://radio.example/live","hls":1,"clickcount":42}"""
        val insecure = """{"countrycode":"FR","lastcheckok":1,"name":"Insecure","url_resolved":"http://radio.example/live"}"""
        val wrongCountry = good.replace("FR", "BE")
        val broken = good.replace("\"lastcheckok\":1", "\"lastcheckok\":0")
        val json = (List(97) { good } + listOf(insecure, wrongCountry, broken)).joinToString(",", "[", "]")
        val page = StationDirectory.parsePage(json, "FR", 100)
        val station = page.stations.single()
        assertEquals("FR", station.countryCode)
        assertEquals(42, station.directoryClicks)
        assertTrue(station.isHls)
        assertEquals(200, page.nextOffset)
        assertNull(StationDirectory.parsePage("[]", "FR", 200).nextOffset)
        assertEquals(page.stations, StationDirectory.decode(StationDirectory.encode(page.stations)))
        assertNull(StationDirectory.decode("""[{"name":"Info","url":"https://radio.example/live","country":"FR"}]""").single().directoryClicks)
    }

    @Test fun continentsCoverTheCountryPickerWithoutOverlap() {
        val grouped = WorldContinent.entries.flatMap { it.countryCodes }
        assertEquals(StationDirectory.countries.toSet(), grouped.toSet())
        assertEquals(grouped.toSet().size, grouped.size)
        assertEquals(WorldContinent.AFRICA, WorldContinent.forCountry("TN"))
        assertEquals(WorldContinent.EUROPE, WorldContinent.forCountry("XK"))
        assertEquals(WorldContinent.ASIA, WorldContinent.forCountry("TW"))
        assertEquals(WorldContinent.NORTH_AMERICA, WorldContinent.forCountry("MX"))
        assertEquals(WorldContinent.SOUTH_AMERICA, WorldContinent.forCountry("BR"))
        assertEquals(WorldContinent.OCEANIA, WorldContinent.forCountry("AU"))
        assertEquals(WorldContinent.ANTARCTICA, WorldContinent.forCountry("AQ"))
    }
}
