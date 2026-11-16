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
        typeface = Typeface.create(Typeface.DEFAULT, 700, false)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)
    }
    private val box = RectF()
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
        val sweep = (shown / scale).coerceIn(0f, 1f) * 240f
        if (sweep > .5f) canvas.drawArc(box, 150f, sweep, false, arcPaint)
        val cx = box.centerX()
        val cy = box.centerY()
        canvas.drawText(speed?.let { shown.toInt().toString() } ?: "—", cx, cy + valuePaint.textSize * .32f, valuePaint)
        canvas.drawText("km/h", cx, cy + valuePaint.textSize * .32f + unitPaint.textSize * 1.6f, unitPaint)
    }

    override fun onDetachedFromWindow() {
        easing?.cancel()
        super.onDetachedFromWindow()
    }
}
