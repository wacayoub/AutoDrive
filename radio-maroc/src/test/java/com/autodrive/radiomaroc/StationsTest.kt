package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioQueue
import com.autodrive.launcher.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationsTest {
    @Test fun categoriesUseDistinctHttpsSourcesAndKeepBroadcastStationIds() {
        val morocco = Stations.visible(StationTab.MOROCCO, emptySet(), "")
        val quran = Stations.visible(StationTab.QURAN, emptySet(), "")
        val tajwid = quran.filter { Stations.quranSection(it) == QuranSection.TAJWID }
        assertEquals(59, morocco.size)
        assertTrue(morocco.mapNotNull { it.logoUrl }.all { RadioStation.validUrl(it) != null })
        assertEquals(32, quran.size)
        assertEquals(22, quran.count { Stations.quranSection(it) == QuranSection.MURATTAL })
        assertEquals(4, tajwid.size)
        assertEquals(Stations.all.size, Stations.all.map { it.url }.toSet().size)
        assertTrue(Stations.all.all { RadioStation.validUrl(it.url) == it.url })
        assertEquals(4, Stations.snrtNational.size)
        assertTrue(Stations.snrtNational.all { it.url.startsWith("https://snrtlive.ma/") })
        assertEquals(Stations.snrt.toSet(), Stations.visible(StationTab.MOROCCO, emptySet(), "SNRT").toSet())
        val streams = mapOf(
            "https://snrtlive.ma/fr/alidaa-alwatania" to "radio_nationale",
            "https://snrtlive.ma/fr/alidaa-alamazighia" to "radio_amazigh",
            "https://snrtlive.ma/fr/chaine-inter" to "radio_inter",
            "https://snrtlive.ma/fr/idaat-mohammed-assadiss" to "radio_med_VI",
        )
        Stations.snrtNational.forEach { station ->
            assertEquals("https://cdn.live.easybroadcast.io/live/${streams.getValue(station.url)}/playlist.m3u8", Stations.streamUrl(station))
            assertTrue(Stations.isHls(station))
            assertEquals("SNRT Live · Maroc", Stations.subtitle(station))
        }
        val twoM = morocco.single { it.url == "https://radio2m.ma/" }
        assertEquals("Radio 2M", twoM.name)
        assertEquals("SOREAD 2M · Maroc", Stations.subtitle(twoM))
        assertTrue(Stations.isHls(twoM))
        assertTrue(Stations.streamUrl(twoM).startsWith("https://cdn-globecast.akamaized.net/live/eds/radio_2m/"))
        val luxe = Stations.visible(StationTab.MOROCCO, emptySet(), "luxe").single()
        assertEquals(Stations.luxe, luxe)
        assertFalse(Stations.isHls(luxe))
        val savedStations = Stations.snrt + twoM + luxe
        assertEquals(savedStations.toSet(),
            Stations.visible(StationTab.FAVORITES, savedStations.map { it.url }.toSet(), "").toSet())
        assertEquals("العيون الكوشي", quran.first().name)
        assertTrue(tajwid.all { it.url.endsWith("_mojawwad") })
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
        assertEquals(moroccan, allMoroccan.last { Stations.region(it) == null })
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
                "Medi 1 Radio", "Chada FM", "Radio Mars", "Radio 2M", "Radio Amazighe", "Medina FM", "Chaîne Inter"),
            morocco.take(12).map { it.name },
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
        assertEquals("محمد السادس للقرآن الكريم", stations.first().name)
        assertEquals(mfm, stations[3])
        assertTrue(stations.indexOf(high) > stations.indexOfFirst { it.name == "Chaîne Inter" })
        assertTrue(stations.indexOf(high) < stations.indexOf(curatedDance))
        assertTrue(stations.indexOf(curatedDance) < stations.indexOf(low))
        assertEquals("https://udance.ice.infomaniak.ch/udance-128.aac", curatedDance.url)
    }

    @Test fun radioFamiliesAreSearchableAndSortIntoContiguousGroups() {
        val morocco = Stations.visible(StationTab.MOROCCO, emptySet(), "", sort = StationSort.GROUPS)
        assertEquals(StationGroup.entries, morocco.map(Stations::group).distinct())
        assertEquals(9, Stations.visible(StationTab.MOROCCO, emptySet(), "hit radio").size)
        assertEquals(14, Stations.visible(StationTab.MOROCCO, emptySet(), "medi1").size)
        assertEquals(2, StationFamilies.medi.count(Stations::isHls))
        val oldJazz = RadioStation("Medi1 Radio - Jazz", "https://audio.example/jazz", countryCode = "MA")
        assertEquals(1, Stations.visible(StationTab.MOROCCO, emptySet(), "jazz", directory = listOf(oldJazz)).size)
        assertEquals(listOf(oldJazz), Stations.visible(StationTab.MOROCCO, setOf(oldJazz.url), "", true, bookmarks = listOf(oldJazz)))
        val ascending = Stations.visible(StationTab.MOROCCO, emptySet(), "", sort = StationSort.NAME_ASC)
        val descending = Stations.visible(StationTab.MOROCCO, emptySet(), "", sort = StationSort.NAME_DESC)
        assertEquals(ascending.reversed(), descending)
        assertFalse(StationSort.GROUPS in StationSort.options(StationTab.WORLD))
    }

    @Test fun regionalStationsStayAtTheEndAndAreSearchableByRegion() {
        val capFromDirectory = RadioStation("CAP RADIO", "https://audio.example/cap", countryCode = "MA")
        val snrtRegional = Stations.snrt - Stations.snrtNational.toSet()
        assertEquals(11, snrtRegional.size)
        snrtRegional.forEach { station ->
            assertEquals(StationGroup.REGIONAL, Stations.group(station))
            assertTrue(Stations.isHls(station))
            val directory = Stations.streamUrl(station).removePrefix("https://${Stations.SNRT_STREAM_HOST}/live/")
                .removeSuffix("/playlist.m3u8")
            assertTrue(directory in Stations.snrtStreamDirectories)
            assertEquals(null, BroadcastProgrammes.source(station))
        }
        val expected = setOf("Cap Radio", "Radio Tanger Med", "Marrakech Plus", "Skyrock Casablanca") +
            snrtRegional.map { it.name }
        for (sort in listOf(StationSort.DEFAULT, StationSort.GROUPS)) {
            val stations = Stations.visible(StationTab.MOROCCO, emptySet(), "", directory = listOf(capFromDirectory), sort = sort)
            assertEquals(expected, stations.takeLast(expected.size).map { it.name }.toSet())
            assertTrue(stations.dropLast(expected.size).none { Stations.region(it) != null })
        }
        val tanger = Stations.visible(StationTab.MOROCCO, emptySet(), "Tétouan")
        assertEquals(setOf("Cap Radio", "Radio Tanger Med", "SNRT Radio Al Hoceïma", "SNRT Radio Tanger", "SNRT Radio Tétouan"),
            tanger.map { it.name }.toSet())
        assertEquals("SNRT Radio Oujda", Stations.visible(StationTab.MOROCCO, emptySet(), "وجدة").single().name)
        assertEquals(tanger, Stations.visible(StationTab.MOROCCO, tanger.map { it.url }.toSet(), "Tétouan", true))
    }

    @Test fun arabicMusicGroupsAndOldFayrouzFavoriteResolveToTheCuratedStream() {
        val arabic = Stations.visible(StationTab.MOROCCO, emptySet(), "", category = MoroccoCategory.ARABIC)
        assertEquals(listOf(MoroccoCategory.FAYROUZ, MoroccoCategory.TARAB, MoroccoCategory.CHAABI,
            MoroccoCategory.ANDALOU, MoroccoCategory.MOROCCAN), arabic.map(Stations::musicCategory).distinct())
        val fayrouz = Stations.visible(StationTab.MOROCCO, emptySet(), "Fayroz").single()
        assertEquals("Fayrouz · فيروزيات", fayrouz.name)
        assertEquals(fayrouz, Stations.visible(StationTab.FAVORITES, setOf(fayrouz.url), "").single())
        assertEquals(3, Stations.visible(StationTab.MOROCCO, emptySet(), "", category = MoroccoCategory.TARAB).size)
        assertEquals(2, Stations.visible(StationTab.MOROCCO, emptySet(), "", category = MoroccoCategory.AMAZIGH).size)
    }

    @Test fun removedDirectoryEntriesDoNotReappearInFavoritesOrRecentPlayback() {
        val aziz = RadioStation("Aziz Mustaphi", "https://audio.example/static.mp3", countryCode = "MA")
        val azrou = RadioStation("Radio Azrou", "https://audio.example/azrou", countryCode = "MA")
        val old = listOf(aziz, azrou)
        assertEquals(listOf(azrou), StationDirectory.decode(StationDirectory.encode(old)))
        assertEquals(listOf(azrou), Stations.visible(StationTab.MOROCCO, emptySet(), "", directory = old,
            category = MoroccoCategory.DIRECTORY))
        assertEquals(listOf(azrou), Stations.visible(StationTab.FAVORITES, old.map { it.url }.toSet(), "", bookmarks = old))
        val plus = Stations.visible(StationTab.MOROCCO, emptySet(), "Radio Plus").single()
        assertEquals("https://stream-158.zeno.fm/bqdbb6hd0neuv", plus.url)
        assertEquals("https://hosting.studioradiomedia.fr:3040/stream", Stations.streamUrl(plus))
        assertFalse(Stations.isHls(plus))
    }

    @Test fun favoritesFirstPromotesRegionalStationsAcrossTheWholeList() {
        val regional = Stations.snrt.last()
        val morocco = Stations.visible(StationTab.MOROCCO, setOf(regional.url), "", sort = StationSort.FAVORITES_FIRST)
        assertEquals(regional, morocco.first())
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
        val results = Stations.visible(StationTab.QURAN, emptySet(), "Badr Turki")
        val badr = results.single { Stations.quranSection(it) == QuranSection.MURATTAL }
        assertEquals("بدر التركي", badr.name)
        assertEquals(3, results.size)
        assertEquals(results, Stations.visible(StationTab.QURAN, emptySet(), "بَدْر التُّرْكِي"))
        val abdelbasetResults = Stations.visible(StationTab.QURAN, emptySet(), "Abdelbaset")
        assertEquals(listOf(QuranSection.MURATTAL, QuranSection.TAJWID),
            abdelbasetResults.mapNotNull(Stations::quranSection))
        val abdelbaset = abdelbasetResults.single { Stations.quranSection(it) == QuranSection.TAJWID }
        assertTrue(abdelbaset.url.endsWith("abdulbasit_abdulsamad_mojawwad"))
        assertTrue(Stations.subtitle(abdelbaset).startsWith("المصحف المجوّد"))
        val previousFavorite = "https://backup.qurango.net/radio/mohammed_siddiq_alminshawi_mojawwad"
        assertEquals(previousFavorite, Stations.visible(StationTab.FAVORITES, setOf(previousFavorite), "").single().url)
    }

    @Test fun haramRecordingsAreSearchableAndKeepThePrayerAndDate() {
        val haram = Stations.visible(StationTab.QURAN, emptySet(), "تلاوات الحرم")
        assertEquals(6, haram.size)
        assertTrue(haram.all { Stations.isRecording(it) && "/makkah/" in it.url && it.url.endsWith(".mp3") })
        assertFalse(Stations.isRecording(Stations.visible(StationTab.QURAN, emptySet(), "Badr Turki").first()))
        for (prayer in listOf("الفجر", "المغرب", "العشاء")) {
            val results = Stations.visible(StationTab.QURAN, emptySet(), "صلاة $prayer")
            assertEquals(2, results.size)
            assertTrue(results.all { Stations.subtitle(it).startsWith("صلاة $prayer · المسجد الحرام · 2026-") })
        }
        assertEquals("بدر التركي · سورة التين", Stations.visible(StationTab.QURAN, emptySet(), "سورة التين").single().name)
        val badr = Stations.visible(StationTab.QURAN, emptySet(), "Badr Turki").filter(Stations::isRecording)
        assertEquals(2, badr.size)
        assertTrue(badr.all { Stations.reciterName(it) == "بدر التركي" })
        assertEquals(badr, Stations.visible(StationTab.FAVORITES, badr.map { it.url }.toSet(), ""))
    }

    @Test fun recurringRecitersUseTheSamePortraitAcrossSections() {
        val badr = Stations.visible(StationTab.QURAN, emptySet(), "Badr Turki").first()
        val haram = Stations.visible(StationTab.QURAN, emptySet(), "").filter {
            Stations.quranSection(it) == QuranSection.HARAM
        }
        assertTrue(haram.all { ReciterPortraits.forStation(it) != null })
        val portrait = requireNotNull(ReciterPortraits.forStation(badr))
        assertTrue(haram.filter { Stations.reciterName(it) == badr.name }.all { ReciterPortraits.forStation(it) == portrait })
    }

    @Test fun herrazUsesThePublishedWarshCollectionAndMatchesTheRequestedSpelling() {
        val herraz = Stations.visible(StationTab.QURAN, emptySet(), "Hicham Hezzaz").single()
        assertEquals("هشام الهراز", herraz.name)
        assertEquals(herraz, Stations.visible(StationTab.QURAN, emptySet(), "هشام لحراز").single())
        assertEquals(herraz, CarRadioLibrary.stationForQuery("Hicham Hezzaz", null))
        assertEquals(QuranSection.MURATTAL, Stations.quranSection(herraz))
        assertTrue(Stations.isRecording(herraz))
        assertTrue(Stations.subtitle(herraz).contains("ورش عن نافع"))
        val chapters = HerrazRecitations.chapters
        assertEquals((1..114).filterNot { it == 33 }, chapters.map { it.number })
        assertEquals("الفاتحة", chapters.first().name)
        assertTrue(chapters.last().url.endsWith("/114.mp3"))
    }

    @Test fun quranSectionsAreOrderedAndLegacyTabsRestoreToQuran() {
        val quran = Stations.visible(StationTab.QURAN, emptySet(), "")
        assertEquals(QuranSection.entries, quran.mapNotNull(Stations::quranSection).distinct())
        for (section in QuranSection.entries) {
            assertEquals(quran.filter { Stations.quranSection(it) == section },
                Stations.visible(StationTab.QURAN, emptySet(), section.label))
        }
        val endOfMurattal = quran.last { Stations.quranSection(it) == QuranSection.MURATTAL }
        val firstTajwid = quran.first { Stations.quranSection(it) == QuranSection.TAJWID }
        assertEquals(firstTajwid, RadioQueue.neighbor(quran, endOfMurattal.url, 1))
        assertEquals("فارس عباد", Stations.visible(StationTab.QURAN, emptySet(), "Fares Abbad").single().name)
        assertEquals(StationTab.QURAN, StationTab.restore("TAJWID"))
        assertEquals(StationTab.QURAN, StationTab.restore("HARAM"))
        assertEquals(StationTab.WORLD, StationTab.restore("WORLD"))
    }

    @Test fun carStationSelectionKeepsTheBrowsedCategoryForSkip() {
        val snrt = Stations.snrtNational.first()
        val selected = requireNotNull(CarRadioLibrary.selection("station/snrt/${snrt.url}", emptyList()))
        assertEquals(snrt, selected.station)
        assertEquals(Stations.snrt.toSet(), selected.queue.toSet())
        assertTrue(RadioQueue.neighbor(selected.queue, snrt.url, 1) in Stations.snrt)
        assertNull(CarRadioLibrary.selection("station/TAJWID/${snrt.url}", emptyList()))
    }

    @Test fun carSearchAndFavoritesResolveOnlyAvailableStations() {
        val med = Stations.all.first { it.name == "Med Radio" }
        val bookmark = RadioStation("Radio préférée", "https://example.org/favorite", countryCode = "FR")
        val favorites = CarRadioLibrary.favorites(setOf(bookmark.url, med.url, Stations.luxe.url), listOf(bookmark))
        assertEquals(listOf(med, bookmark), favorites)
        assertEquals(favorites, CarRadioLibrary.selection("station/favorites/${bookmark.url}", favorites)?.queue)
        assertNull(CarRadioLibrary.selection(bookmark.url, emptyList()))
        assertEquals(bookmark, CarRadioLibrary.stationForQuery("", bookmark.url, favorites))
        assertEquals(listOf(bookmark), CarRadioLibrary.search("radio preferee", favorites))
        assertEquals(listOf(med), CarRadioLibrary.search("Med Radio", emptyList()).filter { it.name == med.name })
        assertTrue(CarRadioLibrary.search("... ", favorites).isEmpty())
        assertNull(CarRadioLibrary.stationForQuery(" ", Stations.luxe.url)?.takeIf { it == Stations.luxe })
    }
}
