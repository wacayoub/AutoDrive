package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioStation
import com.autodrive.launcher.readUpTo
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URI
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.ZoneId

internal object StationWebsites {
    private const val ASWAT = "https://radioaswat.ma/"
    private const val MAX_BYTES = 512 * 1024
    private val pages = mapOf(
        "Med Radio" to "https://medradio.ma/",
        "Radio Aswat" to ASWAT,
        "Chada FM" to "https://chada.ma/",
        "Medina FM" to "https://medinafm.ma/",
        "U Radio" to "https://uradio.ma/",
        "U Dance" to "https://uradio.ma/",
        "Radio 2M" to "https://radio2m.ma/",
        "Ness Radio" to "https://nessradio.com/",
        "Cap Radio" to "https://capradiotv.com/",
        "Radio Tanger Med" to "https://www.radiotangermed.com/",
        "Marrakech Plus" to "https://www.marrakechplus.ma/",
        "Radio Manarat" to "https://fm6education.ma/prestations/culture-education-et-formation/radio-manarat/",
    )
    private val dayLabels = listOf("الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
    private val timetable = Regex("""(?:[01][0-9]|2[0-3]):[0-5][0-9]\s+(?:[01][0-9]|2[0-3]):[0-5][0-9]""")

    fun forStation(station: RadioStation): String? {
        if (station !in Stations.all) return null
        return when {
            station in Stations.snrtNational -> station.url
            station in Stations.snrt -> "https://snrtlive.ma/"
            station in StationFamilies.medi -> "https://www.medi1.com/"
            station in StationFamilies.hit || station.name == "HIT RADIO" -> "https://www.hitradio.ma/"
            station.name.startsWith("Medina FM ") -> pages["Medina FM"]
            else -> pages[station.name]
        }
    }

    fun information(station: RadioStation): String? {
        val site = forStation(station) ?: return null
        BroadcastProgrammes.source(station)?.let { source ->
            runCatching { BroadcastProgrammes.fetch(source) }.getOrNull()?.let {
                return listOfNotNull(it.title, it.hours, it.source).joinToString("\n")
            }
        }
        val connection = URI(site).toURL().openConnection() as HttpURLConnection
        val html = try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 4_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("User-Agent", "RadioMarocCoran")
            if (connection.responseCode != 200 || connection.contentType?.startsWith("text/html") != true) return null
            val data = connection.inputStream.use { it.readUpTo(MAX_BYTES + 1) }
            if (data.size > MAX_BYTES) return null
            data.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
        if (site == ASWAT) {
            val day = ZonedDateTime.now(ZoneId.of("Africa/Casablanca")).dayOfWeek
            aswatSchedule(html, day)?.let { return it }
        }
        return Jsoup.parse(html).selectFirst("meta[property=og:description], meta[name=description]")
            ?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }?.take(400)
    }

    fun aswatSchedule(html: String, day: DayOfWeek): String? {
        val page = Jsoup.parse(html)
        val label = dayLabels[day.value - 1]
        val id = page.select(".home-tabs-holder .tab-nav a").firstOrNull { it.text() == label }
            ?.attr("href")?.takeIf { it.startsWith("#tabs-") }?.drop(1) ?: return null
        val entries = page.getElementById(id)?.select(".outer")?.mapNotNull { item ->
            val time = item.selectFirst("time")?.text()?.takeIf(timetable::matches) ?: return@mapNotNull null
            val title = item.selectFirst("h2")?.text()?.takeIf { it.isNotBlank() && it.length <= 160 }
                ?: return@mapNotNull null
            val hours = time.replace(Regex("\\s+"), " – ")
            "\u2066$hours\u2069  ·  $title"
        }.orEmpty()
        return entries.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "Grille du jour · $label\n\n")
    }
}
