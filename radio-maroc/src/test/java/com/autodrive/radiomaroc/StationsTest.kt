package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioQueue
import com.autodrive.launcher.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationsTest {
    @Test fun categoriesUseDistinctDirectHttpsStreams() {
        val morocco = Stations.visible(StationTab.MOROCCO, emptySet(), "")
        val quran = Stations.visible(StationTab.QURAN, emptySet(), "")
        assertEquals(12, morocco.size)
        assertEquals(12, quran.size)
        assertEquals(Stations.all.size, Stations.all.map { it.url }.toSet().size)
        assertTrue(Stations.all.all { RadioStation.validUrl(it.url) == it.url })
        assertFalse(Stations.all.any { "snrtlive.ma" in it.url })
        assertEquals("العيون الكوشي", quran.first().name)
    }

    @Test fun favoritesSearchAndStationControlsUseTheVisibleList() {
        val morocco = Stations.visible(StationTab.MOROCCO, emptySet(), "")
        assertEquals("Radio Mars", RadioQueue.neighbor(morocco, morocco.first().url, 1)?.name)
        assertEquals(morocco.last(), RadioQueue.neighbor(morocco, morocco.first().url, -1))
        assertEquals("Radio Aswat", Stations.visible(StationTab.MOROCCO, emptySet(), "aswat").single().name)
        val quran = Stations.visible(StationTab.QURAN, emptySet(), "قَزَابْرِي").single()
        assertEquals("عمر القزابري", quran.name)
        assertEquals(listOf(quran), Stations.visible(StationTab.FAVORITES, setOf(quran.url), ""))
    }
}
