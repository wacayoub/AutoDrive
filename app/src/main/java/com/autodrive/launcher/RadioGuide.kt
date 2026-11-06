package com.autodrive.launcher

import java.net.URI
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.jsoup.Jsoup

/** Morocco restored GMT on 20 September 2026 at 02:00 former local time (01:00 UTC).
 * Keep an old AI-box tzdb from applying the obsolete permanent +01 rule to current guides.
 * Primary source: https://www.cg.gov.ma/fr/node/13036 . No device setting is modified.
 */
internal object MoroccoRadioTime {
    private val legacy = ZoneId.of("Africa/Casablanca")
    private val reform = Instant.parse("2026-09-20T01:00:00Z")
    fun zoneAt(instant: Instant): ZoneId = if (!instant.isBefore(reform)) ZoneOffset.UTC else legacy
    fun atLocal(date: LocalDate, minute: Int): Instant {
        val local = date.atTime(minute / 60, minute % 60)
        val prior = local.atZone(legacy).toInstant()
        return if (!prior.isBefore(reform)) local.atZone(ZoneOffset.UTC).toInstant() else prior
    }
}

/** Published weekly planning, never evidence that a programme is actually on air. */
internal data class RadioProgramme(val day: DayOfWeek, val startMinute: Int, val endMinute: Int, val title: String) {
    init { require(startMinute in 0..1439 && endMinute in 0..1440 && startMinute != endMinute && title.isNotBlank() && title.length <= 160) }
}
internal data class ProgrammeOccurrence(val programme: RadioProgramme, val start: Instant, val end: Instant) {
    fun progress(now: Instant): Int = ((Duration.between(start, now).seconds * 100 /
        Duration.between(start, end).seconds.coerceAtLeast(1)).toInt()).coerceIn(0, 100)
}
internal enum class RadioGuideSource(val title: String, val page: String) {
    ASWAT("Radio Aswat", "https://radioaswat.ma/#grille"),
    MARS("Radio Mars", "https://radiomars.ma/%d8%a8%d8%b1%d8%a7%d9%85%d8%ac%d9%86%d8%a7/"),
    HIT("HIT RADIO", "https://www.hitradio.ma/"),
    MFM("Radio MFM", "https://mfmradio.ma/");

    companion object {
        fun forStation(station: RadioStation?): RadioGuideSource? {
            val uri = runCatching { URI(station?.url ?: return null) }.getOrNull() ?: return null
            if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
            val host = uri.host?.lowercase() ?: return null
            val path = uri.path.orEmpty().lowercase()
            return when {
                host in setOf("aswat.ice.infomaniak.ch", "broadcast.ice.infomaniak.ch") && path == "/aswat-high.mp3" -> ASWAT
                host == "radiomars.ice.infomaniak.ch" && path == "/radiomars-128.mp3" -> MARS
                host == "hitradio-maroc.ice.infomaniak.ch" && path == "/hitradio-maroc-128.mp3" -> HIT
                host.endsWith(".mfmradio.ma") || host == "mfmradio.ma" -> MFM
                else -> null
            }
        }
    }
}
internal object RadioProgrammeParser {
    private val times = Regex("(?<![0-9])([0-2]?[0-9]):([0-5][0-9])(?![0-9])")
    fun minute(value: String): Int? {
        val match = times.matchEntire(value.trim()) ?: return null
        val hour = match.groupValues[1].toInt(); val minute = match.groupValues[2].toInt()
        return (hour * 60 + minute).takeIf { hour in 0..23 || (hour == 24 && minute == 0) }
    }
    fun day(value: String): DayOfWeek? {
        val normalized = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").filter(Char::isLetter)
        return when (normalized) {
            "lundi", "monday", "الاثنين" -> DayOfWeek.MONDAY
            "mardi", "tuesday", "الثلاثاء" -> DayOfWeek.TUESDAY
            "mercredi", "wednesday", "الاربعاء" -> DayOfWeek.WEDNESDAY
            "jeudi", "thursday", "الخميس" -> DayOfWeek.THURSDAY
            "vendredi", "friday", "الجمعة" -> DayOfWeek.FRIDAY
            "samedi", "saturday", "السبت" -> DayOfWeek.SATURDAY
            "dimanche", "sunday", "الاحد" -> DayOfWeek.SUNDAY
            else -> null
        }
    }
    fun aswat(html: String): List<RadioProgramme> {
        val doc = Jsoup.parse(html)
        val holder = doc.selectFirst("#grille ~ .home-tabs-holder") ?: return emptyList()
        return holder.select(".tab-nav a[href^='#']").flatMap { tab ->
            val day = day(tab.text()) ?: return@flatMap emptyList()
            val block = holder.getElementById(tab.attr("href").removePrefix("#")) ?: return@flatMap emptyList()
            block.select(".home-grille-widget .outer").mapNotNull { row ->
                val clock = row.selectFirst("time")?.text().orEmpty()
                val values = times.findAll(clock).map { it.value }.toList()
                if (values.size != 2) return@mapNotNull null
                val start = minute(values[0]) ?: return@mapNotNull null
                val end = minute(values[1]) ?: return@mapNotNull null
                val title = row.selectFirst("h2 a")?.text()?.trim()?.take(160) ?: return@mapNotNull null
                if (start >= 1440 || start == end || title.isBlank()) return@mapNotNull null
                RadioProgramme(day, start, end, title)
            }
        }.distinct().take(500).sortedWith(compareBy({ it.day.value }, { it.startMinute }))
    }
}
internal data class RadioGuide(val source: RadioGuideSource, val fetchedAt: Instant, val programmes: List<RadioProgramme>) {
    fun freshForPlanning(now: Instant) = !fetchedAt.isAfter(now.plusSeconds(300)) && !fetchedAt.isBefore(now.minusSeconds(24 * 3600))
    fun occurrences(date: LocalDate): List<ProgrammeOccurrence> = programmes.filter { it.day == date.dayOfWeek }.mapNotNull { programme ->
        val start = MoroccoRadioTime.atLocal(date, programme.startMinute)
        val nextDay = programme.endMinute <= programme.startMinute || programme.endMinute == 1440
        val endDate = if (nextDay) date.plusDays(1) else date
        val endMinute = programme.endMinute % 1440
        val end = MoroccoRadioTime.atLocal(endDate, endMinute)
        if (!end.isAfter(start)) null else ProgrammeOccurrence(programme, start, end)
    }.sortedBy { it.start }
    fun current(now: Instant): ProgrammeOccurrence? {
        if (!freshForPlanning(now)) return null
        val date = now.atZone(MoroccoRadioTime.zoneAt(now)).toLocalDate()
        return (occurrences(date.minusDays(1)) + occurrences(date))
            .filter { !now.isBefore(it.start) && now.isBefore(it.end) }.singleOrNull()
    }
    fun next(now: Instant): ProgrammeOccurrence? {
        if (!freshForPlanning(now)) return null
        val date = now.atZone(MoroccoRadioTime.zoneAt(now)).toLocalDate()
        return (0L..7L).flatMap { occurrences(date.plusDays(it)) }.filter { it.start.isAfter(now) }.minByOrNull { it.start }
    }
}
