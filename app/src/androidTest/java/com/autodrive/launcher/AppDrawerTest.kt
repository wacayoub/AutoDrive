package com.autodrive.launcher

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDrawerTest {
    @Test fun installedAppsHaveNativeIconCardsSearchAndAnHonestEmptyState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        fun views() = descendants(activity.window.decorView)
        try {
            instrumentation.runOnMainSync { views().single { it.contentDescription == "Apps" }.performClick() }
            instrumentation.waitForIdleSync(); SystemClock.sleep(500)
            instrumentation.runOnMainSync {
                val apps = AppLinks(activity).apps()
                val grid = views().filterIsInstance<GridView>().single()
                assertEquals(apps.size, grid.count)
                assertTrue(grid.numColumns in 2..6)
                for (i in 0 until grid.childCount) {
                    val cell = grid.getChildAt(i)
                    assertEquals("Every recycled card must use the current column width", grid.columnWidth, cell.width)
                    assertTrue("Card outside grid width", cell.left >= 0 && cell.right <= grid.width)
                    val next = grid.getChildAt(i + 1)
                    if (next != null && next.top == cell.top) assertTrue("Overlapping cards", cell.right <= next.left)
                }
                val card = grid.getChildAt(0)
                assertTrue(card.height / activity.resources.displayMetrics.density >= 104)
                val first = grid.adapter.getItem(0) as Launchable
                assertEquals("Ouvrir ${first.label}", card.contentDescription)
                assertNotNull(descendants(card).filterIsInstance<ImageView>().single().drawable)
                val search = views().single { it.contentDescription == "Rechercher une application" } as EditText
                search.setText(first.component.packageName)
                assertTrue(grid.count > 0)
                assertTrue((0 until grid.count).all { (grid.adapter.getItem(it) as Launchable).component.packageName.contains(first.component.packageName) })
                search.setText("no-such-installed-application-fixture")
                assertEquals(0, grid.count)
                search.setText("")
                assertEquals(apps.size, grid.count)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
