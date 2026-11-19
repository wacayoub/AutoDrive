package com.incardrive.home

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the head unit's apps show as notifications, kept so a test call can tell why the call strip did or did not
 * appear: which app, its category, whether it stays on, its buttons and whether it speaks of a call. The caller's
 * name, the number and the words of the notification are never kept. Shown in Réglages › Diagnostic d'appel.
 */
internal object CallDiagnostic {
    private const val PREFS = "call_diagnostic"
    private const val LINES = "lines"
    private const val MAX = 14
    private const val OWN = "com.incardrive.home"
    /** The Réglages list, refreshed as each notification is noted. */
    var changed: (() -> Unit)? = null

    // Guidance changes every few metres and would push everything else out of the list.
    private val skipped = setOf("com.google.android.apps.maps", "com.waze", OWN)

    fun record(context: Context, sbn: StatusBarNotification) {
        if (sbn.packageName in skipped) return
        val notification = sbn.notification ?: return
        if (notification.category == Notification.CATEGORY_TRANSPORT) return
        val entry = describe(sbn.packageName, notification.category, sbn.isOngoing,
            notification.actions.orEmpty().mapNotNull { it.title?.toString()?.takeIf(String::isNotBlank) },
            CallWatch.mentionsCall(CallWatch.words(notification)), notification.fullScreenIntent != null)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lines = prefs.getString(LINES, "").orEmpty().lines().filter { it.isNotBlank() }
        // The same notification updated again (a call timer) is one line.
        if (lines.firstOrNull()?.substringAfter(" · ") == entry) return
        val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        prefs.edit().putString(LINES, (listOf("$time · $entry") + lines).take(MAX).joinToString("\n")).apply()
        changed?.invoke()
    }

    /** One line, without anything personal: the button titles are the app's own words (Répondre, Raccrocher). */
    fun describe(packageName: String, category: String?, ongoing: Boolean, buttons: List<String>, mentionsCall: Boolean,
                 fullScreen: Boolean): String {
        val call = CallWatch.isCall(packageName, category, buttons, ongoing, mentionsCall)
        return listOfNotNull(
            packageName,
            "type ${category ?: "aucun"}",
            if (ongoing) "permanente" else null,
            if (buttons.isEmpty()) "0 bouton" else "${buttons.size} bouton${if (buttons.size > 1) "s" else ""} : " +
                buttons.joinToString(", ") { it.take(18) },
            if (mentionsCall) "parle d’appel" else null,
            if (fullScreen) "plein écran" else null,
            if (call) "→ BANDEAU" else "→ ignorée",
        ).joinToString(" · ")
    }

    fun lines(context: Context): List<String> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(LINES, "").orEmpty().lines().filter { it.isNotBlank() }

    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(LINES).apply()
}
