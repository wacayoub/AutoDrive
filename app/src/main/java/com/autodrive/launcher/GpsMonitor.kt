package com.autodrive.launcher

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
    val direction: String? = null
)

internal class GpsMonitor(private val context: Context, private val changed: () -> Unit) {
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
                location.speed.takeIf { location.hasSpeed() && !location.isMock },
                location.speedAccuracyMetersPerSecond.takeIf { location.hasSpeedAccuracy() },
                stamp, now
            )
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

    fun snapshot(): GpsState {
        if (!permitted()) return GpsState(status = "Precise location off")
        if (!manager.hasProvider(LocationManager.GPS_PROVIDER)) return GpsState(status = "GPS unavailable")
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return GpsState(status = "GPS disabled")
        val now = SystemClock.elapsedRealtime()
        val fix = lastFix?.takeIf { now - it.elapsedRealtimeNanos / 1_000_000 in 0..4_000 }
            ?: return GpsState(status = "Waiting for GPS")
        if (fix.isMock) return GpsState(status = "Mock location ignored")
        val speed = filter.current(now)
        val accuracy = if (fix.hasAccuracy()) " ±${fix.accuracy.roundToInt()} m" else ""
        val direction = if (speed.kmh != null && speed.kmh >= 5 && fix.hasBearing() &&
            fix.hasBearingAccuracy() && fix.bearingAccuracyDegrees <= 30
        ) {
            val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            directions[((fix.bearing / 45).roundToInt() % 8 + 8) % 8]
        } else null
        val status = if (speed.kmh == null) "GPS fix · Speed unavailable" else "GPS$accuracy"
        return GpsState(speed, status, direction)
    }
}
