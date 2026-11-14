package com.autodrive.radiomaroc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioScreenProfileTest {
    // The Incarlink head unit is 800 × 480 px; screenWidthDp/HeightDp exclude the system bars.
    private fun car(widthDp: Int, heightDp: Int, layout: DisplayLayout = DisplayLayout.GUIDE) =
        RadioScreenProfile(television = false, landscape = true, widthDp = widthDp, heightDp = heightDp, layout = layout)

    @Test
    fun carScreenAt160DpiKeepsTilesAndLargeRows() {
        val screen = car(800, 432)
        assertTrue(screen.touchGuide)
        assertTrue(screen.showDashboardTiles)
        assertFalse(screen.shortTouchGuide)
        assertFalse(screen.singleColumnGuide)
        assertEquals(52, screen.tvGuideRowDp)
    }

    @Test
    fun carScreenAt213DpiDropsTilesButKeepsTouchTargets() {
        val screen = car(600, 324)
        assertTrue(screen.touchGuide)
        assertFalse(screen.showDashboardTiles)
        assertTrue(screen.shortTouchGuide)
        assertTrue(screen.singleColumnGuide)
        assertTrue(screen.shortLandscape)
        assertEquals(52, screen.tvGuideRowDp)
    }

    @Test
    fun touchRowsNeverDropBelow48Dp() {
        val screen = car(560, 270)
        assertTrue(screen.smallTouchGuide)
        assertTrue(screen.compactGuideTabs)
        assertEquals(48, screen.tvGuideRowDp)
    }

    @Test
    fun presetsAreACarScreenWithHeaderChipsOnlyWhenWide() {
        val wide = car(800, 432, DisplayLayout.MOSAIC)
        assertTrue(wide.touchMosaic)
        assertTrue(wide.carScreen)
        assertFalse(wide.touchGuide)
        assertTrue(wide.headerInfoChips)
        assertFalse(car(600, 324, DisplayLayout.MOSAIC).headerInfoChips)
        assertFalse(RadioScreenProfile(television = true, landscape = true, widthDp = 960, heightDp = 540,
            layout = DisplayLayout.MOSAIC).carScreen)
    }

    @Test
    fun listeningScreenIsNotAGuide() {
        val screen = car(800, 432, DisplayLayout.LISTENING)
        assertFalse(screen.touchGuide)
        assertFalse(screen.showingCatalogue)
        assertFalse(screen.catalogueInGrid)
        assertTrue(screen.showDashboardTiles)
    }

    @Test
    fun smallTelevisionUsesCompactGuide() {
        val screen = RadioScreenProfile(television = true, landscape = true, widthDp = 960, heightDp = 540, layout = DisplayLayout.GUIDE)
        assertFalse(screen.compactTv)
        assertTrue(screen.tvGuide)
        assertEquals(36, screen.tvGuideRowDp)
        assertTrue(screen.copy(widthDp = 720, heightDp = 405).compactTv)
    }
}
