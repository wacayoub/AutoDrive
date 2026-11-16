package com.incardrive.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import java.util.Locale

internal data class Launchable(val component: ComponentName, val label: String)

/** The apps of the head unit, their icons, and the Maps and phone apps the home screen opens. */
internal class AppLinks(private val activity: Activity) {
    private val packages = activity.packageManager

    @Suppress("DEPRECATION")
    fun apps(): List<Launchable> = listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)
        .flatMap { category -> packages.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
        .filter {
            it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled &&
                it.activityInfo.packageName != activity.packageName &&
                (it.activityInfo.permission == null ||
                    activity.checkSelfPermission(it.activityInfo.permission) == PackageManager.PERMISSION_GRANTED)
        }
        .map { Launchable(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(packages).toString()) }
        .distinctBy { it.component }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }

    fun icon(app: Launchable): Drawable? = runCatching { packages.getActivityIcon(app.component) }.getOrNull()

    fun open(app: Launchable) = start(Intent.makeMainActivity(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))

    fun openMaps(): Boolean = (packages.getLaunchIntentForPackage(MAPS)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).takeIf { it.resolveActivity(packages) != null })
        ?.let(::start) ?: false

    /**
     * Starts Google Maps guidance to [destination], an address or « latitude,longitude », full screen. Without the
     * Maps app, any app that shows a `geo:` search for it.
     */
    fun navigate(destination: String): Boolean {
        val guidance = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination))).setPackage(MAPS)
            .takeIf { it.resolveActivity(packages) != null }
        val search = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)))
            .takeIf { it.resolveActivity(packages) != null }
        return (guidance ?: search)?.let(::start) ?: false
    }

    /**
     * Hands [number] to a phone app with Android's documented DIAL request: the Incarlink's Bluetooth phone app
     * first if it accepts it, then any other phone app. Returns the app that received it, or null when no phone
     * app accepts numbers from other apps.
     */
    fun dial(number: String): String? {
        val request = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
        val oem = Intent(request).setPackage(OEM_PHONE.packageName).takeIf { it.resolveActivity(packages) != null }
        val any = request.takeIf { it.resolveActivity(packages) != null }
        val chosen = oem ?: any ?: return null
        return if (start(chosen)) chosen.resolveActivity(packages)?.packageName else null
    }

    /** The Incarlink's Bluetooth phone app when present, otherwise the device's dialer. */
    fun openPhone(): Boolean {
        val oem = Intent.makeMainActivity(OEM_PHONE).takeIf { it.resolveActivity(packages) != null }
        val dialer = Intent(Intent.ACTION_DIAL).takeIf { it.resolveActivity(packages) != null }
        return (oem ?: dialer)?.let(::start) ?: false
    }

    private fun start(intent: Intent): Boolean = try {
        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    companion object {
        const val MAPS = "com.google.android.apps.maps"
        // The Incarlink's own Bluetooth phone app.
        val OEM_PHONE = ComponentName("com.carsyso.bluetooth", "com.carsyso.bluetoothcall.MainActivity")
    }
}
