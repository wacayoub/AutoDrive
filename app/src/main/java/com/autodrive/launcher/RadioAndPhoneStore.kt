package com.autodrive.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

internal object DialNumber {
    fun normalize(input: String): String? {
        if (input.length > 80 || input.any { it !in "0123456789+*# ()-." }) return null
        val number = input.filter { it in "0123456789+*#" }
        if (number.length !in 1..32 || number.none { it in '0'..'9' }) return null
        if ('+' in number.drop(1)) return null
        return number
    }
}

internal data class RadioStation(val name: String, val url: String, val favorite: Boolean = false, val logoUrl: String? = null, val tags: Set<String> = emptySet()) {
    override fun toString() = name
    companion object {
        fun defaultLogo(url: String): String? = if (url == "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3")
            "https://hitradio.ma/small-logo-hitradio.png" else null
        fun validUrl(input: String): String? = runCatching { URI(input.trim()) }.getOrNull()?.takeIf {
            input.length <= 2048 && it.scheme.equals("https", true) && !it.host.isNullOrBlank() &&
                it.userInfo == null && it.fragment == null && (it.port == -1 || it.port in 1..65535)
        }?.toASCIIString()
    }
}

internal data class PhoneFavorite(val name: String, val number: String)

internal class RadioAndPhoneStore(context: Context) {
    private val prefs = context.getSharedPreferences("radio_phone", Context.MODE_PRIVATE)
    val stations: List<RadioStation> get() {
        val saved = read("stations")
        if (!prefs.contains("stations")) return listOf(RadioStation("HIT RADIO",
            "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3", logoUrl = "https://hitradio.ma/small-logo-hitradio.png"))
        return (0 until saved.length()).mapNotNull { i ->
            val row = saved.optJSONObject(i) ?: return@mapNotNull null
            val url = RadioStation.validUrl(row.optString("url")) ?: return@mapNotNull null
            val name = row.optString("name").trim().take(60).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            RadioStation(name, url, row.optBoolean("favorite"), RadioStation.validUrl(row.optString("logo")), RadioCatalogue.parseTags(row.optString("tags")))
        }.distinctBy { it.url }.take(200)
    }
    var selectedUrl: String?
        get() = prefs.getString("selected", null)
        set(value) { prefs.edit().putString("selected", value).apply() }
    var lastStation: RadioStation?
        get() = runCatching {
            val row = JSONObject(prefs.getString("last_station", null) ?: return null)
            val url = RadioStation.validUrl(row.optString("url")) ?: return null
            val name = row.optString("name").trim().take(60).takeIf { it.isNotEmpty() } ?: return null
            RadioStation(name, url, logoUrl = RadioStation.validUrl(row.optString("logo")), tags = RadioCatalogue.parseTags(row.optString("tags")))
        }.getOrNull()
        set(value) {
            val row = value?.let { JSONObject().put("name", it.name).put("url", it.url).put("logo", it.logoUrl.orEmpty()).put("tags", it.tags.joinToString(",")).toString() }
            prefs.edit().putString("last_station", row).apply()
        }
    var favoritesOnly: Boolean
        get() = prefs.getBoolean("favorites_only", false)
        set(value) { prefs.edit().putBoolean("favorites_only", value).apply() }
    var radioCategory: RadioCategory
        get() = runCatching { RadioCategory.valueOf(prefs.getString("category", null).orEmpty()) }.getOrDefault(RadioCategory.ALL)
        set(value) { prefs.edit().putString("category", value.name).apply() }
    var phoneDraft: String
        get() = prefs.getString("phone_draft", "").orEmpty()
        set(value) { prefs.edit().putString("phone_draft", value.take(80)).apply() }
    fun saveStation(station: RadioStation): Boolean {
        val url = RadioStation.validUrl(station.url) ?: return false
        if (station.name.trim().isEmpty()) return false
        val all = stations.toMutableList()
        val index = all.indexOfFirst { it.url == url }
        if (index < 0 && all.size >= 200) return false
        val normalized = station.copy(name = station.name.trim().take(60), url = url,
            logoUrl = station.logoUrl?.let(RadioStation::validUrl))
        if (index >= 0) all[index] = normalized else all.add(normalized)
        writeStations(all)
        return true
    }
    fun removeStation(url: String) {
        writeStations(stations.filterNot { it.url == url })
        if (selectedUrl == url) selectedUrl = null
    }
    private fun writeStations(stations: List<RadioStation>) = prefs.edit().putString("stations", JSONArray(stations.map {
        JSONObject().put("name", it.name).put("url", it.url).put("favorite", it.favorite).put("logo", it.logoUrl.orEmpty()).put("tags", it.tags.joinToString(","))
    }).toString()).apply()
    val phoneFavorites: List<PhoneFavorite> get() {
        val saved = read("phone_favorites")
        return (0 until saved.length()).mapNotNull { i ->
            val row = saved.optJSONObject(i) ?: return@mapNotNull null
            val number = DialNumber.normalize(row.optString("number")) ?: return@mapNotNull null
            val name = row.optString("name").trim().take(40).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            PhoneFavorite(name, number)
        }.distinctBy { it.number }.take(6)
    }
    fun savePhoneFavorite(favorite: PhoneFavorite): Boolean {
        val number = DialNumber.normalize(favorite.number) ?: return false
        if (favorite.name.trim().isEmpty()) return false
        val all = phoneFavorites.toMutableList()
        val index = all.indexOfFirst { it.number == number }
        if (index < 0 && all.size >= 6) return false
        val normalized = PhoneFavorite(favorite.name.trim().take(40), number)
        if (index >= 0) all[index] = normalized else all.add(normalized)
        writeFavorites(all)
        return true
    }
    fun removePhoneFavorite(number: String) = writeFavorites(phoneFavorites.filterNot { it.number == number })
    private fun writeFavorites(favorites: List<PhoneFavorite>) = prefs.edit().putString("phone_favorites", JSONArray(favorites.map {
        JSONObject().put("name", it.name).put("number", it.number)
    }).toString()).apply()
    private fun read(key: String) = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrElse { JSONArray() }
}
