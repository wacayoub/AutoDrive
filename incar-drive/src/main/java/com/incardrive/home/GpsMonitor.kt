package com.incardrive.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import kotlin.math.roundToInt

internal data class GpsState(
    val speed: SpeedReading = SpeedReading(),
    val status: String = "Waiting for GPS",
    val direction: String? = null,
    /** Heading in degrees while moving, from the GPS course. */
    val bearing: Int? = null,
    /** GPS altitude in metres; Android 13 reports it above the ellipsoid, a few tens of metres off sea level. */
    val altitude: Int? = null,
)

internal class GpsMonitor(private val context: Context, private val changed: () -> Unit) {
    /** Every genuine fix, with the filtered speed, for the trip meter. */
    var onFix: ((Location, Int?) -> Unit)? = null
    private val manager = context.getSystemService(LocationManager::class.java)
    private val filter = SpeedFilter()
    private var lastFix: Location? = null
    private var listening = false
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = SystemClock.elapsedRealtime()
            val stamp = location.elapsedRealtimeNanos / 1_000_000L
            if (now - stamp !in 0..4_000 || stamp <= (lastFix?.elapsedRealtimeNanos ?: -1) / 1_000_000L) return
            lastFix = location
            filter.accept(
                location.speed.takeIf { location.hasSpeed() && !location.mocked },
                location.speedAccuracyMetersPerSecond.takeIf { location.hasSpeedAccuracy() },
                stamp, now
            )
            if (!location.mocked) onFix?.invoke(location, filter.current(now).kmh)
            changed()
        }

        override fun onProviderDisabled(provider: String) {
            filter.reset()
            lastFix = null
            changed()
        }

        override fun onProviderEnabled(provider: String) = changed()
    }

    fun permitted() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (listening || !permitted()) return
        listening = runCatching {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
        }.isSuccess
    }

    fun stop() {
        if (listening) manager.removeUpdates(listener)
        listening = false
        filter.reset()
        lastFix = null
    }

    private fun hasGps(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) manager.hasProvider(LocationManager.GPS_PROVIDER)
        else LocationManager.GPS_PROVIDER in manager.allProviders

    fun snapshot(): GpsState {
        if (!permitted()) return GpsState(status = "Precise location off")
        if (!hasGps()) return GpsState(status = "GPS unavailable")
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return GpsState(status = "GPS disabled")
        val now = SystemClock.elapsedRealtime()
        val fix = lastFix?.takeIf { now - it.elapsedRealtimeNanos / 1_000_000 in 0..4_000 }
            ?: return GpsState(status = "Waiting for GPS")
        if (fix.mocked) return GpsState(status = "Mock location ignored")
        val speed = filter.current(now)
        val accuracy = if (fix.hasAccuracy()) " ±${fix.accuracy.roundToInt()} m" else ""
        val bearing = if (speed.kmh != null && speed.kmh >= 5 && fix.hasBearing() &&
            fix.hasBearingAccuracy() && fix.bearingAccuracyDegrees <= 30
        ) (fix.bearing.roundToInt() % 360 + 360) % 360 else null
        val direction = bearing?.let { compassPoint(it) }
        val status = if (speed.kmh == null) "GPS fix · Speed unavailable" else "GPS$accuracy"
        return GpsState(speed, status, direction, bearing, fix.altitude.roundToInt().takeIf { fix.hasAltitude() })
    }
}

// Incar Drive also runs on Android 11, before these calls existed.
@Suppress("DEPRECATION")
private val android.location.Location.mocked: Boolean
    get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) isMock else isFromMockProvider


/** The French compass point of a heading: N, NE, E, SE, S, SO, O, NO. */
internal fun compassPoint(degrees: Int): String =
    listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[((degrees / 45.0).roundToInt() % 8 + 8) % 8]
