package com.incardrive.home

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/** Where the sun is: degrees above the horizon (negative below) and compass bearing (90 east, 270 west). */
internal data class Sun(val elevation: Double, val azimuth: Double) {
    val morning get() = azimuth < 180
}

/**
 * The sun's position from the time and the city, with NOAA's solar equations (within a fraction of a degree). It uses
 * the universal time, so a head unit whose time zone is out of date still gets the real sun.
 */
internal object SunPosition {
    fun at(instant: Instant, latitude: Double, longitude: Double): Sun {
        val utc = instant.atZone(ZoneOffset.UTC)
        val hour = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
        val gamma = 2 * Math.PI / 365 * (utc.dayOfYear - 1 + (hour - 12) / 24)
        val equationOfTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val solarMinutes = hour * 60 + equationOfTime + 4 * longitude
        val hourAngle = Math.toRadians(solarMinutes / 4 - 180)
        val lat = Math.toRadians(latitude)
        val cosZenith = (sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle)).coerceIn(-1.0, 1.0)
        val zenith = acos(cosZenith)
        val cosAzimuth = ((sin(lat) * cosZenith - sin(declination)) / (cos(lat) * sin(zenith))).coerceIn(-1.0, 1.0)
        val fromSouth = Math.toDegrees(acos(cosAzimuth))
        // Before solar noon the sun is in the east.
        val azimuth = if (hourAngle > 0) (180 + fromSouth) % 360 else (540 - fromSouth) % 360
        return Sun(90 - Math.toDegrees(zenith), azimuth)
    }
}
