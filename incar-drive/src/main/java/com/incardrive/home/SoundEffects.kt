package com.incardrive.home

import android.media.audiofx.Equalizer
import kotlin.math.log10
import kotlin.math.roundToInt

/** Radio Maroc & Coran's three sound profiles: how loud bass, mid and treble stay (1 = unchanged). */
internal enum class SoundProfile(val label: String, val bass: Float, val mid: Float, val treble: Float) {
    NORMAL("Normal", 1f, 1f, 1f),
    BASS("Basses", 1f, .72f, .48f),
    VOICE("Voix", .40f, 1f, .62f),
}

/**
 * The radio's equaliser profiles for the whole head unit: an Android equaliser on the global output (audio session
 * 0, documented though deprecated), so radio, YouTube or Spotify all get it. Android 13 does not guarantee global
 * effects; when the head unit refuses, [apply] says so and the sound stays as it is.
 */
internal object SoundEffects {
    private var equalizer: Equalizer? = null

    /** The gain of a band centred on [hz] for [profile], in millibels (0 = unchanged). */
    fun gain(profile: SoundProfile, hz: Int): Int {
        val factor = when {
            hz < 250 -> profile.bass
            hz < 4_000 -> profile.mid
            else -> profile.treble
        }
        return (2000 * log10(factor.toDouble())).roundToInt()
    }

    /** Returns false when the head unit does not allow a global equaliser. */
    fun apply(profile: SoundProfile): Boolean {
        if (profile == SoundProfile.NORMAL) {
            release()
            return true
        }
        val eq = equalizer ?: runCatching { Equalizer(0, 0) }.getOrNull()?.also { equalizer = it } ?: return false
        return runCatching {
            val range = eq.bandLevelRange
            for (band in 0 until eq.numberOfBands) {
                val hz = eq.getCenterFreq(band.toShort()) / 1000
                eq.setBandLevel(band.toShort(), gain(profile, hz).coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            eq.enabled = true
        }.isSuccess.also { if (!it) release() }
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }
}
