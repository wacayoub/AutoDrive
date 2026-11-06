package com.autodrive.launcher

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledAppsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun installedIsDefaultAndChosenAppOpensExternallyWithoutHomeRelaunchLoop() = fixture { activity ->
        lateinit var clock: Launchable
        instrumentation.runOnMainSync {
            val links = AppLinks(activity)
            assertTrue(links.installedApps)
            assertEquals(0, views(activity.window.decorView).filterIsInstance<WebView>().size)
            // Clock is a real installed fixture for intent dispatch, not YouTube playback evidence.
            clock = links.apps().first { it.component.packageName == "com.android.deskclock" }
            links.saveAssociation(MediaTab.YOUTUBE.name, clock.component)
            click(activity, "Settings"); click(activity, "Back")
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { click(activity, "Ouvrir YouTube") }
        awaitForeground(clock.component.packageName)
        // Actual Android Back from the external root activity, followed later by HOME intent re-entry.
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        awaitForeground(context.packageName)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(600)
        assertTrue("Home must not automatically relaunch the external app", foreground(context.packageName))
        instrumentation.runOnMainSync {
            assertEquals(clock.component, AppLinks(activity).forTab(MediaTab.YOUTUBE, AppLinks(activity).apps())?.component)
            assertEquals(0, views(activity.window.decorView).filterIsInstance<WebView>().size)
        }
        checkLayout(activity)
        instrumentation.runOnMainSync { click(activity, "Media") }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { click(activity, "YouTube") }
        awaitForeground(clock.component.packageName)
        instrumentation.runOnMainSync { activity.startActivity(homeIntent()) }
        awaitForeground(context.packageName)
    }

    @Test fun unavailableAssociationStaysMissingAndDoesNotLoadWeb() = fixture { activity ->
        instrumentation.runOnMainSync {
            AppLinks(activity).saveAssociation(MediaTab.YOUTUBE.name, ComponentName("com.autodrive.absent", "Absent"))
            click(activity, "Settings"); click(activity, "Back")
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val all = views(activity.window.decorView)
            assertTrue(all.filterIsInstance<android.widget.TextView>().any { it.text == "Application choisie indisponible" })
            assertTrue(all.none { it is WebView })
            assertTrue(all.none { it.contentDescription == "Ouvrir YouTube" })
            assertNotNull(all.firstOrNull { it.contentDescription == "Choisir YouTube" })
            assertNull(AppLinks(activity).forTab(MediaTab.YOUTUBE, AppLinks(activity).apps()))
        }
        checkLayout(activity)
        if (InstrumentationRegistry.getArguments().getString("captureInstalled") == "true") {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            try { context.openFileOutput("installed-apps-1.14.0.png", 0).use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            } } finally { bitmap.recycle() }
        }
    }

    @Test fun officialGoogleMapsLaunchesInItsOwnActivityWhenInstalled() = fixture { activity ->
        var installed = false
        instrumentation.runOnMainSync { installed = AppLinks(activity).mapsAvailable() }
        assumeTrue("Official Google Maps is not installed in this test environment", installed)
        instrumentation.runOnMainSync { click(activity, "Ouvrir Google Maps") }
        awaitForeground(AppLinks.MAPS)
        instrumentation.runOnMainSync { activity.startActivity(homeIntent()) }
        awaitForeground(context.packageName)
        SystemClock.sleep(600)
        assertTrue(foreground(context.packageName))
    }

    @Test fun missingMapsDoesNotOpenAWebsiteOrAnotherApp() = fixture { activity ->
        var installed = false
        instrumentation.runOnMainSync { installed = AppLinks(activity).mapsAvailable() }
        assumeTrue("This scenario requires Google Maps to be absent", !installed)
        instrumentation.runOnMainSync {
            val all = views(activity.window.decorView)
            assertTrue(all.filterIsInstance<android.widget.TextView>().any { it.text == "Google Maps non installé ou désactivé" })
            assertFalse(AppLinks(activity).openMaps())
            assertTrue(all.none { it is WebView })
            assertTrue(all.none { it.contentDescription == "Ouvrir Google Maps" })
        }
        assertTrue(foreground(context.packageName))
    }

    @Test fun settingsSwitchesBetweenExplicitWebModeAndInstalledApps() = fixture { initial ->
        var current = initial
        try {
            for ((index, expectInstalled) in listOf(1 to false, 0 to true)) {
                instrumentation.runOnMainSync { click(current, "Settings") }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    views(current.window.decorView).single {
                        it.contentDescription?.startsWith("Affichage Maps / YouTube ·") == true
                    }.performClick()
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    // The real Android single-choice dialog lives in a separate window.
                    val list = android.view.inspector.WindowInspector.getGlobalWindowViews()
                        .flatMap(::views).filterIsInstance<android.widget.ListView>().single { it.isShown }
                    list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
                }
                current = awaitRecreated(current)
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals(expectInstalled, AppLinks(current).installedApps)
                    assertEquals(if (expectInstalled) 0 else 2,
                        views(current.window.decorView).filterIsInstance<WebView>().size)
                }
            }
        } finally { instrumentation.runOnMainSync { current.finish() } }
    }

    private fun fixture(block: (WebDashboardActivity) -> Unit) {
        val prefs = context.getSharedPreferences("launcher", 0)
        val oldMode = if (prefs.contains("installed_apps")) prefs.getBoolean("installed_apps", true) else null
        val oldTab = prefs.getString("tab", null)
        val oldApp = prefs.getString("app_YOUTUBE", null)
        val startup = RadioStartup(context)
        val oldStartup = startup.enabled
        startup.enabled = false
        prefs.edit().remove("installed_apps").putString("tab", "YOUTUBE").commit()
        val activity = instrumentation.startActivitySync(homeIntent()) as WebDashboardActivity
        try { instrumentation.waitForIdleSync(); block(activity) } finally {
            instrumentation.runOnMainSync { activity.finish() }
            val edit = prefs.edit().putString("tab", oldTab).putString("app_YOUTUBE", oldApp)
            oldMode?.let { edit.putBoolean("installed_apps", it) } ?: edit.remove("installed_apps")
            edit.commit(); startup.enabled = oldStartup
        }
    }

    private fun checkLayout(activity: WebDashboardActivity) = instrumentation.runOnMainSync {
        val root = activity.window.decorView
        val all = views(root)
        for (name in listOf("Phone", "Media", "Apps", "Settings")) {
            val button = all.single { it.isShown && it.contentDescription == name }
            assertTrue(button.height / activity.resources.displayMetrics.density >= 48)
        }
        for (panel in all.filterIsInstance<InstalledAppPanel>()) {
            for (button in views(panel).filter { it.isClickable }) {
                val pos = IntArray(2).also(button::getLocationOnScreen)
                assertTrue("App action must fit on screen", pos[0] >= 0 && pos[1] >= 0 &&
                    pos[0] + button.width <= root.width && pos[1] + button.height <= root.height)
                assertTrue("App action clipped inside panel", button.top >= panel.paddingTop && button.bottom <= panel.height - panel.paddingBottom)
                for (text in views(button).filterIsInstance<android.widget.TextView>())
                    assertEquals("Clipped app action ${text.text}", 0, text.layout.getEllipsisCount(0))
            }
        }
    }
    private fun homeIntent() = Intent(context, WebDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    private fun click(activity: WebDashboardActivity, description: String) = views(activity.window.decorView)
        .single { it.contentDescription == description }.performClick()
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun foreground(pkg: String): Boolean {
        val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
        val activities = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        return activities.lineSequence().any { "ResumedActivity" in it && "$pkg/" in it }
    }
    private fun awaitForeground(pkg: String) {
        repeat(40) { if (foreground(pkg)) return; SystemClock.sleep(100) }
        fail("Expected real external activity in foreground: $pkg")
    }
    private fun awaitRecreated(old: WebDashboardActivity): WebDashboardActivity {
        repeat(50) {
            var next: WebDashboardActivity? = null
            instrumentation.runOnMainSync {
                next = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<WebDashboardActivity>().firstOrNull { it !== old }
            }
            next?.let { return it }
            SystemClock.sleep(100)
        }
        throw AssertionError("Dashboard did not recreate after changing the display mode")
    }
}
