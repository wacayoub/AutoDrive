package com.autodrive.radiomaroc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect

internal data class DolbyPanel(
    private val activity: ComponentName,
    val openReceiver: ComponentName,
    val closeReceiver: ComponentName,
) {
    fun settingsIntent(context: Context, sessionId: Int): Intent =
        effectIntent(context, AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL, activity, sessionId)

    companion object {
        @Suppress("DEPRECATION")
        fun find(context: Context): DolbyPanel? = runCatching {
            val effects = AudioEffect.queryEffects().orEmpty()
            if (effects.none { it.name.isDolby() || it.implementor.isDolby() }) return null
            val pm = context.packageManager
            val panels = pm.queryIntentActivities(
                Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL),
                PackageManager.MATCH_SYSTEM_ONLY or PackageManager.MATCH_DEFAULT_ONLY,
            )
            panels.firstNotNullOfOrNull { panel ->
                val activity = panel.activityInfo ?: return@firstNotNullOfOrNull null
                if (!activity.canOpen(context) ||
                    !listOf(activity.packageName, panel.loadLabel(pm).toString()).any { it.isDolby() }
                ) return@firstNotNullOfOrNull null
                fun receiver(action: String): ComponentName? = pm.queryBroadcastReceivers(
                    Intent(action).setPackage(activity.packageName), PackageManager.MATCH_SYSTEM_ONLY,
                ).firstOrNull { it.activityInfo?.canOpen(context) == true }?.activityInfo?.component()
                val open = receiver(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                val close = receiver(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                if (open != null && close != null) DolbyPanel(activity.component(), open, close) else null
            }
        }.getOrNull()

        private fun String?.isDolby() = this?.contains("dolby", ignoreCase = true) == true

        private fun ActivityInfo.component() = ComponentName(packageName, name)

        private fun ActivityInfo.canOpen(context: Context) = exported && enabled &&
            (permission == null || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
    }
}

/** Opens or closes only the integrated player's session with the manufacturer's effect controller. */
internal class DolbySession(private val context: Context) {
    private val panel by lazy { DolbyPanel.find(context) }
    var activeId = 0
        private set

    fun update(sessionId: Int): Boolean {
        val previous = activeId
        if (previous == sessionId) return false
        val provider = panel ?: return false
        if (previous > 0) send(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, provider.closeReceiver, previous)
        activeId = if (sessionId > 0 &&
            send(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, provider.openReceiver, sessionId)
        ) sessionId else 0
        return activeId != previous
    }

    private fun send(action: String, receiver: ComponentName, sessionId: Int) = runCatching {
        context.sendBroadcast(effectIntent(context, action, receiver, sessionId))
    }.isSuccess
}

private fun effectIntent(context: Context, action: String, component: ComponentName, sessionId: Int) =
    Intent(action).setComponent(component).apply {
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        if (sessionId > 0) putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
    }
