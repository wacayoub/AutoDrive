package com.autodrive.radiomaroc

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.autodrive.launcher.RadioCatalogue
import com.autodrive.launcher.RadioCategory
import com.autodrive.launcher.RadioStation

internal enum class StationTab(val label: String) {
    MOROCCO("Radio Maroc"), QURAN("القرآن"), FAVORITES("★ Favoris")
}

internal object Stations {
    private val morocco = listOf(
        RadioStation("HIT RADIO", "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3",
            logoUrl = "https://www.hitradio.ma/logo/small-logo-hitradio.webp", tags = setOf("Musique")),
        RadioStation("Radio Mars", "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3", tags = setOf("Sport")),
        RadioStation("Chada FM", "https://chadafm.ice.infomaniak.ch/chadafm-128.aac", tags = setOf("Musique")),
        RadioStation("Radio Aswat", "https://aswat.ice.infomaniak.ch/aswat-high.mp3", tags = setOf("Débats")),
        RadioStation("Med Radio", "https://medradio.ice.infomaniak.ch/medradio-128.mp3", tags = setOf("Débats")),
        RadioStation("Skyrock Casablanca", "https://icecast.skyrock.net/s/casa_aac_64k", tags = setOf("Rap")),
        RadioStation("Medina FM", "https://medinafm.ice.infomaniak.ch/medinafm-64.mp3", tags = setOf("Généraliste")),
        RadioStation("Radio Plus Agadir", "https://stream-158.zeno.fm/bqdbb6hd0neuv", tags = setOf("Agadir")),
        RadioStation("Radio Azawan", "https://az-maroc.ice.infomaniak.ch/az-maroc-64.aac", tags = setOf("Musique")),
        RadioStation("U Dance", "https://udance.ice.infomaniak.ch/udance-128.aac", tags = setOf("Dance")),
        RadioStation("Medina FM Amazigh", "https://medinaamazigh.ice.infomaniak.ch/medinaamazigh-128.mp3",
            logoUrl = "https://api.medinafm.ma/uploads/11ee4925-5901-4d5f-81f3-23db7b0a3fa6-w320.webp", tags = setOf("Amazigh")),
        RadioStation("Medina FM Tarab", "https://medinatarab.ice.infomaniak.ch/medinatarab-128.mp3",
            logoUrl = "https://api.medinafm.ma/uploads/f0b5829f-c5ed-4acc-a775-d921ecd2edc7-w320.webp", tags = setOf("Tarab")),
    )
    private val excludedQuranPaths = setOf("mishary_alafasi", "abdulrahman_alsudaes", "yasser_aldosari")
    private val quran = (RadioCatalogue.featured.filter {
        RadioCatalogue.matches(it, RadioCategory.QURAN) && RadioCatalogue.officialPlayer(it) == null &&
            it.url.substringAfterLast('/') !in excludedQuranPaths
    } + listOf(
        RadioStation("محمود خليل الحصري · ورش", "https://backup.qurango.net/radio/mahmoud_khalil_alhussary_warsh"),
        RadioStation("محمد صديق المنشاوي · مجوّد", "https://backup.qurango.net/radio/mohammed_siddiq_alminshawi_mojawwad"),
        RadioStation("إبراهيم الدوسري", "https://backup.qurango.net/radio/ibrahim_aldosari"),
    )).sortedBy { station ->
        when (station.url.substringAfterLast('/')) {
            "aloyoon_alkoshi" -> 0
            "omar_alqazabri" -> 1
            else -> 2
        }
    }
    val all = morocco + quran

    fun visible(tab: StationTab, favorites: Set<String>, query: String): List<RadioStation> = when (tab) {
        StationTab.MOROCCO -> morocco
        StationTab.QURAN -> quran
        StationTab.FAVORITES -> all.filter { it.url in favorites }
    }.filter { RadioCatalogue.search(it, query) }

    fun subtitle(station: RadioStation): String = station.tags.firstOrNull() ?: "القرآن الكريم · MP3Quran"

    fun item(station: RadioStation): MediaItem = MediaItem.Builder().setMediaId(station.url).setUri(station.url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(station.name).setArtist(subtitle(station)).build())
        .build()
}
