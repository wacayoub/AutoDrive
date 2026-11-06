package com.autodrive.launcher

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapDisplayPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val clients = java.util.IdentityHashMap<WebView, WebViewClient>()

    @Test fun realTouchesRememberDisplayAndNewDocumentRestoresOnce() = fixture { activity ->
        var panel = panel(activity)
        var web = load(panel)
        touch(web,"traffic");touch(web,"satellite");touch(web,"locate")
        assertEquals("Traffic touch", "1", evaluate(web,"window.clicks.traffic"))
        assertEquals("Location touch", "1", evaluate(web,"window.clicks.locate"))
        val memory = MapDisplayMemory(context)
        try {
            await { instrumentation.runOnMainSync { panel.rememberDisplay() }; memory.read().options[MapDisplayOption.TRAFFIC] == true && memory.read().followOnStart }
        } catch (failure: AssertionError) {
            throw AssertionError("snapshot=${evaluate(web,MapDisplayScript.snapshot)} / stored=${memory.read()}",failure)
        }
        assertTrue(memory.read().options[MapDisplayOption.SATELLITE] == true)
        assertFalse(memory.read().viewUrl.orEmpty().contains("authuser"))
        instrumentation.runOnMainSync { panel.release() }
        panel = panel(activity);web = load(panel)
        await { evaluate(web,"document.getElementById('traffic').getAttribute('aria-checked')") == "\"true\"" }
        assertEquals("\"true\"",evaluate(web,"document.getElementById('satellite').getAttribute('aria-checked')"))
        // The fixture records calls, not a fabricated position. Actual provider GPS is checked separately.
        assertEquals("1",evaluate(web,"window.clicks.locate"))
        assertEquals("1",evaluate(web,"window.clicks.traffic"))
        assertEquals("0",evaluate(web,"window.clicks.sharing"))
        instrumentation.runOnMainSync { panel.start() }
        SystemClock.sleep(800)
        assertEquals("1",evaluate(web,"window.clicks.locate"))
        instrumentation.runOnMainSync { panel.release() }
    }

    @Test fun startupDoesNotOverwriteChoicesAndAccountsAndSharingAreUntouched() = fixture { activity ->
        val memory = MapDisplayMemory(context)
        memory.capture("""{"schema":1,"url":"https://www.google.com/maps/@33.57,-7.58,14z?authuser=private","options":{"TRAFFIC":true,"LOCATION_SHARING":true},"followOnStart":false}""")
        val before = memory.read()
        val panel = panel(activity)
        var web = load(panel, "https://accounts.google.com/")
        assertEquals("\"undefined\"",evaluate(web,"typeof window.__autoDriveMapDisplay"))
        assertEquals("0",evaluate(web,"window.clicks.traffic"))
        assertEquals("0",evaluate(web,"window.clicks.sharing"))
        instrumentation.runOnMainSync { panel.rememberDisplay() }
        assertEquals(before,memory.read())
        web = load(panel)
        await { evaluate(web,"document.getElementById('traffic').getAttribute('aria-checked')") == "\"true\"" }
        instrumentation.runOnMainSync { panel.rememberDisplay() }
        SystemClock.sleep(500)
        assertEquals("No user action: do not replace preferences with startup defaults",before,memory.read())
        assertEquals("0",evaluate(web,"window.clicks.sharing"))
        instrumentation.runOnMainSync { panel.loadHome(resetMapDisplay = true) }
        assertEquals(RememberedMapDisplay(),memory.read())
        instrumentation.runOnMainSync { panel.release() }
    }

    @Test fun trustedLocationGrantRequestsRetentionAndOtherOriginsAreDenied() = fixture { activity ->
        val panel = panel(activity)
        val web = load(panel)
        var allowed: Boolean? = null;var retained: Boolean? = null
        val callback = GeolocationPermissions.Callback { _,allow,retain -> allowed=allow;retained=retain }
        instrumentation.runOnMainSync {
            web.webChromeClient!!.onGeolocationPermissionsShowPrompt("https://www.google.com",callback)
            assertEquals(true,allowed);assertEquals(true,retained)
            web.webChromeClient!!.onGeolocationPermissionsShowPrompt("https://accounts.google.com",callback)
            assertEquals(false,allowed);assertEquals(false,retained)
            panel.release()
        }
    }

    private fun fixture(block: (WebDashboardActivity) -> Unit) {
        val prefs = context.getSharedPreferences("maps_display",0)
        val before = prefs.getString("state",null)
        prefs.edit().clear().commit()
        val activity = instrumentation.startActivitySync(Intent(context,WebDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        try { block(activity) } finally {
            instrumentation.runOnMainSync { views(activity.window.decorView).filterIsInstance<BrowserPanel>().forEach { it.release() };activity.finish() }
            prefs.edit().clear().putString("state",before).commit()
        }
    }
    private fun panel(activity: WebDashboardActivity): BrowserPanel {
        lateinit var panel: BrowserPanel
        instrumentation.runOnMainSync {
            views(activity.window.decorView).filterIsInstance<BrowserPanel>().forEach { it.release() }
            panel=BrowserPanel(activity,"about:blank",true,{}, {}) {}
            activity.setContentView(FrameLayout(activity).apply { addView(panel,FrameLayout.LayoutParams(-1,-1)) })
            panel.start()
        }
        return panel
    }
    private fun load(panel: BrowserPanel, url: String = "https://www.google.com/maps/@33.57,-7.58,14z?authuser=private"): WebView {
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            web=views(panel).filterIsInstance<WebView>().single()
            web.stopLoading()
            val real=clients.getOrPut(web){web.webViewClient}
            // Native URL/origin checks must remain enabled. Serve this test-only document through
            // a public request interceptor; loadDataWithBaseURL exposes a data: URL on this WebView.
            web.webViewClient=object:WebViewClient(){
                override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? =
                    if(request.isForMainFrame && request.url.scheme=="https" && request.url.host==android.net.Uri.parse(url).host)
                        WebResourceResponse("text/html","UTF-8",html.byteInputStream()) else null
                override fun onPageStarted(view:WebView,page:String?,icon:android.graphics.Bitmap?){real.onPageStarted(view,page,icon)}
                override fun onPageCommitVisible(view:WebView,page:String?){real.onPageCommitVisible(view,page)}
                override fun onPageFinished(view:WebView,page:String?){real.onPageFinished(view,page)}
            }
            web.loadUrl(url)
        }
        try { await { evaluate(web,"window.fixtureReady === true") == "true" } }
        catch(failure:AssertionError){throw AssertionError("Fixture load: ${evaluate(web,"JSON.stringify({url:location.href,title:document.title,ready:document.readyState})")}",failure)}
        SystemClock.sleep(600)
        return web
    }
    private fun evaluate(web: WebView,script:String):String {
        // Call on the instrumentation thread; callbacks need the UI loop to keep running.
        var value="";val done=CountDownLatch(1)
        instrumentation.runOnMainSync { web.evaluateJavascript(script) { value=it;done.countDown() } }
        check(done.await(3,TimeUnit.SECONDS));return value
    }
    private fun touch(web:WebView,id:String) {
        val data=JSONArray(evaluate(web,"""JSON.stringify((()=>{const r=document.getElementById('$id').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2,innerWidth]})())""").let { JSONTokener(it).nextValue() as String })
        var x=0f;var y=0f
        instrumentation.runOnMainSync {
            val origin=IntArray(2);web.getLocationOnScreen(origin);val scale=web.width/data.getDouble(2)
            x=(origin[0]+data.getDouble(0)*scale).toFloat();y=(origin[1]+data.getDouble(1)*scale).toFloat()
        }
        val now=SystemClock.uptimeMillis();val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);val up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,x,y,0)
        try { instrumentation.sendPointerSync(down);instrumentation.sendPointerSync(up) } finally { down.recycle();up.recycle() }
        instrumentation.waitForIdleSync();SystemClock.sleep(100)
    }
    private fun await(condition:()->Boolean) {
        val deadline=SystemClock.uptimeMillis()+6_000
        while(SystemClock.uptimeMillis()<deadline){if(condition())return;SystemClock.sleep(50)}
        fail("Display fixture did not reach expected state")
    }
    private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))}else emptyList()
    private val html="""
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>
        <button id="traffic" role="checkbox" aria-checked="false">Traffic</button>
        <button id="satellite" role="checkbox" aria-checked="false">Satellite</button>
        <button id="locate" aria-label="Show your location" aria-checked="false">Locate</button>
        <button id="sharing" role="checkbox" aria-checked="false">Real-time location sharing</button>
        <style>button{display:block;width:230px;height:60px;margin:8px}</style>
        <script>window.clicks={traffic:0,satellite:0,locate:0,sharing:0};
        Object.keys(clicks).forEach(id=>document.getElementById(id).onclick=function(){clicks[id]++;this.setAttribute('aria-checked',this.getAttribute('aria-checked')!=='true')});
        window.fixtureReady=true;</script></body></html>
    """.trimIndent()
}
