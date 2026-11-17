package com.incardrive.home

import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar

/**
 * Brightness and media volume sliders, and the state of Wi-Fi, Bluetooth and location with a tap to the system
 * panel that changes them. Apps may not switch Wi-Fi or Bluetooth themselves since Android 10 and 13, so those
 * tiles open Android's own panels. Brightness needs the « modify system settings » permission, asked on first use.
 */
internal class QuickSettings(private val home: HomeActivity) {
    private val audio = home.getSystemService(AudioManager::class.java)

    fun show() = with(home) {
        openPanel(HomeActivity.Page.QUICK, column().apply {
            background = sheet()
            setPadding(dp(18), dp(12), dp(18), dp(14))
            addView(panelHeader("Réglages rapides", null))
            addView(slider(R.drawable.ic_home_brightness, "Luminosité", brightness(), 255) { value ->
                if (Settings.System.canWrite(home)) {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceAtLeast(8))
                    true
                } else {
                    startSafely(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                    false
                }
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
            val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
            addView(slider(R.drawable.ic_home_volume, "Volume", audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0, max) { value ->
                runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0) }.isSuccess
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
            addView(row().apply {
                tile(R.drawable.ic_home_wifi, "Wi-Fi", wifi()) { startSafely(Intent(Settings.Panel.ACTION_WIFI)) }
                tile(R.drawable.ic_home_bluetooth, "Bluetooth", bluetooth()) { startSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                tile(R.drawable.ic_home_hotspot, "Partage", if (sharingConnection) "Activé" else "Désactivé") {
                    startSafely(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }
                tile(R.drawable.ic_home_location, "Position", location()) { startSafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                tile(R.drawable.ic_home_moon, if (night) "Mode jour" else "Mode nuit", "Thème") { toggleNight() }
                tile(R.drawable.ic_home_quiet, "Calme", "Écran noir") { enterQuiet() }
                tile(R.drawable.ic_home_mic, "Assistant", "Voix") { openAssistant() }
                tile(R.drawable.ic_home_settings, "Incarlink", "Réglages", last = true) { openOemSettings() }
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        })
    }

    private fun brightness() = runCatching {
        Settings.System.getInt(home.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)

    private fun wifi(): String {
        val manager = home.applicationContext.getSystemService(WifiManager::class.java)
        val network = home.getSystemService(ConnectivityManager::class.java)?.let { it.getNetworkCapabilities(it.activeNetwork) }
        return when {
            network?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Connecté"
            manager?.isWifiEnabled == true -> "Activé"
            else -> "Désactivé"
        }
    }

    private fun bluetooth(): String {
        val enabled = runCatching { home.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled }.getOrNull()
        return when (enabled) {
            true -> "Activé"
            false -> "Désactivé"
            null -> "Indisponible"
        }
    }

    private fun location() = if (home.getSystemService(LocationManager::class.java)?.isLocationEnabled == true) "Activée" else "Désactivée"

    private fun slider(icon: Int, title: String, value: Int, max: Int, change: (Int) -> Boolean) = with(home) {
        row().apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(getColor(R.color.home_chip))
                setStroke(dp(1), getColor(R.color.home_card_border))
            }
            setPadding(dp(14), 0, dp(6), 0)
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(text)
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(label(title, 14, text, 700), LinearLayout.LayoutParams(dp(92), -2).apply { marginStart = dp(12) })
            addView(SeekBar(context).apply {
                this.max = max
                progress = value
                progressTintList = ColorStateList.valueOf(accent)
                thumbTintList = ColorStateList.valueOf(accent)
                progressBackgroundTintList = ColorStateList.valueOf(muted)
                contentDescription = title
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser && !change(progress)) bar.progress = value
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar) = Unit
                })
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun LinearLayout.tile(icon: Int, title: String, state: String, last: Boolean = false, action: () -> Unit) = with(home) {
        val on = state.startsWith("Activ") || state == "Connecté"
        addView(column().apply {
            gravity = Gravity.CENTER
            background = pressable(GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (on) accent else getColor(R.color.home_chip))
                if (!on) setStroke(dp(1), getColor(R.color.home_card_border))
            }, 14)
            val ink = if (on) onAccent else text
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(ink)
            }, LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(label(title, 13, ink, 700).apply { gravity = Gravity.CENTER; maxLines = 1 },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(label(state, 11, if (on) ink else muted, 500).apply { gravity = Gravity.CENTER; maxLines = 1 },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
            contentDescription = "$title, $state"
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { if (!last) marginEnd = dp(6) })
    }
}
