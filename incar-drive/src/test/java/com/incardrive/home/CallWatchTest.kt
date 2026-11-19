package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallWatchTest {
    @Test
    fun recognisesAnswerAndDeclineButtons() {
        listOf("Answer", "Répondre", "ACCEPT", "接听").forEach { assertEquals(it, CallButton.ANSWER, CallWatch.classify(it)) }
        listOf("Decline", "Refuser", "Hang up", "Raccrocher", "挂断", "End").forEach {
            assertEquals(it, CallButton.DECLINE, CallWatch.classify(it))
        }
        listOf("Speaker", "Mute", "Send message", "Reply").forEach { assertEquals(it, CallButton.OTHER, CallWatch.classify(it)) }
    }

    @Test
    fun aPhoneAppNotificationIsACallOnlyWithCallButtons() {
        assertTrue(CallWatch.isCall("com.carsyso.bluetooth", null, listOf("Refuser", "Répondre")))
        assertFalse(CallWatch.isCall("com.carsyso.bluetooth", null, listOf("Play", "Next")))
        assertFalse(CallWatch.isCall("com.whatsapp", null, listOf("Answer")))
        assertTrue(CallWatch.isCall("com.whatsapp", "call", listOf("Answer", "Decline")))
        assertFalse(CallWatch.isCall("com.example", "call", emptyList()))
    }

    @Test
    fun aPhoneAppCallWithoutButtonsStillShows() {
        // The Incarlink's phone app during « Calling... »: no button, but it stays on and speaks of a call.
        assertTrue(CallWatch.isCall("com.carsyso.bluetooth", null, emptyList(), ongoing = true, mentionsCall = true))
        assertTrue(CallWatch.isCall("com.carsyso.bluetoothcall", null, emptyList(), mentionsCall = true))
        assertTrue(CallWatch.isCall("com.example", "call", emptyList(), ongoing = true))
        // « Bluetooth connected », or another app speaking of calls: not a call.
        assertFalse(CallWatch.isCall("com.carsyso.bluetooth", null, emptyList(), ongoing = true))
        assertFalse(CallWatch.isCall("com.whatsapp", null, emptyList(), ongoing = true, mentionsCall = true))
        assertTrue(CallWatch.mentionsCall(listOf("Calling...")))
        assertTrue(CallWatch.mentionsCall(listOf("通话中")))
        assertFalse(CallWatch.mentionsCall(listOf("Bluetooth connecté")))
    }

    @Test
    fun theDiagnosticKeepsNothingPersonal() {
        val line = CallDiagnostic.describe("com.carsyso.bluetooth", null, true, emptyList(), true, false)
        assertEquals("com.carsyso.bluetooth · type aucun · permanente · 0 bouton · parle d’appel · → BANDEAU", line)
        assertEquals("com.whatsapp · type msg · 1 bouton : Reply · → ignorée",
            CallDiagnostic.describe("com.whatsapp", "msg", false, listOf("Reply"), false, false))
    }
}
