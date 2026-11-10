package com.autodrive.radiomaroc

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.autodrive.launcher.RadioCatalogue
import com.autodrive.launcher.RadioStation

internal object CarRadioLibrary {
    private val morocco = folder("morocco", "Radio Maroc")
    private val quran = folder("quran", "القرآن")
    val root = folder("radio", "Radio Maroc & Coran")
    private val pages by lazy {
        mapOf(
            root.mediaId to listOf(morocco, quran),
            morocco.mediaId to items(StationTab.MOROCCO),
            quran.mediaId to items(StationTab.QURAN),
        )
    }

    fun children(parent: String, page: Int, pageSize: Int): List<MediaItem> {
        val items = pages[parent].orEmpty()
        val first = (page.toLong() * pageSize).coerceIn(0, items.size.toLong()).toInt()
        return items.drop(first).take(pageSize.coerceAtLeast(0))
    }

    fun item(id: String): MediaItem? = if (root.mediaId == id) root
        else pages.values.firstNotNullOfOrNull { items -> items.firstOrNull { it.mediaId == id } }

    fun stationForQuery(query: String, lastId: String?): RadioStation? {
        val playable = Stations.all.filter { it != Stations.luxe }
        if (query.isBlank()) return playable.firstOrNull { it.url == lastId }
            ?: Stations.visible(StationTab.MOROCCO, emptySet(), "").firstOrNull { it != Stations.luxe }
        val key = RadioCatalogue.searchKey(query.take(160))
        if (key.isEmpty()) return null
        return playable.firstOrNull { RadioCatalogue.searchKey(it.name) == key }
            ?: playable.filter { RadioCatalogue.search(it, query.take(160)) }.singleOrNull()
    }

    private fun items(tab: StationTab) = Stations.visible(tab, emptySet(), "").filter { it != Stations.luxe }
        .map { station ->
            val metadata = Stations.metadata(station).buildUpon().setArtworkUri(Stations.defaultArtwork()).build()
            MediaItem.Builder().setMediaId(station.url).setMediaMetadata(metadata).build()
        }

    private fun folder(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).build())
        .build()
}
