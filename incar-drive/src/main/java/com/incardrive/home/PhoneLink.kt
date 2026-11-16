package com.incardrive.home

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * The phone card of the second page: which phone is connected over Bluetooth, the starred contacts and the latest
 * calls that Android knows, and the Incarlink's own phone app. Android exposes no public way to read a connected
 * phone's battery, so the card does not show it. On the head unit, contacts and calls may live only inside the
 * Bluetooth phone app; the card then says so and opens that app.
 */
internal class PhoneLink(private val home: HomeActivity) {
    private val adapter: BluetoothAdapter? = runCatching { home.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
    private val connected = linkedMapOf<String, String>()
    private val proxies = mutableListOf<Pair<Int, BluetoothProfile>>()
    private var listening = false
    private lateinit var deviceName: TextView
    private lateinit var deviceStatus: TextView
    private lateinit var list: LinearLayout

    private data class Entry(val name: String, val number: String, val detail: String)

    fun bluetoothPermitted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        home.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun contactsPermitted() =
        home.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            home.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> device?.takeIf { it.isPhone() }?.let { connected[it.address] = it.label() }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> device?.let { connected.remove(it.address) }
                BluetoothAdapter.ACTION_STATE_CHANGED -> if (adapter?.isEnabled != true) connected.clear()
            }
            render()
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.label(): String = runCatching { name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Téléphone"

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.isPhone(): Boolean = runCatching {
        bluetoothClass?.majorDeviceClass?.let { it == android.bluetooth.BluetoothClass.Device.Major.PHONE } ?: true
    }.getOrDefault(true)

    private val profiles = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            proxies += profile to proxy
            if (bluetoothPermitted()) runCatching {
                proxy.connectedDevices.filter { it.isPhone() }.forEach { connected[it.address] = it.label() }
            }
            render()
        }

        override fun onServiceDisconnected(profile: Int) = Unit
    }

    fun start() {
        if (listening || !bluetoothPermitted() || adapter == null) return
        listening = true
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // System broadcasts only; nothing else may reach this receiver.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) home.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else home.registerReceiver(receiver, filter)
        // A phone streaming or calling through this unit shows up on one of these documented profiles.
        listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP).forEach { profile ->
            runCatching { adapter?.getProfileProxy(home, profiles, profile) }
        }
    }

    fun stop() {
        if (!listening) return
        listening = false
        runCatching { home.unregisterReceiver(receiver) }
        proxies.forEach { (profile, proxy) -> runCatching { adapter?.closeProfileProxy(profile, proxy) } }
        proxies.clear()
    }

    fun view(): View = with(home) {
        column().apply {
            background = glass()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(caps("Téléphone"))
            addView(row().apply {
                addView(FrameLayout(context).apply {
                    background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(getColor(R.color.home_chip)) }
                    addView(ImageView(context).apply {
                        setImageResource(R.drawable.ic_home_bluetooth)
                        imageTintList = ColorStateList.valueOf(text)
                    }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
                }, LinearLayout.LayoutParams(dp(42), dp(42)))
                addView(column().apply {
                    deviceName = label("", 17, text, 700, condensed = true)
                    deviceStatus = label("", 12, muted, 500).apply { maxLines = 2 }
                    addView(deviceName)
                    addView(deviceStatus, wrap().apply { topMargin = dp(2) })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            list = column()
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
            addView(label("Clavier et numéros", 15, onAccent, 700).apply {
                gravity = Gravity.CENTER
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(accent)
                }, 12)
                setOnClickListener { dialer.show() }
            }, LinearLayout.LayoutParams(-1, dp(44)))
            render()
        }
    }

    fun render() {
        if (!::deviceName.isInitialized) return
        val enabled = runCatching { adapter?.isEnabled }.getOrNull() == true
        when {
            adapter == null -> { deviceName.text = "Bluetooth absent"; deviceStatus.text = "Cet appareil n’a pas de Bluetooth" }
            !bluetoothPermitted() -> { deviceName.text = "Téléphone"; deviceStatus.text = "Touche pour autoriser l’accès Bluetooth" }
            !enabled -> { deviceName.text = "Bluetooth désactivé"; deviceStatus.text = "Active-le dans les réglages rapides" }
            connected.isEmpty() -> { deviceName.text = "Aucun téléphone"; deviceStatus.text = "Connecte ton téléphone en Bluetooth" }
            else -> { deviceName.text = connected.values.last(); deviceStatus.text = "Connecté en Bluetooth" }
        }
        (deviceName.parent as? View)?.setOnClickListener(if (!bluetoothPermitted()) View.OnClickListener {
            home.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), HomeActivity.REQUEST_BLUETOOTH)
        } else null)
        renderList()
    }

    private fun renderList() = with(home) {
        list.removeAllViews()
        val saved = dialer.saved().map { Entry(it.name, it.number, "Enregistré") }
        if (!contactsPermitted()) {
            saved.take(2).forEach { addEntry(it) }
            list.addView(label("Afficher favoris et appels récents  ›", 13, text, 600).apply {
                maxLines = 2
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener {
                    requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG),
                        HomeActivity.REQUEST_CONTACTS)
                }
            })
            return@with
        }
        val entries = (saved + favourites() + recents()).distinctBy { it.number }.take(3)
        if (entries.isEmpty()) {
            // Nothing saved here and nothing Android knows.
            list.addView(label("Les contacts et appels sont dans l’appli Téléphone de l’Incarlink.", 12, muted).apply {
                maxLines = 3
                setPadding(0, dp(6), 0, 0)
            })
            return@with
        }
        entries.forEach(::addEntry)
    }

    private fun addEntry(entry: Entry) = with(home) {
            list.addView(row().apply {
                background = pressable(null, 10)
                setPadding(dp(4), 0, dp(4), 0)
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_home_phone)
                    imageTintList = ColorStateList.valueOf(accent)
                }, LinearLayout.LayoutParams(dp(18), dp(18)))
                addView(column().apply {
                    addView(label(entry.name, 14, text, 700))
                    addView(label(entry.detail, 11, muted), wrap().apply { topMargin = dp(1) })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
                contentDescription = "Appeler ${entry.name}"
                setOnClickListener { dialer.call(entry.number) }
            }, LinearLayout.LayoutParams(-1, dp(42)))
    }

    private fun favourites(): List<Entry> = runCatching {
        home.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1", null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < 3) {
                    val number = cursor.getString(1) ?: continue
                    add(Entry(cursor.getString(0) ?: number, number, "Favori"))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun recents(): List<Entry> = runCatching {
        home.contentResolver.query(CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE), null, null,
            "${CallLog.Calls.DATE} DESC")?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < 3) {
                    val number = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    val kind = when (cursor.getInt(2)) {
                        CallLog.Calls.INCOMING_TYPE -> "Appel reçu"
                        CallLog.Calls.OUTGOING_TYPE -> "Appel passé"
                        CallLog.Calls.MISSED_TYPE -> "Appel manqué"
                        else -> "Appel"
                    }
                    add(Entry(cursor.getString(0)?.takeIf { it.isNotBlank() } ?: number, number, kind))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}
