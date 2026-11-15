package com.autodrive.radiomaroc

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Apps the radio opens next to itself from the header: the installed app is launched with its own public launcher
 * intent, never embedded. The radio keeps playing and shrinks to its mini player (see RadioActivity).
 */
// Every package here is also declared in the manifest's <queries>, so Android 11+ lets the radio see it.
internal enum class CompanionApp(val label: String, val icon: Int, private val packages: List<String>) {
    MAPS("Maps", R.drawable.ic_app_maps, listOf("com.google.android.apps.maps", "com.waze")),
    // Google's YouTube first, then the YouTube clients people install on head units that lack it.
    YOUTUBE("YouTube", R.drawable.ic_app_video, listOf(
        "com.google.android.youtube", "app.revanced.android.youtube", "com.google.android.apps.youtube.music",
        "com.liskovsoft.smarttubetv.beta", "com.teamsmart.videomanager.tv", "org.schabi.newpipe",
    ));

    /** The launch intent of the first installed app, or a generic map for Maps; null when nothing can open. */
    fun launchIntent(context: Context): Intent? {
        val manager = context.packageManager
        packages.firstNotNullOfOrNull(manager::getLaunchIntentForPackage)?.let { return it }
        if (this != MAPS) return null
        return Intent(Intent.ACTION_VIEW, "geo:0,0?q=".toUri()).takeIf { it.resolveActivity(manager) != null }
    }
}
