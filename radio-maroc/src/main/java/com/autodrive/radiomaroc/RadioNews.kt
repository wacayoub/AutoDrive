package com.autodrive.radiomaroc

import com.autodrive.launcher.readUpTo
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal enum class NewsTopic(val label: String, private val section: String) {
    WORLD("Monde", "monde"),
    SPORT("Sport", "sports"),
    AFRICA("Afrique", "afrique"),
    ECONOMY("Économie", "%C3%A9conomie"),
    CULTURE("Culture", "culture-m%C3%A9dias");

    val feedUrl: String get() = "https://www.rfi.fr/fr/$section/rss"
}

internal data class NewsHeadline(
    val title: String,
    val url: String,
    val publishedAt: Instant?,
    val summary: String?,
    val publisher: NewsPublisher = NewsPublisher.RFI,
    val imageUrl: String? = null,
)

internal data class NewsBulletin(val source: String, val text: String, val url: String? = null)

internal object RadioNews {
    private const val MAX_BYTES = 128 * 1024
    private const val MAX_HEADLINES = 8

    fun rotation(
        station: List<NewsHeadline>,
        rfiFeeds: Map<NewsTopic, List<NewsHeadline>>,
    ): List<NewsBulletin> {
        val stationBulletins = station.distinctBy { it.url }.take(6)
            .map { NewsBulletin(it.publisher.badge, it.title, it.url) }
        val displayedUrls = stationBulletins.mapTo(mutableSetOf()) { it.url }
        val rfiBulletins = (0 until 3).flatMap { slot ->
            NewsTopic.entries.mapNotNull { topic ->
                rfiFeeds[topic]?.getOrNull(slot)?.takeIf { displayedUrls.add(it.url) }
                    ?.let { NewsBulletin("RFI", "${topic.label} · ${it.title}", it.url) }
            }
        }
        return buildList {
            for (index in 0 until maxOf(stationBulletins.size, rfiBulletins.size)) {
                stationBulletins.getOrNull(index)?.let(::add)
                rfiBulletins.getOrNull(index)?.let(::add)
            }
        }
    }

    fun fetch(topic: NewsTopic): List<NewsHeadline> {
        val connection = URI(topic.feedUrl).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 4_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("User-Agent", "RadioMarocCoran/2.5.17")
            connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            val bytes = connection.inputStream.use { it.readUpTo(MAX_BYTES + 1) }
            if (bytes.size > MAX_BYTES) return emptyList()
            return parse(bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    fun parse(xml: String): List<NewsHeadline> =
        Jsoup.parse(xml, "", Parser.xmlParser()).select("rss > channel > item")
            .asSequence()
            .mapNotNull { item ->
                val title = item.selectFirst("> title")?.text()?.takeIf { it.isNotBlank() && it.length <= 280 }
                    ?: return@mapNotNull null
                val url = articleUrl(item.selectFirst("> link")?.text().orEmpty()) ?: return@mapNotNull null
                val date = item.selectFirst("> pubDate")?.text()?.let {
                    runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
                }
                val summary = item.selectFirst("> description")?.text()?.let {
                    Jsoup.parseBodyFragment(it).text().takeIf(String::isNotBlank)
                }?.let { if (it.length <= 420) it else it.take(420).substringBeforeLast(' ') + "…" }
                val photo = item.selectFirst("media|thumbnail")?.attr("url")
                    ?.let { StationNews.publisherUrl(it, setOf("s.rfi.fr")) }
                NewsHeadline(title, url, date, summary, imageUrl = photo)
            }
            .distinctBy { it.url }
            .take(MAX_HEADLINES)
            .toList()

    private fun articleUrl(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        return value.takeIf {
            uri.scheme == "https" && uri.host == "www.rfi.fr" && uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443) && uri.normalize().rawPath?.startsWith("/fr/") == true
        }
    }
}
