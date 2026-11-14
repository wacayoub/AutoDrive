package com.autodrive.radiomaroc

import android.net.Uri
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import com.autodrive.launcher.RadioCatalogue
import com.autodrive.launcher.RadioStation

@OptIn(UnstableApi::class)
internal object CarRadioLibrary {
    const val ROOT = "radio"
    const val MOROCCO = "morocco"
    const val QURAN = "quran"
    const val FAVORITES = "favorites"
    const val NATIONAL = "national"
    const val SNRT = "snrt"
    const val REGIONAL = "regional"
    const val ARABIC_MUSIC = "arabic_music"
    private const val BROWSE_PREFIX = "station/"
    // BrowserRoot extra documented by Android for Cars for browsable search.
    private const val SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"

    private data class Folder(val id: String, val title: String, @param:DrawableRes val icon: Int)

    private val morocco = Folder(MOROCCO, StationTab.MOROCCO.label, R.drawable.ic_tab_radio)
    private val quran = Folder(QURAN, StationTab.QURAN.label, R.drawable.ic_tab_quran)
    private val favorites = Folder(FAVORITES, StationTab.FAVORITES.label, R.drawable.ic_tab_favorites)
    private val sections = mapOf(
        ROOT to listOf(morocco, quran, favorites),
        MOROCCO to listOf(
            Folder(NATIONAL, "Nationales et web", R.drawable.ic_tab_radio),
            Folder(SNRT, "SNRT", R.drawable.ic_tab_radio),
            Folder(REGIONAL, "Régionales", R.drawable.ic_tab_radio),
            Folder(ARABIC_MUSIC, "Musique arabe", R.drawable.ic_tab_radio),
        ),
        QURAN to QuranSection.entries.map {
            Folder(it.name, it.label, R.drawable.ic_tab_quran)
        },
    )
    private val moroccoStations by lazy { visible(StationTab.MOROCCO) }
    private val quranStations by lazy { visible(StationTab.QURAN) }
    private val categories by lazy {
        mapOf(
            NATIONAL to moroccoStations.filter { Stations.region(it) == null },
            SNRT to moroccoStations.filter { it in Stations.snrt },
            REGIONAL to moroccoStations.filter { Stations.region(it) != null },
            ARABIC_MUSIC to Stations.visible(StationTab.MOROCCO, emptySet(), "", category = MoroccoCategory.ARABIC),
        ) + QuranSection.entries.associate { section ->
            section.name to quranStations.filter { Stations.quranSection(it) == section }
        }
    }
    private val stationsById by lazy { (moroccoStations + quranStations).associateBy(RadioStation::url) }

    data class Selection(val station: RadioStation, val queue: List<RadioStation>)

    fun root() = itemForFolder(Folder(ROOT, "Radio Maroc & Coran", R.drawable.ic_radio))

