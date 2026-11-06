package com.autodrive.launcher

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Controlled transport fixtures exercise consent/continuation, not APK authenticity. */
@RunWith(AndroidJUnit4::class)
class UpdateFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application get() = instrumentation.targetContext.applicationContext as Application

    private class Source(private val file: File) : UpdateSource {
        override val versionCode = 1L
        override val versionName = "fixture"
        val release = UpdateRelease("com.autodrive.launcher", 2, "fixture-next", 33,
            "https://github.com/wacayoub/AutoDrive/releases/download/v2/AutoDrive.apk", "0".repeat(64), 1)
        val finishDownload = CountDownLatch(1)
        @Volatile var downloads = 0
        @Volatile var fail = false
        @Volatile var verifications = 0
        override fun latest() = release
        override fun download(release: UpdateRelease, progress: (Int) -> Unit): File {
            downloads++
            progress(50)
            check(finishDownload.await(8, TimeUnit.SECONDS))
            if (fail) throw IOException("controlled transport failure")
            return file
        }
        override fun verify(file: File, release: UpdateRelease): File { verifications++; return file }
    }

    private fun fixture(test: (UpdateViewModel, Source) -> Unit) {
        val store = ViewModelStore()
        val prefs = application.getSharedPreferences("online_updates", 0)
        val before = prefs.all
        val file = File(application.cacheDir, "update-flow-fixture").apply { writeText("fixture") }
        val source = Source(file)
        lateinit var model: UpdateViewModel
        instrumentation.runOnMainSync {
            model = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = UpdateViewModel(application, source) as T
            })[UpdateViewModel::class.java]
        }
        try { test(model, source) } finally {
            source.finishDownload.countDown()
            instrumentation.runOnMainSync { store.clear() }
            prefs.edit().clear().apply {
                before.forEach { (key, value) -> if (value is Long) putLong(key, value) }
            }.commit()
            file.delete()
        }
    }

    private fun await(phase: UpdatePhase, model: UpdateViewModel) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (model.state.value.phase != phase && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
        assertEquals(phase, model.state.value.phase)
    }

    @Test fun oneConsentDownloadsOnceAndRetainsOneForegroundInstallRequest() = fixture { model, source ->
        instrumentation.runOnMainSync { model.check() }
        await(UpdatePhase.AVAILABLE, model)
        instrumentation.runOnMainSync {
            assertEquals(source.release, model.takeAvailablePrompt())
            assertNull("No repeated popup on each resume", model.takeAvailablePrompt())
            model.offerAgainAfterRecreation()
            assertEquals(source.release, model.takeAvailablePrompt())
        }
        assertEquals("Finding an update does not download without OK", 0, source.downloads)
        instrumentation.runOnMainSync { model.downloadAndInstall(); model.downloadAndInstall() }
        await(UpdatePhase.DOWNLOADING, model)
        assertFalse(model.state.value.installRequested)
        source.finishDownload.countDown()
        await(UpdatePhase.READY, model)
        assertEquals(1, source.downloads)
        SystemClock.sleep(80)
        assertTrue("Ready request survives the absence of a foreground consumer", model.state.value.installRequested)
        assertNotNull(runBlocking { model.verifiedFile() })
        assertEquals(1, source.verifications)
        instrumentation.runOnMainSync {
            assertTrue(model.takeInstallRequest())
            assertFalse("Returning from cancelled installer must not reopen it", model.takeInstallRequest())
            model.downloadAndInstall()
        }
        assertEquals(1, source.downloads)
    }

    @Test fun failedDownloadDoesNotRequestInstallationAndCanRetry() = fixture { model, source ->
        source.fail = true
        source.finishDownload.countDown()
        instrumentation.runOnMainSync { model.check() }
        await(UpdatePhase.AVAILABLE, model)
        instrumentation.runOnMainSync { model.downloadAndInstall() }
        await(UpdatePhase.ERROR, model)
        assertEquals(source.release, model.state.value.release)
        instrumentation.runOnMainSync { assertFalse(model.takeInstallRequest()) }
        source.fail = false
        instrumentation.runOnMainSync { model.downloadAndInstall() }
        await(UpdatePhase.READY, model)
        assertTrue(model.state.value.installRequested)
        assertEquals(2, source.downloads)
    }
}
