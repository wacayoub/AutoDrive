package com.autodrive.radiomaroc

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
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
    private val viewport get() = width - paddingLeft - paddingRight
    private var drawing: StaticLayout? = null
    private var startedAt = 0L
    private var displayMillis = 0L

    init {
        textSize = size.toFloat()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(textColor)
        textDirection = TEXT_DIRECTION_FIRST_STRONG
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        includeFontPadding = false
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        val padding = (10 * density).toInt()
        setPadding(padding, 0, padding, 0)
    }

    fun durationFor(headline: String): Long {
        if (!ValueAnimator.areAnimatorsEnabled()) return 10_000L
        val overflow = measuredWidth(headline) - viewport
        if (viewport <= 0 || overflow <= 0) return STATIC_MILLIS
        return (START_PAUSE + travelTime(overflow) + END_PAUSE).coerceIn(STATIC_MILLIS, MAX_DISPLAY_MILLIS)
    }

    fun showHeadline(headline: String, start: Long, duration: Long) {
        startedAt = start
        displayMillis = duration
        text = headline
        prepareDrawing()
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        prepareDrawing()
    }

    private fun prepareDrawing() {
        drawing = if (displayMillis > 0 && viewport > 0 && measuredWidth(text) > viewport) {
            val scrollBudget = (displayMillis - START_PAUSE - END_PAUSE).coerceAtLeast(0L)
            val maxWidth = viewport + (scrollBudget / 1_000f) * SCROLL_DP_PER_SECOND * density
            val headline = TextUtils.ellipsize(text, paint, maxWidth - 2, TextUtils.TruncateAt.END)
            StaticLayout.Builder.obtain(headline, 0, headline.length, paint, measuredWidth(headline))
                .setIncludePad(false)
                .setMaxLines(1)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .build()
        } else null
    }

    private fun measuredWidth(headline: CharSequence): Int =
        ceil(Layout.getDesiredWidth(headline, paint)).toInt() + 2

    private fun travelTime(overflow: Int): Long =
        (overflow / density / SCROLL_DP_PER_SECOND * 1_000f).toLong()

    override fun onDraw(canvas: Canvas) {
        val headline = drawing
        if (headline == null || !ValueAnimator.areAnimatorsEnabled()) {
            super.onDraw(canvas)
            return
        }
        val elapsed = SystemClock.uptimeMillis() - startedAt
        val travel = (headline.width - viewport).coerceAtLeast(0)
        val duration = travelTime(travel).coerceAtLeast(1L)
        val fraction = ((elapsed - START_PAUSE).toFloat() / duration).coerceIn(0f, 1f)
        val offset = -travel * if (headline.getParagraphDirection(0) < 0) 1f - fraction else fraction
        canvas.save()
        canvas.clipRect(paddingLeft, 0, width - paddingRight, height)
        canvas.translate(paddingLeft + offset, (height - headline.height) / 2f)
        paint.color = currentTextColor
        headline.draw(canvas)
        canvas.restore()
        if (elapsed < START_PAUSE + duration && isShown && windowVisibility == VISIBLE) {
            postInvalidateDelayed(if (elapsed < START_PAUSE) START_PAUSE - elapsed else 32L)
        }
    }

    private companion object {
        const val SCROLL_DP_PER_SECOND = 32f
        const val STATIC_MILLIS = 8_000L
        const val MAX_DISPLAY_MILLIS = 25_000L
        const val START_PAUSE = 1_600L
        const val END_PAUSE = 1_200L
    }
}
