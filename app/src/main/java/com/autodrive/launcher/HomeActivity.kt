package com.autodrive.launcher

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import com.autodrive.radiomaroc.AmbientBackdrop
import com.autodrive.radiomaroc.LocalInfoFeed
import com.autodrive.radiomaroc.PlayerGlow
import com.autodrive.radiomaroc.RadioCity
import com.autodrive.radiomaroc.RadioMotion
import com.autodrive.radiomaroc.SystemNight
import com.autodrive.radiomaroc.WeatherIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * AutoDrive's home screen for the head unit: time, weather and the next prayer, what plays now in any app, the GPS
 * speed, a Maps shortcut and the listener's favourite apps, over the same glass look as Radio Maroc & Coran.
 * Maps opens full screen: showing it inside this screen is blocked on the Incarlink firmware (NATIVE-PANELS-AUDIT.md).
 */
class HomeActivity : Activity() {
    private enum class Appearance(val label: String, val night: Int?) {
        SYSTEM("Système", null), DAY("Jour", Configuration.UI_MODE_NIGHT_NO), NIGHT("Nuit", Configuration.UI_MODE_NIGHT_YES);

        companion object {
            fun restore(prefs: SharedPreferences) = entries.firstOrNull { it.name == prefs.getString(APPEARANCE, null) } ?: SYSTEM
        }
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var links: AppLinks
    private lateinit var media: MediaSessions
    private lateinit var gps: GpsMonitor
    private lateinit var info: LocalInfoFeed
    private var scope: CoroutineScope? = null
    private val handler = Handler(Looper.getMainLooper())

    private val text by lazy { getColor(R.color.home_text) }
    private val muted by lazy { getColor(R.color.home_muted) }
    private val accent by lazy { getColor(R.color.home_accent) }
    private val night get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weatherIcon: WeatherIcon
    private lateinit var weatherText: TextView
    private lateinit var prayerText: TextView
    private lateinit var mediaPanel: LinearLayout
    private lateinit var mediaArt: ImageView
    private lateinit var mediaInitial: TextView
    private lateinit var mediaApp: TextView
    private lateinit var mediaTitle: TextView
    private lateinit var mediaSubtitle: TextView
    private lateinit var mediaControls: LinearLayout
    private lateinit var mediaAction: TextView
    private lateinit var playButton: ImageButton
    private var mediaGlow: PlayerGlow? = null
    private var mediaSource: String? = null
    private lateinit var speedValue: TextView
    private lateinit var speedStatus: TextView
    private lateinit var favoritesGrid: LinearLayout
    private lateinit var panelHost: FrameLayout
    private val backToHome = { closeOverlay() }

    override fun attachBaseContext(newBase: Context) {
        val appearance = Appearance.restore(newBase.getSharedPreferences(PREFS, MODE_PRIVATE))
        val night = appearance.night ?: if (runCatching { SystemNight.isNight(newBase) }.getOrDefault(true))
            Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val type = newBase.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        super.attachBaseContext(newBase.createConfigurationContext(Configuration().apply { uiMode = type or night }))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        links = AppLinks(this)
        media = MediaSessions(this, ::renderMedia).apply { followActive = true }
        gps = GpsMonitor(this, ::renderSpeed)
        info = LocalInfoFeed(RadioCity.restore(prefs.getString(CITY, null)))
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(screen())
        window.insetsController?.setSystemBarsAppearance(
            if (night) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { closeOverlay() }
    }

    override fun onStart() {
        super.onStart()
        media.start()
        if (gps.permitted()) gps.start()
        val tasks = MainScope()
        scope = tasks
        tasks.launch { info.city.collect { city -> info.refreshWeather(city) } }
        tasks.launch { info.refreshPrayerCalendars() }
        tasks.launch { info.weather.collect { renderWeather() } }
        tasks.launch { info.prayerCalendars.collect { renderPrayer() } }
        tick.run()
        renderMedia()
        renderSpeed()
        renderFavorites()
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        scope?.cancel()
        scope = null
        media.stop()
        gps.stop()
        super.onStop()
    }

    // Radio Maroc & Coran hides its floating bubble while this home screen, which already shows the radio, is in front.
    override fun onResume() {
        super.onResume()
        tellRadio(visible = true)
    }

    override fun onPause() {
        tellRadio(visible = false)
        super.onPause()
    }

    private fun tellRadio(visible: Boolean) {
        sendBroadcast(Intent(RADIO_HOME_VISIBLE).setPackage(RADIO_PACKAGE).putExtra("visible", visible))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (gps.permitted()) gps.start()
        renderSpeed()
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = LocalDateTime.now()
            clock.text = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            date.text = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
                .replaceFirstChar { it.titlecase(Locale.FRENCH) }
            renderPrayer()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    // ——— Layout ———

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, 700, false)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    /** A frosted glass card, pressable when it has an action. */
    private fun glass(radius: Int = 22): Drawable = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(getColor(R.color.home_card))
        setStroke(dp(1), getColor(R.color.home_card_border))
    }

