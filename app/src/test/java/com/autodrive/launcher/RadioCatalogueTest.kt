package com.autodrive.launcher

import org.junit.Assert.*
import org.junit.Test

class RadioCatalogueTest {
    @Test fun selectionHasFourOfficialSnrtPlayersAndTwelveDistinctReciters() {
        val all = RadioCatalogue.featured
        assertEquals(16, all.size)
        assertEquals(16, all.map { it.url }.distinct().size)
        val snrt = all.filter { RadioCatalogue.matches(it, RadioCategory.SNRT) }
        assertEquals(4, snrt.size)
        assertTrue(snrt.all { RadioCatalogue.officialPlayer(it)?.startsWith("https://snrtlive.ma/fr/") == true })
        val quran = all.filter { RadioCatalogue.matches(it, RadioCategory.QURAN) }
        assertEquals(13, quran.size)
        assertEquals(12, quran.count { it.url.startsWith("https://backup.qurango.net/radio/") })
        assertTrue(quran.all { it.name.any { char -> char in '\u0600'..'\u06ff' } })
    }
    @Test fun reciterSearchAcceptsArabicAndLatinNamesWithoutLosingAccentsOrWords() {
        val sudais = RadioCatalogue.featured.single { it.name == "عبد الرحمن السديس" }
        assertTrue(RadioCatalogue.search(sudais, "عبدالرحمن"))
        assertTrue(RadioCatalogue.search(sudais, "السُّدَيْس"))
        assertTrue(RadioCatalogue.search(sudais, "Sudais"))
        assertTrue(RadioCatalogue.search(sudais, "quran"))
        assertFalse(RadioCatalogue.search(sudais, "Alafasy"))
        val inter = RadioCatalogue.featured.single { it.name == "Chaîne Inter" }
        assertTrue(RadioCatalogue.search(inter, "chaine inter"))
    }
    @Test fun cleanNamesRemoveDirectoryNoiseAndKeepPersonalLabelsAndArabic() {
        assertEquals("Radio Exemple", RadioCatalogue.cleanName(" [MA]  Radio_Exemple (128kbps) "))
        assertEquals("Radio Test", RadioCatalogue.cleanName("MA:- Radio   Test 64 kb/s"))
        assertEquals("القرآن الكريم", RadioCatalogue.cleanName("القرآن   الكريم"))
        val aswat = RadioStation("Aswat FM", "https://broadcast.ice.infomaniak.ch/aswat-high.mp3")
        assertEquals("Radio Aswat", RadioCatalogue.polish(aswat).name)
        assertEquals("Ma station préférée", RadioCatalogue.polish(aswat.copy(name = "Ma station préférée")).name)
    }
    @Test fun transportQueueUsesTheVisibleCategoryAndFavorites() {
        val all = RadioCatalogue.featured
        val quran = RadioQueue.active(all, false, RadioCategory.QURAN)
        assertEquals(13, quran.size)
        assertEquals(quran.first(), RadioQueue.neighbor(quran, quran.last().url, 1))
        val favorite = all.mapIndexed { i, s -> s.copy(favorite = i == 0 || i == 5) }
        assertEquals(1, RadioQueue.active(favorite, true, RadioCategory.SNRT).size)
        assertEquals(1, RadioQueue.active(favorite, true, RadioCategory.QURAN).size)
        assertNull(RadioCatalogue.officialPlayer(RadioStation("SNRT impostor", "https://example.invalid/radio")))
    }
}
