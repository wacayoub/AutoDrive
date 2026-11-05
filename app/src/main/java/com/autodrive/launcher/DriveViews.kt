package com.autodrive.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt

internal object Palette {
    val background = 0xFF0D1117.toInt()
    val surface = 0xFF15191F.toInt()
    val raised = 0xFF202630.toInt()
    val border = 0xFF2B3541.toInt()
    val blue = 0xFF168BFF.toInt()
    val text = 0xFFF5F7FA.toInt()
    val muted = 0xFFAAB4C0.toInt()
    val dim = 0xFF5B6673.toInt()
    val green = 0xFF34A853.toInt()
}

internal class DriveViews(val context: Context) {
    private val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).roundToInt()
    fun label(value: String, size: Int, color: Int = Palette.text, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
            setTextColor(color)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun line(vertical: Boolean = false) = LinearLayout(context).apply {
        orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        gravity = if (vertical) Gravity.NO_GRAVITY else Gravity.CENTER_VERTICAL
    }

    fun shape(color: Int, radius: Int, outline: Int = Color.TRANSPARENT, end: Int? = null) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(color, end ?: color)).apply {
            cornerRadius = dp(radius).toFloat()
            if (outline != Color.TRANSPARENT) setStroke(dp(1), outline)
        }

    fun touch(view: View, color: Int, radius: Int, outline: Int = Color.TRANSPARENT,
              end: Int? = null, action: () -> Unit) {
        view.background = RippleDrawable(
            ColorStateList.valueOf(0x40FFFFFF), shape(color, radius, outline, end), shape(Color.WHITE, radius)
        )
        view.isFocusable = true
        view.setOnClickListener { action() }
    }

    fun icon(name: String, color: Int = Palette.text, size: Int = 24) = DriveIcon(context, name, color, dp(size))

    fun action(value: String, iconName: String? = null, selected: Boolean = false,
               iconColor: Int = Palette.text, run: () -> Unit): LinearLayout = line().apply {
        gravity = Gravity.CENTER
        minimumHeight = dp(48)
        setPadding(dp(6), 0, dp(6), 0)
        if (iconName != null) {
            addView(icon(iconName, iconColor), LinearLayout.LayoutParams(dp(25), dp(27)).apply {
                marginEnd = dp(9)
            })
        }
        addView(label(value, 17).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
        contentDescription = value
        touch(this, if (selected) 0xFF163C62.toInt() else Palette.raised, 14, Palette.border,
            action = run)
    }

    fun LinearLayout.gap(size: Int) {
        val vertical = orientation == LinearLayout.VERTICAL
        addView(View(context), if (vertical) ViewGroup.LayoutParams(1, dp(size))
            else ViewGroup.LayoutParams(dp(size), 1))
    }
}

@SuppressLint("ViewConstructor") // Created by DriveViews with its icon and color.
internal class DriveIcon(context: Context, var name: String, var color: Int, private val size: Int) : View(context) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    fun style(name: String = this.name, color: Int = this.color) {
        if (this.name == name && this.color == color) return
        this.name = name
        this.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(size, min(width, height)).toFloat()
        canvas.save()
        canvas.translate((width - side) / 2, (height - side) / 2)
        canvas.scale(side / 24, side / 24)
        pen.color = color
        pen.style = Paint.Style.STROKE
        fun line(vararg points: Float, close: Boolean = false, fill: Boolean = false) {
            path.reset()
            path.moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
            if (close) path.close()
            pen.style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
            canvas.drawPath(path, pen)
            pen.style = Paint.Style.STROKE
        }
        fun box(x: Float, y: Float, w: Float, h: Float, radius: Float = 1.5f) =
            canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, pen)

        when (name) {
            "brand" -> {
                line(1f, 19f, 10f, 5f, 14f, 5f, 5f, 19f, close = true, fill = true)
                line(8f, 19f, 17f, 5f, 21f, 5f, 12f, 19f, close = true, fill = true)
            }
            "wifi" -> {
                for (r in listOf(11f, 8f, 4.5f)) {
                    rect.set(12 - r, 18 - r, 12 + r, 18 + r)
                    canvas.drawArc(rect, 223f, 94f, false, pen)
                }
                pen.style = Paint.Style.FILL
                canvas.drawCircle(12f, 17.5f, 1.25f, pen)
            }
            "bluetooth" -> line(6f, 6f, 17f, 17f, 12f, 22f, 12f, 2f, 17f, 7f, 6f, 18f)
            "network" -> for (i in 0..3) canvas.drawLine(5f + 4f * i, 19f, 5f + 4f * i, 16f - i * 3f, pen)
            "apps" -> for (i in listOf(4f, 13.5f)) for (j in listOf(4f, 13.5f)) box(i, j, 6.5f, 6.5f)
            "settings" -> {
                canvas.drawCircle(12f, 12f, 7f, pen)
                canvas.drawCircle(12f, 12f, 3f, pen)
                repeat(8) {
                    canvas.drawLine(12f, 3f, 12f, 5f, pen)
                    canvas.rotate(45f, 12f, 12f)
                }
            }
            "phone" -> line(5f, 3f, 9f, 2f, 11f, 7f, 8f, 10f, 11f, 14f, 14f, 16f, 17f, 13f,
                22f, 16f, 21f, 20f, 17f, 22f, 11f, 20f, 6f, 15f, 3f, 9f, 3f, 5f, close = true, fill = true)
            "radio" -> {
                line(5f, 7f, 19f, 3f)
                box(3f, 8f, 18f, 12f)
                canvas.drawCircle(8f, 14f, 2.5f, pen)
                line(14f, 13f, 18f, 13f)
                line(14f, 16f, 18f, 16f)
            }
            "tv" -> {
                line(8f, 3f, 12f, 7f, 17f, 3f)
                box(3f, 7f, 18f, 13f)
            }
            "nav" -> line(12f, 2f, 21f, 21f, 12f, 16f, 3f, 21f, close = true, fill = true)
            "play" -> line(7f, 4f, 20f, 12f, 7f, 20f, close = true, fill = true)
            "media" -> line(6f, 4f, 20f, 12f, 6f, 20f, close = true)
            "pause" -> {
                pen.strokeWidth = 4f
                line(8f, 5f, 8f, 19f)
                line(16f, 5f, 16f, 19f)
                pen.strokeWidth = 1.8f
            }
            "prev", "next" -> {
                if (name == "prev") canvas.scale(-1f, 1f, 12f, 12f)
                line(5f, 5f, 15f, 12f, 5f, 19f, close = true, fill = true)
                line(18f, 5f, 18f, 19f)
            }
        }
        canvas.restore()
    }
}
