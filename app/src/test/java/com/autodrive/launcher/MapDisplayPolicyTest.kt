package com.autodrive.launcher

import org.junit.Assert.*
import org.junit.Test

class MapDisplayPolicyTest {
    private val view = "https://www.google.com/maps/@33.57311,-7.589843,14z/data=!5m1!1e1"
    @Test fun publicViewportSurvivesWithoutTrackingOrAuthenticationQueries() {
        val result = MapDisplayPolicy.viewUrl("$view?authuser=1&code=private&g_ep=opaque&utm_campaign=test&hl=fr#secret")!!
        assertTrue(result.startsWith(view))
        assertTrue(result.contains("api=1&map_action=map"))
        assertTrue(result.contains("hl=fr"))
        for (key in listOf("authuser", "code", "g_ep", "utm_", "secret", "private")) assertFalse(result.contains(key))
    }
    @Test fun placeAndRouteContributeOnlyANumericViewport() {
        val result = MapDisplayPolicy.viewUrl("https://www.google.com/maps/dir/Private+address/@33.57,-7.58,12z/data=!4m9")!!
        assertTrue(result.contains("/maps/@33.57,-7.58,12z?"))
        assertFalse(result.contains("Private")); assertFalse(result.contains("/dir/"))
        assertNull(MapDisplayPolicy.viewUrl("https://www.google.com/maps/dir/?api=1&destination=Somewhere"))
    }
    @Test fun invalidCoordinatesOriginsAndOtherMapActionsAreRejected() {
        for (url in listOf("http://www.google.com/maps/@33,-7,12z", "https://www.google.com.evil/maps/@33,-7,12z",
            "https://user@www.google.com/maps/@33,-7,12z", "https://www.google.com:444/maps/@33,-7,12z",
            "https://accounts.google.com/signin", "https://www.google.com/maps/@95,-7,12z", "https://www.google.com/maps/@33,185,12z",
            "https://www.google.com/maps/@33,-7,25z", "https://www.google.com/maps/@?api=1&map_action=pano")) {
            assertNull(url, MapDisplayPolicy.viewUrl(url))
        }
    }
    @Test fun originGrantsAreRestrictedToExactHttpsMapOrigins() {
        assertTrue(MapDisplayPolicy.trustedOrigin("https://www.google.com"))
        assertTrue(MapDisplayPolicy.trustedOrigin("https://maps.google.com:443/"))
        for (origin in listOf("https://accounts.google.com", "https://www.google.com/maps", "https://user@www.google.com",
            "http://www.google.com", "https://www.google.com:8443", "https://www.google.com/?token=x")) assertFalse(MapDisplayPolicy.trustedOrigin(origin))
    }
    @Test fun followUsesCurrentProviderLocationRatherThanSavedCoordinates() {
        val saved = RememberedMapDisplay(mapOf(MapDisplayOption.TRAFFIC to true, MapDisplayOption.SATELLITE to true), true, view)
        val launch = saved.launchUrl()
        assertTrue(launch.contains("basemap=satellite&layer=traffic")); assertTrue(launch.contains("zoom=14"))
        assertFalse(launch.contains("33.57311")); assertFalse(launch.contains("-7.589843")); assertFalse(launch.contains("center="))
        assertEquals(MapDisplayPolicy.viewUrl(view), saved.copy(followOnStart = false).launchUrl())
    }
    @Test fun unsupportedOrMissingBookmarksUseTheNormalMapsHome() {
        assertEquals(MapDisplayPolicy.HOME, RememberedMapDisplay().launchUrl())
        assertEquals(MapDisplayPolicy.HOME, RememberedMapDisplay(viewUrl = "https://evil.invalid/maps/").launchUrl())
        assertFalse(MapDisplayPolicy.onMaps("https://www.google.com/search?q=maps"))
    }
}
