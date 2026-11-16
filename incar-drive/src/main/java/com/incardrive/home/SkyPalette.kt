package com.incardrive.home

import com.autodrive.radiomaroc.WeatherCondition
import java.time.Month

/** The three background colours, top to bottom, and the low sun glow (transparent at night). */
internal data class Sky(val top: Int, val mid: Int, val bottom: Int, val glow: Int)

internal enum class Season { SPRING, SUMMER, AUTUMN, WINTER;
    companion object {
        // Morocco is in the northern hemisphere: meteorological seasons.
        fun of(month: Month) = when (month) {
            Month.MARCH, Month.APRIL, Month.MAY -> SPRING
            Month.JUNE, Month.JULY, Month.AUGUST -> SUMMER
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> AUTUMN
            else -> WINTER
        }
    }
}

/**
 * The background of the home screen follows the season and the weather outside. Each season has its own clear sky
 * (fresh green below in spring, a hot sand in summer, copper in autumn, cool silver in winter; by night teal,
 * indigo, midnight blue and icy blue), and clouds, rain or fog then grey it in proportion.
 */
internal object SkyPalette {
    private fun sky(top: Long, mid: Long, bottom: Long, glow: Long = 0) = Sky(top.toInt(), mid.toInt(), bottom.toInt(), glow.toInt())

    private val clearDay = mapOf(
        Season.SPRING to sky(0xFFC9E4FF, 0xFFF4F8F6, 0xFFDDEFD3, 0x4DFFD27A),
        Season.SUMMER to sky(0xFFB9D9FF, 0xFFF6F8FB, 0xFFFFE0B2, 0x66FFB347),
        Season.AUTUMN to sky(0xFFD3E1F2, 0xFFF5F3EF, 0xFFF2D3B3, 0x59F29A4E),
        Season.WINTER to sky(0xFFD8E4F3, 0xFFF5F8FC, 0xFFE3EAF3, 0x33FFE2A8),
    )
    private val clearNight = mapOf(
        Season.SPRING to sky(0xFF0A0E15, 0xFF0B1726, 0xFF0F2A3F),
        Season.SUMMER to sky(0xFF0A0D16, 0xFF0E1430, 0xFF1A1F52),
        Season.AUTUMN to sky(0xFF0A0D14, 0xFF0C1529, 0xFF0F2147),
        Season.WINTER to sky(0xFF080C14, 0xFF0A1A30, 0xFF0D2D57),
    )
    private val cloudsDay = sky(0xFFD2D8E1, 0xFFEEF1F4, 0xFFDCE0E6)
    private val rainDay = sky(0xFFC3CFDC, 0xFFE7ECF2, 0xFFCCD7E2)
    private val stormDay = sky(0xFFB4BCC9, 0xFFDCE0E7, 0xFFC2C8D2)
    private val snowDay = sky(0xFFE4EDF8, 0xFFFFFFFF, 0xFFE9F1FB)
    private val cloudsNight = sky(0xFF0B0E13, 0xFF11161E, 0xFF1A212C)
    private val rainNight = sky(0xFF090D13, 0xFF0D1620, 0xFF11283A)
    private val stormNight = sky(0xFF08090D, 0xFF0F1118, 0xFF1A1C28)
    private val snowNight = sky(0xFF0B111A, 0xFF13202E, 0xFF1F3248)

    fun of(condition: WeatherCondition?, month: Month, night: Boolean): Sky {
        val clear = (if (night) clearNight else clearDay).getValue(Season.of(month))
        val (weather, amount) = when (condition) {
            WeatherCondition.PARTLY_CLOUDY -> (if (night) cloudsNight else cloudsDay) to .35f
            WeatherCondition.CLOUDY -> (if (night) cloudsNight else cloudsDay) to .75f
            WeatherCondition.FOG -> (if (night) cloudsNight else cloudsDay) to .85f
            WeatherCondition.DRIZZLE -> (if (night) rainNight else rainDay) to .6f
            WeatherCondition.SHOWERS -> (if (night) rainNight else rainDay) to .7f
            WeatherCondition.RAIN -> (if (night) rainNight else rainDay) to .85f
            WeatherCondition.STORM -> (if (night) stormNight else stormDay) to 1f
            WeatherCondition.SNOW -> (if (night) snowNight else snowDay) to 1f
            WeatherCondition.CLEAR, WeatherCondition.UNKNOWN, null -> return clear
        }
        return Sky(mix(clear.top, weather.top, amount), mix(clear.mid, weather.mid, amount),
            mix(clear.bottom, weather.bottom, amount), fade(clear.glow, 1 - amount))
    }

    fun mix(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int) = ((from ushr shift and 0xFF) * (1 - amount) + (to ushr shift and 0xFF) * amount).toInt()
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun fade(color: Int, keep: Float) = ((color ushr 24) * keep).toInt() shl 24 or (color and 0xFFFFFF)
}
