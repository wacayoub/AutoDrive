package com.incardrive.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Home pages side by side: a horizontal swipe anywhere moves to the next or previous one, while taps and vertical
 * drags still reach the cards and widgets. Children added with [addPage] are the pages, in order.
 */
internal class SwipePager(context: Context, private val changed: (Int) -> Unit) : FrameLayout(context) {
    private val pages = mutableListOf<View>()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingSpeed = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var offset = 0f
    private var velocity: VelocityTracker? = null
    // A sideways-scrolling row under the finger (the places on the navigation card) keeps its own swipes.
    private var childScrolls = false

    private fun scrollsSideways(view: View, x: Int, y: Int): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        if (x < location[0] || y < location[1] || x >= location[0] + view.width || y >= location[1] + view.height) return false
        if (view.canScrollHorizontally(1) || view.canScrollHorizontally(-1)) return true
        return view is android.view.ViewGroup && (0 until view.childCount).any { scrollsSideways(view.getChildAt(it), x, y) }
    }

    var page = 0
        private set

    fun addPage(view: View) {
        pages += view
        addView(view, 0, LayoutParams(-1, -1))
    }

    fun show(index: Int, animate: Boolean = true) {
        val target = index.coerceIn(0, pages.lastIndex)
        val changedPage = target != page
        page = target
        val end = -page * width.toFloat()
        if (animate && width > 0) {
            val start = offset
            animate().cancel()
            android.animation.ValueAnimator.ofFloat(start, end).apply {
                duration = 260
                interpolator = DecelerateInterpolator()
                addUpdateListener { place(it.animatedValue as Float) }
                start()
            }
        } else place(end)
        if (changedPage) changed(page)
    }

    private fun place(value: Float) {
        offset = value
        pages.forEachIndexed { index, view ->
            view.translationX = index * width + value
            view.visibility = if (abs(view.translationX) < width) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!dragging) place(-page * width.toFloat())
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                childScrolls = pages.getOrNull(page)?.let { scrollsSideways(it, event.rawX.toInt(), event.rawY.toInt()) } == true
                velocity?.recycle()
                velocity = VelocityTracker.obtain().apply { addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (!childScrolls && pages.size > 1 && abs(dx) > slop * 2 && abs(dx) > abs(dy) * 1.5f) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocity?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                if (!dragging && abs(dx) > slop * 2 && abs(dx) > abs(event.y - downY) * 1.5f) dragging = true
                if (dragging) {
                    // Resist past the first and last pages.
                    val raw = -page * width + dx
                    val min = -(pages.size - 1) * width.toFloat()
                    place(if (raw > 0) raw / 3 else if (raw < min) min + (raw - min) / 3 else raw)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    val dx = event.x - downX
                    val speed = velocity?.run { computeCurrentVelocity(1000); xVelocity } ?: 0f
                    val target = when {
                        speed < -flingSpeed || dx < -width / 4f -> page + 1
                        speed > flingSpeed || dx > width / 4f -> page - 1
                        else -> page
                    }
                    dragging = false
                    show(target)
                }
                velocity?.recycle()
                velocity = null
            }
        }
        return true
    }
}
