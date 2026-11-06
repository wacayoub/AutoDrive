package com.autodrive.launcher

import android.app.Instrumentation
import android.os.SystemClock
import android.webkit.WebView
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Wait for the test page and its frame, rather than racing a fixed 700 ms startup delay. */
internal fun awaitWebFixture(instrumentation: Instrumentation, web: WebView, elementId: String) {
    val deadline = SystemClock.elapsedRealtime() + 10_000
    var ready = false
    while (!ready && SystemClock.elapsedRealtime() < deadline) {
        val checked = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web.evaluateJavascript("document.readyState === 'complete' && document.getElementById('$elementId') !== null") {
                ready = it == "true"; checked.countDown()
            }
        }
        checked.await(1, TimeUnit.SECONDS)
        if (!ready) SystemClock.sleep(100)
    }
    assertTrue("Test page was not ready: $elementId", ready)
    val visible = CountDownLatch(1)
    instrumentation.runOnMainSync {
        web.postVisualStateCallback(10, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { web.postOnAnimation { web.postOnAnimation { visible.countDown() } } }
        })
    }
    assertTrue("Test page was not rendered: $elementId", visible.await(5, TimeUnit.SECONDS))
}
