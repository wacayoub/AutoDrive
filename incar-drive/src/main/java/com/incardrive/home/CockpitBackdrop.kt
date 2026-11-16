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
    private val glowAlpha: Int, private val warm: Int,
) : Drawable() {
    private val fill = Paint()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f }

    override fun onBoundsChange(bounds: Rect) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        fill.shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(), intArrayOf(top, mid, bottom),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        sun.shader = RadialGradient(bounds.left + w * .12f, bounds.bottom - h * .05f, maxOf(w, h) * .55f,
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
