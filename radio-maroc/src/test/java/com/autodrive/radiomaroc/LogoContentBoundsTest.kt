package com.autodrive.radiomaroc

import com.autodrive.launcher.LogoContentBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogoContentBoundsTest {
    @Test fun trimsTransparentPaddingAndKeepsLightLogoDetails() {
        val pixels = IntArray(8 * 6)
        pixels[1] = 0x04ffffff
        pixels[2 * 8 + 2] = 0xffe01434.toInt()
        pixels[4 * 8 + 6] = 0x40ffffff
        assertEquals(LogoContentBounds(2, 2, 5, 3), LogoContentBounds.find(pixels, 8, 6))
    }

    @Test fun trimsOnlyTheBlankMarginsOfAWhiteCanvas() {
        val pixels = IntArray(6 * 4) { 0xfffcfafa.toInt() }
        pixels[6 + 1] = 0xfff20a0a.toInt()
        pixels[6 + 3] = 0xff101010.toInt()
        pixels[2 * 6 + 4] = 0xffc10b0b.toInt()
        assertEquals(LogoContentBounds(1, 1, 4, 2), LogoContentBounds.find(pixels, 6, 4))
    }

    @Test fun leavesColoredCanvasesFullBleedAndIgnoresEmptyImages() {
        val colored = IntArray(9) { 0xffe01434.toInt() }.apply { this[4] = -1 }
        assertNull(LogoContentBounds.find(colored, 3, 3))
        assertNull(LogoContentBounds.find(IntArray(9), 3, 3))
        assertNull(LogoContentBounds.find(IntArray(9) { -1 }, 3, 3))
    }
}
