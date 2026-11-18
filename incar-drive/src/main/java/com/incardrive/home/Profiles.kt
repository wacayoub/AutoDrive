package com.incardrive.home

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Two drivers on one head unit: each keeps their own speedometer, colours, theme, Maison and Travail, navigation app,
 * map, bottom bar, favourites and speed alert. Changing driver stores the current choices under the one leaving and
 * puts back those of the one arriving; the first time a driver is chosen, they start from the current choices.
 * Everything stays in the launcher's own settings on the head unit.
 */
internal class Profiles(private val prefs: SharedPreferences) {
    val count = 2

    val active get() = prefs.getInt(ACTIVE, 0).coerceIn(0, count - 1)

    fun name(index: Int) = prefs.getString("$NAME$index", null)?.takeIf { it.isNotBlank() } ?: "Conducteur ${index + 1}"

    fun rename(index: Int, value: String) = prefs.edit().putString("$NAME$index", value.trim().take(18)).apply()

    /** Switches to driver [index]; false when they already drive. */
    fun switchTo(index: Int): Boolean {
        val leaving = active
        if (index == leaving || index !in 0 until count) return false
        val edit = prefs.edit().putString("$DATA$leaving", snapshot().toString())
        prefs.getString("$DATA$index", null)?.let { stored ->
            runCatching { JSONObject(stored) }.getOrNull()?.let { data ->
                KEYS.forEach { edit.remove(it) }
                data.keys().forEach { key ->
                    if (key !in KEYS) return@forEach
                    val entry = data.optJSONArray(key) ?: return@forEach
                    when (entry.optString(0)) {
                        "s" -> edit.putString(key, entry.optString(1))
                        "i" -> edit.putInt(key, entry.optInt(1))
                        "b" -> edit.putBoolean(key, entry.optBoolean(1))
                        "l" -> edit.putLong(key, entry.optLong(1))
                        "f" -> edit.putFloat(key, entry.optDouble(1).toFloat())
                        "set" -> edit.putStringSet(key, (entry.optJSONArray(1) ?: JSONArray()).let { list ->
                            (0 until list.length()).map { list.optString(it) }.toSet()
                        })
                    }
                }
            }
        }
        edit.putInt(ACTIVE, index).apply()
        return true
    }

    private fun snapshot() = JSONObject().apply {
        prefs.all.forEach { (key, value) ->
            if (key !in KEYS || value == null) return@forEach
            put(key, when (value) {
                is String -> JSONArray().put("s").put(value)
                is Int -> JSONArray().put("i").put(value)
                is Boolean -> JSONArray().put("b").put(value)
                is Long -> JSONArray().put("l").put(value)
                is Float -> JSONArray().put("f").put(value.toDouble())
                is Set<*> -> JSONArray().put("set").put(JSONArray(value.map { it.toString() }))
                else -> return@forEach
            })
        }
    }

    companion object {
        const val ACTIVE = "profile_active"
        const val NAME = "profile_name_"
        const val DATA = "profile_data_"
        /** What belongs to a driver; the rest (phone numbers, widgets, the city, updates) is the car's. */
        val KEYS = setOf(HomeActivity.SPEED_STYLE, HomeActivity.CAR_COLOR, HomeActivity.ACCENT, HomeActivity.APPEARANCE,
            "place_home", "place_work", HomeActivity.MY_PLACES, HomeActivity.MAP_SOURCE, HomeActivity.MAP_ON_HOME,
            HomeActivity.DOCK_APPS, HomeActivity.FAVORITES, HomeActivity.SPEED_LIMIT,
            HomeActivity.CLICKS, OwnMap.HEADING_UP, Navigation.KEY, MediaResume.LAST, MediaResume.ENABLED)
    }
}
