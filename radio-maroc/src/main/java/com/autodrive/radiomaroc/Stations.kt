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
    MOROCCO("Radio Maroc"), WORLD("Monde"), QURAN("القرآن"), FAVORITES("Favoris");

    companion object {
        fun restore(name: String?): StationTab = when (name) {
            "TAJWID", "HARAM" -> QURAN
            else -> entries.firstOrNull { it.name == name } ?: MOROCCO
        }
    }
}

internal enum class QuranSection(val label: String) {
    MURATTAL("الترتيل"), TAJWID("التجويد"), HARAM("تلاوات الحرم")
}

internal object Stations {
    private val morocco = listOf(
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
    const val SNRT_STREAM_HOST = "cdn.live.easybroadcast.io"
    const val RADIO_2M_STREAM_HOST = "cdn-globecast.akamaized.net"
    // Keep the catalogue URLs as station IDs so saved stations and favourites survive updates.
    private val snrtStreams = mapOf(
        "alidaa-alwatania" to "radio_nationale",
        "alidaa-alamazighia" to "radio_amazigh",
        "chaine-inter" to "radio_inter",
        "idaat-mohammed-assadiss" to "radio_med_VI",
    ).mapKeys { (slug, _) -> "https://snrtlive.ma/fr/$slug" }
        .mapValues { (_, path) -> "https://$SNRT_STREAM_HOST/live/$path/playlist.m3u8" }
    private val officialStreams = snrtStreams + (radio2m.url to
        "https://$RADIO_2M_STREAM_HOST/live/eds/radio_2m/radio_hls_ts_hy217612tge1f21j83/radio_2m.m3u8")
    private val localRadios = morocco + snrt + radio2m
    private val localRadioNames = localRadios.map { RadioCatalogue.searchKey(it.name) }.toSet()
    // Radiométrie Maroc, Q2 2025, audience share in hundredths of a percentage point.
    private val audienceShares = mapOf(
        "محمد السادس للقرآن الكريم" to 1536,
        "Med Radio" to 1282, "HIT RADIO" to 1025, "MFM Radio" to 809, "Radio Aswat" to 775,
        "Radio Nationale" to 715, "Medi 1 Radio" to 626, "Chada FM" to 572, "Radio Mars" to 565,
        "Radio 2M" to 535, "Radio Amazighe" to 484, "Medina FM" to 265, "Cap Radio" to 246, "Chaîne Inter" to 91,
    ).mapKeys { (name, _) -> RadioCatalogue.searchKey(name) }

    private fun audienceShare(station: RadioStation): Int? = audienceShares[RadioCatalogue.searchKey(station.name)]

    fun audienceLabel(station: RadioStation): String? = audienceShare(station)?.let {
        "${it / 100},${(it % 100).toString().padStart(2, '0')} %"
    }
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
    private val quranLibrary = quran + tajwid + haramStations
    val all = localRadios + quranLibrary

    fun reciterName(station: RadioStation): String = station.name.substringBefore(" ·")
    fun isRecording(station: RadioStation): Boolean = station.url in haram
    fun isReciter(station: RadioStation): Boolean = quranSection(station) != null
    fun quranSection(station: RadioStation): QuranSection? = when {
        station in quran -> QuranSection.MURATTAL
        station in tajwid -> QuranSection.TAJWID
        isRecording(station) -> QuranSection.HARAM
        else -> null
    }
    fun streamUrl(station: RadioStation): String = officialStreams[station.url] ?: station.url
    fun isHls(station: RadioStation): Boolean = station.isHls || station.url in officialStreams

    fun visible(
        tab: StationTab, favorites: Set<String>, query: String, radioFavoritesOnly: Boolean = false,
        directory: List<RadioStation> = emptyList(), bookmarks: List<RadioStation> = emptyList(),
    ): List<RadioStation> {
        val candidates = when (tab) {
            StationTab.MOROCCO -> {
                val listed = (directory + bookmarks).filter { it.countryCode == "MA" }.distinctBy { it.url }
                val clicksByName = listed.groupBy { RadioCatalogue.searchKey(it.name) }
                    .mapValues { (_, entries) -> entries.maxOf { it.directoryClicks ?: -1 } }
                (localRadios + listed.filter { RadioCatalogue.searchKey(it.name) !in localRadioNames })
                    .distinctBy { it.url }
                    .filter { !radioFavoritesOnly || it.url in favorites }
                    .sortedWith(compareByDescending<RadioStation> { audienceShare(it) ?: -1 }
                        .thenByDescending { clicksByName[RadioCatalogue.searchKey(it.name)] ?: -1 })
            }
            StationTab.WORLD -> directory
            StationTab.QURAN -> quranLibrary
            StationTab.FAVORITES -> (all + bookmarks).distinctBy { it.url }.filter { it.url in favorites }
        }
        if (query.isBlank()) return candidates
        val key = RadioCatalogue.searchKey(query)
        val sections = if (tab == StationTab.QURAN && key.isNotEmpty()) {
            QuranSection.entries.filter { RadioCatalogue.searchKey(it.label).contains(key) }
        } else emptyList()
        return candidates.filter { station ->
            RadioCatalogue.search(station, query) ||
                aliases[reciterName(station)].orEmpty().any { RadioCatalogue.searchKey(it).contains(key) } ||
                (sections.isNotEmpty() && quranSection(station) in sections)
        }
    }

    fun subtitle(station: RadioStation): String = when {
        station.url in snrtStreams -> "SNRT Live · Maroc"
        station.url == radio2m.url -> "SOREAD 2M · Maroc"
        station.countryCode != null -> StationDirectory.countryName(station.countryCode)
        isRecording(station) -> "${station.tags.single()} · المسجد الحرام · ${station.url.removeSuffix(".mp3").takeLast(10)}"
        station in tajwid -> "المصحف المجوّد · MP3Quran"
        station == badr -> "حفص عن عاصم · MP3Quran"
        else -> station.tags.firstOrNull() ?: "القرآن الكريم · MP3Quran"
    }

    fun item(station: RadioStation): MediaItem = MediaItem.Builder()
        .setMediaId(station.url)
        .setUri(streamUrl(station))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(station.name).setArtist(subtitle(station)).build())
        .apply {
            if (isHls(station)) setMimeType(MimeTypes.APPLICATION_M3U8)
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
