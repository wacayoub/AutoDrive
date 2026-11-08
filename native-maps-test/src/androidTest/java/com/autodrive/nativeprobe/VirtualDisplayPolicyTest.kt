package com.autodrive.nativeprobe

import android.app.ActivityManager
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualDisplayPolicyTest {
    @Test fun mapsIsAllowedOnMainDisplayButBlockedOnAppOwnedVirtualDisplays() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
        assumeNotNull(launch)
        val maps = checkNotNull(launch)
        val activities = context.getSystemService(ActivityManager::class.java)
        assertTrue(activities.isActivityStartAllowedOnDisplay(context, Display.DEFAULT_DISPLAY, maps))

        val manager = context.getSystemService(DisplayManager::class.java)
        val texture = SurfaceTexture(false)
        texture.setDefaultBufferSize(400, 360)
        val surface = Surface(texture)
        try {
            for ((name, flag) in listOf("private" to 0, "public" to DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC)) {
                val virtual = checkNotNull(manager.createVirtualDisplay(
                    "AutoDrive QA $name", 400, 360, 160, surface,
                    flag or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                ))
                try {
                    assertTrue(virtual.display.isValid)
                    val allowed = activities.isActivityStartAllowedOnDisplay(context, virtual.display.displayId, maps)
                    println("AutoDrive virtual display policy: $name allowed=$allowed")
                    assertFalse("Android allowed Maps on the app-owned $name display", allowed)
                } finally {
                    virtual.release()
                }
            }
        } finally {
            surface.release()
            texture.release()
        }
    }
}
