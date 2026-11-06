package com.autodrive.launcher

import org.junit.Assert.*
import org.junit.Test

class RadioPhoneInputTest {
    @Test fun internationalAndServiceNumbersKeepTheirDialMeaning() {
        assertEquals("+212612345678", DialNumber.normalize("+212 (6)12-34 56.78"))
        assertEquals("*123#", DialNumber.normalize("*123#"))
        for (bad in listOf("", "+", "1+2", "++212", "tel:123", "123;456", "12a34", "1".repeat(33))) assertNull(bad, DialNumber.normalize(bad))
    }
    @Test fun radioAcceptsDirectHttpsWithoutCredentialsOrUnsafeSchemes() {
        assertNotNull(RadioStation.validUrl(" https://radio.example/live.m3u8?token=abc "))
        assertNotNull(RadioStation.validUrl("https://radio.example:8102/live.mp3"))
        for (bad in listOf("http://radio.example/live", "file:///etc/a", "https://user:password@radio.example/a", "https://radio.example/a#b", "https://radio.example:99999/a", "https:///a")) assertNull(bad, RadioStation.validUrl(bad))
    }
}
