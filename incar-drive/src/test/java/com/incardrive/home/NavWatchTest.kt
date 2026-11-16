package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavWatchTest {
    @Test
    fun readsMapsSummaryInFrench() {
        val nav = NavWatch.parse(listOf("Tournez à droite sur Bd Zerktouni", "12 min · 5,3 km · Arrivée prévue à 18:05"))!!
        assertEquals("12 min", nav.remaining)
        assertEquals("5,3 km", nav.distance)
        assertEquals("18:05", nav.arrival)
        assertEquals("Tournez à droite sur Bd Zerktouni", nav.instruction)
    }

    @Test
    fun readsHoursAndArrivalOnAnotherLine() {
        val nav = NavWatch.parse(listOf("1 h 20 min (98 km)", "Arrivée 9:40"))!!
        assertEquals("1 h 20 min", nav.remaining)
        assertEquals("98 km", nav.distance)
        assertEquals("09:40", nav.arrival)
    }

    @Test
    fun ignoresNotificationsWithoutATripSummary() {
        assertNull(NavWatch.parse(listOf("Google Maps", "Recherche de position GPS")))
    }
}
