package com.autodrive.radiomaroc

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.min

internal class PremiumRadioMotif(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val grillePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.7f * density
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val waveColors = intArrayOf(0x306CA8C3, 0x526CA8C3, 0xA6679AB5.toInt())
    private val waves = Array(waveColors.size) { RectF() }
    private var holes = FloatArray(0)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        if (width == 0 || height == 0) {
            holes = FloatArray(0)
            return
        }
        val centerX = width / 2f
        val centerY = height / 2f
        val grilleWidth = min(width.toFloat(), 236 * density)
        val grilleLeft = centerX - grilleWidth / 2f
        val grilleRight = centerX + grilleWidth / 2f
        grillePaint.shader = LinearGradient(
            grilleLeft, centerY, grilleRight, centerY,
            intArrayOf(0x00456071, 0xA6456071.toInt(), 0xA6456071.toInt(), 0x00456071),
            floatArrayOf(0f, .18f, .82f, 1f), Shader.TileMode.CLAMP,
        )
        val spacing = 6 * density
        holes = buildList {
            var y = height * .10f
            while (y < height * .90f) {
                var x = grilleLeft + spacing / 2f
                while (x < grilleRight) {
                    add(x)
                    add(y)
                    x += spacing
                }
                y += spacing
            }
        }.toFloatArray()

        val halfWidth = min(width * .40f, 106 * density)
        val halfHeight = height * .31f
        val gap = min(width * .034f, 8 * density)
        waves.forEachIndexed { index, bounds ->
            val inset = gap * (waves.lastIndex - index)
            bounds.set(centerX - halfWidth - inset, centerY - halfHeight - inset,
                centerX + halfWidth + inset, centerY + halfHeight + inset)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (holes.isEmpty()) return
        canvas.drawPoints(holes, grillePaint)
        canvas.save()
        canvas.rotate(-12f, width / 2f, height / 2f)
        waves.forEachIndexed { index, bounds ->
            wavePaint.color = waveColors[index]
            canvas.drawOval(bounds, wavePaint)
        }
        canvas.restore()
    }
}
