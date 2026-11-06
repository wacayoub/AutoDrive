package com.autodrive.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
                logoUrl = RadioStation.validUrl(row.optString("logo")) ?: RadioStation.defaultLogo(it)) }
        }.filter { it.name.isNotBlank() }.map(RadioCatalogue::polish).distinctBy { it.url }.take(300)
    }.getOrDefault(emptyList())

    fun needsRefresh(): Boolean = runCatching {
        !cache.exists() || System.currentTimeMillis() - cache.lastModified() > 24 * 60 * 60 * 1000L ||
            JSONArray(cache.readText()).let { rows -> (0 until rows.length()).any { rows.optJSONObject(it)?.has("logo") != true } }
    }.getOrDefault(true)

    fun fetch(): List<RadioStation> {
        var lastError: Exception? = null
        for (server in listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info")) {
            try {
                val connection = URL("https://$server/json/stations/bycountrycodeexact/MA?hidebroken=true&order=clickcount&reverse=true&limit=300")
                    .openConnection() as HttpURLConnection
                val text = try {
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 10_000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", userAgent)
                    connection.setRequestProperty("Accept", "application/json")
                    check(connection.responseCode == 200) { "Directory unavailable" }
                    connection.inputStream.use { stream ->
                        val bytes = stream.readNBytes(MAX_BYTES + 1)
                        check(bytes.size <= MAX_BYTES) { "Directory too large" }
                        bytes.toString(Charsets.UTF_8)
                    }
                } finally { connection.disconnect() }
                val result = parse(text)
                check(result.isNotEmpty()) { "No compatible stations" }
                val temporary = File(cache.parentFile, cache.name + ".tmp")
                temporary.writeText(JSONArray(result.map { JSONObject().put("name", it.name).put("url", it.url).put("logo", it.logoUrl.orEmpty()) }).toString())
                check(temporary.renameTo(cache)) { "Directory cache update failed" }
                return result
            } catch (error: Exception) { lastError = error }
        }
        throw lastError ?: IllegalStateException("Directory unavailable")
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
                RadioStation(name, url, logoUrl = RadioStation.validUrl(row.optString("favicon")) ?: RadioStation.defaultLogo(url))
            }.groupBy { it.url }.values.map { duplicates ->
                RadioCatalogue.polish(duplicates.first().copy(logoUrl = duplicates.firstNotNullOfOrNull { it.logoUrl }))
            }
        }
    }
}
