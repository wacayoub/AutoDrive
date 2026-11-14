package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The active country/search only. Bookmarks and playback outlive this view. */
internal class DirectoryBrowser(private val source: StationDirectory, private val changed: () -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var query: StationDirectory.Query? = null
    private var offset: Int? = null
    var stations: List<RadioStation> = emptyList()
        private set
    var loading = false
        private set
    var failed = false
        private set
    val hasMore: Boolean get() = offset != null

    fun open(value: StationDirectory.Query?) {
        if (query == value) return
        job?.cancel()
        // While typing in the same country and sort, keep the previous results on screen until the new ones arrive.
        val refining = value != null && query?.country == value.country && query?.sort == value.sort
        query = value
        if (!refining) stations = emptyList()
        offset = if (value == null) null else 0
        loading = false
        failed = false
        loadMore()
    }

    fun loadMore() {
        val active = query ?: return
        val start = offset ?: return
        if (loading) return
        loading = true
        failed = false
        job = scope.launch {
            changed()
            try {
                if (start == 0 && stations.isEmpty()) {
                    stations = withContext(Dispatchers.IO) { source.cached(active.country) }
                    if (stations.isNotEmpty()) changed()
                }
                if (start == 0 && active.name.isNotBlank()) delay(250)
                val result = withContext(Dispatchers.IO) { source.fetch(active, start) }
                stations = if (start == 0) result.stations else (stations + result.stations).distinctBy { it.url }
                offset = result.nextOffset
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
            loading = false
            changed()
        }
    }

    fun close() = scope.cancel()
}
