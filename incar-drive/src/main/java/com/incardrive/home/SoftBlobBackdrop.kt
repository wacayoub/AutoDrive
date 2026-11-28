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
import androidx.core.graphics.ColorUtils
import kotlin.math.max

/**
 * The « Cabine » backdrop, in the spirit of recent car cockpits' wallpapers: a soft wash with large blurred patches of
 * blue, lavender and teal, pale by day and deep by night. Drawn once per size; nothing moves, so it costs nothing.
 */
internal class SoftBlobBackdrop(private val night: Boolean) : Drawable() {
    private val fill = Paint()
    private val blob = Paint(Paint.ANTI_ALIAS_FLAG)

    // Where each patch sits (fractions of the screen), how large (fraction of the longer side), and its colour.
    private val blobs = if (night) listOf(
        Blob(.12f, .18f, .62f, 0xFF2B4FB8.toInt(), 0xB0),
        Blob(.88f, .10f, .55f, 0xFF6A3FB5.toInt(), 0x99),
        Blob(.55f, .95f, .70f, 0xFF136C86.toInt(), 0x8C),
        Blob(.95f, .85f, .40f, 0xFF3C2E8F.toInt(), 0x80),
    ) else listOf(
        Blob(.10f, .15f, .60f, 0xFF8FB4FF.toInt(), 0xCC),
        Blob(.90f, .08f, .55f, 0xFFC9B2FF.toInt(), 0xB3),
        Blob(.55f, .98f, .70f, 0xFF9EE3E6.toInt(), 0xA6),
        Blob(.97f, .80f, .42f, 0xFFB8C6FF.toInt(), 0x99),
    )

    private data class Blob(val x: Float, val y: Float, val size: Float, val color: Int, val alpha: Int)

    override fun onBoundsChange(bounds: Rect) {
        fill.shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
            if (night) intArrayOf(0xFF0A0D18.toInt(), 0xFF111527.toInt()) else intArrayOf(0xFFEEF2FB.toInt(), 0xFFE6ECF7.toInt()),
            null, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawRect(b, fill)
        val side = max(b.width(), b.height()).toFloat()
        for (it in blobs) {
            val x = b.left + b.width() * it.x
            val y = b.top + b.height() * it.y
            val radius = side * it.size
            blob.shader = RadialGradient(x, y, radius,
                intArrayOf(ColorUtils.setAlphaComponent(it.color, it.alpha), ColorUtils.setAlphaComponent(it.color, it.alpha / 3),
                    Color.TRANSPARENT), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, radius, blob)
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
