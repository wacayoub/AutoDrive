package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioStation

/**
 * The whole Quran by each reciter of the Coran tab, surah by surah, from the reciter's own MP3Quran folder
 * (cdn.mp3quran.net/audio/<folder>/001.mp3 … 114.mp3). Unlike the live radios, it starts at the surah the listener
 * picks and plays on to the end. Folders come from the MP3Quran API (v3, reciters) and each one answered with
 * audio on 15 November 2026.
 */
internal object Mushaf {
    data class Recitation(
        /** The live radio of the same reciter in the Coran tab, used for its rank and its portrait. */
        val radio: String,
        val reciter: String,
        val narration: String,
        val folder: String,
        val missing: Set<Int> = emptySet(),
    ) {
        val surahs: List<Int> = (1..114).filter { it !in missing }
        val station = RadioStation("$reciter · $narration", "https://cdn.mp3quran.net/audio/$folder/",
            tags = setOf("المصحف كاملاً · ${surahs.size} سورة · MP3Quran"))
    }

    private const val HAFS = "حفص"
    private const val WARSH = "ورش"
    private const val MOJAWWAD = "مجوّد"

    val recitations = listOf(
        Recitation("mishary_alafasi", "مشاري راشد العفاسي", HAFS, "mishary-alafasy/r1"),
        Recitation("abdulbasit_abdulsamad", "عبد الباسط عبد الصمد", HAFS, "abdulbasit-abdulsamad/r3"),
        Recitation("abdulrahman_alsudaes", "عبد الرحمن السديس", HAFS, "abdulrahman-sudais/r1"),
        Recitation("maher", "ماهر المعيقلي", HAFS, "maher-muaiqly/r1"),
        Recitation("yasser_aldosari", "ياسر الدوسري", HAFS, "yasser-dosari/r1"),
        Recitation("omar_alqazabri", "عمر القزابري", WARSH, "omar-kazabri/r1"),
        Recitation("hsh-m-lhr-z", HerrazRecitations.RECITER, WARSH, "hicham-lharraz/r1", setOf(33)),
        Recitation("aloyoon_alkoshi", "العيون الكوشي", WARSH, "laayoun-kouchi/r1"),
        Recitation("mohammed_siddiq_alminshawi", "محمد صديق المنشاوي", HAFS, "muhammad-minshawi/r1"),
        Recitation("mahmoud_khalil_alhussary", "محمود خليل الحصري", HAFS, "mahmoud-husary/r1"),
        Recitation("saad_alghamdi", "سعد الغامدي", HAFS, "saad-ghamdi/r1"),
        Recitation("saud_alshuraim", "سعود الشريم", HAFS, "saud-shuraim/r1"),
        Recitation("ahmad_alajmy", "أحمد العجمي", HAFS, "ahmad-ajmi/r1"),
        // MP3Quran has 91 of the 114 surahs by Hazza Al-Balushi.
        Recitation("hazza", "هزاع البلوشي", HAFS, "hazza-balushi/r1",
            setOf(2, 3, 4, 5, 7, 9, 10, 11, 16, 23, 24, 26, 27, 28, 33, 48, 58, 59, 60, 62, 64, 65, 66)),
        Recitation("bader", "بدر التركي", HAFS, "badr-turki/r1"),
        Recitation("nasser_alqatami", "ناصر القطامي", HAFS, "nasser-qatami/r1"),
        Recitation("fares_abbad", "فارس عباد", HAFS, "fares-abbad/r1"),
        Recitation("mohammed_ayyub", "محمد أيوب", HAFS, "muhammad-ayyub/r1"),
        Recitation("abdulbasit_abdulsamad_warsh", "عبد الباسط عبد الصمد", WARSH, "abdulbasit-abdulsamad/r2"),
        Recitation("mahmoud_khalil_alhussary_warsh", "محمود خليل الحصري", WARSH, "mahmoud-husary/r3"),
        Recitation("ali_jaber", "علي جابر", HAFS, "ali-jaber/r1"),
        Recitation("khalid_aljileel", "خالد الجليل", HAFS, "khalid-jalil/r1"),
        Recitation("mohammed_jibreel", "محمد جبريل", HAFS, "muhammad-jibreel/r1"),
        Recitation("abdullah_basfer", "عبد الله بصفر", HAFS, "abdullah-basfar/r1"),
        Recitation("salah_albudair", "صلاح البدير", HAFS, "salah-budair/r1"),
        Recitation("ali_alhuthaifi", "علي الحذيفي", HAFS, "ali-hudhaifi/r1"),
        Recitation("abdullah_aljohany", "عبد الله الجهني", HAFS, "abdullah-juhani/r1"),
        Recitation("mustafa_ismail", "مصطفى إسماعيل", HAFS, "mustafa-ismail/r1"),
        Recitation("mohammad_altablaway", "محمد الطبلاوي", HAFS, "muhammad-tablawi/r1"),
        Recitation("ahmad_nauina", "أحمد نعينع", HAFS, "ahmad-naina/r1"),
        Recitation("ibrahim_aldosari", "إبراهيم الدوسري", HAFS, "ibrahim-dosari/r2"),
        Recitation("abdulrasheed_soufi_khalaf", "عبد الرشيد صوفي", "خلف", "abdulrasheed-sufi/r1"),
        Recitation("abdulmohsen_alqasim", "عبد المحسن القاسم", HAFS, "abdulmohsen-qasim/r1"),
        Recitation("abdulbasit_abdulsamad_mojawwad", "عبد الباسط عبد الصمد", MOJAWWAD, "abdulbasit-abdulsamad/r1"),
        Recitation("mohammed_siddiq_alminshawi_mojawwad", "محمد صديق المنشاوي", MOJAWWAD, "muhammad-minshawi/r2"),
        Recitation("mahmoud_khalil_alhussary_mojawwad", "محمود خليل الحصري", MOJAWWAD, "mahmoud-husary/r2"),
        Recitation("mahmoud_ali__albanna_mojawwad", "محمود علي البنا", MOJAWWAD, "mahmoud-banna/r2"),
    )

    private val byUrl = recitations.associateBy { it.station.url }

    fun of(station: RadioStation?): Recitation? = station?.let { byUrl[it.url] }
    fun of(url: String?): Recitation? = url?.let(byUrl::get)

    /** Playlist position of surah [number] in [recitation], or 0 to start at its first surah. */
    fun startIndex(recitation: Recitation, number: Int?): Int =
        number?.let { recitation.surahs.indexOf(it) }?.takeIf { it >= 0 } ?: 0

    fun url(recitation: Recitation, number: Int) = HerrazRecitations.chapterUrl(recitation.station.url, number)
}

/** Live MP3Quran radios of the morning and evening remembrances. */
internal object Adhkar {
    val sabah = RadioStation("أذكار الصباح", "https://backup.qurango.net/radio/athkar_sabah",
        tags = setOf("أذكار · بث مباشر · MP3Quran"))
    val masa = RadioStation("أذكار المساء", "https://backup.qurango.net/radio/athkar_masa",
        tags = setOf("أذكار · بث مباشر · MP3Quran"))
    val stations = listOf(sabah, masa)
}
