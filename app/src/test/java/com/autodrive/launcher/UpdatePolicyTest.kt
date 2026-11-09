package com.autodrive.launcher

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val apk = "https://github.com/wacayoub/AutoDrive/releases/download/v1.4.0/AutoDrive.apk"
    private fun valid() = UpdateRelease("com.autodrive.launcher", 14, "1.4.0", 33, apk, "a".repeat(64), 1000)

    @Test fun acceptsOnlyOwnedReleaseAssets() {
        assertTrue(UpdatePolicy.releaseAsset(apk, "AutoDrive.apk"))
        assertTrue(UpdatePolicy.releaseAsset(apk.replace("AutoDrive.apk", "autodrive-update.json"), "autodrive-update.json"))
        for (bad in listOf(apk.replace("https:", "http:"), apk.replace("github.com", "github.com.example.org"),
            apk.replace("wacayoub", "other"), apk.replace("github.com/", "github.com@evil.example/"),
            apk.replace("/v1.4.0/", "/../"), apk + "?next=evil", apk.replace("AutoDrive.apk", "other.apk"))) {
            assertFalse(bad, UpdatePolicy.releaseAsset(bad, "AutoDrive.apk"))
        }
    }

    @Test fun permitsOnlyHttpsGithubDownloadRedirects() {
        assertTrue(UpdatePolicy.downloadHost("https://release-assets.githubusercontent.com/path?signature=example"))
        assertFalse(UpdatePolicy.downloadHost("http://release-assets.githubusercontent.com/path"))
        assertFalse(UpdatePolicy.downloadHost("https://release-assets.githubusercontent.com.evil.example/path"))
        assertFalse(UpdatePolicy.downloadHost("https://github.com:444/path"))
        assertFalse(UpdatePolicy.downloadHost("https://user:password@github.com/path"))
    }

    @Test fun rejectsUnboundedOrIncompleteMetadata() {
        valid().validate()
        for (release in listOf(valid().copy(packageName = "other.app"), valid().copy(versionCode = 0),
            valid().copy(sha256 = ""), valid().copy(sha256 = "g".repeat(64)), valid().copy(size = 0),
            valid().copy(size = UpdatePolicy.MAX_APK_BYTES + 1), valid().copy(apkUrl = "https://evil.example/AutoDrive.apk"))) {
            try { release.validate(); fail("Invalid metadata accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun radioMetadataRequiresItsExactVersionedReleaseAsset() {
        val radioApk = "https://github.com/wacayoub/AutoDrive/releases/download/radio-maroc-v1.9.0/Radio-Maroc-Coran-1.9.0.apk"
        val radio = valid().copy(packageName = UpdatePolicy.RADIO_PACKAGE, versionName = "1.9.0", apkUrl = radioApk)
        radio.validate()
        assertTrue(UpdatePolicy.radioTag("radio-maroc-v1.9.0"))
        assertFalse(UpdatePolicy.radioTag("v1.9.0"))
        assertFalse(UpdatePolicy.radioTag("radio-maroc-v1.9.0-test"))
        for (release in listOf(radio.copy(apkUrl = apk), radio.copy(versionName = "1.9.1"),
            radio.copy(apkUrl = radioApk + "?redirect=other"), radio.copy(packageName = UpdatePolicy.LAUNCHER_PACKAGE))) {
            try { release.validate(); fail("Wrong radio release asset accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
