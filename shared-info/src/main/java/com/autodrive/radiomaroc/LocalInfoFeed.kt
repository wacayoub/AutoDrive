package com.autodrive.radiomaroc

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Weather and Habous prayer times for the chosen city, refreshed while the dashboard is visible. */
internal class LocalInfoFeed(city: RadioCity) {
    private val chosenCity = MutableStateFlow(city)
    private val report = MutableStateFlow<RadioWeather?>(null)
    private val calendars = MutableStateFlow<Map<YearMonth, HabousCalendar>>(emptyMap())
    private var nextWeatherFetchAt = 0L
    // A GPS position, when the app has one: the weather is then taken there instead of at the chosen city.
    @Volatile private var position: Pair<Double, Double>? = null

    /** Uses the weather at [latitude], [longitude], fetched again once the car has moved more than 5 km. */
    fun usePosition(latitude: Double, longitude: Double) {
        val last = position
        val movedKm = last?.let {
            val dLat = (latitude - it.first) * 111.2
            val dLon = (longitude - it.second) * 111.2 * kotlin.math.cos(Math.toRadians(latitude))
            kotlin.math.sqrt(dLat * dLat + dLon * dLon)
        }
        if (movedKm == null || movedKm > 5) {
            position = latitude to longitude
            nextWeatherFetchAt = 0L
        }
    }

    val city: StateFlow<RadioCity> = chosenCity.asStateFlow()
    val weather: StateFlow<RadioWeather?> = report.asStateFlow()
    val prayerCalendars: StateFlow<Map<YearMonth, HabousCalendar>> = calendars.asStateFlow()

    /** Returns false when [value] was already selected. */
    fun choose(value: RadioCity): Boolean {
        if (value == chosenCity.value) return false
        report.value = null
        nextWeatherFetchAt = 0L
        chosenCity.value = value
        return true
    }

    suspend fun refreshWeather(city: RadioCity) {
        while (true) {
            if (SystemClock.elapsedRealtime() >= nextWeatherFetchAt) {
                val at = position
                val latest = withContext(Dispatchers.IO) {
                    runCatching { if (at != null) RadioLocalInfo.weather(at.first, at.second) else RadioLocalInfo.weather(city) }.getOrNull()
                }
                report.value = latest
                nextWeatherFetchAt = SystemClock.elapsedRealtime() + if (latest == null) 300_000L else 900_000L
            }
            // Wake at least every 30 s, so a new position is taken into account soon.
            delay((nextWeatherFetchAt - SystemClock.elapsedRealtime()).coerceIn(1L, 30_000L))
        }
    }

    suspend fun refreshPrayerCalendars() {
        while (true) {
            val today = LocalDate.now(RadioLocalInfo.moroccoTime)
            val months = listOf(today, today.minusDays(1), today.plusDays(1)).map(YearMonth::from).toSet()
            val kept = calendars.value.filterKeys { it in months }.toMutableMap()
            val missing = months - kept.keys
            if (missing.isNotEmpty()) {
                kept += withContext(Dispatchers.IO) {
                    missing.mapNotNull { month ->
                        runCatching { RadioLocalInfo.calendar(month) }
                            .onFailure { Log.w("RadioLocalInfo", "Calendrier Habous indisponible pour $month", it) }
                            .getOrNull()?.let { month to it }
                    }
                }
            }
            calendars.value = kept.toMap()
            delay(if (months.all { it in kept }) 3_600_000L else 900_000L)
        }
    }

    fun prayerTimes(city: RadioCity, date: LocalDate): List<PrayerMoment> =
        calendars.value[YearMonth.from(date)]?.prayers(city, date).orEmpty()

    fun prayerStatus(city: RadioCity): PrayerStatus {
        val today = LocalDate.now(RadioLocalInfo.moroccoTime)
        val offset = calendars.value[YearMonth.from(today)]?.offset ?: RadioLocalInfo.moroccoTime
        val now = LocalDateTime.now(offset)
        val date = now.toLocalDate()
        return RadioLocalInfo.prayerStatus(
            now, prayerTimes(city, date.minusDays(1)), prayerTimes(city, date), prayerTimes(city, date.plusDays(1)),
        )
    }
}

internal fun untilNextMinute() = 60_000L - System.currentTimeMillis() % 60_000L

internal fun prayerDescription(moment: PrayerMoment?, date: LocalDate): String {
    if (moment == null) return "—"
    val day = when {
        moment.dateTime.toLocalDate() < date -> " hier"
        moment.dateTime.toLocalDate() > date -> " demain"
        else -> ""
    }
    return "${moment.prayer.label} ${moment.dateTime.toLocalTime()}$day"
}
