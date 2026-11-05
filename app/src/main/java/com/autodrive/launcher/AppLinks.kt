package com.autodrive.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import java.util.Locale

internal enum class MediaTab(val title: String) {
    RADIO("Radio"), YOUTUBE("YouTube"), IPTV("IPTV")
}

internal data class Launchable(val component: ComponentName, val label: String)

internal class AppLinks(private val activity: Activity) {
    private val packages = activity.packageManager
    private val preferences = activity.getSharedPreferences("launcher", Activity.MODE_PRIVATE)

    var tab: MediaTab
        get() = MediaTab.entries.firstOrNull { it.name == preferences.getString("tab", null) }
            ?: MediaTab.YOUTUBE
        set(value) { preferences.edit().putString("tab", value.name).apply() }

    var dashboardYouTubeId: String?
        get() = preferences.getString("dashboard_youtube_id", null)
        set(value) { preferences.edit().putString("dashboard_youtube_id", value).apply() }

    fun apps(): List<Launchable> = listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)
        .flatMap { category ->
            packages.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category),
                PackageManager.ResolveInfoFlags.of(0))
        }
        .filter { it.activityInfo.exported && it.activityInfo.packageName != activity.packageName }
        .map {
            Launchable(ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                it.loadLabel(packages).toString())
        }
        .distinctBy { it.component }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }

    fun saveAssociation(key: String, component: ComponentName?) {
        preferences.edit().putString("app_$key", component?.flattenToString()).apply()
    }

    fun association(key: String) = preferences.getString("app_$key", null)
        ?.let(ComponentName::unflattenFromString)

    fun forTab(tab: MediaTab, apps: List<Launchable>): Launchable? {
        val saved = association(tab.name)
        if (saved != null) return apps.firstOrNull { it.component == saved }
        if (tab == MediaTab.YOUTUBE) return apps.firstOrNull { it.component.packageName == YOUTUBE }
        return null
    }

    fun openYouTube(uri: Uri? = null): Boolean {
        val selected = forTab(MediaTab.YOUTUBE, apps()) ?: return false
        if (uri != null) {
            if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443) ||
                uri.host !in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")) return false
            val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(selected.component.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(packages) != null && start(intent)) return true
        }
        return open(selected)
    }

    fun icon(app: Launchable): Drawable? = runCatching {
        packages.getActivityIcon(app.component)
    }.getOrNull()

    fun open(app: Launchable) = start(Intent.makeMainActivity(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))

    fun dial() = start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))

    private fun mapsIntent() = packages.getLaunchIntentForPackage(MAPS)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse(MAPS_URL)).setPackage(MAPS)
            .takeIf { it.resolveActivity(packages) != null }

    fun mapsAvailable() = mapsIntent() != null

    fun mapsVersion() = runCatching {
        packages.getPackageInfo(MAPS, PackageManager.PackageInfoFlags.of(0)).versionName
    }.getOrNull()

    fun playServices() = runCatching {
        val info = packages.getPackageInfo(PLAY, PackageManager.PackageInfoFlags.of(0))
        if (info.applicationInfo?.enabled == true) "Installed · ${info.versionName.orEmpty()}"
        else "Disabled"
    }.getOrDefault("Unavailable")

    fun start(intent: Intent): Boolean {
        return try {
            activity.startActivity(intent)
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        const val MAPS = "com.google.android.apps.maps"
        const val PLAY = "com.google.android.gms"
        const val YOUTUBE = "com.google.android.youtube"
        const val PHONE = "PHONE"
        private const val MAPS_URL = "https://www.google.com/maps/@?api=1&map_action=map"
    }
}
