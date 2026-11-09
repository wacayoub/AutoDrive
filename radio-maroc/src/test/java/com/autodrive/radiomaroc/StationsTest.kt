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
        val tajwid = Stations.visible(StationTab.TAJWID, emptySet(), "")
        assertEquals(12, morocco.size)
        assertEquals(12, quran.size)
        assertEquals(4, tajwid.size)
        assertEquals(Stations.all.size, Stations.all.map { it.url }.toSet().size)
        assertTrue(Stations.all.all { RadioStation.validUrl(it.url) == it.url })
        assertFalse(Stations.all.any { "snrtlive.ma" in it.url })
        assertEquals("العيون الكوشي", quran.first().name)
        assertTrue(tajwid.all { it.url.endsWith("_mojawwad") && it !in quran })
        assertEquals("عبد الباسط عبد الصمد", tajwid.first().name)
    }

    @Test fun favoritesSearchAndStationControlsUseTheVisibleList() {
        val morocco = Stations.visible(StationTab.MOROCCO, emptySet(), "")
        assertEquals(
            listOf("Med Radio", "HIT RADIO", "Radio Aswat", "Chada FM", "Radio Mars", "Medina FM"),
            morocco.take(6).map { it.name },
        )
        assertEquals("HIT RADIO", RadioQueue.neighbor(morocco, morocco.first().url, 1)?.name)
        assertEquals(morocco.last(), RadioQueue.neighbor(morocco, morocco.first().url, -1))
        assertEquals("Radio Aswat", Stations.visible(StationTab.MOROCCO, emptySet(), "aswat").single().name)
        val quran = Stations.visible(StationTab.QURAN, emptySet(), "قَزَابْرِي").single()
        assertEquals("عمر القزابري", quran.name)
        assertEquals(listOf(quran), Stations.visible(StationTab.FAVORITES, setOf(quran.url), ""))
    }

    @Test fun badrAndTajwidCanBeFoundAndPreviousFavoritesSurviveTheMove() {
        val badr = Stations.visible(StationTab.QURAN, emptySet(), "Badr Turki").single()
        assertEquals("بدر التركي", badr.name)
        assertEquals(badr, Stations.visible(StationTab.QURAN, emptySet(), "بَدْر التُّرْكِي").single())
        val abdelbaset = Stations.visible(StationTab.TAJWID, emptySet(), "Abdelbaset").single()
        assertTrue(abdelbaset.url.endsWith("abdulbasit_abdulsamad_mojawwad"))
        assertTrue(Stations.subtitle(abdelbaset).startsWith("المصحف المجوّد"))
        val previousFavorite = "https://backup.qurango.net/radio/mohammed_siddiq_alminshawi_mojawwad"
        assertEquals(previousFavorite, Stations.visible(StationTab.FAVORITES, setOf(previousFavorite), "").single().url)
    }

    @Test fun haramRecordingsAreSearchableAndKeepThePrayerAndDate() {
        val haram = Stations.visible(StationTab.HARAM, emptySet(), "")
        assertEquals(6, haram.size)
        assertTrue(haram.all { Stations.isRecording(it) && "/makkah/" in it.url && it.url.endsWith(".mp3") })
        assertFalse(Stations.isRecording(Stations.visible(StationTab.QURAN, emptySet(), "Badr Turki").single()))
        for (prayer in listOf("الفجر", "المغرب", "العشاء")) {
            val results = Stations.visible(StationTab.HARAM, emptySet(), "صلاة $prayer")
            assertEquals(2, results.size)
            assertTrue(results.all { it.name.endsWith(prayer) && Stations.subtitle(it).startsWith("المسجد الحرام · 2026-") })
        }
        val badr = Stations.visible(StationTab.HARAM, emptySet(), "Badr Turki")
        assertEquals(2, badr.size)
        assertTrue(badr.all { Stations.reciterName(it) == "بدر التركي" })
        assertEquals(badr, Stations.visible(StationTab.FAVORITES, badr.map { it.url }.toSet(), ""))
    }

    @Test fun recurringRecitersUseTheSamePortraitAcrossTabs() {
        val badr = Stations.visible(StationTab.QURAN, emptySet(), "Badr Turki").single()
        val haram = Stations.visible(StationTab.HARAM, emptySet(), "")
        assertTrue(haram.all { ReciterPortraits.forStation(it) != null })
        val portrait = requireNotNull(ReciterPortraits.forStation(badr))
        assertTrue(haram.filter { Stations.reciterName(it) == badr.name }.all { ReciterPortraits.forStation(it) == portrait })
    }
}
