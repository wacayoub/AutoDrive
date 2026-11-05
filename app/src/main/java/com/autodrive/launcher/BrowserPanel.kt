package com.autodrive.launcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar

/** Top-level provider pages; no SDK, tile extraction or user-agent spoofing. */
internal class BrowserPanel(
    private val activity: Activity,
    val home: String,
    private val maps: Boolean,
    private val requestLocation: () -> Unit,
    private val external: (Uri) -> Unit,
    private val nativeYouTube: ((Uri?) -> Boolean)? = null,
    private val focused: () -> Unit
) : FrameLayout(activity) {
    private var browser: WebView? = null
    private var fullscreen: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private val ui = DriveViews(activity)
    private val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        progressTintList = android.content.res.ColorStateList.valueOf(Palette.blue)
    }
    private val error = ui.line(true).apply {
        gravity = android.view.Gravity.CENTER
        setBackgroundColor(Palette.surface)
        setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
        addView(ui.label("Page indisponible", 19, bold = true))
        addView(ui.label("Vérifie Internet et Android System WebView.", 14, Palette.muted).apply { maxLines = 3 })
        addView(ui.action("Réessayer") { loadHome() }, LinearLayout.LayoutParams(-1, ui.dp(48)))
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(error, LayoutParams(-1, -1))
        addView(progress, LayoutParams(-1, ui.dp(3), android.view.Gravity.TOP))
        error.visibility = GONE
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun start(saved: Bundle? = null) {
        if (browser != null) { browser?.onResume(); return }
        if (!hasNetwork()) { showError(); return }
        try {
            val web = WebView(activity)
            browser = web
            web.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = true
                setGeolocationEnabled(maps)
                setSupportMultipleWindows(false)
            }
            web.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) focused()
                false
            }
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val uri = request.url
                    if (inside(uri)) return false
                    if (uri.scheme == "intent") {
                        val parsed = runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
                        val fallback = parsed?.getStringExtra("browser_fallback_url")?.let(Uri::parse)
                        if (!maps && request.hasGesture() && parsed?.`package` == AppLinks.YOUTUBE) {
                            val link = listOfNotNull(parsed.data, fallback).firstOrNull(::inside)
                            if (nativeYouTube?.invoke(link) == true) return true
                        }
                        val webTarget = listOfNotNull(fallback, parsed?.data).firstOrNull(::inside)
                        if (webTarget != null) {
                            view.loadUrl(webTarget.toString())
                        } else if (request.hasGesture()) parsed?.data?.takeIf {
                            it.scheme in setOf("https", "geo", "google.navigation")
                        }?.let(external)
                    } else if (!maps && request.hasGesture() && uri.scheme in setOf("vnd.youtube", "vnd.youtube.launch")) {
                        nativeYouTube?.invoke(null)
                    } else if (request.hasGesture() && uri.scheme in setOf("https", "geo", "google.navigation")) {
                        external(uri)
                    }
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    error.visibility = GONE
                    progress.visibility = VISIBLE
                }

                override fun onPageCommitVisible(view: WebView, url: String?) {
                    if (maps) view.evaluateJavascript(MapWebPreference.script, null)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    progress.visibility = GONE
                    // Select only the provider's existing web-choice action; preserve all page content.
                    if (maps) view.evaluateJavascript(MapWebPreference.script, null)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame) showError()
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                    if (request.isForMainFrame && response.statusCode >= 400) showError()
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    hideFullscreen()
                    removeView(view)
                    view.destroy()
                    browser = null
                    showError()
                    return true
                }
            }
            web.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, value: Int) { progress.progress = value }

                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                    val uri = Uri.parse(origin)
                    val trusted = maps && uri.scheme == "https" && uri.host in setOf("www.google.com", "maps.google.com", "google.com")
                    val permitted = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (trusted && !permitted) requestLocation()
                    callback.invoke(origin, trusted && permitted, false)
                }

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreen != null) { callback.onCustomViewHidden(); return }
                    fullscreen = view
                    fullscreenCallback = callback
                    web.visibility = GONE
                    addView(view, LayoutParams(-1, -1))
                }

                override fun onHideCustomView() { hideFullscreen() }
            }
            addView(web, 0, LayoutParams(-1, -1))
            if (saved == null || web.restoreState(saved) == null) web.loadUrl(home)
        } catch (_: RuntimeException) {
            browser?.let { removeView(it); it.destroy() }
            browser = null
            showError()
        }
    }

    private fun inside(uri: Uri): Boolean {
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443)) return false
        return if (maps) uri.host in setOf("www.google.com", "google.com", "maps.google.com", "consent.google.com", "maps.app.goo.gl") ||
            (uri.host == "goo.gl" && uri.path?.startsWith("/maps/") == true)
        else uri.host in setOf("www.youtube.com", "m.youtube.com", "youtube.com", "youtu.be", "consent.youtube.com")
    }

    private fun showError() { progress.visibility = GONE; error.visibility = VISIBLE }

    fun loadHome() {
        if (!hasNetwork()) { showError(); return }
        error.visibility = GONE
        if (browser == null) start() else browser?.loadUrl(home)
    }

    private fun hasNetwork() = activity.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork != null

    fun back(): Boolean {
        if (fullscreen != null) { hideFullscreen(); return true }
        val web = browser ?: return false
        if (!web.canGoBack()) return false
        web.goBack()
        return true
    }

    fun save(): Bundle? = browser?.let { web -> Bundle().also { web.saveState(it) } }

    fun pause() { hideFullscreen(); browser?.onPause() }

    fun release() {
        hideFullscreen()
        browser?.let { removeView(it); it.stopLoading(); it.destroy() }
        browser = null
    }

    private fun hideFullscreen() {
        fullscreen?.let(::removeView)
        fullscreen = null
        val callback = fullscreenCallback
        fullscreenCallback = null
        callback?.onCustomViewHidden()
        browser?.visibility = VISIBLE
    }
}
