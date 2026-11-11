package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioStation
import com.autodrive.launcher.readUpTo
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal enum class NewsPublisher(val label: String, val badge: String, val url: String) {
    RFI("RFI", "RFI", ""),
    SNRT("SNRTnews", "SNRT", "https://snrtnews.com/fr/"),
    MEDI1("Medi1News", "MEDI1", "https://idara.medi1tv.ma/rss/medi1news/latest-news-une.aspx?lg=fr"),
    MED_RADIO("Med Radio", "MED", wordpress("medradio.ma")),
    RADIO_MARS("Radio Mars", "MARS", wordpress("radiomars.ma")),
}

private fun wordpress(host: String) = "https://$host/wp-json/wp/v2/posts?_embed=wp%3Afeaturedmedia&per_page=8"

internal object StationNews {
    private val dateMedi1 = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm")

    fun forStation(station: RadioStation?): NewsPublisher? = when {
        station in Stations.snrt -> NewsPublisher.SNRT
        station in StationFamilies.medi -> NewsPublisher.MEDI1
        station !in Stations.all -> null
        station?.name == "Med Radio" -> NewsPublisher.MED_RADIO
        station?.name == "Radio Mars" -> NewsPublisher.RADIO_MARS
        else -> null
    }

    fun fetch(publisher: NewsPublisher): List<NewsHeadline> {
        require(publisher != NewsPublisher.RFI)
        val limit = 768 * 1024
        val connection = URI(publisher.url).toURL().openConnection() as HttpURLConnection
        val body = try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 6_000
            connection.setRequestProperty("User-Agent", "RadioMarocCoran/2.5.18")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            val bytes = connection.inputStream.use { it.readUpTo(limit + 1) }
            if (bytes.size > limit) return emptyList()
            bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
        val cutoff = Instant.now().minus(14, ChronoUnit.DAYS)
        return parse(publisher, body).filter { it.publishedAt?.let { date -> date >= cutoff } == true }
            .distinctBy { it.url }.sortedByDescending { it.publishedAt }.take(8)
    }

    fun parse(publisher: NewsPublisher, body: String): List<NewsHeadline> = when (publisher) {
        NewsPublisher.SNRT -> parseSnrt(body)
        NewsPublisher.MEDI1 -> parseMedi1(JSONArray(body))
        NewsPublisher.MED_RADIO, NewsPublisher.RADIO_MARS -> parseWordPress(publisher, JSONArray(body))
        NewsPublisher.RFI -> emptyList()
    }

    private fun parseSnrt(html: String): List<NewsHeadline> {
        val json = Jsoup.parse(html).selectFirst("script#ng-state")?.data() ?: return emptyList()
        val state = JSONObject(json)
        return state.keys().asSequence().flatMap { key ->
            state.optJSONObject(key)?.optJSONArray("b").objects().asSequence()
        }.mapNotNull { card ->
            val article = card.optJSONObject("article") ?: return@mapNotNull null
            if (article.optString("status") != "published") return@mapNotNull null
            val url = runCatching { URI("https://snrtnews.com/").resolve(article.optString("url")).toString() }
                .getOrNull()?.let { publisherUrl(it, setOf("snrtnews.com", "www.snrtnews.com")) }
                ?: return@mapNotNull null
            if (!URI(url).path.startsWith("/article/")) return@mapNotNull null
            val title = plain(article.optString("title"), 280) ?: return@mapNotNull null
            NewsHeadline(title, url, gmt(article.optString("date_publication")),
                plain(article.optString("description_article"), 420), NewsPublisher.SNRT,
                publisherUrl(article.optString("image"), setOf("cdn.snrtnews.com")))
        }.toList()
    }

    private fun parseMedi1(rows: JSONArray): List<NewsHeadline> = rows.objects().mapNotNull { item ->
        val id = item.optString("id").takeIf { it.matches(Regex("[0-9]{1,10}")) } ?: return@mapNotNull null
        if (item.optString("langue") != "fr") return@mapNotNull null
        val title = plain(item.optString("titre"), 280) ?: return@mapNotNull null
        val date = gmt("${item.optString("date")} ${item.optString("heure")}", dateMedi1)
        NewsHeadline(title, "https://www.medi1news.com/fr/article/$id.html", date, null,
            NewsPublisher.MEDI1, publisherUrl(item.optString("image"), setOf("img.medi1tv.com", "img.medi1news.com")))
    }

    private fun parseWordPress(publisher: NewsPublisher, rows: JSONArray): List<NewsHeadline> {
        val host = URI(publisher.url).host
        val hosts = setOf(host, "www.$host")
        return rows.objects().mapNotNull { item ->
            val url = publisherUrl(item.optString("link"), hosts) ?: return@mapNotNull null
            val title = plain(item.optJSONObject("title")?.optString("rendered").orEmpty(), 280) ?: return@mapNotNull null
            val image = item.optJSONObject("_embedded")?.optJSONArray("wp:featuredmedia")?.optJSONObject(0)
            val medium = image?.optJSONObject("media_details")?.optJSONObject("sizes")
                ?.optJSONObject("medium")?.optString("source_url")
            val photo = publisherUrl(medium.orEmpty(), hosts) ?: publisherUrl(image?.optString("source_url").orEmpty(), hosts)
            NewsHeadline(title, url, gmt(item.optString("date_gmt")),
                plain(item.optJSONObject("excerpt")?.optString("rendered").orEmpty(), 420), publisher, photo)
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        this?.let { array -> (0 until array.length()).mapNotNull(array::optJSONObject) }.orEmpty()

    private fun plain(html: String, limit: Int): String? =
        Jsoup.parseBodyFragment(html).text().takeIf(String::isNotBlank)?.let {
            if (it.length <= limit) it else it.take(limit).substringBeforeLast(' ') + "…"
        }

    private fun gmt(value: String, format: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME): Instant? =
        runCatching { LocalDateTime.parse(value, format).toInstant(ZoneOffset.UTC) }.getOrNull()

    fun publisherUrl(value: String, hosts: Set<String>): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        return value.takeIf { uri.scheme == "https" && uri.host in hosts && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) && !uri.rawPath.isNullOrBlank() }
    }
}
