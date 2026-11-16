package com.autodrive.radiomaroc

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
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
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** How the radio stays in sight on the head unit while Maps or YouTube is in front. */
internal enum class MiniPlayerMode(val label: String) {
    BUBBLE("Bulle flottante"), WINDOW("Fenêtre Android"), OFF("Désactivé");

    companion object {
        const val KEY = "mini_player_mode"
        fun restore(prefs: SharedPreferences): MiniPlayerMode =
            entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: OFF
    }
}

/**
 * The floating bubble: a small see-through card drawn over Maps, YouTube or the home screen with Android's
 * documented « display over other apps » permission, for head units whose system offers no picture-in-picture.
 * The playback service shows it while the radio's own screen is away; the buttons work like the steering wheel's.
 */
internal class FloatingPlayer(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onPrevious: () -> Unit,
    private val onToggle: () -> Unit,
    private val onNext: () -> Unit,
    private val onOpen: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    private var card: LinearLayout? = null
    private lateinit var logo: ImageView
    private lateinit var initials: TextView
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private lateinit var toggle: ImageButton
    private lateinit var params: WindowManager.LayoutParams
    // Closed with ✕: stays away until the radio's screen comes back.
    private var dismissed = false
    private var shownLogo: ByteArray? = null

    val permitted: Boolean get() = Settings.canDrawOverlays(context)

    /** The radio's screen came to the front: the bubble may show again the next time it leaves. */
    fun screenShown() {
        dismissed = false
        hide()
    }

    fun render(visible: Boolean, name: CharSequence?, subtitle: CharSequence?, logoBytes: ByteArray?, playing: Boolean) {
        if (!visible || dismissed || !permitted || MiniPlayerMode.restore(prefs) != MiniPlayerMode.BUBBLE) {
            hide()
            return
        }
        val view = card ?: build() ?: return
        title.text = name ?: context.getString(R.string.app_name)
        detail.text = subtitle
        detail.visibility = if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        if (logoBytes !== shownLogo) {
            shownLogo = logoBytes
            val bitmap = logoBytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
            logo.setImageBitmap(bitmap)
            logo.visibility = if (bitmap != null) View.VISIBLE else View.INVISIBLE
            initials.visibility = if (bitmap != null) View.INVISIBLE else View.VISIBLE
        }
        initials.text = name?.trim()?.take(1) ?: "ر"
        toggle.setImageResource(if (playing) R.drawable.ic_radio_pause else R.drawable.ic_radio_play)
        toggle.contentDescription = if (playing) "Pause" else "Écouter"
        view.visibility = View.VISIBLE
    }

    fun hide() {
        val view = card ?: return
        card = null
        // A new card is built next time: let it draw the logo again.
        shownLogo = null
        runCatching { windowManager.removeView(view) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build(): LinearLayout? {
        val glass = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xCC1D2125.toInt(), 0xB30E1013.toInt())).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), 0x40FFFFFF)
        }
        fun text(size: Float, color: Int, bold: Boolean) = TextView(context).apply {
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(dp(2).toFloat(), 0f, 0f, 0x80000000.toInt())
        }
        fun control(icon: Int, description: String, action: () -> Unit) = ImageButton(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x26FFFFFF)
            }
            contentDescription = description
            setOnClickListener { action() }
        }
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = glass
            elevation = dp(8).toFloat()
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val art = android.widget.FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(0x33FFFFFF)
                }
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) =
                        outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
                }
                initials = text(22f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
                addView(initials, android.widget.FrameLayout.LayoutParams(-1, -1))
                logo = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                addView(logo, android.widget.FrameLayout.LayoutParams(-1, -1))
            }
            addView(art, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                title = text(15f, Color.WHITE, true)
                detail = text(11f, 0xD9FFFFFF.toInt(), false)
                addView(title)
                addView(detail)
            }, LinearLayout.LayoutParams(dp(132), -2).apply { marginStart = dp(10); marginEnd = dp(6) })
            addView(control(R.drawable.ic_radio_previous, "Station précédente", onPrevious),
                LinearLayout.LayoutParams(dp(40), dp(40)))
            toggle = control(R.drawable.ic_radio_play, "Écouter", onToggle)
            addView(toggle, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(4); marginEnd = dp(4) })
            addView(control(R.drawable.ic_radio_next, "Station suivante", onNext), LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(control(R.drawable.ic_radio_close, "Masquer la bulle") {
                dismissed = true
                hide()
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(6) })
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val metrics = context.resources.displayMetrics
            x = prefs.getInt(X, metrics.widthPixels - dp(440)).coerceAtLeast(0)
            y = prefs.getInt(Y, metrics.heightPixels - dp(110)).coerceAtLeast(0)
        }
        // Drag the bubble by its logo and names; a light tap there opens the radio.
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
                    if (dragging) prefs.edit().putInt(X, params.x).putInt(Y, params.y).apply() else onOpen()
                }
            }
            true
        }
        view.getChildAt(0).setOnTouchListener(drag)
        view.getChildAt(1).setOnTouchListener(drag)
        return runCatching { windowManager.addView(view, params) }.map { view }.getOrNull()?.also { card = it }
    }

    private companion object {
        const val X = "bubble_x"
        const val Y = "bubble_y"
    }
}
