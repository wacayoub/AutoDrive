package com.autodrive.radiomaroc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RadioNewsTest {
    @Test fun rotationKeepsRfiAlongsideStationNewsAndSkipsCrossSectionDuplicates() {
        val world = NewsHeadline("Monde", "https://www.rfi.fr/fr/monde/a", null, null)
        val sport = NewsHeadline("Sport", "https://www.rfi.fr/fr/sports/b", null, null)
        val africa = NewsHeadline("Afrique", "https://www.rfi.fr/fr/afrique/c", null, null)
        val station = listOf(
            NewsHeadline("Premier titre", "https://radiomars.ma/a", null, null, NewsPublisher.RADIO_MARS),
            NewsHeadline("Deuxième titre", "https://radiomars.ma/b", null, null, NewsPublisher.RADIO_MARS),
        )
        val feeds = mapOf(
            NewsTopic.WORLD to listOf(world),
            NewsTopic.SPORT to listOf(sport),
            NewsTopic.AFRICA to listOf(world, africa),
        )

        assertEquals(listOf(station[0].url, world.url, station[1].url, sport.url, africa.url),
            RadioNews.rotation(station, feeds).map { it.url })
        assertEquals(listOf("RFI", "RFI", "RFI"), RadioNews.rotation(emptyList(), feeds).map { it.source })
    }

    @Test fun parsesOnlyPublishedHeadlinesAndKeepsPlainTextSummaries() {
        val xml = """
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>RFI</title>
              <item>
                <title><![CDATA[Maroc & Europe]]></title>
                <link>https://www.rfi.fr/fr/afrique/actualite</link>
                <pubDate>Wed, 11 Nov 2026 15:20:50 GMT</pubDate>
                <description><![CDATA[<p>Le point sur <strong>l’actualité</strong>.</p>]]></description>
                <media:thumbnail url="https://s.rfi.fr/media/article.jpg"/>
              </item>
              <item><title>Doublon</title><link>https://www.rfi.fr/fr/afrique/actualite</link></item>
              <item><title>Lien étranger</title><link>https://www.rfi.fr.example/fr/sport</link></item>
              <item><title>Lien non sécurisé</title><link>http://www.rfi.fr/fr/sport</link></item>
              <item><title>Lien non éditorial</title><link>https://www.rfi.fr/fr/../es/sport</link></item>
            </channel></rss>
        """.trimIndent()
        assertEquals(listOf(NewsHeadline("Maroc & Europe", "https://www.rfi.fr/fr/afrique/actualite",
            Instant.parse("2026-11-11T15:20:50Z"), "Le point sur l’actualité.",
            imageUrl = "https://s.rfi.fr/media/article.jpg")), RadioNews.parse(xml))
    }

    @Test fun capsTheHeadlinesAndHandlesMissingDates() {
        val items = (1..12).joinToString("") { index ->
            "<item><title>Titre $index</title><link>https://www.rfi.fr/fr/monde/$index</link></item>"
        }
        val headlines = RadioNews.parse("<rss><channel>$items</channel></rss>")
        assertEquals(8, headlines.size)
        assertEquals("Titre 1", headlines.first().title)
        assertNull(headlines.first().publishedAt)
        assertNull(headlines.first().summary)
        assertEquals(emptyList<NewsHeadline>(), RadioNews.parse("<html><h1>Page indisponible</h1></html>"))
    }
}
