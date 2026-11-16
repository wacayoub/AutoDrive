package com.incardrive.home

import com.autodrive.radiomaroc.WeatherCondition
import java.time.Month

/**
 * The three background colours, top to bottom, the sun's glow (transparent when it is hidden) and where the sun
 * stands on the screen, as fractions of its width and height: east on the left, west on the right, high at the top.
 */
internal data class Sky(
    val top: Int, val mid: Int, val bottom: Int, val glow: Int,
    val sunX: Float = .12f, val sunY: Float = .95f, val sunSize: Float = .55f,
)

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
 * indigo, midnight blue and icy blue). The sun then sets the hour: a full sky by day, a warm golden light as it gets
 * low, pink and lavender at dawn, orange and violet at dusk, and the night sky once it is well below the horizon, with
 * its glow moving from east to west across the screen. Clouds, rain or fog finally grey it all in proportion.
 */
internal object SkyPalette {
    private fun sky(top: Long, mid: Long, bottom: Long, glow: Long = 0) = Sky(top.toInt(), mid.toInt(), bottom.toInt(), glow.toInt())

    private val clearDay = mapOf(
        Season.SPRING to sky(0xFFC9E4FF, 0xFFF4F8F6, 0xFFDDEFD3, 0x4DFFD27A),
        Season.SUMMER to sky(0xFFB9D9FF, 0xFFF6F8FB, 0xFFFFE0B2, 0x66FFB347),
        Season.AUTUMN to sky(0xFFD3E1F2, 0xFFF5F3EF, 0xFFF2D3B3, 0x40F29A4E),
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

    // Low sun, dawn and dusk, for the light (Jour) screen.
    private val golden = sky(0xFFCBD6F2, 0xFFFBE6D4, 0xFFFFC796, 0x99FF8A3D)
    private val dawn = sky(0xFFB9C4E8, 0xFFF6DCE6, 0xFFFFD0B0, 0x66FF9E80)
    private val dusk = sky(0xFFAAA7DA, 0xFFF2CFE0, 0xFFFFB98F, 0x66FF7A59)
    // The horizon still lit just before sunrise and after sunset, for the dark (Nuit) screen.
    private val nightDawn = sky(0xFF0E1530, 0xFF2A2550, 0xFF6A3A4A, 0x40FF9E80)
    private val nightDusk = sky(0xFF101536, 0xFF33244F, 0xFF7A3B2E, 0x40FF7A59)
    // The light screen after dark: a calm lavender blue hour rather than a sunset that never ends.
    private val blueHour = sky(0xFFA9B3D9, 0xFFDCE0F0, 0xFFC9CCE6)
    // The pale, small midday sun.
    private const val HIGH_SUN = 0x4DFFF4D6

    private fun blend(from: Sky, to: Sky, amount: Float) = Sky(mix(from.top, to.top, amount), mix(from.mid, to.mid, amount),
        mix(from.bottom, to.bottom, amount), mix(from.glow, to.glow, amount))

    /** The clear sky for this season at this hour of the sun. */
    private fun clearSky(month: Month, night: Boolean, sun: Sun?): Sky {
        val season = (if (night) clearNight else clearDay).getValue(Season.of(month))
        if (sun == null) return season
        val e = sun.elevation.toFloat()
        val sky = if (!night) {
            val twilight = if (sun.morning) dawn else dusk
            when {
                // High summer sun: a deeper blue overhead.
                e >= 40 -> season.copy(top = mix(season.top, 0xFFA9CFFF.toInt(), ((e - 40) / 40).coerceAtMost(1f) * .35f))
                e >= 12 -> season
                e >= 0 -> blend(season, golden, (12 - e) / 12)
                e >= -6 -> blend(golden, twilight, -e / 6)
                e >= -12 -> blend(twilight, blueHour, (-6 - e) / 6)
                else -> blueHour
            }
        } else {
            val twilight = if (sun.morning) nightDawn else nightDusk
            // The dark screen stays a night sky; the horizon only warms around sunrise and sunset.
            when {
                e <= -12 -> season
                e <= -3 -> blend(season, twilight, (e + 12) / 9 * .8f)
                e <= 6 -> blend(season, twilight, .8f - (e + 3) / 9 * .5f)
                else -> blend(season, twilight, .3f)
            }
        }
        // The glow sits where the sun is: from the east (left) to the west (right), higher as it climbs; just under
        // the bottom edge while it is below the horizon. Pale at noon, orange when low.
        val x = ((sun.azimuth - 60) / 240).toFloat().coerceIn(.04f, .96f)
        val y = if (e <= 0) 1.04f else (1f - (e / 70f).coerceAtMost(1f) * .9f)
        val glow = when {
            night && e <= -12 -> 0
            !night && e < -12 -> 0
            !night && e >= 8 -> mix(sky.glow, HIGH_SUN, ((e - 8) / 20).coerceIn(0f, 1f))
            else -> sky.glow
        }
        // Large and warm near the horizon, small once high.
        val size = .6f - ((e - 5) / 30).coerceIn(0f, 1f) * .28f
        return sky.copy(glow = glow, sunX = x, sunY = y, sunSize = size)
    }

    fun of(condition: WeatherCondition?, month: Month, night: Boolean, sun: Sun? = null): Sky {
        val clear = clearSky(month, night, sun)
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
            mix(clear.bottom, weather.bottom, amount), fade(clear.glow, 1 - amount), clear.sunX, clear.sunY, clear.sunSize)
    }

    fun mix(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int) = ((from ushr shift and 0xFF) * (1 - amount) + (to ushr shift and 0xFF) * amount).toInt()
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun fade(color: Int, keep: Float) = ((color ushr 24) * keep).toInt() shl 24 or (color and 0xFFFFFF)
}
