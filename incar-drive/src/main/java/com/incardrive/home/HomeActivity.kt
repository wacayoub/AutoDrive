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
    private lateinit var gauge: SpeedDial
    private lateinit var speedStatus: TextView
    private lateinit var favoritesGrid: LinearLayout
    private lateinit var panelHost: FrameLayout
    private val dockItems = mutableMapOf<Page, View>()
    private lateinit var dockApps: LinearLayout
    private lateinit var dockBar: LinearLayout
    private lateinit var island: LinearLayout
    private lateinit var islandDot: View
    private lateinit var islandText: TextView
    private lateinit var logoPlate: View
    // What the live island may show, most urgent first: over the limit, a speed camera, the next turn (when the
    // navigation card is out of sight), a prayer within a quarter of an hour.
    private var islandOver: String? = null
    private var islandCamera: String? = null
    private var islandTurn: String? = null
    private var islandPrayer: String? = null
    private var islandShown: String? = null
    private var quiet: QuietMode? = null
    private lateinit var dockDivider: View
    private var guiding = false
    private var mapsFrame: View? = null
    private var mapsAppCard: View? = null
    private lateinit var quick: QuickSettings
    internal lateinit var dialer: Dialer
    private lateinit var navigation: Navigation
    private lateinit var mapWidget: MapWidget
    private lateinit var carLogo: CarLogo
    private lateinit var ownMap: OwnMap
    private lateinit var wazeMap: WazeMap
    private lateinit var googleWeb: WazeMap
    private lateinit var carBadge: CarLogo
    private lateinit var mediaResume: MediaResume
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
            ownMap.position(fix.latitude, fix.longitude, fix.bearing.takeIf { fix.hasBearing() && (kmh ?: 0) >= 5 })
            wazeMap.position(fix.latitude, fix.longitude)
            googleWeb.position(fix.latitude, fix.longitude)
            followRoad(fix, kmh)
            trip.accept(System.currentTimeMillis(), fix.latitude, fix.longitude, fix.accuracy.takeIf { fix.hasAccuracy() }, kmh)
            if (System.currentTimeMillis() - lastTripSave > 30_000) saveTrip()
        }
        quick = QuickSettings(this)
        dialer = Dialer(this)
        navigation = Navigation(this, prefs)
        mapWidget = MapWidget(this)
        carLogo = CarLogo(this)
        googleWeb = WazeMap(this, google = true) {
            val maps = packageManager.getLaunchIntentForPackage(AppLinks.MAPS)
            if (maps != null) startSafely(maps) else Toast.makeText(this, "Google Maps n’est pas installé sur ce boîtier.", Toast.LENGTH_LONG).show()
        }
        wazeMap = WazeMap(this) {
            val waze = packageManager.getLaunchIntentForPackage("com.waze")
            if (waze != null) startSafely(waze) else Toast.makeText(this, "Waze n’est pas installé sur ce boîtier.", Toast.LENGTH_LONG).show()
        }
        ownMap = OwnMap(this) { if (NavWatch.nav.value != null) { if (!navigation.resume()) navMissing() } else if (!navigation.open()) navMissing() }
        carBadge = CarLogo(this, "car_badge", 48)
        mediaResume = MediaResume(this)
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
        showWelcome()
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
        if (::wazeMap.isInitialized) wazeMap.destroy()
        if (::googleWeb.isInitialized) googleWeb.destroy()
        if (::mediaResume.isInitialized) mediaResume.release()
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
        if (carLogo.onResult(requestCode, resultCode, data) || carBadge.onResult(requestCode, resultCode, data)) return
        if (!widgets.onResult(requestCode, resultCode) && !mapWidget.onResult(requestCode, resultCode)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
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
        HomeVisibility.shown = true
        HomeVisibility.changed?.invoke()
        media.start()
        // When the car (and the head unit) starts: play the last app again, without a tap.
        mediaResume.onHomeShown({ media.live().any { it.playbackState?.state == PlaybackState.STATE_PLAYING } }, media::live)
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
        mapWidget.start()
        phone.start()
        tick.run()
        renderMedia()
        renderSpeed()
        renderFavorites()
        renderDockApps()
        wazeMap.resume()
        googleWeb.resume()
    }

    override fun onStop() {
        wazeMap.pause()
        googleWeb.pause()
        HomeVisibility.shown = false
        HomeVisibility.changed?.invoke()
        handler.removeCallbacks(tick)
        scope?.cancel()
        scope = null
        media.stop()
        gps.stop()
        widgets.stop()
        mapWidget.stop()
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

    /**
     * With the « Système » theme, day or night is worked out from the clock when the screen is built. A head unit
     * often boots with a wrong clock and corrects it a moment later (network, GPS), and the hour passes sunset while
     * driving: so the answer is checked again every tick and the screen rebuilt when it has changed. Panels are left
     * alone while open; the switch then happens when they close.
     */
    private fun followSystemTheme() {
        if (Appearance.restore(prefs) != Appearance.SYSTEM || shownPage != Page.HOME || isFinishing || isDestroyed) return
        val wanted = runCatching { SystemNight.isNight(applicationContext) }.getOrNull() ?: return
        if (wanted != night) recreate()
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
            quiet?.tick()
            followSystemTheme()
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
        // Like glass lit from above: a touch lighter at the top edge, the card's own colour below.
        val card = getColor(R.color.home_card)
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        colors = intArrayOf(shadeKeepingAlpha(card, .09f), card, card)
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
            pager = SwipePager(context) { page -> markDots(page); placeMapsFrame(); renderTurnIsland() }.apply {
                clipChildren = true
                addPage(row().apply {
                    gravity = Gravity.FILL
                    addView(mediaCard(), LinearLayout.LayoutParams(0, -1, 1.4f))
                    addView(column().apply {
                        addView(speedCard(), LinearLayout.LayoutParams(-1, 0, 1.25f))
                        addView(mapsCard(), LinearLayout.LayoutParams(-1, 0, .75f).apply { topMargin = dp(10) })
                    }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(10); marginEnd = dp(10) })
                // The live map (a Google Maps or Waze widget) takes the favourites' place when chosen; they move to page 2.
                addView(when {
                    !mapOnHome() -> favoritesCard()
                    mapSource() == MAPS_APP -> mapWidget.guideView(guiding = false) { if (!navigation.open()) navMissing() }
                        .also { mapsAppCard = it }
                    mapSource() == WAZE_MAP -> wazeMap.view()
                    mapSource() == GOOGLE_WEB -> googleWeb.view()
                    ownMapOnHome() -> ownMap.view()
                    else -> mapWidget.view()
                }, LinearLayout.LayoutParams(0, -1, 1.05f))
                })
                // Second page: the connected phone and the driver's Android widgets.
                addPage(row().apply {
                    gravity = Gravity.FILL
                    addView(phone.view(), LinearLayout.LayoutParams(0, -1, 1f))
                    if (mapOnHome()) addView(favoritesCard(), LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(10) })
                    addView(widgets.view(), LinearLayout.LayoutParams(0, -1, if (mapOnHome()) 1.45f else 2.45f)
                        .apply { marginStart = dp(10) })
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
            addView(dock(), LinearLayout.LayoutParams(-1, dp(46)))
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

    /**
     * During guidance on the first page, the right card becomes a frame reaching down to the bottom of the screen,
     * where Android puts Google Maps' small window, and the dock shortens to end before it. The window stays Maps'
     * own: Android decides its exact place and size, the frame is laid out around that corner.
     */
    private fun placeMapsFrame() {
        val wanted = guiding && mapOnHome() && (mapSource() == MAPS_WIDGET || mapSource() == MAPS_APP) && ::pager.isInitialized && pager.page == 0 && ::screenRoot.isInitialized
        if (!wanted) {
            mapsFrame?.let { screenRoot.removeView(it) }
            mapsFrame = null
            if (::dockBar.isInitialized) {
                (dockBar.layoutParams as? LinearLayout.LayoutParams)?.let { if (it.rightMargin != 0) { it.setMargins(0, 0, 0, 0); dockBar.requestLayout() } }
                dockApps.visibility = View.VISIBLE
                dockDivider.visibility = View.VISIBLE
            }
            return
        }
        if (mapsFrame != null) return
        // After the page has finished sliding in (260 ms), so the card is measured where it rests.
        screenRoot.postDelayed({ frameMaps() }, 320)
    }

    private fun frameMaps(retries: Int = 3) {
        run {
            if (mapsFrame != null || !guiding || pager.page != 0 || isDestroyed) return
            val card = (if (mapSource() == MAPS_APP) mapsAppCard else mapWidget.card)?.takeIf { it.width > 0 && it.isShown } ?: return
            val page = generateSequence(card.parent as? View) { it.parent as? View }.firstOrNull { it.parent === pager }
            if (page != null && page.translationX != 0f) {
                if (retries > 0) screenRoot.postDelayed({ frameMaps(retries - 1) }, 150)
                return
            }
            val root = IntArray(2).also { screenRoot.getLocationInWindow(it) }
            val at = IntArray(2).also { card.getLocationInWindow(it) }
            val dockAt = IntArray(2).also { dockBar.getLocationInWindow(it) }
            val left = at[0] - root[0]
            val top = at[1] - root[1]
            val bottom = dockAt[1] - root[1] + dockBar.height
            val dockRight = dockAt[0] - root[0] + dockBar.width
            val frame = mapWidget.guideView { if (!navigation.resume()) navMissing() }
            mapsFrame = frame
            // Above the cards, under the panels and the call banner.
            screenRoot.addView(frame, 1, FrameLayout.LayoutParams(card.width, bottom - top).apply {
                leftMargin = left
                topMargin = top
            })
            mapWidget.guideUpdate(NavWatch.nav.value)
            dockApps.visibility = View.GONE
            dockDivider.visibility = View.GONE
            (dockBar.layoutParams as? LinearLayout.LayoutParams)?.let {
                // The right margin itself: a margin « end » set after the first layout is not applied.
                it.setMargins(0, 0, dockRight - left + dp(10), 0)
                dockBar.requestLayout()
            }
        }
    }

    /**
     * The live island: one pill in the middle of the top bar for what matters right now, in place of the car's logo,
     * which comes back when nothing does. Red for the limit and cameras, blue for the turn, teal for the prayer.
     */
    private fun renderIsland() {
        if (!::island.isInitialized) return
        val (message, color) = when {
            islandOver != null -> islandOver to 0xFFE5262E.toInt()
            islandCamera != null -> islandCamera to 0xFFE5262E.toInt()
            islandTurn != null -> islandTurn to navColor
            islandPrayer != null -> islandPrayer to prayerColor
            else -> null to 0
        }
        if (message == islandShown) return
        islandShown = message
        if (message == null) {
            island.animate().alpha(0f).setDuration(180).withEndAction {
                island.visibility = View.GONE
                logoPlate.visibility = View.VISIBLE
                logoPlate.alpha = 0f
                logoPlate.animate().alpha(1f).setDuration(180).start()
            }.start()
            return
        }
        islandText.text = message
        islandDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        island.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(0xF0121A26.toInt())
            setStroke(dp(1), color)
        }
        if (island.visibility != View.VISIBLE) {
            logoPlate.visibility = View.GONE
            island.visibility = View.VISIBLE
            island.alpha = 0f
            island.scaleX = .85f
            island.animate().alpha(1f).scaleX(1f).setDuration(220).start()
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

    /** Quiet mode: a black screen with the clock, the speed and the next turn; a touch ends it. */
    internal fun enterQuiet() {
        if (quiet != null || !::screenRoot.isInitialized) return
        closePanel()
        val mode = QuietMode(this) { quiet = null }
        quiet = mode
        mode.show(screenRoot)
        mode.speed(gps.snapshot().speed.kmh, false)
        mode.turn(NavWatch.nav.value)
    }

    /** The head unit's voice assistant (Google Assistant when installed), for « navigue vers… » or « appelle… ». */
    internal fun openAssistant() {
        val voice = Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val assist = Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = listOf(voice, assist).any { intent -> runCatching { startActivity(intent) }.isSuccess }
        if (!opened) Toast.makeText(this, "Aucun assistant vocal n’est installé sur ce boîtier.", Toast.LENGTH_LONG).show()
    }

    /**
     * The welcome scene, once per start of the head unit: the car's logo (when one is chosen) and the greeting over
     * the sky, fading into the home screen.
     */
    private fun showWelcome() {
        if (welcomed || !::screenRoot.isInitialized) return
        welcomed = true
        val hour = LocalDateTime.now().hour
        val hello = when (hour) { in 5..11 -> "Bonjour"; in 12..17 -> "Bon après-midi"; in 18..21 -> "Bonsoir"; else -> "Bonne nuit" }
        val scene = FrameLayout(this).apply {
            setBackgroundColor(if (night) 0xFF070B12.toInt() else 0xFFEFF2F6.toInt())
            isClickable = true
            addView(column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                carLogo.bitmap()?.let { logo ->
                    addView(ImageView(context).apply { setImageBitmap(CarLogo.fit(logo, dp(84))) }, LinearLayout.LayoutParams(-2, dp(84)))
                }
                addView(label(hello, 30, text, 300, condensed = true).apply { gravity = Gravity.CENTER },
                    wrap().apply { topMargin = dp(18) })
                addView(label("Incar Drive", 12, muted, 600).apply { letterSpacing = .2f; gravity = Gravity.CENTER },
                    wrap().apply { topMargin = dp(6) })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        screenRoot.addView(scene, FrameLayout.LayoutParams(-1, -1))
        scene.getChildAt(0).apply {
            alpha = 0f
            scaleX = .92f
            scaleY = .92f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500).start()
        }
        scene.postDelayed({
            scene.animate().alpha(0f).setDuration(450).withEndAction { screenRoot.removeView(scene) }.start()
        }, 1_700)
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
        }, LinearLayout.LayoutParams(-2, -2))
        // The driver's car logo and badge (Réglages › Écran d’accueil), centred in the room between the clock and the
        // buttons, so they never cover them.
        addView(row().apply {
            gravity = Gravity.CENTER
            island = row().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(6), dp(14), dp(6))
                islandDot = View(context)
                addView(islandDot, LinearLayout.LayoutParams(dp(8), dp(8)))
                islandText = label("", 14, Color.WHITE, 700).apply { maxLines = 1 }
                addView(islandText, wrap().apply { marginStart = dp(8) })
                visibility = View.GONE
            }
            addView(island, wrap())
            addView(row().apply {
                logoPlate = this
                gravity = Gravity.CENTER_VERTICAL
                // Chrome logos vanish on the day theme's light sky: there they sit on a dark plate.
                if (!night && (carLogo.chosen || carBadge.chosen)) {
                    background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xE6182030.toInt()) }
                    setPadding(dp(12), dp(5), dp(12), dp(5))
                }
                addView(carLogo.view(26), LinearLayout.LayoutParams(-2, dp(26)))
                addView(carBadge.view(15), LinearLayout.LayoutParams(-2, dp(15)).apply { marginStart = dp(8) })
            }, wrap())
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
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
        // The driver's style: the road ahead with the speed over it, or the graduated dial.
        val styleName = prefs.getString(SPEED_STYLE, ROAD) ?: ROAD
        val brand = BrandGauge.Style.entries.firstOrNull { it.name == styleName }
        val dial: View = when {
            brand != null -> BrandGauge(context, brand, text, muted)
            styleName == DIAL -> SpeedGauge(context, getColor(R.color.home_gauge_track), shade(accent, Color.BLACK, .25f),
                shade(accent, Color.WHITE, .3f), text, muted)
            else -> RoadGauge(context, ROAD_BLUE, text, muted, carLogo.bitmap())
        }
        gauge = dial as SpeedDial
        gauge.limit = activeLimit()
        // The road reaches the card's edges, clipped to its rounded corners; the dials keep a margin.
        val road = dial is RoadGauge
        if (road) clipToOutline = true
        addView(dial, FrameLayout.LayoutParams(-1, -1).apply {
            if (!road) setMargins(dp(4), dp(4), dp(4), dp(20))
        })
        // Altitude in the upper left corner, the limit as a road sign in the upper right.
        addView(column().apply {
            altitudeText = label("", 11, muted, 600)
            addView(altitudeText, LinearLayout.LayoutParams(-1, -2))
        }, FrameLayout.LayoutParams(dp(90), -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(12), dp(10), 0, 0) })
        limitSign = LimitSign(context).apply { limit = activeLimit() }
        addView(limitSign,
            FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(10), 0) })
        cameraBadge = label("", 12, Color.WHITE, 700).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFFE5262E.toInt()) }
            setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(R.drawable.ic_home_camera)?.mutate()?.apply {
                setTint(Color.WHITE)
                setBounds(0, 0, dp(14), dp(14))
            }, null, null, null)
            compoundDrawablePadding = dp(4)
            visibility = View.GONE
        }
        addView(cameraBadge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8) })
        // Over the road's dark band the trip line is light in both themes.
        speedStatus = label("", 11, if (road) 0xE6FFFFFF.toInt() else muted, 600).apply { gravity = Gravity.CENTER }
        addView(speedStatus, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(8) })
        contentDescription = "Vitesse et trajet"
        setOnClickListener {
            if (!gps.permitted()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), 1)
            else showTrip()
        }
    }

    /**
     * The navigation card: « Naviguer » in the chosen app (Google Maps or Waze), then one scrolling row of places
     * (Maison, Travail, the driver's own) and quick searches around the car. While the app guides, the card shows the
     * guidance instead: the turn arrow, the distance to the turn, the instruction, the time left and the arrival.
     */
    private fun mapsCard() = column().apply {
        background = glass()
        setPadding(dp(8), dp(6), dp(8), dp(8))
        addView(row().apply {
            background = pressable(null, 12)
            setPadding(dp(8), 0, dp(4), 0)
            addView(FrameLayout(context).apply {
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(tint(navColor, 0x33)) }
                navIcon = ImageView(context).apply {
                    setImageResource(R.drawable.ic_home_navigation)
                    imageTintList = ColorStateList.valueOf(navColor)
                    rotation = 45f
                }
                addView(navIcon, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            addView(column().apply {
                navTitle = label("Naviguer", 19, text, 700, condensed = true)
                navDetail = label(navigation.app().label, 12, muted, 500)
                addView(navTitle)
                addView(navDetail, wrap().apply { topMargin = dp(2) })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
            navEta = label("", 13, navColor, 700).apply {
                gravity = Gravity.END
                maxLines = 2
                ellipsize = null
                visibility = View.GONE
            }
            addView(navEta, wrap().apply { marginStart = dp(6) })
            navChevron = ImageView(context).apply {
                setImageResource(R.drawable.ic_home_chevron)
                imageTintList = ColorStateList.valueOf(navColor)
            }
            addView(navChevron, LinearLayout.LayoutParams(dp(22), dp(22)))
            contentDescription = "Naviguer"
            setOnClickListener {
                // While guiding, back to the guidance; otherwise straight to choosing a destination.
                val opened = if (NavWatch.nav.value != null) navigation.resume() else navigation.open()
                if (!opened) Toast.makeText(context, "${navigation.app().label} n’est pas installé.", Toast.LENGTH_LONG).show()
            }
            RadioMotion.pressScale(this, .97f)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        placesRow = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row().also { placesStrip = it })
        }
        addView(placesRow, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(4) })
        renderPlaces()
    }

    private lateinit var navIcon: ImageView
    private lateinit var navEta: TextView
    private lateinit var navChevron: ImageView
    private lateinit var placesRow: View
    private lateinit var placesStrip: LinearLayout

    private fun chip(icon: Int, title: String, iconColor: Int, action: () -> Unit, long: (() -> Unit)? = null) = row().apply {
        gravity = Gravity.CENTER
        background = pressable(GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(getColor(R.color.home_chip))
            setStroke(dp(1), getColor(R.color.home_card_border))
        }, 12)
        setPadding(dp(12), 0, dp(14), 0)
        addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(iconColor)
        }, LinearLayout.LayoutParams(dp(18), dp(18)))
        addView(label(title, 14, text, 700), wrap().apply { marginStart = dp(7) })
        contentDescription = title
        setOnClickListener { action() }
        long?.let { handler -> setOnLongClickListener { handler(); true } }
        RadioMotion.pressScale(this, .94f)
    }

    /** Maison, Travail, the driver's places, then the quick searches, in one row that scrolls sideways. */
    private fun renderPlaces() {
        if (!::placesStrip.isInitialized) return
        placesStrip.removeAllViews()
        fun add(view: View) = placesStrip.addView(view, LinearLayout.LayoutParams(-2, -1).apply {
            if (placesStrip.childCount > 0) marginStart = dp(6)
        })
        Place.entries.forEach { place -> add(chip(place.icon, place.label, navColor, { goTo(place) }, { editPlace(place) })) }
        myPlaces().forEach { saved ->
            add(chip(R.drawable.ic_home_location, saved.name, navColor, {
                if (!navigation.goTo(saved.number)) navMissing()
            }, { editMyPlace(saved) }))
        }
        QuickSearch.entries.forEach { search ->
            add(chip(search.icon, search.label, weatherColor, { if (!navigation.search(search.query)) navMissing() }))
        }
    }

    /** Whether the live map replaces the favourites on the first page (Réglages › Navigation › Carte sur l’accueil). */
    private fun mapOnHome() = prefs.getBoolean(MAP_ON_HOME, true)

    /** Incar Drive's own OpenStreetMap map rather than Google Maps' widget, when a map is on the home page. */
    private fun mapSource() = prefs.getString(MAP_SOURCE, OWN_MAP) ?: OWN_MAP
    private fun ownMapOnHome() = mapSource() == OWN_MAP

    private fun navMissing() = Toast.makeText(this, "${navigation.app().label} n’est pas installé.", Toast.LENGTH_LONG).show()

    // The driver's own places, stored like the saved phone numbers: a name and an address or position.
    private fun myPlaces(): List<SavedNumber> = SavedNumber.decode(prefs.getString(MY_PLACES, null))

    private fun saveMyPlaces(list: List<SavedNumber>) = prefs.edit().putString(MY_PLACES, SavedNumber.encode(list)).apply()

    /** Adds a place, or renames, moves or removes one (an empty address removes it). */
    private fun editMyPlace(existing: SavedNumber?, after: () -> Unit = {}) {
        val name = EditText(this).apply {
            hint = "Nom (Mosquée, Famille…)"
            setText(existing?.name.orEmpty())
            setSingleLine()
        }
        val address = EditText(this).apply {
            hint = "Adresse ou lieu"
            setText(existing?.number.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
            setSingleLine()
        }
        fun store(value: String?) {
            val list = myPlaces().filter { it != existing }.toMutableList()
            val target = value?.trim().orEmpty()
            if (target.isNotEmpty()) list += SavedNumber(name.text.toString().trim().ifEmpty { target }, target)
            saveMyPlaces(list)
            renderPlaces()
            after()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(existing?.name ?: "Nouveau lieu")
            .setView(column().apply {
                setPadding(dp(20), dp(4), dp(20), 0)
                addView(name)
                addView(address)
            })
            .setPositiveButton("Enregistrer") { _, _ -> store(address.text.toString()) }
            .setNegativeButton("Annuler", null)
            .setNeutralButton(if (existing != null) "Supprimer" else "Ma position") { _, _ ->
                if (existing != null) store(null)
                else currentPosition()?.let(::store)
                    ?: Toast.makeText(this, "Position pas encore disponible.", Toast.LENGTH_LONG).show()
            }
        dialog.show()
    }

    private lateinit var navTitle: TextView
    private lateinit var navDetail: TextView

    /**
     * While the navigation app guides, the card shows its guidance: the turn arrow and the distance to the turn in
     * large, the instruction, and the time left with the arrival on the right. The places row makes way for it.
     */
    private fun renderNav(nav: NavInfo?) {
        guiding = nav != null
        placeMapsFrame()
        ownMap.guide(nav)
        lastNav = nav
        renderTurnIsland()
        quiet?.turn(nav)
        mapWidget.guideUpdate(nav)
        if (!::navTitle.isInitialized) return
        // With the Maps frame on the right, the guidance is shown large there: this card keeps its destinations.
        val inFrame = mapOnHome() && (mapSource() == MAPS_WIDGET || mapSource() == MAPS_APP)
        if (nav == null || inFrame) {
            navIcon.setImageResource(R.drawable.ic_home_navigation)
            navIcon.imageTintList = ColorStateList.valueOf(navColor)
            navIcon.rotation = 45f
            navTitle.text = "Naviguer"
            navTitle.maxLines = 1
            navDetail.text = currentStreet ?: navigation.app().label
            navDetail.maxLines = 1
            navDetail.setTextColor(muted)
            navEta.visibility = View.GONE
            navChevron.visibility = View.VISIBLE
            placesRow.visibility = View.VISIBLE
            return
        }
        if (nav.arrow != null) {
            navIcon.setImageDrawable(nav.arrow)
            navIcon.rotation = 0f
        }
        val title = nav.step ?: nav.remaining ?: "Guidage"
        navTitle.text = title
        // A second line that adds something: the instruction, else the street, never the title again.
        // Maps writes « 8.5 km » with non-breaking or narrow spaces: compare without any kind of space.
        fun plain(value: String) = value.filterNot { it.isWhitespace() }.lowercase() // isWhitespace covers U+00A0, U+202F
        fun differs(value: String?) = value?.takeIf { it.isNotBlank() && plain(it) != plain(title) }
        navDetail.text = differs(nav.instruction)
            ?: currentStreet?.let { "Sur $it" }
            ?: differs(nav.distance)?.let { "$it restants" }
            ?: "Continuer tout droit"
        navDetail.maxLines = 2
        navDetail.setTextColor(text)
        navEta.text = listOfNotNull(nav.remaining, nav.arrival).joinToString("\n")
        navEta.visibility = if (navEta.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        navChevron.visibility = View.GONE
        placesRow.visibility = View.GONE
    }

    /** The traffic layer needs the driver's own free TomTom key: explained and typed here, kept on the head unit. */
    private fun askTrafficKey() {
        val field = android.widget.EditText(this).apply {
            setText(prefs.getString(OwnMap.TRAFFIC_KEY, null).orEmpty())
            hint = "Clé TomTom"
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        AlertDialog.Builder(this)
            .setTitle("Trafic en direct")
            .setMessage("Les bouchons en couleurs sur la carte viennent de TomTom, gratuit jusqu’à 200 000 tuiles par mois, " +
                "sans carte bancaire.\n\n1. Sur un téléphone ou un PC : developer.tomtom.com, crée un compte gratuit.\n" +
                "2. Copie ta clé (« API key »).\n3. Tape-la ici.\n\nLaisse vide pour retirer le trafic.")
            .setView(FrameLayout(this).apply {
                setPadding(dp(20), dp(4), dp(20), 0)
                addView(field, FrameLayout.LayoutParams(-1, -2))
            })
            .setPositiveButton("Enregistrer") { _, _ ->
                val key = field.text.toString().trim()
                prefs.edit().apply { if (key.isEmpty()) remove(OwnMap.TRAFFIC_KEY) else putString(OwnMap.TRAFFIC_KEY, key) }.apply()
                recreate()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun downloadToyotaLogo() {
        Toast.makeText(this, "Téléchargement du logo Toyota…", Toast.LENGTH_SHORT).show()
        Thread {
            val parts = BrandLogo.toyota()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (parts != null && carLogo.set(parts.first) && carBadge.set(BrandLogo.hybridBadge())) {
                    Toast.makeText(this, "Logo Toyota et badge HYBRID ajoutés", Toast.LENGTH_SHORT).show()
                    recreate()
                } else Toast.makeText(this, "Le logo n’a pas pu être téléchargé : vérifie Internet et réessaie.",
                    Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private var lastNav: NavInfo? = null

    /** The next turn goes to the island only where the navigation card is not on screen: page 2 or under a panel. */
    private fun renderTurnIsland() {
        val nav = lastNav
        val hidden = (::pager.isInitialized && pager.page != 0) || shownPage != Page.HOME
        islandTurn = if (nav != null && hidden) listOfNotNull(nav.step ?: nav.remaining, nav.instruction?.take(28)).joinToString(" · ")
            .takeIf { it.isNotBlank() } else null
        renderIsland()
    }

    private fun destination(place: Place) = prefs.getString(place.key, null)?.takeIf { it.isNotBlank() }


    /**
     * Guidance to the address set in Incar Drive, or else to the place saved as Home or Work in the driver's Google
     * Maps account, which Maps finds by its label.
     */
    private fun goTo(place: Place) {
        val own = destination(place)
        val opened = if (own != null) navigation.goTo(own) else navigation.goToSaved(work = place == Place.WORK)
        if (!opened) navMissing()
    }

    private fun describe(target: String?) = when {
        target == null -> "Celle de ${navigation.app().label}"
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
        dockBar = this
        background = glass(18)
        elevation = dp(6).toFloat()
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(5), dp(4), dp(5), dp(4))
        fun item(page: Page?, icon: Int, title: String, color: Int, action: () -> Unit) = row().apply {
            gravity = Gravity.CENTER
            tag = color
            setPadding(dp(10), 0, dp(12), 0)
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(color)
                tag = "icon"
            }, LinearLayout.LayoutParams(dp(18), dp(18)))
            addView(label(title, 13, text, 600).apply { tag = "label" }, wrap().apply { marginStart = dp(6) })
            contentDescription = title
            setOnClickListener { action() }
            page?.let { dockItems[it] = this }
        }
        // The home screen's pages first, each as wide as its name…
        addView(item(Page.HOME, R.drawable.ic_home_home, "Accueil", accent) { closePanel(); pager.show(0) }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(item(Page.DIALER, R.drawable.ic_home_phone, "Téléphone", phoneColor) { dialer.show() }, LinearLayout.LayoutParams(0, -1, 1.25f))
        addView(item(Page.APPS, R.drawable.ic_home_apps, "Applis", appsColor) { showApps(editing = false) }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(item(Page.SETTINGS, R.drawable.ic_home_settings, "Réglages", muted) { showSettings() }, LinearLayout.LayoutParams(0, -1, 1.15f))
        // …then the driver's apps, as logos, like the dock of a phone.
        dockDivider = View(context).apply { setBackgroundColor(tint(text, 0x26)) }
        addView(dockDivider,
            LinearLayout.LayoutParams(dp(1), dp(24)).apply { marginStart = dp(6); marginEnd = dp(6) })
        dockApps = row().apply { gravity = Gravity.CENTER_VERTICAL }
        addView(dockApps, LinearLayout.LayoutParams(-2, -1))
        renderDockApps()
        post { markDock(Page.HOME) }
    }

    /** The apps in the dock: up to [DOCK_SIZE] logos, then a faint « + » while there is room. */
    private fun renderDockApps() {
        if (!::dockApps.isInitialized) return
        dockApps.removeAllViews()
        val list = dockAppList()
        list.forEach { app ->
            dockApps.addView(ImageView(this).apply {
                setImageDrawable(links.icon(app))
                setPadding(dp(4), dp(3), dp(4), dp(3))
                background = pressable(null, 12)
                contentDescription = app.label
                setOnClickListener { links.open(app) }
                setOnLongClickListener { anchor ->
                    PopupMenu(this@HomeActivity, anchor).apply {
                        menu.add("Retirer de la barre")
                        menu.add("Changer les applis de la barre")
                        setOnMenuItemClickListener { item ->
                            if (item.title == "Retirer de la barre") toggleDockApp(app) else showApps(editing = true, dock = true)
                            true
                        }
                    }.show()
                    true
                }
                RadioMotion.pressScale(this, .9f)
            }, LinearLayout.LayoutParams(dp(42), dp(36)).apply { marginStart = dp(2) })
        }
        if (list.size < DOCK_SIZE) dockApps.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_home_add)
            imageTintList = ColorStateList.valueOf(muted)
            alpha = .6f
            setPadding(dp(9), dp(8), dp(9), dp(8))
            background = pressable(null, 12)
            contentDescription = "Ajouter une appli à la barre"
            setOnClickListener { showApps(editing = true, dock = true) }
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(2) })
    }

    private fun dockAppList(): List<Launchable> {
        val apps = links.apps()
        val keys = prefs.getString(DOCK_APPS, null)?.split('\n')?.filter { it.isNotBlank() }
            ?: DEFAULT_DOCK.mapNotNull { pkg -> apps.firstOrNull { it.component.packageName == pkg }?.component?.flattenToString() }
        return keys.mapNotNull { key -> apps.firstOrNull { it.component.flattenToString() == key } }.take(DOCK_SIZE)
    }

    private fun toggleDockApp(app: Launchable) {
        val current = dockAppList().toMutableList()
        if (current.any { it.component == app.component }) current.removeAll { it.component == app.component }
        else if (current.size >= DOCK_SIZE) {
            Toast.makeText(this, "$DOCK_SIZE applis au maximum dans la barre : retire d’abord une appli.", Toast.LENGTH_LONG).show()
            return
        } else current += app
        prefs.edit().putString(DOCK_APPS, current.joinToString("\n") { it.component.flattenToString() }).apply()
        renderDockApps()
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
            islandPrayer = if (minutes in 0..15) "${it.prayer.label} " + (if (minutes <= 0) "maintenant" else "dans $minutes min") else null
            when {
                minutes <= 0 -> "maintenant"
                minutes < 60 -> "dans $minutes min"
                else -> "dans ${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"
            }
        }.orEmpty()
        if (next == null) islandPrayer = null
        renderIsland()
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
        islandOver = if (limit != null && kmh > limit) "$kmh km/h · limite $limit" else null
        renderIsland()
        quiet?.speed(state.speed.kmh, limit != null && kmh > limit)
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
                ownMap.cameras(roadLimits.cameraList())
                fetchingRoads = false
            } ?: run { fetchingRoads = false }
        }
        val bearing = if ((kmh ?: 0) >= 5 && fix.hasBearing()) fix.bearing.toInt() else null
        val road = roadLimits.limitAt(fix.latitude, fix.longitude, bearing)
        // The street under the car, shown on the navigation card when not guiding.
        if (road?.name != currentStreet) {
            currentStreet = road?.name
            if (NavWatch.nav.value == null) renderNav(null)
        }
        renderCamera(roadLimits.cameraAhead(fix.latitude, fix.longitude, bearing), kmh)
        val limit = road?.limit.takeIf { roadMode() }
        if (limit != roadLimit) {
            roadLimit = limit
            if (::gauge.isInitialized) gauge.limit = limit
            if (::limitSign.isInitialized) limitSign.limit = limit
        }
    }

    private var currentStreet: String? = null
    private var warnedCamera: Camera? = null
    private lateinit var cameraBadge: TextView

    /** A red « Radar 350 m » badge on the speed card, with one double beep per camera, while one is ahead. */
    private fun renderCamera(ahead: Pair<Camera, Int>?, kmh: Int?) {
        if (!::cameraBadge.isInitialized) return
        if (ahead == null || (kmh ?: 0) < 10) {
            cameraBadge.visibility = View.GONE
            if (ahead == null) warnedCamera = null
            islandCamera = null
            renderIsland()
            return
        }
        val (camera, meters) = ahead
        cameraBadge.text = listOfNotNull("Radar ${(meters / 10) * 10} m", camera.limit?.let { "$it" }).joinToString(" · ")
        islandCamera = cameraBadge.text.toString()
        renderIsland()
        cameraBadge.visibility = View.VISIBLE
        if (warnedCamera != camera) {
            warnedCamera = camera
            runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).startTone(ToneGenerator.TONE_PROP_BEEP2, 600) }
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
        if (state.playing) state.source?.takeIf { it != packageName }?.let(mediaResume::remember)
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
                // One tap plays the last app again (the radio at first), straight away, without opening it.
                val last = mediaResume.last?.takeIf { packageManager.getLaunchIntentForPackage(it) != null }
                    ?: RADIO_PACKAGE.takeIf { packageManager.getLaunchIntentForPackage(it) != null }
                mediaAction.text = last?.let { "Écouter ${appLabel(it)}" } ?: "Ouvrir les applis"
                mediaAction.setOnClickListener {
                    if (last == null) showApps(editing = false)
                    else if (!mediaResume.play(last, media.live())) packageManager.getLaunchIntentForPackage(last)?.let(::startSafely)
                }
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
            HomeVisibility.front = intent.component?.packageName ?: intent.`package`
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
        val size = dp(when { plain && compact -> 44; plain -> 54; compact -> 38; else -> 48 })
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
        // A free place: just a faint « + », no frame, like the logos around it.
        background = pressable(null, 14)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_home_add)
            imageTintList = ColorStateList.valueOf(muted)
            alpha = .6f
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
                    val tile = if (app == null) addTile() else appTile(app, compact = true, plain = true) { anchor ->
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
        // The greeting would peek out at the panel's rounded corner.
        if (::greeting.isInitialized) greeting.visibility = View.INVISIBLE
        shownPage = page
        renderTurnIsland()
        markDock(page)
    }

    internal fun closePanel() {
        if (!::panelHost.isInitialized) return
        markDock(Page.HOME)
        shownPage = Page.HOME
        tripValues = null
        if (::updatesUi.isInitialized) updatesUi.observe(null)
        if (::greeting.isInitialized) greeting.visibility = View.VISIBLE
        renderTurnIsland()
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
    private fun showApps(editing: Boolean, dock: Boolean = false) {
        val apps = links.apps()
        fun chosen(app: Launchable) = (if (dock) dockAppList() else favorites()).any { it.component == app.component }
        openPanel(Page.APPS, column().apply {
            background = sheet()
            setPadding(dp(16), dp(12), dp(16), dp(10))
            addView(panelHeader(when { dock -> "Applis de la barre"; editing -> "Choisir les favoris"; else -> "Applications" },
                when {
                    dock -> "Touche une appli pour l’ajouter à la barre du bas ou l’en retirer · $DOCK_SIZE au maximum"
                    editing -> "Touche une appli pour l’ajouter ou la retirer · 6 au maximum"
                    else -> "Garde le doigt sur une appli pour l’ajouter aux favoris ou à la barre du bas"
                }))
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
                        val toggle = { if (dock) toggleDockApp(app) else toggleFavorite(app); notifyDataSetChanged() }
                        return appTile(app, compact = false, plain = true) { anchor ->
                            if (editing) toggle() else PopupMenu(this@HomeActivity, anchor).apply {
                                val favorite = favorites().any { it.component == app.component }
                                val docked = dockAppList().any { it.component == app.component }
                                menu.add(if (favorite) "Retirer des favoris" else "Ajouter aux favoris")
                                menu.add(if (docked) "Retirer de la barre du bas" else "Ajouter à la barre du bas")
                                setOnMenuItemClickListener { item ->
                                    if (item.title.toString().contains("favoris")) toggleFavorite(app) else toggleDockApp(app)
                                    notifyDataSetChanged()
                                    true
                                }
                            }.show()
                        }.apply {
                            if (editing) {
                                setOnClickListener { toggle() }
                                alpha = if (chosen(app)) 1f else .5f
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
            addView(caps("Compteur de vitesse"), wrap().apply { topMargin = dp(6) })
            val speedStyle = prefs.getString(SPEED_STYLE, ROAD) ?: ROAD
            val styleNames = mapOf(ROAD to "Route", DIAL to "Cadran", BrandGauge.Style.ELECTRIC.name to "Électrique",
                BrandGauge.Style.FACET.name to "Angulaire", BrandGauge.Style.VIRTUAL.name to "Virtuel",
                BrandGauge.Style.ELEGANT.name to "Élégant")
            styleNames.keys.chunked(3).forEachIndexed { index, group ->
                addView(choices(group, speedStyle, { styleNames.getValue(it) }, { null }) {
                    prefs.edit().putString(SPEED_STYLE, it).apply()
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); if (index == 1) bottomMargin = dp(12) })
            }
            addView(caps("Navigation"), wrap().apply { topMargin = dp(6) })
            addView(row().apply {
                gravity = Gravity.FILL
                val current = navigation.app()
                NavApp.entries.forEachIndexed { index, app ->
                    val selected = app == current
                    addView(label(app.label, 15, if (selected) onAccent else text, 700).apply {
                        gravity = Gravity.CENTER
                        background = pressable(GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            setColor(if (selected) accent else getColor(R.color.home_chip))
                            if (!selected) setStroke(dp(1), getColor(R.color.home_card_border))
                        }, 12)
                        setOnClickListener {
                            if (packageManager.getLaunchIntentForPackage(app.packageName) == null) {
                                Toast.makeText(this@HomeActivity, "${app.label} n’est pas installé.", Toast.LENGTH_LONG).show()
                            } else {
                                navigation.choose(app)
                                renderNav(NavWatch.nav.value)
                                renderPlaces()
                                showSettings()
                            }
                        }
                    }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { if (index > 0) marginStart = dp(8) })
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(label("À droite de l’accueil", 13, text, 700), wrap().apply { topMargin = dp(10) })
            val rightNow = if (!mapOnHome()) "favorites" else mapSource()
            val rightNames = linkedMapOf(MAPS_APP to "Appli Maps", GOOGLE_WEB to "Maps web", WAZE_MAP to "Waze",
                OWN_MAP to "Carte", MAPS_WIDGET to "Widget", "favorites" to "Favoris")
            rightNames.keys.chunked(3).forEach { group ->
                addView(choices(group, rightNow, { rightNames.getValue(it) }, { null }) {
                    prefs.edit().putBoolean(MAP_ON_HOME, it != "favorites")
                        .apply { if (it != "favorites") putString(MAP_SOURCE, it) }.apply()
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
            addView(label("Appli Maps : un cadre où la petite fenêtre de l’appli Google Maps vient se poser pendant le guidage ; " +
                "l’accueil revient tout seul quand le guidage démarre · " +
                "Maps web : le site de Google Maps avec son trafic · Waze : la carte web de Waze · " +
                "Carte : celle d’Incar Drive (OpenStreetMap), avec les radars et la consigne de Maps · Widget : celui de Google Maps, s’il a la position",
                11, muted).apply { maxLines = 5 },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            val hasTraffic = !prefs.getString(OwnMap.TRAFFIC_KEY, null).isNullOrBlank()
            addView(label(if (hasTraffic) "Trafic en direct sur la carte : activé  ›" else "Ajouter le trafic en direct sur la carte  ›",
                14, if (hasTraffic) text else accent, 700).apply {
                setPadding(0, dp(10), 0, dp(6))
                setOnClickListener { askTrafficKey() }
            })
            // Google Maps' own small window (picture-in-picture) during guidance: it is Maps' feature, allowed per app
            // in Android's settings; this row opens Maps' app page there and explains the two steps.
            addView(row().apply {
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(getColor(R.color.home_chip))
                    setStroke(dp(1), getColor(R.color.home_card_border))
                }, 12)
                setPadding(dp(14), 0, dp(14), 0)
                addView(column().apply {
                    addView(label("Petite fenêtre Google Maps", 15, text, 700))
                    addView(label("Pendant un guidage, Accueil garde Maps en petite fenêtre, si le boîtier l’affiche", 11, muted)
                        .apply { maxLines = 2 }, wrap().apply { topMargin = dp(2) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(label("›", 18, accent, 700), wrap().apply { marginStart = dp(12) })
                setOnClickListener {
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Petite fenêtre Google Maps")
                        .setMessage("1. Dans la page de Google Maps, ouvre « Picture-in-picture » (ou « Image dans l’image ») " +
                            "et autorise-la.\n2. Lance un itinéraire dans Maps, puis appuie sur Accueil : Maps reste en petite " +
                            "fenêtre au-dessus d’Incar Drive, déplaçable au doigt.\n\nSi rien n’apparaît, le boîtier n’affiche " +
                            "pas ces fenêtres : la bulle de guidage d’Incar Drive prend le relais.")
                        .setPositiveButton("Ouvrir Google Maps dans Android") { _, _ ->
                            startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${AppLinks.MAPS}")))
                        }
                        .setNegativeButton("Fermer", null)
                        .show()
                }
            }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8) })
            val bubbleOn = prefs.getBoolean(NavBubble.ENABLED, true) && Settings.canDrawOverlays(this@HomeActivity)
            addView(row().apply {
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(getColor(R.color.home_chip))
                    setStroke(dp(1), getColor(R.color.home_card_border))
                }, 12)
                setPadding(dp(14), 0, dp(14), 0)
                addView(column().apply {
                    addView(label("Bulle de guidage", 15, text, 700))
                    addView(label("Flèche, distance et arrivée au-dessus des autres applis pendant un itinéraire", 11, muted)
                        .apply { maxLines = 2 }, wrap().apply { topMargin = dp(2) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(label(if (bubbleOn) "ON" else "OFF", 14, accent, 700), wrap().apply { marginStart = dp(12) })
                setOnClickListener {
                    if (!Settings.canDrawOverlays(this@HomeActivity)) {
                        // Android's « display over other apps » for Incar Drive; the bubble starts once it is allowed.
                        prefs.edit().putBoolean(NavBubble.ENABLED, true).apply()
                        startSafely(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                    } else {
                        prefs.edit().putBoolean(NavBubble.ENABLED, !bubbleOn).apply()
                        showSettings()
                    }
                }
            }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8) })
            addView(caps("Destinations"), wrap().apply { topMargin = dp(12) })
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
            (myPlaces().map { it } + listOf<SavedNumber?>(null)).forEach { saved ->
                addView(row().apply {
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(getColor(R.color.home_chip))
                        setStroke(dp(1), getColor(R.color.home_card_border))
                    }, 12)
                    setPadding(dp(14), 0, dp(14), 0)
                    addView(ImageView(context).apply {
                        setImageResource(if (saved == null) R.drawable.ic_home_add else R.drawable.ic_home_location)
                        imageTintList = ColorStateList.valueOf(text)
                    }, LinearLayout.LayoutParams(dp(20), dp(20)))
                    addView(label(saved?.name ?: "Ajouter un lieu", 15, text, 700), wrap().apply { marginStart = dp(12) })
                    addView(label((saved?.let { describe(it.number) } ?: "Mosquée, famille, salle de sport…") + "  ›", 14, muted, 500)
                        .apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
                    setOnClickListener { editMyPlace(saved) { showSettings() } }
                }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
            }
            addView(caps("Média"), wrap().apply { topMargin = dp(14) })
            addView(row().apply {
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(getColor(R.color.home_chip))
                    setStroke(dp(1), getColor(R.color.home_card_border))
                }, 12)
                setPadding(dp(14), 0, dp(14), 0)
                addView(column().apply {
                    addView(label("Reprise au démarrage", 15, text, 700))
                    addView(label("Relance la dernière appli écoutée (${mediaResume.last?.let(::appLabel) ?: "Radio Maroc"}) quand le " +
                        "boîtier démarre, sans appuyer sur Lecture", 11, muted).apply { maxLines = 2 }, wrap().apply { topMargin = dp(2) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val state = label(if (mediaResume.enabled) "ON" else "OFF", 14, accent, 700)
                addView(state, wrap().apply { marginStart = dp(12) })
                setOnClickListener {
                    mediaResume.enabled = !mediaResume.enabled
                    state.text = if (mediaResume.enabled) "ON" else "OFF"
                }
            }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8) })
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
            fun imageRow(image: CarLogo, choose: String, change: String) = row().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(6))
                addView(label(if (image.chosen) change else choose, 14, text, 700).apply {
                    setOnClickListener { image.pick() }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (image.chosen) addView(label("Retirer", 14, accent, 700).apply {
                    setPadding(dp(12), 0, 0, 0)
                    setOnClickListener { image.remove(); recreate() }
                })
            }
            addView(imageRow(carLogo, "Logo de la voiture en haut  ›", "Changer le logo de la voiture  ›"))
            addView(imageRow(carBadge, "Badge à côté du logo (ex. Hybrid)  ›", "Changer le badge  ›"))
            // No photo on the head unit? Incar Drive fetches the Toyota emblem itself, in silver, with a HYBRID badge.
            addView(label("Logo Toyota argent + HYBRID  ›", 14, accent, 700).apply {
                setPadding(0, dp(10), 0, dp(6))
                setOnClickListener { downloadToyotaLogo() }
            })
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

    /** [color] moved towards white by [amount], its transparency unchanged. */
    private fun shadeKeepingAlpha(color: Int, amount: Float) = Color.argb(Color.alpha(color),
        (Color.red(color) + (255 - Color.red(color)) * amount).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * amount).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt())

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
        const val MY_PLACES = "my_places"
        const val MAP_ON_HOME = "map_on_home"
        // The welcome scene plays once per start of the app's process, not at every theme change.
        private var welcomed = false
        const val MAP_SOURCE = "map_source"
        const val OWN_MAP = "own"
        const val MAPS_WIDGET = "widget"
        const val WAZE_MAP = "waze"
        const val MAPS_APP = "maps_app"
        const val GOOGLE_WEB = "google_web"
        const val PREFS = "incar_drive_home"
        const val APPEARANCE = "appearance"
        const val ACCENT = "accent"
        val COORDINATES = Regex("""-?\d+(\.\d+)?,-?\d+(\.\d+)?""")
        const val CITY = "city"
        const val FAVORITES = "favorites"
        const val DOCK_APPS = "dock_apps"
        const val SPEED_STYLE = "speed_style"
        const val ROAD = "road"
        // The ice blue of the lane lines on recent Japanese digital dashboards, like the driver's Toyota.
        const val ROAD_BLUE = 0xFF8FD8FF.toInt()
        const val DIAL = "dial"
        const val DOCK_SIZE = 5
        // First apps of the bottom bar: navigation, video and music, those that are installed.
        val DEFAULT_DOCK = listOf("com.google.android.apps.maps", "com.waze", "com.google.android.youtube",
            RADIO_PACKAGE, "com.spotify.music")
        const val RADIO_PACKAGE = "com.autodrive.radiomaroc"
        const val RADIO_HOME_VISIBLE = "com.autodrive.radiomaroc.HOME_VISIBLE"
        // First favourites when the listener has not chosen any: the radio, video and navigation apps of the car.
        val DEFAULT_FAVORITES = listOf(RADIO_PACKAGE, "com.google.android.youtube", "com.waze",
            "com.spotify.music", "com.google.android.apps.youtube.music", "com.android.chrome")
    }
}
