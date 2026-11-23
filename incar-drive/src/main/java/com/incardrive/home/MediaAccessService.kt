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

    private val bubble by lazy { NavBubble(this) }

    // The screen going off and on: the head unit asleep with the contact off, then the car starting again.
    private val screen = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val prefs = getSharedPreferences(NavBubble.PREFS, MODE_PRIVATE)
            val now = android.os.SystemClock.elapsedRealtime()
            when (intent.action) {
                android.content.Intent.ACTION_SCREEN_OFF -> prefs.edit().putLong("screen_off_at", now).apply()
                android.content.Intent.ACTION_SCREEN_ON -> {
                    val offAt = prefs.getLong("screen_off_at", 0L)
                    if (offAt in 1..now && now - offAt >= MediaResume.SLEEP_MS) {
                        MediaResume.get(context).onDriveStart("wake:$offAt")
                        HotspotReminder.driveStarted(context, "wake:$offAt")
                    }
                }
            }
        }
    }
    private var watchingScreen = false

    override fun onListenerConnected() {
        Fonts.init(this)
        // Android binds this listener at boot, whichever app is in front: the car has started.
        MediaResume.get(this).onDriveStart()
        if (!watchingScreen) {
            registerReceiver(screen, android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_SCREEN_OFF)
                addAction(android.content.Intent.ACTION_SCREEN_ON)
            })
            watchingScreen = true
        }
        HomeVisibility.changed = { bubble.render(NavWatch.nav.value, NavWatch.source) }
        refresh(current())
    }

    override fun onDestroy() {
        if (watchingScreen) runCatching { unregisterReceiver(screen) }
        watchingScreen = false
        super.onDestroy()
    }

    override fun onListenerDisconnected() {
        CallWatch.clear()
        NavWatch.clear()
        bubble.hide()
        HomeVisibility.changed = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        CallDiagnostic.record(this, sbn)
        // The guidance changes every few metres: its own notification is read at once, without asking Android for
        // the whole list again each time.
        if (NavWatch.post(sbn, this)) {
            bubble.render(NavWatch.nav.value, NavWatch.source)
            returnAfterPick()
            return
        }
        refresh(current() ?: listOf(sbn))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh(current().orEmpty().filter { it.key != sbn.key })

    private fun current() = runCatching { activeNotifications?.toList() }.getOrNull()

    private fun refresh(active: List<StatusBarNotification>?) {
        CallWatch.refresh(active.orEmpty(), this)
        val wasGuiding = NavWatch.nav.value != null
        NavWatch.refresh(active.orEmpty(), this)
        bubble.render(NavWatch.nav.value, NavWatch.source)
        if (!returnAfterPick() && !wasGuiding && NavWatch.nav.value != null) returnHome()
    }

    /**
     * A destination picked on the home screen (Maison, Travail, a place): the navigation app has to open to start
     * guiding, but the driver asked to stay on the home screen, so it comes back two seconds after the guidance is
     * seen, with either app. Android lets an app holding « display over other apps » bring its own screen forward.
     * True when a return was scheduled.
     */
    private fun returnAfterPick(): Boolean {
        val picked = HomeVisibility.destinationPickedAt
        if (picked == 0L || NavWatch.nav.value == null) return false
        HomeVisibility.destinationPickedAt = 0L
        if (android.os.SystemClock.elapsedRealtime() - picked > PICK_WINDOW_MS) return false
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (NavWatch.nav.value != null && !HomeVisibility.shown && android.provider.Settings.canDrawOverlays(this)) {
                runCatching {
                    startActivity(android.content.Intent(this, HomeActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }, 2_000)
        return true
    }

    private companion object {
        // How long after the tap a guidance start still counts as that destination's.
        const val PICK_WINDOW_MS = 90_000L
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * With « Appli Maps » chosen for the home page, a few seconds after Google Maps starts guiding the home screen
     * comes back by itself, as if the driver pressed Home: Maps then shrinks into its small window (when its
     * picture-in-picture permission is on), which lands in the frame kept for it. Android lets an app holding the
     * « display over other apps » permission bring its own screen forward; without it nothing happens. Waze has no
     * small window, so it is left in front.
     */
    private fun returnHome() {
        val prefs = getSharedPreferences(NavBubble.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean("map_on_home", true) || prefs.getString("map_source", null) != "maps_app") return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            val maps = NavWatch.source != "com.waze"
            if (NavWatch.nav.value != null && maps && !HomeVisibility.shown && android.provider.Settings.canDrawOverlays(this)) {
                runCatching {
                    startActivity(android.content.Intent(this, HomeActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }, 5_000)
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
    /** The contact's picture, when the phone app puts one in its notification. */
    val photo: android.graphics.drawable.Icon? = null,
    /** Shown by the Incarlink's own phone app, which draws its grey call bar over the top of every screen. */
    val incarlink: Boolean = false,
) {
    /**
     * An answered call shows only a hang-up button. A notification with no button at all is a call already under way
     * (or being placed): there is nothing to answer.
     */
    val ongoing get() = actions.isEmpty() ||
        actions.none { it.kind == CallButton.ANSWER } && actions.any { it.kind == CallButton.DECLINE }
}

/** The call notification currently shown, if any. */
internal object CallWatch {
    private val state = MutableStateFlow<CallAlert?>(null)
    val call: StateFlow<CallAlert?> = state

    // Apps that handle calls on Android head units and phones.
    private val phoneApps = setOf("com.carsyso.bluetooth", "com.android.dialer", "com.google.android.dialer",
        "com.android.incallui", "com.android.server.telecom")

    /** The Incarlink's Bluetooth phone app, and any other app of the same maker. */
    fun isIncarlink(packageName: String) = packageName.startsWith("com.carsyso.")

    fun isPhoneApp(packageName: String) = packageName in phoneApps || isIncarlink(packageName)

    // Words a phone app writes in a call notification: « Calling... », « Appel en cours », « 通话中 ».
    private val callWords = listOf("call", "appel", "incoming", "outgoing", "dialing", "dialling", "en ligne",
        "来电", "通话", "去电", "呼叫", "拨号", "مكالمة", "اتصال")

    fun mentionsCall(texts: List<String>) = texts.any { text ->
        val lower = text.lowercase(Locale.ROOT)
        callWords.any { lower.contains(it) }
    }

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
     * hang-up button. Some head-unit phone apps put no button in it: then a phone app's ongoing notification, or one
     * that speaks of a call, is one too.
     */
    fun isCall(packageName: String, category: String?, actionTitles: List<String>, ongoing: Boolean = false,
               mentionsCall: Boolean = false): Boolean {
        if (category == Notification.CATEGORY_CALL) return actionTitles.isNotEmpty() || ongoing
        if (!isPhoneApp(packageName)) return false
        if (actionTitles.map(::classify).any { it != CallButton.OTHER }) return true
        return mentionsCall && (ongoing || actionTitles.isEmpty())
    }

    /** The words of a notification: title, text, sub-text and ticker. */
    fun words(notification: Notification): List<String> {
        val extras = notification.extras
        return listOfNotNull(extras?.getCharSequence(Notification.EXTRA_TITLE), extras?.getCharSequence(Notification.EXTRA_TEXT),
            extras?.getCharSequence(Notification.EXTRA_SUB_TEXT), extras?.getCharSequence(Notification.EXTRA_BIG_TEXT),
            notification.tickerText).map { it.toString() }.filter { it.isNotBlank() }
    }

    fun refresh(active: List<StatusBarNotification>, context: android.content.Context? = null) {
        state.value = active.asSequence().mapNotNull { read(it) ?: test(it, context) }.firstOrNull()
    }

    /**
     * Debug builds only: `cmd notification post -t '<name>' call-test '<number>'` shows a ringing call, and a tag ending in
     * `-on` (`call-test-on`) an answered one, so the call screen can be tried without a phone. Their buttons do nothing.
     */
    private fun test(sbn: StatusBarNotification, context: android.content.Context?): CallAlert? {
        if (context == null || sbn.packageName != "com.android.shell" || sbn.tag?.startsWith("call-test") != true ||
            context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val nothing = PendingIntent.getBroadcast(context, 0, android.content.Intent("com.incardrive.home.TEST_CALL")
            .setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        val extras = sbn.notification.extras
        val answered = sbn.tag?.endsWith("-on") == true
        val actions = listOfNotNull(CallAction("Raccrocher", CallButton.DECLINE, nothing),
            if (answered) null else CallAction("Répondre", CallButton.ANSWER, nothing))
        return CallAlert(sbn.key, extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Appel",
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(), actions, null)
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
        if (!isCall(sbn.packageName, notification.category, actions.map { it.title }, sbn.isOngoing,
                mentionsCall(words(notification)))) return null
        val extras = notification.extras
        val caller = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: "Appel"
        val detail = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        return CallAlert(sbn.key, caller, detail, actions, notification.contentIntent, notification.getLargeIcon(),
            isIncarlink(sbn.packageName))
    }
}

/** What Google Maps (or Waze) shows while guiding: time left, distance left, arrival time and the next step. */
internal data class NavInfo(
    val remaining: String?, val distance: String?, val arrival: String?, val instruction: String?,
    /** Distance to the next turn, when the instruction gives it (« 300 m »). */
    val step: String? = null,
    /** The turn arrow Maps draws in its notification. */
    val arrow: android.graphics.drawable.Drawable? = null,
    /** The traffic as Google Maps itself colours it in its notification; null when it gives no colour. */
    val flow: Flow? = null,
    /** Which way that arrow points, read from its shape: all there is when the notification has no words. */
    val turn: Turn? = null,
    /** True when that arrow leans off at an angle (keep, fork, exit) rather than turning square. */
    val slight: Boolean = false,
)

/** The guidance notification of the navigation app, read like the call notification. */
internal object NavWatch {
    private val state = MutableStateFlow<NavInfo?>(null)
    val nav: StateFlow<NavInfo?> = state
    /** The app that guides, to bring it back from the bubble. */
    var source: String? = null
        private set
    private val navigationApps = setOf("com.google.android.apps.maps", "com.waze")

    /** An update of the guidance already being followed: true when [sbn] was one and has been taken. */
    fun post(sbn: StatusBarNotification, context: android.content.Context): Boolean {
        if (state.value == null || sbn.packageName != source) return false
        val info = read(sbn, context) ?: return false
        state.value = info
        // The guidance notification may itself word an alert; one it carried before goes when it no longer does.
        val found = RoadAlerts.find(texts(sbn))
        if (found != null) { alertState.value = found; alertFromGuidance = true }
        else if (alertFromGuidance) { alertState.value = null; alertFromGuidance = false }
        return true
    }

    private val alertState = MutableStateFlow<RoadAlert?>(null)
    /** An accident, a slowdown, works… as the navigation app words it in one of its notifications; null when none. */
    val alert: StateFlow<RoadAlert?> = alertState
    private var alertFromGuidance = false

    private fun fromNavigation(sbn: StatusBarNotification, context: android.content.Context?) = sbn.packageName in navigationApps ||
        (sbn.packageName == "com.android.shell" && sbn.tag?.startsWith("nav-test") == true && context != null &&
            context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)

    private fun texts(sbn: StatusBarNotification): List<String> {
        val extras = sbn.notification?.extras ?: return emptyList()
        return listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT)
            .mapNotNull { extras.getCharSequence(it)?.toString()?.trim()?.takeIf(String::isNotEmpty) }
    }

    private val duration = Regex("""(?i)\b(\d{1,2}\s*h(?:\s*\d{1,2}\s*min)?|\d{1,3}\s*min)\b""")
    private val distance = Regex("""(?i)\b(\d+(?:[.,]\d+)?\s*(?:km|m))\b""")
    private val clock = Regex("""\b([01]?\d|2[0-3])[:h]([0-5]\d)\b""")

    fun refresh(active: List<StatusBarNotification>, context: android.content.Context? = null) {
        val found = active.asSequence().mapNotNull { sbn -> read(sbn, context)?.let { it to sbn.packageName } }.firstOrNull()
        source = found?.second
        state.value = found?.first
        // Any notification of the navigation app may carry the alert: the guidance one, or one of its own.
        val alerting = active.firstOrNull { fromNavigation(it, context) && RoadAlerts.find(texts(it)) != null }
        alertState.value = alerting?.let { RoadAlerts.find(texts(it)) }
        alertFromGuidance = alerting != null && read(alerting, context) != null
    }

    fun clear() {
        state.value = null
        alertState.value = null
    }

    private fun read(sbn: StatusBarNotification, context: android.content.Context?): NavInfo? {
        // Debug builds also take « adb shell cmd notification post nav-test… », to try the bubble without Maps.
        val test = sbn.packageName == "com.android.shell" && sbn.tag?.startsWith("nav-test") == true &&
            context != null && context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (sbn.packageName !in navigationApps && !test) return null
        val notification = sbn.notification ?: return null
        val guiding = notification.category == Notification.CATEGORY_NAVIGATION || sbn.isOngoing || test
        if (!guiding) return null
        val extras = notification.extras
        val texts = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT)
            .mapNotNull { extras.getCharSequence(it)?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        val arrow = context?.let { runCatching { notification.getLargeIcon()?.loadDrawable(it) }.getOrNull() }
        // Maps colours its time left green, orange or red with the traffic: that colour, when the notification carries it.
        val coloured = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT)
            .mapNotNull { extras.getCharSequence(it) as? android.text.Spanned }
            .flatMap { text -> text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java).toList() }
            .firstNotNullOfOrNull { Traffic.ofColor(it.foregroundColor) }
        // Debug builds only: a test notification may name a colour (« [vert] », « [orange] », « [rouge] »).
        val named = if (!test) null else texts.firstNotNullOfOrNull { line ->
            when { "[vert]" in line -> Flow.FLUID; "[orange]" in line -> Flow.SLOW; "[rouge]" in line -> Flow.JAM; else -> null }
        }
        return parse(texts)?.let {
            val shape = ArrowShape.shape(arrow)
            it.copy(arrow = arrow, flow = coloured ?: named, turn = shape?.first, slight = shape?.second == true)
        }
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
        val step = instruction?.let { line -> distance.find(line)?.value?.replace(Regex("""\s+"""), " ") }
        return NavInfo(remaining, left, arrival, instruction, step)
    }
}
