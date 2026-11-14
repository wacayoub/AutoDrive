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
    FOR_YOU("Pour vous"), MOROCCO("Radio Maroc"), QURAN("القرآن"), WORLD("Monde"), FAVORITES("Favoris");

    companion object {
        fun restore(name: String?): StationTab = when (name) {
            null -> FOR_YOU
            "TAJWID", "HARAM" -> QURAN
            else -> entries.firstOrNull { it.name == name } ?: MOROCCO
        }
    }
}

internal enum class QuranSection(val label: String) {
    MURATTAL("الترتيل"), TAJWID("التجويد"), HARAM("تلاوات الحرم")
}

internal enum class StationSort(val label: String) {
    DEFAULT("Popularité"), NAME_ASC("Nom A–Z"), GROUPS("Par groupe"), FAVORITES_FIRST("Favoris en premier");

    fun labelFor(tab: StationTab) = if (this == DEFAULT && tab == StationTab.FAVORITES) "Mon ordre" else label

    companion object {
        fun options(tab: StationTab) = if (tab == StationTab.FOR_YOU) emptyList() else entries.filter {
            when (it) {
                GROUPS -> tab == StationTab.MOROCCO
                FAVORITES_FIRST -> tab == StationTab.MOROCCO || tab == StationTab.WORLD
                else -> true
            }
        }
    }
}

/** Themes of the Radio Maroc tab, shown as chips. A station can belong to several. */
internal enum class MoroccoCategory(val label: String, val chip: String) {
    ALL("Toutes les radios", "Toutes"),
    NEWS("Infos & débats", "Infos & débats"),
    SPORT("Sport", "Sport"),
    HITS("Hits & variétés", "Hits & variétés"),
    TARAB("Tarab & classiques · طرب", "Tarab · طرب"),
    MOROCCAN("Chaâbi & scène marocaine · شعبي", "Chaâbi · شعبي"),
    ANDALOU("Andalou & soufi · أندلسي", "Andalou · أندلسي"),
    AMAZIGH("Amazigh · أمازيغي", "Amazigh"),
    REGIONAL("Radios régionales", "Régions");

