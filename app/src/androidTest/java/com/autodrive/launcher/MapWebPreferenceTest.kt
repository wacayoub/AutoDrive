package com.autodrive.launcher

import android.content.Intent
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
                    web.loadDataWithBaseURL("https://www.google.com/maps/", fixture(label), "text/html", "UTF-8", null)
                }
                var value = ""
                repeat(24) {
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
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun fixture(label: String) = """
        <!doctype html><html><body>
        <button onclick="window.choice='app'">Open app</button>
        <button onclick="window.choice='consent'">Accept</button>
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