    private fun pressable(base: Drawable, radius: Int = 22) = RippleDrawable(
        ColorStateList.valueOf(getColor(R.color.home_ripple)), base,
        GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(Color.WHITE) })

    private fun round(icon: Int, description: String, primary: Boolean = false, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(if (primary) getColor(R.color.home_on_primary) else text)
        background = pressable(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (primary) getColor(R.color.home_primary) else getColor(R.color.home_chip))
        }, 40)
        contentDescription = description
        setOnClickListener { action() }
        RadioMotion.pressScale(this, .9f)
    }

    private fun chip() = row().apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(getColor(R.color.home_card))
            setStroke(dp(1), getColor(R.color.home_card_border))
        }
        setPadding(dp(14), 0, dp(14), 0)
    }

    private fun screen(): View {
        val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(getColor(R.color.home_background_top), getColor(R.color.home_background_bottom)))
        val backdrop: Drawable = if (night) base
            else AmbientBackdrop(base, intArrayOf(0x8A2BB58A.toInt(), 0x99F2B45A.toInt(), 0x80EB8A78.toInt()))
        val content = column().apply {
            addView(topBar(), LinearLayout.LayoutParams(-1, dp(58)))
            addView(row().apply {
                gravity = Gravity.FILL
                addView(mediaCard(), LinearLayout.LayoutParams(0, -1, 1.35f))
                addView(column().apply {
                    addView(speedCard(), LinearLayout.LayoutParams(-1, 0, 1.15f))
                    addView(mapsCard(), LinearLayout.LayoutParams(-1, 0, .85f).apply { topMargin = dp(10) })
                }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(10); marginEnd = dp(10) })
                addView(favoritesCard(), LinearLayout.LayoutParams(0, -1, 1.05f))
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6); bottomMargin = dp(10) })
            addView(dock(), LinearLayout.LayoutParams(-1, dp(60)))
        }
        panelHost = FrameLayout(this).apply { visibility = View.GONE }
        return FrameLayout(this).apply {
            background = backdrop
            addView(content, FrameLayout.LayoutParams(-1, -1))
            addView(panelHost, FrameLayout.LayoutParams(-1, -1))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                content.setPadding(bars.left + dp(12), bars.top + dp(8), bars.right + dp(12), bars.bottom + dp(10))
                panelHost.setPadding(bars.left + dp(12), bars.top + dp(8), bars.right + dp(12), bars.bottom + dp(10))
                insets
            }
        }
    }

    private fun topBar() = row().apply {
        addView(column().apply {
            clock = label("--:--", 34, text, bold = true)
            date = label("", 13, muted)
            addView(clock)
            addView(date, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(chip().apply {
            weatherIcon = WeatherIcon(context, text)
            addView(weatherIcon, LinearLayout.LayoutParams(dp(24), dp(24)))
            weatherText = label("—", 16, text, bold = true)
            addView(weatherText, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            contentDescription = "Météo"
        }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(8) })
        addView(chip().apply {
            prayerText = label("Salat", 15, text, bold = true)
            addView(prayerText)
        }, LinearLayout.LayoutParams(-2, dp(44)))
    }

    private fun mediaCard(): View {
        mediaPanel = column().apply {
            setPadding(dp(16), dp(16), dp(16), dp(14))
            setOnClickListener { openMediaSource() }
            addView(row().apply {
                val art = FrameLayout(context).apply {
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) =
                            outline.setRoundRect(0, 0, view.width, view.height, dp(18).toFloat())
                    }
                    background = GradientDrawable().apply { setColor(getColor(R.color.home_chip)) }
                    mediaInitial = label("♪", 34, text, bold = true).apply { gravity = Gravity.CENTER }
                    addView(mediaInitial, FrameLayout.LayoutParams(-1, -1))
                    mediaArt = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                    addView(mediaArt, FrameLayout.LayoutParams(-1, -1))
                }
                addView(art, LinearLayout.LayoutParams(dp(104), dp(104)))
                addView(column().apply {
                    mediaApp = label("MÉDIA", 11, accent, bold = true)
                    mediaTitle = label("Rien en lecture", 21, text, bold = true).apply { maxLines = 2 }
                    mediaSubtitle = label("", 13, muted).apply { maxLines = 2 }
                    addView(mediaApp)
                    addView(mediaTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                    addView(mediaSubtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
            })
            addView(View(context), LinearLayout.LayoutParams(-1, 0, 1f))
            mediaControls = row().apply {
                gravity = Gravity.CENTER
                addView(round(R.drawable.ic_home_previous, "Précédent") { media.command(PlaybackState.ACTION_SKIP_TO_PREVIOUS) },
                    LinearLayout.LayoutParams(dp(52), dp(52)))
                playButton = round(R.drawable.ic_home_play, "Lecture", primary = true) { media.command(PlaybackState.ACTION_PLAY_PAUSE)
                    .takeIf { it } ?: media.command(PlaybackState.ACTION_PLAY) }
                addView(playButton, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginStart = dp(18); marginEnd = dp(18) })
                addView(round(R.drawable.ic_home_next, "Suivant") { media.command(PlaybackState.ACTION_SKIP_TO_NEXT) },
                    LinearLayout.LayoutParams(dp(52), dp(52)))
            }
            addView(mediaControls, LinearLayout.LayoutParams(-1, -2))
            mediaAction = label("", 15, getColor(R.color.home_on_primary), bold = true).apply {
                gravity = Gravity.CENTER
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(26).toFloat()
                    setColor(getColor(R.color.home_primary))
                }, 26)
                visibility = View.GONE
            }
            addView(mediaAction, LinearLayout.LayoutParams(-1, dp(52)))
        }
        mediaGlow = PlayerGlow(mediaPanel, pressable(glass()), accent, if (night) 110 else 80, .2f, .3f, dp(22).toFloat())
        return mediaPanel
    }

    private fun speedCard() = column().apply {
        background = pressable(glass())
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        addView(label("VITESSE", 11, accent, bold = true), LinearLayout.LayoutParams(-2, -2))
        speedValue = label("—", 58, text, bold = true)
        addView(speedValue, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        addView(label("km/h", 14, muted), LinearLayout.LayoutParams(-2, -2))
        speedStatus = label("", 11, muted)
        addView(speedStatus, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        setOnClickListener {
            if (!gps.permitted()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
    }

    private fun mapsCard() = row().apply {
        background = pressable(glass())
        setPadding(dp(16), 0, dp(12), 0)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_home_maps)
            imageTintList = ColorStateList.valueOf(accent)
        }, LinearLayout.LayoutParams(dp(34), dp(34)))
        addView(column().apply {
            addView(label("Maps", 19, text, bold = true))
            addView(label("Ouvrir la navigation", 12, muted), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        contentDescription = "Ouvrir Google Maps"
        setOnClickListener {
            if (!links.openMaps()) Toast.makeText(context, "Google Maps n’est pas installé.", Toast.LENGTH_LONG).show()
        }
        RadioMotion.pressScale(this, .97f)
    }

    private fun favoritesCard() = column().apply {
        background = glass()
        setPadding(dp(10), dp(12), dp(10), dp(10))
        addView(row().apply {
            addView(label("FAVORIS", 11, accent, bold = true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
            addView(label("Modifier", 12, muted, bold = true).apply {
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener { showApps(editing = true) }
            })
        })
        favoritesGrid = column()
        addView(favoritesGrid, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
    }

    private fun dock() = row().apply {
        background = glass(30)
        setPadding(dp(6), dp(6), dp(6), dp(6))
        fun item(icon: Int, title: String, action: () -> Unit) = row().apply {
            gravity = Gravity.CENTER
            background = pressable(GradientDrawable().apply { cornerRadius = dp(24).toFloat() }, 24)
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(text)
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(label(title, 15, text, bold = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
            contentDescription = title
            setOnClickListener { action() }
        }
        addView(item(R.drawable.ic_home_home, "Accueil") { closeOverlay() }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(item(R.drawable.ic_home_phone, "Téléphone") {
            if (!links.openPhone()) Toast.makeText(context, "Aucune application de téléphone trouvée.", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(item(R.drawable.ic_home_apps, "Applis") { showApps(editing = false) }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(item(R.drawable.ic_home_settings, "Réglages") { showSettings() }, LinearLayout.LayoutParams(0, -1, 1f))
    }

    // ——— Live content ———

    private fun renderWeather() {
        val weather = info.weather.value
        weatherIcon.bind(weather)
        weatherText.text = weather?.let { "${it.degrees}°" } ?: "—"
    }

    private fun renderPrayer() {
        if (!::prayerText.isInitialized) return
        val next = runCatching { info.prayerStatus(info.city.value).next }.getOrNull()
        prayerText.text = next?.let { "${it.prayer.label}  ${it.dateTime.toLocalTime()}" } ?: "Salat"
    }

    private fun renderSpeed() {
        if (!::speedValue.isInitialized) return
        if (!gps.permitted()) {
            speedValue.text = "—"
            speedStatus.text = "Toucher pour activer le GPS"
            return
        }
        val state = gps.snapshot()
        speedValue.text = state.speed.kmh?.toString() ?: "—"
        speedStatus.text = when (state.status) {
            "Precise location off" -> "Localisation précise désactivée"
            "GPS unavailable" -> "GPS indisponible"
            "GPS disabled" -> "GPS désactivé"
            "Waiting for GPS" -> "Recherche du GPS…"
            "Mock location ignored" -> "Position simulée ignorée"
            else -> state.direction.orEmpty()
        }
    }

    private fun renderMedia() {
        if (!::mediaTitle.isInitialized) return
        val state = media.snapshot()
        mediaSource = state.source
        val hasMedia = state.source != null && (state.title != null || state.playing)
        mediaControls.visibility = if (hasMedia) View.VISIBLE else View.GONE
        mediaAction.visibility = if (hasMedia) View.GONE else View.VISIBLE
        when {
            !media.hasAccess() -> {
                mediaApp.text = "MÉDIA"
                mediaTitle.text = "Voir ce qui joue"
                mediaSubtitle.text = "Autorise l’accès aux médias pour afficher et piloter la radio, YouTube ou Spotify"
                mediaAction.text = "Autoriser l’accès"
                mediaAction.setOnClickListener { openMediaAccess() }
            }
            !hasMedia -> {
                mediaApp.text = "MÉDIA"
                mediaTitle.text = "Rien en lecture"
                mediaSubtitle.text = "Lance une radio ou une vidéo"
                val radio = packageManager.getLaunchIntentForPackage(RADIO_PACKAGE)
                mediaAction.text = if (radio != null) "Ouvrir Radio Maroc & Coran" else "Ouvrir les applis"
                mediaAction.setOnClickListener { if (radio != null) startSafely(radio) else showApps(editing = false) }
            }
            else -> {
                mediaApp.text = appLabel(state.source).uppercase(Locale.FRENCH)
                mediaTitle.text = state.title ?: appLabel(state.source)
                mediaSubtitle.text = state.subtitle.orEmpty()
            }
        }
        val art = state.artwork.takeIf { hasMedia }
        mediaArt.setImageBitmap(art)
        mediaArt.visibility = if (art != null) View.VISIBLE else View.INVISIBLE
        mediaInitial.text = state.title?.trim()?.take(1)?.takeIf { hasMedia } ?: "♪"
        mediaGlow?.show(art?.let(RadioMotion::logoColor))
        playButton.setImageResource(if (state.playing) R.drawable.ic_home_pause else R.drawable.ic_home_play)
        playButton.contentDescription = if (state.playing) "Pause" else "Lecture"
    }

    private fun appLabel(packageName: String?): String = packageName?.let {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString() }.getOrNull()
    } ?: "Média"

    private fun openMediaSource() {
        val source = mediaSource ?: return
        packageManager.getLaunchIntentForPackage(source)?.let(::startSafely)
    }

    private fun openMediaAccess() {
        startSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, MediaAccessService::class.java).flattenToString()))
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Cette application n’a pas pu s’ouvrir.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Cette application n’a pas pu s’ouvrir.", Toast.LENGTH_LONG).show()
        }
    }

    // ——— Favourites and apps ———

    private fun installedApps(): List<Launchable> = links.apps()

    private fun favorites(): List<Launchable> {
        val apps = installedApps()
        val saved = prefs.getString(FAVORITES, null)
        val keys = saved?.split('\n')?.filter { it.isNotBlank() }
            ?: DEFAULT_FAVORITES.mapNotNull { pkg -> apps.firstOrNull { it.component.packageName == pkg }?.component?.flattenToString() }
        return keys.mapNotNull { key -> apps.firstOrNull { it.component.flattenToString() == key } }.take(6)
    }

    private fun saveFavorites(list: List<Launchable>) {
        prefs.edit().putString(FAVORITES, list.joinToString("\n") { it.component.flattenToString() }).apply()
    }

    private fun toggleFavorite(app: Launchable) {
        val current = favorites().toMutableList()
        if (current.any { it.component == app.component }) {
            current.removeAll { it.component == app.component }
            Toast.makeText(this, "${app.label} retiré des favoris", Toast.LENGTH_SHORT).show()
        } else if (current.size >= 6) {
            Toast.makeText(this, "6 favoris au maximum : retire d’abord une appli.", Toast.LENGTH_LONG).show()
            return
        } else {
            current += app
            Toast.makeText(this, "${app.label} ajouté aux favoris", Toast.LENGTH_SHORT).show()
        }
        saveFavorites(current)
        renderFavorites()
    }

    private fun appTile(app: Launchable, compact: Boolean, onLong: (View) -> Unit) = column().apply {
        gravity = Gravity.CENTER
        background = pressable(GradientDrawable().apply { cornerRadius = dp(16).toFloat() }, 16)
        setPadding(dp(4), dp(6), dp(4), dp(6))
        addView(ImageView(context).apply { setImageDrawable(links.icon(app)) },
            LinearLayout.LayoutParams(dp(if (compact) 40 else 48), dp(if (compact) 40 else 48)))
        addView(label(app.label, 12, text).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        contentDescription = app.label
        setOnClickListener { links.open(app) }
        setOnLongClickListener { onLong(it); true }
        RadioMotion.pressScale(this, .94f)
    }

    private fun renderFavorites() {
        if (!::favoritesGrid.isInitialized) return
        favoritesGrid.removeAllViews()
        val list = favorites()
        if (list.isEmpty()) {
            favoritesGrid.addView(label("Touche « Modifier » ou garde le doigt sur une appli pour l’ajouter ici", 12, muted).apply {
                maxLines = 4
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -1))
            return
        }
        list.chunked(2).forEach { pair ->
            favoritesGrid.addView(row().apply {
                gravity = Gravity.FILL
                pair.forEach { app ->
                    addView(appTile(app, compact = true) { anchor ->
                        PopupMenu(this@HomeActivity, anchor).apply {
                            menu.add("Retirer des favoris")
                            setOnMenuItemClickListener { toggleFavorite(app); true }
                        }.show()
                    }, LinearLayout.LayoutParams(0, -1, 1f))
                }
                if (pair.size == 1) addView(View(context), LinearLayout.LayoutParams(0, -1, 1f))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        repeat(3 - (list.size + 1) / 2) { favoritesGrid.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f)) }
    }

    /** Every app of the head unit; in edit mode a tap adds or removes a favourite instead of opening it. */
    private fun showApps(editing: Boolean) {
        val apps = installedApps()
        val favoriteKeys = favorites().map { it.component }.toMutableSet()
        panelHost.removeAllViews()
        panelHost.addView(column().apply {
            background = sheet()
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(row().apply {
                addView(column().apply {
                    addView(label(if (editing) "Choisir les favoris" else "Applications", 20, text, bold = true))
                    addView(label(if (editing) "Touche une appli pour l’ajouter ou la retirer · 6 au maximum"
                        else "Garde le doigt sur une appli pour l’ajouter aux favoris", 12, muted),
                        LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(round(R.drawable.ic_home_close, "Fermer") { closeOverlay() }, LinearLayout.LayoutParams(dp(44), dp(44)))
            })
            addView(GridView(context).apply {
                numColumns = if (resources.configuration.screenWidthDp >= 700) 6 else 4
                verticalSpacing = dp(6)
                horizontalSpacing = dp(6)
                selector = GradientDrawable()
                adapter = object : BaseAdapter() {
                    override fun getCount() = apps.size
                    override fun getItem(position: Int) = apps[position]
                    override fun getItemId(position: Int) = position.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val app = apps[position]
                        return appTile(app, compact = false) { toggleFavorite(app); notifyDataSetChanged() }.apply {
                            if (editing) setOnClickListener {
                                toggleFavorite(app)
                                if (!favoriteKeys.remove(app.component)) favoriteKeys += app.component
                                notifyDataSetChanged()
                            }
                            alpha = if (editing && app.component !in favorites().map { it.component }) .55f else 1f
                            layoutParams = ViewGroup.LayoutParams(-1, dp(96))
                        }
                    }
                }
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        }, FrameLayout.LayoutParams(-1, -1))
        panelHost.visibility = View.VISIBLE
        panelHost.alpha = 0f
        panelHost.animate().alpha(1f).setDuration(160).start()
    }

    private fun showSettings() {
        panelHost.removeAllViews()
        panelHost.addView(column().apply {
            background = sheet()
            setPadding(dp(16), dp(12), dp(16), dp(14))
            addView(row().apply {
                addView(label("Réglages", 20, text, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(round(R.drawable.ic_home_close, "Fermer") { closeOverlay() }, LinearLayout.LayoutParams(dp(44), dp(44)))
            })
            addView(label("JOUR / NUIT", 11, accent, bold = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) })
            addView(row().apply {
                val current = Appearance.restore(prefs)
                Appearance.entries.forEach { choice ->
                    addView(label(choice.label, 15, if (choice == current) getColor(R.color.home_on_primary) else text, bold = true).apply {
                        gravity = Gravity.CENTER
                        background = pressable(GradientDrawable().apply {
                            cornerRadius = dp(22).toFloat()
                            setColor(if (choice == current) getColor(R.color.home_primary) else getColor(R.color.home_chip))
                        })
                        setOnClickListener {
                            prefs.edit().putString(APPEARANCE, choice.name).apply()
                            recreate()
                        }
                    }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(label("VILLE (MÉTÉO ET SALAT)", 11, accent, bold = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
            addView(label(info.city.value.label + "  ›", 16, text, bold = true).apply {
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { anchor ->
                    PopupMenu(this@HomeActivity, anchor).apply {
                        RadioCity.entries.forEach { city -> menu.add(city.label) }
                        setOnMenuItemClickListener { item ->
                            RadioCity.entries.firstOrNull { it.label == item.title }?.let { city ->
                                prefs.edit().putString(CITY, city.name).apply()
                                info.choose(city)
                                (anchor as TextView).text = city.label + "  ›"
                                renderPrayer()
                            }
                            true
                        }
                    }.show()
                }
            })
            addView(View(context), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(label("Réglages Android  ›", 15, text, bold = true).apply {
                gravity = Gravity.CENTER
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat()
                    setColor(getColor(R.color.home_chip))
                })
                setOnClickListener { startSafely(Intent(Settings.ACTION_SETTINGS)) }
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }, FrameLayout.LayoutParams(-1, -1))
        panelHost.visibility = View.VISIBLE
        panelHost.alpha = 0f
        panelHost.animate().alpha(1f).setDuration(160).start()
    }

    /** Pages over the home screen (apps, settings) sit on an almost opaque sheet so the cards behind do not show. */
    private fun sheet(): Drawable = GradientDrawable().apply {
        cornerRadius = dp(26).toFloat()
        val base = getColor(R.color.home_background_bottom)
        setColor(base)
        setStroke(dp(1), getColor(R.color.home_card_border))
    }

    private fun closeOverlay() {
        if (!::panelHost.isInitialized || panelHost.visibility != View.VISIBLE) return
        panelHost.animate().alpha(0f).setDuration(140).withEndAction {
            panelHost.visibility = View.GONE
            panelHost.removeAllViews()
        }.start()
    }

    private companion object {
        const val PREFS = "autodrive_home"
        const val APPEARANCE = "appearance"
        const val CITY = "city"
        const val FAVORITES = "favorites"
        const val RADIO_PACKAGE = "com.autodrive.radiomaroc"
        const val RADIO_HOME_VISIBLE = "com.autodrive.radiomaroc.HOME_VISIBLE"
        // First favourites when the listener has not chosen any: the radio, video and navigation apps of the car.
        val DEFAULT_FAVORITES = listOf(RADIO_PACKAGE, "com.google.android.youtube", "com.waze",
            "com.spotify.music", "com.google.android.apps.youtube.music", "com.android.chrome")
    }
}