    companion object {
        /** Maps the genres of earlier versions onto the merged themes. */
        fun restore(name: String?): MoroccoCategory = when (name) {
            "FAYROUZ" -> TARAB
            "CHAABI" -> MOROCCAN
            else -> entries.firstOrNull { it.name == name } ?: ALL
        }
    }
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
            logoUrl = "https://medradio.ma/wp-content/uploads/2023/08/cropped-med-fav-jpg.jpg", tags = setOf("Débats")),
        RadioStation("HIT RADIO", "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3",
            logoUrl = StationFamilies.HIT_LOGO, tags = setOf("Musique")),
        RadioStation("Radio Aswat", "https://aswat.ice.infomaniak.ch/aswat-high.mp3",
            logoUrl = "https://radioaswat.ma/wp-content/themes/aswata108/images/logo@2x.png", tags = setOf("Débats")),
        RadioStation("Chada FM", "https://chadafm.ice.infomaniak.ch/chadafm-128.aac",
            logoUrl = "https://chada.ma/wp-content/themes/chada-theme-v1/assets/images/chada-fm-logo.png", tags = setOf("Musique")),
        RadioStation("Radio Mars", "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3",
            logoUrl = "https://radiomars.ma/fr/wp-content/uploads/2026/04/cropped-radio_mars-1.png", tags = setOf("Sport")),
        RadioStation("Medina FM", "https://medinafm.ice.infomaniak.ch/medinafm-64.mp3",
            logoUrl = "https://medinafm.ma/favicon-512.png", tags = setOf("Généraliste")),
        RadioStation("Skyrock Casablanca", "https://icecast.skyrock.net/s/casa_aac_64k",
            logoUrl = "https://www.skyrock.fm/images/logo-skyrock.png", tags = setOf("Rap")),
        RadioStation("Radio Plus", RADIO_PLUS_ID,
            logoUrl = "https://www.haca.ma/sites/default/files/upload/radio%20plus%20agadir_0.jpg", tags = setOf("Généraliste")),
        RadioStation("Radio Azawan", "https://az-maroc.ice.infomaniak.ch/az-maroc-64.aac",
            logoUrl = "https://www.haca.ma/sites/default/files/upload/LOGO%20AZAWAN.PNG", tags = setOf("Musique")),
        RadioStation("U Dance", "https://udance.ice.infomaniak.ch/udance-128.aac",
            logoUrl = "https://bo.uradio.ma/storage/streaming/U_DANCE.webp", tags = setOf("Dance")),
        RadioStation("Medina FM Amazigh", "https://medinaamazigh.ice.infomaniak.ch/medinaamazigh-128.mp3",
            logoUrl = "https://api.medinafm.ma/uploads/11ee4925-5901-4d5f-81f3-23db7b0a3fa6.webp", tags = setOf("Amazigh")),
        RadioStation("Medina FM Tarab", "https://medinatarab.ice.infomaniak.ch/medinatarab-128.mp3",
            logoUrl = "https://api.medinafm.ma/uploads/f0b5829f-c5ed-4acc-a775-d921ecd2edc7.webp", tags = setOf("Tarab")),
        RadioStation("Medina FM Andalou", "https://medinaandalou.ice.infomaniak.ch/medinaandalou-128.mp3",
            logoUrl = "https://medinafm.ma/favicon-512.png", tags = setOf("Andalou")),
        RadioStation("U Radio", "https://uradio-aac.ice.infomaniak.ch/uradio.aac",
            logoUrl = "https://bo.uradio.ma/storage/streaming/U_RADIO.webp",
            tags = setOf("Musique")),
        RadioStation("Radio Manarat", "https://listen.radioking.com/radio/252934/stream/297385",
            tags = setOf("Éducation et culture", "راديو منارات")),
        RadioStation("Ness Radio", "https://radio.nessradio.net:8212/nessradio-hd",
            logoUrl = "https://nessradio.com/wp-content/uploads/2026/09/ness_logo_outline_723px_2026.png", tags = setOf("Musique")),
        fayrouz,
        RadioStation("Tarab Radio", "https://stream.zeno.fm/fy8achbq97zuv", tags = setOf("Webradio indépendante · طرب")),
    )
    val snrtNational = RadioCatalogue.featured.filter { RadioCatalogue.officialPlayer(it) != null }
    private val radio2m = RadioStation("Radio 2M", "https://radio2m.ma/", logoUrl = "https://radio2m.ma/_nuxt/img/Logo@2x.df151f1.png")
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
            logoUrl = "https://capradiotv.com/wp-content/uploads/2023/11/cropped-LOGO-PNG-CAP-RADIO.png",
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
    private val herraz = RadioStation(HerrazRecitations.RECITER, HerrazRecitations.SOURCE)
    private val excludedQuranPaths = setOf("mishary_alafasi", "abdulrahman_alsudaes", "yasser_aldosari")
    private val quran = (RadioCatalogue.featured.filter {
        RadioCatalogue.matches(it, RadioCategory.QURAN) && RadioCatalogue.officialPlayer(it) == null &&
            it.url.substringAfterLast('/') !in excludedQuranPaths
    } + listOf(
        badr, herraz,
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
            "hsh-m-lhr-z" -> 3
            else -> 4
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
        HerrazRecitations.RECITER to listOf("Hicham Hezzaz", "Hicham El Herraz", "Hicham Lharraz",
            "Hicham Elherraz", "Hisham Al Haraz", "هشام لحراز", "هشام الهزاز"),
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
    fun isRecording(station: RadioStation): Boolean = station.url in haram || station == herraz
    fun isReciter(station: RadioStation): Boolean = quranSection(station) != null
    fun quranSection(station: RadioStation): QuranSection? = when {
        station in quran -> QuranSection.MURATTAL
        station in tajwid -> QuranSection.TAJWID
        station.url in haram -> QuranSection.HARAM
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

    // Editorial themes of the national and web radios, keyed by normalised name.
    private val themeNames: Map<String, Set<MoroccoCategory>> = buildMap<String, MutableSet<MoroccoCategory>> {
        fun put(theme: MoroccoCategory, vararg names: String) = names.forEach {
            getOrPut(RadioCatalogue.searchKey(it)) { mutableSetOf() }.add(theme)
        }
        put(MoroccoCategory.NEWS, "Med Radio", "Radio Aswat", "Medi 1 Radio", "Medi 1 · Afrique", "Radio Nationale",
            "Chaîne Inter", "Radio Manarat")
        put(MoroccoCategory.SPORT, "Radio Mars", "Radio Aswat")
        put(MoroccoCategory.HITS, "HIT RADIO", "Chada FM", "U Radio", "U Dance", "Skyrock Casablanca", "Ness Radio",
            "Luxe Radio", "Radio 2M", "Medina FM", "Radio Plus", "HIT RADIO · 100% Urban", "HIT RADIO · 100% TikTok",
            "HIT RADIO · 100% Party", "HIT RADIO · 100% Classic", "HIT RADIO · 100% Marruecos España",
            "Medi 1 · Hits", "Medi 1 · DJ", "Medi 1 · Latino", "Medi 1 · Lounge", "Medi 1 · Jazz",
            "Medi 1 · Acoustique", "Medi 1 · Classique", "Medi 1 · Chanson française")
        put(MoroccoCategory.TARAB, fayrouz.name, "Medina FM Tarab", "Medi 1 · Tarab", "Tarab Radio")
        put(MoroccoCategory.MOROCCAN, "HIT RADIO · 100% Way Way", "HIT RADIO · 100% M'Gharba", "Medi 1 · Nayda")
        put(MoroccoCategory.ANDALOU, "Medina FM Andalou", "Medi 1 · Andalouse", "Medi 1 · Soufi")
        put(MoroccoCategory.AMAZIGH, "Medina FM Amazigh", "Radio Azawan", "Radio Amazighe")
    }
    // Community stations carry free-form genre tags; these words place them in a theme.
    private val tagThemes = listOf(
        MoroccoCategory.NEWS to listOf("news", "talk", "info", "actualit", "اخبار", "أخبار"),
        MoroccoCategory.SPORT to listOf("sport", "رياض"),
        MoroccoCategory.HITS to listOf("pop", "hits", "dance", "rnb", "hiphop", "variet", "top40"),
        MoroccoCategory.TARAB to listOf("tarab", "طرب", "classicalarab", "oriental"),
        MoroccoCategory.MOROCCAN to listOf("chaabi", "شعبي", "gnawa", "rai"),
        MoroccoCategory.ANDALOU to listOf("andalou", "andalus", "أندلس", "sufi", "soufi"),
        MoroccoCategory.AMAZIGH to listOf("amazigh", "berber", "أمازيغ", "tachelhit", "tarifit"),
    )

    private val tagThemeKeys = tagThemes.map { (theme, words) -> theme to words.map(RadioCatalogue::searchKey) }
    // Normalising names and tags is costly; directory lists hold hundreds of stations and chips count them often.
    private val themeCache = java.util.concurrent.ConcurrentHashMap<String, Set<MoroccoCategory>>()

    fun themes(station: RadioStation): Set<MoroccoCategory> = themeCache.getOrPut(station.url + '|' + station.name) {
        val named = themeNames[RadioCatalogue.searchKey(station.name)].orEmpty()
        if (named.isNotEmpty() || station.countryCode == null) return@getOrPut named
        val tags = station.tags.map(RadioCatalogue::searchKey)
        tagThemeKeys.filter { (_, words) -> tags.any { tag -> words.any(tag::contains) } }.map { it.first }.toSet()
    }

    /** Station count per theme chip, from the full Radio Maroc list in one pass. */
    fun themeCounts(all: List<RadioStation>): Map<MoroccoCategory, Int> {
        val counts = mutableMapOf<MoroccoCategory, Int>()
        for (station in all) {
            themes(station).forEach { counts[it] = (counts[it] ?: 0) + 1 }
            if (region(station) != null) counts[MoroccoCategory.REGIONAL] = (counts[MoroccoCategory.REGIONAL] ?: 0) + 1
        }
        counts[MoroccoCategory.ALL] = all.size
        return counts
    }

    /** Arabic and Moroccan music for the Android Auto folder of the same name. */
    fun arabicMusic(): List<RadioStation> = localRadios.filter {
        themes(it).any { theme -> theme in setOf(MoroccoCategory.TARAB, MoroccoCategory.MOROCCAN, MoroccoCategory.ANDALOU) }
    }

    private val cityAreas = mapOf(
        RadioCity.AGADIR to "Souss–Massa", RadioCity.CASABLANCA to "Casablanca–Settat",
        RadioCity.EL_JADIDA to "Casablanca–Settat", RadioCity.FES to "Fès–Meknès", RadioCity.MEKNES to "Fès–Meknès",
        RadioCity.MARRAKECH to "Marrakech–Safi", RadioCity.SAFI to "Marrakech–Safi", RadioCity.NADOR to "Oriental",
        RadioCity.OUJDA to "Oriental", RadioCity.TANGER to "Tanger–Tétouan–Al Hoceïma",
        RadioCity.TETOUAN to "Tanger–Tétouan–Al Hoceïma",
    )

    /** Regional radios of the city chosen for weather and prayer times; the city's own SNRT station first. */
    fun nearby(city: RadioCity): List<RadioStation> {
        val area = cityAreas[city] ?: return emptyList()
        val cityKey = RadioCatalogue.searchKey(city.label)
        return localRadios.filter { region(it) == area }
            .sortedByDescending { RadioCatalogue.searchKey(it.name).contains(cityKey) }
    }

    val quranRadio: RadioStation? get() = snrtNational.firstOrNull { RadioCatalogue.searchKey(it.name) == RadioCatalogue.searchKey("محمد السادس للقرآن الكريم") }
    val herrazStation: RadioStation get() = herraz

    fun named(name: String): RadioStation? = localRadios.firstOrNull { RadioCatalogue.searchKey(it.name) == RadioCatalogue.searchKey(name) }

    /** The most listened national radios, for suggestions. */
    fun popular(): List<RadioStation> = localRadios.filter { audienceShare(it) != null }.sortedByDescending { audienceShare(it) }

    fun visible(
        tab: StationTab, favorites: Set<String>, query: String, radioFavoritesOnly: Boolean = false,
        directory: List<RadioStation> = emptyList(), bookmarks: List<RadioStation> = emptyList(),
        sort: StationSort = StationSort.DEFAULT, category: MoroccoCategory = MoroccoCategory.ALL,
        favoriteOrder: List<String> = emptyList(), quranSection: QuranSection? = null,
    ): List<RadioStation> {
        val candidates = when (tab) {
            StationTab.FOR_YOU -> emptyList()
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
                    MoroccoCategory.REGIONAL -> localRadios.filter { region(it) != null }
                    else -> (localRadios + community.filter { it.url !in localRadioUrls }).filter { category in themes(it) }
                }
                pool
                    .distinctBy { it.url }
                    .filter { !radioFavoritesOnly || it.url in favorites }
                    .sortedWith(compareByDescending<RadioStation> { audienceShare(it) ?: -1 }
                        .thenByDescending { clicksByName[StationFamilies.directoryKey(it)] ?: -1 })
            }
            StationTab.WORLD -> directory
            StationTab.QURAN -> if (quranSection == null) quranLibrary else quranLibrary.filter { quranSection(it) == quranSection }
            StationTab.FAVORITES -> (all + bookmarks.filter(StationDirectory::isEligible))
                .distinctBy { it.url }.filter { it.url in favorites }
                .let { saved -> if (sort == StationSort.DEFAULT) inFavoriteOrder(saved, favoriteOrder) else saved }
        }
        val nameOrder = compareBy<RadioStation, String>(
            Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }, RadioStation::name,
        ).thenBy { it.url }
        val ordered = when (sort) {
            StationSort.DEFAULT -> candidates
            StationSort.NAME_ASC -> candidates.sortedWith(nameOrder)
            StationSort.GROUPS -> candidates.sortedWith(compareBy<RadioStation>(::group)
                .thenBy { region(it).orEmpty() }.then(nameOrder))
            StationSort.FAVORITES_FIRST -> candidates.sortedByDescending { it.url in favorites }
        }
        val displayed = if (tab == StationTab.MOROCCO && category == MoroccoCategory.ALL && sort == StationSort.DEFAULT) {
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
                (tab == StationTab.MOROCCO && themes(station).any { RadioCatalogue.searchKey(it.label).contains(key) }) ||
                aliases[reciterName(station)].orEmpty().any { RadioCatalogue.searchKey(it).contains(key) } ||
                (sections.isNotEmpty() && quranSection(station) in sections)
        }
    }

    /** Favourites in the listener's own preset order; ones never ordered keep catalogue order after them. */
    fun inFavoriteOrder(stations: List<RadioStation>, order: List<String>): List<RadioStation> {
        val rank = order.withIndex().associate { (index, url) -> url to index }
        return stations.sortedBy { rank[it.url] ?: Int.MAX_VALUE }
    }

    fun subtitle(station: RadioStation): String = region(station)?.let {
        if (station in snrtRegional) "SNRT Live · $it" else it
    } ?: when {
        station.url in snrtStreams -> "SNRT Live · Maroc"
        station.url == radio2m.url -> "SOREAD 2M · Maroc"
        station == luxe -> "Luxe Radio · Maroc"
        station.countryCode != null -> StationDirectory.countryName(station.countryCode)
        station in haramStations -> "${station.tags.single()} · المسجد الحرام · ${station.url.removeSuffix(".mp3").takeLast(10)}"
        station == herraz -> HerrazRecitations.NARRATION
        station in tajwid -> "المصحف المجوّد · MP3Quran"
        station == badr -> "حفص عن عاصم · MP3Quran"
        else -> station.tags.firstOrNull() ?: "القرآن الكريم · MP3Quran"
    }

    fun listDescription(station: RadioStation): String? = when {
        station.name == "HIT RADIO" && group(station) == StationGroup.HIT -> "Variétés · Hits"
        station.url == radio2m.url || station.url in snrtStreams || station == luxe -> subtitle(station)
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
        .apply { if (!isRecording(station)) setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION) }
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .build()

    fun item(station: RadioStation): MediaItem = MediaItem.Builder()
        .setMediaId(station.url)
        .setUri(if (station == herraz) HerrazRecitations.chapters.first().url else streamUrl(station))
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

    fun playbackItems(station: RadioStation): List<MediaItem> {
        val entry = item(station)
        if (station != herraz) return listOf(entry)
        return HerrazRecitations.chapters.map { chapter ->
            entry.buildUpon().setUri(chapter.url)
                .setMediaMetadata(entry.mediaMetadata.buildUpon().setSubtitle("سورة ${chapter.name}").build())
                .build()
        }
    }

    fun directoryItem(request: MediaItem): MediaItem? {
        val metadata = request.requestMetadata
        val url = metadata.mediaUri?.toString()?.let(RadioStation::validUrl) ?: return null
        if (url != request.mediaId) return null
        return MediaItem.Builder().setMediaId(url).setUri(url).setMediaMetadata(request.mediaMetadata)
            .apply { if (metadata.extras?.getBoolean("hls") == true) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
    }
}
