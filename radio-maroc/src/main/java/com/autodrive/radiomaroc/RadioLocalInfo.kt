package com.autodrive.radiomaroc

import com.autodrive.launcher.readUpTo
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.math.roundToInt

internal enum class RadioCity(val label: String, val habousCode: Int, val latitude: Double, val longitude: Double) {
    AGADIR("Agadir", 117, 30.4278, -9.5981),
    CASABLANCA("Casablanca", 58, 33.5731, -7.5898),
    EL_JADIDA("El Jadida", 66, 33.2316, -8.5007),
    FES("Fès", 81, 34.0331, -5.0003),
    KENITRA("Kénitra", 7, 34.2610, -6.5802),
    MARRAKECH("Marrakech", 104, 31.6295, -7.9811),
    MEKNES("Meknès", 99, 33.8730, -5.5407),
    NADOR("Nador", 39, 35.1681, -2.9335),
    OUJDA("Oujda", 31, 34.6814, -1.9086),
    RABAT("Rabat", 1, 34.0209, -6.8416),
    SAFI("Safi", 111, 32.2994, -9.2372),
    TANGER("Tanger", 14, 35.7595, -5.8340),
    TETOUAN("Tétouan", 15, 35.5785, -5.3684);

    companion object {
        fun restore(value: String?): RadioCity = entries.firstOrNull { it.name == value } ?: CASABLANCA
    }
}

internal data class RadioWeather(
    val at: LocalDateTime,
    val degrees: Int,
    val condition: WeatherCondition,
    val low: Int?,
    val high: Int?,
    val isDay: Boolean?,
)

internal enum class WeatherCondition(val label: String) {
    CLEAR("Ciel dégagé"), PARTLY_CLOUDY("Éclaircies"), CLOUDY("Couvert"), FOG("Brouillard"),
    DRIZZLE("Bruine"), RAIN("Pluie"), SNOW("Neige"), SHOWERS("Averses"), STORM("Orages"),
    UNKNOWN("Conditions non précisées");

    companion object {
        fun fromCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            80, 81, 82 -> SHOWERS
            95, 96, 97, 99 -> STORM
            else -> UNKNOWN
        }
    }
}

internal enum class Prayer(val arabic: String, val column: String) {
    FAJR("الفجر", "Fajr"), DHUHR("الظهر", "Dhuhr"), ASR("العصر", "Asr"),
    MAGHRIB("المغرب", "Maghrib"), ISHA("العشاء", "Ishae");

    val label: String get() = if (this == ISHA) "Icha" else column
}

internal data class PrayerMoment(val prayer: Prayer, val dateTime: LocalDateTime)
internal data class PrayerStatus(val date: LocalDate, val current: PrayerMoment?, val next: PrayerMoment?)

internal class HabousCalendar(
    val month: YearMonth,
    val offset: ZoneOffset,
    private val columns: Map<Prayer, Int>,
    private val cities: Map<Int, JSONArray>,
) {
    fun prayers(city: RadioCity, date: LocalDate): List<PrayerMoment> {
        if (YearMonth.from(date) != month) return emptyList()
        val times = cities[city.habousCode]?.optJSONArray(date.dayOfMonth - 1) ?: return emptyList()
        val moments = Prayer.entries.map { prayer ->
            val text = times.optString(columns.getValue(prayer))
            val time = runCatching { LocalTime.parse(text) }.getOrNull() ?: return emptyList()
            PrayerMoment(prayer, date.atTime(time))
        }
        return moments.takeIf { list -> list.zipWithNext().all { (a, b) -> a.dateTime < b.dateTime } }.orEmpty()
    }
}

internal object RadioLocalInfo {
    // Décret marocain 2.26.530, en vigueur depuis le 20 septembre 2026.
    val moroccoTime: ZoneOffset = ZoneOffset.UTC
    const val HABOUS_SITE = "https://www.habous.gov.ma/"
    const val WEATHER_SITE = "https://open-meteo.com/"

    fun weather(city: RadioCity): RadioWeather? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}" +
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_min,temperature_2m_max&forecast_days=1&timezone=GMT"
        return parseWeather(download(url, 32 * 1024))
    }

    fun calendar(month: YearMonth): HabousCalendar? =
        parseCalendar(download("${HABOUS_SITE}data/horaires-$month.js", 512 * 1024), month)

    fun parseWeather(json: String): RadioWeather? {
        val data = JSONObject(json)
        val current = data.optJSONObject("current") ?: return null
        val at = runCatching { LocalDateTime.parse(current.optString("time")) }.getOrNull() ?: return null
        val temperature = current.optDouble("temperature_2m").takeIf { it in -100.0..70.0 } ?: return null
        val condition = WeatherCondition.fromCode(current.optInt("weather_code", -1))
        val isDay = current.optInt("is_day", -1).takeIf { it in 0..1 }?.let { it == 1 }
        val daily = data.optJSONObject("daily")
        val dailyMatches = daily?.optJSONArray("time")?.optString(0) == at.toLocalDate().toString()
        val low = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeIf { dailyMatches && it in -100.0..70.0 }
        val high = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeIf { dailyMatches && it in -100.0..70.0 }
        return RadioWeather(at, temperature.roundToInt(), condition, low?.roundToInt(), high?.roundToInt(), isDay)
    }

    fun parseCalendar(script: String, requestedMonth: YearMonth): HabousCalendar? {
        val prefix = "(window.HORAIRES_MOIS=window.HORAIRES_MOIS||[]).push("
        val body = script.trim().trimStart('\uFEFF')
        if (!body.startsWith(prefix) || !body.endsWith(");")) return null
        val data = JSONObject(body.removePrefix(prefix).removeSuffix(");"))
        if (data.optInt("annee") != requestedMonth.year || data.optInt("mois") != requestedMonth.monthValue) return null
        val rawOffset = data.optString("fuseau").removePrefix("GMT")
        val offset = runCatching { ZoneOffset.of(rawOffset.ifEmpty { "Z" }) }.getOrNull() ?: return null
        if (!data.optString("fuseau").startsWith("GMT")) return null
        val cols = data.optJSONArray("cols") ?: return null
        val names = (0 until cols.length()).map(cols::optString)
        val columns = Prayer.entries.associateWith { names.indexOf(it.column) }
        if (columns.values.any { it < 0 }) return null
        val cities = data.optJSONArray("villes") ?: return null
        val rows = (0 until cities.length()).mapNotNull { index ->
            val city = cities.optJSONObject(index) ?: return@mapNotNull null
            val hours = city.optJSONArray("h") ?: return@mapNotNull null
            city.optInt("c") to hours
        }.toMap()
        return HabousCalendar(requestedMonth, offset, columns, rows)
    }

    fun prayerStatus(
        now: LocalDateTime,
        yesterday: List<PrayerMoment>,
        today: List<PrayerMoment>,
        tomorrow: List<PrayerMoment>,
    ): PrayerStatus {
        val date = now.toLocalDate()
        if (today.isEmpty()) return PrayerStatus(date, null, null)
        val current = today.lastOrNull { it.dateTime <= now } ?: yesterday.lastOrNull()
        val next = today.firstOrNull { it.dateTime > now } ?: tomorrow.firstOrNull()
        return PrayerStatus(date, current, next)
    }

    private fun download(url: String, limit: Int): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 7_000
            connection.setRequestProperty("User-Agent", "RadioMarocCoran/2.5.18")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Source indisponible")
            val bytes = connection.inputStream.use { it.readUpTo(limit + 1) }
            if (bytes.size > limit) throw IOException("Réponse trop volumineuse")
            return bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}
