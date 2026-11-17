package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManeuverTest {
    @Test fun readsTurnsInFrench() {
        assertEquals(Turn.RIGHT, Maneuver.of("Tournez à droite sur Bd de La Mecque", "110 m").turn)
        assertEquals(Turn.LEFT, Maneuver.of("Prendre à gauche vers N1", "300 m").turn)
        assertEquals(Turn.UTURN, Maneuver.of("Faites demi-tour", "50 m").turn)
        assertEquals(Turn.ARRIVE, Maneuver.of("Vous êtes arrivé à destination", "20 m").turn)
        assertEquals(Turn.STRAIGHT, Maneuver.of("Continuer sur Bd de Fès", "1,2 km").turn)
    }

    @Test fun readsTurnsInEnglishAndArabic() {
        assertEquals(Turn.LEFT, Maneuver.of("Turn left onto Main St", "0.3 km").turn)
        assertEquals(Turn.RIGHT, Maneuver.of("Keep right toward A1", "1 km").turn)
        assertEquals(Turn.ROUNDABOUT, Maneuver.of("At the roundabout, take the 2nd exit", "60 m").turn)
        assertEquals(Turn.RIGHT, Maneuver.of("انعطف يمينًا", "100 m").turn)
        assertEquals(Turn.LEFT, Maneuver.of("انعطف يسارًا", "100 m").turn)
    }

    @Test fun theFirstTurnNamedWins() {
        assertEquals(Turn.LEFT, Maneuver.of("Tournez à gauche puis à droite", "80 m").turn)
        assertEquals(Turn.RIGHT, Maneuver.of("Turn right then left", "80 m").turn)
    }

    @Test fun countsTheRoundaboutExit() {
        assertEquals(2, Maneuver.of("Au rond-point, prendre la 2e sortie sur Bd Zerktouni", "60 m").exit)
        assertEquals(3, Maneuver.of("Au rond-point, prenez la troisième sortie", "60 m").exit)
        assertEquals(1, Maneuver.of("At the roundabout, take the 1st exit onto N1", "60 m").exit)
        assertNull(Maneuver.of("Entrez dans le rond-point", "60 m").exit)
        assertNull(Maneuver.of("Tournez à droite", "60 m").exit)
    }

    @Test fun findsTheStreetAndTheDistance() {
        val turn = Maneuver.of("Tournez à droite sur Bd de La Mecque", "110 m")
        assertEquals("Bd de La Mecque", turn.street)
        assertEquals(110, turn.meters)
        assertEquals(1200, Maneuver.of("Continuer", "1,2 km").meters)
        assertEquals(8500, Maneuver.meters("8.5 km"))
        assertNull(Maneuver.of("Tournez à gauche", null).meters)
        assertNull(Maneuver.of("Tournez à gauche", "bientôt").street)
    }

    @Test fun aLeftoverOfTheInstructionIsNotAStreet() {
        assertNull(Maneuver.of("Dans 90 m, au rond-point, prenez la 3e sortie", "90 m").street)
        assertNull(Maneuver.of("Serrez sur la gauche", "200 m").street)
        assertEquals("Bd Zerktouni", Maneuver.of("Au rond-point, prenez la 2e sortie sur Bd Zerktouni", "60 m").street)
    }

    @Test fun anUnknownInstructionGoesStraight() {
        assertEquals(Turn.STRAIGHT, Maneuver.of(null, "200 m").turn)
        assertEquals(Turn.STRAIGHT, Maneuver.of("11 km", "11 km").turn)
    }
}
