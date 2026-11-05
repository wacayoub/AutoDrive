package com.autodrive.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.SizeF

/** Hosts Google's published Nearby Traffic AppWidget through Android's consent flow. */
internal class HomeMapWidget(private val activity: Activity) {
    private val manager: AppWidgetManager? = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, HOST_ID)
    private val prefs = activity.getSharedPreferences("home_map", Activity.MODE_PRIVATE)

    val widgetId: Int get() = prefs.getInt("widget_id", INVALID)
    private val pendingId: Int get() = prefs.getInt("pending_id", INVALID)

    fun start() {
        if (manager == null) return
        val current = widgetId
        val pending = pendingId
        // This host is dedicated to the map. Remove unused bindings left by older builds.
        for (id in host.appWidgetIds) {
            if (id != current && id != pending) host.deleteAppWidgetId(id)
        }
        host.startListening()
    }
    fun stop() { if (manager != null) host.stopListening() }

    fun available(): Boolean = manager?.getInstalledProvidersForPackage(AppLinks.MAPS, Process.myUserHandle())
        ?.any { it.provider == TRAFFIC } == true

    fun bound(): Boolean = widgetId != INVALID && manager?.getAppWidgetInfo(widgetId)?.provider == TRAFFIC

    fun setUpAutomatically(request: (Intent) -> Unit) {
        if (!prefs.getBoolean("traffic_setup_attempted", false)) bind(request)
    }

    fun bind(request: (Intent) -> Unit): Boolean {
        val manager = manager ?: return false
        if (bound() || !available()) return false
        prefs.edit().putBoolean("traffic_setup_attempted", true).apply()
        discardPending()
        val id = host.allocateAppWidgetId()
        prefs.edit().putInt("pending_id", id).apply()
        try {
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            }
            if (manager.bindAppWidgetIdIfAllowed(id, TRAFFIC, options)) completeBinding(true)
            else request(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, TRAFFIC)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options))
            return true
        } catch (_: android.content.ActivityNotFoundException) {
            discardPending()
            return false
        } catch (_: SecurityException) {
            discardPending()
            return false
        }
    }

    fun completeBinding(approved: Boolean) {
        val id = pendingId
        if (id == INVALID) return
        val info = manager?.getAppWidgetInfo(id)
        if (!approved || info?.provider != TRAFFIC) {
            discardPending()
        } else if (info.configure != null &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL == 0) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, id, 0, CONFIGURE, null)
            } catch (_: android.content.ActivityNotFoundException) {
                discardPending()
            } catch (_: SecurityException) {
                discardPending()
            }
        } else finishConfiguration(true)
    }

    fun finishConfiguration(success: Boolean) {
        val id = pendingId
        if (!success || id == INVALID || manager?.getAppWidgetInfo(id)?.provider != TRAFFIC) {
            discardPending()
            return
        }
        if (widgetId != INVALID && widgetId != id) host.deleteAppWidgetId(widgetId)
        prefs.edit().putInt("widget_id", id).remove("pending_id").apply()
    }

    fun view(): AppWidgetHostView? {
        val id = widgetId
        val info = manager?.getAppWidgetInfo(id)?.takeIf { it.provider == TRAFFIC } ?: return null
        return host.createView(activity, id, info).apply {
            addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val width = right - left
                val height = bottom - top
                if (width > 0 && height > 0 && (width != oldRight - oldLeft || height != oldBottom - oldTop)) {
                    val density = v.resources.displayMetrics.density
                    updateAppWidgetSize(Bundle(), listOf(SizeF(width / density, height / density)))
                }
            }
        }
    }

    private fun discardPending() {
        if (pendingId == INVALID) return
        host.deleteAppWidgetId(pendingId)
        prefs.edit().remove("pending_id").apply()
    }

    companion object {
        const val CONFIGURE = 740
        private const val HOST_ID = 0x4154
        private const val INVALID = AppWidgetManager.INVALID_APPWIDGET_ID
        private val TRAFFIC = ComponentName(AppLinks.MAPS,
            "com.google.android.apps.gmm.widget.traffic.TrafficWidgetProvider")
    }
}
