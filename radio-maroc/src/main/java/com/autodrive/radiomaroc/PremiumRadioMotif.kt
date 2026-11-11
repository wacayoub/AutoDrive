package com.autodrive.radiomaroc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.min
import kotlin.math.sqrt

internal class PremiumRadioMotif(
    context: Context,
    private val logo: View,
    private val belowLogo: Boolean = false,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val motifColor = context.getColor(R.color.radio_motif)
    private val bassColor = context.getColor(R.color.radio_motif_bass)
    private val midColor = context.getColor(R.color.radio_motif_mid)
    private val trebleColor = context.getColor(R.color.radio_motif_treble)
    private val bandColors = intArrayOf(bassColor, bassColor, midColor, midColor, midColor, trebleColor, trebleColor)
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.5f * density
    }
    private val heights = FloatArray(7)
    private val shape = floatArrayOf(.74f, .91f, 1f, .84f, .96f, .72f, .53f)
    var playing = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) heights.fill(0f)
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val available = if (belowLogo) width - 8 * density else (width - logo.width) / 2f - 11 * density
        if (available < 22 * density || height < 14 * density) return
        val spacing = min((if (belowLogo) 6 else 5) * density, available / heights.size)
        val start = if (belowLogo) centerX - heights.lastIndex * spacing / 2f else centerX + logo.width / 2f + 7 * density
        val signal = RadioAudioMeter.latest?.takeIf { playing && System.nanoTime() - it.measuredAt < 400_000_000L }
        val bass = sqrt(((signal?.bass ?: 0f) * 4f).coerceIn(0f, 1f))
        val mid = sqrt(((signal?.mid ?: 0f) * 5f).coerceIn(0f, 1f))
        val treble = sqrt(((signal?.treble ?: 0f) * 9f).coerceIn(0f, 1f))
        val scale = min(height * if (belowLogo) .34f else .27f, 23 * density)
        for (index in heights.indices) {
            val target = when (index) {
                0, 1 -> bass
                2, 3, 4 -> mid
                else -> treble
            } * shape[index]
            val level = heights[index] + (target - heights[index]) * if (target > heights[index]) .65f else .36f
            heights[index] = level
            val x = start + index * spacing
            val halfHeight = density + level * scale
            val color = if (playing) bandColors[index] else motifColor
            levelPaint.color = ColorUtils.setAlphaComponent(color, 108 + (level * 147).toInt())
            canvas.drawLine(x, centerY - halfHeight, x, centerY + halfHeight, levelPaint)
            if (!belowLogo) canvas.drawLine(width - x, centerY - halfHeight, width - x, centerY + halfHeight, levelPaint)
        }
        if (playing && isShown && windowVisibility == VISIBLE) postInvalidateDelayed(45)
    }
}
