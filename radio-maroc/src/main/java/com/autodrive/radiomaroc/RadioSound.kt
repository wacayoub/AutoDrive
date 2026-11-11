package com.autodrive.radiomaroc

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool

internal enum class SoundProfile(val label: String, val bass: Float, val mid: Float, val treble: Float) {
    NORMAL("Normal", 1f, 1f, 1f),
    BASS("Basses", 1f, .72f, .48f),
    VOICE("Voix", .40f, 1f, .62f),
}

internal object RadioSound {
    const val CLICKS = "radio_sound_clicks"
    const val PROFILE = "radio_sound_profile"
    const val STATUS = "com.autodrive.radiomaroc.SOUND_STATUS"

    enum class Status { NORMAL, WAITING, ACTIVE, UNAVAILABLE }

    // The activity and the playback service share one short sample on the app's main thread.
    private var clickPool: SoundPool? = null
    private var clickSample = 0
    private var clickReady = false
    private var playWhenLoaded = false

    fun profile(prefs: SharedPreferences): SoundProfile {
        val saved = prefs.getString(PROFILE, null)
        return SoundProfile.entries.firstOrNull { it.name == saved } ?: SoundProfile.NORMAL
    }

    fun prepareClicks(context: Context, prefs: SharedPreferences) {
        if (clickPool != null || !prefs.getBoolean(CLICKS, false)) return
        runCatching {
            val pool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            ).build()
            clickPool = pool
            pool.setOnLoadCompleteListener { _, sample, status ->
                if (sample == clickSample && status == 0) {
                    clickReady = true
                    if (playWhenLoaded && prefs.getBoolean(CLICKS, false)) playLoadedClick(pool)
                }
                playWhenLoaded = false
                pool.setOnLoadCompleteListener(null)
            }
            clickSample = pool.load(context.applicationContext, R.raw.radio_click, 1)
        }
    }

    fun playClick(context: Context, prefs: SharedPreferences) {
        if (!prefs.getBoolean(CLICKS, false)) return
        prepareClicks(context, prefs)
        if (clickReady) clickPool?.let(::playLoadedClick) else playWhenLoaded = true
    }

    private fun playLoadedClick(pool: SoundPool) {
        pool.play(clickSample, .72f, .72f, 1, 0, 1f)
    }
}
