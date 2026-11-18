package com.incardrive.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.PendingIntent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * The call as a strip at the top of the home screen, over the cards: who calls (with the picture the phone app gives,
 * or the initials), a ring while it rings, how long the call has lasted, and round buttons that press the phone app's
 * own Answer and Decline buttons; during the call, a microphone button when the phone app offers none.
 */
internal class CallScreen(private val home: HomeActivity, private val host: FrameLayout) {
    private val main = Handler(Looper.getMainLooper())
    private var shownKey: String? = null
    private var answeredAt = 0L
    private var timer: TextView? = null
    private var pulse: ValueAnimator? = null
    private var mutedHere = false
    private val audio get() = home.getSystemService(AudioManager::class.java)

    private val tick = object : Runnable {
        override fun run() {
            timer?.text = "En appel · ${duration()}"
            main.postDelayed(this, 1000)
        }
    }

    fun render(call: CallAlert?) {
        if (call == null) {
            stop()
            if (mutedHere) runCatching { audio?.isMicrophoneMute = false }
            mutedHere = false
            shownKey = null
            if (host.visibility == View.VISIBLE) host.animate().alpha(0f).setDuration(160)
                .withEndAction { host.visibility = View.GONE; host.removeAllViews() }.start()
            return
        }
        val wasShown = host.visibility == View.VISIBLE && host.alpha > 0f
        if (call.key != shownKey) { answeredAt = 0L; shownKey = call.key }
        if (call.ongoing && answeredAt == 0L) answeredAt = System.currentTimeMillis()
        stop()
        host.removeAllViews()
        host.addView(strip(call), FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        // The strip lets the home screen under it be used.
        host.isClickable = false
        host.visibility = View.VISIBLE
        if (!wasShown) {
            host.alpha = 0f
            host.translationY = -home.dp(24).toFloat()
            host.animate().alpha(1f).translationY(0f).setDuration(220).start()
        }
        if (call.ongoing) main.post(tick)
    }

    private fun stop() {
        main.removeCallbacks(tick)
        pulse?.cancel()
        pulse = null
        timer = null
    }

    private fun duration(): String {
        val seconds = ((System.currentTimeMillis() - answeredAt) / 1000).coerceAtLeast(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = (seconds % 60).toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$s" else "$m:$s"
    }

    private fun status(call: CallAlert) = if (call.ongoing) "En appel · ${duration()}" else "Appel entrant"

    /** Up to two letters for a name, the last digits for a number. */
    private fun initials(caller: String): String {
        val words = caller.split(' ', '-').filter { it.isNotBlank() }
        if (words.isEmpty()) return "?"
        if (caller.count { it.isDigit() } >= 4) return ""
        return words.take(2).joinToString("") { it.first().uppercase() }
    }

    private fun avatar(call: CallAlert, size: Int) = FrameLayout(home).apply {
        val photo = call.photo?.let { runCatching { it.loadDrawable(home) }.getOrNull() }
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(home.shade(home.accent, Color.WHITE, .25f), home.shade(home.accent, Color.BLACK, .35f))).apply {
            shape = GradientDrawable.OVAL
        }
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) = outline.setOval(0, 0, view.width, view.height)
        }
        val letters = initials(call.caller)
        when {
            photo != null -> addView(ImageView(home).apply {
                setImageDrawable(photo)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(-1, -1))
            letters.isNotEmpty() -> addView(home.label(letters, size / 3, Color.WHITE, 700).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1))
            else -> addView(ImageView(home).apply {
                setImageResource(R.drawable.ic_home_phone)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
            }, FrameLayout.LayoutParams(home.dp(size * 2 / 5), home.dp(size * 2 / 5), Gravity.CENTER))
        }
    }

    private fun press(intent: PendingIntent) {
        try {
            intent.send()
        } catch (_: PendingIntent.CanceledException) {
            Toast.makeText(home, "L’appel n’est plus disponible.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isMute(action: CallAction) = action.title.lowercase().let { "mute" in it || "muet" in it || "micro" in it }

    private fun round(icon: Int, color: Int, iconColor: Int, turn: Float, word: String, click: () -> Unit) = FrameLayout(home).apply {
        background = home.pressable(GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }, 26)
        addView(ImageView(home).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(iconColor)
            rotation = turn
        }, FrameLayout.LayoutParams(home.dp(24), home.dp(24), Gravity.CENTER))
        contentDescription = word
        setOnClickListener { click() }
    }

    private fun strip(call: CallAlert) = home.row().apply {
        background = GradientDrawable().apply {
            cornerRadius = home.dp(20).toFloat()
            setColor(home.getColor(R.color.home_background_top))
            setStroke(home.dp(1), if (call.ongoing) home.getColor(R.color.home_card_border) else HomeActivity.CALL_GREEN)
        }
        elevation = home.dp(12).toFloat()
        setPadding(home.dp(10), home.dp(8), home.dp(10), home.dp(8))
        // The picture, with a ring that breathes while the phone rings.
        addView(FrameLayout(home).apply {
            val ring = View(home).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(home.dp(2), if (call.ongoing) home.accent else HomeActivity.CALL_GREEN)
                }
            }
            addView(ring, FrameLayout.LayoutParams(-1, -1))
            addView(avatar(call, 44), FrameLayout.LayoutParams(home.dp(44), home.dp(44), Gravity.CENTER))
            if (!call.ongoing) pulse = ObjectAnimator.ofFloat(ring, View.ALPHA, 1f, .15f).apply {
                duration = 900
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }, LinearLayout.LayoutParams(home.dp(54), home.dp(54)))
        addView(home.column().apply {
            val state = home.caps(status(call), if (call.ongoing) home.muted else HomeActivity.CALL_GREEN)
            if (call.ongoing) timer = state
            addView(state)
            addView(home.row().apply {
                addView(home.label(call.caller, 20, home.text, 700), LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = home.dp(3) })
            call.detail?.takeIf { it != call.caller }?.let { addView(home.label(it, 12, home.muted), home.wrap().apply { topMargin = home.dp(2) }) }
            setOnClickListener { runCatching { call.open?.send() } }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = home.dp(12) })
        val decline = call.actions.firstOrNull { it.kind == CallButton.DECLINE }
        val answer = call.actions.firstOrNull { it.kind == CallButton.ANSWER }
        val others = call.actions.filter { it.kind == CallButton.OTHER }.take(2)
        others.forEach { action ->
            addView(round(if (isMute(action)) R.drawable.ic_home_mic_off else R.drawable.ic_home_volume,
                home.getColor(R.color.home_chip), home.text, 0f, action.title) { press(action.intent) },
                LinearLayout.LayoutParams(home.dp(52), home.dp(52)).apply { marginStart = home.dp(10) })
        }
        // The microphone, cut by Android itself when the phone app offers no button for it.
        if (call.ongoing && others.none(::isMute)) {
            addView(round(R.drawable.ic_home_mic_off, if (mutedHere) home.accent else home.getColor(R.color.home_chip),
                if (mutedHere) home.onAccent else home.text, 0f, if (mutedHere) "Micro coupé" else "Couper le micro") {
                val manager = audio
                if (manager != null && runCatching { manager.isMicrophoneMute = !mutedHere }.isSuccess) {
                    mutedHere = manager.isMicrophoneMute
                    render(CallWatch.call.value)
                }
            }, LinearLayout.LayoutParams(home.dp(52), home.dp(52)).apply { marginStart = home.dp(10) })
        }
        decline?.let {
            addView(round(R.drawable.ic_home_phone, HomeActivity.CALL_RED, Color.WHITE, 135f,
                "${if (call.ongoing) "Raccrocher" else "Refuser"} ${call.caller}") { press(it.intent) },
                LinearLayout.LayoutParams(home.dp(52), home.dp(52)).apply { marginStart = home.dp(10) })
        }
        answer?.let {
            addView(round(R.drawable.ic_home_phone, HomeActivity.CALL_GREEN, Color.WHITE, 0f, "Répondre ${call.caller}") { press(it.intent) },
                LinearLayout.LayoutParams(home.dp(52), home.dp(52)).apply { marginStart = home.dp(10) })
        }
    }
}
