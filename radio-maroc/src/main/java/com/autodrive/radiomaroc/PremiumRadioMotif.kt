package com.autodrive.radiomaroc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal class PremiumRadioMotif(
    context: Context,
    private val logo: View,
    private val belowLogo: Boolean = false,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val motifColor = context.getColor(R.color.radio_motif)
    private val bandColors = intArrayOf(
        context.getColor(R.color.radio_motif_bass),
        context.getColor(R.color.radio_motif_mid),
        context.getColor(R.color.radio_motif_treble),
    )
    private val bands = if (belowLogo) intArrayOf(2, 2, 1, 1, 0, 0, 0, 1, 1, 2, 2)
        else intArrayOf(0, 0, 1, 1, 1, 2, 2)
    private val shape = if (belowLogo) floatArrayOf(.66f, .88f, .88f, 1f, .87f, 1f, .87f, 1f, .88f, .88f, .66f)
        else floatArrayOf(.74f, .91f, 1f, .84f, .96f, .72f, .53f)
    private val heights = FloatArray(bands.size)
    private val peaks = FloatArray(bands.size)
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val available = if (belowLogo) width - 8 * density else (width - logo.width) / 2f - 11 * density
        if (available < 22 * density || height < 14 * density) return
        val spacing = min((if (belowLogo) 6 else 5) * density, available / heights.size)
        val start = if (belowLogo) centerX - heights.lastIndex * spacing / 2f else centerX + logo.width / 2f + 7 * density
        val signal = RadioAudioMeter.latest?.takeIf { playing && System.nanoTime() - it.measuredAt < 400_000_000L }
        val bass = sqrt(((signal?.bass ?: 0f) * 4f).coerceIn(0f, 1f))
        val mid = sqrt(((signal?.mid ?: 0f) * 5f).coerceIn(0f, 1f))
        val treble = sqrt(((signal?.treble ?: 0f) * 9f).coerceIn(0f, 1f))
        val graphHeight = min(height * if (belowLogo) .88f else .46f, 24 * density)
        val segments = (graphHeight / (2.7f * density)).toInt().coerceIn(3, 8)
        val pitch = graphHeight / segments
        val halfWidth = min(spacing * .31f, 2f * density)
        val baseline = (height + graphHeight) / 2f
        val visibleSegments = if (playing) segments else 1
        for (index in heights.indices) {
            val band = bands[index]
            val target = when (band) {
                0 -> bass
                1 -> mid
                else -> treble
            } * shape[index]
            val level = heights[index] + (target - heights[index]) * if (target > heights[index]) .56f else .22f
            heights[index] = level
            val peak = max(level, peaks[index] - .045f)
            peaks[index] = peak
            val peakSegment = (peak * segments).toInt().coerceAtMost(segments - 1)
            val x = start + index * spacing
            for (segment in 0 until visibleSegments) {
                val fill = (level * segments - segment).coerceIn(0f, 1f)
                val peakMarker = peak > 0f && peakSegment == segment
                val color = if (fill > 0f || peakMarker) bandColors[band] else motifColor
                val alpha = when {
                    fill > 0f -> 72 + (fill * 183).toInt()
                    peakMarker -> 150
                    else -> if (playing) 32 else 84
                }
                levelPaint.color = ColorUtils.setAlphaComponent(color, alpha)
                val bottom = baseline - segment * pitch
                val top = bottom - pitch * .59f
                val radius = (bottom - top) / 2f
                canvas.drawRoundRect(x - halfWidth, top, x + halfWidth, bottom, radius, radius, levelPaint)
                if (!belowLogo) canvas.drawRoundRect(
                    width - x - halfWidth, top, width - x + halfWidth, bottom, radius, radius, levelPaint,
                )
            }
        }
        if (playing && isShown && windowVisibility == VISIBLE) postInvalidateDelayed(33)
    }
}
