package com.incardrive.home

import android.annotation.SuppressLint
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.util.Locale

/**
 * Waze's live map on the home page: its official embeddable map (embed.waze.com), free and without an account, with
 * Waze's traffic and the alerts its drivers report. It is Waze's web map shown on its own (Waze asks not to mix it
 * with other maps): it does not follow the car by itself, so it is centred again on the car as it moves away, and
 * it shows neither a route nor the guidance. The buttons centre it now and open the Waze app.
 */
internal class WazeMap(private val home: HomeActivity, private val openWaze: () -> Unit) {
    private var web: WebView? = null
    private var latitude = Double.NaN
    private var longitude = Double.NaN
    private var shownLatitude = Double.NaN
    private var shownLongitude = Double.NaN
    private var shownAt = 0L

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun view(): View = with(home) {
        FrameLayout(this).apply {
            background = glass()
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
            }
            val page = runCatching { WebView(context) }.getOrNull()
            if (page == null) {
                // No system WebView on this head unit: say so instead of an empty card.
                addView(label("La carte Waze a besoin du composant « Android System WebView », absent de ce boîtier.", 12, muted).apply {
                    gravity = Gravity.CENTER
                    maxLines = 4
                    setPadding(dp(14), 0, dp(14), 0)
                }, FrameLayout.LayoutParams(-1, -1))
                return@apply
            }
            web = page
            page.settings.javaScriptEnabled = true // Waze's map is a script
            page.settings.domStorageEnabled = true
            page.settings.allowFileAccess = false
            page.settings.allowContentAccess = false
            page.settings.setGeolocationEnabled(false)
            page.setBackgroundColor(if (night) 0xFF121A26.toInt() else 0xFFE6E9ED.toInt())
            page.webViewClient = object : WebViewClient() {
                // The map only: any link out of Waze's embed is ignored.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    request.url.host != HOST
            }
            // Dragging the map sideways moves the map, not the home pages.
            page.setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) view.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
            addView(page, FrameLayout.LayoutParams(-1, -1))
            addView(row().apply {
                fun button(title: String, description: String, action: () -> Unit) = label(title, 12, text, 700).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(10), 0, dp(10), 0)
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(if (night) 0xE6141C2A.toInt() else 0xF2FFFFFF.toInt())
                        setStroke(dp(1), tint(home.text, 0x22))
                    }, 10)
                    contentDescription = description
                    setOnClickListener { action() }
                }
                addView(button("Centrer", "Centrer la carte sur la voiture") { show(force = true) },
                    LinearLayout.LayoutParams(-2, dp(30)))
                addView(button("Ouvrir Waze  ›", "Ouvrir l’application Waze") { openWaze() },
                    LinearLayout.LayoutParams(-2, dp(30)).apply { marginStart = dp(6) })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(8), 0, 0, dp(34)) })
            show(force = true)
        }
    }

    /** The car's position: the map is centred again once the car is 400 m away, at most once a minute. */
    fun position(lat: Double, lon: Double) {
        latitude = lat
        longitude = lon
        show(force = false)
    }

    private fun show(force: Boolean) {
        val page = web ?: return
        if (latitude.isNaN()) return
        val now = System.currentTimeMillis()
        if (!force && !shownLatitude.isNaN()) {
            val moved = FloatArray(1).also { Location.distanceBetween(shownLatitude, shownLongitude, latitude, longitude, it) }[0]
            if (moved < RECENTRE_M || now - shownAt < RECENTRE_MS) return
        }
        shownLatitude = latitude
        shownLongitude = longitude
        shownAt = now
        // As Waze documents it: its map inside a frame that fills the card.
        val url = String.format(Locale.US, "https://%s/fr/iframe?zoom=15&lat=%.5f&lon=%.5f&pin=1", HOST, latitude, longitude)
        page.loadDataWithBaseURL("https://$HOST/", """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>html,body{margin:0;height:100%;overflow:hidden;background:transparent}iframe{border:0;width:100%;height:100%}</style></head>
            <body><iframe src="$url" allowfullscreen></iframe></body></html>""", "text/html", "utf-8", null)
    }

    fun resume() = web?.onResume()
    fun pause() = web?.onPause()

    fun destroy() {
        web?.destroy()
        web = null
    }

    private companion object {
        const val HOST = "embed.waze.com"
        const val RECENTRE_M = 400f
        const val RECENTRE_MS = 60_000L
    }
}
