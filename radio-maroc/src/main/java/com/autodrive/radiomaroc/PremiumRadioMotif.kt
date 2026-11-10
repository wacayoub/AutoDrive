package com.autodrive.radiomaroc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.min

internal class PremiumRadioMotif(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val motifColor = context.getColor(R.color.radio_motif)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(motifColor, 73)
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2 * density
    }
    private val levels = floatArrayOf(.35f, .55f, .85f, .61f, 1f, .78f, .48f)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * .47f
        for (ring in 0..2) {
            wavePaint.color = ColorUtils.setAlphaComponent(motifColor, 27 + ring * 15)
            canvas.drawCircle(centerX, centerY, radius * (1f - ring * .12f), wavePaint)
        }
        val spacing = 4 * density
        val start = centerX + radius + 9 * density
        levels.forEachIndexed { index, level ->
            val x = start + index * spacing
            val halfHeight = level * min(height * .19f, 16 * density)
            canvas.drawLine(x, centerY - halfHeight, x, centerY + halfHeight, levelPaint)
            canvas.drawLine(width - x, centerY - halfHeight, width - x, centerY + halfHeight, levelPaint)
        }
    }
}
