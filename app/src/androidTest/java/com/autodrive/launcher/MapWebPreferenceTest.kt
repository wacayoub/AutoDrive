package com.autodrive.launcher

import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapWebPreferenceTest {
    @Test fun usesOnlyTheVisibleWebChoiceOnMapsPages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, WebDashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        lateinit var web: WebView
        try {
            instrumentation.runOnMainSync { web = views(activity.window.decorView).filterIsInstance<WebView>().first() }
            // Synthetic controls exercise the preference action, not real map rendering.
            for (label in listOf("Stay on web", "Rester sur le site Web")) {
                instrumentation.runOnMainSync {
                    // Stop the real startup page before replacing it with this local fixture.
                    web.stopLoading()
                    web.loadDataWithBaseURL("https://www.google.com/maps/", fixture(label), "text/html", "UTF-8", null)
                }
                var value = ""
                repeat(80) {
                    if (value != "\"web\"") {
                        SystemClock.sleep(100)
                        val result = CountDownLatch(1)
                        instrumentation.runOnMainSync {
                            web.evaluateJavascript("window.choice") { value = it; result.countDown() }
                        }
                        result.await(1, TimeUnit.SECONDS)
                    }
                }
                assertEquals("Provider's web choice was not used: $label", "\"web\"", value)
                val inspected = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    web.evaluateJavascript("""
                        getComputedStyle(document.getElementById('launch')).display==='none' &&
                          getComputedStyle(document.getElementById('consent')).display!=='none' &&
                          document.getElementById('logo').textContent==='Google Maps'
                    """.trimIndent()) { value = it; inspected.countDown() }
                }
                inspected.await(2, TimeUnit.SECONDS)
                assertEquals("Hide only launch invitation, retain consent and logo", "true", value)
            }
            instrumentation.runOnMainSync {
                web.loadDataWithBaseURL("https://accounts.google.com/", fixture("Stay on web"), "text/html", "UTF-8", null)
            }
            SystemClock.sleep(700)
            val completed = CountDownLatch(1)
            var value = ""
            instrumentation.runOnMainSync {
                web.evaluateJavascript("window.choice") { value = it; completed.countDown() }
            }
            completed.await(2, TimeUnit.SECONDS)
            assertEquals("Never act on a Google account page", "\"\"", value)
            val inspected = CountDownLatch(1)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("getComputedStyle(document.getElementById('launch')).display==='none'") {
                    value = it; inspected.countDown()
                }
            }
            inspected.await(2, TimeUnit.SECONDS)
            assertEquals("Never hide account controls", "false", value)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun completeMapDisplayFollowsTheDeviceMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, WebDashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        lateinit var web: WebView
        try {
            instrumentation.runOnMainSync {
                web = views(activity.window.decorView).filterIsInstance<WebView>().first()
                web.setInitialScale(100)
                // Test-only contrast pattern, never a dashboard map replacement.
                web.loadDataWithBaseURL("https://www.google.com/maps/", """
                    <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                    <body style="margin:0;background:white"><div style="width:80px;height:80px;background:black"></div></body></html>
                """.trimIndent(), "text/html", "UTF-8", null)
            }
            SystemClock.sleep(700)
            val completed = CountDownLatch(1)
            var value = ""
            instrumentation.runOnMainSync {
                web.scrollTo(0, 0)
                web.evaluateJavascript("matchMedia('(prefers-color-scheme: dark)').matches") { value = it; completed.countDown() }
            }
            org.junit.Assert.assertTrue(completed.await(2, TimeUnit.SECONDS))
            val visible = CountDownLatch(1)
            instrumentation.runOnMainSync {
                web.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        web.postOnAnimation { web.postOnAnimation { visible.countDown() } }
                    }
                })
            }
            org.junit.Assert.assertTrue("Wait for rendered map display", visible.await(3, TimeUnit.SECONDS))
            val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            assertEquals("Stable provider theme prevents double darkening", "false", value)
            val origin = IntArray(2)
            instrumentation.runOnMainSync { web.getLocationOnScreen(origin) }
            val shot = instrumentation.uiAutomation.takeScreenshot()
            try {
                val density = activity.resources.displayMetrics.density
                val lightPatch = shot.getPixel(origin[0] + (150 * density).toInt(), origin[1] + (150 * density).toInt())
                val darkPatch = shot.getPixel(origin[0] + (20 * density).toInt(), origin[1] + (20 * density).toInt())
                fun brightness(color: Int) = (android.graphics.Color.red(color) + android.graphics.Color.green(color) + android.graphics.Color.blue(color)) / 3
                org.junit.Assert.assertTrue("Whole-page light background must adapt", if (night) brightness(lightPatch) < 80 else brightness(lightPatch) > 200)
                org.junit.Assert.assertTrue("Labels must retain contrasting luminance: ${brightness(darkPatch)}, night=$night", if (night) brightness(darkPatch) > 180 else brightness(darkPatch) < 60)
            } finally { shot.recycle() }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun fixture(label: String) = """
        <!doctype html><html><body>
        <span id="logo">Google Maps</span>
        <button id="launch" onclick="window.choice='app'">${if (label.startsWith("Rester")) "Ouvrir l’appli" else "Open app"}</button>
        <button id="consent" onclick="window.choice='consent'">Accept</button>
        <script>
        window.choice = '';
        setTimeout(() => {
          const button = document.createElement('button');
          button.textContent = '$label';
          button.onclick = () => window.choice = 'web';
          document.body.appendChild(button);
        }, 300);
        </script></body></html>
    """.trimIndent()

    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
}
