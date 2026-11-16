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
 * can show the call large and press those same buttons. Nothing else is read or kept.
 */
class MediaAccessService : NotificationListenerService() {
    override fun onListenerConnected() {
        CallWatch.refresh(runCatching { activeNotifications?.toList() }.getOrNull().orEmpty())
    }

    override fun onListenerDisconnected() = CallWatch.clear()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        CallWatch.refresh(runCatching { activeNotifications?.toList() }.getOrNull() ?: listOf(sbn))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        CallWatch.refresh(runCatching { activeNotifications?.toList() }.getOrNull().orEmpty().filter { it.key != sbn.key })
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
