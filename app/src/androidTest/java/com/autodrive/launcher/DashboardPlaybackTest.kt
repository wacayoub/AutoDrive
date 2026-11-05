package com.autodrive.launcher

import android.content.Intent
import android.os.SystemClock
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class DashboardPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun hlsPlaysAfterExplicitStart() {
        val source = InstrumentationRegistry.getArguments().getString("hlsUrl")?.let { VideoSource.iptv(it) }
        assumeNotNull(source)
        withPlayer(source!!) { view ->
            val engine = (view as PlayerView).player!!
            val ready = CountDownLatch(1)
            val frame = CountDownLatch(1)
            instrumentation.runOnMainSync {
                assertFalse(engine.playWhenReady)
                if (engine.playbackState == Player.STATE_READY) ready.countDown()
                engine.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) ready.countDown()
                    }
                    override fun onRenderedFirstFrame() { frame.countDown() }
                })
            }
            assertTrue("HLS did not become ready", ready.await(25, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { engine.play() }
            assertTrue("No HLS frame rendered", frame.await(15, TimeUnit.SECONDS))
            SystemClock.sleep(1_200)
            instrumentation.runOnMainSync { assertTrue("HLS time did not advance", engine.currentPosition > 0) }
        }
    }

    @Test fun youtubeCreatesOfficialIframeWithoutAutoplay() {
        withPlayer(VideoSource.YouTube("M7lc1UVf-VE")) { view ->
            val web = view as WebView
            val loaded = CountDownLatch(1)
            var iframe = ""
            for (attempt in 1..20) {
                instrumentation.runOnMainSync {
                    web.evaluateJavascript("document.querySelector('iframe')?.src") { value ->
                        iframe = value
                        if (value.startsWith("\"https://")) loaded.countDown()
                    }
                }
                if (loaded.await(400, TimeUnit.MILLISECONDS)) break
            }
            assertTrue("Official embed URL missing", iframe.contains("https://www.youtube.com/embed/M7lc1UVf-VE"))
            assertTrue("Autoplay must be disabled", iframe.contains("autoplay=0"))
        }
    }

    private fun withPlayer(source: VideoSource, check: (android.view.View) -> Unit) {
        // The test APK exercises the real player independently of the production stationary GPS gate.
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val fixture = DashboardVideo(activity, {}, {})
        var view: android.view.View? = null
        try {
            instrumentation.runOnMainSync {
                val panel = FrameLayout(activity)
                activity.setContentView(panel)
                view = fixture.create(source).also(panel::addView)
            }
            check(checkNotNull(view))
        } finally {
            instrumentation.runOnMainSync {
                view?.let { (it.parent as? FrameLayout)?.removeView(it) }
                fixture.close()
                assertEquals(false, fixture.showing)
                activity.finish()
            }
        }
    }
}
