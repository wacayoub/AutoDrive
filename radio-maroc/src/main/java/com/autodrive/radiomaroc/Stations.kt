package com.autodrive.radiomaroc

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.autodrive.launcher.RadioCatalogue
import com.autodrive.launcher.RadioCategory
import com.autodrive.launcher.RadioStation
import java.text.Collator
import java.util.Locale

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

internal enum class StationSort(val label: String) {
    DEFAULT("Popularité"), NAME_ASC("Nom A–Z"), NAME_DESC("Nom Z–A"), GROUPS("Par groupe"),
    FAVORITES_FIRST("Favoris en premier");

    fun labelFor(tab: StationTab) = if (this == DEFAULT && tab == StationTab.FAVORITES) "Ordre du catalogue" else label

    companion object {
        fun options(tab: StationTab) = entries.filter {
            when (it) {
                GROUPS -> tab == StationTab.MOROCCO
                FAVORITES_FIRST -> tab == StationTab.MOROCCO || tab == StationTab.WORLD
                else -> true
            }
        }
    }
}

internal enum class MoroccoCategory(val label: String) {
    ALL("Toutes les radios"), ARABIC("Musique arabe · موسيقى عربية"),
    FAYROUZ("Fayrouz · فيروز"), TARAB("Tarab · طرب"), CHAABI("Chaâbi · شعبي"),
    ANDALOU("Andalou · أندلسي"), MOROCCAN("Scène marocaine · موسيقى مغربية"),
    AMAZIGH("Amazigh · أمازيغي"), REGIONAL("Radios régionales"), DIRECTORY("Annuaire communautaire")
}

internal enum class StationGroup(val label: String) {
    HIT("GROUPE HIT RADIO"), MEDI1("MEDI 1"), SNRT("SNRT"), OTHER("AUTRES RADIOS"), REGIONAL("RADIOS RÉGIONALES")
}

