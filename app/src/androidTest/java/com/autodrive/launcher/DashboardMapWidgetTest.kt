package com.autodrive.launcher

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardMapWidgetTest {
    @Test
    fun dashboardDeliversTouchesToTheHostedWidget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        var clicks = 0
        lateinit var widget: View
        try {
            instrumentation.runOnMainSync {
                val dashboard = DashboardView(activity, object : DashboardActions {
                    override fun phone() = Unit
                    override fun apps() = Unit
                    override fun settings() = Unit
                    override fun location() = Unit
                    override fun tab(value: MediaTab) = Unit
                    override fun openMedia() = Unit
                    override fun closeVideo() = Unit
                    override fun transport(action: Long) = Unit
                    override fun favorites() = Unit
                })
                widget = View(activity).apply { setOnClickListener { clicks++ } }
                dashboard.setMapWidget(widget)
                activity.setContentView(dashboard)
            }
            instrumentation.waitForIdleSync()
            val screen = IntArray(2)
            instrumentation.runOnMainSync { widget.getLocationOnScreen(screen) }
            val x = screen[0] + widget.width / 2f
            val y = screen[1] + widget.height / 2f
            val start = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0)
                try {
                    instrumentation.sendPointerSync(event)
                } finally {
                    event.recycle()
                }
            }
            instrumentation.waitForIdleSync()
            assertEquals(1, clicks)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
