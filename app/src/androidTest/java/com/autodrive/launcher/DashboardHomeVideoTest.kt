package com.autodrive.launcher

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardHomeVideoTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun sharedVideoKeepsTabsAndRestoresAfterLeavingHome() {
        val context = instrumentation.targetContext
        context.getSharedPreferences("launcher", 0).edit().clear().commit()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.startActivity(Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Vidéo choisie\nhttps://youtu.be/M7lc1UVf-VE"))
            }
            waitFor { nodes(activity.window.decorView).any { it is WebView && it.isShown } }
            instrumentation.runOnMainSync {
                val all = nodes(activity.window.decorView)
                for (tab in listOf("Radio", "YouTube", "IPTV")) {
                    assertTrue("Tab lost above player: $tab", all.any { it.contentDescription == tab && it.isShown })
                }
                val web = all.filterIsInstance<WebView>().single { it.isShown }
                val density = activity.resources.displayMetrics.density
                assertTrue("Player width below documented minimum", web.width / density >= 200)
                assertTrue("Player height below documented minimum", web.height / density >= 200)
                all.first { it.contentDescription == "Settings" }.performClick()
                assertFalse(nodes(activity.window.decorView).any { it is WebView && it.isShown })
                nodes(activity.window.decorView).first { it.contentDescription == "Back" }.performClick()
            }
            waitFor { nodes(activity.window.decorView).any { it is WebView && it.isShown } }
            instrumentation.runOnMainSync {
                nodes(activity.window.decorView).first { it.contentDescription == "Radio" }.performClick()
                assertFalse(nodes(activity.window.decorView).any { it is WebView && it.isShown })
                nodes(activity.window.decorView).first { it.contentDescription == "YouTube" }.performClick()
            }
            waitFor { nodes(activity.window.decorView).any { it is WebView && it.isShown } }
            assertEquals("M7lc1UVf-VE", context.getSharedPreferences("launcher", 0)
                .getString("dashboard_youtube_id", null))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            context.getSharedPreferences("launcher", 0).edit().clear().commit()
        }
    }

    private fun nodes(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()

    private fun waitFor(condition: () -> Boolean) {
        repeat(40) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(150)
        }
        throw AssertionError("Dashboard player did not become visible")
    }
}
