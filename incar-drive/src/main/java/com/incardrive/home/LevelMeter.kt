package com.incardrive.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Radio Maroc & Coran's level meter under the station logo: eleven columns of small rounded segments, amber for the
 * bass on the outside, green for the middle, blue-grey for the treble in the centre, each with a falling peak. The
 * radio measures its own sound; another app cannot hear it without the microphone permission, so here the columns
 * move to a lively, music-like rhythm while the radio plays, and rest flat when it is paused.
 */
internal class LevelMeter(context: Context, night: Boolean) : View(context) {
    private val density = resources.displayMetrics.density
    private val restColor = if (night) 0xFF5FD8B6.toInt() else 0xFF7A7268.toInt()
    private val bandColors = if (night) intArrayOf(0xFFFFBB60.toInt(), 0xFF64E4AC.toInt(), 0xFF9FB8D9.toInt())
        else intArrayOf(0xFFB35F16.toInt(), 0xFF0F8063.toInt(), 0xFF8A6A3F.toInt())
    private val bands = intArrayOf(2, 2, 1, 1, 0, 0, 0, 1, 1, 2, 2)
    private val shape = floatArrayOf(.66f, .88f, .88f, 1f, .87f, 1f, .87f, 1f, .88f, .88f, .66f)
    private val heights = FloatArray(bands.size)
    private val peaks = FloatArray(bands.size)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var started = System.nanoTime()

    var playing = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                heights.fill(0f)
                peaks.fill(0f)
            }
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** A beat-like level per band, from a few unrelated waves so it never looks like a loop. */
    private fun level(band: Int, seconds: Double): Float {
        val base = when (band) {
            0 -> .55 + .35 * sin(seconds * 7.1) * sin(seconds * 1.3)
            1 -> .45 + .30 * sin(seconds * 9.7 + 1.1) * sin(seconds * 2.1)
            else -> .35 + .25 * sin(seconds * 13.3 + 2.3) * sin(seconds * 3.7)
        }
        return base.toFloat().coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        val available = width - 8 * density
        if (available < 22 * density || height < 10 * density) return
        val spacing = min(6 * density, available / heights.size)
        val start = width / 2f - heights.lastIndex * spacing / 2f
        val seconds = (System.nanoTime() - started) / 1e9
        val graphHeight = min(height * .9f, 24 * density)
        val segments = (graphHeight / (2.7f * density)).toInt().coerceIn(3, 8)
        val pitch = graphHeight / segments
        val halfWidth = min(spacing * .31f, 2f * density)
        val baseline = (height + graphHeight) / 2f
        val visible = if (playing) segments else 1
        for (index in heights.indices) {
            val band = bands[index]
            val target = if (playing) level(band, seconds + index * .37) * shape[index] else 0f
            val value = heights[index] + (target - heights[index]) * if (target > heights[index]) .56f else .22f
            heights[index] = value
            val peak = max(value, peaks[index] - .045f)
            peaks[index] = peak
            val peakSegment = (peak * segments).toInt().coerceAtMost(segments - 1)
            val x = start + index * spacing
            for (segment in 0 until visible) {
                val fill = (value * segments - segment).coerceIn(0f, 1f)
                val peakMarker = peak > 0f && peakSegment == segment
                val color = if (fill > 0f || peakMarker) bandColors[band] else restColor
                val alpha = when {
                    fill > 0f -> 72 + (fill * 183).toInt()
                    peakMarker -> 150
                    else -> if (playing) 32 else 84
                }
                paint.color = ColorUtils.setAlphaComponent(color, alpha)
                val bottom = baseline - segment * pitch
                val top = bottom - pitch * .59f
                val radius = (bottom - top) / 2f
                canvas.drawRoundRect(x - halfWidth, top, x + halfWidth, bottom, radius, radius, paint)
            }
        }
        if (playing && isShown && windowVisibility == VISIBLE) postInvalidateDelayed(50)
    }
}
