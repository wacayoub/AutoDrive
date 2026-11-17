package com.incardrive.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import kotlin.math.abs

/**
 * Which way the navigation app's turn arrow points, read from its shape. On some head units Google Maps' guidance
 * notification carries no instruction in words, only the distance and this arrow as an image: the arrow is then the
 * only thing that says « left » or « right ».
 *
 * The arrow is drawn small, the part of the picture that is the arrow is found (the opaque part, or on a filled
 * icon the part that differs from the background), and its shape is compared top against bottom: an arrow starts at
 * the bottom (its stem) and ends at the top (its head). A head well to the left of the stem is a left turn, to the
 * right a right turn; a shape that comes back down beside its stem is a U-turn.
 */
internal object ArrowShape {
    private const val SIZE = 48

    fun turn(icon: Drawable?): Turn? {
        icon ?: return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val copy = icon.constantState?.newDrawable()?.mutate() ?: icon
            copy.setBounds(0, 0, SIZE, SIZE)
            copy.draw(Canvas(bitmap))
            val pixels = IntArray(SIZE * SIZE)
            bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            bitmap.recycle()
            turn(mask(pixels), SIZE, SIZE)
        }.getOrNull()
    }

    /** The arrow's pixels: the opaque ones, or on an icon filled edge to edge those unlike its background. */
    private fun mask(pixels: IntArray): BooleanArray {
        val opaque = pixels.count { Color.alpha(it) > 128 }
        if (opaque < pixels.size * .7) return BooleanArray(pixels.size) { Color.alpha(pixels[it]) > 128 }
        val background = pixels[SIZE + 1]
        return BooleanArray(pixels.size) {
            val pixel = pixels[it]
            abs(Color.red(pixel) - Color.red(background)) + abs(Color.green(pixel) - Color.green(background)) +
                abs(Color.blue(pixel) - Color.blue(background)) > 150
        }
    }

    /** The direction of an arrow given as a [width] × [height] grid, true where the arrow is; null when unclear. */
    fun turn(arrow: BooleanArray, width: Int, height: Int): Turn? {
        var left = width
        var right = -1
        var top = height
        var bottom = -1
        for (y in 0 until height) for (x in 0 until width) if (arrow[y * width + x]) {
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        val w = right - left + 1
        val h = bottom - top + 1
        if (w < 6 || h < 6) return null
        fun centre(fromRow: Int, toRow: Int): Float? {
            var sum = 0L
            var count = 0
            for (y in fromRow..toRow) for (x in left..right) if (arrow[y * width + x]) { sum += x - left; count++ }
            return if (count == 0) null else sum.toFloat() / count / w
        }
        // A U-turn: at the bottom, two separate strokes side by side (the stem, and the head coming back down).
        val low = top + (h * .72f).toInt()
        val columns = BooleanArray(w) { x -> (low..bottom).any { y -> arrow[y * width + left + x] } }
        val first = columns.indexOfFirst { it }
        val last = columns.indexOfLast { it }
        if (first >= 0) {
            var gap = 0
            var widest = 0
            for (x in first..last) if (columns[x]) gap = 0 else { gap++; if (gap > widest) widest = gap }
            if (widest >= w * .2f) return Turn.UTURN
        }
        val stem = centre(top + (h * .7f).toInt(), bottom) ?: return null
        val head = centre(top, top + (h * .4f).toInt()) ?: return null
        val lean = head - stem
        return when {
            lean < -.14f -> Turn.LEFT
            lean > .14f -> Turn.RIGHT
            else -> Turn.STRAIGHT
        }
    }
}
