package com.autodrive.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager

internal data class BluetoothCallAccount(val handle: PhoneAccountHandle, val label: String)
internal interface BluetoothCallTransport {
    fun accounts(): List<BluetoothCallAccount>
    fun emergency(number: String): Boolean
    fun permitted(handle: PhoneAccountHandle): Boolean
    fun place(number: String, handle: PhoneAccountHandle)
}

/** Public Telecom path only, bound to a known system HFP connection service. */
internal class BluetoothCalls(private val context: Context,
    private val transport: BluetoothCallTransport = SystemBluetoothCalls(context)) {
    private val prefs = context.getSharedPreferences("bluetooth_calls", Context.MODE_PRIVATE)
    fun authorized() = permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    fun accounts(): List<BluetoothCallAccount> = if (!authorized()) emptyList() else runCatching { transport.accounts() }.getOrDefault(emptyList())
    private fun key(value: BluetoothCallAccount) = value.handle.componentName.flattenToString() + "/" + value.handle.id
    fun hasSelection() = prefs.contains("account")
    fun clearSelection() { prefs.edit().remove("account").apply() }
    fun choose(value: BluetoothCallAccount) { prefs.edit().putString("account", key(value)).apply() }
    fun current(): BluetoothCallAccount? {
        val all = accounts()
        val selected = prefs.getString("account", null)
        return if (selected != null) all.firstOrNull { key(it) == selected } else all.singleOrNull()
    }
    /** null means submitted to Telecom, never confirmation that a call connected. */
    fun call(raw: String, expected: PhoneAccountHandle? = null): String? {
        val number = DialNumber.normalize(raw)?.takeIf { it.matches(Regex("\\+?[0-9]{3,20}")) }
            ?: return "Numéro invalide pour l’appel direct · utilise BT Phone pour les codes spéciaux"
        if (!authorized()) return "Autorise les appels dans Appels Bluetooth"
        val account = current() ?: return "Aucun compte HFP choisi disponible · ouvre Appels Bluetooth"
        if (expected != null && account.handle != expected) return "Le téléphone Bluetooth a changé · vérifie le compte choisi"
        return try {
            if (transport.emergency(number)) return "Utilise l’application téléphone pour ce numéro"
            if (!transport.permitted(account.handle)) return "Appel Bluetooth indisponible pour le moment"
            transport.place(number, account.handle)
            null
        } catch (_: SecurityException) { "Autorisation d’appel indisponible" }
          catch (_: RuntimeException) { "Appel non transmis · vérifie la connexion dans BT Phone" }
    }
    companion object {
        val permissions = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.CALL_PHONE)
        const val SERVICE = "com.android.bluetooth.hfpclient.connserv.HfpClientConnectionService"
        fun eligible(packageName: String, service: String, system: Boolean, bindingPermission: String?, enabled: Boolean,
            callProvider: Boolean, sim: Boolean, scheme: String?, tel: Boolean): Boolean =
            packageName == "com.android.bluetooth" && service == SERVICE && system &&
                bindingPermission == Manifest.permission.BIND_TELECOM_CONNECTION_SERVICE && enabled && callProvider && !sim && scheme == "hfpc" && tel
    }
}

@SuppressLint("MissingPermission") // BluetoothCalls checks the required runtime permissions before use.
private class SystemBluetoothCalls(private val context: Context) : BluetoothCallTransport {
    private val telecom get() = context.getSystemService(TelecomManager::class.java) ?: error("Telecom unavailable")
    override fun accounts(): List<BluetoothCallAccount> = telecom.callCapablePhoneAccounts.mapNotNull { handle ->
        if (handle.componentName.packageName != "com.android.bluetooth" || handle.componentName.className != BluetoothCalls.SERVICE) return@mapNotNull null
        val account = telecom.getPhoneAccount(handle) ?: return@mapNotNull null
        val service = runCatching { context.packageManager.getServiceInfo(handle.componentName, PackageManager.ComponentInfoFlags.of(0)) }.getOrNull() ?: return@mapNotNull null
        if (!BluetoothCalls.eligible(handle.componentName.packageName, handle.componentName.className,
            service.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0, service.permission,
            account.isEnabled && service.enabled && service.applicationInfo.enabled,
            account.hasCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER), account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION),
            account.address?.scheme, account.supportsUriScheme(PhoneAccount.SCHEME_TEL))) return@mapNotNull null
        BluetoothCallAccount(handle, account.label?.toString()?.take(60).orEmpty().ifBlank { "Téléphone Bluetooth" })
    }
    override fun emergency(number: String) = context.getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(number) ?: error("Emergency check unavailable")
    // isOutgoingCallPermitted is restricted to the ConnectionService owner in AOSP.
    // A dialer rechecks registration and submits placeCall; Telecom handles call limits.
    override fun permitted(handle: PhoneAccountHandle) = accounts().any { it.handle == handle }
    override fun place(number: String, handle: PhoneAccountHandle) {
        telecom.placeCall(Uri.fromParts("tel", number, null), Bundle().apply { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle) })
    }
}
