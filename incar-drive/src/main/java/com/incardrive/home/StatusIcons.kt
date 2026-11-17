package com.incardrive.home

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.Inet4Address
import java.net.NetworkInterface

/** Four rising bars, [level] of them lit (0 to 4), for the mobile signal. */
internal class SignalBars(context: Context, private val on: Int, private val off: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = RectF()
    var level = 0
        set(value) { field = value.coerceIn(0, 4); invalidate() }

    override fun onDraw(canvas: Canvas) {
        val gap = width * .1f
        val w = (width - gap * 3) / 4
        for (i in 0 until 4) {
            val h = height * (.34f + .22f * i)
            bar.set(i * (w + gap), height - h, i * (w + gap) + w, height.toFloat())
            paint.color = if (i < level) on else off
            canvas.drawRoundRect(bar, w / 2, w / 2, paint)
        }
    }
}

/** The Wi-Fi fan: a dot and three arcs, [level] of them lit (0 to 3), the rest faint. */
internal class WifiFan(context: Context, private val on: Int, private val off: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val box = RectF()
    var level = 3
        set(value) { field = value.coerceIn(0, 3); invalidate() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val base = height * .88f
        val stroke = height * .13f
        paint.style = Paint.Style.FILL
        paint.color = on
        canvas.drawCircle(cx, base - stroke * .2f, stroke * .75f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        for (i in 1..3) {
            val r = height * (.06f + .25f * i)
            box.set(cx - r, base - r, cx + r, base + r)
            paint.color = if (i <= level) on else off
            canvas.drawArc(box, 225f, 90f, false, paint)
        }
    }
}

/**
 * Wi-Fi, mobile data, hotspot and Bluetooth in the top bar, since Incar Drive hides Android's status bar to use the
 * whole screen. Wi-Fi shows its strength as a fan and its generation (Wi-Fi 6, 6E, 7) when the head unit reports it;
 * the mobile signal shows its bars and its network in a small badge (5G, 5G+, 4G+, 4G…), which needs the phone-state
 * permission; the hotspot icon shows while the head unit shares its connection; Bluetooth while it is on.
 */
internal class StatusIcons(private val home: HomeActivity) {
    private lateinit var wifi: LinearLayout
    private lateinit var fan: WifiFan
    private lateinit var wifiGeneration: TextView
    private lateinit var mobile: LinearLayout
    private lateinit var bars: SignalBars
    private lateinit var network: TextView
    private lateinit var hotspot: ImageView
    private lateinit var bluetooth: ImageView
    // What the network shows on its own status bar for the mobile data (5G on an LTE anchor, carrier aggregation…).
    private var override = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
    private var listening: Any? = null
    @Volatile private var hotspotOn = false

    fun view(): View = with(home) {
        row().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            wifi = row().apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                fan = WifiFan(context, text, tint(text, 0x40))
                addView(fan, LinearLayout.LayoutParams(dp(17), dp(14)))
                // The Wi-Fi's generation in a badge, like the mobile network's.
                wifiGeneration = label("", 10, onAccent, 700).apply {
                    includeFontPadding = false
                    setPadding(dp(4), dp(1), dp(4), dp(1))
                    background = GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(accent) }
                }
                addView(wifiGeneration, wrap().apply { marginStart = dp(4) })
                setOnClickListener { startSafely(Intent(Settings.Panel.ACTION_WIFI)) }
            }
            addView(wifi, wrap())
            mobile = row().apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                bars = SignalBars(context, text, tint(text, 0x40))
                addView(bars, LinearLayout.LayoutParams(dp(15), dp(13)))
                network = label("", 10, onAccent, 700).apply {
                    letterSpacing = .03f
                    includeFontPadding = false
                    setPadding(dp(4), dp(1), dp(4), dp(1))
                    background = GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(accent) }
                }
                addView(network, wrap().apply { marginStart = dp(4) })
                setOnClickListener {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), HomeActivity.REQUEST_PHONE_STATE)
                    }
                }
            }
            addView(mobile, wrap().apply { marginStart = dp(10) })
            hotspot = ImageView(context).apply {
                setImageResource(R.drawable.ic_home_hotspot)
                imageTintList = ColorStateList.valueOf(text)
                contentDescription = "Partage de connexion actif"
                visibility = View.GONE
                setOnClickListener { startSafely(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            }
            addView(hotspot, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(8) })
            bluetooth = ImageView(context).apply {
                setImageResource(R.drawable.ic_home_bluetooth)
                imageTintList = ColorStateList.valueOf(text)
            }
            addView(bluetooth, LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginStart = dp(8) })
            render()
        }
    }

    /** Follows what the network displays for the mobile data, while the home screen is shown. */
    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || listening != null) return
        if (home.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val telephony = home.getSystemService(TelephonyManager::class.java) ?: return
        val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                override = info.overrideNetworkType
                render()
            }
        }
        runCatching { telephony.registerTelephonyCallback(home.mainExecutor, callback) }.onSuccess { listening = callback }
    }

    fun stop() {
        // The callback class only exists from Android 12: nothing was registered before it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val callback = listening as? TelephonyCallback ?: return
        listening = null
        runCatching { home.getSystemService(TelephonyManager::class.java)?.unregisterTelephonyCallback(callback) }
    }

    fun render() {
        if (!::wifi.isInitialized) return
        if (home.hasWindowFocus()) start() // once the phone-state permission is given
        val connectivity = home.getSystemService(ConnectivityManager::class.java)
        val active = runCatching { connectivity?.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
        // Wi-Fi: shown when switched on; its fan fills with the signal, and its generation follows when known.
        val wifiManager = home.applicationContext.getSystemService(WifiManager::class.java)
        val wifiOn = runCatching { wifiManager?.isWifiEnabled }.getOrNull() == true
        val onWifi = active?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        wifi.visibility = if (wifiOn) View.VISIBLE else View.GONE
        wifi.alpha = if (onWifi) 1f else .4f
        val rssi = active?.signalStrength ?: Int.MIN_VALUE
        val bars4 = if (!onWifi) 0 else if (rssi == Int.MIN_VALUE) 4
            else runCatching { wifiManager?.calculateSignalLevel(rssi) }.getOrNull() ?: 4
        fan.level = if (!onWifi) 0 else (bars4.coerceIn(0, 4) * 3 + 2) / 4
        // The connection's own description first; the Wi-Fi service's when that one is not given to apps.
        @Suppress("DEPRECATION")
        val info = if (!onWifi) null else (active?.transportInfo as? WifiInfo)
            ?: runCatching { wifiManager?.connectionInfo }.getOrNull()
        val standard = info?.let {
            when (runCatching { it.wifiStandard }.getOrNull()) {
                ScanResult.WIFI_STANDARD_11BE -> "7"
                ScanResult.WIFI_STANDARD_11AX -> if (it.frequency >= 5925) "6E" else "6"
                ScanResult.WIFI_STANDARD_11AC -> "5"
                ScanResult.WIFI_STANDARD_11N -> "4"
                else -> null
            }
        }
        wifiGeneration.text = standard.orEmpty()
        wifiGeneration.visibility = if (standard == null) View.GONE else View.VISIBLE
        // Mobile: the bars when there is a SIM network, with its generation when Android tells it.
        val telephony = home.getSystemService(TelephonyManager::class.java)
        val hasSim = runCatching { telephony?.simState == TelephonyManager.SIM_STATE_READY }.getOrNull() == true
        mobile.visibility = if (hasSim) View.VISIBLE else View.GONE
        if (hasSim) {
            bars.level = runCatching { telephony?.signalStrength?.level }.getOrNull() ?: 0
            val name = generation(telephony)
            network.text = name
            network.visibility = if (name.isEmpty()) View.GONE else View.VISIBLE
            mobile.alpha = if (active?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true || onWifi) 1f else .5f
        }
        // Hotspot: looked up off the main thread, shown on the next pass.
        hotspot.visibility = if (hotspotOn) View.VISIBLE else View.GONE
        Thread {
            val now = sharing()
            if (now != hotspotOn) {
                hotspotOn = now
                home.runOnUiThread { if (::hotspot.isInitialized) hotspot.visibility = if (now) View.VISIBLE else View.GONE }
            }
        }.start()
        // Debug builds only: « debug_status » previews Wi-Fi 6, 5G and the hotspot icon where the device has none.
        if (home.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && home.prefs.contains("debug_status")) {
            wifiGeneration.text = "6"
            wifiGeneration.visibility = View.VISIBLE
            network.text = "5G"
            network.visibility = View.VISIBLE
            mobile.visibility = View.VISIBLE
            hotspot.visibility = View.VISIBLE
        }
        // Bluetooth: shown when on.
        val btOn = runCatching { home.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled }.getOrNull() == true
        bluetooth.visibility = if (btOn) View.VISIBLE else View.GONE
    }

    /** Whether the head unit shares its connection as a Wi-Fi hotspot, for the quick settings too. */
    val sharingNow get() = hotspotOn

    /**
     * Android has no public call that says « the hotspot is on »; it does list the network interfaces. A hotspot
     * shows as an interface of its own (ap0, swlan0, softap0, wlan1…) that is up, or as the address Android gives a
     * hotspot (192.168.43.1 and the like, ending in .1 on a wireless interface).
     */
    private fun sharing(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { net ->
            if (!net.isUp || net.isLoopback) return@any false
            val name = net.name.lowercase()
            val own = name.inetAddresses(net).any { it.endsWith(".1") && it.startsWith("192.168.") }
            HOTSPOT_NAMES.containsMatchIn(name) && name.inetAddresses(net).isNotEmpty() || (name.startsWith("wlan") && own)
        }
    }.getOrDefault(false)

    private fun String.inetAddresses(net: NetworkInterface): List<String> =
        net.inetAddresses.toList().filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }

    private fun generation(telephony: TelephonyManager?): String {
        if (home.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return ""
        // What the network itself displays comes first: 5G on an LTE anchor reads as LTE below.
        when (override) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> return "5G+"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> return "5G"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> return "4G+"
        }
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

    private companion object {
        val HOTSPOT_NAMES = Regex("""^(ap\d|swlan\d|softap\d|wlan[1-9]|wl\d+\.\d|wifi_ap)""")
    }
}
