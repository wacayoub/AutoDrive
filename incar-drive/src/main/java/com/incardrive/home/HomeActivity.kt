package com.incardrive.home

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import android.media.AudioManager
import android.media.ToneGenerator
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
import com.autodrive.radiomaroc.RadioLocalInfo
import com.autodrive.radiomaroc.RadioMotion
import com.autodrive.radiomaroc.SystemNight
import com.autodrive.radiomaroc.WeatherIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    internal enum class Page { HOME, APPS, SETTINGS, QUICK, WIDGETS, TRIP, DIALER }

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
        RED("Rouge", 0xFFFF4D5E.toInt(), 0xFFE5394F.toInt()),
        BLUE("Bleu", 0xFF5B9BFF.toInt(), 0xFF2563EB.toInt()),
        GREEN("Vert", 0xFF3DDC97.toInt(), 0xFF0E9F6E.toInt()),
        VIOLET("Violet", 0xFFB197FC.toInt(), 0xFF7C3AED.toInt()),
        PINK("Rose", 0xFFFF7EB6.toInt(), 0xFFDB2777.toInt()),
        GOLD("Or", 0xFFE8C872.toInt(), 0xFFB8860B.toInt());

        companion object {
            fun restore(prefs: SharedPreferences) = entries.firstOrNull { it.name == prefs.getString(ACCENT, null) } ?: NEUTRAL
        }
    }

    internal lateinit var prefs: SharedPreferences
    internal lateinit var links: AppLinks
    private lateinit var media: MediaSessions
    private lateinit var gps: GpsMonitor
    private lateinit var info: LocalInfoFeed
    private var scope: CoroutineScope? = null
    private val handler = Handler(Looper.getMainLooper())

    internal val text by lazy { getColor(R.color.home_text) }
    internal val muted by lazy { getColor(R.color.home_muted) }
    private val accentChoice by lazy { Accent.restore(prefs) }
    internal val accent by lazy { if (night) accentChoice.night else accentChoice.day }
    internal val onAccent by lazy { if (Color.luminance(accent) > .3f) 0xFF0B0D12.toInt() else Color.WHITE }
    private val neutral get() = accentChoice == Accent.NEUTRAL
    // Each part of the screen keeps its own colour, whatever the dashboard colour: navigation blue, phone green,
    // weather amber, prayer teal, apps violet. Brighter by night, deeper by day.
    internal val navColor by lazy { if (night) 0xFF5B9BFF.toInt() else 0xFF2563EB.toInt() }
    internal val phoneColor by lazy { if (night) 0xFF3DDC97.toInt() else 0xFF0E9F6E.toInt() }
    internal val weatherColor by lazy { if (night) 0xFFFFB020.toInt() else 0xFFD97706.toInt() }
    internal val prayerColor by lazy { if (night) 0xFF2DD4BF.toInt() else 0xFF0D9488.toInt() }
    internal val appsColor by lazy { if (night) 0xFFB197FC.toInt() else 0xFF7C3AED.toInt() }

    /** [color] at [alpha], for the light washes behind coloured icons and selected items. */
    internal fun tint(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    internal val night get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

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
    private lateinit var quick: QuickSettings
    internal lateinit var dialer: Dialer
    private lateinit var widgets: WidgetBoard
    private lateinit var phone: PhoneLink
    private lateinit var trip: TripMeter
    private lateinit var pager: SwipePager
    private lateinit var dots: List<View>
    private lateinit var altitudeText: TextView
    private var shownPage = Page.HOME
    private lateinit var callBanner: FrameLayout
    private lateinit var screenRoot: FrameLayout
    private var shownSky: Sky? = null
    private var alerted = false
    private val roadLimits = RoadLimits()
    private var roadLimit: Int? = null
    private var fetchingRoads = false
    private lateinit var limitSign: LimitSign
    private var lastTripSave = 0L
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
        trip = TripMeter(Trip.decode(prefs.getString(TRIP, null)))
        // The head unit starts with the car: a trip from before this start is over; the new one starts when the car
        // first moves, so its time counts the drive and not the minutes parked.
        val bootAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        if (trip.trip.startedAt != 0L && trip.trip.lastMoveAt < bootAt) {
            trip.reset(0)
            prefs.edit().putString(TRIP, trip.trip.encode()).apply()
        }
        gps.onFix = { fix, kmh ->
            info.usePosition(fix.latitude, fix.longitude)
            if (roadMode()) followRoad(fix, kmh)
            trip.accept(System.currentTimeMillis(), fix.latitude, fix.longitude, fix.accuracy.takeIf { fix.hasAccuracy() }, kmh)
            if (System.currentTimeMillis() - lastTripSave > 30_000) saveTrip()
        }
        quick = QuickSettings(this)
        dialer = Dialer(this)
        widgets = WidgetBoard(this)
        phone = PhoneLink(this)
        updatesUi = IncarUpdates(this)
        status = StatusIcons(this)
        clicks = ClickSound(this).apply { enabled = prefs.getBoolean(CLICKS, true) }
        // The chosen equaliser profile stays on as long as the home screen runs.
        if (soundProfile() != SoundProfile.NORMAL && !SoundEffects.apply(soundProfile())) {
            prefs.edit().putString(SOUND_PROFILE, SoundProfile.NORMAL.name).apply()
        }
        info = LocalInfoFeed(RadioCity.restore(prefs.getString(CITY, null)))
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(screen())
        window.insetsController?.setSystemBarsAppearance(
            if (night) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        hideStatusBar()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goBack() }
        }
    }

    /**
     * Android's status bar is hidden so the home screen uses the whole display; Incar Drive shows the time, Wi-Fi,
     * mobile and Bluetooth itself. A swipe down from the top edge still shows Android's bar for a moment.
     */
    private fun hideStatusBar() {
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private lateinit var clicks: ClickSound

    private fun soundProfile() = SoundProfile.entries.firstOrNull { it.name == prefs.getString(SOUND_PROFILE, null) }
        ?: SoundProfile.NORMAL

    // Radio Maroc & Coran's click on every tap, when « Clics de l’interface » is on.
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (::clicks.isInitialized) clicks.onTouch(window.decorView, event)
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        if (::clicks.isInitialized) clicks.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    private lateinit var updatesUi: IncarUpdates
    private lateinit var status: StatusIcons

    // Before Android 13, Back reaches the activity here; a home screen never closes itself.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        goBack()
    }

    // Home pressed while Incar Drive is already in front, or coming back to it: show the first page, like other launchers.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            closePanel()
            if (::pager.isInitialized) pager.show(0, animate = hasWindowFocus())
        }
    }

    /** Back closes the open page, then returns to the first home page. */
    private fun goBack() {
        if (shownPage != Page.HOME) closePanel() else if (::pager.isInitialized) pager.show(0)
    }

    private fun saveTrip() {
        lastTripSave = System.currentTimeMillis()
        prefs.edit().putString(TRIP, trip.trip.encode()).apply()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_HOME_ROLE) {
            renderHomeBadge()
            if (!isDefaultHome()) startSafely(Intent(Settings.ACTION_HOME_SETTINGS))
            return
        }
        if (!widgets.onResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    /** Whether Home opens Incar Drive. */
    private fun isDefaultHome(): Boolean {
        val roles = getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.isRoleHeld(RoleManager.ROLE_HOME)
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY)
        return home?.activityInfo?.packageName == packageName
    }

    /**
     * Android's own question « make Incar Drive the default home app? » (RoleManager), or the default-apps screen
     * when this head unit does not offer it.
     */
    private fun askDefaultHome() {
        val roles = getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        startSafely(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private lateinit var homeBadge: TextView

    /** A button in the top bar until Incar Drive is the default home screen. */
    private fun renderHomeBadge() {
        if (::homeBadge.isInitialized) homeBadge.visibility = if (isDefaultHome()) View.GONE else View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        media.start()
        if (gps.permitted()) gps.start()
        // Weather where the car is, from the last known position until the GPS answers; the chosen city otherwise.
        currentPosition()?.split(',')?.let { (lat, lon) -> info.usePosition(lat.toDouble(), lon.toDouble()) }
        val tasks = MainScope()
        scope = tasks
        tasks.launch { info.city.collect { city -> info.refreshWeather(city) } }
        tasks.launch { info.refreshPrayerCalendars() }
        tasks.launch { info.weather.collect { renderWeather() } }
        tasks.launch { info.prayerCalendars.collect { renderPrayer() } }
        tasks.launch { CallWatch.call.collect { renderCall(it) } }
        tasks.launch { NavWatch.nav.collect { renderNav(it) } }
        updatesUi.follow(tasks)
        widgets.start()
        phone.start()
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
        widgets.stop()
        phone.stop()
        saveTrip()
        super.onStop()
    }

    // Radio Maroc & Coran hides its floating bubble while this home screen, which already shows the radio, is in front.
    override fun onResume() {
        super.onResume()
        tellRadio(visible = true)
        renderHomeBadge()
        status.render()
        scope?.let(updatesUi::onResume)
        // Ask once, on the first start, to become the screen Home opens.
        if (!prefs.getBoolean(ASKED_HOME, false) && !isDefaultHome()) {
            prefs.edit().putBoolean(ASKED_HOME, true).apply()
            askDefaultHome()
        }
        // Back from an Android settings screen: show the new Wi-Fi, Bluetooth and brightness states.
        if (shownPage == Page.QUICK) quick.show()
        phone.render()
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
        if (requestCode == REQUEST_BLUETOOTH) phone.start()
        status.render()
        phone.render()
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
            renderSky()
            status.render()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    // ——— Building blocks ———

    internal fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    internal fun label(value: String, size: Int, color: Int, weight: Int = 400, condensed: Boolean = false) = TextView(this).apply {
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
    internal fun caps(value: String, color: Int = accent) = label(value, 11, color, 700).apply {
        isAllCaps = true
        letterSpacing = .14f
    }

    internal fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    internal fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    internal fun wrap() = LinearLayout.LayoutParams(-2, -2)

    /** A dashboard panel: near opaque, square-ish corners, a hairline edge. */
    internal fun glass(radius: Int = 16): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(getColor(R.color.home_card))
        setStroke(dp(1), getColor(R.color.home_card_border))
    }

    internal fun pressable(base: Drawable?, radius: Int = 16) = RippleDrawable(
        ColorStateList.valueOf(getColor(R.color.home_ripple)), base,
        GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(Color.WHITE) })

    internal fun round(icon: Int, description: String, primary: Boolean = false, action: () -> Unit) = ImageButton(this).apply {
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
        val backdrop = skyBackdrop()
        val content = column().apply {
            addView(topBar(), LinearLayout.LayoutParams(-1, dp(54)))
            pager = SwipePager(context) { page -> markDots(page) }.apply {
                clipChildren = true
                addPage(row().apply {
                    gravity = Gravity.FILL
                    addView(mediaCard(), LinearLayout.LayoutParams(0, -1, 1.4f))
                    addView(column().apply {
                        addView(speedCard(), LinearLayout.LayoutParams(-1, 0, 1.25f))
                        addView(mapsCard(), LinearLayout.LayoutParams(-1, 0, .75f).apply { topMargin = dp(10) })
                    }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(10); marginEnd = dp(10) })
                    addView(favoritesCard(), LinearLayout.LayoutParams(0, -1, 1.05f))
                })
                // Second page: the connected phone and the driver's Android widgets.
                addPage(row().apply {
                    gravity = Gravity.FILL
                    addView(phone.view(), LinearLayout.LayoutParams(0, -1, 1f))
                    addView(widgets.view(), LinearLayout.LayoutParams(0, -1, 2.45f).apply { marginStart = dp(10) })
                })
            }
            addView(pager, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
            addView(row().apply {
                gravity = Gravity.CENTER
                dots = List(2) { index ->
                    View(context).also { dot ->
                        addView(dot, LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginStart = dp(4); marginEnd = dp(4) })
                        dot.setOnClickListener { pager.show(index) }
                    }
                }
                markDots(0)
            }, LinearLayout.LayoutParams(-1, dp(14)))
            addView(dock(), LinearLayout.LayoutParams(-1, dp(44)))
        }
        panelHost = FrameLayout(this).apply { visibility = View.GONE }
        callBanner = FrameLayout(this).apply { visibility = View.GONE }
        return FrameLayout(this).apply {
            screenRoot = this
            background = backdrop
            addView(content, FrameLayout.LayoutParams(-1, -1))
            addView(panelHost, FrameLayout.LayoutParams(-1, -1))
            addView(callBanner, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                content.setPadding(bars.left + dp(14), bars.top + dp(8), bars.right + dp(14), bars.bottom + dp(10))
                panelHost.setPadding(bars.left + dp(14), bars.top + dp(8), bars.right + dp(14), bars.bottom + dp(62))
                callBanner.setPadding(bars.left + dp(14), bars.top + dp(8), bars.right + dp(14), 0)
                insets
            }
        }
    }

    /** Two small marks under the pages; the current one is a longer bar in the accent colour. */
    private fun markDots(page: Int) {
        if (!::dots.isInitialized) return
        dots.forEachIndexed { index, dot ->
            val active = index == page
            dot.background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(if (active) accent else muted)
            }
            dot.alpha = if (active) 1f else .5f
            dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply { width = dp(if (active) 18 else 6) }
        }
    }

    internal fun toggleNight() {
        prefs.edit().putString(APPEARANCE, if (night) Appearance.DAY.name else Appearance.NIGHT.name).apply()
        recreate()
    }

    private fun topBar() = row().apply {
        addView(column().apply {
            greeting = caps("")
            addView(row().apply {
                addView(greeting)
                addView(status.view(), wrap().apply { marginStart = dp(14) })
            })
            addView(row().apply {
                gravity = Gravity.BOTTOM
                clock = label("--:--", 38, text, 300, condensed = true)
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
        homeBadge = label("Définir par défaut", 13, onAccent, 700).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            background = pressable(GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(accent)
            }, 14)
            contentDescription = "Faire d’Incar Drive l’écran d’accueil par défaut"
            setOnClickListener { askDefaultHome() }
            visibility = View.GONE
        }
        addView(homeBadge, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(8) })
        addView(ImageButton(context).apply {
            setImageResource(R.drawable.ic_home_tune)
            imageTintList = ColorStateList.valueOf(text)
            background = pressable(glass(14), 14)
            contentDescription = "Réglages rapides"
            setOnClickListener { quick.show() }
            RadioMotion.pressScale(this, .92f)
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
        addView(chip {
            weatherIcon = WeatherIcon(context, weatherColor)
            addView(weatherIcon, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(column().apply {
                weatherText = label("—", 17, text, 700)
                weatherDetail = label("Météo", 11, muted)
                addView(weatherText, wrap())
                addView(weatherDetail, wrap().apply { topMargin = dp(2) })
            }, wrap().apply { marginStart = dp(10) })
            contentDescription = "Météo"
        }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(8) })
        addView(chip {
            addView(column().apply {
                prayerText = label("Salat", 16, text, 700)
                prayerDetail = caps("", prayerColor)
                addView(prayerText, wrap())
                addView(prayerDetail, wrap().apply { topMargin = dp(2) })
            })
        }, LinearLayout.LayoutParams(-2, dp(44)))
    }

    private fun mediaCard(): View {
        mediaWash = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)).apply {
            cornerRadius = dp(16).toFloat()
        }
        radioCard = RadioCard(this) { action ->
            media.command(action) || (action == PlaybackState.ACTION_PLAY_PAUSE && media.command(PlaybackState.ACTION_PLAY))
        }
        radioBody = radioCard.view().apply { visibility = View.GONE }
        mediaBody = column()
        return FrameLayout(this).apply {
            background = pressable(LayerDrawable(arrayOf(glass(), mediaWash)))
            setPadding(dp(16), dp(16), dp(16), dp(14))
            setOnClickListener { openMediaSource() }
            addView(mediaBody, FrameLayout.LayoutParams(-1, -1))
            addView(radioBody, FrameLayout.LayoutParams(-1, -1))
        }.also { buildMediaBody() }
    }

    private lateinit var radioCard: RadioCard
    private lateinit var radioBody: View
    private lateinit var mediaBody: LinearLayout

    /** The card for any other player: artwork, app, title and the three transport buttons. */
    private fun buildMediaBody() {
        mediaBody.apply {
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
        gauge.limit = activeLimit()
        addView(gauge, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(4), dp(4), dp(4), dp(20)) })
        // Altitude in the upper left corner, the limit as a road sign in the upper right.
        addView(column().apply {
            altitudeText = label("", 11, muted, 600)
            addView(altitudeText)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(12), dp(10), 0, 0) })
        limitSign = LimitSign(context).apply { limit = activeLimit() }
        addView(limitSign,
            FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(10), 0) })
        speedStatus = label("", 11, muted, 600).apply { gravity = Gravity.CENTER }
        addView(speedStatus, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(8) })
        contentDescription = "Vitesse et trajet"
        setOnClickListener {
            if (!gps.permitted()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), 1)
            else showTrip()
        }
    }

    /** « Naviguer » opens Google Maps full screen; Maison and Travail start its guidance straight away. */
    private fun mapsCard() = column().apply {
        // A panel like the others, in navigation blue: the icon, the chevron and the Maison / Travail icons.
        background = glass()
        setPadding(dp(8), dp(6), dp(8), dp(8))
        addView(row().apply {
            background = pressable(null, 12)
            setPadding(dp(8), 0, dp(4), 0)
            addView(FrameLayout(context).apply {
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(tint(navColor, 0x33)) }
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_home_navigation)
                    imageTintList = ColorStateList.valueOf(navColor)
                    rotation = 45f
                }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(column().apply {
                navTitle = label("Naviguer", 19, text, 700, condensed = true)
                navDetail = label("Google Maps", 12, muted, 500)
                addView(navTitle)
                addView(navDetail, wrap().apply { topMargin = dp(2) })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_home_chevron)
                imageTintList = ColorStateList.valueOf(navColor)
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            contentDescription = "Ouvrir Google Maps"
            setOnClickListener {
                // While guiding, back to the guidance; otherwise straight to choosing a destination.
                val opened = if (NavWatch.nav.value != null) packageManager.getLaunchIntentForPackage(AppLinks.MAPS)
                    ?.let { startSafely(it); true } ?: false else links.openMaps()
                if (!opened) Toast.makeText(context, "Google Maps n’est pas installé.", Toast.LENGTH_LONG).show()
            }
            RadioMotion.pressScale(this, .97f)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(row().apply {
            Place.entries.forEachIndexed { index, place ->
                val chip = row().apply {
                    gravity = Gravity.CENTER
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(getColor(R.color.home_chip))
                        setStroke(dp(1), getColor(R.color.home_card_border))
                    }, 12)
                    addView(ImageView(context).apply {
                        setImageResource(place.icon)
                        imageTintList = ColorStateList.valueOf(navColor)
                        tag = "icon"
                    }, LinearLayout.LayoutParams(dp(18), dp(18)))
                    addView(label(place.label, 14, text, 700).apply { tag = "label" },
                        wrap().apply { marginStart = dp(8) })
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

    private lateinit var navTitle: TextView
    private lateinit var navDetail: TextView

    /**
     * While Google Maps guides, the Maps card shows the trip as Maps reports it: time left and arrival in large, then
     * the distance left and the next instruction.
     */
    private fun renderNav(nav: NavInfo?) {
        if (!::navTitle.isInitialized) return
        if (nav == null) {
            navTitle.text = "Naviguer"
            navDetail.text = "Google Maps"
            navDetail.setTextColor(muted)
            return
        }
        navTitle.text = listOfNotNull(nav.remaining, nav.arrival?.let { "arrivée $it" }).joinToString(" · ")
        navDetail.text = listOfNotNull(nav.distance, nav.instruction).joinToString(" · ").ifEmpty { "Guidage en cours" }
        navDetail.setTextColor(navColor)
    }

    private fun destination(place: Place) = prefs.getString(place.key, null)?.takeIf { it.isNotBlank() }

    /** Both places always work: Incar Drive's own address if one was set (long press), else Google Maps' one. */
    private fun renderPlaces() {
        placeChips.forEach { (place, chip) -> chip.contentDescription = "Aller à ${place.label}" }
    }

    /**
     * Guidance to the address set in Incar Drive, or else to the place saved as Home or Work in the driver's Google
     * Maps account, which Maps finds by its label.
     */
    private fun goTo(place: Place) {
        val target = destination(place) ?: mapsLabel(place)
        if (!links.navigate(target)) Toast.makeText(this, "Google Maps n’est pas installé.", Toast.LENGTH_LONG).show()
    }

    private fun mapsLabel(place: Place) = when {
        Locale.getDefault().language == "fr" -> if (place == Place.HOME) "Domicile" else "Travail"
        else -> if (place == Place.HOME) "Home" else "Work"
    }

    private fun describe(target: String?) = when {
        target == null -> "Celle de Google Maps"
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
            .setMessage("Laisser vide pour utiliser l’adresse ${place.label} enregistrée dans Google Maps.")
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
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
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
        setPadding(dp(5), dp(4), dp(5), dp(4))
        fun item(page: Page?, icon: Int, title: String, color: Int, action: () -> Unit) = row().apply {
            gravity = Gravity.CENTER
            tag = color
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(color)
                tag = "icon"
            }, LinearLayout.LayoutParams(dp(19), dp(19)))
            addView(label(title, 14, text, 600).apply { tag = "label" }, wrap().apply { marginStart = dp(8) })
            contentDescription = title
            setOnClickListener { action() }
            page?.let { dockItems[it] = this }
        }
        addView(item(Page.HOME, R.drawable.ic_home_home, "Accueil", accent) { closePanel(); pager.show(0) }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(item(Page.DIALER, R.drawable.ic_home_phone, "Téléphone", phoneColor) { dialer.show() }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(item(Page.APPS, R.drawable.ic_home_apps, "Applis", appsColor) { showApps(editing = false) }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(item(Page.SETTINGS, R.drawable.ic_home_settings, "Réglages", muted) { showSettings() }, LinearLayout.LayoutParams(0, -1, 1f))
        post { markDock(Page.HOME) }
    }

    /**
     * Each dock item keeps its own colour on its icon. The current page sits in a see-through pill of that colour,
     * with its name in the colour too.
     */
    private fun markDock(current: Page) {
        dockItems.forEach { (page, view) ->
            val active = page == current
            val color = view.tag as? Int ?: accent
            view.background = pressable(GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (active) tint(color, if (night) 0x33 else 0x24) else Color.TRANSPARENT)
                if (active) setStroke(dp(1), tint(color, 0x73))
            }, 12)
            (view.findViewWithTag<ImageView>("icon"))?.imageTintList = ColorStateList.valueOf(color)
            (view.findViewWithTag<TextView>("label"))?.setTextColor(if (active) color else text)
        }
    }

    // ——— Live content ———

    /** The background for this season and the weather outside, behind the cards. */
    private fun currentSky(): Sky {
        val city = info.city.value
        // Debug builds only: a fixed time to preview the sky (« debug_sky_time », an ISO instant in the preferences).
        val debugTime = if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            prefs.getString("debug_sky_time", null)?.let { runCatching { Instant.parse(it) }.getOrNull() } else null
        val sun = SunPosition.at(debugTime ?: Instant.now(), city.latitude, city.longitude)
        return SkyPalette.of(info.weather.value?.condition, LocalDate.now().month, night, sun)
    }

    private fun skyBackdrop(): CockpitBackdrop {
        val sky = currentSky()
        shownSky = sky
        return CockpitBackdrop(sky.top, sky.mid, sky.bottom, accent, getColor(R.color.home_grid), if (night) 0x26 else 0x30,
            sky.glow, sky.sunX, sky.sunY, sky.sunSize, sky.stars)
    }

    /** Called every minute and on new weather: the sun moves, so the sky follows it. */
    private fun renderSky() {
        if (::screenRoot.isInitialized && currentSky() != shownSky) screenRoot.background = skyBackdrop()
    }

    private fun renderWeather() {
        val weather = info.weather.value
        renderSky()
        weatherIcon.bind(weather)
        weatherText.text = weather?.let { "${it.degrees}°" } ?: "—"
        weatherDetail.text = weather?.condition?.label ?: info.city.value.label
    }

    private fun renderPrayer() {
        if (!::prayerText.isInitialized) return
        // The next prayer by the head unit's own clock, the one shown on screen, so « dans 25 min » always agrees with it.
        val next = runCatching {
            val now = LocalDateTime.now()
            val city = info.city.value
            val today = now.toLocalDate()
            RadioLocalInfo.prayerStatus(now, info.prayerTimes(city, today.minusDays(1)), info.prayerTimes(city, today),
                info.prayerTimes(city, today.plusDays(1))).next
        }.getOrNull()
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
        altitudeText.text = state.altitude?.let { "≈ $it m" }.orEmpty()
        speedStatus.text = when (state.status) {
            "Precise location off" -> "Localisation précise désactivée"
            "GPS unavailable" -> "GPS indisponible"
            "GPS disabled" -> "GPS désactivé"
            "Waiting for GPS" -> "Recherche du GPS…"
            "Mock location ignored" -> "Position simulée ignorée"
            else -> tripSummary()
        }
        // One beep each time the speed passes the chosen limit, again only after dropping 3 km/h below it.
        val limit = gauge.limit
        val kmh = state.speed.kmh ?: 0
        if (limit != null && kmh > limit && !alerted) {
            alerted = true
            runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP2, 400) }
        } else if (limit == null || kmh <= limit - 3) alerted = false
        if (shownPage == Page.TRIP) tripValues?.invoke()
    }

    /**
     * A call shown by the phone app's notification, large over the top of the screen, with that notification's own
     * buttons: Answer in green, Decline or Hang up in red. Tapping the caller opens the phone app.
     */
    private fun renderCall(call: CallAlert?) {
        if (!::callBanner.isInitialized) return
        if (call == null) {
            if (callBanner.visibility == View.VISIBLE) callBanner.animate().alpha(0f).translationY(-dp(20).toFloat())
                .setDuration(150).withEndAction { callBanner.visibility = View.GONE; callBanner.removeAllViews() }.start()
            return
        }
        val wasShown = callBanner.visibility == View.VISIBLE && callBanner.alpha > 0f
        callBanner.removeAllViews()
        callBanner.addView(row().apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(getColor(R.color.home_background_top))
                setStroke(dp(1), getColor(R.color.home_card_border))
            }
            elevation = dp(12).toFloat()
            setPadding(dp(16), dp(10), dp(12), dp(10))
            addView(FrameLayout(context).apply {
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(CALL_GREEN) }
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_home_phone)
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(column().apply {
                addView(caps(if (call.ongoing) "En appel" else "Appel entrant", if (call.ongoing) muted else CALL_GREEN))
                addView(label(call.caller, 22, text, 700, condensed = true), wrap().apply { topMargin = dp(3) })
                call.detail?.takeIf { it != call.caller }?.let { addView(label(it, 12, muted), wrap().apply { topMargin = dp(2) }) }
                setOnClickListener { runCatching { call.open?.send() } }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
            // Decline first, then answer, as on phones; other buttons (speaker, mute…) after them.
            call.actions.sortedBy { it.kind.ordinal.let { order -> if (order == 1) -1 else order } }.take(3).forEach { action ->
                val color = when (action.kind) {
                    CallButton.ANSWER -> CALL_GREEN
                    CallButton.DECLINE -> CALL_RED
                    CallButton.OTHER -> getColor(R.color.home_chip)
                }
                addView(label(action.title, 15, if (action.kind == CallButton.OTHER) text else Color.WHITE, 700).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(18), 0, dp(18), 0)
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(color)
                    }, 14)
                    contentDescription = "${action.title} ${call.caller}"
                    setOnClickListener {
                        try {
                            action.intent.send()
                        } catch (_: PendingIntent.CanceledException) {
                            Toast.makeText(context, "L’appel n’est plus disponible.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, LinearLayout.LayoutParams(-2, dp(52)).apply { marginStart = dp(10) })
            }
        }, FrameLayout.LayoutParams(-1, -2))
        callBanner.visibility = View.VISIBLE
        if (!wasShown) {
            callBanner.alpha = 0f
            callBanner.translationY = -dp(20).toFloat()
            callBanner.animate().alpha(1f).translationY(0f).setDuration(200).start()
        }
    }

    /** The alert setting: [ROAD_LIMIT] follows the road's legal limit (the default), 0 is off, else a fixed speed. */
    private fun alertSetting() = prefs.getInt(SPEED_LIMIT, ROAD_LIMIT)

    private fun roadMode() = alertSetting() == ROAD_LIMIT

    /** The limit the dial and the beep use now. */
    private fun activeLimit(): Int? = if (roadMode()) roadLimit else alertSetting().takeIf { it > 0 }

    /** Matches each GPS fix to the road under the car; fetches the roads around it again every 350 m. */
    private fun followRoad(fix: android.location.Location, kmh: Int?) {
        val now = System.currentTimeMillis()
        if (!fetchingRoads && roadLimits.needsRefresh(fix.latitude, fix.longitude, now)) {
            fetchingRoads = true
            scope?.launch {
                withContext(Dispatchers.IO) { roadLimits.refresh(fix.latitude, fix.longitude, now) }
                fetchingRoads = false
            } ?: run { fetchingRoads = false }
        }
        val bearing = if ((kmh ?: 0) >= 5 && fix.hasBearing()) fix.bearing.toInt() else null
        val limit = roadLimits.limitAt(fix.latitude, fix.longitude, bearing)?.limit
        if (limit != roadLimit) {
            roadLimit = limit
            if (::gauge.isInitialized) gauge.limit = limit
            if (::limitSign.isInitialized) limitSign.limit = limit
        }
    }

    private fun kilometres(meters: Double) = if (meters < 10_000) String.format(Locale.FRENCH, "%.1f km", meters / 1000)
        else "${(meters / 1000).toInt()} km"

    private fun hours(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"
    }

    /** Time since the trip started (the head unit starting with the car), then distance and average. */
    private fun tripSummary(): String {
        val current = trip.trip
        val elapsed = current.startedAt.takeIf { it > 0 }?.let { System.currentTimeMillis() - it }?.coerceAtLeast(0) ?: 0
        return listOfNotNull("Trajet ${hours(elapsed)}", kilometres(current.meters),
            current.averageKmh?.let { "moy. $it km/h" }).joinToString(" · ")
    }

    private var tripValues: (() -> Unit)? = null

    /** The trip in big numbers, its reset, and the speed alert. */
    private fun showTrip() {
        val values = mutableMapOf<String, TextView>()
        fun stat(name: String) = column().apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(getColor(R.color.home_chip))
                setStroke(dp(1), getColor(R.color.home_card_border))
            }
            val value = label("—", 24, text, 700, condensed = true).apply { gravity = Gravity.CENTER }
            values[name] = value
            addView(value, LinearLayout.LayoutParams(-1, -2))
            addView(caps(name, muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        fun statRow(vararg names: String) = row().apply {
            gravity = Gravity.FILL
            names.forEachIndexed { index, name ->
                addView(stat(name), LinearLayout.LayoutParams(0, -1, 1f).apply { if (index > 0) marginStart = dp(8) })
            }
        }
        openPanel(Page.TRIP, column().apply {
            background = sheet()
            setPadding(dp(18), dp(12), dp(18), dp(12))
            addView(panelHeader("Trajet", null).apply {
                addView(label("Remettre à zéro", 13, text, 700).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(14), 0, dp(14), 0)
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(getColor(R.color.home_chip))
                    }, 12)
                    setOnClickListener { trip.reset(System.currentTimeMillis()); saveTrip(); tripValues?.invoke(); renderSpeed() }
                }, 1, LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(10) })
            })
            addView(statRow("Distance", "Temps de conduite", "Moyenne", "Maximum"),
                LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
            addView(statRow("Départ", "Durée du trajet", "Altitude"), LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
            addView(caps("Alerte de vitesse · Route = limite lue sur la carte"), wrap().apply { topMargin = dp(12) })
            val limits = listOf(ROAD_LIMIT, 0, 60, 80, 100, 120)
            addView(choices(limits, alertSetting(), { when (it) { ROAD_LIMIT -> "Route"; 0 -> "Non"; else -> "$it" } }, { null }) {
                prefs.edit().putInt(SPEED_LIMIT, it).apply()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        })
        tripValues = {
            val current = trip.trip
            val state = gps.snapshot()
            values["Distance"]?.text = kilometres(current.meters)
            values["Temps de conduite"]?.text = hours(current.movingMs)
            values["Moyenne"]?.text = current.averageKmh?.let { "$it km/h" } ?: "—"
            values["Maximum"]?.text = if (current.maxKmh > 0) "${current.maxKmh} km/h" else "—"
            values["Départ"]?.text = current.startedAt.takeIf { it > 0 }?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            } ?: "—"
            values["Durée du trajet"]?.text = current.startedAt.takeIf { it > 0 }
                ?.let { hours((System.currentTimeMillis() - it).coerceAtLeast(0)) } ?: "—"
            values["Altitude"]?.text = state.altitude?.let { "≈ $it m" } ?: "—"
        }
        tripValues?.invoke()
    }

    private fun renderMedia() {
        if (!::mediaTitle.isInitialized) return
        val state = media.snapshot()
        mediaSource = state.source
        val hasMedia = state.source != null && (state.title != null || state.playing)
        // Radio Maroc & Coran gets its own player look; every other app the common card.
        val radio = hasMedia && state.source == RADIO_PACKAGE
        radioBody.visibility = if (radio) View.VISIBLE else View.GONE
        mediaBody.visibility = if (radio) View.GONE else View.VISIBLE
        if (radio) radioCard.bind(state)
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
        // The radio card always glows in the station's colour, like the radio's player.
        washWith(if (neutral && !radio) Color.TRANSPARENT else art?.let(RadioMotion::logoColor) ?: if (hasMedia) accent else Color.TRANSPARENT)
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

    internal fun startSafely(intent: Intent) {
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

    /** A favourite on a tinted tile, or, in the apps list ([plain]), just the app's logo and name. */
    private fun appTile(app: Launchable, compact: Boolean, plain: Boolean = false, onLong: (View) -> Unit) = column().apply {
        gravity = Gravity.CENTER
        val icon = links.icon(app)
        val tint = iconColor(app, icon) ?: accent
        background = if (plain) pressable(null, 14) else pressable(GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            if (neutral) {
                setColor(getColor(R.color.home_chip))
                setStroke(dp(1), getColor(R.color.home_card_border))
            } else setColor(Color.argb(if (night) 0x2E else 0x26, Color.red(tint), Color.green(tint), Color.blue(tint)))
        }, 12)
        setPadding(dp(4), dp(6), dp(4), dp(6))
        val size = dp(when { plain -> 54; compact -> 38; else -> 48 })
        addView(ImageView(context).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(size, size))
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

    internal fun sheet(): Drawable = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(getColor(R.color.home_sheet))
        setStroke(dp(1), getColor(R.color.home_card_border))
    }

    internal fun openPanel(page: Page, content: View) {
        panelHost.removeAllViews()
        panelHost.addView(content, FrameLayout.LayoutParams(-1, -1))
        panelHost.visibility = View.VISIBLE
        panelHost.alpha = 0f
        panelHost.translationY = dp(16).toFloat()
        panelHost.animate().alpha(1f).translationY(0f).setDuration(180).start()
        shownPage = page
        markDock(page)
    }

    internal fun closePanel() {
        if (!::panelHost.isInitialized) return
        markDock(Page.HOME)
        shownPage = Page.HOME
        tripValues = null
        if (::updatesUi.isInitialized) updatesUi.observe(null)
        if (panelHost.visibility != View.VISIBLE) return
        panelHost.animate().alpha(0f).translationY(dp(12).toFloat()).setDuration(150).withEndAction {
            panelHost.visibility = View.GONE
            panelHost.removeAllViews()
        }.start()
    }

    internal fun panelHeader(title: String, subtitle: String?) = row().apply {
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
                        return appTile(app, compact = false, plain = true) { toggleFavorite(app); notifyDataSetChanged() }.apply {
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
            addView(accentPicker(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
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
            addView(caps("Effets sonores"), wrap().apply { topMargin = dp(14) })
            fun settingRow(title: String, detail: String, value: String, action: (TextView) -> Unit) = row().apply {
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(getColor(R.color.home_chip))
                    setStroke(dp(1), getColor(R.color.home_card_border))
                }, 12)
                setPadding(dp(14), 0, dp(14), 0)
                addView(column().apply {
                    addView(label(title, 15, text, 700))
                    addView(label(detail, 11, muted), wrap().apply { topMargin = dp(2) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val state = label(value, 14, accent, 700)
                addView(state, wrap().apply { marginStart = dp(12) })
                setOnClickListener { action(state) }
            }
            addView(settingRow("Clics de l’interface", "Son discret à chaque appui, comme Radio Maroc",
                if (clicks.enabled) "ON" else "OFF") { state ->
                val enabled = !clicks.enabled
                clicks.enabled = enabled
                prefs.edit().putBoolean(CLICKS, enabled).apply()
                state.text = if (enabled) "ON" else "OFF"
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
            // ÉGALISEUR: the radio's three profiles, for all the head unit's sound.
            addView(caps("Égaliseur", muted), wrap().apply { topMargin = dp(12) })
            val soundStatus = label("", 11, muted)
            val profiles = mutableMapOf<SoundProfile, TextView>()
            fun markProfiles(current: SoundProfile) = profiles.forEach { (profile, view) ->
                val selected = profile == current
                view.setTextColor(if (selected) onAccent else text)
                view.background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (selected) accent else getColor(R.color.home_chip))
                    if (!selected) setStroke(dp(1), getColor(R.color.home_card_border))
                }, 12)
            }
            addView(row().apply {
                gravity = Gravity.FILL
                SoundProfile.entries.forEachIndexed { index, profile ->
                    val view = label(profile.label, 15, text, 700).apply {
                        gravity = Gravity.CENTER
                        setOnClickListener {
                            val applied = SoundEffects.apply(profile)
                            if (applied) prefs.edit().putString(SOUND_PROFILE, profile.name).apply()
                            markProfiles(if (applied) profile else SoundProfile.NORMAL)
                            soundStatus.text = if (applied) "Appliqué à tout le son du boîtier"
                                else "Ce boîtier n’autorise pas l’égaliseur pour tout le son"
                        }
                    }
                    profiles[profile] = view
                    addView(view, LinearLayout.LayoutParams(0, dp(46), 1f).apply { if (index > 0) marginStart = dp(8) })
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            markProfiles(soundProfile())
            soundStatus.text = "Normal, Basses ou Voix, comme dans Radio Maroc"
            addView(soundStatus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            val equalizer = ClickSound.equalizerIntent(this@HomeActivity)
            addView(settingRow("Dolby", if (equalizer != null) "Effets sonores du boîtier" else "Indisponible sur cet appareil",
                if (equalizer != null) "›" else "—") {
                if (equalizer != null) startSafely(equalizer)
                else Toast.makeText(this@HomeActivity, "Ce boîtier ne propose pas de réglage Dolby aux applis.", Toast.LENGTH_LONG).show()
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
            addView(caps("Mises à jour"), wrap().apply { topMargin = dp(14) })
            addView(row().apply {
                val message = label("", 14, text, 600).apply { maxLines = 2 }
                val action = label("", 14, onAccent, 700).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(16), 0, dp(16), 0)
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(accent)
                    }, 12)
                    setOnClickListener { scope?.let(updatesUi::primaryAction) }
                }
                addView(column().apply {
                    addView(message)
                    addView(label("Version installée ${updatesUi.installedVersion} · vérifiée et signée, via GitHub", 11, muted),
                        wrap().apply { topMargin = dp(2) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(action, LinearLayout.LayoutParams(-2, dp(40)).apply { marginStart = dp(10) })
                updatesUi.observe { state ->
                    message.text = state.message
                    action.text = updatesUi.actionLabel(state)
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(caps("Écran d’accueil"), wrap().apply { topMargin = dp(14) })
            addView(label(if (isDefaultHome()) "Incar Drive est l’écran d’accueil par défaut" else "Définir Incar Drive par défaut  ›",
                14, if (isDefaultHome()) muted else text, 700).apply {
                setPadding(0, dp(10), 0, dp(6))
                if (!isDefaultHome()) setOnClickListener { askDefaultHome() }
            })
            addView(row().apply {
                gravity = Gravity.FILL
                fun button(title: String, action: () -> Unit) = label(title, 15, text, 700).apply {
                    gravity = Gravity.CENTER
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(getColor(R.color.home_chip))
                    }, 14)
                    setOnClickListener { action() }
                }
                addView(button("Réglages Android  ›") { startSafely(Intent(Settings.ACTION_SETTINGS)) },
                    LinearLayout.LayoutParams(0, -1, 1f))
                addView(button("Réglages Incarlink  ›") { openOemSettings() },
                    LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(8) })
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        }
    }

    /**
     * The Incarlink's own settings app: `com.suding.settings` from the box's firmware, or, on another head unit, the
     * one installed app whose name says settings. Never a list of every app.
     */
    internal fun openOemSettings(@Suppress("UNUSED_PARAMETER") choose: Boolean = false) {
        if (links.openOemSettings()) return
        val candidates = links.oemSettingsCandidates()
        when (candidates.size) {
            0 -> Toast.makeText(this, "Les réglages Incarlink ne sont pas sur cet appareil.", Toast.LENGTH_LONG).show()
            1 -> links.open(candidates.single())
            else -> AlertDialog.Builder(this)
                .setTitle("Réglages Incarlink")
                .setItems(candidates.map { it.label }.toTypedArray()) { _, index -> links.open(candidates[index]) }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    /** Every dashboard colour as a round swatch with its name; the chosen one is ringed. */
    private fun accentPicker() = row().apply {
        gravity = Gravity.FILL
        Accent.entries.forEach { choice ->
            val color = if (night) choice.night else choice.day
            val selected = choice == accentChoice
            addView(column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                background = pressable(null, 12)
                setPadding(0, dp(4), 0, dp(4))
                addView(View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        setStroke(dp(if (selected) 4 else 1), if (selected) text else getColor(R.color.home_card_border))
                    }
                }, LinearLayout.LayoutParams(dp(if (selected) 40 else 36), dp(if (selected) 40 else 36)))
                addView(label(choice.label, 12, if (selected) text else muted, if (selected) 700 else 500).apply {
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
                contentDescription = "Couleur ${choice.label}"
                setOnClickListener {
                    prefs.edit().putString(ACCENT, choice.name).apply()
                    recreate()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    /** A row of segmented choices; picking one saves it and redraws the screen in it. */
    internal fun <T> choices(all: List<T>, current: T, name: (T) -> String, swatch: (T) -> Int?, pick: (T) -> Unit) = row().apply {
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

    internal fun shade(color: Int, toward: Int, amount: Float) = ArgbEvaluator().evaluate(amount, color, toward) as Int

    internal companion object {
        const val REQUEST_BLUETOOTH = 5
        const val CALL_GREEN = 0xFF1FA35B.toInt()
        const val CALL_RED = 0xFFE5394F.toInt()
        const val REQUEST_CONTACTS = 6
        const val TRIP = "trip"
        const val SPEED_LIMIT = "speed_limit"
        const val ROAD_LIMIT = -1
        const val ASKED_HOME = "asked_home"
        const val REQUEST_HOME_ROLE = 7
        const val REQUEST_PHONE_STATE = 8
        const val CLICKS = "interface_clicks"
        const val SOUND_PROFILE = "sound_profile"
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
