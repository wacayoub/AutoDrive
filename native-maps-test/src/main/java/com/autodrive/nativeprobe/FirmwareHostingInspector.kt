package com.autodrive.nativeprobe

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

/** Reads public package metadata only. Never grants permissions or launches another app. */
class FirmwareHostingInspector(private val pm: PackageManager, private val hostPackage: String) {
    fun inspect(): JSONObject {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homes = pm.queryIntentActivities(homeIntent, PackageManager.ResolveInfoFlags.of(0))
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .distinctBy { it.flattenToString() }.sortedBy { it.flattenToString() }
        val resolvedHome = pm.resolveActivity(homeIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            ?.activityInfo?.let { ComponentName(it.packageName, it.name) }
        val packages = (listOf("android", hostPackage, "com.autodrive.launcher", "com.android.launcher3",
            "com.aibox.launcher", "kr.duohome.launcher.aibox") + homes.map { it.packageName }).distinct()
        val inventory = packages.map(::inspectPackage)
        val platform = inventory.first { it.optString("package") == "android" }
        val certs = platform.optJSONArray("currentSignerSha256")
        val platformKnown = platform.optString("status") == "READ" && certs != null && certs.length() > 0
        val referenceMatch = platformKnown && (0 until certs!!.length()).any { certs.getString(it) == REFERENCE_CERT }
        return JSONObject().put("phase", "COMPLETE").put("checkedAtUtc", Instant.now().toString())
            .put("method", "PUBLIC_PACKAGE_METADATA_ONLY")
            .put("referenceAiBoxLauncherCertificateSha256", REFERENCE_CERT)
            .put("platformCertificateMatchesReference", if (platformKnown) referenceMatch else JSONObject.NULL)
            .put("assessment", when {
                !platformKnown -> "PLATFORM_CERTIFICATE_UNKNOWN"
                referenceMatch -> "REFERENCE_CERT_MATCH_VENDOR_INTERFACE_STILL_REQUIRED"
                else -> "NO_REFERENCE_CERT_MATCH_OEM_INTERFACE_UNRESOLVED"
            })
            .put("resolvedHomeComponent", resolvedHome?.flattenToString() ?: JSONObject.NULL)
            .put("resolvedHomeIsListedHandler", resolvedHome in homes)
            .put("homeActivities", JSONArray(homes.map { it.flattenToString() }))
            .put("packages", JSONArray(inventory))
            .put("nativeHostingVerified", false)
            .put("limitations", "Certificate and permission metadata do not prove app hosting or navigation compatibility. No vendor API was invoked. Missing packages may be filtered by Android visibility. A reference certificate match grants AutoDrive no privileges.")
    }

    private fun inspectPackage(name: String): JSONObject {
        val result = JSONObject().put("package", name)
        return try {
            val info = pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(
                (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS).toLong()))
            val app = info.applicationInfo
            val fingerprints = info.signingInfo?.apkContentsSigners.orEmpty().map { signer ->
                MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
            }.sorted()
            val requested = info.requestedPermissions.orEmpty()
            val flags = info.requestedPermissionsFlags ?: IntArray(0)
            val permissions = CAPABILITIES.map { permission ->
                val index = requested.indexOf(permission)
                JSONObject().put("name", permission).put("requested", index >= 0)
                    .put("requestedPermissionFlagGranted", index >= 0 && index < flags.size &&
                        flags[index] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0)
                    .put("effectiveGranted", pm.checkPermission(permission, name) == PackageManager.PERMISSION_GRANTED)
            }
            result.put("status", "READ").put("versionName", info.versionName ?: JSONObject.NULL)
                .put("uid", app?.uid ?: JSONObject.NULL)
                .put("systemImageApp", app != null && app.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                .put("currentSignerSha256", JSONArray(fingerprints))
                .put("platformSignatureMatch", pm.checkSignatures("android", name) == PackageManager.SIGNATURE_MATCH)
                .put("hostingPermissions", JSONArray(permissions))
        } catch (_: PackageManager.NameNotFoundException) {
            result.put("status", "NOT_VISIBLE_OR_NOT_INSTALLED")
        } catch (error: RuntimeException) {
            result.put("status", "UNREADABLE").put("errorType", error.javaClass.simpleName)
        }
    }

    companion object {
        // Public certificate fingerprint observed in publisher-distributed AB 1.5.5 and DUO 1.7.8.
        const val REFERENCE_CERT = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"
        val CAPABILITIES = listOf("ADD_TRUSTED_DISPLAY", "INJECT_EVENTS", "MANAGE_ACTIVITY_TASKS",
            "ACTIVITY_EMBEDDING", "INTERNAL_SYSTEM_WINDOW").map { "android.permission.$it" }
    }
}
