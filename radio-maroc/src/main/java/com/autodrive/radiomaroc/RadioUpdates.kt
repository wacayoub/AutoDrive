package com.autodrive.radiomaroc

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import com.autodrive.launcher.UpdatePhase
import com.autodrive.launcher.UpdateState
import com.autodrive.launcher.UpdateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class RadioUpdates(private val activity: RadioActivity, savedState: Bundle?) {
    private val updates = ViewModelProvider(activity)[UpdateViewModel::class.java]
    private var dialog: AlertDialog? = null
    private var installJob: Job? = null
    private var waitingForPermission = savedState?.getBoolean("radio_update_permission") == true
    private val permissionSettings = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val resume = waitingForPermission && activity.packageManager.canRequestPackageInstalls()
        waitingForPermission = false
        if (resume && updates.state.value.phase == UpdatePhase.READY) install()
    }

    init {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updates.autoCheck()
                if (savedState?.getBoolean("radio_update_dialog") == true && dialog == null) {
                    savedState.remove("radio_update_dialog")
                    show()
                }
                updates.state.collect { state ->
                    if (updates.takeAvailablePrompt() != null) show()
                    dialog?.let { render(it, state) }
                    if (updates.takeInstallRequest()) install()
                }
            }
        }
    }

    fun show() {
        if (dialog != null || activity.isFinishing) return
        val panel = AlertDialog.Builder(activity)
            .setTitle("Mises à jour")
            .setMessage("Recherche de mise à jour")
            .setPositiveButton("Rechercher", null)
            .setNegativeButton("Fermer", null)
            .create()
        dialog = panel
        panel.setOnDismissListener { dialog = null }
        panel.show()
        panel.window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
        panel.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { primaryAction(updates.state.value) }
        render(panel, updates.state.value)
        if (updates.state.value.phase == UpdatePhase.IDLE) updates.check()
    }

    private fun render(panel: AlertDialog, state: UpdateState) {
        val available = state.phase == UpdatePhase.AVAILABLE
        panel.setTitle(if (available) state.message else "Mises à jour")
        val message = when (state.phase) {
            UpdatePhase.AVAILABLE ->
                "Appuie sur OK pour télécharger la mise à jour vérifiée et ouvrir l’installateur Android."
            UpdatePhase.DOWNLOADING -> "${state.message} ${state.progress} %"
            else -> state.message
        }
        panel.setMessage("Version installée ${updates.source.versionName}\n\n$message")
        panel.getButton(AlertDialog.BUTTON_NEGATIVE).text = if (available) "Plus tard" else "Fermer"
        panel.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = state.phase != UpdatePhase.CHECKING && state.phase != UpdatePhase.DOWNLOADING
            text = when (state.phase) {
                UpdatePhase.AVAILABLE -> "OK"
                UpdatePhase.READY -> "Reprendre l’installation"
                UpdatePhase.ERROR -> "Réessayer"
                else -> "Rechercher"
            }
        }
    }

    private fun primaryAction(state: UpdateState) {
        when (state.phase) {
            UpdatePhase.AVAILABLE -> updates.downloadAndInstall()
            UpdatePhase.READY -> install()
            UpdatePhase.ERROR -> if (state.release == null) updates.check() else updates.downloadAndInstall()
            UpdatePhase.IDLE, UpdatePhase.CURRENT -> updates.check()
            UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING -> Unit
        }
    }

    private fun install() {
        if (installJob?.isActive == true || waitingForPermission) return
        installJob = activity.lifecycleScope.launch {
            try {
                val file = updates.verifiedFile()
                activity.lifecycle.withResumed {
                    if (!activity.packageManager.canRequestPackageInstalls()) {
                        waitingForPermission = true
                        permissionSettings.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}")))
                    } else {
                        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
                        activity.startActivity(Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                waitingForPermission = false
                Toast.makeText(activity, "Installation indisponible. Tu peux réessayer depuis Mises à jour.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    fun save(outState: Bundle) {
        outState.putBoolean("radio_update_dialog", dialog?.isShowing == true)
        outState.putBoolean("radio_update_permission", waitingForPermission)
    }

    fun close() {
        dialog?.dismiss()
    }
}
