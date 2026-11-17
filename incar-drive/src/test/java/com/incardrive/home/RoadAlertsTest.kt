package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadAlertsTest {
    @Test fun anAccidentIsSerious() {
        val alert = RoadAlerts.find(listOf("36 min · 14 km", "Accident signalé sur Bd Mohammed VI"))!!
        assertTrue(alert.serious)
        assertEquals("Accident signalé sur Bd Mohammed VI", alert.text)
        assertTrue(RoadAlerts.find(listOf("Road closed ahead"))!!.serious)
        assertTrue(RoadAlerts.find(listOf("Crash reported ahead"))!!.serious)
        assertTrue(RoadAlerts.find(listOf("حادث على الطريق"))!!.serious)
    }

    @Test fun aSlowdownOrWorksIsNotSerious() {
        assertFalse(RoadAlerts.find(listOf("Ralentissement de 5 min sur votre trajet"))!!.serious)
        assertFalse(RoadAlerts.find(listOf("Travaux dans 800 m"))!!.serious)
        assertFalse(RoadAlerts.find(listOf("Heavy traffic ahead, 7 min delay"))!!.serious)
        assertFalse(RoadAlerts.find(listOf("Contrôle de vitesse signalé"))!!.serious)
    }

    @Test fun ordinaryGuidanceIsNotAnAlert() {
        assertNull(RoadAlerts.find(listOf("Dans 300 m, tournez à droite sur Bd Zerktouni", "12 min · 5,3 km · Arrivée 18:05")))
        assertNull(RoadAlerts.find(listOf("20 m", "2 min · 450 m · 19:44")))
        assertNull(RoadAlerts.find(emptyList()))
    }

    @Test fun aLongMessageIsShortened() {
        val alert = RoadAlerts.find(listOf("Accident signalé   sur le boulevard Mohammed VI en direction du centre-ville"))!!
        assertTrue(alert.text.length <= 46)
        assertTrue(alert.text.endsWith("…"))
        assertFalse(alert.text.contains("  "))
    }
}
