package com.autodrive.radiomaroc

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.autodrive.launcher.RadioCatalogue
import com.autodrive.launcher.RadioCategory
import com.autodrive.launcher.RadioStation

internal enum class StationTab(val label: String) {
    MOROCCO("Radio Maroc"), QURAN("القرآن"), TAJWID("التجويد"), HARAM("صلوات الحرم"), FAVORITES("★ Favoris")
}

internal object Stations {
    private val morocco = listOf(
        // Radiométrie Maroc, Q2 2025: descending audience share among these stations.
        RadioStation("Med Radio", "https://medradio.ice.infomaniak.ch/medradio-128.mp3", tags = setOf("Débats")),
        RadioStation("HIT RADIO", "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3",
            logoUrl = "https://www.hitradio.ma/logo/small-logo-hitradio.webp", tags = setOf("Musique")),
        RadioStation("Radio Aswat", "https://aswat.ice.infomaniak.ch/aswat-high.mp3", tags = setOf("Débats")),
        RadioStation("Chada FM", "https://chadafm.ice.infomaniak.ch/chadafm-128.aac", tags = setOf("Musique")),
        RadioStation("Radio Mars", "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3", tags = setOf("Sport")),
        RadioStation("Medina FM", "https://medinafm.ice.infomaniak.ch/medinafm-64.mp3", tags = setOf("Généraliste")),
        // Regional and web stations without a comparable individual measurement.
        RadioStation("Skyrock Casablanca", "https://icecast.skyrock.net/s/casa_aac_64k", tags = setOf("Rap")),
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
    // Public audio links published by Haramain Salaah Recordings for Makkah.
    private fun haramRecording(reciter: String, prayer: String, file: String) =
        RadioStation("$reciter · صلاة $prayer", "https://mirrors.quranicaudio.com/haramain/$file.mp3")

    private val haram = listOf(
        haramRecording("ماهر المعيقلي", "الفجر", "2026/11/makkah/SheikhMuayqali_Fajr-2026-11-07"),
        haramRecording("عبد الله الجهني", "الفجر", "2026/11/makkah/SheikhJuhany_Fajr-2026-11-08"),
        haramRecording("بدر التركي", "المغرب", "2026/11/makkah/SheikhBadr_al_Turki_Maghrib-2026-11-08"),
        haramRecording("ماهر المعيقلي", "المغرب", "2026/11/makkah/SheikhMuayqali_Maghrib-2026-11-06"),
        haramRecording("ياسر الدوسري", "العشاء", "2026/11/makkah/SheikhDosary_Isha-2026-11-08"),
        haramRecording("بدر التركي", "العشاء", "2026/10/makkah/SheikhBadr_al_Turki_Isha-2026-10-26"),
    )
    private val aliases = mapOf(
        "بدر التركي" to listOf("Badr Turki", "Badr Al Turki", "Bader Alturki"),
        "العيون الكوشي" to listOf("Laayoun El Kouchi", "Al Oyoun Al Koshi"),
        "عبد الباسط عبد الصمد" to listOf("Abdelbaset Abdelssamad", "Abdulbasit Abdulsamad", "عبدالباسط عبدالصمد"),
        "محمد صديق المنشاوي" to listOf("Mohamed Siddiq Al Minshawi"),
        "محمود خليل الحصري" to listOf("Mahmoud Khalil Al Hussary"),
        "محمود علي البنا" to listOf("Mahmoud Ali Al Banna"),
        "ماهر المعيقلي" to listOf("Maher Al Muaiqly", "Maher Al Mueaqly"),
        "ياسر الدوسري" to listOf("Yasser Al Dosari", "Yasser Al Dossari"),
        "عبد الله الجهني" to listOf("Abdullah Al Juhany", "عبدالله الجهني"),
    )
    val all = morocco + quran + tajwid + haram

    fun reciterName(station: RadioStation): String = station.name.substringBefore(" ·")
    fun isRecording(station: RadioStation): Boolean = station in haram

    fun visible(tab: StationTab, favorites: Set<String>, query: String): List<RadioStation> = when (tab) {
        StationTab.MOROCCO -> morocco
        StationTab.QURAN -> quran
        StationTab.TAJWID -> tajwid
        StationTab.HARAM -> haram
        StationTab.FAVORITES -> all.filter { it.url in favorites }
    }.filter { station ->
        RadioCatalogue.search(station, query) || aliases[reciterName(station)].orEmpty().any {
            RadioCatalogue.searchKey(it).contains(RadioCatalogue.searchKey(query))
        }
    }

    fun subtitle(station: RadioStation): String = when {
        isRecording(station) -> "المسجد الحرام · ${station.url.removeSuffix(".mp3").takeLast(10)}"
        station in tajwid -> "المصحف المجوّد · MP3Quran"
        station == badr -> "حفص عن عاصم · MP3Quran"
        else -> station.tags.firstOrNull() ?: "القرآن الكريم · MP3Quran"
    }

    fun item(station: RadioStation): MediaItem = MediaItem.Builder().setMediaId(station.url).setUri(station.url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(station.name).setArtist(subtitle(station)).build())
        .build()
}
