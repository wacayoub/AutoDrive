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
 * The call, large: who calls (with the picture the phone app gives, or the initials), how long the call has lasted,
 * and big round buttons that press the phone app's own Answer, Decline and other buttons. It can be made small, a
 * strip at the top, to keep the road and the map in view during a long call.
 */
internal class CallScreen(private val home: HomeActivity, private val host: FrameLayout) {
    private val main = Handler(Looper.getMainLooper())
    private var shownKey: String? = null
    private var small = false
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
        if (call.key != shownKey) { small = false; answeredAt = 0L; shownKey = call.key }
        if (call.ongoing && answeredAt == 0L) answeredAt = System.currentTimeMillis()
        stop()
        host.removeAllViews()
        host.addView(if (small) strip(call) else full(call),
            if (small) FrameLayout.LayoutParams(-1, -2, Gravity.TOP) else FrameLayout.LayoutParams(-1, -1))
        // The large screen takes every touch; the strip lets the home screen under it be used.
        host.isClickable = !small
        host.visibility = View.VISIBLE
        if (!wasShown) {
            host.alpha = 0f
            host.animate().alpha(1f).setDuration(200).start()
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

    /** A round button with its word under it. */
    private fun button(icon: Int, word: String, color: Int, iconColor: Int, turn: Float = 0f, click: () -> Unit) =
        home.column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(FrameLayout(home).apply {
                background = home.pressable(GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }, 36)
                elevation = home.dp(6).toFloat()
                addView(ImageView(home).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(iconColor)
                    rotation = turn
                }, FrameLayout.LayoutParams(home.dp(30), home.dp(30), Gravity.CENTER))
                contentDescription = word
                setOnClickListener { click() }
            }, LinearLayout.LayoutParams(home.dp(70), home.dp(70)))
            addView(home.label(word, 13, home.text, 600).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(home.dp(96), -2).apply { topMargin = home.dp(7) })
        }

    private fun full(call: CallAlert) = FrameLayout(home).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            home.shade(home.getColor(R.color.home_background_top), home.accent, .14f),
            home.getColor(R.color.home_background_top))).apply { cornerRadius = home.dp(22).toFloat() }
        elevation = home.dp(16).toFloat()
        addView(home.row().apply {
            setPadding(home.dp(34), home.dp(18), home.dp(34), home.dp(18))
            // The picture, with a ring that breathes while the phone rings.
            addView(FrameLayout(home).apply {
                val ring = View(home).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setStroke(home.dp(3), if (call.ongoing) home.accent else HomeActivity.CALL_GREEN)
                    }
                }
                addView(ring, FrameLayout.LayoutParams(-1, -1))
                addView(avatar(call, 132), FrameLayout.LayoutParams(home.dp(132), home.dp(132), Gravity.CENTER))
                if (!call.ongoing) pulse = ObjectAnimator.ofFloat(ring, View.ALPHA, 1f, .15f).apply {
                    duration = 900
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { ring.scaleX = 1f + (1f - ring.alpha) * .08f; ring.scaleY = ring.scaleX }
                    start()
                }
            }, LinearLayout.LayoutParams(home.dp(156), home.dp(156)))
            addView(home.column().apply {
                val state = home.caps(status(call), if (call.ongoing) home.accent else HomeActivity.CALL_GREEN).apply { textSize = 13f }
                if (call.ongoing) timer = state
                addView(state)
                addView(home.label(call.caller, 34, home.text, 700).apply { maxLines = 2 },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = home.dp(8) })
                call.detail?.takeIf { it != call.caller }?.let {
                    addView(home.label(it, 15, home.muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = home.dp(6) })
                }
                addView(home.row().apply {
                    val decline = call.actions.firstOrNull { it.kind == CallButton.DECLINE }
                    val answer = call.actions.firstOrNull { it.kind == CallButton.ANSWER }
                    val others = call.actions.filter { it.kind == CallButton.OTHER }.take(2)
                    decline?.let {
                        addView(button(R.drawable.ic_home_phone, if (call.ongoing) "Raccrocher" else "Refuser",
                            HomeActivity.CALL_RED, Color.WHITE, 135f) { press(it.intent) })
                    }
                    others.forEach { action ->
                        addView(button(if (isMute(action)) R.drawable.ic_home_mic_off else R.drawable.ic_home_volume, action.title,
                            home.getColor(R.color.home_chip), home.text) { press(action.intent) },
                            LinearLayout.LayoutParams(-2, -2).apply { marginStart = home.dp(10) })
                    }
                    // The microphone, cut by Android itself when the phone app offers no button for it.
                    if (call.ongoing && others.none(::isMute)) {
                        addView(button(R.drawable.ic_home_mic_off, if (mutedHere) "Micro coupé" else "Couper micro",
                            if (mutedHere) home.accent else home.getColor(R.color.home_chip),
                            if (mutedHere) home.onAccent else home.text) {
                            val manager = audio
                            if (manager != null && runCatching { manager.isMicrophoneMute = !mutedHere }.isSuccess) {
                                mutedHere = manager.isMicrophoneMute
                                render(CallWatch.call.value)
                            }
                        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = home.dp(10) })
                    }
                    answer?.let {
                        addView(button(R.drawable.ic_home_phone, "Répondre", HomeActivity.CALL_GREEN, Color.WHITE) { press(it.intent) },
                            LinearLayout.LayoutParams(-2, -2).apply { marginStart = home.dp(10) })
                    }
                    addView(button(R.drawable.ic_home_chevron, "Réduire", home.getColor(R.color.home_chip), home.text, 90f) {
                        small = true
                        render(call)
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = home.dp(10) })
                }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = home.dp(22) })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = home.dp(30) })
        }, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
    }

    /** The call made small: a strip at the top; a touch on the name makes it large again. */
    private fun strip(call: CallAlert) = home.row().apply {
        background = GradientDrawable().apply {
            cornerRadius = home.dp(18).toFloat()
            setColor(home.getColor(R.color.home_background_top))
            setStroke(home.dp(1), home.getColor(R.color.home_card_border))
        }
        elevation = home.dp(12).toFloat()
        setPadding(home.dp(10), home.dp(8), home.dp(10), home.dp(8))
        addView(avatar(call, 46), LinearLayout.LayoutParams(home.dp(46), home.dp(46)))
        addView(home.column().apply {
            val state = home.caps(status(call), if (call.ongoing) home.muted else HomeActivity.CALL_GREEN)
            if (call.ongoing) timer = state
            addView(state)
            addView(home.label(call.caller, 20, home.text, 700), home.wrap().apply { topMargin = home.dp(3) })
            setOnClickListener { small = false; render(call) }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = home.dp(12) })
        call.actions.filter { it.kind != CallButton.OTHER }.sortedBy { if (it.kind == CallButton.DECLINE) 0 else 1 }.forEach { action ->
            val answer = action.kind == CallButton.ANSWER
            addView(FrameLayout(home).apply {
                background = home.pressable(GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (answer) HomeActivity.CALL_GREEN else HomeActivity.CALL_RED)
                }, 26)
                addView(ImageView(home).apply {
                    setImageResource(R.drawable.ic_home_phone)
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                    rotation = if (answer) 0f else 135f
                }, FrameLayout.LayoutParams(home.dp(24), home.dp(24), Gravity.CENTER))
                contentDescription = "${action.title} ${call.caller}"
                setOnClickListener { press(action.intent) }
            }, LinearLayout.LayoutParams(home.dp(52), home.dp(52)).apply { marginStart = home.dp(10) })
        }
    }
}
