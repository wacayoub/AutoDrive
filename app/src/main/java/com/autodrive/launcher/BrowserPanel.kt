package com.autodrive.launcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ContextThemeWrapper
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
    private val openAccountBrowser: (() -> Boolean)? = null,
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
    private val account: android.widget.ScrollView = android.widget.ScrollView(activity).apply {
        contentDescription = "Connexion Google dans le navigateur"
        setBackgroundColor(Palette.surface)
        addView(ui.line(true).apply {
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            addView(ui.label("Compte Google", 19, bold = true))
            addView(ui.label("Google peut refuser une connexion dans un panneau intégré. Le navigateur a sa propre connexion : ouvre la page complète pour l’utiliser.", 14, Palette.muted).apply { maxLines = 8 }, LinearLayout.LayoutParams(-1, -2))
            addView(ui.action("Ouvrir dans le navigateur") {
                if (openAccountBrowser?.invoke() != true) android.widget.Toast.makeText(activity, "Aucun navigateur compatible disponible", android.widget.Toast.LENGTH_LONG).show()
            }, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(8) })
            addView(ui.action("Retour au panneau") { this@BrowserPanel.account.visibility = GONE }, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(4) })
        })
        visibility = GONE
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(error, LayoutParams(-1, -1))
        addView(progress, LayoutParams(-1, ui.dp(3), android.view.Gravity.TOP))
        addView(account, LayoutParams(-1, -1))
        error.visibility = GONE
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun start(saved: Bundle? = null) {
        if (browser != null) { browser?.onResume(); return }
        if (!hasNetwork()) { showError(); return }
        try {
            // Keep a stable light provider rendering, then adapt the complete map display at night.
            val web = WebView(if (maps) ContextThemeWrapper(activity, R.style.AutoDriveMapWebTheme) else activity)
            WebSessionStorage.configure(web)
            if (maps) MapDisplayTheme.apply(web)
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
                    val googleAccounts = uri.scheme == "https" && uri.userInfo == null && uri.port in setOf(-1, 443) &&
                        uri.host in setOf("accounts.google.com", "accounts.youtube.com")
                    val oauth = googleAccounts && (uri.path.orEmpty().startsWith("/o/oauth2") || uri.path.orEmpty().startsWith("/signin/oauth") ||
                        (uri.getQueryParameter("client_id") != null && uri.getQueryParameter("response_type") != null))
                    if (googleAccounts && oauth) {
                        // Never transfer a browser login back into WebView, or attempt embedded OAuth.
                        showAccountNotice()
                        return true
                    }
                    // A first-party website sign-in stays in its existing WebView profile.
                    // Google may refuse this browser; do not spoof it or bypass that refusal.
                    if (googleAccounts) return false
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
                    account.visibility = GONE
                    error.visibility = GONE
                    progress.visibility = VISIBLE
                }

                override fun onPageCommitVisible(view: WebView, url: String?) {
                    if (maps) view.evaluateJavascript(MapWebPreference.script, null)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    WebSessionStorage.persist()
                    progress.visibility = GONE
                    // Apply the owner's Maps web preference without changing attribution or map data.
                    if (maps) view.evaluateJavascript(MapWebPreference.script, null)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame) showError()
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                    if (request.isForMainFrame && response.statusCode >= 400) {
                        if (request.url.scheme == "https" && request.url.host in setOf("accounts.google.com", "accounts.youtube.com")) showAccountNotice()
                        else showError()
                    }
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
    private fun showAccountNotice() { progress.visibility = GONE; error.visibility = GONE; account.visibility = VISIBLE }

    fun loadHome() {
        account.visibility = GONE
        if (!hasNetwork()) { showError(); return }
        error.visibility = GONE
        if (browser == null) start() else browser?.loadUrl(home)
    }

    private fun hasNetwork() = activity.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork != null

    fun back(): Boolean {
        if (account.visibility == VISIBLE) { account.visibility = GONE; return true }
        if (fullscreen != null) { hideFullscreen(); return true }
        val web = browser ?: return false
        if (!web.canGoBack()) return false
        web.goBack()
        return true
    }

    fun save(): Bundle? = browser?.let { web ->
        WebSessionStorage.persist()
        Bundle().also { web.saveState(it) }
    }

    fun pause() {
        hideFullscreen()
        // Retain the document, history and sessionStorage; stop ordinary HTML media while hidden.
        browser?.evaluateJavascript("document.querySelectorAll('video,audio').forEach(media => media.pause());", null)
        browser?.onPause()
        WebSessionStorage.persist()
    }

    fun release() {
        WebSessionStorage.persist()
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
