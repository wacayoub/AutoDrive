package com.autodrive.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.DateFormat
import java.util.Date

internal data class DeviceStatus(
    val wifi: Boolean = false,
    val bluetooth: Boolean? = null,
    val network: Boolean = false,
    val clock: String = ""
)

@SuppressLint("MissingPermission")
internal fun deviceStatus(context: Context): DeviceStatus {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val active = manager.getNetworkCapabilities(manager.activeNetwork)
    val wifi = context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    val bluetooth = when {
        adapter == null -> false
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED ->
            runCatching { adapter.isEnabled }.getOrNull()
        else -> null
    }
    return DeviceStatus(
        wifi, bluetooth,
        active?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
        DateFormat.getTimeFormat(context).format(Date())
    )
}
