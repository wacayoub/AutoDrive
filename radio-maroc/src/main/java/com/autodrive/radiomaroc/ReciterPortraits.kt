package com.autodrive.radiomaroc

import android.net.Uri
import com.autodrive.launcher.RadioStation

internal data class ReciterPortrait(
    val image: Int,
    val file: String,
    val author: String,
    val license: String,
) {
    val sourceUrl: String get() = "https://commons.wikimedia.org/wiki/File:${Uri.encode(file.replace(' ', '_'))}"
    val licenseUrl: String? get() = when (license) {
        "CC BY-SA 3.0" -> "https://creativecommons.org/licenses/by-sa/3.0/"
        "CC BY-SA 4.0" -> "https://creativecommons.org/licenses/by-sa/4.0/"
        "CC0 1.0" -> "https://creativecommons.org/publicdomain/zero/1.0/"
        else -> null
    }
}

internal object ReciterPortraits {
    private val portraits = mapOf(
        "بدر التركي" to ReciterPortrait(R.drawable.reciter_badr, "Photo of badr al-turki in 2023.jpg", "Abo.ibrahim0", "CC0 1.0"),
        "العيون الكوشي" to ReciterPortrait(R.drawable.reciter_kouchi, "صورة للقارئ الشيخ العيون الكوشي.jpeg", "Hamdaouiabde", "CC BY-SA 4.0"),
        "عبد الباسط عبد الصمد" to ReciterPortrait(R.drawable.reciter_basit, "Abdul Basit Abd us-Samad.jpg", "Auteur inconnu · 1956", "Domaine public"),
        "محمد صديق المنشاوي" to ReciterPortrait(R.drawable.reciter_minshawi, "Minshawy.jpg", "Radio du Coran égyptienne · auteur inconnu", "Domaine public"),
        "محمود خليل الحصري" to ReciterPortrait(R.drawable.reciter_hussary, "Hussary.jpg", "Zubairkhan1", "Domaine public"),
        "ماهر المعيقلي" to ReciterPortrait(R.drawable.reciter_maher, "Mahir Al-Muaiqly.png", "Sazwanmisuari", "CC0 1.0"),
        "سعود الشريم" to ReciterPortrait(R.drawable.reciter_shuraim, "Saud Shuraim.png", "Sazwanmisuari", "CC0 1.0"),
        "أحمد العجمي" to ReciterPortrait(R.drawable.reciter_ajmi, "Ahmad bin Ali Al-Ajmi.png", "أحمد زين", "CC BY-SA 3.0"),
        "ياسر الدوسري" to ReciterPortrait(R.drawable.reciter_dosari, "Yasser Al-Dosari.png", "أحمد زين", "CC BY-SA 3.0"),
        "عبد الله الجهني" to ReciterPortrait(R.drawable.reciter_juhany, "Abdullah Al Juhany (Cropped).png", "الشيخ هيثم الدخين", "CC BY-SA 4.0"),
    )

    fun forStation(station: RadioStation): ReciterPortrait? = portraits[Stations.reciterName(station)]
}
