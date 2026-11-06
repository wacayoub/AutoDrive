package com.autodrive.launcher

import java.net.URI
import java.text.Normalizer
import java.util.Locale

internal enum class RadioCategory(val title: String, val description: String) {
    ALL("Toutes", "Afficher toutes les radios"), SNRT("SNRT", "Afficher les radios SNRT"),
    QURAN("القرآن", "Afficher les radios du Coran"), MUSIC("Musique", "Afficher les radios de musique"),
    NEWS("Infos / Débats", "Afficher les radios d’information"), SPORT("Sport", "Afficher les radios de sport"),
    OTHER("Autres", "Afficher les autres radios")
}

/** Small editorial selection, sourced from SNRT Live and MP3Quran's public Arabic radio API.
 * SNRT entries are official web players, explicitly not direct MP3/HLS feeds.
 */
internal object RadioCatalogue {
    private data class Entry(val station: RadioStation, val category: RadioCategory, val aliases: String,
        val officialPlayer: Boolean = false)
    private fun snrt(name: String, slug: String, logo: String, aliases: String) = Entry(
        RadioStation(name, "https://snrtlive.ma/fr/$slug", logoUrl = "https://www.snrt.ma/sites/default/files/2023-04/$logo.png"),
        RadioCategory.SNRT, aliases, officialPlayer = true)
    private fun quran(name: String, path: String, aliases: String) = Entry(
        RadioStation(name, "https://backup.qurango.net/radio/$path"), RadioCategory.QURAN, aliases)
    private val entries = listOf(
        snrt("Radio Nationale", "alidaa-alwatania", "idaawataniya", "SNRT الإذاعة الوطنية Al Idaa Al Watania"),
        snrt("Radio Amazighe", "alidaa-alamazighia", "idaatamazight", "SNRT الإذاعة الأمازيغية Amazigh"),
        snrt("Chaîne Inter", "chaine-inter", "chaineinter", "SNRT الدولية Internationale"),
        snrt("محمد السادس للقرآن الكريم", "idaat-mohammed-assadiss", "idaatmohammedassadiss", "SNRT Mohammed VI Assadissa Coran Quran إذاعة محمد السادس"),
        quran("مشاري راشد العفاسي", "mishary_alafasi", "Mishary Rashid Alafasy Afasi مشاري العفاسي"),
        quran("ماهر المعيقلي", "maher", "Maher Al Muaiqly Mueaqly"),
        quran("عبد الرحمن السديس", "abdulrahman_alsudaes", "Abdulrahman Al Sudais عبدالرحمن السديس"),
        quran("ياسر الدوسري", "yasser_aldosari", "Yasser Al Dossari Dosari"),
        quran("محمود خليل الحصري", "mahmoud_khalil_alhussary", "Mahmoud Khalil Al Hussary"),
        quran("محمد صديق المنشاوي", "mohammed_siddiq_alminshawi", "Mohamed Siddiq Al Minshawi"),
        quran("عبد الباسط عبد الصمد", "abdulbasit_abdulsamad", "Abdulbasit Abdussamad عبدالباسط عبدالصمد"),
        quran("سعد الغامدي", "saad_alghamdi", "Saad Al Ghamdi"),
        quran("سعود الشريم", "saud_alshuraim", "Saud Al Shuraim"),
        quran("عمر القزابري", "omar_alqazabri", "Omar Al Kazabri Qazabri Maroc"),
        quran("العيون الكوشي", "aloyoon_alkoshi", "Laayoun El Kouchi Al Oyoun Al Koshi Maroc"),
        quran("أحمد العجمي", "ahmad_alajmy", "Ahmed Al Ajmi Ahmad"),
    )
    val featured: List<RadioStation> = entries.map { it.station }
    private fun key(url: String) = runCatching { URI(url).let { "${it.host.lowercase(Locale.ROOT)}${it.path}" } }.getOrDefault(url)
    private fun entry(station: RadioStation?) = station?.let { value -> entries.firstOrNull { key(it.station.url) == key(value.url) } }
    fun officialPlayer(station: RadioStation?): String? = entry(station)?.takeIf { it.officialPlayer }?.station?.url
    fun matches(station: RadioStation, category: RadioCategory): Boolean = when (category) {
        RadioCategory.ALL -> true
        RadioCategory.SNRT -> entry(station)?.category == category
        RadioCategory.QURAN -> entry(station)?.category == category || station.url == featured[3].url || station.tags.any { searchKey(it) in setOf("quran", "coran", "القران", "قران") }
        RadioCategory.MUSIC -> station.tags.any { searchKey(it) in musicTags } || key(station.url) == "hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3"
        RadioCategory.NEWS -> station.tags.any { searchKey(it) in newsTags }
        RadioCategory.SPORT -> station.tags.any { searchKey(it) in setOf("sport", "sports", "football") } || key(station.url) == "radiomars.ice.infomaniak.ch/radiomars-128.mp3"
        RadioCategory.OTHER -> listOf(RadioCategory.SNRT, RadioCategory.QURAN, RadioCategory.MUSIC, RadioCategory.NEWS, RadioCategory.SPORT).none { matches(station, it) }
    }
    private val musicTags = setOf("music", "musique", "pop", "rock", "hits", "top40", "chaabi", "rai", "rap", "hiphop", "dance", "jazz", "arabicmusic")
    private val newsTags = setOf("news", "talk", "information", "infos", "actualites", "اخبار", "debats")
    fun parseTags(raw: String): Set<String> = raw.take(2048).split(',').asSequence()
        .map { it.trim().take(60) }.filter { it.isNotEmpty() }.take(24).toSet()
    fun subtitle(station: RadioStation): String? = when (entry(station)?.category) {
        RadioCategory.SNRT -> "SNRT · lecteur officiel"
        RadioCategory.QURAN -> "القرآن الكريم · MP3Quran"
        else -> listOf(RadioCategory.MUSIC, RadioCategory.NEWS, RadioCategory.SPORT).filter { matches(station, it) }.joinToString(" · ") { it.title }.ifEmpty { null }
    }
    fun search(station: RadioStation, query: String): Boolean {
        val extra = entry(station)?.let { "${it.aliases} ${if (it.category == RadioCategory.QURAN) "Coran Quran قرآن القراء المقرئين" else ""}" }.orEmpty()
        return searchKey("${station.name} ${station.tags.joinToString(" ")} $extra").contains(searchKey(query))
    }
    fun searchKey(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace('ى', 'ي').filter { it.isLetterOrDigit() && it != 'ـ' }

    /** Keep meaningful station/genre/city names; remove directory boilerplate and bitrate debris. */
    fun cleanName(raw: String): String {
        val cleaned = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        .replace(Regex("[\\p{Cc}\\p{Cf}]"), " ")
        .replace('_', ' ')
        .replace(Regex("(?i)^\\s*(?:(?:MA|MRC)\\s*[:|–-]+\\s*|\\[MA\\]\\s*)"), "")
        .replace(Regex("(?i)\\s*[\\[(]?\\s*\\d{2,3}\\s*k(?:b(?:it)?/?s|bps)?\\s*[\\])]?"), " ")
        .replace(Regex("\\s+"), " ").trim(' ', '-', '|', '·')
        val readable = when (searchKey(cleaned)) {
            "hitradiomorocco", "hitradiomaroc", "hitradio" -> "HIT RADIO"
            "radioplusagadirfm924" -> "Radio Plus Agadir"
            "raplbeldimarochiphopmusicfrommoroccofrar" -> "Rap Lbeldi"
            "mfmradio", "radiomfm" -> "MFM Radio"
            "medi1radio" -> "Medi 1 Radio"
            "medinafm" -> "Medina FM"
            "marrakechplus" -> "Marrakech Plus"
            "capradio" -> "Cap Radio"
            "uradio" -> "U Radio"
            "zinebladi" -> "Zine Bladi"
            else -> cleaned
        }
        return readable.take(60)
    }

    fun polish(station: RadioStation): RadioStation {
        val cleaned = cleanName(station.name)
        val canonical = when (key(station.url)) {
            "hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3" -> "HIT RADIO"
            "radiomars.ice.infomaniak.ch/radiomars-128.mp3" -> "Radio Mars"
            "broadcast.ice.infomaniak.ch/aswat-high.mp3", "aswat.ice.infomaniak.ch/aswat-high.mp3" -> "Radio Aswat"
            else -> null
        }
        // Recognise common catalogue spellings without replacing a personal station label.
        val standard = searchKey(cleaned) in setOf("hitradio", "radiomars", "mars", "aswat", "aswatfm", "radioaswat")
        return station.copy(name = if (canonical != null && standard) canonical else cleaned.ifBlank { "Radio" })
    }
}
