package com.autodrive.radiomaroc

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Dashboard tiles under the player. [compact] shrinks them for small television layouts. */
@SuppressLint("ViewConstructor") // Built in code only, never inflated.
internal class WeatherTile(context: Context, compact: Boolean, open: () -> Unit) : LinearLayout(context) {
    private val headingColor = context.getColor(R.color.radio_weather_label)
    private val icon = WeatherIcon(context, headingColor)
    private val temperature = context.tileLine("", if (compact) 15 else 19, context.getColor(R.color.radio_text), bold = true)
    private val detail = context.tileLine("", if (compact) 10 else 12, context.getColor(R.color.radio_muted))

    init {
        prepareLocalTile(open)
        setPadding(context.dp(2), 0, context.dp(2), 0)
        addView(context.tileLine("Météo", if (compact) 10 else 12, headingColor, bold = true), LayoutParams(-1, -2))
        addView(context.row().apply {
            gravity = Gravity.CENTER
            val size = context.dp(if (compact) 17 else 23)
            addView(icon, LayoutParams(size, size).apply { marginEnd = context.dp(3) })
            addView(temperature, LayoutParams(-2, -2))
        }, LayoutParams(-1, -2).apply { topMargin = context.dp(2) })
        addView(detail, LayoutParams(-1, -2))
    }

    fun bind(report: RadioWeather?, city: RadioCity) {
        icon.bind(report)
        temperature.text = report?.let { "${it.degrees}°" } ?: "—"
        detail.text = report?.condition?.label ?: "Indisponible"
        contentDescription = listOfNotNull("Météo", city.label, report?.let { "${it.degrees} °C" }, detail.text)
            .joinToString(" · ")
    }
}

@SuppressLint("ViewConstructor") // Built in code only, never inflated.
internal class PrayerTile(context: Context, private val compact: Boolean, open: () -> Unit) : LinearLayout(context) {
    private val headingColor = context.getColor(R.color.radio_prayer_label)
    private val textColor = context.getColor(R.color.radio_text)
    private val currentName = context.tileLine("", if (compact) 10 else 12, textColor, bold = true)
    private val nextName = context.tileLine("", if (compact) 10 else 12, textColor, bold = true)
    private val currentTime = context.tileLine("", if (compact) 12 else 15, textColor, bold = true)
    private val nextTime = context.tileLine("", if (compact) 12 else 15, headingColor, bold = true)

    init {
        prepareLocalTile(open)
        addView(context.tileLine("Salat", if (compact) 10 else 12, headingColor, bold = true), LayoutParams(-1, -2))
        addView(context.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(prayerColumn("Maint.", currentName, currentTime), LayoutParams(0, -2, 1f))
            addView(View(context).apply { setBackgroundColor(context.getColor(R.color.radio_control_border)) },
                LayoutParams(context.dp(1), context.dp(if (compact) 28 else 34)))
            addView(prayerColumn("Ensuite", nextName, nextTime), LayoutParams(0, -2, 1f))
        }, LayoutParams(-1, -2).apply { topMargin = context.dp(2) })
    }

    private fun prayerColumn(label: String, name: TextView, time: TextView) = context.column().apply {
        addView(context.tileLine(label, if (compact) 9 else 11, context.getColor(R.color.radio_muted)), LayoutParams(-1, -2))
        addView(name, LayoutParams(-1, -2))
        addView(time, LayoutParams(-1, -2))
    }

    fun bind(status: PrayerStatus, city: RadioCity) {
        currentName.text = status.current?.prayer?.label ?: "—"
        nextName.text = status.next?.prayer?.label ?: "—"
        currentTime.text = status.current?.dateTime?.toLocalTime()?.toString().orEmpty()
        nextTime.text = status.next?.dateTime?.toLocalTime()?.toString().orEmpty()
        contentDescription = "Salat · ${city.label} · Maintenant ${prayerDescription(status.current, status.date)}" +
            " · Ensuite ${prayerDescription(status.next, status.date)}"
    }
}

private fun LinearLayout.prepareLocalTile(action: () -> Unit) {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    setPadding(context.dp(4), 0, context.dp(4), 0)
    background = context.pressable(R.drawable.radio_control)
    isFocusable = true
    remoteFocus()
    setRadioClick(action)
}

private fun Context.tileLine(text: String, size: Int, color: Int, bold: Boolean = false) =
    label(text, size, color, bold).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
