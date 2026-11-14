package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioCatalogue
import java.util.Locale

internal object HerrazRecitations {
    const val RECITER = "هشام الهراز"
    const val SOURCE = "https://www.mp3quran.net/ar/hsh-m-lhr-z"
    const val NARRATION = "ورش عن نافع · MP3Quran"
    private const val AUDIO_ROOT = "https://cdn.mp3quran.net/audio/hicham-lharraz/r1"
    private val names = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال",
        "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل",
        "الإسراء", "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور",
        "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة",
        "", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
        "فصلت", "الشورى", "الزخرف", "الدّخان", "الجاثية", "الأحقاف", "محمد", "الفتح",
        "الحجرات", "ق", "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة",
        "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف", "الجمعة", "المنافقون", "التغابن",
        "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج", "نوح", "الجن",
        "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
        "التكوير", "الإنفطار", "المطففين", "الإنشقاق", "البروج", "الطارق", "الأعلى", "الغاشية",
        "الفجر", "البلد", "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق",
        "القدر", "البينة", "الزلزلة", "العاديات", "القارعة", "التكاثر", "العصر", "الهمزة",
        "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر", "المسد", "الإخلاص",
        "الفلق", "الناس",
    )

    // Common French and English transliterations, one per surah, for searching without an Arabic keyboard.
    private val latinNames = listOf(
        "Al-Fatiha", "Al-Baqara", "Al-Imran", "An-Nisa", "Al-Maida", "Al-Anam", "Al-Araf", "Al-Anfal",
        "At-Tawba", "Yunus", "Hud", "Yusuf", "Ar-Rad", "Ibrahim", "Al-Hijr", "An-Nahl",
        "Al-Isra", "Al-Kahf", "Maryam", "Ta-Ha", "Al-Anbiya", "Al-Hajj", "Al-Muminun", "An-Nur",
        "Al-Furqan", "Ash-Shuara", "An-Naml", "Al-Qasas", "Al-Ankabut", "Ar-Rum", "Luqman", "As-Sajda",
        "Al-Ahzab", "Saba", "Fatir", "Ya-Sin", "As-Saffat", "Sad", "Az-Zumar", "Ghafir",
        "Fussilat", "Ash-Shura", "Az-Zukhruf", "Ad-Dukhan", "Al-Jathiya", "Al-Ahqaf", "Muhammad", "Al-Fath",
        "Al-Hujurat", "Qaf", "Adh-Dhariyat", "At-Tur", "An-Najm", "Al-Qamar", "Ar-Rahman", "Al-Waqia",
        "Al-Hadid", "Al-Mujadila", "Al-Hashr", "Al-Mumtahana", "As-Saff", "Al-Jumua", "Al-Munafiqun", "At-Taghabun",
        "At-Talaq", "At-Tahrim", "Al-Mulk", "Al-Qalam", "Al-Haqqa", "Al-Maarij", "Nuh", "Al-Jinn",
        "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyama", "Al-Insan", "Al-Mursalat", "An-Naba", "An-Naziat", "Abasa",
        "At-Takwir", "Al-Infitar", "Al-Mutaffifin", "Al-Inshiqaq", "Al-Buruj", "At-Tariq", "Al-Ala", "Al-Ghashiya",
        "Al-Fajr", "Al-Balad", "Ash-Shams", "Al-Layl", "Ad-Duha", "Ash-Sharh", "At-Tin", "Al-Alaq",
        "Al-Qadr", "Al-Bayyina", "Az-Zalzala", "Al-Adiyat", "Al-Qaria", "At-Takathur", "Al-Asr", "Al-Humaza",
        "Al-Fil", "Quraysh", "Al-Maun", "Al-Kawthar", "Al-Kafirun", "An-Nasr", "Al-Masad", "Al-Ikhlas",
        "Al-Falaq", "An-Nas",
    )

    data class Chapter(val number: Int, val name: String, val url: String) {
        val latin: String get() = latinNames[number - 1]
    }
    val chapters = names.mapIndexedNotNull { index, name ->
        if (name.isBlank()) null else {
            val number = index + 1
            Chapter(number, name, "$AUDIO_ROOT/${String.format(Locale.ROOT, "%03d", number)}.mp3")
        }
    }

    /** Playlist position of surah [number], or null when this recitation does not include it. */
    fun indexOf(number: Int): Int? = chapters.indexOfFirst { it.number == number }.takeIf { it >= 0 }

    fun chapter(number: Int): Chapter? = chapters.firstOrNull { it.number == number }

    /**
     * The surah a search names, in Arabic or Latin letters ("الكهف", "kahf", "Al-Kahf", "ya sin").
     * Exact names win over prefixes, so "nas" finds An-Nas rather than An-Nasr.
     */
    fun find(query: String): Chapter? {
        val key = RadioCatalogue.searchKey(query).removePrefix("sourate").removePrefix("surah").removePrefix("سورة")
        if (key.length < 3) return null
        fun cores(chapter: Chapter): List<String> {
            val latinKey = RadioCatalogue.searchKey(chapter.latin)
            val article = Regex("^(adh|ash|al|an|ar|as|at|ad|az)").find(latinKey)?.value
                ?.takeIf { chapter.latin.contains('-') && !chapter.latin.startsWith("Ta-") && !chapter.latin.startsWith("Ya-") }
            val arabicKey = RadioCatalogue.searchKey(chapter.name)
            return listOfNotNull(latinKey, article?.let { latinKey.removePrefix(it) }, arabicKey, arabicKey.removePrefix("ال"))
        }
        return chapters.firstOrNull { chapter -> cores(chapter).any { it == key } }
            ?: chapters.firstOrNull { chapter -> cores(chapter).any { it.startsWith(key) } }
            ?: chapters.firstOrNull { chapter -> cores(chapter).any { it.contains(key) } }
    }
}
