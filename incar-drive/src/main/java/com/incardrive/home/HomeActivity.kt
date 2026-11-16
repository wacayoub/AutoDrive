package com.incardrive.home

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.location.LocationManager
import android.media.session.PlaybackState
import android.os.Build
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
import android.text.InputType
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import com.autodrive.radiomaroc.EqualizerBars
import com.autodrive.radiomaroc.LocalInfoFeed
import com.autodrive.radiomaroc.RadioCity
import com.autodrive.radiomaroc.RadioMotion
import com.autodrive.radiomaroc.SystemNight
import com.autodrive.radiomaroc.WeatherIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Incar Drive, the home screen for the Incarlink head unit, styled like a dashboard: graphite by night, silver by
 * day, one accent colour the driver picks (amber, cyan or red), a road grid on the horizon, a graduated speed dial,
 * the media playing in any app, a Maps card and favourite apps tinted with their icons. Maps opens full screen: showing it inside this screen is blocked on the Incarlink
 * firmware (NATIVE-PANELS-AUDIT.md).
 */
class HomeActivity : Activity() {
    private enum class Appearance(val label: String, val night: Int?) {
        SYSTEM("Système", null), DAY("Jour", Configuration.UI_MODE_NIGHT_NO), NIGHT("Nuit", Configuration.UI_MODE_NIGHT_YES);

        companion object {
            fun restore(prefs: SharedPreferences) = entries.firstOrNull { it.name == prefs.getString(APPEARANCE, null) } ?: SYSTEM
        }
    }

    private enum class Page { HOME, APPS, SETTINGS }

    /** The two saved destinations: one tap starts Google Maps guidance there. */
    private enum class Place(val label: String, val icon: Int, val key: String) {
        HOME("Maison", R.drawable.ic_home_home, "place_home"), WORK("Travail", R.drawable.ic_home_work, "place_work")
    }

    /**
     * The dashboard light: one colour for the speed arc, the play button, the dock and the labels. « Neutre », the
     * default, is white on graphite at night and graphite on silver by day, with no tint from logos or icons.
     */
    private enum class Accent(val label: String, val night: Int, val day: Int) {
        NEUTRAL("Neutre", 0xFFE4E8EE.toInt(), 0xFF1E242D.toInt()),
        AMBER("Ambre", 0xFFFFB020.toInt(), 0xFFF29A0E.toInt()),
        CYAN("Cyan", 0xFF3DD6F5.toInt(), 0xFF1BA8CC.toInt()),
        RED("Rouge", 0xFFFF4D5E.toInt(), 0xFFE5394F.toInt());

        companion object {
            fun restore(prefs: SharedPreferences) = entries.firstOrNull { it.name == prefs.getString(ACCENT, null) } ?: NEUTRAL
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
    private val accentChoice by lazy { Accent.restore(prefs) }
    private val accent by lazy { if (night) accentChoice.night else accentChoice.day }
    private val onAccent by lazy { if (Color.luminance(accent) > .3f) 0xFF0B0D12.toInt() else Color.WHITE }
    private val neutral get() = accentChoice == Accent.NEUTRAL
    private val night get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private lateinit var greeting: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weatherIcon: WeatherIcon
    private lateinit var weatherText: TextView
    private lateinit var weatherDetail: TextView
    private lateinit var prayerText: TextView
    private lateinit var prayerDetail: TextView
    private lateinit var mediaArt: ImageView
    private lateinit var mediaInitial: TextView
    private lateinit var mediaApp: TextView
    private lateinit var mediaBars: EqualizerBars
    private lateinit var mediaTitle: TextView
    private lateinit var mediaSubtitle: TextView
    private lateinit var mediaControls: LinearLayout
    private lateinit var mediaAction: TextView
    private lateinit var playButton: ImageButton
    private lateinit var mediaWash: GradientDrawable
    private var mediaColor = Color.TRANSPARENT
    private var washFade: ValueAnimator? = null
    private var mediaSource: String? = null
    private lateinit var gauge: SpeedGauge
    private lateinit var speedStatus: TextView
    private lateinit var favoritesGrid: LinearLayout
    private lateinit var panelHost: FrameLayout
    private val dockItems = mutableMapOf<Page, View>()
    private val placeChips = mutableMapOf<Place, View>()
    private val iconColors = HashMap<ComponentName, Int?>()

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { closePanel() }
        }
    }

