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
    private val mutedColor: Int, private val badge: android.graphics.Bitmap? = null) : View(context), SpeedDial {
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
    // The logo in dark chrome on the light body, whatever its own colours.
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = android.graphics.PorterDuffColorFilter(0xFF39414E.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
    }
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

    // Brake lights on while the speed drops quickly.
    private var braking = false

    override fun setSpeed(value: Int?) {
        braking = value != null && speed != null && value <= speed!! - 3
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
        drawCar(canvas, cx, h - band + 3 * density, min(w * .32f, 92 * density))
        // The speed above the road.
        valuePaint.textSize = size
        valuePaint.color = if (over) alert else textColor
        valuePaint.setShadowLayer(10 * density, 0f, 0f, ColorUtils.setAlphaComponent(tone, 0x80))
        canvas.drawText(speed?.let { shown.roundToInt().toString() } ?: "—", cx, baseline, valuePaint)
        unitPaint.textSize = 10 * density
        canvas.drawText("KM/H", cx, baseline + 13 * density, unitPaint)
        if (shown > .5f && isShown && windowVisibility == VISIBLE) postInvalidateDelayed(33) else lastFrame = 0L
    }

    /**
     * A compact hybrid saloon from behind, in the lines of the driver's Corolla Hybrid: pearl-white body with rounded
     * shoulders, sloping rear window, door mirrors, two wrap-around tail lights joined by a dark trim strip with the
     * driver's emblem between them, the plate, and a black lower bumper with reflectors. The lights brighten on braking.
     */
    private fun drawCar(canvas: Canvas, cx: Float, bottom: Float, carW: Float) {
        val u = carW
        val v = carW * .62f
        val top = bottom - v
        fun x(f: Float) = cx + u * f
        fun y(f: Float) = top + v * f
        // Shadow and tyres.
        carPaint.shader = RadialGradient(cx, bottom, u * .8f, 0xA0000000.toInt(), 0, Shader.TileMode.CLAMP)
        rect.set(x(-.8f), bottom - v * .22f, x(.8f), bottom + v * .22f)
        canvas.drawOval(rect, carPaint)
        carPaint.shader = null
        carPaint.color = 0xFF06080B.toInt()
        rect.set(x(-.47f), y(.8f), x(-.33f), bottom + v * .02f)
        canvas.drawRoundRect(rect, v * .05f, v * .05f, carPaint)
        rect.set(x(.33f), y(.8f), x(.47f), bottom + v * .02f)
        canvas.drawRoundRect(rect, v * .05f, v * .05f, carPaint)
        // Door mirrors.
        carPaint.color = 0xFF1A1F27.toInt()
        rect.set(x(-.53f), y(.36f), x(-.43f), y(.45f))
        canvas.drawOval(rect, carPaint)
        rect.set(x(.43f), y(.36f), x(.53f), y(.45f))
        canvas.drawOval(rect, carPaint)
        // Cabin: a sloping greenhouse, narrower at the roof.
        val pearl = LinearGradient(0f, top, 0f, bottom, 0xFFF7F8FA.toInt(), 0xFFB9C0CA.toInt(), Shader.TileMode.CLAMP)
        carPaint.shader = pearl
        path.reset()
        path.moveTo(x(-.42f), y(.46f))
        path.cubicTo(x(-.36f), y(.2f), x(-.3f), y(.02f), x(-.18f), y(0f))
        path.lineTo(x(.18f), y(0f))
        path.cubicTo(x(.3f), y(.02f), x(.36f), y(.2f), x(.42f), y(.46f))
        path.close()
        canvas.drawPath(path, carPaint)
        // Rear window, dark glass with a reflection.
        carPaint.shader = LinearGradient(0f, y(.04f), 0f, y(.4f), 0xFF2E3A4E.toInt(), 0xFF070B12.toInt(), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(x(-.35f), y(.4f))
        path.cubicTo(x(-.3f), y(.2f), x(-.26f), y(.07f), x(-.16f), y(.05f))
        path.lineTo(x(.16f), y(.05f))
        path.cubicTo(x(.26f), y(.07f), x(.3f), y(.2f), x(.35f), y(.4f))
        path.close()
        canvas.drawPath(path, carPaint)
        // Body: wide, with rounded shoulders and a slightly tucked-in bottom.
        carPaint.shader = pearl
        path.reset()
        path.moveTo(x(-.47f), y(.44f))
        path.quadTo(x(-.51f), y(.46f), x(-.51f), y(.56f))
        path.lineTo(x(-.49f), y(.86f))
        path.quadTo(x(-.48f), y(.94f), x(-.4f), y(.94f))
        path.lineTo(x(.4f), y(.94f))
        path.quadTo(x(.48f), y(.94f), x(.49f), y(.86f))
        path.lineTo(x(.51f), y(.56f))
        path.quadTo(x(.51f), y(.46f), x(.47f), y(.44f))
        path.close()
        canvas.drawPath(path, carPaint)
        // The boot lid's shut line.
        carPaint.shader = null
        stroke(0x33000000, .8f)
        canvas.drawLine(x(-.3f), y(.47f), x(.3f), y(.47f), outline)
        // Wrap-around tail lights, joined by a dark trim strip.
        val glowing = braking
        carPaint.color = if (glowing) 0x88FF2020.toInt() else 0x44FF2020
        rect.set(x(-.56f), y(.46f), x(.56f), y(.66f))
        canvas.drawRoundRect(rect, v * .1f, v * .1f, carPaint)
        carPaint.color = 0xFF1B1F26.toInt()
        rect.set(x(-.22f), y(.53f), x(.22f), y(.575f))
        canvas.drawRect(rect, carPaint)
        for (side in SIDES) {
            path.reset()
            path.moveTo(x(side * .51f), y(.5f))
            path.lineTo(x(side * .22f), y(.52f))
            path.lineTo(x(side * .22f), y(.585f))
            path.lineTo(x(side * .5f), y(.63f))
            path.close()
            carPaint.color = if (glowing) 0xFFFF2B2B.toInt() else 0xFFD8262E.toInt()
            canvas.drawPath(path, carPaint)
            // The light's inner LED line.
            stroke(if (glowing) 0xFFFFD0D0.toInt() else 0xFFFF7A7A.toInt(), 1f)
            canvas.drawLine(x(side * .48f), y(.535f), x(side * .25f), y(.548f), outline)
        }
        // Plate, then the black lower bumper with its reflectors.
        carPaint.color = 0xFFE9ECF0.toInt()
        rect.set(x(-.13f), y(.66f), x(.13f), y(.77f))
        canvas.drawRoundRect(rect, v * .02f, v * .02f, carPaint)
        carPaint.color = 0xFF15191F.toInt()
        rect.set(x(-.47f), y(.82f), x(.47f), y(.95f))
        canvas.drawRoundRect(rect, v * .05f, v * .05f, carPaint)
        carPaint.color = 0xFFB0222A.toInt()
        rect.set(x(-.42f), y(.85f), x(-.33f), y(.88f))
        canvas.drawRect(rect, carPaint)
        rect.set(x(.33f), y(.85f), x(.42f), y(.88f))
        canvas.drawRect(rect, carPaint)
        // The driver's emblem on the boot, above the trim strip.
        badge?.let { original ->
            val logo = fitted(original, v * .14f, u * .16f)
            val wide = logo.width.toFloat()
            val tall = logo.height.toFloat()
            rect.set(cx - wide / 2, y(.525f) - tall, cx + wide / 2, y(.525f))
            canvas.drawBitmap(logo, null, rect, badgePaint)
        }
    }

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private fun stroke(color: Int, widthDp: Float) {
        outline.color = color
        outline.strokeWidth = widthDp * density
    }

    // Each badge shrunk once to the size it is drawn at, so its edges stay clean.
    private val fittedCache = HashMap<android.graphics.Bitmap, android.graphics.Bitmap>()

    private fun fitted(source: android.graphics.Bitmap, maxHeight: Float, maxWidth: Float): android.graphics.Bitmap {
        val height = minOf(maxHeight, maxWidth * source.height / source.width.coerceAtLeast(1)).roundToInt().coerceAtLeast(1)
        fittedCache[source]?.takeIf { it.height == height }?.let { return it }
        return CarLogo.fit(source, height).also { fittedCache[source] = it }
    }

    private companion object {
        val LANES = floatArrayOf(-1f / 3f, 1f / 3f)
        val SIDES = floatArrayOf(-1f, 1f)
    }
}
