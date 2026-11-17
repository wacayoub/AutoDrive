package com.incardrive.home

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/**
 * A live map on the home screen through the widgets Google Maps and Waze publish for home screens (Android's public
 * AppWidgetHost API): Google Maps' « Trafic à proximité » first, a map of the traffic around the car that Maps keeps
 * up to date, or any other Maps or Waze widget. It is Google's or Waze's own widget, not the full app: searching and
 * guidance stay in the app, which a tap on the widget opens. Android asks the driver once to allow it.
 */
internal class MapWidget(private val home: HomeActivity) {
    private val manager = AppWidgetManager.getInstance(home)
    private val host = AppWidgetHost(home, HOST_ID)
    private var pending: Int? = null
    private lateinit var frame: FrameLayout

    /** The card on the home page, once built. */
    val card: View? get() = if (::frame.isInitialized) frame else null

    private val widgetId get() = home.prefs.getInt(KEY, 0).takeIf { it != 0 && manager.getAppWidgetInfo(it) != null }

    fun start() = runCatching { host.startListening() }
    fun stop() = runCatching { host.stopListening() }

    /** The map widgets installed: Google Maps' traffic map first, then the other Maps and Waze widgets. */
    private fun providers(): List<AppWidgetProviderInfo> = listOf(AppLinks.MAPS, "com.waze").flatMap {
        runCatching { manager.getInstalledProvidersForPackage(it, Process.myUserHandle()) }.getOrDefault(emptyList())
    }.sortedBy { if (it.provider == TRAFFIC) 0 else 1 }

