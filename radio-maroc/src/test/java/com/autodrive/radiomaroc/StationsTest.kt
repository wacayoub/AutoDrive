package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioQueue
import com.autodrive.launcher.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationsTest {
    @Test fun categoriesUseDistinctHttpsSourcesAndOfficialSnrtPlayers() {
        val morocco = Stations.visible(StationTab.MOROCCO, emptySet(), "")
        val quran = Stations.visible(StationTab.QURAN, emptySet(), "")
        val tajwid = Stations.visible(StationTab.TAJWID, emptySet(), "")
        assertEquals(17, morocco.size)
        assertEquals(17, morocco.mapNotNull { it.logoUrl?.let(RadioStation::validUrl) }.toSet().size)
        assertEquals(12, quran.size)
        assertEquals(4, tajwid.size)
        assertEquals(Stations.all.size, Stations.all.map { it.url }.toSet().size)
        assertTrue(Stations.all.all { RadioStation.validUrl(it.url) == it.url })
        assertEquals(4, Stations.snrt.size)
        assertTrue(Stations.snrt.all { it.url.startsWith("https://snrtlive.ma/") })
        assertEquals(Stations.snrt.toSet(), Stations.visible(StationTab.MOROCCO, emptySet(), "SNRT").toSet())
        assertEquals("العيون الكوشي", quran.first().name)
        assertTrue(tajwid.all { it.url.endsWith("_mojawwad") && it !in quran })
        assertEquals("عبد الباسط عبد الصمد", tajwid.first().name)
    }

    @Test fun directoryRowsMergeWithMoroccoAndBookmarksStayAvailableAcrossCountries() {
        val pinned = Stations.visible(StationTab.MOROCCO, emptySet(), "").first()
        val moroccan = RadioStation("Radio Azrou", "https://audio.example/azrou", countryCode = "MA")
        val french = RadioStation("France Info", "https://audio.example/info", countryCode = "FR")
        val mfm = RadioStation("MFM Radio", "https://audio.example/mfm", countryCode = "MA")
        val directory = listOf(pinned.copy(countryCode = "MA"), moroccan, french, mfm)
        val allMoroccan = Stations.visible(StationTab.MOROCCO, emptySet(), "", directory = directory)
        assertEquals(1, allMoroccan.count { it.url == pinned.url })
        assertEquals(mfm, allMoroccan[3])
        assertEquals(moroccan, allMoroccan.last())
        assertTrue(french !in allMoroccan)
        val bookmarks = listOf(moroccan, french)
        val favorites = bookmarks.map { it.url }.toSet()
        assertEquals(bookmarks, Stations.visible(StationTab.FAVORITES, favorites, "", bookmarks = bookmarks))
        assertEquals(listOf(moroccan), Stations.visible(StationTab.MOROCCO, favorites, "", true, bookmarks = bookmarks))
    }

    @Test fun favoritesSearchAndStationControlsUseTheVisibleList() {
        val morocco = Stations.visible(StationTab.MOROCCO, emptySet(), "")
        assertEquals(
            listOf("محمد السادس للقرآن الكريم", "Med Radio", "HIT RADIO", "Radio Aswat", "Radio Nationale",
                "Chada FM", "Radio Mars", "Radio 2M", "Radio Amazighe", "Medina FM", "Chaîne Inter"),
            morocco.take(11).map { it.name },
        )
        assertEquals("Med Radio", RadioQueue.neighbor(morocco, morocco.first().url, 1)?.name)
        assertEquals(morocco.last(), RadioQueue.neighbor(morocco, morocco.first().url, -1))
        assertEquals("Radio Aswat", Stations.visible(StationTab.MOROCCO, emptySet(), "aswat").single().name)
        val quran = Stations.visible(StationTab.QURAN, emptySet(), "قَزَابْرِي").single()
        assertEquals("عمر القزابري", quran.name)
        assertEquals(listOf(quran), Stations.visible(StationTab.FAVORITES, setOf(quran.url), ""))
    }

    @Test fun publishedSharesPrecedeDirectoryClicksAndKeepCuratedStreams() {
        val high = RadioStation("Radio Azrou", "https://audio.example/azrou", countryCode = "MA", directoryClicks = 420)
        val low = RadioStation("Radio Ifrane", "https://audio.example/ifrane", countryCode = "MA", directoryClicks = 10)
        val dance = RadioStation("U Dance", "https://audio.example/dance", countryCode = "MA", directoryClicks = 100)
        val mfm = RadioStation("MFM Radio", "https://audio.example/mfm", countryCode = "MA", directoryClicks = 2)
        val stations = Stations.visible(StationTab.MOROCCO, emptySet(), "", directory = listOf(low, high, dance, mfm))
        val curatedDance = stations.single { it.name == dance.name }
        assertEquals("15,36 %", Stations.audienceLabel(stations.first()))
        assertEquals("8,09 %", Stations.audienceLabel(stations[3]))
        assertNull(Stations.audienceLabel(high))
        assertTrue(stations.indexOf(high) > stations.indexOfLast { Stations.audienceLabel(it) != null })
        assertTrue(stations.indexOf(high) < stations.indexOf(curatedDance))
        assertTrue(stations.indexOf(curatedDance) < stations.indexOf(low))
        assertEquals("https://udance.ice.infomaniak.ch/udance-128.aac", curatedDance.url)
    }

    @Test fun radioFavoritesKeepAudienceOrderAndExcludeOtherTabs() {
        val morocco = Stations.visible(StationTab.MOROCCO, emptySet(), "")
        val med = morocco.single { it.name == "Med Radio" }
        val mars = morocco.single { it.name == "Radio Mars" }
        val inter = morocco.single { it.name == "Chaîne Inter" }
        val quran = Stations.visible(StationTab.QURAN, emptySet(), "").first()
        val favorites = setOf(mars.url, quran.url, med.url, inter.url)
        val visible = Stations.visible(StationTab.MOROCCO, favorites, "", radioFavoritesOnly = true)
        assertEquals(listOf(med, mars, inter), visible)
        assertEquals(mars, RadioQueue.neighbor(visible, med.url, 1))
        assertEquals(listOf(mars), Stations.visible(StationTab.MOROCCO, favorites, "mars", radioFavoritesOnly = true))
        assertEquals(listOf(med, mars, inter, quran), Stations.visible(StationTab.FAVORITES, favorites, ""))
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
            assertTrue(results.all { Stations.subtitle(it).startsWith("صلاة $prayer · المسجد الحرام · 2026-") })
        }
        assertEquals("بدر التركي · سورة التين", Stations.visible(StationTab.HARAM, emptySet(), "سورة التين").single().name)
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
