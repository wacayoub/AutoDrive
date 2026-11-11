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
    private var scrollMillis = 1L
    private var isArticle = false

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

    fun durationFor(bulletin: NewsBulletin): Long {
        if (!ValueAnimator.areAnimatorsEnabled()) return 8_000L
        val textWidthDp = Layout.getDesiredWidth(bulletin.text, paint) / density
        return ((textWidthDp + 32f) * 18f).toLong().coerceIn(4_500L, 14_000L)
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
        val fullWidth = ceil(Layout.getDesiredWidth(text, paint)).toInt() + 2
        drawing = if (isArticle && viewport > 0) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, fullWidth)
                .setIncludePad(false)
                .setMaxLines(1)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .build()
        } else null
    }

    override fun onDraw(canvas: Canvas) {
        val headline = drawing
        if (headline == null || !ValueAnimator.areAnimatorsEnabled()) {
            super.onDraw(canvas)
            return
        }
        val elapsed = SystemClock.uptimeMillis() - startedAt
        val fraction = (elapsed.toFloat() / scrollMillis).coerceIn(0f, 1f)
        val viewport = width - paddingLeft - paddingRight
        val entrance = viewport * .18f
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
}
