package com.autodrive.radiomaroc

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.autodrive.launcher.RadioCatalogue
import com.autodrive.launcher.RadioCategory
import com.autodrive.launcher.RadioStation

internal enum class StationTab(val label: String) {
    MOROCCO("Radio Maroc"), WORLD("Monde"), QURAN("القرآن"), TAJWID("التجويد"), HARAM("تلاوات الحرم"), FAVORITES("★ Favoris")
}

internal object Stations {
    private val morocco = listOf(
        // Radiométrie Maroc, Q2 2025: descending audience share among these stations.
        RadioStation("Med Radio", "https://medradio.ice.infomaniak.ch/medradio-128.mp3",
            logoUrl = "https://medradio.ma/wp-content/uploads/2023/08/cropped-med-fav-jpg-192x192.jpg", tags = setOf("Débats")),
        RadioStation("HIT RADIO", "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3",
            logoUrl = "https://www.hitradio.ma/logo/small-logo-hitradio.webp", tags = setOf("Musique")),
        RadioStation("Radio Aswat", "https://aswat.ice.infomaniak.ch/aswat-high.mp3",
            logoUrl = "https://radioaswat.ma/wp-content/themes/aswata108/img/icons/touch.png", tags = setOf("Débats")),
        RadioStation("Chada FM", "https://chadafm.ice.infomaniak.ch/chadafm-128.aac",
            logoUrl = "https://chada.ma/wp-content/themes/chada-theme-v1/assets/images/android-chrome-192x192.png", tags = setOf("Musique")),
        RadioStation("Radio Mars", "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3",
            logoUrl = "https://www.haca.ma/sites/default/files/upload/images/logo-radio-marsN-new%282%29.PNG", tags = setOf("Sport")),
        RadioStation("Medina FM", "https://medinafm.ice.infomaniak.ch/medinafm-64.mp3",
            logoUrl = "https://medinafm.ma/favicon-192.png", tags = setOf("Généraliste")),
        // Regional and web stations without a comparable individual measurement.
        RadioStation("Skyrock Casablanca", "https://icecast.skyrock.net/s/casa_aac_64k",
            logoUrl = "https://www.skyrock.fm/images/logo-skyrock.png", tags = setOf("Rap")),
        RadioStation("Radio Plus Agadir", "https://stream-158.zeno.fm/bqdbb6hd0neuv",
            logoUrl = "https://www.haca.ma/sites/default/files/upload/radio%20plus%20agadir_0.jpg", tags = setOf("Agadir")),
        RadioStation("Radio Azawan", "https://az-maroc.ice.infomaniak.ch/az-maroc-64.aac",
            logoUrl = "https://www.haca.ma/sites/default/files/upload/LOGO%20AZAWAN.PNG", tags = setOf("Musique")),
        RadioStation("U Dance", "https://udance.ice.infomaniak.ch/udance-128.aac",
            logoUrl = "https://bo.uradio.ma/storage/streaming/U_DANCE.webp", tags = setOf("Dance")),
        RadioStation("Medina FM Amazigh", "https://medinaamazigh.ice.infomaniak.ch/medinaamazigh-128.mp3",
            logoUrl = "https://api.medinafm.ma/uploads/11ee4925-5901-4d5f-81f3-23db7b0a3fa6-w320.webp", tags = setOf("Amazigh")),
        RadioStation("Medina FM Tarab", "https://medinatarab.ice.infomaniak.ch/medinatarab-128.mp3",
            logoUrl = "https://api.medinafm.ma/uploads/f0b5829f-c5ed-4acc-a775-d921ecd2edc7-w320.webp", tags = setOf("Tarab")),
    )
    val snrt = RadioCatalogue.featured.filter { RadioCatalogue.officialPlayer(it) != null }
    private val radio2m = RadioStation("Radio 2M", "https://radio2m.ma/", logoUrl = "https://radio2m.ma/favRadio2M.png")
    private val localRadios = morocco + snrt + radio2m
    private val localRadioNames = localRadios.map { RadioCatalogue.searchKey(it.name) }.toSet()
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
    private data class HaramExcerpt(val station: RadioStation, val startMs: Long, val endMs: Long)

    private fun haramExcerpt(
        reciter: String, surah: String, prayer: String, file: String, startMs: Long, endMs: Long,
    ) = HaramExcerpt(
        RadioStation("$reciter · سورة $surah", "https://mirrors.quranicaudio.com/haramain/$file.mp3",
            tags = setOf("صلاة $prayer")),
        startMs,
        endMs,
    )

