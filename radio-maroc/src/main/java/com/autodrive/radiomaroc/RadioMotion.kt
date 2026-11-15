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
