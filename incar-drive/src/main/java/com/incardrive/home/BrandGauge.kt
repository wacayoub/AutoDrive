package com.incardrive.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Speed styles close to the look of well-known digital dashboards, drawn from scratch (no brand artwork, fonts or
 * marks), named neutrally in the settings:
 * - [Style.ELECTRIC]: the large figure alone over a recharge / power bar, like recent Chinese electric cars.
 * - [Style.FACET]: an angular, faceted dial lit in orange, like recent German curved displays.
 * - [Style.VIRTUAL]: a large round dial with fine graduations, a red needle and the figure in the hub.
 * - [Style.ELEGANT]: a thin silver ring and a soft turquoise arc around a refined figure.
 * The recharge / power bar follows the GPS speed's changes: the head unit cannot read the car's own data.
 */
internal class BrandGauge(context: Context, private val style: Style, private val textColor: Int,
    private val mutedColor: Int) : View(context), SpeedDial {
    enum class Style { ELECTRIC, FACET, VIRTUAL, ELEGANT }

    private val density = resources.displayMetrics.density
    private val alert = 0xFFFF4D4F.toInt()
    private val orange = 0xFFFF8A1F.toInt()
    private val turquoise = 0xFF4FD8C8.toInt()
    private val silver = 0xFFC9CDD3.toInt()
    private val condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val box = RectF()
    private val path = Path()
    private val part = Path()

    override var limit: Int? = null
        set(value) { field = value; invalidate() }
    private val over get() = limit?.let { (speed ?: 0) > it } == true
    private var speed: Int? = null
    private var shown = 0f
    private var easing: ValueAnimator? = null
    // −1 strong braking (recharge) … +1 strong acceleration (power), smoothed.
    private var power = 0f
    private var lastSpeed: Int? = null
    private var lastAt = 0L

    init {
        if (style == Style.ELEGANT) setLayerType(LAYER_TYPE_SOFTWARE, null) // for the glow's blur
    }

    override fun setSpeed(value: Int?) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastSpeed
        if (value != null && previous != null && now > lastAt) {
            val accel = (value - previous) / ((now - lastAt) / 1000f) // km/h per second
            val cruise = (value / 260f).coerceAtMost(.3f)
            val target = (accel / 8f + if (value > 3) cruise else 0f).coerceIn(-1f, 1f)
            power += (target - power) * .5f
        } else if (value == null || value == 0) power = 0f
        lastSpeed = value
        lastAt = now
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

    private val figure get() = speed?.let { shown.roundToInt().toString() } ?: "—"
    private val fraction get() = (shown / MAX).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        if (width < 40 * density || height < 40 * density) return
        when (style) {
            Style.ELECTRIC -> electric(canvas)
            Style.FACET -> facet(canvas)
            Style.VIRTUAL -> virtual(canvas)
            Style.ELEGANT -> elegant(canvas)
        }
    }

    private fun electric(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(h * .42f, w * .42f)
        textPaint.typeface = light
        textPaint.textSize = size
        textPaint.color = if (over) alert else textColor
        val baseline = h * .58f
        canvas.drawText(figure, w / 2f, baseline, textPaint)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 12 * density
        textPaint.color = 0xFF85B7EB.toInt()
        canvas.drawText("km/h", w / 2f, baseline + 16 * density, textPaint)
        // Recharge on the left in green, power on the right in blue, from the middle of the bar.
        val pad = 16 * density
        val y = h - 22 * density
        val thick = 7 * density
        stroke.strokeWidth = thick
        stroke.color = ColorUtils.setAlphaComponent(textColor, 0x22)
        canvas.drawLine(pad, y, w - pad, y, stroke)
        val mid = w / 2f
        val reach = (w - 2 * pad) / 2f
        if (power < -.02f) {
            stroke.color = 0xFF3DDC97.toInt()
            canvas.drawLine(mid, y, mid + power * reach, y, stroke)
        } else if (power > .02f) {
            stroke.color = if (over) alert else 0xFF4D9BFF.toInt()
            canvas.drawLine(mid, y, mid + power * reach, y, stroke)
        }
        fill.color = textColor
        canvas.drawCircle(mid, y, thick * .45f, fill)
        textPaint.textSize = 10 * density
        textPaint.color = mutedColor
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Recharge", pad, y - 9 * density, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Puissance", w - pad, y - 9 * density, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
    }

    /** Centre and radius of a 240° dial open at the bottom that fits the view. */
    private fun dial(pad: Float): Triple<Float, Float, Float> {
        val r = min((width - 2 * pad) / 2f, (height - 2 * pad) / 1.75f)
        return Triple(width / 2f, pad + r, r)
    }

    private fun point(cx: Float, cy: Float, r: Float, degrees: Float) =
        Math.toRadians(degrees.toDouble()).let { cx + r * cos(it).toFloat() to cy + r * sin(it).toFloat() }

    private fun facet(canvas: Canvas) {
        val (cx, cy, r) = dial(10 * density)
        // Seven straight facets from 150° to 390°, like a cut gem.
        path.reset()
        for (i in 0..7) {
            val (x, y) = point(cx, cy, r, START + SWEEP * i / 7f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.strokeWidth = 8 * density
        stroke.color = ColorUtils.setAlphaComponent(textColor, 0x26)
        canvas.drawPath(path, stroke)
        val measure = PathMeasure(path, false)
        part.reset()
        measure.getSegment(0f, measure.length * fraction, part, true)
        stroke.color = if (over) alert else orange
        canvas.drawPath(part, stroke)
        // Graduations every 40 km/h inside the facets.
        stroke.strokeWidth = 2 * density
        stroke.color = ColorUtils.setAlphaComponent(textColor, 0x66)
        for (i in 0..6) {
            val angle = START + SWEEP * i / 6f
            val (x0, y0) = point(cx, cy, r * .78f, angle)
            val (x1, y1) = point(cx, cy, r * .86f, angle)
            canvas.drawLine(x0, y0, x1, y1, stroke)
        }
        textPaint.typeface = Typeface.create(condensed, 700, false)
        textPaint.textSize = r * .62f
        textPaint.color = if (over) alert else textColor
        canvas.drawText(figure, cx, cy + r * .2f, textPaint)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 10 * density
        textPaint.letterSpacing = .2f
        textPaint.color = orange
        canvas.drawText("KM/H", cx, cy + r * .44f, textPaint)
        textPaint.letterSpacing = 0f
    }

    private fun virtual(canvas: Canvas) {
        val pad = 6 * density
        val r = min(width - 2 * pad, height - 2 * pad) / 2f
        val cx = width / 2f
        val cy = height / 2f
        fill.color = 0xFF10151E.toInt()
        canvas.drawCircle(cx, cy, r, fill)
        stroke.strokeWidth = 1.5f * density
        stroke.color = 0xFF4A4F57.toInt()
        canvas.drawCircle(cx, cy, r, stroke)
        // A tick every 10 km/h, longer every 20, numbers every 40.
        textPaint.typeface = light
        textPaint.textSize = r * .15f
        textPaint.color = textColor
        for (kmh in 0..MAX.toInt() step 10) {
            val angle = START + SWEEP * kmh / MAX
            val major = kmh % 20 == 0
            stroke.strokeWidth = (if (major) 2f else 1f) * density
            stroke.color = ColorUtils.setAlphaComponent(textColor, if (major) 0xE6 else 0x80)
            val (x0, y0) = point(cx, cy, r * (if (major) .8f else .85f), angle)
            val (x1, y1) = point(cx, cy, r * .92f, angle)
            canvas.drawLine(x0, y0, x1, y1, stroke)
            if (kmh % 40 == 0) {
                val (tx, ty) = point(cx, cy, r * .64f, angle)
                canvas.drawText(kmh.toString(), tx, ty + textPaint.textSize * .35f, textPaint)
            }
        }
        // The red needle, then the hub with the figure.
        val angle = START + SWEEP * fraction
        val (nx, ny) = point(cx, cy, r * .9f, angle)
        val (bx, by) = point(cx, cy, r * .3f, angle)
        glow.strokeWidth = 7 * density
        glow.color = ColorUtils.setAlphaComponent(alert, 0x40)
        canvas.drawLine(bx, by, nx, ny, glow)
        stroke.strokeWidth = 3 * density
        stroke.color = alert
        canvas.drawLine(bx, by, nx, ny, stroke)
        fill.color = 0xFF0B0F17.toInt()
        canvas.drawCircle(cx, cy, r * .32f, fill)
        stroke.strokeWidth = 1.5f * density
        stroke.color = 0xFF5F5E5A.toInt()
        canvas.drawCircle(cx, cy, r * .32f, stroke)
        textPaint.typeface = Typeface.create(condensed, 700, false)
        textPaint.textSize = r * .3f
        textPaint.color = if (over) alert else textColor
        canvas.drawText(figure, cx, cy + r * .1f, textPaint)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 9 * density
        textPaint.color = mutedColor
        canvas.drawText("km/h", cx, cy + r * .23f, textPaint)
    }

    private fun elegant(canvas: Canvas) {
        val (cx, cy, r) = dial(12 * density)
        stroke.strokeWidth = 1.5f * density
        stroke.color = silver
        box.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(box, START - 8f, SWEEP + 16f, false, stroke)
        val inner = r * .86f
        box.set(cx - inner, cy - inner, cx + inner, cy + inner)
        stroke.strokeWidth = 5 * density
        stroke.color = ColorUtils.setAlphaComponent(textColor, 0x1F)
        canvas.drawArc(box, START, SWEEP, false, stroke)
        val tone = if (over) alert else turquoise
        if (fraction > 0f) {
            glow.strokeWidth = 12 * density
            glow.color = ColorUtils.setAlphaComponent(tone, 0x66)
            glow.maskFilter = BlurMaskFilter(8 * density, BlurMaskFilter.Blur.NORMAL)
            canvas.drawArc(box, START, SWEEP * fraction, false, glow)
            stroke.color = tone
            canvas.drawArc(box, START, SWEEP * fraction, false, stroke)
        }
        textPaint.typeface = Typeface.create("serif", Typeface.NORMAL)
        textPaint.textSize = r * .58f
        textPaint.color = if (over) alert else textColor
        canvas.drawText(figure, cx, cy + r * .2f, textPaint)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 10 * density
        textPaint.letterSpacing = .25f
        textPaint.color = silver
        canvas.drawText("KM/H", cx, cy + r * .44f, textPaint)
        textPaint.letterSpacing = 0f
    }

    private companion object {
        const val MAX = 240f
        const val START = 150f
        const val SWEEP = 240f
    }
}