    // Play only the first recitation after Al-Fatiha, ending before the transition to bowing.
    private val haram = listOf(
        haramExcerpt("ماهر المعيقلي", "الرحمن", "الفجر", "2026/11/makkah/SheikhMuayqali_Fajr-2026-11-07", 124_400, 384_500),
        haramExcerpt("عبد الله الجهني", "الأنعام", "الفجر", "2026/11/makkah/SheikhJuhany_Fajr-2026-11-08", 125_800, 399_100),
        haramExcerpt("بدر التركي", "التين", "المغرب", "2026/11/makkah/SheikhBadr_al_Turki_Maghrib-2026-11-08", 108_700, 162_700),
        haramExcerpt("ماهر المعيقلي", "الأحزاب", "المغرب", "2026/11/makkah/SheikhMuayqali_Maghrib-2026-11-06", 101_550, 196_300),
        haramExcerpt("ياسر الدوسري", "الفرقان", "العشاء", "2026/11/makkah/SheikhDosary_Isha-2026-11-08", 106_950, 317_250),
        haramExcerpt("بدر التركي", "التوبة", "العشاء", "2026/10/makkah/SheikhBadr_al_Turki_Isha-2026-10-26", 113_200, 275_750),
    ).associateBy { it.station.url }
    private val haramStations = haram.values.map { it.station }
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
    val all = localRadios + quran + tajwid + haramStations

    fun reciterName(station: RadioStation): String = station.name.substringBefore(" ·")
    fun isRecording(station: RadioStation): Boolean = station.url in haram
    fun isReciter(station: RadioStation): Boolean = station in quran || station in tajwid || isRecording(station)
    fun officialPlayer(station: RadioStation?): String? =
        RadioCatalogue.officialPlayer(station) ?: radio2m.url.takeIf { station?.url == it }

    fun visible(
        tab: StationTab, favorites: Set<String>, query: String, radioFavoritesOnly: Boolean = false,
        directory: List<RadioStation> = emptyList(), bookmarks: List<RadioStation> = emptyList(), snrtOnly: Boolean = false,
    ): List<RadioStation> = when (tab) {
        StationTab.MOROCCO -> {
            val listed = if (snrtOnly) snrt else (localRadios + (directory + bookmarks).filter {
                it.countryCode == "MA" && RadioCatalogue.searchKey(it.name) !in localRadioNames
            }).distinctBy { it.url }
            if (radioFavoritesOnly) listed.filter { it.url in favorites } else listed
        }
        StationTab.WORLD -> directory
        StationTab.QURAN -> quran
        StationTab.TAJWID -> tajwid
        StationTab.HARAM -> haramStations
        StationTab.FAVORITES -> (all + bookmarks).distinctBy { it.url }.filter { it.url in favorites }
    }.filter { station ->
        query.isBlank() || RadioCatalogue.search(station, query) || aliases[reciterName(station)].orEmpty().any {
            RadioCatalogue.searchKey(it).contains(RadioCatalogue.searchKey(query))
        }
    }

    fun subtitle(station: RadioStation): String = when {
        station in snrt -> "SNRT Live · lecteur officiel"
        station == radio2m -> "Radio 2M · lecteur officiel"
        station.countryCode != null -> StationDirectory.countryName(station.countryCode)
        isRecording(station) -> "${station.tags.single()} · المسجد الحرام · ${station.url.removeSuffix(".mp3").takeLast(10)}"
        station in tajwid -> "المصحف المجوّد · MP3Quran"
        station == badr -> "حفص عن عاصم · MP3Quran"
        else -> station.tags.firstOrNull() ?: "القرآن الكريم · MP3Quran"
    }

    fun item(station: RadioStation): MediaItem = MediaItem.Builder()
        .setMediaId(station.url)
        .setUri(station.url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(station.name).setArtist(subtitle(station)).build())
        .apply {
            if (station.isHls) setMimeType(MimeTypes.APPLICATION_M3U8)
            if (station.countryCode != null) setRequestMetadata(MediaItem.RequestMetadata.Builder()
                .setMediaUri(Uri.parse(station.url))
                .setExtras(Bundle().apply { putBoolean("hls", station.isHls) })
                .build())
            haram[station.url]?.let { excerpt ->
                setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(excerpt.startMs)
                    .setEndPositionMs(excerpt.endMs)
                    .build())
            }
        }.build()

    fun directoryItem(request: MediaItem): MediaItem? {
        val metadata = request.requestMetadata
        val url = metadata.mediaUri?.toString()?.let(RadioStation::validUrl) ?: return null
        if (url != request.mediaId) return null
        return MediaItem.Builder().setMediaId(url).setUri(url).setMediaMetadata(request.mediaMetadata)
            .apply { if (metadata.extras?.getBoolean("hls") == true) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
    }
}
