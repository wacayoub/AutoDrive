package com.incardrive.home

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * The notification access the listener grants for the media card. It also watches for call notifications, such
 * as the Incarlink Bluetooth phone app's « incoming call » with its Answer and Decline buttons, so the home screen
 * can show the call large and press those same buttons, and for Google Maps' guidance notification, to show the time
 * left and the arrival on the Maps card. Nothing else is read or kept.
 */
class MediaAccessService : NotificationListenerService() {
    override fun onListenerConnected() = refresh(current())

    override fun onListenerDisconnected() {
        CallWatch.clear()
        NavWatch.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh(current() ?: listOf(sbn))

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh(current().orEmpty().filter { it.key != sbn.key })

    private fun current() = runCatching { activeNotifications?.toList() }.getOrNull()

    private fun refresh(active: List<StatusBarNotification>?) {
        CallWatch.refresh(active.orEmpty())
        NavWatch.refresh(active.orEmpty())
    }
}

internal enum class CallButton { ANSWER, DECLINE, OTHER }

internal data class CallAction(val title: String, val kind: CallButton, val intent: PendingIntent)

internal data class CallAlert(
    val key: String,
    val caller: String,
    val detail: String?,
    val actions: List<CallAction>,
    val open: PendingIntent?,
) {
    /** An answered call shows only a hang-up button. */
    val ongoing get() = actions.none { it.kind == CallButton.ANSWER } && actions.any { it.kind == CallButton.DECLINE }
}

/** The call notification currently shown, if any. */
internal object CallWatch {
    private val state = MutableStateFlow<CallAlert?>(null)
    val call: StateFlow<CallAlert?> = state

    // Apps that handle calls on Android head units and phones.
    private val phoneApps = setOf("com.carsyso.bluetooth", "com.android.dialer", "com.google.android.dialer",
        "com.android.incallui", "com.android.server.telecom")

    private val answerWords = listOf("answer", "accept", "pick up", "répondre", "repondre", "décrocher", "decrocher",
        "接听", "接通", "接受", "رد")
    private val declineWords = listOf("decline", "reject", "refuse", "hang up", "end call", "rejeter", "raccrocher",
        "terminer", "挂断", "拒接", "拒绝", "结束", "إنهاء", "رفض")

    fun classify(title: String): CallButton {
        val text = title.lowercase(Locale.ROOT).trim()
        return when {
            answerWords.any { text.contains(it) } -> CallButton.ANSWER
            text == "end" || declineWords.any { text.contains(it) } -> CallButton.DECLINE
            else -> CallButton.OTHER
        }
    }

    /**
     * A notification is a call when Android marks it as one, or when a phone app shows one with an answer or
     * hang-up button.
     */
    fun isCall(packageName: String, category: String?, actionTitles: List<String>): Boolean {
        if (category == Notification.CATEGORY_CALL) return actionTitles.isNotEmpty()
        return packageName in phoneApps && actionTitles.map(::classify).any { it != CallButton.OTHER }
    }

    fun refresh(active: List<StatusBarNotification>) {
        state.value = active.asSequence().mapNotNull(::read).firstOrNull()
    }

    fun clear() {
        state.value = null
    }

    private fun read(sbn: StatusBarNotification): CallAlert? {
        val notification = sbn.notification ?: return null
        val actions = notification.actions.orEmpty().mapNotNull { action ->
            val title = action.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CallAction(title, classify(title), action.actionIntent ?: return@mapNotNull null)
        }
        if (!isCall(sbn.packageName, notification.category, actions.map { it.title })) return null
        val extras = notification.extras
        val caller = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: "Appel"
        val detail = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        return CallAlert(sbn.key, caller, detail, actions, notification.contentIntent)
    }
}

/** What Google Maps (or Waze) shows while guiding: time left, distance left, arrival time and the next step. */
internal data class NavInfo(val remaining: String?, val distance: String?, val arrival: String?, val instruction: String?)

/** The guidance notification of the navigation app, read like the call notification. */
internal object NavWatch {
    private val state = MutableStateFlow<NavInfo?>(null)
    val nav: StateFlow<NavInfo?> = state
    private val navigationApps = setOf("com.google.android.apps.maps", "com.waze")

    private val duration = Regex("""(?i)\b(\d{1,2}\s*h(?:\s*\d{1,2}\s*min)?|\d{1,3}\s*min)\b""")
    private val distance = Regex("""(?i)\b(\d+(?:[.,]\d+)?\s*(?:km|m))\b""")
    private val clock = Regex("""\b([01]?\d|2[0-3])[:h]([0-5]\d)\b""")

    fun refresh(active: List<StatusBarNotification>) {
        state.value = active.asSequence().mapNotNull(::read).firstOrNull()
    }

    fun clear() {
        state.value = null
    }

    private fun read(sbn: StatusBarNotification): NavInfo? {
        if (sbn.packageName !in navigationApps) return null
        val notification = sbn.notification ?: return null
        val guiding = notification.category == Notification.CATEGORY_NAVIGATION || sbn.isOngoing
        if (!guiding) return null
        val extras = notification.extras
        val texts = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT)
            .mapNotNull { extras.getCharSequence(it)?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        return parse(texts)
    }

    /**
     * Finds the trip summary among the notification's lines: the line with a duration gives the time left, and the
     * distance and arrival time next to it; the first other line is the next instruction.
     */
    fun parse(texts: List<String>): NavInfo? {
        val summary = texts.firstOrNull { duration.containsMatchIn(it) } ?: return null
        val remaining = duration.find(summary)?.value?.replace(Regex("""\s+"""), " ")
        val left = distance.find(summary)?.value?.replace(Regex("""\s+"""), " ")
        val arrival = (clock.find(summary) ?: texts.firstNotNullOfOrNull { clock.find(it) })
            ?.let { "${it.groupValues[1].padStart(2, '0')}:${it.groupValues[2]}" }
        val instruction = texts.firstOrNull { it != summary && !duration.containsMatchIn(it) }
        return NavInfo(remaining, left, arrival, instruction)
    }
}
