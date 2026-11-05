package com.autodrive.launcher

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebDashboardTest {
    @Test fun homePagesAndPanelLifecycle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("launcher", 0).edit().putString("tab", "YOUTUBE").commit()
        val activity = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        fun all(): List<View> = descendants(activity.window.decorView)
        fun web(): List<WebView> = all().filterIsInstance<WebView>()
        fun click(description: String) { all().first { it.contentDescription == description }.performClick() }
        try {
            SystemClock.sleep(1500)
            instrumentation.runOnMainSync {
                assertEquals(2, web().size)
                assertTrue(web().any { it.url?.startsWith("https://www.google.com/maps") == true })
                assertTrue(web().any { it.url?.startsWith("https://m.youtube.com/") == true })
                assertTrue(all().none { it is android.widget.TextView && it.text == "Ajouter une vidéo" })
                for (label in listOf("Phone", "Media", "Apps", "Settings")) {
                    val buttons = all().filter { it.isShown && it.contentDescription == label }
                    assertEquals(label, 1, buttons.size)
                    assertTrue(buttons.single().height / activity.resources.displayMetrics.density >= 48)
                    val text = descendants(buttons.single()).filterIsInstance<android.widget.TextView>().single()
                    assertEquals("Clipped shortcut: $label", 0, text.layout.getEllipsisCount(0))
                }
                for (label in listOf("Radio", "YouTube", "IPTV")) {
                    val button = all().first { it.contentDescription == label }
                    val text = descendants(button).filterIsInstance<android.widget.TextView>().single()
                    assertEquals("Clipped tab: $label", 0, text.layout.getEllipsisCount(0))
                }
                val map = web().first { it.url?.contains("google.com/maps") == true }
                val video = web().first { it.url?.contains("youtube.com") == true }
                val mapLocation = IntArray(2).also(map::getLocationOnScreen)
                val videoLocation = IntArray(2).also(video::getLocationOnScreen)
                if (activity.resources.configuration.screenHeightDp > activity.resources.configuration.screenWidthDp) {
                    assertTrue("Portrait panels must be stacked", videoLocation[1] > mapLocation[1] + map.height)
                    assertTrue("Portrait map must use full width", map.width >= activity.window.decorView.width * .85f)
                } else assertTrue("Landscape panels must be side by side", videoLocation[0] > mapLocation[0] + map.width)
                click("Radio")
                assertEquals(1, web().size)
                click("YouTube")
                assertEquals(2, web().size)
                click("Settings")
                assertEquals(0, web().size)
                click("Back")
                assertEquals(2, web().size)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
