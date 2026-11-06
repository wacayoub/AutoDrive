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
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in live provider inspection, no account/profile/cookie extraction. */
@RunWith(AndroidJUnit4::class)
class MapLiveAuditTest {
    @Test fun inspectLiveMapControls() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveMapsAudit") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if(args.getString("resetDisplay")=="true") MapDisplayMemory(instrumentation.targetContext).clear()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, WebDashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        try {
            SystemClock.sleep(7_000)
            lateinit var web: WebView
            instrumentation.runOnMainSync {
                web = views(activity.window.decorView).filterIsInstance<BrowserPanel>().first().let { views(it).filterIsInstance<WebView>().single() }
            }
            val action = args.getString("mapAction").orEmpty()
            if (action.isNotBlank()) {
                for(step in action.split('|')) {
                    val parts=step.split('=',limit=2)
                    touchControl(web,parts[0],parts.getOrNull(1)?.toBooleanStrictOrNull())
                }
                SystemClock.sleep(2000)
            }
            val scriptedAction = args.getString("scriptedAction").orEmpty()
            if(scriptedAction.isNotBlank()) {
                val click = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    web.evaluateJavascript("""
                        (() => {const buttons=[...document.querySelectorAll('button,[role="button"],[role="menuitemcheckbox"]')];
                          const needle=${org.json.JSONObject.quote(scriptedAction)};
                          const el=buttons.find(e => (e.getAttribute('aria-label')||e.textContent||'').trim()===needle && e.getClientRects().length);
                          if(el) el.click(); return !!el;})();
                    """.trimIndent()) { click.countDown() }
                }
                click.await(3,TimeUnit.SECONDS);SystemClock.sleep(2_000)
            }
            var result = ""
            val done = CountDownLatch(1)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("""
                    JSON.stringify({url:location.href,title:document.title,replay:window.__autoDriveMapDisplay?{locationAttempted:window.__autoDriveMapDisplay.locationAttempted,attempted:window.__autoDriveMapDisplay.attempted}:null,controls:[...document.querySelectorAll('button,[role="button"],[role="menuitemcheckbox"],[role="radio"],[role="switch"]')]
                    .filter(e=>e.getClientRects().length && getComputedStyle(e).visibility!=='hidden').slice(0,100).map(e=>({tag:e.tagName,role:e.getAttribute('role'),label:e.getAttribute('aria-label'),text:(e.textContent||'').trim().slice(0,100),pressed:e.getAttribute('aria-pressed'),checked:e.getAttribute('aria-checked'),title:e.getAttribute('title')}))})
                """.trimIndent()) { result=it;done.countDown() }
            }
            check(done.await(5,TimeUnit.SECONDS))
            if(args.getString("expectRestored")=="true"){
                val data=org.json.JSONObject(org.json.JSONTokener(result).nextValue() as String)
                val controls=data.getJSONArray("controls")
                fun selected(suffix:String)=(0 until controls.length()).any{index->controls.getJSONObject(index).let{it.optString("text").endsWith(suffix)&&it.optString("checked")=="true"}}
                org.junit.Assert.assertTrue("Live Google traffic selection restored",selected("Trafic")||selected("Traffic"))
                org.junit.Assert.assertTrue("Live Google satellite selection restored",selected("Satellite"))
                org.junit.Assert.assertTrue(data.getJSONObject("replay").getBoolean("locationAttempted"))
                org.junit.Assert.assertTrue("Live Google current-location control active",(0 until controls.length()).any{index->controls.getJSONObject(index).let{
                    it.optString("label") in setOf("Recentrer la carte sur votre position","Show your location","Recenter map on your location")&&it.optString("checked")=="true"
                }})
            }
            activity.openFileOutput("map-live-audit.json",0).use { it.write(result.toByteArray()) }
            activity.openFileOutput("map-live-audit-pid.txt",0).use { it.write(android.os.Process.myPid().toString().toByteArray()) }
            if(args.getString("capture")=="true"){
                val image=instrumentation.uiAutomation.takeScreenshot()
                try{activity.openFileOutput("map-live-audit.png",0).use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
                finally{image.recycle()}
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    private fun touchControl(web:WebView,label:String,wanted:Boolean?=null,openMenu:Boolean=true){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun geometry(scroll:Boolean):org.json.JSONObject?{
            var result="";val done=CountDownLatch(1)
            instrumentation.runOnMainSync{
                web.evaluateJavascript("""
                    JSON.stringify((()=>{
                      const norm=s=>(s||'').replace(/[\ue000-\uf8ff\u200e\u200f\u202a-\u202e]/g,'').replace(/\s+/g,' ').trim();
                      const el=[...document.querySelectorAll('button,[role="button"]')].find(e=>{const r=e.getBoundingClientRect();return norm(e.getAttribute('aria-label')||e.textContent)===${org.json.JSONObject.quote(label)} && r.width>0 && r.height>0 && r.x+r.width/2>0 && r.x+r.width/2<innerWidth && getComputedStyle(e).visibility!=='hidden'});
                      if(!el)return null;
                      if($scroll)el.scrollIntoView({block:'center'});
                      const r=el.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,width:innerWidth,checked:el.getAttribute('aria-checked')};
                    })())
                """.trimIndent()){result=it;done.countDown()}
            }
            check(done.await(3,TimeUnit.SECONDS))
            val text=org.json.JSONTokener(result).nextValue() as? String ?:return null
            return if(text=="null")null else org.json.JSONObject(text)
        }
        var data=geometry(true)
        if(data==null && openMenu && wanted!=null){touchControl(web,"Menu",openMenu=false);data=geometry(true)}
        if(data==null && label=="Fermer")return
        check(data!=null){"Missing live map control: $label"}
        SystemClock.sleep(300);data=geometry(false)!!
        if(wanted!=null && data.optString("checked")==wanted.toString())return
        var x=0f;var y=0f
        instrumentation.runOnMainSync{
            val pos=IntArray(2);web.getLocationOnScreen(pos);val scale=web.width/data.getDouble("width")
            x=(pos[0]+data.getDouble("x")*scale).toFloat();y=(pos[1]+data.getDouble("y")*scale).toFloat()
        }
        val time=SystemClock.uptimeMillis()
        val down=android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,x,y,0)
        val up=android.view.MotionEvent.obtain(time,time+60,android.view.MotionEvent.ACTION_UP,x,y,0)
        try{instrumentation.sendPointerSync(down);instrumentation.sendPointerSync(up)}finally{down.recycle();up.recycle()}
        instrumentation.waitForIdleSync();SystemClock.sleep(500)
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
}
