package com.incardrive.home

import com.autodrive.launcher.UpdatePolicy
import com.autodrive.launcher.UpdateRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IncarUpdateChannelTest {
    private fun release(url: String, version: String = "1.0.1") = UpdateRelease(UpdatePolicy.INCAR_PACKAGE, 2, version, 30,
        url, "a".repeat(64), 1000)

    @Test
    fun ordersIncarDriveTagsOnly() {
        assertTrue(UpdatePolicy.versionOrder("incar-drive-v", "incar-drive-v1.0.10")!! >
            UpdatePolicy.versionOrder("incar-drive-v", "incar-drive-v1.0.9")!!)
        assertNull(UpdatePolicy.versionOrder("incar-drive-v", "radio-maroc-v2.5.47"))
        assertEquals(UpdatePolicy.radioVersionOrder("radio-maroc-v2.5.47"),
            UpdatePolicy.versionOrder("radio-maroc-v", "radio-maroc-v2.5.47"))
    }

    @Test
    fun acceptsOnlyTheIncarDriveReleaseAsset() {
        release("https://github.com/wacayoub/AutoDrive/releases/download/incar-drive-v1.0.1/IncarDrive.apk").validate()
        assertThrows(IllegalArgumentException::class.java) {
            release("https://github.com/wacayoub/AutoDrive/releases/download/v1.0.1/IncarDrive.apk").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            release("https://example.com/IncarDrive.apk").validate()
        }
    }
}
