package com.incardrive.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
    private val mutedColor: Int, private val badge: android.graphics.Bitmap? = null,
    private val bodyColor: Int = 0xFFF2F5F9.toInt(), private val night: Boolean = true) : View(context), SpeedDial {
    private val guideColor = 0xFF3B8CFF.toInt()
    // Road markings are white on the asphalt in both themes.
    private val laneColor = Color.WHITE
    private val arriveColor = 0xFF1D9E75.toInt()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // What the navigation app asks next, drawn on the road; null when it is not guiding.
    private var maneuver: Maneuver? = null
    private var turnIcon: android.graphics.drawable.Drawable? = null
    private var stepText: String? = null
    // The speed camera ahead: its distance in metres and its limit.
    private var cameraMeters: Int? = null
    private var cameraLimit: Int? = null
    private var skyTop = 0xFF0A1020.toInt()
    private var skyBottom = 0xFF1B2C4A.toInt()

    /** The next instruction of Google Maps or Waze, or null when guidance stops. */
    fun guide(nav: NavInfo?) {
        maneuver = nav?.let {
            val read = Maneuver.of(it.instruction, it.step ?: it.instruction)
            // No direction in the words (often there are none): the arrow's own shape says it.
            if (read.turn == Turn.STRAIGHT && it.turn != null) read.copy(turn = it.turn) else read
        }
        turnIcon = nav?.arrow
        val text = nav?.let { it.step ?: it.remaining }
        // A new value from the navigation app sets the distance again; the same text repeated lets the countdown
        // that runs between its updates carry on.
        if (text != stepText || nav == null) {
            baseMeters = Maneuver.meters(nav?.step)
            travelled = 0f
        }
        stepText = text
        invalidate()
    }

    /** The traffic the car is in, which colours the road green, orange or red; null leaves it ice blue. */
    var flow: Flow? = null
        set(value) { if (field != value) { field = value; invalidate() } }

    // The foot of the card, kept for the navigation's destinations and instruction: the car stops above it.
    private var footer = 0f
    private var footerColor = 0

    fun footer(height: Float, color: Int) {
        footer = height
        footerColor = color
        invalidate()
    }

    /** The speed camera ahead on this road, or null. */
    fun camera(meters: Int?, limit: Int?) {
        cameraMeters = meters
        cameraLimit = limit
        invalidate()
    }

    /** The sky of the moment, behind the horizon. */
    fun sky(top: Int, bottom: Int) {
        if (top == skyTop && bottom == skyBottom) return
        skyTop = top
        skyBottom = bottom
        invalidate()
    }

    /** What is drawn on the road: a bend or a roundabout when close, else the straight road with what lies on it. */
    private data class Scene(val shape: Turn?, val onRoad: Turn?, val exit: Int?, val street: String?, val ahead: Boolean)

    private var shownScene: Scene? = null
    private var previousScene: Scene? = null
    private var enter = 1f
    private var enterStart = 0L
    private var flowPhase = 0f
    // The time between two frames, smoothed: how well this head unit keeps up with the animation.
    private var frameMs = 16f

    private fun drawScene(canvas: Canvas, scene: Scene, w: Float, h: Float, horizon: Float, road: Float, tone: Int) {
        val cx = w / 2f
        when (scene.shape) {
            Turn.LEFT, Turn.RIGHT -> drawBend(canvas, w, horizon, road, tone, left = scene.shape == Turn.LEFT)
            Turn.ROUNDABOUT -> drawRoundabout(canvas, w, horizon, road, tone, scene.exit)
            else -> {
                drawStraight(canvas, w, h, horizon, tone)
                when (scene.onRoad) {
                    Turn.UTURN -> drawUTurn(canvas, cx, horizon, road, w)
                    Turn.ARRIVE -> drawFlag(canvas, cx, horizon, road)
                    null -> Unit
                    else -> if (scene.ahead) drawAhead(canvas, cx, horizon, road, w)
                }
            }
        }
        // The street to take, on a road sign over the far end of the turn.
        if (scene.street != null) drawStreetSign(canvas, w, horizon, road, scene.street, left = scene.shape == Turn.LEFT)
    }

    /** An arrow filled with light: a soft halo, a body that brightens towards its head, a thin bright rim. */
    private fun drawLitArrow(canvas: Canvas, arrow: Path, top: Float, bottom: Float) {
        linePaint.color = ColorUtils.setAlphaComponent(guideColor, 0x38)
        linePaint.strokeWidth = 7 * density
        canvas.drawPath(arrow, linePaint)
        arrowPaint.shader = linear(0f, top, 0f, bottom, 0xFF7CC0FF.toInt(), 0xFF2468E0.toInt())
        canvas.drawPath(arrow, arrowPaint)
        arrowPaint.shader = null
        linePaint.color = 0x73FFFFFF
        linePaint.strokeWidth = 1.1f * density
        canvas.drawPath(arrow, linePaint)
    }

    /** The turn is drawn on the road only once it is close; before that the road stays straight. */
    private val close get() = (liveMeters ?: 0) <= NEAR_M
    private val cameraFirst get() = cameraMeters != null && (maneuver == null || cameraMeters!! < (liveMeters ?: Int.MAX_VALUE))

    // The navigation app updates its distance in steps and a little late. Between two of its values the distance is
    // counted down here from the car's own speed, frame by frame, and set again at each new value.
    private var baseMeters: Int? = null
    private var travelled = 0f
    private val liveMeters: Int? get() = baseMeters?.let { (it - travelled).toInt().coerceAtLeast(0) }

    /** The distance to the next turn as counted down now, written like the navigation app does; null when unknown. */
    fun liveStep(): String? {
        val metres = liveMeters ?: return null
        return when {
            metres >= 1000 -> String.format(java.util.Locale.FRENCH, "%.1f km", metres / 1000f)
            metres >= 100 -> "${metres / 10 * 10} m"
            metres >= 15 -> "${metres / 5 * 5} m"
            else -> "maintenant"
        }
    }

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
    // Gradients are built once and kept: making them again at every frame was most of the drawing time.
    private val shaders = HashMap<Long, Shader>()

    private fun key(a: Float, b: Float, c: Float, d: Float, e: Int, f: Int, kind: Int): Long {
        var hash = kind.toLong()
        for (bits in intArrayOf(a.toBits(), b.toBits(), c.toBits(), d.toBits(), e, f)) hash = hash * 1_000_003L + bits
        return hash
    }

    private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, from: Int, to: Int): Shader {
        if (shaders.size > 160) shaders.clear()
        return shaders.getOrPut(key(x0, y0, x1, y1, from, to, 1)) { LinearGradient(x0, y0, x1, y1, from, to, Shader.TileMode.CLAMP) }
    }

    private fun radial(x: Float, y: Float, radius: Float, from: Int, to: Int): Shader {
        if (shaders.size > 160) shaders.clear()
        return shaders.getOrPut(key(x, y, radius, 0f, from, to, 2)) { RadialGradient(x, y, radius, from, to, Shader.TileMode.CLAMP) }
    }

    private val path = Path()
    private val part = Path()
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
        // Everything is drawn above the card's foot, which is filled plainly for the navigation.
        val h = height.toFloat() - footer
        if (w < 40 * density || h < 40 * density) return
        if (footer > 0f) {
            roadPaint.shader = null
            roadPaint.color = footerColor
            canvas.drawRect(0f, h, w, height.toFloat(), roadPaint)
            canvas.save()
            canvas.clipRect(0f, 0f, w, h)
        }
        val turn = maneuver
        val guiding = turn != null || cameraMeters != null
        val arriving = turn?.turn == Turn.ARRIVE
        val tone = if (over) alert else flow?.color ?: accent
        val cx = w / 2f
        // The figure on top, then the horizon just under it; while guiding the figure is smaller and to the right,
        // leaving the left for the instruction and more of the card for the road.
        val size = if (guiding) min(h * .2f, w * .22f) else min(h * .27f, w * .3f)
        val baseline = if (guiding) size + 8 * density else maxOf(size * .95f + 6 * density, h * .35f)
        val horizon = if (guiding) maxOf(h * .36f, baseline + 16 * density) else baseline + 20 * density
        val road = h - horizon
        // The sky of the moment above the horizon, then a soft glow where the road meets it.
        if (night) {
            roadPaint.shader = linear(0f, 0f, 0f, horizon, ColorUtils.setAlphaComponent(skyTop, 0xB0),
                ColorUtils.setAlphaComponent(skyBottom, 0xB0))
            canvas.drawRect(0f, 0f, w, horizon, roadPaint)
        } else {
            // By day: a clear blue sky tinted by the sky of the moment, paler towards the horizon, a soft sun, and
            // light ground on both sides of the road instead of the bare card.
            roadPaint.shader = linear(0f, 0f, 0f, horizon, ColorUtils.blendARGB(skyTop, 0xFF3F8BE6.toInt(), .6f),
                ColorUtils.blendARGB(skyBottom, 0xFFD6EAFF.toInt(), .6f))
            canvas.drawRect(0f, 0f, w, horizon, roadPaint)
            glowPaint.shader = radial(w * .82f, horizon * .3f, w * .3f, 0x99FFFFFF.toInt(), 0x00FFFFFF)
            canvas.drawRect(0f, 0f, w, horizon, glowPaint)
            roadPaint.shader = linear(0f, horizon, 0f, h, 0xFFDFE9F1.toInt(), 0xFFB4C6D6.toInt())
            canvas.drawRect(0f, horizon, w, h, roadPaint)
        }
        glowPaint.shader = radial(cx, horizon, w * .55f, ColorUtils.setAlphaComponent(tone, 0x26),
            ColorUtils.setAlphaComponent(tone, 0))
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        val now = System.nanoTime()
        if (lastFrame != 0L) {
            val seconds = (now - lastFrame) / 1e9f
            if (seconds < .5f) frameMs += (seconds * 1000f - frameMs) * .08f
            travel = (travel + shown * seconds * .05f) % 2f
            // The light running along the arrows: slow when stopped, quicker with the car.
            if (seconds < 1.5f) flowPhase = (flowPhase + seconds * (.55f + shown / 110f)) % 1f
            // Only over short gaps: after a pause in drawing (another app in front) the app's own value is kept.
            if (seconds < 1.5f) travelled += shown / 3.6f * seconds
        }
        lastFrame = now
        val bend = turn != null && close && (turn.turn == Turn.LEFT || turn.turn == Turn.RIGHT)
        val circle = turn != null && close && turn.turn == Turn.ROUNDABOUT
        val scene = Scene(when { bend -> turn!!.turn; circle -> Turn.ROUNDABOUT; else -> null },
            turn?.turn.takeIf { !bend && !circle }, turn?.exit.takeIf { circle }, turn?.street.takeIf { bend || circle },
            ahead = !cameraFirst)
        // A new scene fades in over the one before it instead of replacing it at once.
        if (scene != shownScene) {
            if (shownScene != null && isLaidOut) {
                previousScene = shownScene
                enter = 0f
                enterStart = now
            }
            shownScene = scene
        }
        if (previousScene != null) {
            enter = ((now - enterStart) / 1e9f / FADE_S).coerceIn(0f, 1f)
            if (enter >= 1f) previousScene = null
        }
        val before = previousScene
        if (before != null) {
            val eased = enter * enter * (3f - 2f * enter)
            canvas.saveLayerAlpha(0f, horizon - 30 * density, w, h, ((1f - eased) * 255).toInt())
            drawScene(canvas, before, w, h, horizon, road, tone)
            canvas.restore()
            canvas.saveLayerAlpha(0f, horizon - 30 * density, w, h, (eased * 255).toInt())
            drawScene(canvas, scene, w, h, horizon, road, tone)
            canvas.restore()
        } else drawScene(canvas, scene, w, h, horizon, road, tone)
        if (cameraMeters != null && !bend && !circle) drawCamera(canvas, cx, w, h, horizon)

        // A dark band at the bottom keeps the trip line readable over the road.
        // A short shade at the road's foot, under the car.
        val band = 12 * density
        roadPaint.shader = linear(0f, h - band, 0f, h, 0x000B1018, 0xD90B1018.toInt())
        canvas.drawRect(0f, h - band, w, h, roadPaint)
        // The line under the trip summary, where the road meets the card's foot: plain in the road's colour when
        // idle; while guiding it fills over the last 500 m before the turn (or the camera).
        val metres = if (cameraFirst) cameraMeters else liveMeters
        run {
            val left = 10 * density
            val thick = 4 * density
            val y = h - thick - 1.5f * density
            arrowPaint.shader = null
            if (guiding && metres != null) {
                val fraction = (1f - metres.coerceAtMost(FAR_M) / FAR_M.toFloat()).coerceIn(.03f, 1f)
                arrowPaint.color = ColorUtils.setAlphaComponent(textColor, 0x33)
                rect.set(left, y, w - left, y + thick)
                canvas.drawRoundRect(rect, thick / 2, thick / 2, arrowPaint)
                val colour = when { cameraFirst -> alert; arriving -> arriveColor; else -> guideColor }
                rect.set(left, y, left + (w - 2 * left) * fraction, y + thick)
                // A gradient along the filled part: pale where it started, full colour at its head.
                arrowPaint.shader = linear(left, 0f, w - left, 0f, ColorUtils.blendARGB(colour, Color.WHITE, .6f), colour)
            } else {
                rect.set(left, y, w - left, y + thick)
                // Idle: the road's colour fading out to the right.
                arrowPaint.shader = linear(left, 0f, w - left, 0f, tone, ColorUtils.setAlphaComponent(tone, 0x26))
            }
            arrowPaint.color = Color.WHITE
            canvas.drawRoundRect(rect, thick / 2, thick / 2, arrowPaint)
            arrowPaint.shader = null
        }
        // Indicator on the side of a close turn, blinking.
        val blinkSide = if (bend && (liveMeters ?: 0) <= 300) (if (turn!!.turn == Turn.LEFT) -1 else 1) else 0
        val blinkOn = blinkSide != 0 && (System.nanoTime() / 450_000_000L) % 2L == 0L
        drawCar(canvas, cx, h - band + 3 * density, min(w * .28f, 84 * density), if (blinkOn) blinkSide else 0)

        // The speed.
        valuePaint.textSize = size
        valuePaint.color = if (over) alert else textColor
        valuePaint.setShadowLayer(10 * density, 0f, 0f, ColorUtils.setAlphaComponent(tone, 0x80))
        val speedX = if (guiding) w * .64f else cx
        canvas.drawText(speed?.let { shown.roundToInt().toString() } ?: "—", speedX, baseline, valuePaint)
        if (!guiding) {
            unitPaint.textSize = 10 * density
            canvas.drawText("KM/H", cx, baseline + 13 * density, unitPaint)
        } else drawPill(canvas, turn, arriving)
        if (footer > 0f) canvas.restore()

        when {
            // Every display frame where the head unit keeps up; every other one where it does not, rather than
            // asking for frames it cannot draw.
            (shown > .5f || guiding || previousScene != null) && isShown && windowVisibility == VISIBLE ->
                if (frameMs < 24f) postInvalidateOnAnimation() else postInvalidateDelayed(33)
            blinkSide != 0 && isShown && windowVisibility == VISIBLE -> { lastFrame = 0L; postInvalidateDelayed(225) }
            else -> lastFrame = 0L
        }
    }

    /** The straight road to the horizon: asphalt, lit curbs, white edge lines, lane marks and reflector posts. */
    private fun drawStraight(canvas: Canvas, w: Float, h: Float, horizon: Float, tone: Int) {
        val cx = w / 2f
        val far = w * .035f
        val near = w * .78f
        fun half(y: Float) = far + (near - far) * ((y - horizon) / (h - horizon))
        path.reset()
        path.moveTo(cx - far, horizon)
        path.lineTo(cx + far, horizon)
        path.lineTo(cx + near, h)
        path.lineTo(cx - near, h)
        path.close()
        // Asphalt: fading into the dark at night, solid under the daylight.
        roadPaint.shader = if (night) linear(0f, horizon, 0f, h, 0x001A2230, 0xF01A2230.toInt())
            else linear(0f, horizon, 0f, h, 0xFF5A6676.toInt(), 0xFF2C343F.toInt())
        canvas.drawPath(path, roadPaint)
        roadPaint.shader = linear(0f, horizon, 0f, h, ColorUtils.setAlphaComponent(tone, 0),
            ColorUtils.setAlphaComponent(tone, 0x2E))
        canvas.drawPath(path, roadPaint)
        edgePaint.shader = linear(0f, horizon, 0f, h, ColorUtils.setAlphaComponent(tone, 0x10),
            ColorUtils.setAlphaComponent(tone, 0x50))
        edgePaint.strokeWidth = 7 * density
        canvas.drawLine(cx - far, horizon, cx - near, h, edgePaint)
        canvas.drawLine(cx + far, horizon, cx + near, h, edgePaint)
        edgePaint.shader = linear(0f, horizon, 0f, h, ColorUtils.setAlphaComponent(tone, 0x20), tone)
        edgePaint.strokeWidth = 2.2f * density
        canvas.drawLine(cx - far, horizon, cx - near, h, edgePaint)
        canvas.drawLine(cx + far, horizon, cx + near, h, edgePaint)
        edgePaint.shader = linear(0f, horizon, 0f, h, ColorUtils.setAlphaComponent(laneColor, 0x10),
            ColorUtils.setAlphaComponent(laneColor, 0xB0))
        edgePaint.strokeWidth = 1.6f * density
        canvas.drawLine(cx - far * .86f, horizon, cx - near * .86f, h, edgePaint)
        canvas.drawLine(cx + far * .86f, horizon, cx + near * .86f, h, edgePaint)
        edgePaint.shader = null
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
            markPaint.color = ColorUtils.setAlphaComponent(laneColor, (30 + 190 * fade).roundToInt())
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
                markPaint.color = ColorUtils.setAlphaComponent(laneColor, (25 + 120 * fade).roundToInt())
                canvas.drawRect(x - wide * .5f, y0 - tall, x + wide * .5f, y0, markPaint)
                markPaint.color = ColorUtils.setAlphaComponent(0xFFFFB020.toInt(), (60 + 190 * fade).roundToInt())
                canvas.drawRect(x - wide * .5f, y0 - tall, x + wide * .5f, y0 - tall * .75f, markPaint)
            }
        }
    }

    /**
     * Going straight on while guiding: chevrons of light running up the car's lane towards the horizon, smaller and
     * fainter with the distance, like the lane guidance of a recent head-up display.
     */
    private fun drawAhead(canvas: Canvas, cx: Float, horizon: Float, road: Float, w: Float) {
        val count = 5
        for (index in 0 until count) {
            // 0 just ahead of the car, 1 at the far end; evenly spread in depth and moving away.
            val t = (index + flowPhase) / count
            val depth = 1f + t * 5.2f
            val y = horizon + road * .66f / depth
            val scale = 1f / depth
            val half = w * .15f * scale
            val tall = road * .2f * scale
            val thick = tall * .52f
            val fade = (minOf(t / .14f, 1f) * minOf((1f - t) / .3f, 1f)).coerceIn(0f, 1f)
            path.reset()
            path.moveTo(cx, y - tall)
            path.lineTo(cx + half, y)
            path.lineTo(cx + half, y + thick)
            path.lineTo(cx, y - tall + thick)
            path.lineTo(cx - half, y + thick)
            path.lineTo(cx - half, y)
            path.close()
            arrowPaint.shader = null
            arrowPaint.color = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(0xFF2E78F0.toInt(), 0xFF9CD0FF.toInt(), t), (fade * 245).toInt())
            canvas.drawPath(path, arrowPaint)
        }
        arrowPaint.shader = null
    }

    /**
     * A turn that is close: the road itself bends to that side and the arrow follows it, with chevrons running
     * along it. Drawn for a right turn and mirrored for a left one.
     */
    private fun drawBend(canvas: Canvas, w: Float, horizon: Float, road: Float, tone: Int, left: Boolean) {
        fun x(f: Float) = w * f
        fun y(f: Float) = horizon + road * f
        canvas.save()
        if (left) canvas.scale(-1f, 1f, w / 2f, 0f)
        path.reset()
        path.moveTo(x(.02f), y(1f))
        path.lineTo(x(.367f), y(.284f))
        path.cubicTo(x(.43f), y(.137f), x(.508f), y(.084f), x(.625f), y(.084f))
        path.lineTo(x(1f), y(.084f))
        path.lineTo(x(1f), y(.4f))
        path.lineTo(x(.696f), y(.4f))
        path.cubicTo(x(.633f), y(.4f), x(.608f), y(.453f), x(.592f), y(.547f))
        path.lineTo(x(.98f), y(1f))
        path.close()
        roadPaint.shader = null
        roadPaint.color = 0xF01C2535.toInt()
        canvas.drawPath(path, roadPaint)
        roadPaint.color = ColorUtils.setAlphaComponent(tone, 0x22)
        canvas.drawPath(path, roadPaint)
        // The two curbs.
        linePaint.strokeWidth = 2.4f * density
        linePaint.color = tone
        path.reset()
        path.moveTo(x(.02f), y(1f))
        path.lineTo(x(.367f), y(.284f))
        path.cubicTo(x(.43f), y(.137f), x(.508f), y(.084f), x(.625f), y(.084f))
        path.lineTo(x(1f), y(.084f))
        canvas.drawPath(path, linePaint)
        path.reset()
        path.moveTo(x(.98f), y(1f))
        path.lineTo(x(.605f), y(.57f))
        path.cubicTo(x(.6f), y(.47f), x(.63f), y(.4f), x(.696f), y(.4f))
        path.lineTo(x(1f), y(.4f))
        canvas.drawPath(path, linePaint)
        // The arrow, from the car into the turn.
        path.reset()
        path.moveTo(x(.483f), y(.663f))
        path.lineTo(x(.492f), y(.421f))
        path.cubicTo(x(.496f), y(.295f), x(.542f), y(.242f), x(.625f), y(.242f))
        path.lineTo(x(.775f), y(.242f))
        path.lineTo(x(.775f), y(.147f))
        path.lineTo(x(.883f), y(.295f))
        path.lineTo(x(.775f), y(.442f))
        path.lineTo(x(.775f), y(.347f))
        path.lineTo(x(.625f), y(.347f))
        path.cubicTo(x(.567f), y(.347f), x(.5375f), y(.379f), x(.533f), y(.463f))
        path.lineTo(x(.5375f), y(.663f))
        path.close()
        drawLitArrow(canvas, path, y(.147f), y(.663f))
        // Chevrons of light running to the arrow's head, fading in and out.
        for (index in 0 until 3) {
            val t = (index + flowPhase) / 3f
            val f = .585f + t * .17f
            val fade = (minOf(t / .2f, 1f) * minOf((1f - t) / .25f, 1f)).coerceIn(0f, 1f)
            arrowPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, (fade * 230).toInt())
            part.reset()
            part.moveTo(x(f), y(.262f))
            part.lineTo(x(f + .024f), y(.295f))
            part.lineTo(x(f), y(.328f))
            part.lineTo(x(f + .008f), y(.295f))
            part.close()
            canvas.drawPath(part, arrowPaint)
        }
        canvas.restore()
    }

    /**
     * A roundabout that is close: the ring, the roads joining it and the arrow going round (anticlockwise, as where
     * traffic keeps right) to the exit the instruction counts: first to the right, second straight on, third to the
     * left; when it gives no number, the arrow leaves up and to the right.
     */
    private fun drawRoundabout(canvas: Canvas, w: Float, horizon: Float, road: Float, tone: Int, exit: Int?) {
        val cx = w / 2f
        val cy = horizon + road * .27f
        val r = min(road * .3f, w * .2f)
        roadPaint.shader = null
        roadPaint.color = 0xF01C2535.toInt()
        // The road the car is on, and the three others.
        path.reset()
        path.moveTo(cx - w * .09f, horizon + road)
        path.lineTo(cx - r * .32f, cy + r * .8f)
        path.lineTo(cx + r * .32f, cy + r * .8f)
        path.lineTo(cx + w * .09f, horizon + road)
        path.close()
        canvas.drawPath(path, roadPaint)
        canvas.drawRect(0f, cy - r * .3f, cx - r * .8f, cy + r * .3f, roadPaint)
        canvas.drawRect(cx + r * .8f, cy - r * .3f, w, cy + r * .3f, roadPaint)
        canvas.drawRect(cx - r * .26f, horizon - r * .1f, cx + r * .26f, cy - r * .8f, roadPaint)
        canvas.drawCircle(cx, cy, r, roadPaint)
        linePaint.color = tone
        linePaint.strokeWidth = 2.4f * density
        canvas.drawCircle(cx, cy, r, linePaint)
        roadPaint.color = 0xFF0F1A2C.toInt()
        canvas.drawCircle(cx, cy, r * .42f, roadPaint)
        linePaint.strokeWidth = 1.8f * density
        canvas.drawCircle(cx, cy, r * .42f, linePaint)
        // The arrow: in at the bottom, round the ring, out at the exit.
        val out = when (exit) { 1 -> 0f; 2 -> -90f; 3 -> -180f; null -> -45f; else -> -225f }
        val ring = r * .71f
        rect.set(cx - ring, cy - ring, cx + ring, cy + ring)
        path.reset()
        path.moveTo(cx, horizon + road * .62f)
        path.lineTo(cx, cy + ring)
        path.arcTo(rect, 90f, out - 90f)
        val angle = Math.toRadians(out.toDouble())
        val dx = Math.cos(angle).toFloat()
        val dy = Math.sin(angle).toFloat()
        val endX = cx + dx * r * 1.2f
        val endY = cy + dy * r * 1.2f
        path.lineTo(endX, endY)
        linePaint.color = ColorUtils.setAlphaComponent(guideColor, 0x38)
        linePaint.strokeWidth = w * .034f + 7 * density
        canvas.drawPath(path, linePaint)
        linePaint.shader = linear(0f, cy - r, 0f, horizon + road * .62f, 0xFF7CC0FF.toInt(), 0xFF2468E0.toInt())
        linePaint.color = guideColor
        linePaint.strokeWidth = w * .034f
        canvas.drawPath(path, linePaint)
        linePaint.shader = null
        val head = w * .055f
        path.reset()
        path.moveTo(endX + dx * head, endY + dy * head)
        path.lineTo(endX - dy * head * .8f, endY + dx * head * .8f)
        path.lineTo(endX + dy * head * .8f, endY - dx * head * .8f)
        path.close()
        arrowPaint.shader = null
        arrowPaint.color = guideColor
        canvas.drawPath(path, arrowPaint)
        if (exit != null) {
            // The exit's number beside the arrow's head, clear of the figures above.
            val bx = endX - dy * head * 1.7f + dx * head * .3f
            val by = endY + kotlin.math.abs(dx) * head * 1.7f + dy * head * .3f
            canvas.drawCircle(bx, by, 9 * density, arrowPaint)
            textPaint.color = Color.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 11 * density
            canvas.drawText(exit.toString(), bx, by + 4 * density, textPaint)
        }
    }

    /** A U-turn: up the lane, round to the left and back down. */
    private fun drawUTurn(canvas: Canvas, cx: Float, horizon: Float, road: Float, w: Float) {
        val right = cx + w * .03f
        val left = cx - w * .12f
        val top = horizon + road * .16f
        path.reset()
        path.moveTo(right, horizon + road * .58f)
        path.lineTo(right, top + road * .12f)
        rect.set(left, top, right, top + road * .24f)
        path.arcTo(rect, 0f, -180f)
        path.lineTo(left, horizon + road * .44f)
        linePaint.color = ColorUtils.setAlphaComponent(guideColor, 0x38)
        linePaint.strokeWidth = w * .036f + 7 * density
        canvas.drawPath(path, linePaint)
        linePaint.shader = linear(0f, top, 0f, horizon + road * .58f, 0xFF7CC0FF.toInt(), 0xFF2468E0.toInt())
        linePaint.color = guideColor
        linePaint.strokeWidth = w * .036f
        canvas.drawPath(path, linePaint)
        linePaint.shader = null
        val head = w * .055f
        path.reset()
        path.moveTo(left, horizon + road * .44f + head)
        path.lineTo(left - head * .8f, horizon + road * .44f)
        path.lineTo(left + head * .8f, horizon + road * .44f)
        path.close()
        arrowPaint.shader = null
        arrowPaint.color = guideColor
        canvas.drawPath(path, arrowPaint)
    }

    /** The destination: a green flag standing on the road ahead. */
    private fun drawFlag(canvas: Canvas, cx: Float, horizon: Float, road: Float) {
        val foot = horizon + road * .14f
        val tall = 30 * density
        linePaint.color = Color.WHITE
        linePaint.strokeWidth = 2.2f * density
        canvas.drawLine(cx, foot, cx, foot - tall, linePaint)
        path.reset()
        path.moveTo(cx, foot - tall)
        path.lineTo(cx + 22 * density, foot - tall + 7 * density)
        path.lineTo(cx, foot - tall + 14 * density)
        path.close()
        arrowPaint.shader = null
        arrowPaint.color = arriveColor
        canvas.drawPath(path, arrowPaint)
        canvas.drawCircle(cx, foot, 5 * density, arrowPaint)
        linePaint.strokeWidth = 1.6f * density
        canvas.drawCircle(cx, foot, 5 * density, linePaint)
    }

    /** The speed camera on the right verge and its limit on the left, nearer as the car approaches; a red zone on the road. */
    private fun drawCamera(canvas: Canvas, cx: Float, w: Float, h: Float, horizon: Float) {
        val metres = cameraMeters ?: return
        val t = (1f - metres.coerceAtMost(FAR_M) / FAR_M.toFloat()).coerceIn(0f, 1f)
        val road = h - horizon
        val y = horizon + road * (.1f + .42f * t)
        val half = w * .035f + (w * .78f - w * .035f) * ((y - horizon) / road)
        val scale = .55f + .65f * t
        // The zone before the camera, in red on the roadway.
        path.reset()
        val y0 = y - road * .07f
        val half0 = w * .035f + (w * .78f - w * .035f) * ((y0 - horizon) / road)
        path.moveTo(cx - half0, y0)
        path.lineTo(cx + half0, y0)
        path.lineTo(cx + half, y)
        path.lineTo(cx - half, y)
        path.close()
        arrowPaint.shader = null
        arrowPaint.color = 0x59FF2B2B
        canvas.drawPath(path, arrowPaint)
        // The camera on its pole.
        val x = cx + half + 8 * density * scale
        val pole = 22 * density * scale
        linePaint.color = 0xFFC9CDD3.toInt()
        linePaint.strokeWidth = 2 * density * scale
        canvas.drawLine(x, y, x, y - pole, linePaint)
        val bw = 26 * density * scale
        val bh = 18 * density * scale
        rect.set(x - bw / 2, y - pole - bh, x + bw / 2, y - pole)
        arrowPaint.color = Color.WHITE
        canvas.drawRoundRect(rect, 4 * density * scale, 4 * density * scale, arrowPaint)
        rect.inset(1.6f * density * scale, 1.6f * density * scale)
        arrowPaint.color = 0xFFE5262E.toInt()
        canvas.drawRoundRect(rect, 3 * density * scale, 3 * density * scale, arrowPaint)
        arrowPaint.color = Color.WHITE
        rect.set(x - bw * .3f, y - pole - bh * .7f, x + bw * .08f, y - pole - bh * .3f)
        canvas.drawRoundRect(rect, density, density, arrowPaint)
        canvas.drawCircle(x + bw * .24f, y - pole - bh * .5f, bh * .12f, arrowPaint)
        // Its limit as a road sign on the other verge.
        val limit = cameraLimit ?: return
        val lx = cx - half - 8 * density * scale
        canvas.drawLine(lx, y, lx, y - pole, linePaint)
        val radius = 11 * density * scale
        arrowPaint.color = Color.WHITE
        canvas.drawCircle(lx, y - pole - radius, radius, arrowPaint)
        linePaint.color = 0xFFE5262E.toInt()
        linePaint.strokeWidth = 3 * density * scale
        canvas.drawCircle(lx, y - pole - radius, radius - 1.5f * density * scale, linePaint)
        textPaint.color = 0xFF222222.toInt()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 10 * density * scale
        canvas.drawText(limit.toString(), lx, y - pole - radius + 3.6f * density * scale, textPaint)
    }

    /** The street to take, white on green like a road sign, over the far end of the turn. */
    private fun drawStreetSign(canvas: Canvas, w: Float, horizon: Float, road: Float, street: String, left: Boolean) {
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 11 * density
        val name = if (street.length > 20) street.take(19) + "…" else street
        val label = if (left) "← $name" else "$name →"
        val wide = min(textPaint.measureText(label) + 14 * density, w * .5f)
        val bottom = horizon + road * .084f - 5 * density
        val centre = if (left) w * .04f + wide / 2 else w * .96f - wide / 2
        rect.set(centre - wide / 2, bottom - 20 * density, centre + wide / 2, bottom)
        arrowPaint.shader = null
        arrowPaint.color = Color.WHITE
        canvas.drawRoundRect(rect, 4 * density, 4 * density, arrowPaint)
        rect.inset(1.2f * density, 1.2f * density)
        arrowPaint.color = 0xFF0B6B3A.toInt()
        canvas.drawRoundRect(rect, 3 * density, 3 * density, arrowPaint)
        textPaint.color = Color.WHITE
        canvas.save()
        canvas.clipRect(rect)
        canvas.drawText(label, centre, bottom - 6.5f * density, textPaint)
        canvas.restore()
    }

    /** Top left: the navigation app's own arrow, the distance, and in a word what comes (or the camera). */
    private fun drawPill(canvas: Canvas, turn: Maneuver?, arriving: Boolean) {
        val camera = cameraFirst
        val title = if (camera) "${(cameraMeters!! / 10) * 10} m" else liveStep()?.let { if (it == "maintenant") "ici" else it } ?: stepText ?: "Guidage"
        val detail = when {
            camera -> listOfNotNull("Radar", cameraLimit?.toString()).joinToString(" · ")
            turn == null -> ""
            else -> when (turn.turn) {
                Turn.LEFT -> "à gauche"
                Turn.RIGHT -> "à droite"
                Turn.ROUNDABOUT -> turn.exit?.let { "sortie $it" } ?: "rond-point"
                Turn.UTURN -> "demi-tour"
                Turn.ARRIVE -> "arrivée"
                Turn.STRAIGHT -> "tout droit"
            }
        }
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 700, false)
        textPaint.textSize = 17 * density
        val titleWidth = textPaint.measureText(title)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 10 * density
        val detailWidth = textPaint.measureText(detail)
        val icon = 26 * density
        val left = 8 * density
        val top = 8 * density
        val wide = min(icon + 20 * density + maxOf(titleWidth, detailWidth), width * .5f)
        rect.set(left, top, left + wide, top + 40 * density)
        arrowPaint.shader = null
        arrowPaint.color = when { camera -> 0xF05A1418.toInt(); arriving -> 0xF00B4A37.toInt(); else -> 0xF0102A5C.toInt() }
        canvas.drawRoundRect(rect, 12 * density, 12 * density, arrowPaint)
        if (camera) {
            linePaint.color = 0xFFE5262E.toInt()
            linePaint.strokeWidth = 1.4f * density
            canvas.drawRoundRect(rect, 12 * density, 12 * density, linePaint)
        }
        val ix = left + 7 * density
        val iy = top + 7 * density
        val drawable = turnIcon
        if (!camera && drawable != null) {
            drawable.setBounds(ix.toInt(), iy.toInt(), (ix + icon).toInt(), (iy + icon).toInt())
            drawable.draw(canvas)
        } else {
            // A small camera, or a plain arrow when the app sent no icon.
            arrowPaint.color = Color.WHITE
            if (camera) {
                rect.set(ix + 2 * density, iy + 7 * density, ix + 17 * density, iy + 19 * density)
                canvas.drawRoundRect(rect, 2 * density, 2 * density, arrowPaint)
                canvas.drawCircle(ix + 22 * density, iy + 13 * density, 3 * density, arrowPaint)
            } else {
                path.reset()
                path.moveTo(ix + icon / 2, iy + 3 * density)
                path.lineTo(ix + icon - 4 * density, iy + icon - 5 * density)
                path.lineTo(ix + icon / 2, iy + icon - 9 * density)
                path.lineTo(ix + 4 * density, iy + icon - 5 * density)
                path.close()
                canvas.drawPath(path, arrowPaint)
            }
        }
        val tx = ix + icon + 6 * density
        canvas.save()
        canvas.clipRect(left, top, left + wide - 4 * density, top + 40 * density)
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 700, false)
        textPaint.textSize = 17 * density
        canvas.drawText(title, tx, top + 19 * density, textPaint)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 10 * density
        textPaint.color = 0xE6FFFFFF.toInt()
        canvas.drawText(detail, tx, top + 32 * density, textPaint)
        canvas.restore()
    }

    /**
     * A compact hybrid saloon from behind, in the lines of the driver's Corolla Hybrid: pearl-white body with rounded
     * shoulders, sloping rear window, door mirrors, two wrap-around tail lights joined by a dark trim strip with the
     * driver's emblem between them, the plate, and a black lower bumper with reflectors. The lights brighten on braking.
     */
    private fun drawCar(canvas: Canvas, cx: Float, bottom: Float, carW: Float, blink: Int = 0) {
        val u = carW
        val v = carW * .62f
        val top = bottom - v
        fun x(f: Float) = cx + u * f
        fun y(f: Float) = top + v * f
        // At night, the headlights' beam on the road ahead.
        if (night) {
            carPaint.shader = linear(0f, top - v * .9f, 0f, top + v * .3f, 0x00FFF4D6, 0x38FFF4D6)
            path.reset()
            path.moveTo(cx - u * .34f, top + v * .3f)
            path.lineTo(cx - u * .62f, top - v * .9f)
            path.lineTo(cx + u * .62f, top - v * .9f)
            path.lineTo(cx + u * .34f, top + v * .3f)
            path.close()
            canvas.drawPath(path, carPaint)
        }
        // Shadow and tyres.
        carPaint.shader = radial(cx, bottom, u * .8f, 0xA0000000.toInt(), 0)
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
        // The body in the driver's colour, lit from above.
        val pearl = linear(0f, top, 0f, bottom, ColorUtils.blendARGB(bodyColor, Color.WHITE, .12f),
            ColorUtils.blendARGB(bodyColor, Color.BLACK, .26f))
        carPaint.shader = pearl
        path.reset()
        path.moveTo(x(-.42f), y(.46f))
        path.cubicTo(x(-.36f), y(.2f), x(-.3f), y(.02f), x(-.18f), y(0f))
        path.lineTo(x(.18f), y(0f))
        path.cubicTo(x(.3f), y(.02f), x(.36f), y(.2f), x(.42f), y(.46f))
        path.close()
        canvas.drawPath(path, carPaint)
        // Rear window, dark glass with a reflection.
        carPaint.shader = linear(0f, y(.04f), 0f, y(.4f), 0xFF2E3A4E.toInt(), 0xFF070B12.toInt())
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
            carPaint.color = when {
                blink != 0 && side == blink.toFloat() -> 0xFFFFB020.toInt() // the indicator, on the side of the turn
                glowing -> 0xFFFF2B2B.toInt()
                else -> 0xFFD8262E.toInt()
            }
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
        // A turn is drawn on the road within this distance; the countdown bar fills over the second one.
        const val FADE_S = .45f
        const val NEAR_M = 400
        const val FAR_M = 500
        val LANES = floatArrayOf(-1f / 3f, 1f / 3f)
        val SIDES = floatArrayOf(-1f, 1f)
    }
}
