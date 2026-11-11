package com.autodrive.radiomaroc

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer

internal enum class SoundProfile(val label: String) {
    NORMAL("Normal"), BASS("Basses"), VOICE("Voix");

    fun bandLevel(frequencyHz: Int): Int = when (this) {
        NORMAL -> 0
        BASS -> when {
            frequencyHz < 200 -> 0
            frequencyHz < 650 -> -100
            else -> -280
        }
        VOICE -> when {
            frequencyHz < 250 -> -360
            frequencyHz < 4_000 -> 0
            else -> -180
        }
    }
}

internal object RadioSound {
    const val CLICKS = "radio_sound_clicks"
    const val PROFILE = "radio_sound_profile"
    const val STATUS = "com.autodrive.radiomaroc.SOUND_STATUS"

    enum class Status { NORMAL, WAITING, ACTIVE, UNAVAILABLE }

    fun profile(prefs: SharedPreferences): SoundProfile {
        val saved = prefs.getString(PROFILE, null)
        return SoundProfile.entries.firstOrNull { it.name == saved } ?: SoundProfile.NORMAL
    }

    fun playClick(context: Context, prefs: SharedPreferences) {
        if (prefs.getBoolean(CLICKS, false)) {
            runCatching {
                context.getSystemService(AudioManager::class.java)?.playSoundEffect(AudioManager.FX_KEY_CLICK)
            }
        }
    }
}

/** Owns an effect attached only to the radio player's current audio session. */
internal class RadioEqualizer {
    private var effect: Equalizer? = null
    private var sessionId = 0
    private var appliedProfile: SoundProfile? = null

    fun apply(profile: SoundProfile, audioSessionId: Int): RadioSound.Status {
        if (profile == SoundProfile.NORMAL || audioSessionId <= 0) {
            close()
            return if (profile == SoundProfile.NORMAL) RadioSound.Status.NORMAL else RadioSound.Status.WAITING
        }
        if (sessionId != audioSessionId) close()
        return try {
            val equalizer = effect ?: Equalizer(0, audioSessionId).also {
                effect = it
                sessionId = audioSessionId
            }
            if (!equalizer.hasControl()) return RadioSound.Status.UNAVAILABLE
            if (appliedProfile != profile || !equalizer.enabled) {
                val range = equalizer.bandLevelRange
                // Use relative emphasis by attenuating other bands, without increasing their input gain.
                for (index in 0 until equalizer.numberOfBands) {
                    val band = index.toShort()
                    val frequencyHz = equalizer.getCenterFreq(band) / 1_000
                    val gain = profile.bandLevel(frequencyHz).coerceIn(range[0].toInt(), range[1].toInt())
                    equalizer.setBandLevel(band, gain.toShort())
                }
                if (equalizer.setEnabled(true) != AudioEffect.SUCCESS) {
                    close()
                    return RadioSound.Status.UNAVAILABLE
                }
                appliedProfile = profile
            }
            RadioSound.Status.ACTIVE
        } catch (_: RuntimeException) {
            close()
            RadioSound.Status.UNAVAILABLE
        }
    }

    fun close() {
        effect?.let {
            runCatching { if (it.hasControl()) it.setEnabled(false) }
            runCatching { it.release() }
        }
        effect = null
        sessionId = 0
        appliedProfile = null
    }
}
