package com.autodrive.radiomaroc

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Small view builders shared by the radio screen, its tiles and its dialogs. */
internal const val RADIO_PREFERENCES = "radio_maroc"

internal val Context.isTelevision: Boolean
    get() = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

internal fun Context.row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

internal fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

internal fun Context.label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
    text = value
    textSize = size.toFloat()
    setTextColor(color)
    textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

internal fun Context.button(value: String, action: () -> Unit) =
    label(value, 14, getColor(R.color.radio_text), bold = true).apply {
        gravity = Gravity.CENTER
        background = pressable(R.drawable.radio_control)
        isFocusable = true
        remoteFocus()
        setRadioClick(action)
    }

internal fun Context.pressable(drawable: Int? = null) = RippleDrawable(
    ColorStateList.valueOf(getColor(R.color.radio_ripple)), drawable?.let(::getDrawable),
    GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() },
)

internal fun View.setRadioClick(action: () -> Unit) {
    isSoundEffectsEnabled = false
    setOnClickListener {
        RadioSound.playClick(context, context.getSharedPreferences(RADIO_PREFERENCES, Context.MODE_PRIVATE))
        action()
    }
}

/** Remote controls need a visible focus ring; touch screens keep the plain ripple. */
internal fun View.remoteFocus() {
    if (context.isTelevision) foreground = context.getDrawable(R.drawable.radio_focus)
}

internal fun enable(view: View, enabled: Boolean) {
    view.isEnabled = enabled
    view.alpha = if (enabled) 1f else .45f
}
