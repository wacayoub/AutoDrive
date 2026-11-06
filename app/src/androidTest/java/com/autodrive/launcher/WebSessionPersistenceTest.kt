package com.autodrive.launcher

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses only reserved .example origins and synthetic values, never a real Google account. */
@RunWith(AndroidJUnit4::class)
class WebSessionPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val hosts = listOf("https://maps.autodrive.example/", "https://video.autodrive.example/")
    private val cookieName = "autodrive_persistence_probe"
    private val storageKey = "autodrive_persistence_probe"

    @Test fun cookiePolicyAndPanelRecreationKeepExistingWebData() {
        val value = UUID.randomUUID().toString()
        withActivity { activity ->
            for ((index, origin) in hosts.withIndex()) {
                var panel = panel(activity, origin, index == 0)
                var web = web(panel)
                try {
                    instrumentation.runOnMainSync {
                        assertTrue(CookieManager.getInstance().acceptCookie())
                        assertTrue(CookieManager.getInstance().acceptThirdPartyCookies(web))
                    }
                    setCookie(origin, value)
                    loadFixture(web, origin, "localStorage.setItem('$storageKey','$value');")
                    instrumentation.runOnMainSync { panel.release() }
                    flush()
                    panel = panel(activity, origin, index == 0)
                    web = web(panel)
                    loadFixture(web, origin)
                    assertTrue(CookieManager.getInstance().getCookie(origin).orEmpty().contains("$cookieName=$value"))
                    assertEquals("\"$value\"", javascript(web, "localStorage.getItem('$storageKey')"))
                    // Leaving through AutoDrive's real lifecycle must not remove the cookies.
                    instrumentation.runOnMainSync { panel.pause(); panel.release() }
                    flush()
                    assertTrue(CookieManager.getInstance().getCookie(origin).orEmpty().contains("$cookieName=$value"))
                } finally {
                    instrumentation.runOnMainSync { panel.release() }
                    setCookie(origin, "", delete = true)
                }
            }
        }
    }

    @Test fun persistentCookiesAcrossProcessBoundary() {
        val phase = InstrumentationRegistry.getArguments().getString("webSessionPhase") ?: "single"
        val prefs = context.getSharedPreferences("web_session_test", 0)
        val value = if (phase == "read") prefs.getString("marker", null) ?: error("Run write phase first")
            else UUID.randomUUID().toString().also { prefs.edit().putString("marker", it).putInt("writer_pid", android.os.Process.myPid()).commit() }
        val writerPid = prefs.getInt("writer_pid", -1)
        if (phase == "read") assertNotEquals("Read phase must run in a new app process", writerPid, android.os.Process.myPid())
        var localStorageMatches = 0
        withActivity { activity ->
            for ((index, origin) in hosts.withIndex()) {
                val panel = panel(activity, origin, index == 0)
                val web = web(panel)
                try {
                    if (phase != "read") {
                        setCookie(origin, value)
                        loadFixture(web, origin, "localStorage.setItem('$storageKey','$value');")
                    } else loadFixture(web, origin)
                    flush()
                    assertTrue("Persistent cookie missing after $phase phase", CookieManager.getInstance().getCookie(origin).orEmpty().contains("$cookieName=$value"))
                    val localValue = javascript(web, "localStorage.getItem('$storageKey')")
                    if (localValue == "\"$value\"") localStorageMatches++
                    // No public WebStorage disk-flush API exists. A hard kill can lose a recent
                    // DOM-storage write; observe that limitation rather than claiming it is durable.
                    if (phase != "read") assertEquals("\"$value\"", localValue)
                    if (phase != "write") {
                        javascript(web, "localStorage.removeItem('$storageKey')")
                        setCookie(origin, "", delete = true)
                    }
                } finally { instrumentation.runOnMainSync { panel.release() } }
            }
        }
        flush()
        java.io.File(context.filesDir, "session-process-test.json").writeText("""{
          "phase":"$phase", "writerPid":$writerPid, "currentPid":${android.os.Process.myPid()},
          "originsChecked":2, "persistentCookiesVerified":true, "localStorageMatches":$localStorageMatches,
          "dataKind":"SYNTHETIC_EXAMPLE_ORIGINS_ONLY"
        }""")
        if (phase != "write") prefs.edit().clear().commit()
    }

    private fun panel(activity: WebDashboardActivity, origin: String, maps: Boolean): BrowserPanel {
        lateinit var panel: BrowserPanel
        instrumentation.runOnMainSync {
            descendants(activity.window.decorView).filterIsInstance<BrowserPanel>().forEach { it.release() }
            panel = BrowserPanel(activity, origin, maps, {}, {}) {}
            activity.setContentView(FrameLayout(activity).apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) })
            panel.start()
        }
        return panel
    }
    private fun web(panel: BrowserPanel): WebView {
        lateinit var result: WebView
        instrumentation.runOnMainSync { result = descendants(panel).filterIsInstance<WebView>().single() }
        return result
    }
    private fun loadFixture(web: WebView, origin: String, script: String = "") {
        instrumentation.runOnMainSync {
            web.stopLoading()
            web.loadDataWithBaseURL(origin, """<html><body><div id="session-fixture">Session test</div><script>$script</script></body></html>""", "text/html", "UTF-8", null)
        }
        awaitWebFixture(instrumentation, web, "session-fixture")
    }
    private fun setCookie(origin: String, value: String, delete: Boolean = false) {
        val done = CountDownLatch(1)
        var accepted = false
        instrumentation.runOnMainSync {
            CookieManager.getInstance().setCookie(origin,
                "$cookieName=$value; Max-Age=${if (delete) 0 else 3600}; Path=/; Secure; HttpOnly; SameSite=None") {
                accepted = it; done.countDown()
            }
        }
        assertTrue(done.await(5, TimeUnit.SECONDS)); assertTrue(accepted)
    }
    private fun javascript(web: WebView, script: String): String {
        val done = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync { web.evaluateJavascript(script) { result = it; done.countDown() } }
        assertTrue(done.await(5, TimeUnit.SECONDS)); return result
    }
    private fun flush() = runBlocking { WebSessionStorage.persist().join() }
    private fun withActivity(block: (WebDashboardActivity) -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        try { block(activity) } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
