package com.incardrive.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import java.util.Locale

/** The navigation app the driver chose for Naviguer, Maison, Travail, the places and the quick searches. */
internal enum class NavApp(val label: String, val packageName: String) {
    MAPS("Google Maps", AppLinks.MAPS), WAZE("Waze", "com.waze");
}

/** A one-tap search around the car, opened in the navigation app. */
internal enum class QuickSearch(val label: String, val icon: Int, val query: String) {
    FUEL("Station", R.drawable.ic_home_fuel, "station service"),
    PARKING("Parking", R.drawable.ic_home_parking, "parking"),
    MOSQUE("Mosquée", R.drawable.ic_home_mosque, "mosquée"),
    CAFE("Café", R.drawable.ic_home_cafe, "café restaurant"),
}

/**
 * Hands destinations and searches to Google Maps or Waze with their documented links: Maps' « google.navigation » and
 * « geo » intents, and Waze's « waze.com/ul » links (including its saved Home and Work favourites).
 */
internal class Navigation(private val activity: Activity, private val prefs: SharedPreferences) {
    private val packages = activity.packageManager

    private fun installed(app: NavApp) = packages.getLaunchIntentForPackage(app.packageName) != null

    /** The chosen app if installed, else the other one if it is. */
    fun app(): NavApp {
        val chosen = NavApp.entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: NavApp.MAPS
        return if (installed(chosen)) chosen else NavApp.entries.firstOrNull(::installed) ?: chosen
    }

    fun choose(app: NavApp) = prefs.edit().putString(KEY, app.name).apply()

    private fun start(intent: Intent?): Boolean = intent?.let {
        try {
            activity.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            HomeVisibility.front = it.component?.packageName ?: it.`package`
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    } ?: false

    private fun view(uri: String, app: NavApp) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(app.packageName)
        .takeIf { it.resolveActivity(packages) != null }

    /** The app on its « where to? » screen: Maps' directions, Waze's search. */
    fun open(): Boolean = when (app()) {
        NavApp.MAPS -> AppLinks(activity).openMaps()
        NavApp.WAZE -> start(packages.getLaunchIntentForPackage(NavApp.WAZE.packageName))
    }

    /** Back to the guidance in progress. */
    fun resume(): Boolean = start(packages.getLaunchIntentForPackage(app().packageName))

    /** Guidance to an address, a place or « latitude,longitude ». */
    fun goTo(destination: String): Boolean = when (app()) {
        NavApp.MAPS -> AppLinks(activity).navigate(destination)
        NavApp.WAZE -> start(view(wazeLink(destination), NavApp.WAZE))
    }.also { if (it) HomeVisibility.destinationPickedAt = android.os.SystemClock.elapsedRealtime() }

    /** Guidance home or to work, saved in the navigation app's own account. */
    fun goToSaved(work: Boolean): Boolean = when (app()) {
        NavApp.MAPS -> AppLinks(activity).navigate(mapsLabel(work))
        NavApp.WAZE -> start(view("https://waze.com/ul?favorite=${if (work) "work" else "home"}&navigate=yes", NavApp.WAZE))
    }.also { if (it) HomeVisibility.destinationPickedAt = android.os.SystemClock.elapsedRealtime() }

    /** Places matching [query] around the car. */
    fun search(query: String): Boolean = when (app()) {
        NavApp.MAPS -> start(view("geo:0,0?q=" + Uri.encode(query), NavApp.MAPS)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))).takeIf { it.resolveActivity(packages) != null })
        NavApp.WAZE -> start(view("https://waze.com/ul?q=" + Uri.encode(query), NavApp.WAZE))
    }

    companion object {
        const val KEY = "nav_app"
        private val COORDINATES = Regex("""-?\d+(\.\d+)?,-?\d+(\.\d+)?""")

        fun mapsLabel(work: Boolean) = when {
            Locale.getDefault().language == "fr" -> if (work) "Travail" else "Domicile"
            else -> if (work) "Work" else "Home"
        }

        /** Waze's documented link: coordinates go in « ll », anything else in « q ». */
        fun wazeLink(destination: String): String =
            if (COORDINATES.matches(destination)) "https://waze.com/ul?ll=${Uri.encode(destination)}&navigate=yes"
            else "https://waze.com/ul?q=${Uri.encode(destination)}&navigate=yes"
    }
}
