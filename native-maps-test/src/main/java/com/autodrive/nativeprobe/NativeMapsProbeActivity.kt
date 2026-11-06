package com.autodrive.nativeprobe

import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.window.embedding.RuleController
import androidx.window.embedding.SplitAttributes
import androidx.window.embedding.SplitController
import androidx.window.embedding.SplitPairFilter
import androidx.window.embedding.SplitPairRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

/** Standalone public-API experiment. No website, HOME role, adjacent-window flag or hidden API. */
class NativeMapsProbeActivity : ComponentActivity() {
    private val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)
    private var report = JSONObject()
    private lateinit var status: TextView
    private lateinit var launch: Button
    private lateinit var firmwareStatus: TextView
    private var target: ComponentName? = null
    private var eligible = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 12, 20, 12)
            setBackgroundColor(0xFF101F32.toInt())
        }
        body.addView(TextView(this).apply { text = "AutoDrive · Test Maps Android 0.3"; textSize = 22f })
        status = TextView(this).apply { textSize = 15f; setPadding(0, 10, 0, 10) }
        body.addView(status)
        body.addView(Button(this).apply { text = "Vérifier la compatibilité"; setOnClickListener { inspect() } })
        launch = Button(this).apply { text = "Tester Maps dans le panneau"; setOnClickListener { attempt(false) } }
        body.addView(launch)
        body.addView(Button(this).apply {
            text = "Copier le rapport"
            setOnClickListener {
                report.put("copiedAtUtc", Instant.now().toString())
                save()
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AutoDrive Maps test", report.toString(2)))
                Toast.makeText(this@NativeMapsProbeActivity, "Rapport copié", Toast.LENGTH_SHORT).show()
            }
        })
        body.addView(TextView(this).apply {
            text = "Test séparé : ne remplace pas AutoDrive. Aucun site web ni ouverture plein écran de remplacement. Une intégration annoncée par Android doit encore être vérifiée visuellement."
            textSize = 13f
        })
        firmwareStatus = TextView(this).apply { textSize = 15f; setPadding(0, 12, 0, 12) }
        body.addView(firmwareStatus)
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(body) })
        inspect()
        scope.launch {
            SplitController.getInstance(this@NativeMapsProbeActivity).splitInfoList(this@NativeMapsProbeActivity).collect { splits ->
                report.put("reportedSplitCount", splits.size)
                if (splits.isNotEmpty()) report.put("phase", "EMBEDDING_REPORTED_NEEDS_VISUAL_CHECK")
                save()
            }
        }
        // Negative-path experiment is restricted to Android emulators, never exposed in the UI.
        if (intent.getBooleanExtra("emulatorAttempt", false) && Build.HARDWARE in setOf("ranchu", "goldfish")) {
            launch.postDelayed({ attempt(true) }, 1000)
        }
    }

    private fun inspect() {
        report = JSONObject()
        val support = SplitController.getInstance(this).splitSupportStatus
        report.put("reportSchema", 3)
            .put("probeVersion", packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName)
            .put("generatedAtUtc", Instant.now().toString())
            .put("deviceManufacturer", Build.MANUFACTURER).put("deviceModel", Build.MODEL)
            .put("deviceBrand", Build.BRAND).put("buildDisplay", Build.DISPLAY)
            .put("androidRelease", Build.VERSION.RELEASE)
            .put("androidApi", Build.VERSION.SDK_INT).put("hardware", Build.HARDWARE)
            .put("splitSupport", support.toString()).put("phase", "CHECKED").put("reportedSplitCount", 0)
        val device = "Appareil : ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\n"
        target = packageManager.getLaunchIntentForPackage(MAPS)?.component
        eligible = false
        val component = target
        if (component != null) {
            val info = packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
            val host = packageManager.getPackageInfo(packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            val fingerprints = host.signingInfo?.apkContentsSigners.orEmpty().map {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02X".format(b) }
            }.toSet()
            val allowed = info.knownActivityEmbeddingCerts.orEmpty().map { it.replace(":", "").uppercase(Locale.ROOT) }.toSet()
            val untrusted = info.flags and ActivityInfo.FLAG_ALLOW_UNTRUSTED_ACTIVITY_EMBEDDING != 0
            val trusted = fingerprints.any(allowed::contains)
            eligible = support == SplitController.SplitSupportStatus.SPLIT_AVAILABLE && (untrusted || trusted)
            report.put("mapsComponent", component.flattenToString())
                .put("mapsVersion", packageManager.getPackageInfo(MAPS, PackageManager.PackageInfoFlags.of(0)).versionName)
                .put("mapsAllowsUntrustedEmbedding", untrusted).put("hostCertificateAccepted", trusted)
                .put("knownHostCertificateCount", allowed.size).put("mapsLaunchMode", info.launchMode)
            // This projection activity is reserved for Android Auto, not a public launcher widget.
            val projection = runCatching { packageManager.getActivityInfo(
                ComponentName(MAPS, "com.google.android.apps.auto.client.activity.ghost.GhostActivity"),
                PackageManager.ComponentInfoFlags.of(0)) }.getOrNull()
            report.put("projectionRequiredPermission", projection?.permission ?: JSONObject.NULL)
                .put("projectionPermissionGranted", projection?.permission?.let {
                    checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                } ?: false)
            val filter = SplitPairFilter(ComponentName(this, NativeMapsProbeActivity::class.java), component, null)
            RuleController.getInstance(this).setRules(setOf(SplitPairRule.Builder(setOf(filter))
                .setMinWidthDp(0).setMinHeightDp(0).setMinSmallestWidthDp(0)
                .setDefaultSplitAttributes(SplitAttributes.Builder()
                    .setSplitType(SplitAttributes.SplitType.ratio(.48f))
                    .setLayoutDirection(SplitAttributes.LayoutDirection.RIGHT_TO_LEFT).build()).build()))
            status.text = device + "Maps ${report.optString("mapsVersion")}\nSupport Android : $support\nMaps autorise cet hébergement : ${untrusted || trusted}\n" +
                if (eligible) "Prêt pour un essai natif. Le résultat reste à vérifier." else "Essai bloqué : support Android ou autorisation Maps manquants."
        } else {
            report.put("mapsComponent", JSONObject.NULL).put("phase", "MAPS_MISSING")
            status.text = device + "Google Maps Android est absent ou désactivé. Aucun remplacement web n’est chargé."
        }
        launch.isEnabled = eligible
        report.put("eligible", eligible)
        report.put("oemHostingAudit", JSONObject().put("phase", "PENDING"))
        firmwareStatus.text = "Firmware / lanceurs : vérification en cours…"
        save()
        val inspectedReport = report
        scope.launch {
            val audit = withContext(Dispatchers.IO) {
                try {
                    FirmwareHostingInspector(packageManager, packageName).inspect()
                } catch (error: RuntimeException) {
                    JSONObject().put("phase", "FAILED").put("errorType", error.javaClass.simpleName)
                }
            }
            // Refreshes can finish out of order. Never append an old audit to a new report.
            if (report !== inspectedReport) return@launch
            report.put("oemHostingAudit", audit)
            firmwareStatus.text = "Firmware / lanceurs — lecture seule\n" + when {
                audit.optString("phase") != "COMPLETE" -> "Métadonnées indisponibles. Aucun réglage modifié."
                audit.isNull("platformCertificateMatchesReference") -> "Certificat système non lisible. Compatibilité indéterminée."
                audit.optBoolean("platformCertificateMatchesReference") -> "Certificat système identique à la référence AB / DUO. L’interface constructeur et le fonctionnement restent à vérifier."
                else -> "Certificat système différent de la référence AB / DUO. Une interface constructeur reste à rechercher."
            } + "\nLe rapport inclut les lanceurs visibles et leurs permissions. Ce contrôle n’accorde aucun droit et ne prouve pas l’intégration de Maps."
            save()
        }
    }

    private fun attempt(emulatorOnly: Boolean) {
        if (target == null || (!eligible && !emulatorOnly)) return
        report.put("phase", "NATIVE_LAUNCH_REQUESTED").put("emulatorForcedAttempt", emulatorOnly)
        save()
        try {
            // Same host-owned task is required for activity embedding; no adjacent-window flags.
            startActivity(Intent(Intent.ACTION_MAIN).setComponent(target).addCategory(Intent.CATEGORY_LAUNCHER))
        } catch (error: RuntimeException) {
            report.put("phase", "LAUNCH_FAILED").put("error", error.javaClass.simpleName)
            status.text = "Essai refusé par Android : ${error.javaClass.simpleName}"
            save()
        }
    }

    private fun save() {
        File(filesDir, "native-maps-probe.json").writeText(report.toString(2))
        Log.i("AutoDriveNativeProbe", report.toString())
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object { const val MAPS = "com.google.android.apps.maps" }
}