    // Before Android 13, Back reaches the activity here; a home screen never closes itself.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        closePanel()
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
            greeting.text = when (now.hour) {
                in 5..11 -> "Bonjour"
                in 12..17 -> "Bon après-midi"
                in 18..21 -> "Bonsoir"
                else -> "Bonne nuit"
            }
            renderPrayer()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    // ——— Building blocks ———

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Int, color: Int, weight: Int = 400, condensed: Boolean = false) = TextView(this).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        typeface = Typeface.create(if (condensed) Typeface.create("sans-serif-condensed", Typeface.NORMAL) else Typeface.DEFAULT,
            weight, false)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    /** A dashboard caption: small, spaced capitals in the accent colour. */
    private fun caps(value: String, color: Int = accent) = label(value, 11, color, 700).apply {
        isAllCaps = true
        letterSpacing = .14f
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun wrap() = LinearLayout.LayoutParams(-2, -2)

    /** A dashboard panel: near opaque, square-ish corners, a hairline edge. */
    private fun glass(radius: Int = 16): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(getColor(R.color.home_card))
        setStroke(dp(1), getColor(R.color.home_card_border))
    }

    private fun pressable(base: Drawable?, radius: Int = 16) = RippleDrawable(
        ColorStateList.valueOf(getColor(R.color.home_ripple)), base,
        GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(Color.WHITE) })

    private fun round(icon: Int, description: String, primary: Boolean = false, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(if (primary) onAccent else text)
        // Rounded squares like hardware keys, not the radio's round buttons.
        background = pressable(GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (primary) accent else getColor(R.color.home_chip))
            if (!primary) setStroke(dp(1), getColor(R.color.home_card_border))
        }, 14)
        if (primary) elevation = dp(6).toFloat()
        contentDescription = description
        setOnClickListener { action() }
        RadioMotion.pressScale(this, .9f)
    }

    private fun roundedClip(view: View, radius: Int) {
        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(target: View, outline: Outline) =
                outline.setRoundRect(0, 0, target.width, target.height, dp(radius).toFloat())
        }
    }

    // ——— Screen ———

    private fun screen(): View {
        val backdrop = CockpitBackdrop(getColor(R.color.home_background_top), getColor(R.color.home_background_bottom),
            accent, getColor(R.color.home_grid), if (night) 0x26 else 0x1C)
        val content = column().apply {
            addView(topBar(), LinearLayout.LayoutParams(-1, dp(62)))
            addView(row().apply {
                gravity = Gravity.FILL
                addView(mediaCard(), LinearLayout.LayoutParams(0, -1, 1.4f))
                addView(column().apply {
                    addView(speedCard(), LinearLayout.LayoutParams(-1, 0, 1.05f))
                    addView(mapsCard(), LinearLayout.LayoutParams(-1, 0, .95f).apply { topMargin = dp(10) })
                }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(10); marginEnd = dp(10) })
                addView(favoritesCard(), LinearLayout.LayoutParams(0, -1, 1.05f))
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6); bottomMargin = dp(10) })
            addView(dock(), LinearLayout.LayoutParams(-1, dp(58)))
        }
        panelHost = FrameLayout(this).apply { visibility = View.GONE }
        return FrameLayout(this).apply {
            background = backdrop
            addView(content, FrameLayout.LayoutParams(-1, -1))
            addView(panelHost, FrameLayout.LayoutParams(-1, -1))
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                content.setPadding(bars.left + dp(14), bars.top + dp(8), bars.right + dp(14), bars.bottom + dp(10))
                panelHost.setPadding(bars.left + dp(14), bars.top + dp(8), bars.right + dp(14), bars.bottom + dp(78))
                insets
            }
        }
    }

    private fun topBar() = row().apply {
        addView(column().apply {
            greeting = caps("")
            addView(greeting)
            addView(row().apply {
                gravity = Gravity.BOTTOM
                clock = label("--:--", 44, text, 300, condensed = true)
                addView(clock)
                date = label("", 14, muted, 500)
                addView(date, wrap().apply { marginStart = dp(12); bottomMargin = dp(6) })
            }, wrap().apply { topMargin = dp(2) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        fun chip(content: LinearLayout.() -> Unit) = row().apply {
            background = glass(14)
            setPadding(dp(14), 0, dp(16), 0)
            content()
        }
        addView(chip {
            weatherIcon = WeatherIcon(context, text)
            addView(weatherIcon, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(column().apply {
                weatherText = label("—", 17, text, 700)
                weatherDetail = label("Météo", 11, muted)
                addView(weatherText, wrap())
                addView(weatherDetail, wrap().apply { topMargin = dp(2) })
            }, wrap().apply { marginStart = dp(10) })
            contentDescription = "Météo"
        }, LinearLayout.LayoutParams(-2, dp(50)).apply { marginEnd = dp(8) })
        addView(chip {
            addView(column().apply {
                prayerText = label("Salat", 16, text, 700)
                prayerDetail = caps("")
                addView(prayerText, wrap())
                addView(prayerDetail, wrap().apply { topMargin = dp(2) })
            })
        }, LinearLayout.LayoutParams(-2, dp(50)))
    }

    private fun mediaCard(): View {
        mediaWash = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)).apply {
            cornerRadius = dp(16).toFloat()
        }
        return column().apply {
            background = pressable(LayerDrawable(arrayOf(glass(), mediaWash)))
            setPadding(dp(16), dp(16), dp(16), dp(14))
            setOnClickListener { openMediaSource() }
            addView(row().apply {
                gravity = Gravity.TOP
                val art = FrameLayout(context).apply {
                    roundedClip(this, 12)
                    background = GradientDrawable().apply { setColor(getColor(R.color.home_chip)) }
                    elevation = dp(8).toFloat()
                    mediaInitial = label("♪", 38, text, 700).apply { gravity = Gravity.CENTER }
                    addView(mediaInitial, FrameLayout.LayoutParams(-1, -1))
                    mediaArt = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                    addView(mediaArt, FrameLayout.LayoutParams(-1, -1))
                }
                addView(art, LinearLayout.LayoutParams(dp(112), dp(112)))
                addView(column().apply {
                    addView(row().apply {
                        mediaApp = caps("Média")
                        addView(mediaApp, LinearLayout.LayoutParams(0, -2, 1f))
                        mediaBars = EqualizerBars(context, accent)
                        addView(mediaBars, LinearLayout.LayoutParams(dp(16), dp(12)))
                    })
                    mediaTitle = label("Rien en lecture", 23, text, 700, condensed = true).apply { maxLines = 2 }
                    mediaSubtitle = label("", 13, muted).apply { maxLines = 2 }
                    addView(mediaTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                    addView(mediaSubtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14); topMargin = dp(4) })
            })
            addView(View(context), LinearLayout.LayoutParams(-1, 0, 1f))
            mediaControls = row().apply {
                gravity = Gravity.CENTER
                addView(round(R.drawable.ic_home_previous, "Précédent") { media.command(PlaybackState.ACTION_SKIP_TO_PREVIOUS) },
                    LinearLayout.LayoutParams(dp(50), dp(50)))
                playButton = round(R.drawable.ic_home_play, "Lecture", primary = true) {
                    media.command(PlaybackState.ACTION_PLAY_PAUSE).takeIf { it } ?: media.command(PlaybackState.ACTION_PLAY)
                }
                addView(playButton, LinearLayout.LayoutParams(dp(66), dp(66)).apply { marginStart = dp(20); marginEnd = dp(20) })
                addView(round(R.drawable.ic_home_next, "Suivant") { media.command(PlaybackState.ACTION_SKIP_TO_NEXT) },
                    LinearLayout.LayoutParams(dp(50), dp(50)))
            }
            addView(mediaControls, LinearLayout.LayoutParams(-1, -2))
            mediaAction = label("", 15, onAccent, 700).apply {
                gravity = Gravity.CENTER
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(accent)
                }, 14)
                visibility = View.GONE
            }
            addView(mediaAction, LinearLayout.LayoutParams(-1, dp(52)))
        }
    }

    /** The media card takes the colour of what plays, fading from the old one. */
    private fun washWith(color: Int) {
        if (color == mediaColor) return
        washFade?.cancel()
        val alpha = if (night) 0x4D else 0x2E
        fun paint(value: Int) {
            mediaWash.colors = intArrayOf(Color.argb(if (Color.alpha(value) == 0) 0 else alpha,
                Color.red(value), Color.green(value), Color.blue(value)), Color.TRANSPARENT)
        }
        val start = if (mediaColor == Color.TRANSPARENT) Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)) else mediaColor
        washFade = ValueAnimator.ofObject(ArgbEvaluator(), start, color).apply {
            duration = 520
            addUpdateListener { paint(it.animatedValue as Int) }
            start()
        }
        mediaColor = color
    }

    private fun speedCard() = FrameLayout(this).apply {
        background = pressable(glass())
        gauge = SpeedGauge(context, getColor(R.color.home_gauge_track), shade(accent, Color.BLACK, .25f),
            shade(accent, Color.WHITE, .3f), text, muted)
        addView(gauge, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(8), dp(8), dp(8), dp(18)) })
        speedStatus = label("", 10, muted, 500).apply { gravity = Gravity.CENTER }
        addView(speedStatus, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(10) })
        setOnClickListener {
            if (!gps.permitted()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
    }

    /** « Naviguer » opens Google Maps full screen; Maison and Travail start its guidance straight away. */
    private fun mapsCard() = column().apply {
        // Neutral: a panel like the others. A colour: deep accent at night with white text, the bright accent by day.
        val ink = if (neutral) text else if (night) Color.WHITE else onAccent
        fun inked(alpha: Int) = Color.argb(alpha, Color.red(ink), Color.green(ink), Color.blue(ink))
        background = if (neutral) glass() else GradientDrawable(GradientDrawable.Orientation.BL_TR,
            if (night) intArrayOf(shade(accent, Color.BLACK, .45f), shade(accent, Color.BLACK, .1f))
                else intArrayOf(shade(accent, Color.BLACK, .08f), shade(accent, Color.WHITE, .15f))).apply {
            cornerRadius = dp(16).toFloat()
        }
        elevation = dp(4).toFloat()
        setPadding(dp(8), dp(6), dp(8), dp(8))
        addView(row().apply {
            background = pressable(null, 12)
            setPadding(dp(8), 0, dp(4), 0)
            addView(FrameLayout(context).apply {
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(inked(0x2E)) }
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_home_navigation)
                    imageTintList = ColorStateList.valueOf(ink)
                    rotation = 45f
                }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(column().apply {
                addView(label("Naviguer", 19, ink, 700, condensed = true))
                addView(label("Google Maps", 12, inked(0xCC), 500), wrap().apply { topMargin = dp(2) })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_home_chevron)
                imageTintList = ColorStateList.valueOf(ink)
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            contentDescription = "Ouvrir Google Maps"
            setOnClickListener {
                if (!links.openMaps()) Toast.makeText(context, "Google Maps n’est pas installé.", Toast.LENGTH_LONG).show()
            }
            RadioMotion.pressScale(this, .97f)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(row().apply {
            Place.entries.forEachIndexed { index, place ->
                val chip = row().apply {
                    gravity = Gravity.CENTER
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(inked(0x1F))
                    }, 12)
                    addView(ImageView(context).apply {
                        setImageResource(place.icon)
                        imageTintList = ColorStateList.valueOf(ink)
                    }, LinearLayout.LayoutParams(dp(18), dp(18)))
                    addView(label(place.label, 14, ink, 700), wrap().apply { marginStart = dp(8) })
                    setOnClickListener { goTo(place) }
                    setOnLongClickListener { editPlace(place); true }
                    RadioMotion.pressScale(this, .94f)
                }
                placeChips[place] = chip
                addView(chip, LinearLayout.LayoutParams(0, -1, 1f).apply { if (index == 0) marginEnd = dp(8) })
            }
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(4) })
        renderPlaces()
    }

    private fun destination(place: Place) = prefs.getString(place.key, null)?.takeIf { it.isNotBlank() }

    /** A place without an address is shown faded and opens its editor. */
    private fun renderPlaces() {
        placeChips.forEach { (place, chip) ->
            val target = destination(place)
            chip.alpha = if (target != null) 1f else .55f
            chip.contentDescription = target?.let { "Aller à ${place.label}" } ?: "Choisir l’adresse ${place.label}"
        }
    }

    private fun goTo(place: Place) {
        val target = destination(place) ?: return editPlace(place)
        if (!links.navigate(target)) Toast.makeText(this, "Google Maps n’est pas installé.", Toast.LENGTH_LONG).show()
    }

    private fun describe(target: String?) = when {
        target == null -> "Non définie"
        COORDINATES.matches(target) -> "Position enregistrée ($target)"
        else -> target
    }

    /** Type an address, or save where the car is now. An empty address removes the place. */
    private fun editPlace(place: Place, after: () -> Unit = {}) {
        val field = EditText(this).apply {
            setText(destination(place).orEmpty())
            hint = "Adresse, quartier ou lieu"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
            setSingleLine()
            selectAll()
        }
        fun save(value: String?) {
            prefs.edit().putString(place.key, value?.trim()?.takeIf { it.isNotEmpty() }).apply()
            renderPlaces()
            after()
        }
        AlertDialog.Builder(this)
            .setTitle(place.label)
            .setMessage("Google Maps lancera le guidage vers cette adresse.")
            .setView(FrameLayout(this).apply {
                setPadding(dp(20), dp(4), dp(20), 0)
                addView(field)
            })
            .setPositiveButton("Enregistrer") { _, _ -> save(field.text.toString()) }
            .setNegativeButton("Annuler", null)
            .setNeutralButton("Ma position") { _, _ ->
                val here = currentPosition()
                if (here == null) {
                    Toast.makeText(this, if (gps.permitted()) "Position pas encore disponible : réessaie dans un instant."
                        else "Autorise la localisation pour enregistrer ta position.", Toast.LENGTH_LONG).show()
                    if (!gps.permitted()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION), 1)
                } else {
                    save(here)
                    Toast.makeText(this, "${place.label} : position enregistrée", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /** The freshest last known fix of the device, as « latitude,longitude ». */
    private fun currentPosition(): String? {
        if (!gps.permitted()) return null
        val manager = getSystemService(LocationManager::class.java) ?: return null
        val fix = runCatching {
            manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull() ?: return null
        return String.format(Locale.US, "%.6f,%.6f", fix.latitude, fix.longitude)
    }

    private fun favoritesCard() = column().apply {
        background = glass()
        setPadding(dp(10), dp(12), dp(10), dp(10))
        addView(row().apply {
            addView(caps("Favoris"), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
            addView(label("Modifier", 12, muted, 600).apply {
                setPadding(dp(8), dp(4), dp(6), dp(4))
                setOnClickListener { showApps(editing = true) }
            })
        })
        favoritesGrid = column()
        addView(favoritesGrid, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
    }

    private fun dock() = row().apply {
        background = glass(18)
        elevation = dp(6).toFloat()
        setPadding(dp(6), dp(6), dp(6), dp(6))
        fun item(page: Page?, icon: Int, title: String, action: () -> Unit) = row().apply {
            gravity = Gravity.CENTER
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(text)
                tag = "icon"
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(label(title, 15, text, 600).apply { tag = "label" }, wrap().apply { marginStart = dp(10) })
            contentDescription = title
            setOnClickListener { action() }
            page?.let { dockItems[it] = this }
        }
        addView(item(Page.HOME, R.drawable.ic_home_home, "Accueil") { closePanel() }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(item(null, R.drawable.ic_home_phone, "Téléphone") {
            if (!links.openPhone()) Toast.makeText(context, "Aucune application de téléphone trouvée.", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(item(Page.APPS, R.drawable.ic_home_apps, "Applis") { showApps(editing = false) }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(item(Page.SETTINGS, R.drawable.ic_home_settings, "Réglages") { showSettings() }, LinearLayout.LayoutParams(0, -1, 1f))
        post { markDock(Page.HOME) }
    }

    /** The current page's dock item sits in a filled pill. */
    private fun markDock(current: Page) {
        val activeFill = accent
        val onActive = onAccent
        dockItems.forEach { (page, view) ->
            val active = page == current
            view.background = pressable(GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (active) activeFill else Color.TRANSPARENT)
            }, 12)
            val tint = if (active) onActive else text
            (view.findViewWithTag<ImageView>("icon"))?.imageTintList = ColorStateList.valueOf(tint)
            (view.findViewWithTag<TextView>("label"))?.setTextColor(tint)
        }
    }

    // ——— Live content ———

    private fun renderWeather() {
        val weather = info.weather.value
        weatherIcon.bind(weather)
        weatherText.text = weather?.let { "${it.degrees}°" } ?: "—"
        weatherDetail.text = weather?.condition?.label ?: info.city.value.label
    }

    private fun renderPrayer() {
        if (!::prayerText.isInitialized) return
        val next = runCatching { info.prayerStatus(info.city.value).next }.getOrNull()
        prayerText.text = next?.let { "${it.prayer.label}  ${it.dateTime.toLocalTime()}" } ?: "Salat"
        prayerDetail.text = next?.let {
            // Habous times are Moroccan wall-clock times, like the head unit's own clock.
            val minutes = Duration.between(LocalDateTime.now(), it.dateTime).toMinutes()
            when {
                minutes <= 0 -> "maintenant"
                minutes < 60 -> "dans $minutes min"
                else -> "dans ${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"
            }
        }.orEmpty()
    }

    private fun renderSpeed() {
        if (!::gauge.isInitialized) return
        if (!gps.permitted()) {
            gauge.setSpeed(null)
            speedStatus.text = "Toucher pour activer le GPS"
            return
        }
        val state = gps.snapshot()
        gauge.setSpeed(state.speed.kmh)
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
        washWith(if (neutral) Color.TRANSPARENT else art?.let(RadioMotion::logoColor) ?: if (hasMedia) accent else Color.TRANSPARENT)
        mediaBars.visibility = if (hasMedia && state.playing) View.VISIBLE else View.INVISIBLE
        mediaBars.playing = hasMedia && state.playing
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

    private fun favorites(): List<Launchable> {
        val apps = links.apps()
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

    /** The dominant colour of an app's icon, measured once, to tint its tile. */
    private fun iconColor(app: Launchable, icon: Drawable?): Int? = iconColors.getOrPut(app.component) {
        icon ?: return@getOrPut null
        runCatching {
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, 48, 48)
            icon.draw(Canvas(bitmap))
            RadioMotion.logoColor(bitmap).also { bitmap.recycle() }
        }.getOrNull()
    }

    private fun appTile(app: Launchable, compact: Boolean, onLong: (View) -> Unit) = column().apply {
        gravity = Gravity.CENTER
        val icon = links.icon(app)
        val tint = iconColor(app, icon) ?: accent
        background = pressable(GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            if (neutral) {
                setColor(getColor(R.color.home_chip))
                setStroke(dp(1), getColor(R.color.home_card_border))
            } else setColor(Color.argb(if (night) 0x2E else 0x26, Color.red(tint), Color.green(tint), Color.blue(tint)))
        }, 12)
        setPadding(dp(4), dp(6), dp(4), dp(6))
        addView(ImageView(context).apply { setImageDrawable(icon) },
            LinearLayout.LayoutParams(dp(if (compact) 38 else 48), dp(if (compact) 38 else 48)))
        addView(label(app.label, 11, text, 500).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        contentDescription = app.label
        setOnClickListener { links.open(app) }
        setOnLongClickListener { onLong(it); true }
        RadioMotion.pressScale(this, .94f)
    }

    private fun addTile() = column().apply {
        gravity = Gravity.CENTER
        background = pressable(GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), getColor(R.color.home_card_border), dp(6).toFloat(), dp(4).toFloat())
            setColor(getColor(R.color.home_chip))
        }, 12)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_home_add)
            imageTintList = ColorStateList.valueOf(muted)
        }, LinearLayout.LayoutParams(dp(24), dp(24)))
        contentDescription = "Ajouter un favori"
        setOnClickListener { showApps(editing = true) }
    }

    private fun renderFavorites() {
        if (!::favoritesGrid.isInitialized) return
        favoritesGrid.removeAllViews()
        val list = favorites()
        // Always three rows of two: the favourites, then « + » tiles for the free places.
        val slots = list.map<Launchable, Launchable?> { it } + List(6 - list.size) { null }
        slots.chunked(2).forEachIndexed { index, pair ->
            favoritesGrid.addView(row().apply {
                gravity = Gravity.FILL
                pair.forEachIndexed { column, app ->
                    val tile = if (app == null) addTile() else appTile(app, compact = true) { anchor ->
                        PopupMenu(this@HomeActivity, anchor).apply {
                            menu.add("Retirer des favoris")
                            setOnMenuItemClickListener { toggleFavorite(app); true }
                        }.show()
                    }
                    addView(tile, LinearLayout.LayoutParams(0, -1, 1f).apply { if (column == 0) marginEnd = dp(8) })
                }
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply { if (index > 0) topMargin = dp(8) })
        }
    }

    // ——— Pages over the home screen ———

    private fun sheet(): Drawable = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(getColor(R.color.home_background_bottom))
        setStroke(dp(1), getColor(R.color.home_card_border))
    }

    private fun openPanel(page: Page, content: View) {
        panelHost.removeAllViews()
        panelHost.addView(content, FrameLayout.LayoutParams(-1, -1))
        panelHost.visibility = View.VISIBLE
        panelHost.alpha = 0f
        panelHost.translationY = dp(16).toFloat()
        panelHost.animate().alpha(1f).translationY(0f).setDuration(180).start()
        markDock(page)
    }

    private fun closePanel() {
        if (!::panelHost.isInitialized) return
        markDock(Page.HOME)
        if (panelHost.visibility != View.VISIBLE) return
        panelHost.animate().alpha(0f).translationY(dp(12).toFloat()).setDuration(150).withEndAction {
            panelHost.visibility = View.GONE
            panelHost.removeAllViews()
        }.start()
    }

    private fun panelHeader(title: String, subtitle: String?) = row().apply {
        addView(column().apply {
            addView(label(title, 22, text, 700, condensed = true))
            subtitle?.let { addView(label(it, 12, muted), wrap().apply { topMargin = dp(4) }) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(round(R.drawable.ic_home_close, "Fermer") { closePanel() }, LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    /** Every app of the head unit; in edit mode a tap adds or removes a favourite instead of opening it. */
    private fun showApps(editing: Boolean) {
        val apps = links.apps()
        openPanel(Page.APPS, column().apply {
            background = sheet()
            setPadding(dp(16), dp(12), dp(16), dp(10))
            addView(panelHeader(if (editing) "Choisir les favoris" else "Applications",
                if (editing) "Touche une appli pour l’ajouter ou la retirer · 6 au maximum"
                else "Garde le doigt sur une appli pour l’ajouter aux favoris"))
            addView(GridView(context).apply {
                numColumns = if (resources.configuration.screenWidthDp >= 700) 6 else 4
                verticalSpacing = dp(8)
                horizontalSpacing = dp(8)
                selector = GradientDrawable()
                adapter = object : BaseAdapter() {
                    override fun getCount() = apps.size
                    override fun getItem(position: Int) = apps[position]
                    override fun getItemId(position: Int) = position.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val app = apps[position]
                        val chosen = favorites().any { it.component == app.component }
                        return appTile(app, compact = false) { toggleFavorite(app); notifyDataSetChanged() }.apply {
                            if (editing) {
                                setOnClickListener { toggleFavorite(app); notifyDataSetChanged() }
                                alpha = if (chosen) 1f else .5f
                            }
                            layoutParams = ViewGroup.LayoutParams(-1, dp(98))
                        }
                    }
                }
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
        })
    }

    private fun showSettings() {
        val body = column()
        openPanel(Page.SETTINGS, column().apply {
            background = sheet()
            setPadding(dp(18), dp(12), dp(18), dp(10))
            addView(panelHeader("Réglages", "Incar Drive"))
            addView(ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(body)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        body.apply {
            addView(caps("Thème"), wrap().apply { topMargin = dp(10) })
            addView(choices(Appearance.entries, Appearance.restore(prefs), { it.label }, { null }) {
                prefs.edit().putString(APPEARANCE, it.name).apply()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(caps("Couleur du tableau de bord"), wrap().apply { topMargin = dp(14) })
            addView(choices(Accent.entries, accentChoice, { it.label }, { if (night) it.night else it.day }) {
                prefs.edit().putString(ACCENT, it.name).apply()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(caps("Ville (météo et salat)"), wrap().apply { topMargin = dp(14) })
            addView(label(info.city.value.label + "  ›", 17, text, 700).apply {
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
            addView(caps("Destinations"), wrap().apply { topMargin = dp(6) })
            Place.entries.forEach { place ->
                addView(row().apply {
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(getColor(R.color.home_chip))
                        setStroke(dp(1), getColor(R.color.home_card_border))
                    }, 12)
                    setPadding(dp(14), 0, dp(14), 0)
                    addView(ImageView(context).apply {
                        setImageResource(place.icon)
                        imageTintList = ColorStateList.valueOf(text)
                    }, LinearLayout.LayoutParams(dp(20), dp(20)))
                    addView(label(place.label, 15, text, 700), wrap().apply { marginStart = dp(12) })
                    addView(label(describe(destination(place)) + "  ›", 14, muted, 500).apply { gravity = Gravity.END },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
                    setOnClickListener { editPlace(place) { showSettings() } }
                }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
            }
            addView(label("Réglages Android  ›", 15, text, 700).apply {
                gravity = Gravity.CENTER
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(getColor(R.color.home_chip))
                }, 14)
                setOnClickListener { startSafely(Intent(Settings.ACTION_SETTINGS)) }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(16) })
        }
    }

    /** A row of segmented choices; picking one saves it and redraws the screen in it. */
    private fun <T> choices(all: List<T>, current: T, name: (T) -> String, swatch: (T) -> Int?, pick: (T) -> Unit) = row().apply {
        all.forEach { choice ->
            val selected = choice == current
            addView(row().apply {
                gravity = Gravity.CENTER
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (selected) accent else getColor(R.color.home_chip))
                    if (!selected) setStroke(dp(1), getColor(R.color.home_card_border))
                }, 12)
                swatch(choice)?.let { color ->
                    addView(View(context).apply {
                        background = GradientDrawable().apply {
                            cornerRadius = dp(5).toFloat()
                            setColor(color)
                            if (selected) setStroke(dp(2), onAccent)
                        }
                    }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) })
                }
                addView(label(name(choice), 15, if (selected) onAccent else text, 700))
                setOnClickListener { pick(choice); recreate() }
            }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
        }
    }

    private fun shade(color: Int, toward: Int, amount: Float) = ArgbEvaluator().evaluate(amount, color, toward) as Int

    private companion object {
        const val PREFS = "incar_drive_home"
        const val APPEARANCE = "appearance"
        const val ACCENT = "accent"
        val COORDINATES = Regex("""-?\d+(\.\d+)?,-?\d+(\.\d+)?""")
        const val CITY = "city"
        const val FAVORITES = "favorites"
        const val RADIO_PACKAGE = "com.autodrive.radiomaroc"
        const val RADIO_HOME_VISIBLE = "com.autodrive.radiomaroc.HOME_VISIBLE"
        // First favourites when the listener has not chosen any: the radio, video and navigation apps of the car.
        val DEFAULT_FAVORITES = listOf(RADIO_PACKAGE, "com.google.android.youtube", "com.waze",
            "com.spotify.music", "com.google.android.apps.youtube.music", "com.android.chrome")
    }
}
