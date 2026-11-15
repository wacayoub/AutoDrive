package com.autodrive.radiomaroc

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Whether Android itself is dark right now, for the « Système » appearance.
 *
 * Asking Android to make this app follow the system (setApplicationNightMode(MODE_NIGHT_AUTO)) is not applied the same
 * way on every phone: on some the radio stayed light after the phone turned dark. So the app reads the system's own
 * dark theme setting, which no per-app override changes, and works out the answer itself.
 */
internal object SystemNight {
    // Casablanca: sunset and sunrise for the « sunset to sunrise » schedule when the phone does not say more.
    private const val LATITUDE = 33.57
    private const val LONGITUDE = -7.59

    fun isNight(context: Context, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        val manager = context.getSystemService(UiModeManager::class.java)
            ?: return configNight(context)
        return when (manager.nightMode) {
            UiModeManager.MODE_NIGHT_YES -> true
            UiModeManager.MODE_NIGHT_NO -> false
            UiModeManager.MODE_NIGHT_CUSTOM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                inWindow(now.toLocalTime(), manager.customNightModeStart, manager.customNightModeEnd)
            } else configNight(context)
            else -> !isDaylight(now)
        }
    }

    /** A night window may run past midnight, for example 22:00 to 07:00. */
    fun inWindow(time: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start <= end) time >= start && time < end else time >= start || time < end

    /** Between sunrise and sunset at [latitude], [longitude], within a few minutes (standard solar approximation). */
    fun isDaylight(now: ZonedDateTime, latitude: Double = LATITUDE, longitude: Double = LONGITUDE): Boolean {
        val utc = now.withZoneSameInstant(ZoneOffset.UTC)
        val day = utc.dayOfYear
        val b = 2 * PI / 365 * (day - 81)
        val declination = Math.toRadians(23.44) * sin(b)
        val equationOfTime = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        val lat = Math.toRadians(latitude)
        val cosHour = (sin(Math.toRadians(-0.833)) - sin(lat) * sin(declination)) / (cos(lat) * cos(declination))
        if (cosHour <= -1) return true
        if (cosHour >= 1) return false
        val halfDay = Math.toDegrees(acos(cosHour)) / 15
        val noon = 12 - longitude / 15 - equationOfTime / 60
        val hour = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
        return hour >= noon - halfDay && hour < noon + halfDay
    }

    private fun configNight(context: Context) =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
