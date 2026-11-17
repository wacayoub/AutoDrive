package com.incardrive.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * The guidance bubble: while Google Maps or Waze guides and another app is in front (YouTube, the radio…), a small
 * see-through card over it with the turn arrow, the distance to the turn, the instruction, the time left and the
 * arrival, read from the navigation app's notification. A tap brings the navigation app back; it can be dragged and
 * closed with ✕ until the next trip. Drawn with Android's documented « display over other apps » permission, like
 * Radio Maroc & Coran's bubble, from the notification service that already reads the guidance.
 */
internal class NavBubble(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    private var card: LinearLayout? = null
    private lateinit var arrow: ImageView
    private lateinit var step: TextView
    private lateinit var instruction: TextView
    private lateinit var eta: TextView
    private lateinit var params: WindowManager.LayoutParams
    private var dismissed = false

    private val enabled get() = prefs.getBoolean(ENABLED, true) && Settings.canDrawOverlays(context)

    /** Shows the guidance while it lasts and Incar Drive's home screen is away; hides it otherwise. */
    fun render(nav: NavInfo?, packageName: String?) {
        if (nav == null) dismissed = false
        // Over the navigation app itself the bubble would only repeat what that app already shows.
        val overNavigation = HomeVisibility.front != null && (HomeVisibility.front == packageName || HomeVisibility.front in NAVIGATION_APPS)
        if (nav == null || dismissed || HomeVisibility.shown || overNavigation || !enabled) {
            hide()
            return
        }
        val view = card ?: build(packageName) ?: return
        if (nav.arrow != null) arrow.setImageDrawable(nav.arrow) else arrow.setImageResource(R.drawable.ic_home_navigation)
        arrow.rotation = if (nav.arrow != null) 0f else 45f
        val title = nav.step ?: nav.remaining ?: "Guidage"
        step.text = title
        // The second line only when it adds something: Maps sometimes repeats the distance there.
        fun plain(value: String) = value.filterNot { it.isWhitespace() }.lowercase()
        val line = nav.instruction?.takeIf { it.isNotBlank() && plain(it) != plain(title) }
        instruction.text = line.orEmpty()
        instruction.visibility = if (line == null) View.GONE else View.VISIBLE
        eta.text = listOfNotNull(nav.remaining?.takeIf { nav.step != null }, nav.arrival).joinToString("\n")
        eta.visibility = if (eta.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        view.tag = packageName
        view.visibility = View.VISIBLE
    }

    fun hide() {
        val view = card ?: return
        card = null
        runCatching { windowManager.removeView(view) }
    }

    private fun build(packageName: String?): LinearLayout? {
        fun text(size: Float, color: Int, bold: Boolean) = TextView(context).apply {
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) typeface = Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 700, false)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(dp(2).toFloat(), 0f, 0f, 0x80000000.toInt())
        }
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xCC14233F.toInt(), 0xB30A1222.toInt())).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0x405B9BFF)
            }
            elevation = dp(8).toFloat()
            setPadding(dp(10), dp(8), dp(8), dp(8))
            arrow = ImageView(context).apply { imageTintList = ColorStateList.valueOf(Color.WHITE) }
            addView(android.widget.FrameLayout(context).apply {
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xFF2563EB.toInt()) }
                addView(arrow, android.widget.FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                step = text(22f, Color.WHITE, true)
                instruction = text(12f, 0xE6FFFFFF.toInt(), false)
                addView(step)
                addView(instruction)
            }, LinearLayout.LayoutParams(dp(170), -2).apply { marginStart = dp(10) })
            eta = text(12f, 0xFF8FB8FF.toInt(), true).apply { maxLines = 2; gravity = Gravity.END }
            addView(eta, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_home_close)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x26FFFFFF) }
                contentDescription = "Masquer la bulle de guidage"
                setOnClickListener {
                    dismissed = true
                    hide()
                }
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(8) })
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Top centre by default, away from the radio's bubble at the bottom.
            gravity = Gravity.TOP or Gravity.START
            val metrics = context.resources.displayMetrics
            x = prefs.getInt(X, (metrics.widthPixels - dp(330)) / 2).coerceAtLeast(0)
            y = prefs.getInt(Y, dp(8)).coerceAtLeast(0)
        }
        // Drag by the arrow and the text; a light tap there brings the navigation app back.
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val drag = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        params.x = (startX + dx).toInt().coerceAtLeast(0)
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        card?.let { runCatching { windowManager.updateViewLayout(it, params) } }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) prefs.edit().putInt(X, params.x).putInt(Y, params.y).apply()
                    else (card?.tag as? String ?: packageName)?.let { pkg ->
                        HomeVisibility.front = pkg
                        context.packageManager.getLaunchIntentForPackage(pkg)
                            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            ?.let { runCatching { context.startActivity(it) } }
                    }
                }
            }
            true
        }
        view.getChildAt(0).setOnTouchListener(drag)
        view.getChildAt(1).setOnTouchListener(drag)
        return runCatching { windowManager.addView(view, params) }.map { view }.getOrNull()?.also { card = it }
    }

    companion object {
        // Read by the notification service, written by the home screen's settings.
        const val PREFS = "incar_drive_home"
        const val ENABLED = "nav_bubble"
        const val X = "nav_bubble_x"
        const val Y = "nav_bubble_y"
        val NAVIGATION_APPS = setOf("com.google.android.apps.maps", "com.waze")
    }
}

/** Whether Incar Drive's home screen is on screen, so the bubble steps aside there (the card shows the guidance). */
internal object HomeVisibility {
    @Volatile var shown = false
    /**
     * The app last opened from the home screen, which as the launcher starts nearly all of them: how the bubble
     * knows the navigation app itself is in front, without asking for Android's usage access. Null when unknown.
     */
    @Volatile var front: String? = null
        set(value) { field = value; changed?.invoke() }
    var changed: (() -> Unit)? = null
}