internal object Stations {
    private const val RADIO_PLUS_ID = "https://stream-158.zeno.fm/bqdbb6hd0neuv"
    private val fayrouz = RadioStation("Fayrouz · فيروزيات", "https://stream.zeno.fm/zv3e5wykprhvv.mp3",
        tags = setOf("Webradio indépendante · فيروزيات"))
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
        RadioStation("Radio Plus", RADIO_PLUS_ID,
            logoUrl = "https://www.haca.ma/sites/default/files/upload/radio%20plus%20agadir_0.jpg", tags = setOf("Généraliste")),
        RadioStation("Radio Azawan", "https://az-maroc.ice.infomaniak.ch/az-maroc-64.aac",
            logoUrl = "https://www.haca.ma/sites/default/files/upload/LOGO%20AZAWAN.PNG", tags = setOf("Musique")),
        RadioStation("U Dance", "https://udance.ice.infomaniak.ch/udance-128.aac",
            logoUrl = "https://bo.uradio.ma/storage/streaming/U_DANCE.webp", tags = setOf("Dance")),
        RadioStation("Medina FM Amazigh", "https://medinaamazigh.ice.infomaniak.ch/medinaamazigh-128.mp3",
            logoUrl = "https://api.medinafm.ma/uploads/11ee4925-5901-4d5f-81f3-23db7b0a3fa6-w320.webp", tags = setOf("Amazigh")),
        RadioStation("Medina FM Tarab", "https://medinatarab.ice.infomaniak.ch/medinatarab-128.mp3",
            logoUrl = "https://api.medinafm.ma/uploads/f0b5829f-c5ed-4acc-a775-d921ecd2edc7-w320.webp", tags = setOf("Tarab")),
        RadioStation("Medina FM Andalou", "https://medinaandalou.ice.infomaniak.ch/medinaandalou-128.mp3",
            logoUrl = "https://medinafm.ma/favicon-192.png", tags = setOf("Andalou")),
        RadioStation("U Radio", "https://uradio-aac.ice.infomaniak.ch/uradio.aac",
            tags = setOf("Musique")),
        RadioStation("Radio Manarat", "https://listen.radioking.com/radio/252934/stream/297385",
            tags = setOf("Éducation et culture", "راديو منارات")),
        RadioStation("Ness Radio", "https://radio.nessradio.net:8212/nessradio-hd", tags = setOf("Musique")),
        fayrouz,
        RadioStation("Tarab Radio", "https://stream.zeno.fm/fy8achbq97zuv", tags = setOf("Webradio indépendante · طرب")),
    )
    val snrtNational = RadioCatalogue.featured.filter { RadioCatalogue.officialPlayer(it) != null }
    private val radio2m = RadioStation("Radio 2M", "https://radio2m.ma/", logoUrl = "https://radio2m.ma/favRadio2M.png")
    val luxe = RadioStation("Luxe Radio", "https://open.luxeradio.ma/radio/live",
        logoUrl = "https://www.haca.ma/sites/default/files/upload/images/logo-luxeradio.PNG", tags = setOf("Musique"))
    const val SNRT_STREAM_HOST = "cdn.live.easybroadcast.io"
    const val RADIO_2M_STREAM_HOST = "cdn-globecast.akamaized.net"
    // Keep the catalogue URLs as station IDs so saved stations and favourites survive updates.
    private val snrtPaths = mapOf(
        "alidaa-alwatania" to "radio_nationale",
        "alidaa-alamazighia" to "radio_amazigh",
        "chaine-inter" to "radio_inter",
        "idaat-mohammed-assadiss" to "radio_med_VI",
    )
    private val snrtStreams = snrtPaths.mapKeys { (slug, _) -> "https://snrtlive.ma/fr/$slug" }
        .mapValues { (_, path) -> "https://$SNRT_STREAM_HOST/live/$path/playlist.m3u8" }
    // Streams published by the official SNRT Live regional player.
    private val snrtRegional = listOf(
        snrtRegional("Agadir", "agadir", "Souss–Massa", "أكادير"),
        snrtRegional("Al Hoceïma", "houceima", "Tanger–Tétouan–Al Hoceïma", "الحسيمة"),
        snrtRegional("Casablanca", "casablanca", "Casablanca–Settat", "الدار البيضاء"),
        snrtRegional("Dakhla", "dakhla", "Dakhla–Oued Ed-Dahab", "الداخلة"),
        snrtRegional("Fès", "fes", "Fès–Meknès", "فاس"),
        snrtRegional("Laâyoune", "laayoune", "Laâyoune–Sakia El Hamra", "العيون"),
        snrtRegional("Marrakech", "marrakech", "Marrakech–Safi", "مراكش"),
        snrtRegional("Meknès", "meknes", "Fès–Meknès", "مكناس"),
        snrtRegional("Oujda", "oujda", "Oriental", "وجدة"),
        snrtRegional("Tanger", "tanger", "Tanger–Tétouan–Al Hoceïma", "طنجة"),
        snrtRegional("Tétouan", "tetouan", "Tanger–Tétouan–Al Hoceïma", "تطوان"),
    ).toMap()
    val snrt = snrtNational + snrtRegional.keys
    val snrtStreamDirectories = snrtPaths.values.toSet() + snrtRegional.keys.map {
        it.url.substringAfter("/live/").substringBefore('/')
    }

    private fun snrtRegional(city: String, mount: String, area: String, arabicCity: String) =
        RadioStation("SNRT Radio $city", "https://$SNRT_STREAM_HOST/live/radio_$mount/playlist.m3u8",
            logoUrl = "https://www.haca.ma/sites/default/files/upload/images/rn.jpg",
            tags = setOf(arabicCity), isHls = true) to area

    private val officialStreams = snrtStreams + mapOf(
        radio2m.url to "https://$RADIO_2M_STREAM_HOST/live/eds/radio_2m/radio_hls_ts_hy217612tge1f21j83/radio_2m.m3u8",
        RADIO_PLUS_ID to "https://hosting.studioradiomedia.fr:3040/stream",
    )
    private val regional = listOf(
        RadioStation("Cap Radio", "https://listen.radioking.com/radio/710810/stream/776366",
            logoUrl = "https://capradiotv.com/wp-content/uploads/2023/11/cropped-LOGO-PNG-CAP-RADIO-192x192.png",
            tags = setOf("Tanger", "Rif", "الشمال", "طنجة")),
        RadioStation("Radio Tanger Med", "https://radiotangermed-22.ice.infomaniak.ch/radiotangermed-22-128.mp3",
            logoUrl = "https://www.radiotangermed.com/wp-content/uploads/2020/11/VERTICAL-LOGO-01-AR-COMP.png",
            tags = setOf("Tanger", "Infos portuaires", "طنجة المتوسط")),
        RadioStation("Marrakech Plus", "https://cast5.my-control-panel.com/proxy/marrakec/stream",
            logoUrl = "https://www.marrakechplus.ma/wp-content/uploads/2025/06/cropped-icon-marrakechplus-512-x-512-px-1.png",
            tags = setOf("Marrakech", "مراكش بلوس")),
    )
    private val regionalAreas = mapOf(
        "capradio" to "Tanger–Tétouan–Al Hoceïma",
        "radiotangermed" to "Tanger–Tétouan–Al Hoceïma",
        "marrakechplus" to "Marrakech–Safi",
        "skyrockcasablanca" to "Casablanca–Settat",
    ) + snrtRegional.mapKeys { (station, _) -> StationFamilies.directoryKey(station) }
    private val localRadios = morocco + snrt + radio2m + luxe + StationFamilies.hit + StationFamilies.medi + regional
    private val localRadioNames = localRadios.map(StationFamilies::directoryKey).toSet()
    private val localRadioUrls = localRadios.map(RadioStation::url).toSet()
    private val hitGroupNames = (StationFamilies.hit + morocco.filter { it.name == "HIT RADIO" || it.name == "Radio Azawan" })
        .map(StationFamilies::directoryKey).toSet()
    private val mediGroupNames = StationFamilies.medi.map(StationFamilies::directoryKey).toSet()
    // Radiométrie Maroc, Q2 2025, audience share in hundredths of a percentage point.
    private val audienceShares = mapOf(
        "محمد السادس للقرآن الكريم" to 1536,
        "Med Radio" to 1282, "HIT RADIO" to 1025, "MFM Radio" to 809, "Radio Aswat" to 775,
        "Radio Nationale" to 715, "Medi 1 Radio" to 626, "Chada FM" to 572, "Radio Mars" to 565,
        "Radio 2M" to 535, "Radio Amazighe" to 484, "Medina FM" to 265, "Cap Radio" to 246, "Chaîne Inter" to 91,
    ).mapKeys { (name, _) -> RadioCatalogue.searchKey(name) }

    private fun audienceShare(station: RadioStation): Int? = audienceShares[RadioCatalogue.searchKey(station.name)]

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
        quranStation("فارس عباد", "fares_abbad"),
        quranStation("ناصر القطامي", "nasser_alqatami"),
        quranStation("أحمد نعينع", "ahmad_nauina"),
        quranStation("صلاح البدير", "salah_albudair"),
        quranStation("عبد الله بصفر", "abdullah_basfer"),
        quranStation("عبد الله الجهني", "abdullah_aljohany"),
        quranStation("عبد الرشيد صوفي", "abdulrasheed_soufi_khalaf"),
        quranStation("عبد المحسن القاسم", "abdulmohsen_alqasim"),
        quranStation("علي الحذيفي", "ali_alhuthaifi"),
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
        "فارس عباد" to listOf("Fares Abbad", "Faris Abbad"),
        "ناصر القطامي" to listOf("Nasser Al Qatami"),
        "أحمد نعينع" to listOf("Ahmed Nuainaa", "Ahmad Nauina"),
        "صلاح البدير" to listOf("Salah Al Budair"),
        "عبد الله بصفر" to listOf("Abdullah Basfar"),
        "عبد الرشيد صوفي" to listOf("Abdul Rashid Sufi", "Abdulrasheed Soufi"),
        "عبد المحسن القاسم" to listOf("Abdul Mohsen Al Qasim"),
        "علي الحذيفي" to listOf("Ali Al Hudhaifi", "Ali Al Huthaifi"),
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
    fun isHls(station: RadioStation): Boolean = station.isHls ||
        officialStreams[station.url]?.substringBefore('?')?.endsWith(".m3u8", ignoreCase = true) == true
    fun region(station: RadioStation): String? = regionalAreas[StationFamilies.directoryKey(station)]

    fun group(station: RadioStation): StationGroup {
        if (station.url in snrtStreams) return StationGroup.SNRT
        return when (StationFamilies.directoryKey(station)) {
            in regionalAreas -> StationGroup.REGIONAL
            in hitGroupNames -> StationGroup.HIT
            in mediGroupNames -> StationGroup.MEDI1
            else -> StationGroup.OTHER
        }
    }

    fun musicCategory(station: RadioStation): MoroccoCategory? = when (station.name) {
        fayrouz.name -> MoroccoCategory.FAYROUZ
        "Medina FM Tarab", "Medi 1 · Tarab", "Tarab Radio" -> MoroccoCategory.TARAB
        "HIT RADIO · 100% Way Way" -> MoroccoCategory.CHAABI
        "Medina FM Andalou", "Medi 1 · Andalouse" -> MoroccoCategory.ANDALOU
        "HIT RADIO · 100% M’Gharba", "Medi 1 · Nayda" -> MoroccoCategory.MOROCCAN
        "Medina FM Amazigh", "Radio Azawan" -> MoroccoCategory.AMAZIGH
        else -> null
    }

    fun visible(
        tab: StationTab, favorites: Set<String>, query: String, radioFavoritesOnly: Boolean = false,
        directory: List<RadioStation> = emptyList(), bookmarks: List<RadioStation> = emptyList(),
        sort: StationSort = StationSort.DEFAULT, category: MoroccoCategory = MoroccoCategory.ALL,
    ): List<RadioStation> {
        val candidates = when (tab) {
            StationTab.MOROCCO -> {
                val listed = (directory + bookmarks).filter { it.countryCode == "MA" && StationDirectory.isEligible(it) }
                    .distinctBy { it.url }
                val clicksByName = listed.groupBy(StationFamilies::directoryKey)
                    .mapValues { (_, entries) -> entries.maxOf { it.directoryClicks ?: -1 } }
                val community = listed.filter {
                    StationFamilies.directoryKey(it) !in localRadioNames || (radioFavoritesOnly && it.url in favorites)
                }
                val pool = when (category) {
                    MoroccoCategory.ALL -> localRadios + community
                    MoroccoCategory.DIRECTORY -> community.filter { it.url !in localRadioUrls }
                    MoroccoCategory.REGIONAL -> localRadios.filter { region(it) != null }
                    MoroccoCategory.ARABIC -> localRadios.filter {
                        val genre = musicCategory(it)
                        genre != null && genre != MoroccoCategory.AMAZIGH
                    }
                    else -> localRadios.filter { musicCategory(it) == category }
                }
                pool
                    .distinctBy { it.url }
                    .filter { !radioFavoritesOnly || it.url in favorites }
                    .sortedWith(compareByDescending<RadioStation> { audienceShare(it) ?: -1 }
                        .thenByDescending { clicksByName[StationFamilies.directoryKey(it)] ?: -1 })
            }
            StationTab.WORLD -> directory
            StationTab.QURAN -> quranLibrary
            StationTab.FAVORITES -> (all + bookmarks.filter(StationDirectory::isEligible))
                .distinctBy { it.url }.filter { it.url in favorites }
        }
        val nameOrder = compareBy<RadioStation, String>(
            Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }, RadioStation::name,
        ).thenBy { it.url }
        val ordered = when (sort) {
            StationSort.DEFAULT -> candidates
            StationSort.NAME_ASC -> candidates.sortedWith(nameOrder)
            StationSort.NAME_DESC -> candidates.sortedWith(nameOrder.reversed())
            StationSort.GROUPS -> candidates.sortedWith(compareBy<RadioStation>(::group)
                .thenBy { region(it).orEmpty() }.then(nameOrder))
            StationSort.FAVORITES_FIRST -> candidates.sortedByDescending { it.url in favorites }
        }
        val displayed = if (tab == StationTab.MOROCCO && category == MoroccoCategory.ARABIC && sort == StationSort.DEFAULT) {
            ordered.sortedBy(::musicCategory)
        } else if (tab == StationTab.MOROCCO && category == MoroccoCategory.ALL && sort == StationSort.DEFAULT) {
            val (local, national) = ordered.partition { region(it) != null }
            national + local
        } else ordered
        if (query.isBlank()) return displayed
        val key = RadioCatalogue.searchKey(query)
        if (key.isEmpty()) return displayed
        val sections = if (tab == StationTab.QURAN && key.isNotEmpty()) {
            QuranSection.entries.filter { RadioCatalogue.searchKey(it.label).contains(key) }
        } else emptyList()
        return displayed.filter { station ->
            RadioCatalogue.search(station, query) ||
                (tab == StationTab.MOROCCO && group(station) != StationGroup.OTHER && RadioCatalogue.searchKey(group(station).label).contains(key)) ||
                (tab == StationTab.MOROCCO && region(station)?.let { RadioCatalogue.searchKey(it).contains(key) } == true) ||
                (station == fayrouz && listOf("fayroz", "fairouz", "fairouziat").any { it.contains(key) }) ||
                (tab == StationTab.MOROCCO && musicCategory(station)?.let { RadioCatalogue.searchKey(it.label).contains(key) } == true) ||
                aliases[reciterName(station)].orEmpty().any { RadioCatalogue.searchKey(it).contains(key) } ||
                (sections.isNotEmpty() && quranSection(station) in sections)
        }
    }

    fun subtitle(station: RadioStation): String = region(station)?.let {
        if (station in snrtRegional) "SNRT Live · $it" else it
    } ?: when {
        station.url in snrtStreams -> "SNRT Live · Maroc"
        station.url == radio2m.url -> "SOREAD 2M · Maroc"
        station == luxe -> "Luxe Radio · Maroc"
        station.countryCode != null -> StationDirectory.countryName(station.countryCode)
        isRecording(station) -> "${station.tags.single()} · المسجد الحرام · ${station.url.removeSuffix(".mp3").takeLast(10)}"
        station in tajwid -> "المصحف المجوّد · MP3Quran"
        station == badr -> "حفص عن عاصم · MP3Quran"
        else -> station.tags.firstOrNull() ?: "القرآن الكريم · MP3Quran"
    }

    fun listDescription(station: RadioStation): String? = when {
        station.name == "HIT RADIO" && group(station) == StationGroup.HIT -> "Variétés · Hits"
        station.countryCode != null -> listOfNotNull(station.tags.firstOrNull(), StationDirectory.countryName(station.countryCode))
            .distinct().joinToString(" · ")
        station.tags.isNotEmpty() || region(station) != null || isReciter(station) -> subtitle(station)
        else -> null
    }

    private const val DEFAULT_ARTWORK = "android.resource://com.autodrive.radiomaroc/drawable/ic_radio"
    fun defaultArtwork(): Uri = Uri.parse(DEFAULT_ARTWORK)

    fun metadata(station: RadioStation): MediaMetadata = MediaMetadata.Builder()
        .setTitle(station.name)
        .setDisplayTitle(station.name)
        .setArtist(subtitle(station))
        .setSubtitle(subtitle(station))
        .setStation(station.name)
        .setArtworkUri(Uri.parse(station.logoUrl?.let(RadioStation::validUrl) ?: DEFAULT_ARTWORK))
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .build()

    fun item(station: RadioStation): MediaItem = MediaItem.Builder()
        .setMediaId(station.url)
        .setUri(streamUrl(station))
        .setMediaMetadata(metadata(station))
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
