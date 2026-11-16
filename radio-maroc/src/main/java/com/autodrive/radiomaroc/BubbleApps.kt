package com.autodrive.radiomaroc

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Process

/**
 * The apps over which the floating bubble appears: Google Maps and Waze at first, and any app the listener adds in
 * Réglages › Bulle flottante. Knowing which app is on screen needs Android's « usage access », granted by the
 * listener in Android's settings (documented UsageStatsManager); without it the bubble shows over every app, as before.
 */
internal object BubbleApps {
    const val KEY = "bubble_apps"
    val DEFAULTS = setOf("com.google.android.apps.maps", "com.waze")

    fun chosen(prefs: SharedPreferences): Set<String> = prefs.getStringSet(KEY, null) ?: DEFAULTS

    fun save(prefs: SharedPreferences, apps: Set<String>) = prefs.edit().putStringSet(KEY, apps).apply()

    fun usageAllowed(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return runCatching {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
                AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    private var lastQuery = 0L
    private var front: String? = null

    /** The app now in front, from the activity events since the last call (the last two minutes at first). */
    fun foreground(context: Context): String? {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val from = if (lastQuery == 0L || now - lastQuery > 120_000) now - 120_000 else lastQuery - 1_000
        lastQuery = now
        runCatching {
            val events = usage.queryEvents(from, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) front = event.packageName
            }
        }
        return front
    }
}
