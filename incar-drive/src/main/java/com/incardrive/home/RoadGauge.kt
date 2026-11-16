package com.incardrive.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.min
import kotlin.math.roundToInt

/** What the speed card needs from its dial, whichever style the driver picked. */
internal interface SpeedDial {
    var limit: Int?
    fun setSpeed(value: Int?)
}

/**
 * The « Route » speed style: the speed in large figures over the road seen from behind the car, three lanes running
 * to the horizon, in the spirit of the lane view of a recent Toyota's digital dashboard (no brand mark). The lane
 * marks scroll at the car's pace and stand still when it stops; the road's edges are ice blue, red above the limit,
 * like the number.
 */
internal class RoadGauge(context: Context, private val accent: Int, private val textColor: Int,
    private val mutedColor: Int) : View(context), SpeedDial {
    private val density = resources.displayMetrics.density
    private val alert = 0xFFFF4D4F.toInt()
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 700, false)
        textAlign = Paint.Align.CENTER
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = mutedColor
        letterSpacing = .12f
    }
    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val carPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    override var limit: Int? = null
        set(value) { field = value; invalidate() }
    private val over get() = limit?.let { (speed ?: 0) > it } == true
    private var speed: Int? = null
    private var shown = 0f
    private var easing: ValueAnimator? = null
    // How far the lane marks have travelled, in road units, and when they were last drawn.
    private var travel = 0f
    private var lastFrame = 0L

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun setSpeed(value: Int?) {
        speed = value
        easing?.cancel()
        easing = ValueAnimator.ofFloat(shown, (value ?: 0).toFloat()).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
        contentDescription = value?.let { "$it km/h" } ?: "Vitesse inconnue"
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 40 * density || h < 40 * density) return
        val tone = if (over) alert else accent
        val horizon = h * .47f
        val cx = w / 2f
        // The road: a trapezoid from a narrow strip at the horizon to the full width under the car.
        val far = w * .05f
        val near = w * .62f
        fun half(y: Float) = far + (near - far) * ((y - horizon) / (h - horizon))
        roadPaint.shader = LinearGradient(0f, horizon, 0f, h, ColorUtils.setAlphaComponent(tone, 0),
            ColorUtils.setAlphaComponent(tone, 0x40), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(cx - far, horizon)
        path.lineTo(cx + far, horizon)
        path.lineTo(cx + near, h)
        path.lineTo(cx - near, h)
        path.close()
        canvas.drawPath(path, roadPaint)
        // Edges in the dashboard colour, fading towards the horizon.
        edgePaint.strokeWidth = 2.5f * density
        edgePaint.shader = LinearGradient(0f, horizon, 0f, h, ColorUtils.setAlphaComponent(tone, 0x10), tone, Shader.TileMode.CLAMP)
        canvas.drawLine(cx - far, horizon, cx - near, h, edgePaint)
        canvas.drawLine(cx + far, horizon, cx + near, h, edgePaint)
        // Lane marks: dashes at even depths (screen height ∝ 1 / distance), moving with the speed.
        val now = System.nanoTime()
        if (lastFrame != 0L) travel = (travel + shown * (now - lastFrame) / 1e9f * .05f) % 1f
        lastFrame = now
        for (lane in listOf(-1f / 3f, 1f / 3f)) {
            var step = 0
            while (step < 14) {
                val z0 = 1f + step - travel
                val z1 = z0 + .45f
                step++
                if (z0 < 1f) continue
                val y0 = horizon + (h - horizon) / z0
                val y1 = horizon + (h - horizon) / z1
                val fade = ((y0 - horizon) / (h - horizon)).coerceIn(0f, 1f)
                markPaint.color = ColorUtils.setAlphaComponent(textColor, (40 + 170 * fade).roundToInt())
                val wide0 = 1.6f * density * (.4f + 2.2f * fade)
                path.reset()
                path.moveTo(cx + lane * 2 * half(y1) - wide0 * .6f, y1)
                path.lineTo(cx + lane * 2 * half(y1) + wide0 * .6f, y1)
                path.lineTo(cx + lane * 2 * half(y0) + wide0, y0)
                path.lineTo(cx + lane * 2 * half(y0) - wide0, y0)
                path.close()
                canvas.drawPath(path, markPaint)
            }
        }
        // The car, seen from behind, in the middle lane.
        val carW = min(w * .2f, 64 * density)
        val carH = carW * .62f
        val top = h - carH - 2 * density
        carPaint.shader = null
        carPaint.color = ColorUtils.setAlphaComponent(textColor, 0xE6)
        rect.set(cx - carW / 2, top + carH * .28f, cx + carW / 2, h - 2 * density)
        canvas.drawRoundRect(rect, carH * .22f, carH * .22f, carPaint)
        rect.set(cx - carW * .36f, top, cx + carW * .36f, top + carH * .5f)
        canvas.drawRoundRect(rect, carH * .2f, carH * .2f, carPaint)
        carPaint.color = ColorUtils.setAlphaComponent(0xFF0B1220.toInt(), 0xD0)
        rect.set(cx - carW * .29f, top + carH * .08f, cx + carW * .29f, top + carH * .4f)
        canvas.drawRoundRect(rect, carH * .12f, carH * .12f, carPaint)
        carPaint.color = alert
        rect.set(cx - carW * .46f, top + carH * .5f, cx - carW * .26f, top + carH * .62f)
        canvas.drawRoundRect(rect, carH * .05f, carH * .05f, carPaint)
        rect.set(cx + carW * .26f, top + carH * .5f, cx + carW * .46f, top + carH * .62f)
        canvas.drawRoundRect(rect, carH * .05f, carH * .05f, carPaint)
        // The speed above the road.
        val size = min(h * .27f, w * .3f)
        valuePaint.textSize = size
        valuePaint.color = if (over) alert else textColor
        canvas.drawText(speed?.let { shown.roundToInt().toString() } ?: "—", cx, horizon - size * .32f, valuePaint)
        unitPaint.textSize = 11 * density
        canvas.drawText("KM/H", cx, horizon + 2 * density, unitPaint)
        if (shown > .5f && isShown && windowVisibility == VISIBLE) postInvalidateDelayed(33) else lastFrame = 0L
    }
}
