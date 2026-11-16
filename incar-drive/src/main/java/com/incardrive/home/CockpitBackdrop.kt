package com.incardrive.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Incar Drive's own backdrop, unlike Radio Maroc & Coran's drifting colours: a dark or silver gradient, a glow of the
 * accent colour in the upper right like a dashboard light, and a road grid running to the horizon at the bottom.
 * It is static, so it costs nothing while the screen is shown.
 */
internal class CockpitBackdrop(
    private val top: Int, private val mid: Int, private val bottom: Int, private val accent: Int, private val grid: Int,
    private val glowAlpha: Int, private val warm: Int, private val sunX: Float = .12f, private val sunY: Float = .95f, private val sunSize: Float = .55f,
    private val stars: Float = 0f,
) : Drawable() {
    private val fill = Paint()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG)
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    // The same scattered stars every time, denser and brighter towards the top of the sky.
    private val starField = java.util.Random(7).let { random ->
        List(90) { floatArrayOf(random.nextFloat(), random.nextFloat().let { it * it } * .62f, .5f + random.nextFloat(), random.nextFloat()) }
    }
    private val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f }

    override fun onBoundsChange(bounds: Rect) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        fill.shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(), intArrayOf(top, mid, bottom),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        sun.shader = RadialGradient(bounds.left + w * sunX, bounds.top + h * sunY, maxOf(w, h) * sunSize,
            intArrayOf(warm, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        glow.shader = RadialGradient(bounds.left + w * .82f, bounds.top + h * .05f, maxOf(w, h) * .6f,
            intArrayOf(Color.argb(glowAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        // The grid fades in towards the viewer.
        lines.shader = LinearGradient(0f, bounds.top + h * .55f, 0f, bounds.bottom.toFloat(),
            Color.TRANSPARENT, grid, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawRect(b, fill)
        canvas.drawRect(b, glow)
        if (Color.alpha(warm) > 0) canvas.drawRect(b, sun)
        if (stars > 0f) {
            val density = b.height() / 300f
            starField.forEach { (x, y, size, twinkle) ->
                star.alpha = ((90 + twinkle * 165) * stars * (1 - y)).toInt().coerceIn(0, 255)
                canvas.drawCircle(b.left + x * b.width(), b.top + y * b.height(), size * density.coerceAtLeast(1f), star)
            }
        }
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val horizon = b.top + h * .55f
        val cx = b.left + w / 2
        // Lines from the vanishing point, and cross lines closer together towards the horizon.
        for (i in -12..12) canvas.drawLine(cx, horizon, cx + i * w * .16f, b.bottom.toFloat(), lines)
        var y = horizon
        var step = h * .012f
        while (y < b.bottom) {
            canvas.drawLine(b.left.toFloat(), y, b.right.toFloat(), y, lines)
            y += step
            step *= 1.35f
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
