package com.autodrive.launcher

import android.content.Context
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.roundToInt
import org.json.JSONObject

internal enum class MapDisplayOption { TRAFFIC, TRANSIT, BICYCLING, SATELLITE, TERRAIN, WILDFIRES, MOTION_CONTROLS }

internal object MapDisplayPolicy {
    const val HOME = "https://www.google.com/maps/@?api=1&map_action=map"
    private val hosts = setOf("www.google.com", "google.com", "maps.google.com")
    private val camera = Regex("@(-?\\d{1,2}(?:\\.\\d+)?),(-?\\d{1,3}(?:\\.\\d+)?),(\\d{1,2}(?:\\.\\d+)?)z")
    fun trustedOrigin(value: String): Boolean = runCatching { URI(value).let {
        it.scheme == "https" && it.host in hosts && it.port in setOf(-1, 443) && it.userInfo == null &&
            it.rawQuery == null && it.rawFragment == null && it.path.orEmpty() in setOf("", "/")
    } }.getOrDefault(false)
    fun onMaps(value: String?): Boolean = runCatching { URI(value ?: return false).let {
        it.scheme == "https" && it.host in hosts && it.port in setOf(-1, 443) && it.userInfo == null &&
            (it.path == "/maps" || it.path.startsWith("/maps/"))
    } }.getOrDefault(false)
    fun zoom(value: String?): Int? = runCatching {
        val uri=URI(value ?: return null)
        camera.find(uri.path)?.groupValues?.get(3)?.toDouble()?.roundToInt()?.coerceIn(0,21)
            ?: uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("zoom=") }?.substringAfter('=')?.toIntOrNull()?.takeIf { it in 0..21 }
    }.getOrNull()

    /** One private public-map bookmark, never an account/search/directions URL or auth query. */
    fun viewUrl(value: String?): String? = runCatching {
        if (value == null || value.length > 2048 || !onMaps(value)) return null
        val uri = URI(value)
        val path = uri.rawPath
        val match = camera.find(uri.path)
        if (match != null) {
            val lat = match.groupValues[1].toDouble(); val lng = match.groupValues[2].toDouble(); val zoom = match.groupValues[3].toDouble()
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0 || zoom !in 0.0..21.0) return null
        }
        val mapPath = when {
            path in setOf("/maps", "/maps/", "/maps/@") -> "/maps/@"
            path.startsWith("/maps/@") && match != null &&
                Regex("/maps/@[-0-9.,]+z(?:/data=[A-Za-z0-9!%._~,=@/+-]+)?/?").matches(path) -> path
            match != null -> "/maps/${match.value}" // A place/route can contribute a viewport, never an auto-resumed trip.
            else -> return null
        }
        val allowed = linkedMapOf("api" to "1", "map_action" to "map")
        for (part in uri.rawQuery.orEmpty().split('&')) {
            val bits = part.split('=', limit = 2)
            if (bits.size != 2) continue
            val key = URLDecoder.decode(bits[0], "UTF-8"); val text = URLDecoder.decode(bits[1], "UTF-8")
            when (key) {
                "map_action" -> if (text != "map") return null
                "basemap" -> if (text in setOf("roadmap", "satellite", "terrain")) allowed[key] = text
                "layer" -> if (text in setOf("none", "traffic", "transit", "bicycling")) allowed[key] = text
                "zoom" -> if (text.toIntOrNull() in 0..21) allowed[key] = text
                "center" -> {
                    val center = text.split(',').map { it.toDoubleOrNull() }
                    if (center.size == 2 && center[0] != null && center[1] != null && center[0]!! in -90.0..90.0 && center[1]!! in -180.0..180.0) allowed[key] = text
                }
                "hl" -> if (Regex("[a-z]{2,3}(?:-[A-Za-z]{2,4})?").matches(text)) allowed[key] = text
            }
        }
        "https://${uri.host}$mapPath?" + allowed.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
    }.getOrNull()
}

internal data class RememberedMapDisplay(val options: Map<MapDisplayOption, Boolean> = emptyMap(),
    val followOnStart: Boolean = false, val viewUrl: String? = null) {
    fun launchUrl(): String {
        if (!followOnStart) return MapDisplayPolicy.viewUrl(viewUrl) ?: MapDisplayPolicy.HOME
        // Google obtains current location. Do not launch at a stale saved coordinate when following.
        val basemap = when { options[MapDisplayOption.SATELLITE] == true -> "satellite"; options[MapDisplayOption.TERRAIN] == true -> "terrain"; else -> "roadmap" }
        val layer = listOf(MapDisplayOption.TRAFFIC, MapDisplayOption.TRANSIT, MapDisplayOption.BICYCLING).firstOrNull { options[it] == true }?.name?.lowercase() ?: "none"
        return "${MapDisplayPolicy.HOME}&basemap=$basemap&layer=$layer" + (MapDisplayPolicy.zoom(viewUrl)?.let { "&zoom=$it" } ?: "")
    }
}

internal class MapDisplayMemory(context: Context) {
    private val prefs = context.getSharedPreferences("maps_display", Context.MODE_PRIVATE)
    fun read(): RememberedMapDisplay = runCatching {
        val saved = prefs.getString("state", "{}").orEmpty()
        if (saved.length > 12_000) return RememberedMapDisplay()
        val data = JSONObject(saved)
        if (data.optInt("schema") != 1) return RememberedMapDisplay()
        val rows = data.optJSONObject("options") ?: JSONObject()
        RememberedMapDisplay(MapDisplayOption.entries.mapNotNull { option ->
            (rows.opt(option.name) as? Boolean)?.let { option to it }
        }.toMap(), data.opt("followOnStart") == true, MapDisplayPolicy.viewUrl(data.optString("viewUrl")))
    }.getOrDefault(RememberedMapDisplay())

    fun capture(snapshot: String) {
        if (snapshot.length > 12_000) return
        runCatching {
            val data = JSONObject(snapshot)
            if (data.optInt("schema") != 1 || !MapDisplayPolicy.onMaps(data.optString("url"))) return
            val old = read()
            val rows = data.optJSONObject("options") ?: JSONObject()
            val updates = MapDisplayOption.entries.mapNotNull { option -> (rows.opt(option.name) as? Boolean)?.let { option to it } }.toMap()
            val follow=(data.opt("followOnStart") as? Boolean) ?: old.followOnStart
            val view=MapDisplayPolicy.viewUrl(data.optString("url")) ?: old.viewUrl
            // Following a moving car must not write GPS coordinates to preferences every second.
            val rememberedView=if(follow) MapDisplayPolicy.zoom(view)?.let { "${MapDisplayPolicy.HOME}&zoom=$it" } else view
            write(old.copy(options = old.options + updates, followOnStart = follow, viewUrl = rememberedView))
        }
    }
    fun setFollow(enabled: Boolean, currentView: String? = null) {
        val old=read();val view=MapDisplayPolicy.viewUrl(currentView) ?: old.viewUrl
        write(old.copy(followOnStart = enabled, viewUrl = if(enabled) MapDisplayPolicy.zoom(view)?.let { "${MapDisplayPolicy.HOME}&zoom=$it" } else view))
    }
    fun clear() { prefs.edit().clear().apply() }
    private fun write(value: RememberedMapDisplay) {
        val json = JSONObject().put("schema", 1).put("followOnStart", value.followOnStart)
            .put("options", JSONObject(value.options.mapKeys { it.key.name })).put("viewUrl", value.viewUrl.orEmpty()).toString()
        if (prefs.getString("state", null) != json) prefs.edit().putString("state", json).apply()
    }
}
