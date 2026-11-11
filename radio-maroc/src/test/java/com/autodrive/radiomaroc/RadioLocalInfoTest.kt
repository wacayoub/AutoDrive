package com.autodrive.radiomaroc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class RadioLocalInfoTest {
    @Test fun readsPublishedCityAndNextPrayerAfterIsha() {
        val month = YearMonth.of(2026, 11)
        val script = """(window.HORAIRES_MOIS=window.HORAIRES_MOIS||[]).push({
            "annee":2026,"mois":11,"fuseau":"GMT","cols":["Fajr","Chorouq","Dhuhr","Asr","Maghrib","Ishae"],
            "villes":[{"c":58,"h":[["05:18","06:55","12:19","15:15","17:40","18:57"],
             ["05:19","06:56","12:19","15:14","17:39","18:56"]]}]});"""
        val calendar = requireNotNull(RadioLocalInfo.parseCalendar(script, month))
        assertEquals(ZoneOffset.UTC, calendar.offset)
        val date = LocalDate.of(2026, 11, 1)
        val today = calendar.prayers(RadioCity.CASABLANCA, date)
        assertEquals(Prayer.entries, today.map { it.prayer })
        assertEquals("12:19", today[1].dateTime.toLocalTime().toString())
        val next = RadioLocalInfo.nextPrayer(date.atTime(22, 0), today,
            calendar.prayers(RadioCity.CASABLANCA, date.plusDays(1)))
        assertEquals(PrayerMoment(Prayer.FAJR, LocalDateTime.of(2026, 11, 2, 5, 19)), next)
        assertEquals(emptyList<PrayerMoment>(), calendar.prayers(RadioCity.RABAT, date))
        assertNull(RadioLocalInfo.parseCalendar(script, month.plusMonths(1)))
        assertNull(RadioLocalInfo.parseCalendar("<html></html>", month))
    }

    @Test fun readsWeatherAndIgnoresAnUnrelatedDailyRange() {
        val data = """{
          "current":{"time":"2026-11-11T17:45","temperature_2m":22.5,"weather_code":61},
          "daily":{"time":["2026-11-11"],"temperature_2m_min":[20.6],"temperature_2m_max":[25.1]}
        }"""
        val weather = requireNotNull(RadioLocalInfo.parseWeather(data))
        assertEquals(RadioWeather(LocalDateTime.of(2026, 11, 11, 17, 45), 23, "Pluie", 21, 25), weather)
        assertNull(RadioLocalInfo.parseWeather(data.replace("2026-11-11\"]", "2026-11-10\"]"))?.low)
        assertNull(RadioLocalInfo.parseWeather("{}"))
    }
}
