package com.autodrive.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
internal class DashboardVideo(
    private val context: Context,
    private val openYouTubeLink: (Uri) -> Unit,
    private val onRendererGone: () -> Unit
) {
    private var web: WebView? = null
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    val showing get() = web != null || player != null

    fun create(source: VideoSource): View = when (source) {
        is VideoSource.YouTube -> youtube(source.id)
        is VideoSource.Iptv -> iptv(source.url)
    }

    @SuppressLint("SetJavaScriptEnabled") // Required by the official YouTube player.
    private fun youtube(id: String): View = WebView(context).apply {
        web = this
        setBackgroundColor(Color.BLACK)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url
                if (request.hasGesture() && url.scheme == "https" &&
                    url.host in setOf("www.youtube.com", "youtube.com", "m.youtube.com", "youtu.be")) {
                    openYouTubeLink(url)
                }
                return true
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                onRendererGone()
                return true
            }
        }
        val origin = "https://${context.packageName}"
        loadDataWithBaseURL(
            "$origin/",
            """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>html,body{height:100%;margin:0;background:#000}iframe{width:100%;height:100%;border:0;display:block}</style>
            </head><body><iframe title="YouTube player" referrerpolicy="strict-origin-when-cross-origin"
            src="https://www.youtube.com/embed/$id?autoplay=0&amp;playsinline=1&amp;fs=0"></iframe></body></html>
            """.trimIndent(),
            "text/html", "UTF-8", null
        )
    }

    private fun iptv(url: String): View {
        val view = LayoutInflater.from(context).inflate(R.layout.dashboard_player, null, false) as PlayerView
        playerView = view
        val engine = ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = engine
        view.player = engine
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        engine.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                view.setCustomErrorMessage("Stream unavailable · Check the HLS link or use Smarters")
            }
        })
        engine.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
        engine.prepare()
        return view
    }

    fun close() {
        web?.destroy()
        web = null
        playerView?.player = null
        playerView = null
        player?.release()
        player = null
    }
}
