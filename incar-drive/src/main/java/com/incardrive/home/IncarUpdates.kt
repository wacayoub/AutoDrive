package com.incardrive.home

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.autodrive.launcher.UpdatePhase
import com.autodrive.launcher.UpdateState
import com.autodrive.launcher.UpdateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Online updates from Incar Drive's GitHub releases, with the same checks as Radio Maroc & Coran: the version
 * manifest of the newest « incar-drive-v » release, a download checked against its SHA-256 and signed with the
 * installed app's key, then Android's own installer, which asks the driver to confirm. Checked once a day.
 */
internal class IncarUpdates(private val home: HomeActivity) {
    // One updater for the app, so recreating the screen (theme, colour) does not restart a download.
    private val updates: UpdateViewModel = shared ?: UpdateViewModel(home.application).also { shared = it }
    private var waitingForPermission = false
    private var listener: ((UpdateState) -> Unit)? = null

    val state get() = updates.state.value
    val installedVersion get() = updates.source.versionName

    /** Follows the updater while the screen is shown: offers a new version once, installs when it is ready. */
    fun follow(scope: CoroutineScope) {
        scope.launch {
            updates.state.collect { state ->
                listener?.invoke(state)
                updates.takeAvailablePrompt()?.let { release ->
                    AlertDialog.Builder(home)
                        .setTitle("Incar Drive ${release.versionName}")
                        .setMessage("Une nouvelle version est disponible. La télécharger et l’installer ?")
                        .setPositiveButton("Installer") { _, _ -> updates.downloadAndInstall() }
                        .setNegativeButton("Plus tard", null)
                        .show()
                }
                if (updates.takeInstallRequest()) install(scope)
            }
        }
    }

    fun onResume(scope: CoroutineScope) {
        updates.autoCheck()
        if (waitingForPermission && home.packageManager.canRequestPackageInstalls()) {
            waitingForPermission = false
            if (state.phase == UpdatePhase.READY) install(scope)
        }
    }

    /** For the Réglages row: called with every new state while the row is shown. */
    fun observe(callback: ((UpdateState) -> Unit)?) {
        listener = callback
        callback?.invoke(state)
    }

    fun primaryAction(scope: CoroutineScope) {
        when (state.phase) {
            UpdatePhase.AVAILABLE -> updates.downloadAndInstall()
            UpdatePhase.READY -> install(scope)
            UpdatePhase.ERROR -> if (state.release == null) updates.check() else updates.downloadAndInstall()
            UpdatePhase.IDLE, UpdatePhase.CURRENT -> updates.check()
            UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING -> Unit
        }
    }

    fun actionLabel(state: UpdateState) = when (state.phase) {
        UpdatePhase.AVAILABLE -> "Installer"
        UpdatePhase.READY -> "Installer"
        UpdatePhase.ERROR -> "Réessayer"
        UpdatePhase.CHECKING -> "Recherche…"
        UpdatePhase.DOWNLOADING -> "${state.progress} %"
        UpdatePhase.IDLE, UpdatePhase.CURRENT -> "Rechercher"
    }

    private fun install(scope: CoroutineScope) {
        scope.launch {
            try {
                val file = updates.verifiedFile()
                if (!home.packageManager.canRequestPackageInstalls()) {
                    // Android asks once that Incar Drive may install apps; the install resumes on return.
                    waitingForPermission = true
                    home.startSafely(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${home.packageName}")))
                } else {
                    val uri = FileProvider.getUriForFile(home, "${home.packageName}.updates", file)
                    home.startSafely(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(home, "Installation indisponible. Réessaie depuis Réglages › Mises à jour.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private companion object {
        var shared: UpdateViewModel? = null
    }
}
