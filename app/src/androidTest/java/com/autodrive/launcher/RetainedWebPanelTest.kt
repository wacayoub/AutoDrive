package com.autodrive.launcher

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetainedWebPanelTest {
    // These are optional-web-mode regressions, not claims of native app hosting.
    private var originalInstalledMode: Boolean? = null
    @org.junit.Before fun useExplicitWebMode() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("launcher", 0)
        originalInstalledMode = if (prefs.contains("installed_apps")) prefs.getBoolean("installed_apps", true) else null
        prefs.edit().putBoolean("installed_apps", false).commit()
    }
    @org.junit.After fun restoreMode() {
        val edit = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("launcher", 0).edit()
        originalInstalledMode?.let { edit.putBoolean("installed_apps", it) } ?: edit.remove("installed_apps")
        edit.commit()
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun actualPageTabAndAppChangesRetainSessionStorageAndPauseHtmlAudio() = withActivity { activity ->
        lateinit var map: WebView
        lateinit var video: WebView
        instrumentation.runOnMainSync {
            val web = descendants(activity.window.decorView).filterIsInstance<WebView>()
            map = web.first { it.url?.contains("google.com/maps") == true }
            video = web.first { it !== map }
            map.stopLoading(); video.stopLoading()
            map.loadDataWithBaseURL("https://map-session.autodrive.example/", "<html><body><div id='session-test'>Map</div><script>sessionStorage.setItem('autodrive-test','map');</script></body></html>", "text/html", "UTF-8", null)
            val wav = java.nio.ByteBuffer.allocate(16044).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(16036); put("WAVEfmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                putInt(8000); putInt(16000); putShort(2); putShort(16); put("data".toByteArray()); putInt(16000)
            }.array()
            val audio = android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP)
            video.settings.mediaPlaybackRequiresUserGesture = false // Synthetic silent media fixture only.
            video.loadDataWithBaseURL("https://video-session.autodrive.example/", "<html><body><div id='session-test'>Video</div><audio id='audio' loop src='data:audio/wav;base64,$audio'></audio><script>sessionStorage.setItem('autodrive-test','video');</script></body></html>", "text/html", "UTF-8", null)
        }
        awaitWebFixture(instrumentation, map, "session-test")
        awaitWebFixture(instrumentation, video, "session-test")
        javascript(video, "document.getElementById('audio').play(); 'started'")
        await { javascript(video, "document.getElementById('audio').paused") == "false" }
        instrumentation.runOnMainSync { click(activity.window.decorView, "Settings") }
        await { javascript(video, "document.getElementById('audio').paused") == "true" }
        instrumentation.runOnMainSync {
            click(activity.window.decorView, "Back")
            val web = descendants(activity.window.decorView).filterIsInstance<WebView>()
            assertTrue(web.any { it === map }); assertTrue(web.any { it === video })
            click(activity.window.decorView, "Media")
            click(activity.window.decorView, "Radio")
            click(activity.window.decorView, "Media")
            click(activity.window.decorView, "YouTube")
            assertTrue(descendants(activity.window.decorView).any { it === video })
        }
        assertEquals("\"map\"", javascript(map, "sessionStorage.getItem('autodrive-test')"))
        assertEquals("\"video\"", javascript(video, "sessionStorage.getItem('autodrive-test')"))
        // Genuine Activity background/foreground transition; no account or external site is used.
        instrumentation.runOnMainSync { activity.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        await {
            var background = false
            instrumentation.runOnMainSync { background = activity.lifecycle.currentState != androidx.lifecycle.Lifecycle.State.RESUMED }
            background
        }
        instrumentation.runOnMainSync { activity.startActivity(Intent(context, WebDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        await {
            var foreground = false
            instrumentation.runOnMainSync { foreground = activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED }
            foreground
        }
        instrumentation.waitForIdleSync()
        assertEquals("\"video\"", javascript(video, "sessionStorage.getItem('autodrive-test')"))
        assertEquals("true", javascript(video, "document.getElementById('audio').paused"))
    }

    @Test fun accountRedirectExplainsProfileSeparationAndNeverPretendsToAuthenticatePanel() = withActivity { activity ->
        lateinit var panel: BrowserPanel
        lateinit var web: WebView
        var opened = 0
        instrumentation.runOnMainSync {
            panel = BrowserPanel(activity, "https://session.autodrive.example/", false, {}, {}, openAccountBrowser = { opened++; true }) {}
            activity.setContentView(android.widget.FrameLayout(activity).apply { addView(panel) })
            panel.start()
            web = descendants(panel).filterIsInstance<WebView>().single()
            web.stopLoading()
            web.loadDataWithBaseURL("https://session.autodrive.example/", "<html><body><div id='auth-test'>Test</div></body></html>", "text/html", "UTF-8", null)
        }
        awaitWebFixture(instrumentation, web, "auth-test")
        instrumentation.runOnMainSync {
            fun request(uri: String) = object : WebResourceRequest {
                override fun getUrl() = Uri.parse(uri)
                override fun isForMainFrame() = true
                override fun isRedirect() = true
                override fun hasGesture() = false
                override fun getMethod() = "GET"
                override fun getRequestHeaders() = emptyMap<String,String>()
            }
            assertFalse(web.webViewClient.shouldOverrideUrlLoading(web, request("https://accounts.google.com/ServiceLogin?service=youtube")))
            assertEquals(0, opened)
            assertTrue(web.webViewClient.shouldOverrideUrlLoading(web, request("https://accounts.google.com/o/oauth2/v2/auth?client_id=fixture&response_type=code")))
            assertEquals(0, opened)
            assertTrue(descendants(panel).single { it.contentDescription == "Connexion Google dans le navigateur" }.isShown)
            click(panel, "Ouvrir dans le navigateur")
            assertEquals(1, opened)
            click(panel, "Retour au panneau")
            assertFalse(descendants(panel).single { it.contentDescription == "Connexion Google dans le navigateur" }.isShown)
            assertSame(web, descendants(panel).filterIsInstance<WebView>().single())
            assertFalse(AppLinks(activity).openInBrowser(Uri.parse("http://www.google.com/maps/")))
            panel.release()
        }
    }

    private fun javascript(web: WebView, script: String): String {
        var result = ""; val done = CountDownLatch(1)
        instrumentation.runOnMainSync { web.evaluateJavascript(script) { result = it; done.countDown() } }
        assertTrue(done.await(5, TimeUnit.SECONDS)); return result
    }
    private fun await(condition: () -> Boolean) {
        repeat(40) { if (condition()) return; SystemClock.sleep(100) }
        fail("Web media state not reached")
    }
    private fun withActivity(block: (WebDashboardActivity) -> Unit) {
        val prefs = context.getSharedPreferences("launcher", 0)
        val old = prefs.getString("tab", null)
        prefs.edit().putString("tab", "YOUTUBE").commit()
        val activity = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        try { block(activity) } finally { instrumentation.runOnMainSync { activity.finish() }; prefs.edit().putString("tab", old).commit() }
    }
    private fun click(root: View, description: String) { descendants(root).single { it.isShown && it.contentDescription == description }.performClick() }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
