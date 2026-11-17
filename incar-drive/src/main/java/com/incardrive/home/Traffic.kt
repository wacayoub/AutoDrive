package com.incardrive.home

import android.graphics.Color

/** How the traffic flows, in the three colours drivers know from their navigation app. */
internal enum class Flow(val color: Int) {
    FLUID(0xFF2FBF71.toInt()), SLOW(0xFFFF9F1A.toInt()), JAM(0xFFE5262E.toInt())
}

/**
 * The traffic level, taken only from Google Maps: the colour it gives its time left in its guidance notification
 * (green, orange, red). Incar Drive makes no estimate of its own.
 */
internal object Traffic {
    /** From a text colour of the navigation app: its green, orange or red; null for any other colour. */
    fun ofColor(color: Int): Flow? {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[1] < .35f || hsv[2] < .35f) return null
        return when (hsv[0]) {
            in 0f..18f, in 340f..360f -> Flow.JAM
            in 18f..50f -> Flow.SLOW
            in 80f..170f -> Flow.FLUID
            else -> null
        }
    }
}
