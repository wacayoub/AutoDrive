package com.autodrive.launcher

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeMapsLaunchTest {
    @Test fun nativeHandoffOrExplicitMissingStateWithoutMapWebsite() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, WebDashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        var installed = false
        fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
        try {
            SystemClock.sleep(700)
            instrumentation.runOnMainSync {
                val all = views(activity.window.decorView)
                assertTrue(all.filterIsInstance<WebView>().none { it.url?.contains("google.com/maps") == true })
                assertEquals(1, all.filterIsInstance<NativeMapsPanel>().size)
                installed = AppLinks(activity).mapsAvailable()
                if (installed) all.single { it.contentDescription == "Ouvrir Google Maps" }.performClick()
                else {
                    assertFalse(AppLinks(activity).openMaps())
                    assertEquals(1, all.count { it.contentDescription == "Installer Google Maps" })
                    assertTrue(all.filterIsInstance<android.widget.TextView>()
                        .any { it.text == "Application absente ou désactivée" })
                }
            }
            if (installed) {
                SystemClock.sleep(1800)
                val pipe = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
                val dump = ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().use { it.readText() }
                assertTrue("Google Maps Android must be in foreground", dump.lineSequence()
                    .any { "ResumedActivity" in it && "com.google.android.apps.maps/" in it })
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
