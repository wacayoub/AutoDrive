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
import kotlin.math.sqrt

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
            when {
                read.turn == Turn.STRAIGHT && it.turn != null -> read.copy(turn = it.turn, slight = it.slight)
                // The words say « à droite », the arrow leans off to that side: a slip road or a fork, not a square turn.
                read.turn == it.turn && it.slight -> read.copy(slight = true)
                else -> read
            }
        }
        turnIcon = nav?.arrow
        // The whole route, from where the guidance started (A) to the destination (B): the longest distance left
        // seen is the route's length, and the bar under the road fills as that distance shrinks.
        val left = Maneuver.meters(nav?.distance)
        if (nav == null) { routeMeters = 0; routeLeft = null; stretches.clear() } else if (left != null) {
            routeLeft = left
            // A much longer distance than before is a new destination: start again from A.
            if (left > routeMeters) { routeMeters = left; stretches.clear() }
            // Each change of Google Maps' traffic colour starts a new stretch of the bar where the car is now, so
            // the part already driven keeps the colours Maps gave along the way.
            val at = 1f - left / routeMeters.toFloat()
            if (stretches.isEmpty() || stretches.last().second != nav.flow) stretches += at to nav.flow
        }
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

    /**
     * The traffic along the road ahead, stretch by stretch (metres from the car), nearest first: Google's along the
     * whole trip, or TomTom's over the next kilometres. Drawn on the road's edges like the coloured route line of a
     * navigation app, and on the A to B bar at its place on the trip.
     */
    var aheadFlow: List<FlowStretch> = emptyList()
        set(value) { if (field != value) { field = value; invalidate() } }

    /** TomTom's traffic ahead in words (« Bouchon à 500 m · 18 km/h »), or null while the road is free. */
    var traffic: String? = null
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

    // The sky outside: its colours for this hour and weather, where the sun stands, and what falls from it.
    private var outside: Sky? = null
    private var elevation = 45f
    private var weather: com.autodrive.radiomaroc.WeatherCondition? = null
    // Dark outside (the sun well below the horizon): night road, headlights and light figures, whatever the theme.
    private var dark = night

    /**
     * The « Studio » look: no sky, the road drawn lightly on the card itself, light or dark with the theme and not
     * with the hour outside.
     */
    var flat = false
        set(value) { field = value; if (value) dark = night; invalidate() }

    /** The sky of this hour and this weather: [elevation] is the sun's height in degrees, negative below the horizon. */
    fun outside(sky: Sky, elevation: Float, weather: com.autodrive.radiomaroc.WeatherCondition?) {
        if (sky == outside && weather == this.weather && kotlin.math.abs(elevation - this.elevation) < .3f) return
        outside = sky
        this.elevation = elevation
        this.weather = weather
        dark = if (flat) night else elevation < -4f
        invalidate()
    }

    private val ink get() = if (dark) Color.WHITE else 0xFF10161F.toInt()

    // The same scattered stars and clouds every time.
    private val starSeeds = FloatArray(90) {
        // Well mixed, so stars and drops never line up.
        val value = kotlin.math.sin(it * 12.9898 + 78.233) * 43758.5453
        (value - kotlin.math.floor(value)).toFloat()
    }
    private var drift = 0f

    /** The sky above the horizon, and by day the ground on both sides of the road. */
    private fun drawOutside(canvas: Canvas, sky: Sky, w: Float, h: Float, horizon: Float) {
        val cloudy = when (weather) {
            com.autodrive.radiomaroc.WeatherCondition.PARTLY_CLOUDY -> .45f
            com.autodrive.radiomaroc.WeatherCondition.CLOUDY, com.autodrive.radiomaroc.WeatherCondition.FOG -> 1f
            com.autodrive.radiomaroc.WeatherCondition.DRIZZLE, com.autodrive.radiomaroc.WeatherCondition.SHOWERS -> .8f
            com.autodrive.radiomaroc.WeatherCondition.RAIN, com.autodrive.radiomaroc.WeatherCondition.STORM,
            com.autodrive.radiomaroc.WeatherCondition.SNOW -> 1f
            else -> 0f
        }
        // The palette's colours are pastel, made for the home screen behind the cards: here a clear day gets a
        // fuller blue, less so as clouds cover it or as the low sun colours it.
        val blue = if (dark) 0f else (.55f * (1f - cloudy * .8f) * (elevation / 22f).coerceIn(0f, 1f))
        val top = ColorUtils.blendARGB(sky.top, 0xFF3A86E2.toInt(), blue)
        val mid = ColorUtils.blendARGB(sky.mid, 0xFFCFE6FF.toInt(), blue * .7f)
        roadPaint.shader = linear(0f, 0f, 0f, horizon, top, if (dark) sky.bottom else mid)
        canvas.drawRect(0f, 0f, w, horizon, roadPaint)
        if (dark) {
            // Stars, fewer and fainter under clouds.
            val shine = (sky.stars.coerceAtLeast(if (cloudy < .5f) .5f else 0f) * (1f - cloudy)).coerceIn(0f, 1f)
            if (shine > 0f) {
                arrowPaint.shader = null
                for (i in 0 until starSeeds.size / 3) {
                    val x = starSeeds[i * 3] * w
                    val y = starSeeds[i * 3 + 1] * horizon * .92f
                    val twinkle = .55f + .45f * kotlin.math.sin(drift * 6f + i * 1.7f)
                    arrowPaint.color = ColorUtils.setAlphaComponent(Color.WHITE,
                        (220 * shine * twinkle * (1f - y / horizon * .6f)).toInt().coerceIn(0, 255))
                    canvas.drawCircle(x, y, (0.6f + starSeeds[i * 3 + 2]) * density * .8f, arrowPaint)
                }
            }
        } else {
            // The sun, where it stands now: east on the left, west on the right, low near the horizon.
            if (elevation > -2f && cloudy < 1f) {
                val x = w * sky.sunX.coerceIn(.06f, .94f)
                val y = horizon * (1f - (elevation / 70f).coerceIn(0f, 1f) * .82f)
                val low = (1f - elevation / 18f).coerceIn(0f, 1f)
                val warm = ColorUtils.blendARGB(0xFFFFF6D8.toInt(), 0xFFFF9A45.toInt(), low)
                val strength = (1f - cloudy * .75f)
                glowPaint.shader = radial(x, y, w * (.3f + .2f * low), ColorUtils.setAlphaComponent(warm, (150 * strength).toInt()),
                    ColorUtils.setAlphaComponent(warm, 0))
                canvas.save()
                canvas.clipRect(0f, 0f, w, horizon)
                canvas.drawRect(0f, 0f, w, horizon, glowPaint)
                arrowPaint.shader = null
                arrowPaint.color = ColorUtils.setAlphaComponent(warm, (255 * strength).toInt())
                canvas.drawCircle(x, y, 7 * density, arrowPaint)
                canvas.restore()
            }
            // Ground beside the road: lit by the sky's own colour at the horizon.
            roadPaint.shader = linear(0f, horizon, 0f, h, ColorUtils.blendARGB(sky.bottom, 0xFFDFE9F1.toInt(), .55f),
                ColorUtils.blendARGB(sky.bottom, 0xFFA9BCCD.toInt(), .7f))
            canvas.drawRect(0f, horizon, w, h, roadPaint)
        }
        // Clouds drifting across, more of them as the sky covers.
        if (cloudy > 0f) {
            arrowPaint.shader = null
            val count = (2 + cloudy * 4).toInt()
            for (i in 0 until count) {
                val seed = starSeeds[i * 5 + 2]
                val x = ((seed + drift * (.012f + .01f * starSeeds[i + 40])) % 1.3f - .15f) * w
                val y = horizon * (.12f + .55f * starSeeds[i * 2 + 7])
                val size = w * (.14f + .1f * starSeeds[i + 20])
                arrowPaint.color = if (dark) ColorUtils.setAlphaComponent(0xFF8A94A6.toInt(), (60 + 60 * cloudy).toInt())
                    else ColorUtils.setAlphaComponent(Color.WHITE, (150 + 80 * cloudy).toInt())
                rect.set(x - size, y - size * .26f, x + size, y + size * .26f)
                canvas.drawRoundRect(rect, size * .26f, size * .26f, arrowPaint)
                canvas.drawCircle(x - size * .3f, y - size * .2f, size * .3f, arrowPaint)
                canvas.drawCircle(x + size * .2f, y - size * .28f, size * .38f, arrowPaint)
            }
        }
        // Rain or snow falling over the whole view.
        val falling = when (weather) {
            com.autodrive.radiomaroc.WeatherCondition.DRIZZLE -> 14
            com.autodrive.radiomaroc.WeatherCondition.SHOWERS, com.autodrive.radiomaroc.WeatherCondition.RAIN -> 26
            com.autodrive.radiomaroc.WeatherCondition.STORM -> 36
            com.autodrive.radiomaroc.WeatherCondition.SNOW -> 22
            else -> 0
        }
        if (falling > 0) {
            val snow = weather == com.autodrive.radiomaroc.WeatherCondition.SNOW
            // Pale drops against the night, slate ones against the day's light sky; snow stays white.
            linePaint.color = ColorUtils.setAlphaComponent(if (dark || snow) 0xFFD6E4F7.toInt() else 0xFF5B7896.toInt(),
                if (snow) 230 else 150)
            linePaint.strokeWidth = (if (snow) 2.2f else 1.1f) * density
            for (i in 0 until falling) {
                val x = starSeeds[i] * w
                val speed = if (snow) .25f else 1.6f
                val y = ((starSeeds[i + 30] + drift * speed * (.7f + starSeeds[i + 50] * .6f)) % 1f) * h
                if (snow) canvas.drawPoint(x + kotlin.math.sin(drift * 2f + i) * 4 * density, y, linePaint)
                else canvas.drawLine(x, y, x - 3 * density, y + 9 * density, linePaint)
            }
        }
    }

    /** What is drawn on the road: a bend or a roundabout when close, else the straight road with what lies on it. */
    private data class Scene(val shape: Turn?, val onRoad: Turn?, val exit: Int?, val street: String?, val ahead: Boolean,
                             val slight: Boolean = false)

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
            Turn.LEFT, Turn.RIGHT -> if (scene.slight) drawFork(canvas, w, horizon, road, tone, left = scene.shape == Turn.LEFT)
                else drawBend(canvas, w, horizon, road, tone, left = scene.shape == Turn.LEFT)
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
    // Where along the route (0 at A, 1 at B) each traffic colour of Google Maps began; null = no colour given.
    private val stretches = ArrayList<Pair<Float, Flow?>>()
    private var routeMeters = 0
    private var routeLeft: Int? = null
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
        typeface = Fonts.get(700)
        textAlign = Paint.Align.CENTER
    }
    // A soft dark (or light) edge under the speed, so it stays readable over bright clouds or a dark night sky.
    private val valueEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.get(700)
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
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
        // The sky outside above the horizon (this hour's sun, today's weather), then a soft glow where the road
        // meets it. Until the first sky is known: the theme's own.
        val real = outside
        if (flat) Unit
        else if (real != null) drawOutside(canvas, real, w, h, horizon)
        else if (night) {
            roadPaint.shader = linear(0f, 0f, 0f, horizon, ColorUtils.setAlphaComponent(skyTop, 0xB0),
                ColorUtils.setAlphaComponent(skyBottom, 0xB0))
            canvas.drawRect(0f, 0f, w, horizon, roadPaint)
        } else {
            roadPaint.shader = linear(0f, 0f, 0f, horizon, ColorUtils.blendARGB(skyTop, 0xFF3F8BE6.toInt(), .6f),
                ColorUtils.blendARGB(skyBottom, 0xFFD6EAFF.toInt(), .6f))
            canvas.drawRect(0f, 0f, w, horizon, roadPaint)
            roadPaint.shader = linear(0f, horizon, 0f, h, 0xFFDFE9F1.toInt(), 0xFFB4C6D6.toInt())
            canvas.drawRect(0f, horizon, w, h, roadPaint)
        }
        glowPaint.shader = radial(cx, horizon, w * .55f, ColorUtils.setAlphaComponent(tone, if (flat) 0x14 else 0x26),
            ColorUtils.setAlphaComponent(tone, 0))
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        val now = System.nanoTime()
        if (lastFrame != 0L) {
            val seconds = (now - lastFrame) / 1e9f
            if (seconds < .5f) frameMs += (seconds * 1000f - frameMs) * .08f
            travel = (travel + shown * seconds * .05f) % 2f
            if (seconds < 1.5f) drift += seconds
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
            ahead = !cameraFirst, slight = bend && turn!!.slight)
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
        if (!flat) {
            roadPaint.shader = linear(0f, h - band, 0f, h, 0x000B1018, 0xD90B1018.toInt())
            canvas.drawRect(0f, h - band, w, h, roadPaint)
        }
        // The line where the road meets the card's foot. Idle: plain, in the road's colour. While guiding: the route
        // from A (where the guidance started) to B (the destination), filled as far as the car has come, its colour
        // running from A's blue to B's green, with a dot at each end and one for the car.
        run {
            val thick = 4 * density
            val y = h - thick - 1.5f * density
            val middle = y + thick / 2
            arrowPaint.shader = null
            val leftMetres = routeLeft
            if (turn != null && leftMetres != null && routeMeters > 0) {
                val dot = 4.5f * density
                val from = 10 * density + dot
                val to = w - 10 * density - dot
                val fraction = (1f - leftMetres / routeMeters.toFloat()).coerceIn(0f, 1f)
                val head = from + (to - from) * fraction
                // Ahead: the colour Maps gives the rest of the trip now, faint; plain when it gives none.
                arrowPaint.color = flow?.let { ColorUtils.setAlphaComponent(it.color, 0x66) } ?: ColorUtils.setAlphaComponent(ink, 0x33)
                rect.set(from, y, to, y + thick)
                canvas.drawRoundRect(rect, thick / 2, thick / 2, arrowPaint)
                // The traffic ahead as measured, stretch by stretch, at its place on the trip.
                for ((start, end, stretchFlow) in aheadFlow) {
                    val a = (fraction + start / routeMeters.toFloat()).coerceIn(fraction, 1f)
                    val b = (fraction + end / routeMeters.toFloat()).coerceIn(a, 1f)
                    if (b <= a) continue
                    arrowPaint.color = stretchFlow.color
                    rect.set(from + (to - from) * a, y, from + (to - from) * b, y + thick)
                    canvas.drawRect(rect, arrowPaint)
                }
                // Behind: each stretch in the colour Maps gave while the car drove it (blue where it gave none).
                for (index in stretches.indices) {
                    val start = stretches[index].first.coerceIn(0f, fraction)
                    val end = (stretches.getOrNull(index + 1)?.first ?: fraction).coerceIn(start, fraction)
                    if (end <= start) continue
                    arrowPaint.color = stretches[index].second?.color ?: guideColor
                    rect.set(from + (to - from) * start, y, from + (to - from) * end, y + thick)
                    canvas.drawRect(rect, arrowPaint)
                }
                // A: a blue dot. B: a green one, hollow until the car is there.
                arrowPaint.color = guideColor
                canvas.drawCircle(from, middle, dot, arrowPaint)
                arrowPaint.color = arriveColor
                canvas.drawCircle(to, middle, dot, arrowPaint)
                if (fraction < .97f) {
                    arrowPaint.color = footerColor.takeIf { footer > 0f } ?: 0xFF0B1018.toInt()
                    canvas.drawCircle(to, middle, dot * .5f, arrowPaint)
                }
                // The car on the route.
                arrowPaint.color = Color.WHITE
                canvas.drawCircle(head, middle, dot * .95f, arrowPaint)
                arrowPaint.color = flow?.color ?: guideColor
                canvas.drawCircle(head, middle, dot * .5f, arrowPaint)
            } else {
                val left = 10 * density
                rect.set(left, y, w - left, y + thick)
                arrowPaint.color = Color.WHITE
                arrowPaint.shader = linear(left, 0f, w - left, 0f, tone, ColorUtils.setAlphaComponent(tone, 0x26))
                canvas.drawRoundRect(rect, thick / 2, thick / 2, arrowPaint)
                arrowPaint.shader = null
            }
        }
        // Indicator on the side of a close turn, blinking.
        val blinkSide = if (bend && (liveMeters ?: 0) <= 300) (if (turn!!.turn == Turn.LEFT) -1 else 1) else 0
        val blinkOn = blinkSide != 0 && (System.nanoTime() / 450_000_000L) % 2L == 0L
        drawCar(canvas, cx, h - band + 3 * density, min(w * .26f, 76 * density), if (blinkOn) blinkSide else 0)

        // The speed.
        valuePaint.textSize = size
        valuePaint.color = if (over) alert else ink
        unitPaint.color = ColorUtils.setAlphaComponent(ink, 0xB3)
        valuePaint.setShadowLayer(10 * density, 0f, 0f, ColorUtils.setAlphaComponent(tone, 0x80))
        val speedX = if (guiding) w * .64f else cx
        val figure = speed?.let { shown.roundToInt().toString() } ?: "—"
        valueEdge.textSize = size
        valueEdge.strokeWidth = size * .09f
        valueEdge.color = if (dark || over) 0x8C0B1018.toInt() else 0x99FFFFFF.toInt()
        canvas.drawText(figure, speedX, baseline, valueEdge)
        canvas.drawText(figure, speedX, baseline, valuePaint)
        if (!guiding) {
            unitPaint.textSize = 10 * density
            canvas.drawText("KM/H", cx, baseline + 13 * density, unitPaint)
        } else drawPill(canvas, turn, arriving)
        traffic?.let { drawTraffic(canvas, it, w, if (guiding) 54 * density else horizon + 8 * density, !guiding) }
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
        roadPaint.shader = when {
            // Studio: a pale road fading in from the horizon on the card's own white (or graphite).
            flat && dark -> linear(0f, horizon, 0f, h, 0x002A313C, 0xFF2A313C.toInt())
            flat -> linear(0f, horizon, 0f, h, 0x00C3CCD8, 0xFFCDD5E0.toInt())
            dark -> linear(0f, horizon, 0f, h, 0x001A2230, 0xF01A2230.toInt())
            else -> linear(0f, horizon, 0f, h, 0xFF5A6676.toInt(), 0xFF2C343F.toInt())
        }
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
        drawFlowAhead(canvas, cx, h, horizon, ::half)
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
     * The traffic ahead along both edges of the straight road, each stretch in its colour, from the car (bottom) to
     * 3.5 km (the horizon). Small marks name 1, 2 and 3 km.
     */
    private fun drawFlowAhead(canvas: Canvas, cx: Float, h: Float, horizon: Float, half: (Float) -> Float) {
        val stretches = aheadFlow
        if (stretches.isEmpty()) return
        // Not true perspective, which would squeeze 1 to 3.5 km into a few pixels under the horizon: a gentler scale
        // where 500 m is about a third of the way up, 1 km half and 2 km three quarters.
        fun y(meters: Int) = h - (h - horizon) * sqrt(meters / TrafficAhead.FAR_METERS.toFloat()).coerceAtMost(1f)
        edgePaint.shader = null
        edgePaint.strokeCap = Paint.Cap.BUTT
        for ((from, end, flow) in stretches) {
            if (from >= TrafficAhead.FAR_METERS) break
            val to = end.coerceAtMost(TrafficAhead.FAR_METERS)
            if (to <= from) continue
            val y0 = y(from)
            val y1 = y(to)
            val fade = ((y0 - horizon) / (h - horizon)).coerceIn(0f, 1f)
            edgePaint.color = flow.color
            edgePaint.strokeWidth = (2f + 5f * fade) * density
            for (side in SIDES) canvas.drawLine(cx + side * half(y0), y0, cx + side * half(y1), y1, edgePaint)
        }
        edgePaint.strokeCap = Paint.Cap.ROUND
        textPaint.typeface = Fonts.get(700)
        textPaint.textSize = 9 * density
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = ColorUtils.setAlphaComponent(ink, 0xB3)
        for ((meters, name) in DISTANCE_MARKS) {
            if (stretches.none { meters in it.from..it.to }) continue
            val y = y(meters)
            canvas.drawText(name, cx + half(y) + 6 * density, y + 3 * density, textPaint)
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
     * A road that branches off at an angle (keep to a side, a fork, a slip road): the road going on straight, the
     * branch leaving it up to the right (mirrored for the left), and the arrow from the car into the branch.
     */
    private val shaft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    private fun drawFork(canvas: Canvas, w: Float, horizon: Float, road: Float, tone: Int, left: Boolean) {
        fun x(f: Float) = w * f
        fun y(f: Float) = horizon + road * f
        canvas.save()
        if (left) canvas.scale(-1f, 1f, w / 2f, 0f)
        path.reset()
        path.moveTo(x(.02f), y(1f))
        path.lineTo(x(.40f), y(0f))
        path.lineTo(x(.56f), y(0f))
        path.lineTo(x(.62f), y(.30f))
        path.lineTo(x(.84f), y(0f))
        path.lineTo(x(1f), y(0f))
        path.lineTo(x(1f), y(.26f))
        path.cubicTo(x(.88f), y(.40f), x(.80f), y(.55f), x(.80f), y(.66f))
        path.lineTo(x(.98f), y(1f))
        path.close()
        roadPaint.shader = null
        roadPaint.color = 0xF01C2535.toInt()
        canvas.drawPath(path, roadPaint)
        roadPaint.color = ColorUtils.setAlphaComponent(tone, 0x22)
        canvas.drawPath(path, roadPaint)
        // The curbs: the left one, the point between the two roads, the branch's outer one.
        linePaint.strokeWidth = 2.4f * density
        linePaint.color = tone
        canvas.drawLine(x(.02f), y(1f), x(.40f), y(0f), linePaint)
        path.reset()
        path.moveTo(x(.56f), y(0f))
        path.lineTo(x(.62f), y(.30f))
        path.lineTo(x(.84f), y(0f))
        canvas.drawPath(path, linePaint)
        path.reset()
        path.moveTo(x(.98f), y(1f))
        path.lineTo(x(.80f), y(.66f))
        path.cubicTo(x(.80f), y(.55f), x(.88f), y(.40f), x(1f), y(.26f))
        canvas.drawPath(path, linePaint)
        // The arrow: up from the car, then off at an angle into the branch.
        shaft.strokeWidth = w * .055f
        part.reset()
        part.moveTo(x(.51f), y(.66f))
        part.lineTo(x(.515f), y(.50f))
        part.quadTo(x(.52f), y(.40f), x(.60f), y(.33f))
        part.lineTo(x(.72f), y(.225f))
        path.reset()
        shaft.getFillPath(part, path)
        // The head, square to the branch's direction.
        val dx = x(.72f) - x(.60f)
        val dy = y(.225f) - y(.33f)
        val length = kotlin.math.hypot(dx, dy)
        val ux = dx / length
        val uy = dy / length
        val baseX = x(.72f) - ux * w * .01f
        val baseY = y(.225f) - uy * w * .01f
        val half = w * .065f
        val reach = w * .11f
        part.reset()
        part.moveTo(baseX + ux * reach, baseY + uy * reach)
        part.lineTo(baseX - uy * half, baseY + ux * half)
        part.lineTo(baseX + uy * half, baseY - ux * half)
        part.close()
        path.op(part, Path.Op.UNION)
        drawLitArrow(canvas, path, baseY + uy * reach, y(.66f))
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
            textPaint.typeface = Fonts.get(700)
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
        textPaint.typeface = Fonts.get(700)
        textPaint.textSize = 10 * density * scale
        canvas.drawText(limit.toString(), lx, y - pole - radius + 3.6f * density * scale, textPaint)
    }

    /** The street to take, white on green like a road sign, over the far end of the turn. */
    private fun drawStreetSign(canvas: Canvas, w: Float, horizon: Float, road: Float, street: String, left: Boolean) {
        textPaint.typeface = Fonts.get(700)
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

    /**
     * The traffic ahead in a small pill in its colour: under the guidance pill while guiding, else centred on the road
     * just under the horizon.
     */
    private fun drawTraffic(canvas: Canvas, label: String, w: Float, top: Float, centred: Boolean) {
        val colour = flow?.color ?: Flow.SLOW.color
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Fonts.get(700)
        textPaint.textSize = 11 * density
        val dot = 3.5f * density
        val wide = min(textPaint.measureText(label) + dot * 2 + 22 * density, w - 16 * density)
        val left = if (centred) (w - wide) / 2 else 8 * density
        rect.set(left, top, left + wide, top + 22 * density)
        arrowPaint.shader = null
        arrowPaint.color = 0xE60B1018.toInt()
        canvas.drawRoundRect(rect, 11 * density, 11 * density, arrowPaint)
        linePaint.color = colour
        linePaint.strokeWidth = 1.4f * density
        canvas.drawRoundRect(rect, 11 * density, 11 * density, linePaint)
        arrowPaint.color = colour
        canvas.drawCircle(left + 10 * density, top + 11 * density, dot, arrowPaint)
        textPaint.color = Color.WHITE
        canvas.save()
        canvas.clipRect(rect)
        canvas.drawText(label, left + 10 * density + dot + 6 * density, top + 15 * density, textPaint)
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
                Turn.LEFT -> if (turn.slight) "serrez à gauche" else "à gauche"
                Turn.RIGHT -> if (turn.slight) "serrez à droite" else "à droite"
                Turn.ROUNDABOUT -> turn.exit?.let { "sortie $it" } ?: "rond-point"
                Turn.UTURN -> "demi-tour"
                Turn.ARRIVE -> "arrivée"
                Turn.STRAIGHT -> "tout droit"
            }
        }
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Fonts.get(700)
        textPaint.textSize = 17 * density
        val titleWidth = textPaint.measureText(title)
        textPaint.typeface = Fonts.get(400)
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
        textPaint.typeface = Fonts.get(700)
        textPaint.textSize = 17 * density
        canvas.drawText(title, tx, top + 19 * density, textPaint)
        textPaint.typeface = Fonts.get(400)
        textPaint.textSize = 10 * density
        textPaint.color = 0xE6FFFFFF.toInt()
        canvas.drawText(detail, tx, top + 32 * density, textPaint)
        canvas.restore()
    }

    /**
     * The car from behind, drawn low and wide like a recent saloon: a glass roof flowing into the rear window, broad
     * shoulders, one thin light bar across the whole tail with the driver's emblem under it, a clean boot, and a dark
     * diffuser with two reflectors. The light bar brightens on braking; its end blinks amber on the side of a turn.
     */
    private fun drawCar(canvas: Canvas, cx: Float, bottom: Float, carW: Float, blink: Int = 0) {
        val u = carW
        val v = carW * .56f
        val top = bottom - v
        fun x(f: Float) = cx + u * f
        fun y(f: Float) = top + v * f
        // At night, the headlights' beam on the road ahead.
        if (dark) {
            carPaint.shader = linear(0f, top - v * .9f, 0f, top + v * .3f, 0x00FFF4D6, 0x38FFF4D6)
            path.reset()
            path.moveTo(cx - u * .34f, top + v * .3f)
            path.lineTo(cx - u * .62f, top - v * .9f)
            path.lineTo(cx + u * .62f, top - v * .9f)
            path.lineTo(cx + u * .34f, top + v * .3f)
            path.close()
            canvas.drawPath(path, carPaint)
        }
        // A soft shadow on the road, then the tyres.
        carPaint.shader = radial(cx, bottom, u * .78f, 0xB0000000.toInt(), 0)
        rect.set(x(-.78f), bottom - v * .2f, x(.78f), bottom + v * .2f)
        canvas.drawOval(rect, carPaint)
        carPaint.shader = null
        carPaint.color = 0xFF05070A.toInt()
        rect.set(x(-.49f), y(.76f), x(-.34f), bottom + v * .03f)
        canvas.drawRoundRect(rect, v * .06f, v * .06f, carPaint)
        rect.set(x(.34f), y(.76f), x(.49f), bottom + v * .03f)
        canvas.drawRoundRect(rect, v * .06f, v * .06f, carPaint)
        // Slim door mirrors.
        carPaint.color = ColorUtils.blendARGB(bodyColor, Color.BLACK, .45f)
        rect.set(x(-.555f), y(.34f), x(-.455f), y(.41f))
        canvas.drawRoundRect(rect, v * .03f, v * .03f, carPaint)
        rect.set(x(.455f), y(.34f), x(.555f), y(.41f))
        canvas.drawRoundRect(rect, v * .03f, v * .03f, carPaint)
        // The cabin: one dark glass shape from the roof down to the boot, with a thin body-colour pillar line.
        carPaint.color = Color.WHITE
        val body = linear(0f, top, 0f, bottom, ColorUtils.blendARGB(bodyColor, Color.WHITE, .18f),
            ColorUtils.blendARGB(bodyColor, Color.BLACK, .3f))
        carPaint.shader = body
        path.reset()
        path.moveTo(x(-.43f), y(.44f))
        path.cubicTo(x(-.38f), y(.16f), x(-.3f), y(.01f), x(-.2f), y(0f))
        path.lineTo(x(.2f), y(0f))
        path.cubicTo(x(.3f), y(.01f), x(.38f), y(.16f), x(.43f), y(.44f))
        path.close()
        canvas.drawPath(path, carPaint)
        carPaint.shader = linear(0f, y(.03f), 0f, y(.42f), 0xFF3A4860.toInt(), 0xFF080C14.toInt())
        path.reset()
        path.moveTo(x(-.385f), y(.41f))
        path.cubicTo(x(-.34f), y(.17f), x(-.28f), y(.05f), x(-.19f), y(.04f))
        path.lineTo(x(.19f), y(.04f))
        path.cubicTo(x(.28f), y(.05f), x(.34f), y(.17f), x(.385f), y(.41f))
        path.close()
        canvas.drawPath(path, carPaint)
        // A streak of light across the glass.
        carPaint.shader = null
        carPaint.color = 0x24FFFFFF
        path.reset()
        path.moveTo(x(-.3f), y(.13f))
        path.lineTo(x(-.12f), y(.07f))
        path.lineTo(x(.02f), y(.07f))
        path.lineTo(x(-.2f), y(.2f))
        path.close()
        canvas.drawPath(path, carPaint)
        // The body: broad shoulders, tucked in towards the road.
        carPaint.color = Color.WHITE // full opacity again after the translucent streak
        carPaint.shader = body
        path.reset()
        path.moveTo(x(-.46f), y(.4f))
        path.quadTo(x(-.53f), y(.42f), x(-.53f), y(.54f))
        path.lineTo(x(-.5f), y(.84f))
        path.quadTo(x(-.49f), y(.92f), x(-.41f), y(.92f))
        path.lineTo(x(.41f), y(.92f))
        path.quadTo(x(.49f), y(.92f), x(.5f), y(.84f))
        path.lineTo(x(.53f), y(.54f))
        path.quadTo(x(.53f), y(.42f), x(.46f), y(.4f))
        path.close()
        canvas.drawPath(path, carPaint)
        carPaint.shader = null
        // A highlight along the shoulder line.
        stroke(0x59FFFFFF, 1f)
        canvas.drawLine(x(-.44f), y(.425f), x(.44f), y(.425f), outline)
        // The light bar: a glow behind it, then one thin line across the tail, thicker at its ends.
        val braked = braking
        carPaint.color = if (braked) 0x80FF1E1E.toInt() else 0x2EFF1E1E
        rect.set(x(-.56f), y(.475f), x(.56f), y(.6f))
        canvas.drawRoundRect(rect, v * .1f, v * .1f, carPaint)
        val red = if (braked) 0xFFFF3030.toInt() else 0xFFDA2A32.toInt()
        carPaint.color = red
        rect.set(x(-.5f), y(.515f), x(.5f), y(.555f))
        canvas.drawRoundRect(rect, v * .02f, v * .02f, carPaint)
        for (side in SIDES) {
            carPaint.color = if (blink != 0 && side == blink.toFloat()) 0xFFFFB020.toInt() else red
            path.reset()
            path.moveTo(x(side * .525f), y(.47f))
            path.lineTo(x(side * .3f), y(.515f))
            path.lineTo(x(side * .3f), y(.555f))
            path.lineTo(x(side * .52f), y(.6f))
            path.close()
            canvas.drawPath(path, carPaint)
        }
        // A bright core along the bar.
        stroke(if (braked) 0xFFFFE0E0.toInt() else 0xB3FFB0B0.toInt(), .8f)
        canvas.drawLine(x(-.47f), y(.535f), x(.47f), y(.535f), outline)
        // The plate, set low, then the diffuser with its reflectors.
        carPaint.color = 0xFFEDF0F4.toInt()
        rect.set(x(-.12f), y(.68f), x(.12f), y(.775f))
        canvas.drawRoundRect(rect, v * .02f, v * .02f, carPaint)
        carPaint.color = 0xFF12161C.toInt()
        path.reset()
        path.moveTo(x(-.47f), y(.82f))
        path.lineTo(x(.47f), y(.82f))
        path.lineTo(x(.43f), y(.95f))
        path.lineTo(x(-.43f), y(.95f))
        path.close()
        canvas.drawPath(path, carPaint)
        carPaint.color = 0xFFB0222A.toInt()
        rect.set(x(-.41f), y(.845f), x(-.31f), y(.87f))
        canvas.drawRoundRect(rect, v * .01f, v * .01f, carPaint)
        rect.set(x(.31f), y(.845f), x(.41f), y(.87f))
        canvas.drawRoundRect(rect, v * .01f, v * .01f, carPaint)
        // The driver's emblem on the boot, under the light bar.
        badge?.let { original ->
            val logo = fitted(original, v * .11f, u * .14f)
            val wide = logo.width.toFloat()
            val tall = logo.height.toFloat()
            rect.set(cx - wide / 2, y(.575f), cx + wide / 2, y(.575f) + tall)
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
        val DISTANCE_MARKS = listOf(1000 to "1 km", 2000 to "2 km", 3000 to "3 km")
    }
}
