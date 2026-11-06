package com.autodrive.launcher

import org.junit.Assert.*
import org.junit.Test

class RadioQueueTest {
    private val hit = RadioStation("HIT", "https://radio.example/hit")
    private val mars = RadioStation("Mars", "https://radio.example/mars", logoUrl = "https://radio.example/logo.png")
    private val medi = RadioStation("Medi1", "https://radio.example/medi")
    @Test fun oneSavedStationDoesNotRestrictTheCatalogueQueue() {
        val all = RadioQueue.merge(listOf(hit, mars, medi), listOf(hit))
        assertEquals(3, all.size)
        assertEquals(mars, RadioQueue.neighbor(all, hit.url, 1))
        assertEquals(hit, RadioQueue.neighbor(all, mars.url, -1))
        assertEquals(medi, RadioQueue.neighbor(all, hit.url, -1))
        assertEquals(hit, RadioQueue.neighbor(all, medi.url, 1))
    }
    @Test fun favoriteQueueAndMissingSelectionHaveDefinedBoundaries() {
        val all = RadioQueue.merge(listOf(hit, mars, medi), listOf(hit.copy(favorite = true), medi.copy(favorite = true)))
        val favorites = RadioQueue.active(all, true)
        assertEquals(listOf(hit.url, medi.url), favorites.map { it.url })
        assertEquals(medi.url, RadioQueue.neighbor(favorites, hit.url, 1)?.url)
        assertEquals(hit.url, RadioQueue.neighbor(favorites, mars.url, 1)?.url)
        assertEquals(medi.url, RadioQueue.neighbor(favorites, mars.url, -1)?.url)
        assertNull(RadioQueue.neighbor(emptyList(), hit.url, 1))
        assertNull(RadioQueue.neighbor(listOf(hit), hit.url, -1))
    }
    @Test fun catalogueMetadataAndPersonalEditsAreMergedWithoutDuplicates() {
        val result = RadioQueue.merge(listOf(mars), listOf(mars.copy(name = "Ma radio", logoUrl = null, favorite = true), hit))
        assertEquals(2, result.size)
        assertEquals("Ma radio", result.first().name)
        assertEquals(mars.logoUrl, result.first().logoUrl)
        assertTrue(result.first().favorite)
    }
}
