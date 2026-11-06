package com.autodrive.launcher

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal enum class RadioGuidePhase { UNAVAILABLE, LOADING, READY, ERROR }
internal data class RadioGuideState(val source: RadioGuideSource? = null, val phase: RadioGuidePhase = RadioGuidePhase.UNAVAILABLE,
    val guide: RadioGuide? = null, val cached: Boolean = false, val message: String = "Sélectionne une radio pour son programme")

/** Bounded public schedule requests. No cookies, login, credentials or insecure TLS fallback. */
internal class RadioGuideRepository(context: Context) {
    private val folder = File(context.filesDir, "radio-guides")
    fun cached(source: RadioGuideSource): RadioGuide? = runCatching {
        val file = File(folder, "${source.name}.json")
        if (!file.exists() || file.length() > MAX_BYTES) return null
        val data = JSONObject(file.readText())
        if (data.optInt("schema") != 1 || data.optString("source") != source.name) return null
        val rows = data.getJSONArray("programmes")
        if (rows.length() > 500) return null
        val programmes = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            RadioProgramme(DayOfWeek.of(row.getInt("day")), row.getInt("start"), row.getInt("end"), row.getString("title"))
        }
        RadioGuide(source, Instant.ofEpochMilli(data.getLong("fetchedAt")), programmes)
    }.getOrNull()

    fun fetch(source: RadioGuideSource, now: Instant = Instant.now()): RadioGuide {
        require(source == RadioGuideSource.ASWAT) { "No supported structured schedule for this source" }
        var target = URI("https://radioaswat.ma/")
        repeat(4) {
            require(target.scheme == "https" && target.userInfo == null && target.port in setOf(-1, 443) &&
                target.host in setOf("radioaswat.ma", "www.radioaswat.ma")) { "Unexpected schedule origin" }
            val connection = target.toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 7_000; connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "AutoDrive-RadioGuide/1.0")
                connection.setRequestProperty("Accept", "text/html")
                val code = connection.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    target = target.resolve(connection.getHeaderField("Location") ?: error("Missing redirect"))
                } else {
                    check(code == 200) { "Schedule unavailable" }
                    val bytes = connection.inputStream.use { stream -> stream.readNBytes(MAX_BYTES + 1) }
                    check(bytes.size <= MAX_BYTES) { "Schedule too large" }
                    val programmes = RadioProgrammeParser.aswat(bytes.toString(Charsets.UTF_8))
                    check(programmes.isNotEmpty()) { "No readable published schedule" }
                    val guide = RadioGuide(source, Instant.ofEpochMilli(now.toEpochMilli()), programmes)
                    folder.mkdirs()
                    val data = JSONObject().put("schema", 1).put("source", source.name).put("fetchedAt", guide.fetchedAt.toEpochMilli())
                        .put("programmes", JSONArray(programmes.map { programme -> JSONObject().put("day", programme.day.value)
                            .put("start", programme.startMinute).put("end", programme.endMinute).put("title", programme.title) }))
                    val file = File(folder, "${source.name}.json")
                    val temporary = File.createTempFile("${source.name}-", ".tmp", folder)
                    try { temporary.writeText(data.toString()); check(temporary.renameTo(file)) { "Schedule cache unavailable" } }
                    finally { temporary.delete() }
                    return guide
                }
            } finally { connection.disconnect() }
        }
        error("Too many schedule redirects")
    }
    companion object { private const val MAX_BYTES = 2 * 1024 * 1024 }
}

internal class RadioGuideViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RadioGuideRepository(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(RadioGuideState())
    val state = mutable.asStateFlow()
    private var selected: RadioGuideSource? = null
    private var attemptedAt = Instant.EPOCH
    private var job: Job? = null

    fun select(station: RadioStation?, force: Boolean = false) {
        val source = RadioGuideSource.forStation(station)
        val now = Instant.now()
        val until = if (mutable.value.phase == RadioGuidePhase.ERROR) attemptedAt.plusSeconds(300)
            else mutable.value.guide?.fetchedAt?.plusSeconds(6 * 3600) ?: attemptedAt.plusSeconds(6 * 3600)
        if (!force && source == selected && (job?.isActive == true || now.isBefore(until))) return
        selected = source; attemptedAt = now; job?.cancel()
        if (source != RadioGuideSource.ASWAT) {
            mutable.value = RadioGuideState(source, message = if (station == null) "Sélectionne une radio pour son programme"
                else "Aucune grille horaire compatible disponible pour cette station")
            return
        }
        mutable.value = RadioGuideState(source, RadioGuidePhase.LOADING, message = "Lecture de la grille officielle…")
        job = scope.launch {
            val saved = withContext(Dispatchers.IO) { repository.cached(source) }
            if (saved != null && !force && saved.fetchedAt.isAfter(now.minusSeconds(6 * 3600)) && !saved.fetchedAt.isAfter(now.plusSeconds(300))) {
                mutable.value = RadioGuideState(source, RadioGuidePhase.READY, saved, cached = true)
                return@launch
            }
            if (saved != null) mutable.value = RadioGuideState(source, RadioGuidePhase.LOADING, saved, cached = true, message = "Actualisation de la grille…")
            try {
                val guide = withContext(Dispatchers.IO) { repository.fetch(source) }
                mutable.value = RadioGuideState(source, RadioGuidePhase.READY, guide)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.value = RadioGuideState(source, RadioGuidePhase.ERROR, saved, cached = saved != null,
                    message = if (saved != null) "Grille enregistrée · actualisation impossible" else "Grille indisponible · vérifie Internet ou réessaie")
            }
        }
    }
    override fun onCleared() { job?.cancel(); scope.cancel(); super.onCleared() }
}
