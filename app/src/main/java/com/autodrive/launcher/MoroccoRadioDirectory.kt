package com.autodrive.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Public directory metadata only. Streams remain on their publishers' servers. */
internal class MoroccoRadioDirectory(context: Context) {
    private val cache = File(context.filesDir, "morocco-radio-directory.json")
    private val userAgent = "AutoDrive/" + context.packageManager.getPackageInfo(context.packageName, 0).versionName
    fun cached(): List<RadioStation> = runCatching {
        if (cache.length() > MAX_BYTES) return@runCatching emptyList()
        val rows = JSONArray(cache.readText())
        (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            RadioStation.validUrl(row.optString("url"))?.let { RadioStation(row.optString("name").take(60), it,
                logoUrl = RadioStation.validUrl(row.optString("logo")) ?: RadioStation.defaultLogo(it), tags = RadioCatalogue.parseTags(row.optString("tags"))) }
        }.filter { it.name.isNotBlank() }.map(RadioCatalogue::polish).distinctBy { it.url }.take(300)
    }.getOrDefault(emptyList())

    fun needsRefresh(): Boolean = runCatching {
        !cache.exists() || System.currentTimeMillis() - cache.lastModified() > 24 * 60 * 60 * 1000L ||
            JSONArray(cache.readText()).let { rows -> (0 until rows.length()).any { rows.optJSONObject(it)?.let { !it.has("logo") || !it.has("tags") } != false } }
    }.getOrDefault(true)

    fun fetch(): List<RadioStation> {
        val result = RadioBrowser.read("stations/bycountrycodeexact/MA?hidebroken=true&order=clickcount&reverse=true&limit=300", userAgent) {
            parse(it).also { rows -> check(rows.isNotEmpty()) { "No compatible stations" } }
        }
        val temporary = File(cache.parentFile, cache.name + ".tmp")
        temporary.writeText(JSONArray(result.map { JSONObject().put("name", it.name).put("url", it.url).put("logo", it.logoUrl.orEmpty()).put("tags", it.tags.joinToString(",")) }).toString())
        check(temporary.renameTo(cache)) { "Directory cache update failed" }
        return result
    }
    companion object {
        // Public live URLs used by each broadcaster's own website; useful if the directory is unavailable.
        val starter = listOf(
            RadioStation("HIT RADIO", "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3", logoUrl = "https://hitradio.ma/small-logo-hitradio.png"),
            RadioStation("Radio Mars", "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3"),
            RadioStation("Radio Aswat", "https://broadcast.ice.infomaniak.ch/aswat-high.mp3")
        )
        private const val MAX_BYTES = 2 * 1024 * 1024
        fun parse(json: String): List<RadioStation> {
            val rows = JSONArray(json)
            return (0 until minOf(rows.length(), 300)).mapNotNull { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNull null
                if (row.optString("countrycode") != "MA" || row.optInt("lastcheckok") != 1) return@mapNotNull null
                val url = RadioStation.validUrl(row.optString("url_resolved")) ?: return@mapNotNull null
                val name = row.optString("name").trim().take(60).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                RadioStation(name, url, logoUrl = RadioStation.validUrl(row.optString("favicon")) ?: RadioStation.defaultLogo(url), tags = RadioCatalogue.parseTags(row.optString("tags")))
            }.groupBy { it.url }.values.map { duplicates ->
                RadioCatalogue.polish(duplicates.first().copy(logoUrl = duplicates.firstNotNullOfOrNull { it.logoUrl }, tags = duplicates.flatMap { it.tags }.distinct().take(24).toSet()))
            }
        }
    }
}
