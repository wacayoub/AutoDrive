package com.autodrive.radiomaroc

import android.content.SharedPreferences

/** Ordered lists the screen and the playback service share through the radio preferences. */
internal object ListeningPrefs {
    const val FAVORITE_ORDER = "favorite_order"
    const val RECENT_STATIONS = "recent_stations"
    const val RECENT_COUNTRIES = "recent_countries"
    const val UNAVAILABLE = "unavailable_streams"
    const val WHEEL_PRESETS = "wheel_presets"
    const val HERRAZ_CHAPTER = "herraz_chapter"
    const val HERRAZ_POSITION = "herraz_position"

    fun list(prefs: SharedPreferences, key: String): List<String> =
        prefs.getString(key, null)?.split('\n')?.filter(String::isNotBlank).orEmpty()

    fun put(editor: SharedPreferences.Editor, key: String, values: List<String>): SharedPreferences.Editor =
        editor.putString(key, values.joinToString("\n"))

    /** Moves [value] to the front of [key], keeping at most [max] distinct entries. */
    fun remember(prefs: SharedPreferences, key: String, value: String, max: Int): List<String> {
        val updated = (listOf(value) + list(prefs, key).filter { it != value }).take(max)
        put(prefs.edit(), key, updated).apply()
        return updated
    }

    /** Saved preset order: every favourite exactly once, new ones appended at the end. */
    fun favoriteOrder(prefs: SharedPreferences, favorites: Set<String>): List<String> {
        val saved = list(prefs, FAVORITE_ORDER).filter { it in favorites }
        return saved + favorites.filter { it !in saved }.sorted()
    }
}
