package com.autodrive.launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateVerifierTest {
    @Test fun verifiesCandidateAndRejectsTamperingAndOtherSigners() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.filesDir, "update-fixtures")
        val json = JSONObject(File(folder, "autodrive-update.json").readText())
        val release = UpdateRelease(json.getString("packageName"), json.getLong("versionCode"), json.getString("versionName"),
            json.getInt("minSdk"), json.getString("apkUrl"), json.getString("sha256"), json.getLong("size"))
        val source = GitHubUpdates(context)
        assertTrue("Install a lower-version updater test build", source.versionCode < release.versionCode)
        val candidate = File(folder, "AutoDrive.apk")
        assertEquals(candidate, source.verify(candidate, release))
        val wrongSigner = File(folder, "WrongSigner.apk")
        val hash = wrongSigner.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        try {
            source.verify(wrongSigner, release.copy(sha256 = hash, size = wrongSigner.length()))
            fail("Different signing identity was accepted")
        } catch (error: IllegalArgumentException) { assertTrue(error.message.orEmpty().contains("signature")) }
        try {
            source.verify(candidate, release.copy(sha256 = "0".repeat(64)))
            fail("Wrong hash was accepted")
        } catch (error: IllegalArgumentException) { assertTrue(error.message.orEmpty().contains("Empreinte")) }
        try {
            source.verify(candidate, release.copy(versionCode = source.versionCode))
            fail("Non-increasing version was accepted")
        } catch (error: IllegalArgumentException) { assertTrue(error.message.orEmpty().contains("Version")) }
    }
}
