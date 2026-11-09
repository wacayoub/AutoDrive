package com.autodrive.radiomaroc

import android.content.Context
import android.util.AtomicFile
import com.autodrive.launcher.RadioBrowser
import com.autodrive.launcher.RadioCatalogue
import com.autodrive.launcher.RadioStation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.text.Collator
import java.util.Locale

internal class StationDirectory(context: Context) {
    data class Query(val country: String, val name: String)
    data class Page(val stations: List<RadioStation>, val nextOffset: Int?)

    private val folder = File(context.filesDir, "radio-directory")
    private val userAgent = "RadioMarocCoran/" + context.packageManager.getPackageInfo(context.packageName, 0).versionName

    fun cached(country: String): List<RadioStation> = synchronized(cacheLock) {
        runCatching {
            val bytes = cache(country).openRead().use { it.readNBytes(CACHE_LIMIT + 1) }
            if (bytes.size > CACHE_LIMIT) emptyList() else decode(bytes.toString(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    fun fetch(query: Query, offset: Int): Page {
        require(query.country in countries)
        val name = URLEncoder.encode(query.name.trim().take(80), Charsets.UTF_8)
        val path = "stations/search?countrycode=${query.country}&name=$name&hidebroken=true" +
            "&order=clickcount&reverse=true&limit=$PAGE_SIZE&offset=$offset"
        val page = RadioBrowser.read(path, userAgent) { parsePage(it, query.country, offset) }
        if (offset == 0 && query.name.isBlank() && page.stations.isNotEmpty()) {
            synchronized(cacheLock) {
                runCatching {
                    val file = cache(query.country)
                    folder.mkdirs()
                    val stream = file.startWrite()
                    try {
                        stream.write(encode(page.stations).toByteArray())
                        file.finishWrite(stream)
                    } catch (error: Exception) {
                        file.failWrite(stream)
                        throw error
                    }
                }
            }
        }
        return page
    }

    private fun cache(country: String): AtomicFile {
        require(country in countries)
        return AtomicFile(File(folder, "$country.json"))
    }

    companion object {
        private const val PAGE_SIZE = 100
        private const val CACHE_LIMIT = 512 * 1024
        private val cacheLock = Any()
        val countries: List<String> = (Locale.getISOCountries().toList() + "XK")
            .sortedWith(compareBy(Collator.getInstance(Locale.FRENCH), ::countryName))

        fun countryName(code: String): String =
            if (code == "XK") "Kosovo" else Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.FRENCH)

        fun parsePage(json: String, country: String, offset: Int): Page {
            val rows = JSONArray(json)
            val stations = (0 until minOf(rows.length(), PAGE_SIZE)).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                if (row.optString("countrycode") != country || row.optInt("lastcheckok") != 1) return@mapNotNull null
                val url = RadioStation.validUrl(row.optString("url_resolved"))
                    ?: RadioStation.validUrl(row.optString("url")) ?: return@mapNotNull null
                val name = RadioCatalogue.cleanName(row.optString("name").take(512)).ifBlank { return@mapNotNull null }
                RadioCatalogue.polish(RadioStation(name, url, logoUrl = RadioStation.validUrl(row.optString("favicon")),
                    tags = RadioCatalogue.parseTags(row.optString("tags")), countryCode = country,
                    isHls = row.optInt("hls") == 1))
            }.distinctBy { it.url }
            return Page(stations, if (rows.length() >= PAGE_SIZE) offset + PAGE_SIZE else null)
        }

        fun encode(stations: List<RadioStation>): String = JSONArray(stations.map {
            JSONObject().put("name", it.name).put("url", it.url).put("country", it.countryCode)
                .put("logo", it.logoUrl.orEmpty()).put("tags", it.tags.joinToString(",")).put("hls", it.isHls)
        }).toString()

        fun decode(json: String): List<RadioStation> = runCatching {
            val rows = JSONArray(json)
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val name = RadioCatalogue.cleanName(row.optString("name").take(512)).ifBlank { return@mapNotNull null }
                val url = RadioStation.validUrl(row.optString("url")) ?: return@mapNotNull null
                val country = row.optString("country").takeIf { it in countries } ?: return@mapNotNull null
                RadioStation(name, url, logoUrl = RadioStation.validUrl(row.optString("logo")),
                    tags = RadioCatalogue.parseTags(row.optString("tags")), countryCode = country,
                    isHls = row.optBoolean("hls"))
            }.distinctBy { it.url }
        }.getOrDefault(emptyList())
    }
}
