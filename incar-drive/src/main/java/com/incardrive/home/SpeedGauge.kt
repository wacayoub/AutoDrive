package com.incardrive.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The speed dial: an open 240° arc sized to fill its card, graduated every 10 km/h and numbered every 40, a lit arc
 * with a soft glow and a bright tip that eases to the GPS speed, and the speed in large figures. The scale grows past
 * 120 and 180 km/h so the arc never pins. Above the chosen limit, arc, glow and figures turn red.
 */
internal class SpeedGauge(context: Context, private val track: Int, private val from: Int, private val to: Int,
    private val textColor: Int, private val mutedColor: Int) : View(context), SpeedDial {
    private val density = resources.displayMetrics.density
    private val stroke = 9 * density
    private val condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private val trackPaint = arc(stroke).apply { color = track }
    private val arcPaint = arc(stroke)
    private val glowPaint = arc(stroke * 2.6f)
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(condensed, 700, false)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedColor
        textAlign = Paint.Align.CENTER
        letterSpacing = .12f
        typeface = Typeface.create(Typeface.DEFAULT, 600, false)
    }
    private val scalePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedColor
        textAlign = Paint.Align.CENTER
        textSize = 9 * density
        typeface = Typeface.create(condensed, 500, false)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        color = mutedColor
    }
    private val limitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3 * density
        strokeCap = Paint.Cap.ROUND
        color = ALERT
    }
    private val box = RectF()
    private var radius = 0f

    private fun arc(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = width
    }

    /** Above this speed the arc and the number turn red; null turns the alert off. */
    override var limit: Int? = null
        set(value) { field = value; invalidate() }
    val over get() = limit?.let { (speed ?: 0) > it } == true
    private var shown = 0f
    private var speed: Int? = null
    private var easing: ValueAnimator? = null

    override fun setSpeed(value: Int?) {
        speed = value
        val target = (value ?: 0).toFloat()
        easing?.cancel()
        easing = ValueAnimator.ofFloat(shown, target).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
        contentDescription = value?.let { "$it km/h" } ?: "Vitesse inconnue"
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // An arc open by 120° at the bottom is 2r wide and 1.5r tall: size it to whichever is tighter.
        val pad = stroke * 1.4f
        radius = min((w - 2 * pad) / 2f, (h - 2 * pad) / 1.5f)
        val cx = w / 2f
        val cy = (h - radius * 1.5f) / 2f + radius
        box.set(cx - radius, cy - radius, cx + radius, cy + radius)
        arcPaint.shader = sweep(from, to)
        valuePaint.textSize = radius * .62f
        unitPaint.textSize = radius * .15f
    }

    // The colours run along the arc, from its start (lower left) to its end (lower right).
    private fun sweep(start: Int, end: Int) = SweepGradient(box.centerX(), box.centerY(), intArrayOf(start, end, end, start),
        floatArrayOf(0f, .67f, .9f, 1f)).apply {
        setLocalMatrix(Matrix().apply { setRotate(150f, box.centerX(), box.centerY()) })
    }

    private fun point(degrees: Float, distance: Float) = Math.toRadians(degrees.toDouble()).let {
        floatArrayOf(box.centerX() + distance * cos(it).toFloat(), box.centerY() + distance * sin(it).toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val scale = when {
            shown <= 120 -> 140f
            shown <= 180 -> 200f
            else -> 260f
        }
        fun angle(kmh: Float) = 150 + kmh / scale * 240
        canvas.drawArc(box, 150f, 240f, false, trackPaint)
        // Graduations inside the arc: long every 20 km/h, short every 10, numbers every 40.
        var mark = 0
        while (mark <= scale) {
            val major = mark % 20 == 0
            tickPaint.strokeWidth = (if (major) 1.6f else 1f) * density
            tickPaint.alpha = if (major) 170 else 90
            val outer = point(angle(mark.toFloat()), radius - stroke * 1.2f)
            val inner = point(angle(mark.toFloat()), radius - stroke * 1.2f - (if (major) 6f else 3f) * density)
            canvas.drawLine(outer[0], outer[1], inner[0], inner[1], tickPaint)
            if (mark % 40 == 0) {
                val at = point(angle(mark.toFloat()), radius - stroke * 1.2f - 18 * density)
                canvas.drawText(mark.toString(), at[0], at[1] + scalePaint.textSize * .35f, scalePaint)
            }
            mark += 10
        }
        val sweep = (shown / scale).coerceIn(0f, 1f) * 240f
        val alert = over
        if (sweep > .5f) {
            glowPaint.shader = null
            glowPaint.color = if (alert) ALERT else to
            glowPaint.alpha = 46
            canvas.drawArc(box, 150f, sweep, false, glowPaint)
            if (alert) {
                arcPaint.shader = null
                arcPaint.color = ALERT
            } else if (arcPaint.shader == null) arcPaint.shader = sweep(from, to)
            canvas.drawArc(box, 150f, sweep, false, arcPaint)
            val tip = point(150 + sweep, radius)
            tipPaint.color = if (alert) ALERT else to
            canvas.drawCircle(tip[0], tip[1], stroke * .95f, tipPaint)
            tipPaint.color = Color.WHITE
            canvas.drawCircle(tip[0], tip[1], stroke * .45f, tipPaint)
        }
        // The chosen limit as a red mark across the track.
        limit?.takeIf { it < scale }?.let { kmh ->
            val outer = point(angle(kmh.toFloat()), radius + stroke * .9f)
            val inner = point(angle(kmh.toFloat()), radius - stroke * .9f)
            canvas.drawLine(outer[0], outer[1], inner[0], inner[1], limitPaint)
        }
        val cx = box.centerX()
        val baseline = box.centerY() + valuePaint.textSize * .36f
        valuePaint.color = when {
            speed == null -> mutedColor
            alert -> ALERT
            else -> textColor
        }
        canvas.drawText(speed?.let { shown.toInt().toString() } ?: "—", cx, baseline, valuePaint)
        canvas.drawText("KM/H", cx, baseline + unitPaint.textSize * 1.7f, unitPaint)
    }

    private companion object {
        const val ALERT = 0xFFFF4D5E.toInt()
    }

    override fun onDetachedFromWindow() {
        easing?.cancel()
        super.onDetachedFromWindow()
    }
}

/** A round speed-limit road sign: white disc, red ring, black figures. */
internal class LimitSign(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFE5262E.toInt()
    }
    private val figures = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111111.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 700, false)
    }

    var limit: Int? = null
        set(value) {
            field = value
            visibility = if (value == null) GONE else VISIBLE
            contentDescription = value?.let { "Alerte à $it km/h" }
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val value = limit ?: return
        val r = min(width, height) / 2f
        ring.strokeWidth = r * .2f
        canvas.drawCircle(width / 2f, height / 2f, r - density, fill)
        canvas.drawCircle(width / 2f, height / 2f, r - ring.strokeWidth / 2 - density, ring)
        figures.textSize = r * (if (value >= 100) .72f else .9f)
        canvas.drawText(value.toString(), width / 2f, height / 2f + figures.textSize * .36f, figures)
    }
}
