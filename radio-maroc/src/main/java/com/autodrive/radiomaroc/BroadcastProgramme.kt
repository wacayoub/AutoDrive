package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioStation
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URI

internal data class BroadcastProgramme(val title: String, val source: String, val hours: String? = null)

internal object BroadcastProgrammes {
    private const val RADIO_2M_PAGE = "https://radio2m.ma/"
    private const val RADIO_2M_FEED = "https://bo.radio2m.ma/api/live"
    private const val MAX_BYTES = 512 * 1024
    private val snrtHours = Regex("""(?:[01][0-9]|2[0-3])H[0-5][0-9]\s*-\s*(?:[01][0-9]|2[0-3])H[0-5][0-9]""")

    fun source(station: RadioStation?): String? = when {
        station?.url == RADIO_2M_PAGE -> RADIO_2M_FEED
        station in Stations.snrt -> station?.url
        else -> null
    }

    fun fetch(source: String): BroadcastProgramme? {
        if (source != RADIO_2M_FEED && Stations.snrt.none { it.url == source }) return null
        val connection = URI(source).toURL().openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 6_000
            connection.readTimeout = 7_000
            connection.setRequestProperty("User-Agent", "RadioMarocCoran")
            connection.setRequestProperty("Cache-Control", "no-cache")
            if (connection.responseCode != 200) return null
            val data = connection.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
            if (data.size > MAX_BYTES) return null
            val text = data.toString(Charsets.UTF_8)
            if (source == RADIO_2M_FEED) radio2m(text) else snrt(text)
        } finally {
            connection.disconnect()
        }
    }

    fun snrt(html: String): BroadcastProgramme? {
        val badge = Jsoup.parse(html).select(".genre-first > .badge.badge-danger")
            .singleOrNull { it.text().equals("En direct", ignoreCase = true) } ?: return null
        val item = badge.parent()?.parent() ?: return null
        val title = cleanTitle(item.selectFirst("h2.program-title-sm")?.text()) ?: return null
        val hours = item.selectFirst("p.mt-3")?.text()?.takeIf(snrtHours::matches)
        return BroadcastProgramme(title, "Programme SNRT Live", hours)
    }

    fun radio2m(json: String): BroadcastProgramme? {
        val result = JSONObject(json)
        if (!result.optBoolean("success")) return null
        val emission = result.optJSONObject("data")?.optJSONObject("data")?.optJSONObject("emmision")
        val title = cleanTitle(emission?.opt("name") as? String) ?: return null
        return BroadcastProgramme(title, "Programme Radio 2M")
    }

    fun fromStream(station: RadioStation, title: CharSequence?): BroadcastProgramme? =
        cleanTitle(title?.toString())?.takeUnless { it.equals(station.name, ignoreCase = true) }
            ?.let { BroadcastProgramme(it, "Info du flux") }

    private fun cleanTitle(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() && it.length <= 160 }
}
