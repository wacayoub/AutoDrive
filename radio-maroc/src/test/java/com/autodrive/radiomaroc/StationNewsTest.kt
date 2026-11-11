package com.autodrive.radiomaroc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class StationNewsTest {
    @Test fun bannerShowsOnlyUniqueHeadlinesFromTheSelectedStation() {
        val station = NewsHeadline("Titre de la station", "https://radiomars.ma/a", null, null, NewsPublisher.RADIO_MARS)
        val other = NewsHeadline("Autre station", "https://snrtnews.com/article/b", null, null, NewsPublisher.SNRT)
        val rfi = NewsHeadline("Titre RFI", "https://www.rfi.fr/fr/monde/a", null, null)
        val headlines = listOf(station, rfi, other, station)

        assertEquals(listOf(NewsBulletin("MARS", station.title, station.url)),
            StationNews.bulletins(NewsPublisher.RADIO_MARS, headlines))
        assertEquals(emptyList<NewsBulletin>(), StationNews.bulletins(null, headlines))
        assertEquals(emptyList<NewsBulletin>(), StationNews.bulletins(NewsPublisher.RFI, headlines))
    }

    @Test fun readsMedi1TitlesAndAuthorisedPhotos() {
        val headlines = StationNews.parse(NewsPublisher.MEDI1, """[
          {"id":"415345","titre":"Nouvelles du monde","langue":"fr","image":"https://img.medi1tv.com/photo.webp",
           "date":"11/11/2026","heure":"17:36"},
          {"id":"415343","titre":"Autre titre","langue":"fr","image":"https://img.medi1tv.com.example/photo.webp",
           "date":"11/11/2026","heure":"16:26"}
        ]""")
        assertEquals(NewsHeadline("Nouvelles du monde", "https://www.medi1news.com/fr/article/415345.html",
            Instant.parse("2026-11-11T17:36:00Z"), null, NewsPublisher.MEDI1, "https://img.medi1tv.com/photo.webp"), headlines.first())
        assertNull(headlines.last().imageUrl)
    }

    @Test fun prefersPublishedNewsOverDraftsAndForeignLinks() {
        val html = """
          <script type="application/json" id="ng-state">{"section":{"b":[
           {"article":{"status":"draft","url":"/article/old","title":"Brouillon"}},
           {"article":{"status":"published","url":"https://other.example/article/news","title":"Extérieur"}},
           {"article":{"status":"published","url":"/article/news","title":"Titre","date_publication":"2026-11-11T16:00:00",
            "image":"https://cdn.snrtnews.com/news.webp","description_article":"<p>Résumé du jour</p>"}}
          ]}}</script>
        """.trimIndent()
        assertEquals(listOf(NewsHeadline("Titre", "https://snrtnews.com/article/news",
            Instant.parse("2026-11-11T16:00:00Z"), "Résumé du jour", NewsPublisher.SNRT,
            "https://cdn.snrtnews.com/news.webp")), StationNews.parse(NewsPublisher.SNRT, html))
    }
}