    fun rootParams(hints: LibraryParams?) = LibraryParams.Builder().setExtras(Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        putBoolean(SEARCH_SUPPORTED, true)
    }).setRecent(hints?.isRecent == true).setOffline(hints?.isOffline == true)
        .setSuggested(hints?.isSuggested == true).build()

    fun favorites(ids: Set<String>, bookmarks: List<RadioStation>): List<RadioStation> =
        Stations.visible(StationTab.FAVORITES, ids, "", bookmarks = bookmarks).filter { it != Stations.luxe }

    fun children(parent: String, page: Int, pageSize: Int, favorites: List<RadioStation>, rootLimit: Int): List<MediaItem> {
        val folders = sections[parent]
        if (folders != null) {
            val shown = if (parent == ROOT) folders.take(rootLimit.coerceAtLeast(0)) else folders
            return shown.page(page, pageSize).map(::itemForFolder)
        }
        return queueForFolder(parent, favorites).page(page, pageSize).map { itemForStation(it, parent) }
    }

    fun item(id: String, favorites: List<RadioStation>): MediaItem? {
        if (id == ROOT) return root()
        sections.values.firstNotNullOfOrNull { folders -> folders.firstOrNull { it.id == id } }
            ?.let { return itemForFolder(it) }
        val station = selection(id, favorites)?.station ?: return null
        return itemForStation(station, folderId(id))
    }

    fun selection(id: String, favorites: List<RadioStation>): Selection? {
        val folder = folderId(id)
        val stationId = if (folder == null) id else id.removePrefix("$BROWSE_PREFIX$folder/")
        val station = stationsById[stationId] ?: favorites.firstOrNull { it.url == stationId } ?: return null
        val queue = if (folder == null) defaultQueue(station) else queueForFolder(folder, favorites)
            .takeIf { it.any { candidate -> candidate.url == stationId } } ?: return null
        return Selection(station, queue)
    }

    fun stationForQuery(query: String, lastId: String?, favorites: List<RadioStation> = emptyList()): RadioStation? {
        if (query.isBlank()) return lastId?.let { selection(it, favorites)?.station } ?: moroccoStations.firstOrNull()
        val key = RadioCatalogue.searchKey(query.take(160))
        val results = search(query, favorites)
        return results.firstOrNull { RadioCatalogue.searchKey(it.name) == key } ?: results.singleOrNull()
    }

    fun search(query: String, favorites: List<RadioStation>): List<RadioStation> {
        val input = query.take(160).trim()
        if (RadioCatalogue.searchKey(input).isEmpty()) return emptyList()
        return (visible(StationTab.MOROCCO, input) + visible(StationTab.QURAN, input) +
            favorites.filter { RadioCatalogue.search(it, input) }).distinctBy(RadioStation::url)
    }

    fun searchItems(query: String, favorites: List<RadioStation>, page: Int, size: Int): List<MediaItem> =
        search(query, favorites).page(page, size).map { itemForStation(it, null) }

    fun portraitUri(station: RadioStation): Uri? = ReciterPortraits.forStation(station)?.let { resourceUri(it.image) }

    private fun defaultQueue(station: RadioStation): List<RadioStation> =
        if (station.countryCode != null) listOf(station)
        else if (Stations.isReciter(station)) quranStations else moroccoStations

    private fun queueForFolder(folder: String, favorites: List<RadioStation>): List<RadioStation> =
        if (folder == FAVORITES) favorites else categories[folder].orEmpty()

    private fun folderId(id: String): String? = id.takeIf { it.startsWith(BROWSE_PREFIX) }
        ?.removePrefix(BROWSE_PREFIX)?.substringBefore('/')

    private fun visible(tab: StationTab, query: String = "") =
        Stations.visible(tab, emptySet(), query).filter { it != Stations.luxe }

    private fun itemForStation(station: RadioStation, folder: String?) = MediaItem.Builder()
        .setMediaId(if (folder == null) station.url else "$BROWSE_PREFIX$folder/${station.url}")
        .setMediaMetadata(Stations.metadata(station).buildUpon()
            .setSubtitle(Stations.listDescription(station))
            .setArtworkUri(portraitUri(station) ?: CarArtworkProvider.stationUri(station.logoUrl) ?: resourceUri(
                if (Stations.isReciter(station)) R.drawable.ic_tab_quran else R.drawable.ic_tab_radio,
            ))
            .build())
        .build()

    private fun itemForFolder(folder: Folder) = MediaItem.Builder()
        .setMediaId(folder.id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(folder.title)
            .setArtworkUri(resourceUri(folder.icon)).setIsBrowsable(true).setIsPlayable(false).build())
        .build()

    private fun resourceUri(@DrawableRes icon: Int): Uri =
        Uri.parse("android.resource://com.autodrive.radiomaroc/$icon")

    private fun <T> List<T>.page(page: Int, size: Int): List<T> {
        if (page < 0 || size < 1) return emptyList()
        val start = (page.toLong() * size).coerceAtMost(this.size.toLong()).toInt()
        val end = (start.toLong() + size).coerceAtMost(this.size.toLong()).toInt()
        return subList(start, end)
    }
}