    fun view(): View = with(home) {
        FrameLayout(this).apply {
            frame = this
            background = glass()
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
            }
            addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
                if (r - l != or - ol || b - t != ob - ot) widgetId?.let { size(it, v.width, v.height) }
            }
            render()
        }
    }

    /**
     * The frame shown during guidance in place of this card, reaching down to the bottom of the screen: Google Maps'
     * own small window (picture-in-picture), which Android puts at the bottom right, then sits inside it. When the
     * window is not open, the frame offers to open Maps.
     */
    fun guideView(guiding: Boolean = true, openMaps: () -> Unit): View = with(home) {
        FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (night) 0xFF0F1622.toInt() else 0xFFE9EEF3.toInt())
                setStroke(dp(1), tint(navColor, 0x55))
            }
            isClickable = true
            fun openButton() = label("Ouvrir Maps  ›", 12, onAccent, 700).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(navColor)
                }, 14)
                contentDescription = "Ouvrir Google Maps"
                setOnClickListener { openMaps() }
            }
            addView(column().apply {
                if (!guiding) {
                    addView(row().apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(ImageView(context).apply {
                            setImageResource(R.drawable.ic_home_navigation)
                            imageTintList = ColorStateList.valueOf(navColor)
                            rotation = 45f
                        }, LinearLayout.LayoutParams(dp(18), dp(18)))
                        addView(label("Google Maps", 13, text, 700), wrap().apply { marginStart = dp(6) })
                    })
                    addView(label("Lance un guidage : la fenêtre de Maps se place ici", 11, muted, 500).apply { maxLines = 3 },
                        wrap().apply { topMargin = dp(2) })
                    addView(openButton(), wrap().apply { topMargin = dp(10) })
                    return@apply
                }
                // While guiding: the next turn in large, above where Maps' own window sits (bottom right).
                addView(row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(FrameLayout(context).apply {
                        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(navColor) }
                        guideArrow = ImageView(context).apply { imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE) }
                        addView(guideArrow, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
                    }, LinearLayout.LayoutParams(dp(46), dp(46)))
                    guideStep = label("", 28, text, 700, condensed = true).apply { maxLines = 1 }
                    addView(guideStep, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
                })
                guideLine = label("", 13, text, 500).apply { maxLines = 2 }
                addView(guideLine, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                guideEta = label("", 13, navColor, 700).apply { maxLines = 1 }
                addView(guideEta, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                addView(openButton(), wrap().apply { topMargin = dp(8) })
            }, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { setMargins(dp(12), dp(10), dp(12), 0) })
        }
    }

    private var guideArrow: ImageView? = null
    private var guideStep: android.widget.TextView? = null
    private var guideLine: android.widget.TextView? = null
    private var guideEta: android.widget.TextView? = null

    /** Fills the guidance frame from the navigation app's notification. */
    fun guideUpdate(nav: NavInfo?) {
        val step = guideStep ?: return
        if (nav == null) return
        guideArrow?.apply {
            if (nav.arrow != null) { setImageDrawable(nav.arrow); rotation = 0f }
            else { setImageResource(R.drawable.ic_home_navigation); rotation = 45f }
        }
        val title = nav.step ?: nav.remaining ?: "Guidage"
        step.text = title
        fun plain(value: String) = value.filterNot { it.isWhitespace() }.lowercase()
        val line = nav.instruction?.takeIf { it.isNotBlank() && plain(it) != plain(title) }
        guideLine?.apply {
            text = line.orEmpty()
            visibility = if (line == null) View.GONE else View.VISIBLE
        }
        guideEta?.setTextColor(nav.flow?.color ?: home.navColor)
        guideEta?.text = listOfNotNull(nav.remaining?.takeIf { nav.step != null }, nav.arrival, nav.distance?.takeIf { plain(it) != plain(title) })
            .joinToString(" · ")
    }

    fun render() {
        if (!::frame.isInitialized) return
        frame.removeAllViews()
        val id = widgetId
        val info = id?.let { manager.getAppWidgetInfo(it) }
        if (id != null && info != null) {
            frame.addView(host.createView(home, id, info).apply { setPadding(0, 0, 0, 0) }, FrameLayout.LayoutParams(-1, -1))
            if (frame.width > 0) size(id, frame.width, frame.height)
            // A small button in the corner to change or remove the map.
            frame.addView(ImageView(home).apply {
                setImageResource(R.drawable.ic_home_tune)
                imageTintList = ColorStateList.valueOf(home.text)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(home.tint(home.text, 0x33)) }
                setPadding(home.dp(6), home.dp(6), home.dp(6), home.dp(6))
                contentDescription = "Changer la carte"
                setOnClickListener { manage() }
            }, FrameLayout.LayoutParams(home.dp(30), home.dp(30), Gravity.TOP or Gravity.END).apply {
                setMargins(0, home.dp(6), home.dp(6), 0)
            })
            return
        }
        frame.addView(with(home) {
            column().apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(10), dp(14), dp(10))
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_home_navigation)
                    imageTintList = ColorStateList.valueOf(navColor)
                    rotation = 45f
                }, LinearLayout.LayoutParams(dp(34), dp(34)))
                addView(label("Carte en direct", 17, text, 700, condensed = true).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                addView(label(if (providers().isEmpty()) "Installe Google Maps ou Waze pour afficher leur carte ici"
                    else "Le trafic autour de toi, par Google Maps. Touche pour l’afficher.", 12, muted).apply {
                    gravity = Gravity.CENTER
                    maxLines = 3
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                setOnClickListener { choose() }
            }
        }, FrameLayout.LayoutParams(-1, -1))
    }

    /** Tells the widget its real size, so Maps draws its map to fit. */
    private fun size(id: Int, widthPx: Int, heightPx: Int) {
        val density = home.resources.displayMetrics.density
        val w = (widthPx / density).toInt()
        val h = (heightPx / density).toInt()
        runCatching {
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(w.toFloat(), h.toFloat())))
                }
            })
        }
    }

    /** Google Maps' traffic map straight away when it is there; otherwise a choice among the Maps and Waze widgets. */
    fun choose() {
        val list = providers()
        when {
            list.isEmpty() -> Toast.makeText(home, "Installe Google Maps ou Waze pour afficher leur carte.", Toast.LENGTH_LONG).show()
            list.first().provider == TRAFFIC -> add(list.first())
            list.size == 1 -> add(list.single())
            else -> AlertDialog.Builder(home)
                .setTitle("Carte sur l’accueil")
                .setItems(list.map { it.loadLabel(home.packageManager) }.toTypedArray()) { _, index -> add(list[index]) }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun manage() {
        AlertDialog.Builder(home)
            .setTitle("Carte sur l’accueil")
            .setItems(arrayOf("Choisir une autre carte", "Retirer la carte")) { _, index ->
                widgetId?.let { runCatching { host.deleteAppWidgetId(it) } }
                home.prefs.edit().remove(KEY).apply()
                if (index == 0) {
                    val others = providers()
                    if (others.size > 1) AlertDialog.Builder(home)
                        .setItems(others.map { it.loadLabel(home.packageManager) }.toTypedArray()) { _, i -> add(others[i]) }
                        .show()
                    else choose()
                }
                render()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun add(info: AppWidgetProviderInfo) {
        widgetId?.let { runCatching { host.deleteAppWidgetId(it) } }
        val id = host.allocateAppWidgetId()
        pending = id
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        }
        if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, options)) configure(id)
        else try {
            @Suppress("DEPRECATION")
            home.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options), REQUEST_BIND)
        } catch (_: ActivityNotFoundException) {
            cancel()
        }
    }

    private fun configure(id: Int) {
        val info = manager.getAppWidgetInfo(id) ?: return cancel()
        if (info.configure == null) return finish(id)
        try {
            host.startAppWidgetConfigureActivityForResult(home, id, 0, REQUEST_CONFIGURE, null)
        } catch (_: Exception) {
            finish(id)
        }
    }

    private fun finish(id: Int) {
        pending = null
        home.prefs.edit().putInt(KEY, id).apply()
        render()
    }

    private fun cancel() {
        pending?.let { runCatching { host.deleteAppWidgetId(it) } }
        pending = null
    }

    fun onResult(requestCode: Int, resultCode: Int): Boolean {
        val id = pending ?: return false
        when (requestCode) {
            REQUEST_BIND -> if (resultCode == Activity.RESULT_OK) configure(id) else cancel()
            REQUEST_CONFIGURE -> if (resultCode == Activity.RESULT_OK) finish(id) else cancel()
            else -> return false
        }
        return true
    }

    private companion object {
        const val HOST_ID = 2027
        const val KEY = "map_widget"
        const val REQUEST_BIND = 45
        const val REQUEST_CONFIGURE = 46
        val TRAFFIC = ComponentName(AppLinks.MAPS, "com.google.android.apps.gmm.widget.traffic.TrafficWidgetProvider")
    }
}
