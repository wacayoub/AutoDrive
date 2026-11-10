package com.autodrive.radiomaroc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnrtAccessTest {
    private val scope = "/live/radio_inter"
    private val body = "?token=sample_token%2Bfor_test&token_path=%2Flive%2Fradio_inter&expires=2000\n"

    @Test fun readsOnlyTheProviderGrantFields() {
        assertEquals(SnrtAccess.Grant("sample_token+for_test", scope, 2000), SnrtAccess.parseGrant(body, scope, 1000))
    }

    @Test fun rejectsExpiredCrossStationAndMalformedGrants() {
        assertNull(SnrtAccess.parseGrant(body, scope, 1999))
        assertNull(SnrtAccess.parseGrant(body, "/live/radio_nationale", 1000))
        assertNull(SnrtAccess.parseGrant(body.replace("%2B", "%x"), scope, 1000))
        assertNull(SnrtAccess.parseGrant("403 Forbidden", scope, 1000))
    }
}
