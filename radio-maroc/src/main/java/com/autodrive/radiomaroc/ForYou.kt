package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioStation
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

/** One entry of the « Pour vous » tab: the station, why it is suggested now, and where a recitation starts. */
internal data class Suggestion(val station: RadioStation, val reason: String, val chapter: Int? = null)

/**
 * Suggestions built only from what the app already knows on the device: the clock, Habous prayer times,
 * the chosen city, the listening history and the presets. Nothing claims a specific programme is on air.
 */
internal object ForYou {
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private const val AL_KAHF = 18
    private const val MAX_SUGGESTIONS = 12

    fun isRamadan(now: LocalDateTime): Boolean =
        runCatching { HijrahDate.from(now.toLocalDate()).get(ChronoField.MONTH_OF_YEAR) == 9 }.getOrDefault(false)

    fun suggestions(
        now: LocalDateTime,
        prayers: PrayerStatus?,
        todayPrayers: List<PrayerMoment>,
        city: RadioCity,
        recents: List<RadioStation>,
        presets: List<RadioStation>,
        herrazChapter: Int?,
    ): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        fun add(station: RadioStation?, reason: String, chapter: Int? = null) {
            if (station != null && out.none { it.station.url == station.url }) out += Suggestion(station, reason, chapter)
        }
        val quran = Stations.quranRadio
        val herraz = Stations.herrazStation
        val hour = now.hour
        val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY

        // Around prayer times the Quran radio comes first.
        val next = prayers?.next
        val current = prayers?.current
        val untilNext = next?.let { Duration.between(now, it.dateTime).toMinutes() }
        val sinceCurrent = current?.let { Duration.between(it.dateTime, now).toMinutes() }
        if (next != null && untilNext != null && untilNext in 0..20) {
            add(quran, "Avant ${next.prayer.label} · ${clock.format(next.dateTime)}")
        } else if (current != null && sinceCurrent != null && sinceCurrent in 0..15) {
            add(quran, "Après ${current.prayer.label}")
        }

        if (isRamadan(now)) {
            val iftar = todayPrayers.firstOrNull { it.prayer == Prayer.MAGHRIB }?.dateTime
            add(quran, if (iftar != null && iftar.isAfter(now)) "Ramadan · iftar à ${clock.format(iftar)}" else "Ramadan Moubarak")
            add(herraz, "Ramadan · " + (herrazChapter?.let(HerrazRecitations::chapter)
                ?.let { "reprendre سورة ${it.name}" } ?: "lecture du Coran"), herrazChapter)
        }

        if (now.dayOfWeek == DayOfWeek.FRIDAY && hour in 5..16) add(herraz, "Vendredi · سورة الكهف", AL_KAHF)

        when {
            weekend && hour in 14..22 -> {
                add(Stations.named("Radio Mars"), "Week-end · sport")
                add(Stations.named("Radio Aswat"), "Week-end · sport")
            }
            hour in 6..9 -> {
                add(Stations.named("Medi 1 Radio"), "Matinale · infos")
                add(Stations.named("Med Radio"), "Matinale · débats")
            }
            hour in 12..13 -> {
                add(Stations.named("Medi 1 Radio"), "Midi · infos")
                add(Stations.named("Chaîne Inter"), "Midi · infos")
            }
            hour in 17..19 -> {
                add(Stations.named("Med Radio"), "Retour · débats")
                add(Stations.named("Radio Aswat"), "Retour · débats")
            }
            hour >= 22 || hour < 5 -> {
                add(Stations.named("Medina FM Tarab"), "Soirée · tarab")
                add(Stations.named("Medina FM Andalou"), "Soirée · andalou")
            }
        }

        herrazChapter?.let(HerrazRecitations::chapter)?.let { add(herraz, "Reprendre · سورة ${it.name}", it.number) }
        recents.take(4).forEach { add(it, "Écoutée récemment") }
        Stations.nearby(city).take(3).forEach { add(it, "Près de vous · ${city.label}") }
        presets.take(4).forEachIndexed { index, station -> add(station, "Présélection ${index + 1}") }
        for (station in Stations.popular()) {
            if (out.size >= MAX_SUGGESTIONS) break
            add(station, "Populaire au Maroc")
        }
        return out
    }
}
