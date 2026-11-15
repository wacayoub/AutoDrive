package com.autodrive.radiomaroc

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/** Short, GPU-friendly motion for the radio screen. Everything is skipped when Android animations are turned off. */
internal object RadioMotion {
    val enabled: Boolean get() = ValueAnimator.areAnimatorsEnabled()

    /** Pressed tiles and buttons sink a little, then spring back. */
    fun pressScale(view: View, scale: Float = .95f) {
        if (!enabled) return
        fun to(value: Float, duration: Long) = AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, value), ObjectAnimator.ofFloat(view, View.SCALE_Y, value))
            this.duration = duration
            interpolator = DecelerateInterpolator()
        }
        view.stateListAnimator = StateListAnimator().apply {
            addState(intArrayOf(android.R.attr.state_pressed), to(scale, 90))
            addState(intArrayOf(), to(1f, 160))
        }
    }

    /**
     * A new station arrives: the logo zooms in with a slight bounce and the texts slide in from the side the
     * listener moved towards ([direction] 1 = next, -1 = previous, 0 = picked directly, slides up).
     */
    fun stationIn(artwork: View, texts: List<View>, direction: Int, distance: Float) {
        if (!enabled) return
        artwork.animate().cancel()
        artwork.alpha = 0f
        artwork.scaleX = .78f
        artwork.scaleY = .78f
        artwork.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
        texts.forEachIndexed { index, text ->
            text.animate().cancel()
            text.alpha = 0f
            text.translationX = direction * distance
            text.translationY = if (direction == 0) distance / 2 else 0f
            text.animate().alpha(1f).translationX(0f).translationY(0f).setStartDelay(50L + index * 45L)
                .setDuration(320).setInterpolator(DecelerateInterpolator(1.8f)).start()
        }
    }

    /** A radial light for a tile, painted later in its logo's colour with [paintLight]. */
    fun tileLight(corner: Float, centerY: Float = .38f) = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        setGradientCenter(.5f, centerY)
        cornerRadius = corner
        colors = intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)
    }

    /** A wash of colour from the logo side of a list row, fading out before the text ends. */
    fun rowLight(corner: Float) = GradientDrawable().apply {
        orientation = GradientDrawable.Orientation.LEFT_RIGHT
        cornerRadius = corner
        colors = intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)
    }

    fun paintRow(light: GradientDrawable, color: Int?, alpha: Int, rightToLeft: Boolean) {
        light.orientation = if (rightToLeft) GradientDrawable.Orientation.RIGHT_LEFT else GradientDrawable.Orientation.LEFT_RIGHT
        light.colors = if (color == null || color == Color.TRANSPARENT) intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)
            else intArrayOf(
                Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(alpha / 4, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT,
            )
    }

    fun paintLight(light: GradientDrawable, color: Int?, alpha: Int) {
        light.colors = if (color == null || color == Color.TRANSPARENT) intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)
            else intArrayOf(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT)
    }

    /** A white logo on a transparent background, which would vanish on the light Jour panels. */
    fun isLightOnClear(bitmap: Bitmap): Boolean = runCatching {
        val source = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val sample = Bitmap.createScaledBitmap(source, 24, 24, true)
        var clear = 0
        var light = 0
        var opaque = 0
        for (x in 0 until sample.width) for (y in 0 until sample.height) {
            val pixel = sample.getPixel(x, y)
            if (Color.alpha(pixel) < 40) { clear++; continue }
            opaque++
            if (Color.red(pixel) > 215 && Color.green(pixel) > 215 && Color.blue(pixel) > 215) light++
        }
        if (sample !== source) sample.recycle()
        if (source !== bitmap) source.recycle()
        clear > 24 * 24 / 4 && opaque > 0 && light > opaque * .7f
    }.getOrDefault(false)

    /** The most vivid colour of a logo, brightened enough to glow on the dark player; null for grey logos. */
    fun logoColor(bitmap: Bitmap): Int? = runCatching {
        val source = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val sample = Bitmap.createScaledBitmap(source, 24, 24, true)
        val hsv = FloatArray(3)
        var red = 0f
        var green = 0f
        var blue = 0f
        var weight = 0f
        for (x in 0 until sample.width) for (y in 0 until sample.height) {
            val pixel = sample.getPixel(x, y)
            if (Color.alpha(pixel) < 160) continue
            Color.colorToHSV(pixel, hsv)
            if (hsv[1] < .28f || hsv[2] < .22f) continue
            val w = hsv[1] * hsv[2]
            red += Color.red(pixel) * w
            green += Color.green(pixel) * w
            blue += Color.blue(pixel) * w
            weight += w
        }
        if (sample !== source) sample.recycle()
        if (source !== bitmap) source.recycle()
        if (weight < 6f) return@runCatching null
        Color.colorToHSV(Color.rgb((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt()), hsv)
        hsv[1] = hsv[1].coerceAtLeast(.55f)
        hsv[2] = hsv[2].coerceAtLeast(.85f)
        Color.HSVToColor(hsv)
    }.getOrNull()
}

/**
 * A soft light in the station's colour behind the player, fading between stations. It is a second layer over the
 * panel's own background, clipped to the same corners.
 */
