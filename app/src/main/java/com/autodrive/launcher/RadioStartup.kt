package com.autodrive.launcher

import android.content.Context
import android.provider.Settings

/** Reserve one foreground startup attempt per actual Android boot. No boot receiver/service. */
internal class RadioStartup(private val context: Context, private val boot: () -> Int? = {
    runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 } }.getOrNull()
}) {
    private val prefs = context.getSharedPreferences("radio_startup", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    fun reserve(): Boolean {
        if (!enabled) return false
        val count = boot() ?: return false
        if (prefs.getInt("consumed_boot", -1) == count) return false
        return prefs.edit().putInt("consumed_boot", count).commit()
    }
}
