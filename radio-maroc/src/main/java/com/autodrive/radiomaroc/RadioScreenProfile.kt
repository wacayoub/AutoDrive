package com.autodrive.radiomaroc

import android.content.res.Configuration

internal enum class DisplayLayout(val label: String) {
    GUIDE("Guide TV"), MOSAIC("Mosaïque"), LISTENING("Écran d’écoute");

    companion object {
        fun restore(name: String?) = entries.firstOrNull { it.name == name } ?: GUIDE
    }
}

/**
 * Which variant of the radio screen fits the current display. Pure so the car-screen thresholds
 * (800 × 480 at 160 dpi is 800 × 480 dp, at 213 dpi about 600 × 360 dp) can be checked in unit tests.
 */
internal data class RadioScreenProfile(
    val television: Boolean,
    val landscape: Boolean,
    val widthDp: Int,
    val heightDp: Int,
    val layout: DisplayLayout,
) {
    val compactTv get() = television && (heightDp < 430 || widthDp < 760)
    /** Car "Cockpit": large player on the left, one column of stations on the right. */
    val touchGuide get() = !television && landscape && layout == DisplayLayout.GUIDE

    /** Car "Présélections": big logo tiles with the player docked at the bottom. */
    val touchMosaic get() = !television && landscape && layout == DisplayLayout.MOSAIC

    /** Either car layout: shared header with tabs, clock and info chips, long-press favourites. */
    val carScreen get() = touchGuide || touchMosaic

    // Weather and prayer chips only fit beside the tabs on wide headers (800 × 480 at 160 dpi).
    val headerInfoChips get() = carScreen && widthDp >= 700
    val smallTouchGuide get() = touchGuide && heightDp < 300
    val compactGuideTabs get() = carScreen && widthDp < 580
    val singleColumnGuide get() = touchGuide && widthDp < 640

    // Below 400 dp of height the weather and prayer tiles would squeeze the station logo out of the player.
    val showDashboardTiles get() = !touchGuide || heightDp >= 400

    // Short car screens (about 213 dpi at 800 × 480) keep one line of player text so the logo keeps its room.
    val shortTouchGuide get() = touchGuide && heightDp < 400
    val compactGuide get() = compactTv || touchGuide
    val shortLandscape get() = !television && landscape && heightDp < 330
    val tightLandscape get() = shortLandscape && heightDp < 280
    val showingCatalogue get() = layout != DisplayLayout.LISTENING
    val tvGuide get() = touchGuide || television && layout == DisplayLayout.GUIDE

    // Touch rows stay at or above 48 dp so a driver can hit a station at arm's length.
    val tvGuideRowDp get() = if (touchGuide) (if (smallTouchGuide) 48 else 52) else if (compactTv) 30 else 36
    val catalogueInGrid get() = tvGuide || layout == DisplayLayout.MOSAIC
    val compactDirectory get() = tvGuide || shortLandscape

    companion object {
        fun of(configuration: Configuration, layout: DisplayLayout) = RadioScreenProfile(
            television = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION,
            landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp,
            layout = layout,
        )
    }
}
