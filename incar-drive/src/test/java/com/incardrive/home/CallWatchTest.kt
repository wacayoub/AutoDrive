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
}
