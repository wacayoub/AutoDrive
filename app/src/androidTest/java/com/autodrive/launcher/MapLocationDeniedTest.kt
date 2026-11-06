package com.autodrive.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run separately after revoking location on the disposable emulator, before restoring it. */
@RunWith(AndroidJUnit4::class)
class MapLocationDeniedTest {
    @Test fun permissionResultOrCancellationCompletesTheDeferredCallbackOnlyOnce() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        org.junit.Assume.assumeTrue(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
        val activity=instrumentation.startActivitySync(Intent(context,WebDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        var requested=0;var callbacks=0;var allowed:Boolean?=null;var retained:Boolean?=null
        val callback=GeolocationPermissions.Callback{_,allow,retain->callbacks++;allowed=allow;retained=retain}
        lateinit var panel:BrowserPanel
        try{
            instrumentation.runOnMainSync{
                views(activity.window.decorView).filterIsInstance<BrowserPanel>().forEach{it.release()}
                panel=BrowserPanel(activity,"about:blank",true,{requested++},{}){}
                activity.setContentView(FrameLayout(activity).apply{addView(panel,FrameLayout.LayoutParams(-1,-1))});panel.start()
                val web=views(panel).filterIsInstance<WebView>().single()
                web.webChromeClient!!.onGeolocationPermissionsShowPrompt("https://www.google.com",callback)
                assertEquals(1,requested);assertEquals(0,callbacks)
                panel.onLocationPermissionResult()
                assertEquals(1,callbacks);assertEquals(false,allowed);assertEquals(false,retained)
                panel.onLocationPermissionResult();assertEquals(1,callbacks)
                web.webChromeClient!!.onGeolocationPermissionsShowPrompt("https://www.google.com",callback)
                web.webChromeClient!!.onGeolocationPermissionsHidePrompt()
                assertEquals(2,callbacks)
                panel.onLocationPermissionResult();assertEquals(2,callbacks)
            }
        }finally{instrumentation.runOnMainSync{panel.release();activity.finish()}}
    }
    private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))}else emptyList()
}
