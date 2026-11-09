package com.autodrive.radiomaroc

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.autodrive.launcher.RadioCatalogue
import com.autodrive.launcher.RadioCategory
import com.autodrive.launcher.RadioStation

internal enum class StationTab(val label: String) {
    MOROCCO("Radio Maroc"), QURAN("القرآن"), TAJWID("التجويد"), FAVORITES("★ Favoris")
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
    private fun quranStation(name: String, path: String) = RadioStation(name, "https://backup.qurango.net/radio/$path")

    private val badr = quranStation("بدر التركي", "bader")
    private val excludedQuranPaths = setOf("mishary_alafasi", "abdulrahman_alsudaes", "yasser_aldosari")
    private val quran = (RadioCatalogue.featured.filter {
        RadioCatalogue.matches(it, RadioCategory.QURAN) && RadioCatalogue.officialPlayer(it) == null &&
            it.url.substringAfterLast('/') !in excludedQuranPaths
    } + listOf(
        badr,
        quranStation("محمود خليل الحصري · ورش", "mahmoud_khalil_alhussary_warsh"),
        quranStation("إبراهيم الدوسري", "ibrahim_aldosari"),
    )).sortedBy { station ->
        when (station.url.substringAfterLast('/')) {
            "aloyoon_alkoshi" -> 0
            "omar_alqazabri" -> 1
            "bader" -> 2
            else -> 3
        }
    }
    private val tajwid = listOf(
        quranStation("عبد الباسط عبد الصمد", "abdulbasit_abdulsamad_mojawwad"),
        quranStation("محمد صديق المنشاوي · مجوّد", "mohammed_siddiq_alminshawi_mojawwad"),
        quranStation("محمود خليل الحصري", "mahmoud_khalil_alhussary_mojawwad"),
        quranStation("محمود علي البنا", "mahmoud_ali__albanna_mojawwad"),
    )
    private val aliases = mapOf(
        "bader" to listOf("Badr Turki", "Badr Al Turki", "Bader Alturki"),
        "abdulbasit_abdulsamad_mojawwad" to listOf("Abdelbaset Abdelssamad", "Abdulbasit Abdulsamad", "عبدالباسط عبدالصمد"),
        "mohammed_siddiq_alminshawi_mojawwad" to listOf("Mohamed Siddiq Al Minshawi"),
        "mahmoud_khalil_alhussary_mojawwad" to listOf("Mahmoud Khalil Al Hussary"),
        "mahmoud_ali__albanna_mojawwad" to listOf("Mahmoud Ali Al Banna"),
    )
    val all = morocco + quran + tajwid

    fun visible(tab: StationTab, favorites: Set<String>, query: String): List<RadioStation> = when (tab) {
        StationTab.MOROCCO -> morocco
        StationTab.QURAN -> quran
        StationTab.TAJWID -> tajwid
        StationTab.FAVORITES -> all.filter { it.url in favorites }
    }.filter { station ->
        RadioCatalogue.search(station, query) || aliases[station.url.substringAfterLast('/')].orEmpty().any {
            RadioCatalogue.searchKey(it).contains(RadioCatalogue.searchKey(query))
        }
    }

    fun subtitle(station: RadioStation): String = when {
        station in tajwid -> "المصحف المجوّد · MP3Quran"
        station == badr -> "حفص عن عاصم · MP3Quran"
        else -> station.tags.firstOrNull() ?: "القرآن الكريم · MP3Quran"
    }

    fun item(station: RadioStation): MediaItem = MediaItem.Builder().setMediaId(station.url).setUri(station.url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(station.name).setArtist(subtitle(station)).build())
        .build()
}
