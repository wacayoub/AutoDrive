package com.autodrive.radiomaroc

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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
    private var dialog: UpdatePanel? = null
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
        val sheet = RadioSheet(activity, "Mises à jour", "Version installée ${updates.source.versionName}")
        val muted = activity.getColor(R.color.radio_muted)
        val status = activity.label("", 15, activity.getColor(R.color.radio_text), bold = true).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(activity.dp(4), activity.dp(2), activity.dp(4), 0)
        }
        val detail = activity.label("", 13, muted).apply { setPadding(activity.dp(4), activity.dp(4), activity.dp(4), 0) }
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(activity.getColor(R.color.radio_accent))
        }
        val primary = activity.label("", 16, activity.getColor(R.color.radio_on_primary), bold = true).apply {
            gravity = Gravity.CENTER
            background = activity.pressable(R.drawable.radio_segment_active)
            isFocusable = true
            remoteFocus()
            setRadioClick { primaryAction(updates.state.value) }
        }
        val later = activity.label("Fermer", 15, activity.getColor(R.color.radio_text), bold = true).apply {
            gravity = Gravity.CENTER
            background = activity.pressable(R.drawable.radio_option)
            isFocusable = true
            remoteFocus()
            setRadioClick { sheet.dismiss() }
        }
        sheet.body.addView(status, LinearLayout.LayoutParams(-1, -2))
        sheet.body.addView(detail, LinearLayout.LayoutParams(-1, -2))
        sheet.body.addView(progress, LinearLayout.LayoutParams(-1, activity.dp(8)).apply {
            topMargin = activity.dp(10)
            marginStart = activity.dp(4)
            marginEnd = activity.dp(4)
        })
        sheet.body.addView(activity.row().apply {
            addView(later, LinearLayout.LayoutParams(0, activity.dp(54), 1f).apply { marginEnd = activity.dp(8) })
            addView(primary, LinearLayout.LayoutParams(0, activity.dp(54), 1.4f))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(14) })
        val panel = UpdatePanel(sheet, status, detail, progress, primary, later)
        dialog = panel
        sheet.dialog.setOnDismissListener { dialog = null }
        sheet.show()
        primary.requestFocus()
        render(panel, updates.state.value)
        if (updates.state.value.phase == UpdatePhase.IDLE) updates.check()
    }

    /** The views of the open update panel, refreshed as the check and download progress. */
    private class UpdatePanel(
        val sheet: RadioSheet, val status: TextView, val detail: TextView, val progress: ProgressBar,
        val primary: TextView, val secondary: TextView,
    ) {
        val isShowing get() = sheet.dialog.isShowing
        fun dismiss() = sheet.dismiss()
    }

    private fun render(panel: UpdatePanel, state: UpdateState) {
        val available = state.phase == UpdatePhase.AVAILABLE
        panel.status.text = when (state.phase) {
            UpdatePhase.AVAILABLE -> state.message
            UpdatePhase.DOWNLOADING -> "Téléchargement · ${state.progress} %"
            else -> state.message
        }
        panel.detail.text = when (state.phase) {
            UpdatePhase.AVAILABLE -> "Le fichier est vérifié avant d’ouvrir l’installateur Android, qui demande ta confirmation."
            UpdatePhase.CHECKING -> "Recherche en ligne de la dernière version…"
            UpdatePhase.DOWNLOADING -> state.message
            UpdatePhase.READY -> "Le fichier vérifié est prêt. Android demande ta confirmation pour l’installer."
            UpdatePhase.CURRENT -> "La radio vérifie aussi chaque jour à l’ouverture."
            else -> ""
        }
        panel.detail.visibility = if (panel.detail.text.isEmpty()) View.GONE else View.VISIBLE
        panel.progress.visibility = if (state.phase == UpdatePhase.DOWNLOADING) View.VISIBLE else View.GONE
        panel.progress.progress = state.progress
        panel.primary.text = when (state.phase) {
            UpdatePhase.AVAILABLE -> "Installer"
            UpdatePhase.READY -> "Reprendre l’installation"
            UpdatePhase.ERROR -> "Réessayer"
            UpdatePhase.CHECKING -> "Recherche…"
            UpdatePhase.DOWNLOADING -> "Téléchargement…"
            else -> "Rechercher"
        }
        enable(panel.primary, state.phase != UpdatePhase.CHECKING && state.phase != UpdatePhase.DOWNLOADING)
        panel.secondary.text = if (available) "Plus tard" else "Fermer"
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
