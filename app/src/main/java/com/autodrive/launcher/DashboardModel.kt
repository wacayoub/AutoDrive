package com.autodrive.launcher

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DashboardState(
    val tab: MediaTab = MediaTab.YOUTUBE,
    val gps: GpsState = GpsState(),
    val device: DeviceStatus = DeviceStatus(),
    val mapsInstalled: Boolean = false,
    val mediaApp: Launchable? = null,
    val mediaAssigned: Boolean = false,
    val media: MediaState = MediaState(status = "No active media session")
)

internal class DashboardModel : ViewModel() {
    private val mutable = MutableStateFlow(DashboardState())
    val state = mutable.asStateFlow()
    fun update(value: DashboardState) { mutable.value = value }
}
