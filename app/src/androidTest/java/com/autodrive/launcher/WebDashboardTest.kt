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
            SystemClock.sleep(if (InstrumentationRegistry.getArguments().getString("captureDashboard") == "true") 20_000 else 1500)
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
                    if (label == "Phone") assertEquals(Palette.green,
                        descendants(buttons.single()).filterIsInstance<DriveIcon>().single().color)
                }
                assertTrue("Source bar removed from home", all().none { it.isShown && it.contentDescription in listOf("Radio", "YouTube", "IPTV") })
                val map = web().first { it.url?.contains("google.com/maps") == true }
                val video = web().first { it.url?.contains("youtube.com") == true }
                val mapLocation = IntArray(2).also(map::getLocationOnScreen)
                val videoLocation = IntArray(2).also(video::getLocationOnScreen)
                if (activity.resources.configuration.screenHeightDp > activity.resources.configuration.screenWidthDp) {
                    assertTrue("Portrait panels must be stacked", videoLocation[1] > mapLocation[1] + map.height)
                    assertTrue("Portrait map must use full width", map.width >= activity.window.decorView.width * .85f)
                } else {
                    assertTrue("Landscape panels must be side by side", videoLocation[0] > mapLocation[0] + map.width)
                    assertTrue("No source bar above video", kotlin.math.abs(videoLocation[1] - mapLocation[1]) <= activity.resources.displayMetrics.density * 2)
                }
                val bounds = android.graphics.Rect().also(activity.window.decorView::getWindowVisibleDisplayFrame)
                for (panel in listOf(map, video)) {
                    val position = IntArray(2).also(panel::getLocationOnScreen)
                    assertTrue("Panel must fit the visible display", position[0] >= bounds.left && position[1] >= bounds.top &&
                        position[0] + panel.width <= bounds.right && position[1] + panel.height <= bounds.bottom)
                }
                if (InstrumentationRegistry.getArguments().getString("captureDashboard") == "true") {
                    val dimensions = org.json.JSONObject().put("density", activity.resources.displayMetrics.density)
                        .put("maps", org.json.JSONObject().put("width", map.width).put("height", map.height))
                        .put("youtube", org.json.JSONObject().put("width", video.width).put("height", video.height))
                    context.openFileOutput("dashboard-size.json", 0).use { it.write(dimensions.toString().toByteArray()) }
                    video.evaluateJavascript("""
                        JSON.stringify([...document.querySelectorAll('ytm-pivot-bar-renderer,ytm-pivot-bar-renderer *')].slice(0,45).map(e=>{
                          const r=e.getBoundingClientRect(),s=getComputedStyle(e);return {tag:e.tagName,classes:e.className?.baseVal??e.className,role:e.getAttribute('role'),label:e.getAttribute('aria-label'),width:r.width,height:r.height,y:r.y,cssHeight:s.height,font:s.fontSize,padding:s.padding,position:s.position};
                        }))
                    """.trimIndent()) { value -> context.openFileOutput("youtube-nav-layout.json",0).use { it.write(value.toByteArray()) } }

                    val image = instrumentation.uiAutomation.takeScreenshot()
                    try { context.openFileOutput("dashboard-size.png", 0).use {
                        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    } } finally { image.recycle() }
                }
                click("Media")
                assertEquals(0, web().size)
                assertTrue(all().single { it.contentDescription == "YouTube" }.isSelected)
                click("Back")
                assertTrue(web().any { it === map }); assertTrue(web().any { it === video })
            }
            // Source pages are newly attached views: let Android lay them out before checking targets.
            for (source in listOf("Radio", "IPTV", "YouTube")) {
                instrumentation.runOnMainSync { click("Media") }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    for (title in listOf("Radio", "YouTube", "IPTV")) {
                        val target = all().single { it.contentDescription == title }
                        assertTrue("Source target: $title", target.height / activity.resources.displayMetrics.density >= 48)
                    }
                    click(source)
                    assertEquals(if (source == "YouTube") 2 else 1, web().size)
                    assertEquals(source, AppLinks(activity).tab.title)
                }
                instrumentation.waitForIdleSync()
            }
            instrumentation.runOnMainSync {
                val beforeSettings = web().toList()
                click("Settings")
                assertEquals(0, web().size)
                click("Back")
                assertEquals(2, web().size)
                beforeSettings.forEach { retained -> assertTrue(web().any { it === retained }) }
            }
            if (InstrumentationRegistry.getArguments().getString("liveYouTubeNavigation") == "true") {
                auditYouTubeNavigation(activity)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    /** Opt-in real website taps; never logs accounts, cookies or page content. */
    private fun auditYouTubeNavigation(activity: WebDashboardActivity) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var video: WebView
        instrumentation.runOnMainSync { video = descendants(activity.window.decorView).filterIsInstance<WebView>().first { it.url?.contains("youtube.com") == true } }
        fun evaluate(script: String): String {
            var result = ""; val done = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync { video.evaluateJavascript(script) { result = it; done.countDown() } }
            check(done.await(4, java.util.concurrent.TimeUnit.SECONDS)); return result
        }
        val compact = org.json.JSONObject(org.json.JSONTokener(evaluate("""
            JSON.stringify((()=>{const b=document.querySelector('ytm-pivot-bar-renderer');
              const icon=b&&[...b.querySelectorAll('c3-icon')].find(e=>e.getBoundingClientRect().width>0);
              const title=b&&b.querySelector('.pivot-bar-item-title');
              return {height:b?.getBoundingClientRect().height||0,icon:icon?.getBoundingClientRect().width||0,font:title?parseFloat(getComputedStyle(title).fontSize):0};})())
        """.trimIndent())).nextValue() as String)
        assertTrue("Actual compact provider bar: $compact", compact.getDouble("height") in 48.0..49.5)
        assertTrue("Actual compact provider icon: $compact", compact.getDouble("icon") in 17.97..18.03)
        assertTrue("Actual compact provider label: $compact", compact.getDouble("font") in 9.97..10.03)
        val observations = org.json.JSONArray()
        for ((selector, paths) in listOf(
            ".pivot-you" to listOf("/feed/you", "/feed/library"),
            ".pivot-w2w" to listOf("/"),
            ".pivot-shorts" to listOf("/shorts", "/feed/shorts")
        )) {
            val raw = evaluate("""
                JSON.stringify((()=>{const e=document.querySelector('ytm-pivot-bar-renderer [role="tab"]$selector');
                  if(!e)return null;const r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,height:r.height,width:innerWidth};})())
            """.trimIndent())
            val decoded = org.json.JSONTokener(raw).nextValue() as String
            assertTrue("Missing provider tab $selector at ${evaluate("location.pathname")}", decoded != "null")
            val rectangle = org.json.JSONObject(decoded)
            assertTrue("Compact provider target", Math.round(rectangle.getDouble("height") * activity.resources.displayMetrics.density) >= Math.round(48 * activity.resources.displayMetrics.density))
            var x = 0f; var y = 0f
            instrumentation.runOnMainSync {
                val origin = IntArray(2).also(video::getLocationOnScreen)
                val scale = video.width / rectangle.getDouble("width")
                x = (origin[0] + rectangle.getDouble("x") * scale).toFloat()
                y = (origin[1] + rectangle.getDouble("y") * scale).toFloat()
            }
            val time = SystemClock.uptimeMillis()
            val down = android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,x,y,0)
            val up = android.view.MotionEvent.obtain(time,time+70,android.view.MotionEvent.ACTION_UP,x,y,0)
            try { instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up) } finally { down.recycle(); up.recycle() }
            val deadline = SystemClock.uptimeMillis() + 12_000
            var path = ""
            do {
                SystemClock.sleep(300)
                path = org.json.JSONTokener(evaluate("location.pathname")).nextValue() as String
                if(paths.any { if(it=="/")path==it else path.startsWith(it) }) break
            } while (SystemClock.uptimeMillis() < deadline)
            assertTrue("Actual provider route after $selector: $path", paths.any { if(it=="/")path==it else path.startsWith(it) })
            observations.put(org.json.JSONObject().put("tab",selector).put("path",path))
            println("Verified actual provider navigation tap: $selector")
            SystemClock.sleep(1500)
        }
        // Shorts may use its own immersive surface without the site's bottom navigation.
        // Return through Android Back rather than demanding Home remain present there.
        repeat(3) {
            if (evaluate("location.pathname") != "\"/\"") {
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                SystemClock.sleep(800)
            }
        }
        assertEquals("Android Back returns from Shorts", "\"/\"", evaluate("location.pathname"))
        instrumentation.targetContext.openFileOutput("youtube-navigation-taps.json",0).use { it.write(observations.toString().toByteArray()) }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
