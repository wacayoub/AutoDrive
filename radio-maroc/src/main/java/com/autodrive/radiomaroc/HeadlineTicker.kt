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
    private val entrance = 16f * density
    private var drawing: StaticLayout? = null
    private var startedAt = 0L
    private var scrollMillis = 1L
    private var isArticle = false

    init {
        textSize = size.toFloat()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(textColor)
        textDirection = TEXT_DIRECTION_FIRST_STRONG
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        includeFontPadding = false
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        val padding = (7 * density).toInt()
        setPadding(padding, 0, padding, 0)
    }

    fun durationFor(bulletin: NewsBulletin): Long {
        if (!ValueAnimator.areAnimatorsEnabled()) return 10_000L
        val travelDp = (measuredWidth(scrollingText(bulletin.text)) + entrance) / density
        return (travelDp / SCROLL_DP_PER_SECOND * 1_000).toLong().coerceIn(5_500L, MAX_SCROLL_MILLIS)
    }

    fun showHeadline(bulletin: NewsBulletin, start: Long, duration: Long) {
        isArticle = bulletin.url != null
        startedAt = start
        scrollMillis = duration.coerceAtLeast(1L)
        text = bulletin.text
        prepareDrawing()
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        prepareDrawing()
    }

    private fun prepareDrawing() {
        val viewport = width - paddingLeft - paddingRight
        drawing = if (isArticle && viewport > 0) {
            val headline = scrollingText(text)
            StaticLayout.Builder.obtain(headline, 0, headline.length, paint, measuredWidth(headline))
                .setIncludePad(false)
                .setMaxLines(1)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .build()
        } else null
    }

    private fun scrollingText(headline: CharSequence): CharSequence {
        val maxWidth = SCROLL_DP_PER_SECOND * (MAX_SCROLL_MILLIS / 1_000f) * density - entrance
        return TextUtils.ellipsize(headline, paint, maxWidth, TextUtils.TruncateAt.END)
    }

    private fun measuredWidth(headline: CharSequence): Int =
        ceil(Layout.getDesiredWidth(headline, paint)).toInt() + 2

    override fun onDraw(canvas: Canvas) {
        val headline = drawing
        if (headline == null || !ValueAnimator.areAnimatorsEnabled()) {
            super.onDraw(canvas)
            return
        }
        val elapsed = SystemClock.uptimeMillis() - startedAt
        val fraction = (elapsed.toFloat() / scrollMillis).coerceIn(0f, 1f)
        val viewport = width - paddingLeft - paddingRight
        val travel = headline.width + entrance
        val left = entrance - fraction * travel
        val offset = if (headline.getParagraphDirection(0) < 0) viewport - headline.width - left else left
        canvas.save()
        canvas.clipRect(paddingLeft, 0, width - paddingRight, height)
        canvas.translate(paddingLeft + offset, (height - headline.height) / 2f)
        paint.color = currentTextColor
        headline.draw(canvas)
        canvas.restore()
        if (elapsed < scrollMillis && isShown && windowVisibility == VISIBLE) {
            postInvalidateDelayed(32L)
        }
    }

    private companion object {
        const val SCROLL_DP_PER_SECOND = 32f
        const val MAX_SCROLL_MILLIS = 25_000L
    }
}
