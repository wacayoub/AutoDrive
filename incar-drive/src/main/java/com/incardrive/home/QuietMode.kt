package com.incardrive.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Quiet mode, for driving at night: the whole screen goes black and keeps only the clock, the speed in large
 * figures and, while the navigation app guides, the next turn. A touch anywhere brings the home screen back.
 */
internal class QuietMode(private val home: HomeActivity, private val closed: () -> Unit) {
    private lateinit var clock: TextView
    private lateinit var speedText: TextView
    private lateinit var turnRow: LinearLayout
    private lateinit var turnArrow: ImageView
    private lateinit var turnText: TextView
    private var root: FrameLayout? = null

    fun show(host: FrameLayout) = with(home) {
        val view = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            contentDescription = "Mode calme. Toucher pour revenir à l’accueil."
            clock = label("", 22, 0xB3FFFFFF.toInt(), 300, condensed = true)
            addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(22), dp(14), 0, 0) })
            addView(column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                speedText = label("—", 120, Color.WHITE, 300, condensed = true).apply { gravity = Gravity.CENTER; includeFontPadding = false }
                addView(speedText)
                addView(label("KM/H", 13, 0x99FFFFFF.toInt(), 600).apply { letterSpacing = .2f; gravity = Gravity.CENTER })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            turnRow = row().apply {
                gravity = Gravity.CENTER_VERTICAL
                turnArrow = ImageView(context).apply { imageTintList = ColorStateList.valueOf(Color.WHITE) }
                addView(turnArrow, LinearLayout.LayoutParams(dp(34), dp(34)))
                turnText = label("", 20, Color.WHITE, 600, condensed = true).apply { maxLines = 1 }
                addView(turnText, wrap().apply { marginStart = dp(12) })
                visibility = View.GONE
            }
            addView(turnRow, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(26) })
            addView(label("Toucher pour revenir", 11, 0x66FFFFFF, 500),
                FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(20), dp(22), 0) })
            setOnClickListener { hide() }
        }
        root = view
        host.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(250).start()
        tick()
    }

    fun hide() {
        val view = root ?: return
        root = null
        view.animate().alpha(0f).setDuration(200).withEndAction { (view.parent as? FrameLayout)?.removeView(view) }.start()
        closed()
    }

    fun tick() {
        if (root != null) clock.text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    fun speed(kmh: Int?, over: Boolean) {
        if (root == null) return
        speedText.text = kmh?.toString() ?: "—"
        speedText.setTextColor(if (over) 0xFFFF4D4F.toInt() else Color.WHITE)
    }

    fun turn(nav: NavInfo?) {
        if (root == null) return
        if (nav == null) {
            turnRow.visibility = View.GONE
            return
        }
        if (nav.arrow != null) {
            turnArrow.setImageDrawable(nav.arrow)
            turnArrow.rotation = 0f
        } else {
            turnArrow.setImageResource(R.drawable.ic_home_navigation)
            turnArrow.rotation = 45f
        }
        turnText.text = listOfNotNull(nav.step ?: nav.remaining, nav.arrival?.let { "arrivée $it" }).joinToString("  ·  ")
        turnRow.visibility = View.VISIBLE
    }
}
