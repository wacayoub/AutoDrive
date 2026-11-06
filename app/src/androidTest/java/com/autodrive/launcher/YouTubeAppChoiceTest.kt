package com.autodrive.launcher

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YouTubeAppChoiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun selectionPersistsAndMissingSelectionDoesNotSilentlyChangeApps() {
        withActivity { activity ->
            instrumentation.runOnMainSync {
                val links = AppLinks(activity)
                val fixture = links.apps().first { it.component.packageName == "com.android.deskclock" }
                links.saveAssociation(MediaTab.YOUTUBE.name, fixture.component)
                assertEquals(fixture.component, AppLinks(activity).forTab(MediaTab.YOUTUBE, links.apps())?.component)
                assertFalse(links.openYouTube(Uri.parse("https://accounts.google.com/")))
                assertFalse(links.openYouTube(Uri.parse("https://youtube.com.example.org/watch?v=test")))
                val missing = ComponentName("com.autodrive.missing", "com.autodrive.missing.MainActivity")
                links.saveAssociation(MediaTab.YOUTUBE.name, missing)
                assertNull(links.forTab(MediaTab.YOUTUBE, links.apps()))
                assertFalse(links.openYouTube())
                assertEquals(missing, links.association(MediaTab.YOUTUBE.name))
                links.saveAssociation(MediaTab.YOUTUBE.name, null)
                assertNull(links.association(MediaTab.YOUTUBE.name))
            }
        }
    }

    @Test fun explicitWebOpenAppGestureUsesTheSelectedApp() {
        withActivity { activity ->
            lateinit var panel: BrowserPanel
            lateinit var web: WebView
            val requested = CountDownLatch(1)
            var opened = false
            instrumentation.runOnMainSync {
                val links = AppLinks(activity)
                links.saveAssociation(MediaTab.YOUTUBE.name,
                    links.apps().first { it.component.packageName == "com.android.deskclock" }.component)
                descendants(activity.window.decorView).filterIsInstance<BrowserPanel>().forEach { it.release() }
                panel = BrowserPanel(activity, "https://m.youtube.com/", false, {}, {}, nativeYouTube = { uri ->
                    opened = links.openYouTube(uri)
                    requested.countDown()
                    opened
                }) {}
                activity.setContentView(FrameLayout(activity).apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) })
                panel.start()
                web = descendants(panel).filterIsInstance<WebView>().single()
                web.stopLoading()
                // Local test page, never shown in the shipped application.
                web.loadDataWithBaseURL("https://m.youtube.com/", """
                    <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                    <body style="margin:0"><a id="native-handoff" style="display:block;width:100vw;height:100vh"
                    href="intent://www.youtube.com/watch?v=M7lc1UVf-VE#Intent;scheme=https;package=com.google.android.youtube;end">Open app</a></body></html>
                """.trimIndent(), "text/html", "UTF-8", null)
            }
            try {
                awaitWebFixture(instrumentation, web, "native-handoff")
                val location = IntArray(2)
                var width = 0
                var height = 0
                instrumentation.runOnMainSync { web.getLocationOnScreen(location); width = web.width; height = web.height }
                val start = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                        location[0] + width / 2f, location[1] + height / 2f, 0)
                    try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                }
                assertTrue("Web Open App did not request the selected app", requested.await(5, TimeUnit.SECONDS))
                assertTrue("Selected fixture app did not launch", opened)
                SystemClock.sleep(500)
                val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
                val activities = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
                assertTrue("Expected clock fixture in the foreground", activities.lineSequence()
                    .any { "ResumedActivity" in it && "com.android.deskclock/" in it })
            } finally {
                instrumentation.runOnMainSync { panel.release() }
            }
        }
    }

    private fun withActivity(block: (WebDashboardActivity) -> Unit) {
        val old = context.getSharedPreferences("launcher", 0).getString("app_YOUTUBE", null)
        val activity = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        try { block(activity) } finally {
            context.getSharedPreferences("launcher", 0).edit().putString("app_YOUTUBE", old).commit()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