internal class PlayerGlow(
    private val panel: View, base: Drawable, private val fallback: Int, private val strength: Int,
    private val centerX: Float, private val centerY: Float, corner: Float,
) {
    private val light = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        setGradientCenter(centerX, centerY)
        cornerRadius = corner
    }
    private var color = fallback
    private var fade: ValueAnimator? = null

    init {
        paint(color)
        panel.background = LayerDrawable(arrayOf(base, light))
        panel.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            light.gradientRadius = maxOf(right - left, bottom - top) * .62f
        }
    }

    /** Arabic station names lay the player out right to left, so the light follows the logo to the other side. */
    fun mirror(rightToLeft: Boolean) {
        light.setGradientCenter(if (rightToLeft) 1f - centerX else centerX, centerY)
    }

    fun show(target: Int?) {
        val next = target ?: fallback
        if (next == color && fade == null) return
        fade?.cancel()
        if (!RadioMotion.enabled) {
            color = next
            paint(next)
            return
        }
        fade = ValueAnimator.ofObject(ArgbEvaluator(), color, next).apply {
            duration = 520
            addUpdateListener { paint(it.animatedValue as Int) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (fade === animation) fade = null
                }
            })
            start()
        }
        color = next
    }

    private fun paint(value: Int) {
        light.colors = intArrayOf(Color.argb(strength, Color.red(value), Color.green(value), Color.blue(value)), Color.TRANSPARENT)
    }
}

/**
 * The Jour backdrop: the ivory gradient with three large, soft patches of colour drifting slowly behind the
 * translucent panels. The patches only move their shader matrices, at about 24 frames a second, and stop whenever
 * the screen is hidden or Android animations are off.
 */
internal class AmbientBackdrop(private val base: Drawable, private val glows: IntArray) : Drawable() {
    private class Glow(val paint: android.graphics.Paint, val matrix: android.graphics.Matrix)

    private val patches = glows.map {
        Glow(android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG), android.graphics.Matrix())
    }
    private var phase = 0f
    private var lastFrame = 0L
    private val drift = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 18_000
        repeatCount = ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastFrame < 40) return@addUpdateListener
            lastFrame = now
            phase = it.animatedValue as Float
            invalidateSelf()
        }
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        base.bounds = bounds
        val radius = maxOf(bounds.width(), bounds.height()) * .55f
        patches.forEachIndexed { index, glow ->
            val color = glows[index]
            glow.paint.shader = android.graphics.RadialGradient(0f, 0f, radius,
                intArrayOf(color, Color.argb(Color.alpha(color) / 3, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.TRANSPARENT),
                floatArrayOf(0f, .45f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
    }

    override fun draw(canvas: android.graphics.Canvas) {
        base.draw(canvas)
        val box = bounds
        if (box.isEmpty) return
        val w = box.width().toFloat()
        val h = box.height().toFloat()
        patches.forEachIndexed { index, glow ->
            // Each patch circles its own home on a slow Lissajous path, out of step with the others.
            val angle = phase + index * 2.1f
            val homeX = floatArrayOf(.12f, .88f, .55f)[index % 3]
            val homeY = floatArrayOf(.18f, .30f, .95f)[index % 3]
            val x = box.left + w * (homeX + .24f * Math.cos(angle.toDouble()).toFloat())
            val y = box.top + h * (homeY + .28f * Math.sin((angle * 2 + index).toDouble()).toFloat())
            glow.matrix.setTranslate(x, y)
            glow.paint.shader?.setLocalMatrix(glow.matrix)
            canvas.drawRect(box, glow.paint)
        }
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible && RadioMotion.enabled) {
            if (!drift.isStarted) drift.start()
        } else drift.cancel()
        return changed
    }

    override fun setAlpha(alpha: Int) = base.setAlpha(alpha)
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = base.setColorFilter(colorFilter)
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
}

/** Three bars that bounce while a station plays, used as the « now playing » mark of the channel list. */
internal class EqualizerBars(context: android.content.Context, color: Int) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val bar = android.graphics.RectF()
    private var phase = 0f
    private val motion = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 1100
        repeatCount = ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    var playing = false
        set(value) {
            if (field == value) return
            field = value
            update()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun update() {
        if (playing && isAttachedToWindow && visibility == VISIBLE && RadioMotion.enabled) {
            if (!motion.isStarted) motion.start()
        } else {
            motion.cancel()
            phase = 0f
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        update()
    }

    override fun onDetachedFromWindow() {
        motion.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        update()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val gap = width / 7f
        val barWidth = (width - gap * 2) / 3f
        for (index in 0 until 3) {
            // Each bar follows the same wave a third of a turn apart; at rest they settle at staggered heights.
            val level = if (motion.isRunning) .35f + .65f * ((Math.sin((phase + index * 2.1f).toDouble()) + 1) / 2).toFloat()
                else floatArrayOf(.55f, 1f, .7f)[index]
            val left = index * (barWidth + gap)
            bar.set(left, height * (1 - level), left + barWidth, height.toFloat())
            canvas.drawRoundRect(bar, barWidth / 2, barWidth / 2, paint)
        }
    }
}
