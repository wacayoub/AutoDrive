package com.incardrive.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        val cx = w / 2f
        // The figure on top, then the horizon just under its unit.
        val size = min(h * .27f, w * .3f)
        val baseline = maxOf(size * .95f + 6 * density, h * .35f)
        val horizon = baseline + 20 * density
        // A soft glow where the road meets the sky.
        glowPaint.shader = RadialGradient(cx, horizon, w * .55f, ColorUtils.setAlphaComponent(tone, 0x26),
            ColorUtils.setAlphaComponent(tone, 0), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, glowPaint)
        // The road: dark asphalt from a point at the horizon to past both sides under the car.
        val far = w * .035f
        val near = w * .78f
        fun half(y: Float) = far + (near - far) * ((y - horizon) / (h - horizon))
        path.reset()
        path.moveTo(cx - far, horizon)
        path.lineTo(cx + far, horizon)
        path.lineTo(cx + near, h)
        path.lineTo(cx - near, h)
        path.close()
        roadPaint.shader = LinearGradient(0f, horizon, 0f, h, 0x001A2230, 0xF01A2230.toInt(), Shader.TileMode.CLAMP)
        canvas.drawPath(path, roadPaint)
        roadPaint.shader = LinearGradient(0f, horizon, 0f, h, ColorUtils.setAlphaComponent(tone, 0),
            ColorUtils.setAlphaComponent(tone, 0x2E), Shader.TileMode.CLAMP)
        canvas.drawPath(path, roadPaint)
        // Lit curbs in the dashboard blue (red above the limit), with a halo.
        edgePaint.shader = LinearGradient(0f, horizon, 0f, h, ColorUtils.setAlphaComponent(tone, 0x10),
            ColorUtils.setAlphaComponent(tone, 0x50), Shader.TileMode.CLAMP)
        edgePaint.strokeWidth = 7 * density
        canvas.drawLine(cx - far, horizon, cx - near, h, edgePaint)
        canvas.drawLine(cx + far, horizon, cx + near, h, edgePaint)
        edgePaint.shader = LinearGradient(0f, horizon, 0f, h, ColorUtils.setAlphaComponent(tone, 0x20), tone, Shader.TileMode.CLAMP)
        edgePaint.strokeWidth = 2.2f * density
        canvas.drawLine(cx - far, horizon, cx - near, h, edgePaint)
        canvas.drawLine(cx + far, horizon, cx + near, h, edgePaint)
        // Solid white edge lines just inside the curbs.
        edgePaint.shader = LinearGradient(0f, horizon, 0f, h, ColorUtils.setAlphaComponent(textColor, 0x10),
            ColorUtils.setAlphaComponent(textColor, 0xB0), Shader.TileMode.CLAMP)
        edgePaint.strokeWidth = 1.6f * density
        canvas.drawLine(cx - far * .86f, horizon, cx - near * .86f, h, edgePaint)
        canvas.drawLine(cx + far * .86f, horizon, cx + near * .86f, h, edgePaint)
        // Lane marks and reflector posts at even depths (screen height ∝ 1 / distance), moving with the speed.
        val now = System.nanoTime()
        if (lastFrame != 0L) travel = (travel + shown * (now - lastFrame) / 1e9f * .05f) % 2f
        lastFrame = now
        var step = 0
        while (step < 16) {
            val z0 = 1f + step - (travel % 1f)
            val z1 = z0 + .45f
            val post = (step + if (travel >= 1f) 1 else 0) % 2 == 0
            step++
            if (z0 < 1f) continue
            val y0 = horizon + (h - horizon) / z0
            val y1 = horizon + (h - horizon) / z1
            val fade = ((y0 - horizon) / (h - horizon)).coerceIn(0f, 1f)
            markPaint.color = ColorUtils.setAlphaComponent(textColor, (30 + 190 * fade).roundToInt())
            val wide = 1.5f * density * (.4f + 2.2f * fade)
            for (lane in LANES) {
                path.reset()
                path.moveTo(cx + lane * 2 * half(y1) - wide * .6f, y1)
                path.lineTo(cx + lane * 2 * half(y1) + wide * .6f, y1)
                path.lineTo(cx + lane * 2 * half(y0) + wide, y0)
                path.lineTo(cx + lane * 2 * half(y0) - wide, y0)
                path.close()
                canvas.drawPath(path, markPaint)
            }
            if (post) for (side in SIDES) {
                val x = cx + side * half(y0) * 1.1f
                val tall = (4 + 16 * fade) * density
                markPaint.color = ColorUtils.setAlphaComponent(textColor, (25 + 120 * fade).roundToInt())
                canvas.drawRect(x - wide * .5f, y0 - tall, x + wide * .5f, y0, markPaint)
                markPaint.color = ColorUtils.setAlphaComponent(0xFFFFB020.toInt(), (60 + 190 * fade).roundToInt())
                canvas.drawRect(x - wide * .5f, y0 - tall, x + wide * .5f, y0 - tall * .75f, markPaint)
            }
        }
        // A dark band at the bottom keeps the trip line readable over the road.
        val band = 26 * density
        roadPaint.shader = LinearGradient(0f, h - band, 0f, h, 0x000B1018, 0xD90B1018.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h - band, w, h, roadPaint)
        drawCar(canvas, cx, h - band + 3 * density, min(w * .28f, 84 * density))
        // The speed above the road.
        valuePaint.textSize = size
        valuePaint.color = if (over) alert else textColor
        valuePaint.setShadowLayer(10 * density, 0f, 0f, ColorUtils.setAlphaComponent(tone, 0x80))
        canvas.drawText(speed?.let { shown.roundToInt().toString() } ?: "—", cx, baseline, valuePaint)
        unitPaint.textSize = 10 * density
        canvas.drawText("KM/H", cx, baseline + 13 * density, unitPaint)
        if (shown > .5f && isShown && windowVisibility == VISIBLE) postInvalidateDelayed(33) else lastFrame = 0L
    }

    /** The car from behind: a soft shadow, the wheels, the body, the rear window and a full-width tail light. */
    private fun drawCar(canvas: Canvas, cx: Float, bottom: Float, carW: Float) {
        val carH = carW * .68f
        val top = bottom - carH
        carPaint.shader = RadialGradient(cx, bottom, carW * .75f, 0x99000000.toInt(), 0, Shader.TileMode.CLAMP)
        rect.set(cx - carW * .75f, bottom - carH * .25f, cx + carW * .75f, bottom + carH * .25f)
        canvas.drawOval(rect, carPaint)
        carPaint.shader = null
        carPaint.color = 0xFF07090D.toInt()
        rect.set(cx - carW * .47f, bottom - carH * .22f, cx - carW * .3f, bottom + carH * .02f)
        canvas.drawRoundRect(rect, carH * .05f, carH * .05f, carPaint)
        rect.set(cx + carW * .3f, bottom - carH * .22f, cx + carW * .47f, bottom + carH * .02f)
        canvas.drawRoundRect(rect, carH * .05f, carH * .05f, carPaint)
        // Cabin then body, light grey lit from above.
        carPaint.shader = LinearGradient(0f, top, 0f, bottom, 0xFFF2F5F9.toInt(), 0xFFAEB7C4.toInt(), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(cx - carW * .3f, top + carH * .42f)
        path.lineTo(cx - carW * .24f, top + carH * .04f)
        path.quadTo(cx, top - carH * .04f, cx + carW * .24f, top + carH * .04f)
        path.lineTo(cx + carW * .3f, top + carH * .42f)
        path.close()
        canvas.drawPath(path, carPaint)
        rect.set(cx - carW / 2, top + carH * .36f, cx + carW / 2, bottom - carH * .08f)
        canvas.drawRoundRect(rect, carH * .2f, carH * .2f, carPaint)
        carPaint.shader = LinearGradient(0f, top, 0f, top + carH * .38f, 0xFF2A3446.toInt(), 0xFF0B1220.toInt(), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(cx - carW * .25f, top + carH * .38f)
        path.lineTo(cx - carW * .2f, top + carH * .1f)
        path.quadTo(cx, top + carH * .04f, cx + carW * .2f, top + carH * .1f)
        path.lineTo(cx + carW * .25f, top + carH * .38f)
        path.close()
        canvas.drawPath(path, carPaint)
        // The tail light across the boot, glowing.
        val lightTop = top + carH * .5f
        carPaint.shader = null
        carPaint.color = 0x55FF2A2A
        rect.set(cx - carW * .5f, lightTop - carH * .06f, cx + carW * .5f, lightTop + carH * .16f)
        canvas.drawRoundRect(rect, carH * .1f, carH * .1f, carPaint)
        carPaint.color = 0xFFFF3B3B.toInt()
        rect.set(cx - carW * .46f, lightTop, cx + carW * .46f, lightTop + carH * .08f)
        canvas.drawRoundRect(rect, carH * .04f, carH * .04f, carPaint)
        carPaint.color = 0xFFE4E8EE.toInt()
        rect.set(cx - carW * .12f, lightTop + carH * .18f, cx + carW * .12f, lightTop + carH * .3f)
        canvas.drawRoundRect(rect, carH * .02f, carH * .02f, carPaint)
    }

    private companion object {
        val LANES = floatArrayOf(-1f / 3f, 1f / 3f)
        val SIDES = floatArrayOf(-1f, 1f)
    }
}
