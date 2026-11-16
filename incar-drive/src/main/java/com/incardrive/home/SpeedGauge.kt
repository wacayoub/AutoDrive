package com.incardrive.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/**
 * The speed dial: a 240° track with a gradient arc that eases to the GPS speed, the number in the middle and
 * « km/h » under it. Its scale grows past 120 and 180 km/h so the arc never pins.
 */
internal class SpeedGauge(context: Context, private val track: Int, private val from: Int, private val to: Int,
    private val textColor: Int, private val mutedColor: Int) : View(context) {
    private val density = resources.displayMetrics.density
    private val stroke = 10 * density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = stroke
        color = track
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = stroke
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 700, false)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)
    }
    private val limitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3 * density
        strokeCap = Paint.Cap.ROUND
        color = ALERT
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
        color = mutedColor
        alpha = 110
    }
    private val alertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = stroke
        color = ALERT
    }
    private val box = RectF()

    /** Above this speed the arc and the number turn red; null turns the alert off. */
    var limit: Int? = null
        set(value) { field = value; invalidate() }
    val over get() = limit?.let { (speed ?: 0) > it } == true
    private var shown = 0f
    private var speed: Int? = null
    private var easing: ValueAnimator? = null

    fun setSpeed(value: Int?) {
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
        val size = min(w, h) - stroke * 2
        val left = (w - size) / 2f
        val top = (h - size) / 2f + stroke * .6f
        box.set(left, top, left + size, top + size)
        // The colours run along the arc, from its start (lower left) to its end (lower right).
        arcPaint.shader = SweepGradient(box.centerX(), box.centerY(), intArrayOf(from, to, to, from),
            floatArrayOf(0f, .67f, .9f, 1f)).apply {
            setLocalMatrix(Matrix().apply { setRotate(150f, box.centerX(), box.centerY()) })
        }
        valuePaint.textSize = size * .3f
        unitPaint.textSize = size * .1f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawArc(box, 150f, 240f, false, trackPaint)
        val scale = when {
            shown <= 120 -> 140f
            shown <= 180 -> 200f
            else -> 260f
        }
        // Graduations inside the arc, like a speedometer: a long tick every 20 km/h, a short one every 10.
        val radius = box.width() / 2
        val cx0 = box.centerX()
        val cy0 = box.centerY()
        var mark = 0
        while (mark <= scale) {
            val angle = Math.toRadians((150 + mark / scale * 240).toDouble())
            val outer = radius - stroke * 1.1f
            val inner = outer - (if (mark % 20 == 0) 7f else 3.5f) * density
            canvas.drawLine(cx0 + (outer * Math.cos(angle)).toFloat(), cy0 + (outer * Math.sin(angle)).toFloat(),
                cx0 + (inner * Math.cos(angle)).toFloat(), cy0 + (inner * Math.sin(angle)).toFloat(), tickPaint)
            mark += 10
        }
        val sweep = (shown / scale).coerceIn(0f, 1f) * 240f
        if (sweep > .5f) canvas.drawArc(box, 150f, sweep, false, if (over) alertPaint else arcPaint)
        // The chosen limit as a small mark on the track.
        limit?.takeIf { it < scale }?.let { mark ->
            val angle = Math.toRadians((150 + mark / scale * 240).toDouble())
            val outer = radius + stroke * .9f
            val inner = radius - stroke * .9f
            canvas.drawLine(cx0 + (outer * Math.cos(angle)).toFloat(), cy0 + (outer * Math.sin(angle)).toFloat(),
                cx0 + (inner * Math.cos(angle)).toFloat(), cy0 + (inner * Math.sin(angle)).toFloat(), limitPaint)
        }
        valuePaint.color = if (over) ALERT else textColor
        val cx = box.centerX()
        val cy = box.centerY()
        canvas.drawText(speed?.let { shown.toInt().toString() } ?: "—", cx, cy + valuePaint.textSize * .32f, valuePaint)
        canvas.drawText("km/h", cx, cy + valuePaint.textSize * .32f + unitPaint.textSize * 1.6f, unitPaint)
    }

    private companion object {
        const val ALERT = 0xFFFF4D5E.toInt()
    }

    override fun onDetachedFromWindow() {
        easing?.cancel()
        super.onDetachedFromWindow()
    }
}
