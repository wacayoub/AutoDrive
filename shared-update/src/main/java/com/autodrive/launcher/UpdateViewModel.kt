package com.autodrive.launcher

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal enum class UpdatePhase { IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY, CURRENT, ERROR }
internal data class UpdateState(val phase: UpdatePhase = UpdatePhase.IDLE, val release: UpdateRelease? = null,
    val progress: Int = 0, val message: String = "Rechercher une nouvelle version sur GitHub",
    val installRequested: Boolean = false)

internal class UpdateViewModel(application: Application, val source: UpdateSource) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, GitHubUpdates(application))
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var readyFile: File? = null
    private var announcedVersion: Long? = null
    private var lastAttempt: Long? = null
    private val prefs = application.getSharedPreferences("online_updates", 0)

    fun autoCheck() {
        val value = state.value
        if (value.phase !in setOf(UpdatePhase.IDLE, UpdatePhase.CURRENT, UpdatePhase.ERROR) || value.release != null) return
        if (lastAttempt?.let { SystemClock.elapsedRealtime() - it < 15 * 60 * 1000L } == true) return
        val age = System.currentTimeMillis() - prefs.getLong("checked_at", 0)
        val due = age !in 0..24 * 60 * 60 * 1000L ||
            prefs.getLong("checked_version", -1) != source.versionCode
        if (due) check()
    }

    fun check() {
        if (job?.isActive == true) return
        lastAttempt = SystemClock.elapsedRealtime()
        readyFile = null
        job = scope.launch {
            mutable.value = UpdateState(UpdatePhase.CHECKING, message = "Recherche sur GitHub…")
            try {
                val release = withContext(Dispatchers.IO) { source.latest() }
                require(release.minSdk <= Build.VERSION.SDK_INT) { "Cette version nécessite un Android plus récent" }
                prefs.edit().putLong("checked_at", System.currentTimeMillis()).putLong("checked_version", source.versionCode).apply()
                mutable.value = if (release.versionCode > source.versionCode)
                    UpdateState(UpdatePhase.AVAILABLE, release, message = "Version ${release.versionName} disponible")
                else UpdateState(UpdatePhase.CURRENT, message = "La version installée est à jour")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.value = UpdateState(UpdatePhase.ERROR, message = friendly(error))
            }
        }
    }

    /** One explicit OK authorizes download, verification, then a foreground installer handoff. */
    fun downloadAndInstall() {
        if (job?.isActive == true) return
        val release = state.value.release ?: return
        if (state.value.phase !in setOf(UpdatePhase.AVAILABLE, UpdatePhase.ERROR)) return
        job = scope.launch {
            mutable.value = UpdateState(UpdatePhase.DOWNLOADING, release, message = "Téléchargement de ${release.versionName}…")
            try {
                readyFile = withContext(Dispatchers.IO) {
                    source.download(release) { value -> mutable.value = mutable.value.copy(progress = value) }
                }
                mutable.value = UpdateState(UpdatePhase.READY, release, 100,
                    "Téléchargement vérifié. Confirmation de l’installation dans Android…", installRequested = true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.value = UpdateState(UpdatePhase.ERROR, release, message = friendly(error))
            }
        }
    }

    fun takeAvailablePrompt(): UpdateRelease? {
        val value = state.value
        val release = value.release ?: return null
        if (value.phase != UpdatePhase.AVAILABLE || announcedVersion == release.versionCode) return null
        announcedVersion = release.versionCode
        return release
    }

    fun offerAgainAfterRecreation() { announcedVersion = null }

    /** Claim before opening any external Activity, so returning cannot reopen the installer. */
    fun takeInstallRequest(): Boolean {
        val value = state.value
        if (value.phase != UpdatePhase.READY || !value.installRequested) return false
        mutable.value = value.copy(installRequested = false,
            message = "Mise à jour vérifiée. Si nécessaire, tu peux rouvrir l’installation.")
        return true
    }

    suspend fun verifiedFile(): File {
        val release = state.value.release ?: error("Aucune mise à jour préparée")
        val file = readyFile ?: error("Télécharge d’abord la mise à jour")
        return withContext(Dispatchers.IO) { source.verify(file, release) }
    }

    private fun friendly(error: Exception) = when (error) {
        is java.net.SocketTimeoutException -> "Connexion trop lente. Réessaie."
        is java.net.UnknownHostException -> "Internet indisponible. Réessaie après reconnexion."
        is IllegalArgumentException -> error.message ?: "Mise à jour non valide"
        else -> "Impossible de récupérer la mise à jour. Vérifie Internet et réessaie."
    }

    override fun onCleared() { scope.cancel(); super.onCleared() }
}
