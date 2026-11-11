package com.autodrive.radiomaroc

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextUtils
import android.view.Gravity
import android.widget.TextView
import kotlin.math.ceil

internal class HeadlineTicker(context: Context, size: Int, textColor: Int) : TextView(context) {
    private val density = resources.displayMetrics.density
    private var drawing: StaticLayout? = null
    private var startedAt = 0L
    private var travel = 0
    private var scrollMillis = 0L

    init {
        textSize = size.toFloat()
        setTextColor(textColor)
        textDirection = TEXT_DIRECTION_FIRST_STRONG
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        includeFontPadding = false
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        val padding = (6 * density).toInt()
        setPadding(padding, 0, padding, 0)
    }

    fun showHeadline(headline: String): Long {
        text = headline
        prepareDrawing()
        invalidate()
        return when {
            !ValueAnimator.areAnimatorsEnabled() -> 10_000L
            drawing != null -> (START_PAUSE + scrollMillis + 1_000L).coerceAtLeast(7_000L)
            width == 0 -> (Layout.getDesiredWidth(headline, paint) / density * 25).toLong().coerceIn(7_000L, 48_000L)
            else -> 7_000L
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        prepareDrawing()
    }

    private fun prepareDrawing() {
        val viewport = width - paddingLeft - paddingRight
        val fullWidth = ceil(Layout.getDesiredWidth(text, paint)).toInt() + 2
        travel = (fullWidth - viewport).coerceAtLeast(0)
        drawing = if (viewport > 0 && travel > 0 && ValueAnimator.areAnimatorsEnabled()) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, fullWidth)
                .setIncludePad(false)
                .setMaxLines(1)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .build()
        } else null
        startedAt = SystemClock.uptimeMillis()
        scrollMillis = (travel / density * 25).toLong().coerceIn(900L, 45_000L)
    }

    override fun onDraw(canvas: Canvas) {
        val headline = drawing
        if (headline == null || !ValueAnimator.areAnimatorsEnabled()) {
            super.onDraw(canvas)
            return
        }
        val elapsed = SystemClock.uptimeMillis() - startedAt - START_PAUSE
        val fraction = (elapsed.toFloat() / scrollMillis).coerceIn(0f, 1f)
        val offset = if (headline.getParagraphDirection(0) < 0) 1f - fraction else fraction
        canvas.save()
        canvas.clipRect(paddingLeft, 0, width - paddingRight, height)
        canvas.translate(paddingLeft - travel * offset, (height - headline.height) / 2f)
        paint.color = currentTextColor
        headline.draw(canvas)
        canvas.restore()
        if (elapsed < scrollMillis && isShown && windowVisibility == VISIBLE) {
            postInvalidateDelayed(if (elapsed < 0) -elapsed else 32L)
        }
    }

    private companion object {
        const val START_PAUSE = 1_300L
    }
}
