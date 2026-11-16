package com.incardrive.home

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Four rising bars, [level] of them lit (0 to 4), for the mobile signal. */
internal class SignalBars(context: Context, private val on: Int, private val off: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = RectF()
    var level = 0
        set(value) { field = value.coerceIn(0, 4); invalidate() }

    override fun onDraw(canvas: Canvas) {
        val gap = width * .08f
        val w = (width - gap * 3) / 4
        for (i in 0 until 4) {
            val h = height * (i + 1) / 4f
            bar.set(i * (w + gap), height - h, i * (w + gap) + w, height.toFloat())
            paint.color = if (i < level) on else off
            canvas.drawRoundRect(bar, w / 3, w / 3, paint)
        }
    }
}

/**
 * Wi-Fi, mobile data and Bluetooth in the top bar, since Incar Drive hides Android's status bar to use the whole
 * screen. Wi-Fi shows its strength, the mobile signal its bars and network (4G, 5G… once the driver allows phone
 * state; « 4G » needs it on Android), Bluetooth whether it is on.
 */
internal class StatusIcons(private val home: HomeActivity) {
    private lateinit var wifi: ImageView
    private lateinit var mobile: LinearLayout
    private lateinit var bars: SignalBars
    private lateinit var network: TextView
    private lateinit var bluetooth: ImageView

    fun view(): View = with(home) {
        row().apply {
            wifi = ImageView(context).apply {
                setImageResource(R.drawable.ic_home_wifi)
                imageTintList = ColorStateList.valueOf(text)
            }
            addView(wifi, LinearLayout.LayoutParams(dp(15), dp(15)))
            mobile = row().apply {
                bars = SignalBars(context, text, tint(text, 0x40))
                addView(bars, LinearLayout.LayoutParams(dp(14), dp(12)))
                network = label("", 10, text, 700).apply { letterSpacing = .04f }
                addView(network, wrap().apply { marginStart = dp(3) })
                setOnClickListener {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), HomeActivity.REQUEST_PHONE_STATE)
                    }
                }
            }
            addView(mobile, wrap().apply { marginStart = dp(10) })
            bluetooth = ImageView(context).apply {
                setImageResource(R.drawable.ic_home_bluetooth)
                imageTintList = ColorStateList.valueOf(text)
            }
            addView(bluetooth, LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginStart = dp(8) })
            render()
        }
    }

    fun render() {
        if (!::wifi.isInitialized) return
        val connectivity = home.getSystemService(ConnectivityManager::class.java)
        val active = runCatching { connectivity?.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
        // Wi-Fi: shown when switched on, brighter with a stronger signal.
        val wifiManager = home.applicationContext.getSystemService(WifiManager::class.java)
        val wifiOn = runCatching { wifiManager?.isWifiEnabled }.getOrNull() == true
        val onWifi = active?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        wifi.visibility = if (wifiOn) View.VISIBLE else View.GONE
        wifi.alpha = if (!onWifi) .35f else {
            val rssi = active?.signalStrength ?: Int.MIN_VALUE
            val level = if (rssi == Int.MIN_VALUE) 4 else runCatching { wifiManager?.calculateSignalLevel(rssi) }.getOrNull() ?: 4
            .55f + (level.coerceIn(0, 4) / 4f) * .45f
        }
        // Mobile: the bars when there is a SIM network, with its generation when Android tells it.
        val telephony = home.getSystemService(TelephonyManager::class.java)
        val hasSim = runCatching { telephony?.simState == TelephonyManager.SIM_STATE_READY }.getOrNull() == true
        mobile.visibility = if (hasSim) View.VISIBLE else View.GONE
        if (hasSim) {
            bars.level = runCatching { telephony?.signalStrength?.level }.getOrNull() ?: 0
            network.text = generation(telephony)
            mobile.alpha = if (active?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true || onWifi) 1f else .5f
        }
        // Bluetooth: shown when on.
        val btOn = runCatching { home.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled }.getOrNull() == true
        bluetooth.visibility = if (btOn) View.VISIBLE else View.GONE
    }

    private fun generation(telephony: TelephonyManager?): String {
        if (home.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return ""
        val type = runCatching { telephony?.dataNetworkType }.getOrNull() ?: return ""
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA -> "H+"
            TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "E"
            TelephonyManager.NETWORK_TYPE_GPRS -> "G"
            else -> ""
        }
    }
}
