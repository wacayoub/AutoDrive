package com.autodrive.radiomaroc

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.SearchManager
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.provider.MediaStore
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextDirectionHeuristics
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.autodrive.launcher.RadioArtwork
import com.autodrive.launcher.RadioCatalogue
import com.autodrive.launcher.RadioQueue
import com.autodrive.launcher.RadioStation
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class RadioActivity : ComponentActivity() {
    private val dolbyPanel by lazy { DolbyPanel.find(this) }
    private val openDolbyPanel = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val stationPopupPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private companion object {
        const val PREFERENCES = "radio_maroc"
        const val APPEARANCE = "appearance"
        const val LEGACY_DAY_APPEARANCE = "day_appearance"
        const val DISPLAY_LAYOUT = "tv_layout"
        const val CATALOGUE_LAYOUT = "tv_catalogue"
    }

    private enum class CarResume(val label: String) {
        OFF("Désactivée"),
        AFTER_BOOT("Après redémarrage Android"),
        ON_OPEN("À chaque ouverture"),
    }

    private enum class Appearance(val label: String, val nightFlag: Int?, val androidMode: Int) {
        DAY("Jour", Configuration.UI_MODE_NIGHT_NO, UiModeManager.MODE_NIGHT_NO),
        NIGHT("Nuit", Configuration.UI_MODE_NIGHT_YES, UiModeManager.MODE_NIGHT_YES),
        SYSTEM("Système", null, UiModeManager.MODE_NIGHT_AUTO);

        companion object {
            fun restore(prefs: SharedPreferences): Appearance {
                val saved = prefs.getString(APPEARANCE, null)
                return entries.firstOrNull { it.name == saved }
                    ?: if (prefs.getBoolean(LEGACY_DAY_APPEARANCE, false)) DAY else NIGHT
            }
        }
    }

    private var startupDeadline = 0L
    private var restoringActivity = false
    private var playbackQuery: String? = null
    private val accent by lazy { getColor(R.color.radio_accent) }
    private val textColor by lazy { getColor(R.color.radio_text) }
    private val muted by lazy { getColor(R.color.radio_muted) }
    private val amber by lazy { getColor(R.color.radio_favorite) }
    private val ripple by lazy { ColorStateList.valueOf(getColor(R.color.radio_ripple)) }
    private lateinit var prefs: SharedPreferences
    private lateinit var artwork: RadioArtwork
    private lateinit var directory: DirectoryBrowser
    private lateinit var radioUpdates: RadioUpdates
    private var tab = StationTab.MOROCCO
    private var displayLayout = DisplayLayout.GUIDE
    private var catalogueLayout = DisplayLayout.GUIDE
    private var catalogueFocusStation: String? = null
    private var revealCatalogueSelection = true
    private lateinit var menuButton: TextView
    private lateinit var catalogueButton: TextView
    private var radioFavoritesOnly = false
    private var country = "FR"
    private var directoryBookmarks = emptyList<RadioStation>()
    private var favorites = emptySet<String>()
    private var query = ""
    private var rows = emptyList<RadioStation>()
    private var listEntries = emptyList<ListEntry>()
    private var savedStation: RadioStation? = null
    private var highlighted: Pair<String?, Boolean>? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var connectionFailed = false
    private var piconsOffline = false
    private lateinit var tabs: Map<StationTab, TextView>
    private lateinit var radioFilter: ImageButton
    private lateinit var countryButton: TextView
    private lateinit var stationSearch: EditText
    private lateinit var worldRegion: TextView
    private lateinit var sortButton: TextView
    private lateinit var categoryButton: TextView
    private lateinit var stationToolbar: View
    private lateinit var chipScroller: View
    private lateinit var chipBar: LinearLayout
    private var favoriteOrder = emptyList<String>()
    // Per-row subtitle overrides (suggestion reasons, preset numbers) and the surah a Herraz row starts at.
    private var rowNotes = emptyMap<String, String>()
    private var rowChapters = emptyMap<String, Int>()
    private lateinit var directoryAction: TextView
    private lateinit var list: AbsListView
    private lateinit var empty: TextView
    private lateinit var playerTitle: TextView
    private lateinit var playerPanel: LinearLayout
    private lateinit var playerSubtitle: TextView
    private var newsFooter: View? = null
    private var newsTicker: HeadlineTicker? = null
    private var tickerSource: TextView? = null
    private var clockTime: TextView? = null
    private var clockDate: TextView? = null
    private val clockTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val clockDateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)
    private val shortClockDateFormat = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT)
    private var tickerBulletin: NewsBulletin? = null
    private var tickerStartedAt = 0L
    private var tickerDuration = 0L
    private data class NewsEdition(val expiresAt: Long, val headlines: List<NewsHeadline>)
    private data class TvStationInfo(
        val id: String, val publisher: NewsPublisher?, val streamTitle: String?, val fallback: NewsBulletin,
    )
    private val tvStationInfo = MutableStateFlow<TvStationInfo?>(null)
    private val stationHeadlines = MutableStateFlow<List<NewsHeadline>>(emptyList())
    private val stationNews = mutableMapOf<NewsPublisher, NewsEdition>()
    private lateinit var localInfo: LocalInfoFeed
    private var weatherTile: WeatherTile? = null
    private var prayerTile: PrayerTile? = null
    private var weatherChip: WeatherChip? = null
    private var prayerChip: PrayerChip? = null
    private var playerCategory: TextView? = null
    private lateinit var playerArtwork: StationArtwork
    private lateinit var playerStatus: TextView
    private lateinit var statusBadge: LinearLayout
    private lateinit var liveIndicator: LinearLayout
    private lateinit var liveDot: View
    // A slow blink instead of a continuous fade: the LIVE dot no longer forces a redraw on every frame while listening.
    private var livePulsing = false
    private val livePulse = object : Runnable {
        override fun run() {
            if (!liveDot.isAttachedToWindow || liveIndicator.visibility != View.VISIBLE) {
                livePulsing = false
                liveDot.alpha = 1f
                return
            }
            liveDot.alpha = if (liveDot.alpha > .6f) .3f else 1f
            liveDot.postDelayed(this, 900)
        }
    }
    private lateinit var audioMotif: PremiumRadioMotif
    private lateinit var playButton: ImageButton
    private lateinit var previous: ImageButton
    private lateinit var next: ImageButton
    private var playerFavorite: ImageButton? = null
    private var soundStatusLabel: TextView? = null
    private val dashboardInfo = MutableStateFlow(false)
    private var typingInGuide = false
    // The screen variant follows the configuration and the chosen layout; RadioScreenProfile holds the thresholds.
    private val profile get() = RadioScreenProfile.of(resources.configuration, displayLayout)
    private val television get() = profile.television
    private val landscape get() = profile.landscape
    private val compactTv get() = profile.compactTv
    private val touchGuide get() = profile.touchGuide
    private val touchMosaic get() = profile.touchMosaic
    private val carScreen get() = profile.carScreen
    private val smallTouchGuide get() = profile.smallTouchGuide
    private val compactGuideTabs get() = profile.compactGuideTabs
    private val showDashboardTiles get() = profile.showDashboardTiles
    private val compactGuide get() = profile.compactGuide
    private val shortLandscape get() = profile.shortLandscape
    private val tightLandscape get() = profile.tightLandscape
    private val showingCatalogue get() = profile.showingCatalogue
    private val tvGuide get() = profile.tvGuide
    private val tvGuideRowDp get() = profile.tvGuideRowDp
    private val shortTouchGuide get() = profile.shortTouchGuide
    private val catalogueInGrid get() = profile.catalogueInGrid
    private val catalogueAdapter get() = if (catalogueInGrid) tileAdapter else stationAdapter
    private val compactDirectory get() = profile.compactDirectory
    private val inlineDirectoryPage get() = compactDirectory && directory.hasMore && (!directory.loading || rows.isNotEmpty())
    private val directoryPageLabel get() = when {
        directory.loading -> "Chargement de l’annuaire…"
        directory.failed -> "Réessayer l’annuaire"
        else -> "Charger plus de radios"
    }
    private val returnFromListening = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = openLayout(catalogueLayout)
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncPlayingStation(mediaItem)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            renderPlayer()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val appearance = Appearance.restore(newBase.getSharedPreferences(PREFERENCES, MODE_PRIVATE))
        val nightFlag = appearance.nightFlag
        val context = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R && nightFlag != null) {
            newBase.createConfigurationContext(Configuration().apply { uiMode = nightFlag })
        } else newBase
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(UiModeManager::class.java).setApplicationNightMode(Appearance.restore(prefs).androidMode)
        }
        val artworkCacheMb = if (television && resources.displayMetrics.density >= 2f) 8 else 2
        artwork = RadioArtwork(this, maxImageBytes = 1024 * 1024, memoryBytes = artworkCacheMb * 1024 * 1024,
            normalizeLogoMargins = true)
        directory = DirectoryBrowser(StationDirectory(this)) { renderRows() }
        radioUpdates = RadioUpdates(this, savedInstanceState)
        localInfo = LocalInfoFeed(RadioCity.restore(prefs.getString("tv_info_city", null)))
        tab = StationTab.restore(prefs.getString("tab", null))
        country = prefs.getString("world_country", null)?.takeIf { it in StationDirectory.countries && it != "MA" } ?: "FR"
        favorites = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
        favoriteOrder = ListeningPrefs.favoriteOrder(prefs, favorites)
        directoryBookmarks = StationDirectory.decode(prefs.getString("directory_bookmarks", null).orEmpty())
        savedStation = findStation(prefs.getString("last_station", null))
        playbackQuery = voiceQuery(intent)
        restoringActivity = savedInstanceState != null
        startupDeadline = savedInstanceState?.getLong("startup_deadline") ?: 0L
        query = savedInstanceState?.getString("query").orEmpty()
        displayLayout = DisplayLayout.restore(savedInstanceState?.getString("display_screen") ?: prefs.getString(DISPLAY_LAYOUT, null))
        radioFavoritesOnly = !television && !carScreen && savedInstanceState?.getBoolean("radio_favorites_only") == true
        catalogueLayout = DisplayLayout.restore(prefs.getString(CATALOGUE_LAYOUT, null))
            .takeUnless { it == DisplayLayout.LISTENING } ?: DisplayLayout.GUIDE
        catalogueFocusStation = savedInstanceState?.getString("catalogue_station")
        onBackPressedDispatcher.addCallback(this, returnFromListening)
        returnFromListening.isEnabled = !showingCatalogue
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        window.setDecorFitsSystemWindows(false)
        setContentView(screen())
        window.decorView.addOnUnhandledKeyEventListener { _, event -> handleStationKey(event) }
        updateDirectory()
        restoreTvCatalogueFocus()
        if (television || landscape) lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashboardInfo.collectLatest { enabled ->
                    if (enabled) coroutineScope {
                        launch { rollNewsHeadlines() }
                        launch {
                            tvStationInfo.map { it?.publisher }.distinctUntilChanged().collectLatest { publisher ->
                                publisher?.let { refreshStationNews(it) }
                            }
                        }
                        launch { localInfo.city.collectLatest { localInfo.refreshWeather(it) } }
                        launch { localInfo.refreshPrayerCalendars() }
                        launch { refreshLocalTiles() }
                        launch {
                            while (true) {
                                renderTvClock()
                                if (tab == StationTab.FOR_YOU && showingCatalogue) renderRows()
                                delay(untilNextMinute())
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query)
        outState.putBoolean("radio_favorites_only", radioFavoritesOnly)
        outState.putLong("startup_deadline", startupDeadline)
        if (television) rememberTvFocus()
        outState.putString("display_screen", displayLayout.name)
        outState.putString("catalogue_station", catalogueFocusStation)
        radioUpdates.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        RadioSound.prepareClicks(this, prefs)
        if (!restoringActivity || carResume() == CarResume.AFTER_BOOT) armCarStartup()
        restoringActivity = false
        connectPlayer()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        playbackQuery = voiceQuery(intent)
        playFromSearch()
        if ((intent.action == Intent.ACTION_MAIN || intent.action == null) && carResume() == CarResume.ON_OPEN) {
            armCarStartup()
            controller?.let { onNetworkStatus(it.sessionExtras) }
        }
    }

    private fun voiceQuery(intent: Intent): String? =
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            intent.getStringExtra(SearchManager.QUERY).orEmpty() else null

    private fun playFromSearch() {
        if (controller == null) return
        val query = playbackQuery ?: return
        playbackQuery = null
        startupDeadline = 0L
        CarRadioLibrary.stationForQuery(query, savedStation?.url)?.let(::select)
    }

    override fun onStop() {
        startupDeadline = 0L
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        audioMotif.playing = false
        updateLiveDot(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        PlaybackService.radioScreenActive = true
    }

    override fun onPause() {
        PlaybackService.radioScreenActive = false
        super.onPause()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) startupDeadline = 0L
        return super.dispatchTouchEvent(event)
    }

    private fun handleStationKey(event: KeyEvent): Boolean {
        val command = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> Player.COMMAND_SEEK_TO_NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> Player.COMMAND_SEEK_TO_PREVIOUS
            else -> return false
        }
        val player = controller ?: return false
        if (!player.isCommandAvailable(command)) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (command == Player.COMMAND_SEEK_TO_NEXT) player.seekToNext() else player.seekToPrevious()
        }
        return true
    }

    override fun onDestroy() {
        radioUpdates.close()
        directory.close()
        artwork.close()
        super.onDestroy()
    }

    private fun connectPlayer() {
        connectionFailed = false
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                if (this@RadioActivity.controller === controller) onNetworkStatus(extras)
            }
        }).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture === future) {
                controller = runCatching { future.get() }.getOrNull()
                connectionFailed = controller == null
                controller?.addListener(playerListener)
                syncPlayingStation(controller?.currentMediaItem)
                if (savedStation == Stations.luxe) {
                    controller?.sendCustomCommand(PlaybackService.LUXE_OBSERVE, Bundle.EMPTY)
                }
                playFromSearch()
                onNetworkStatus(controller?.sessionExtras ?: Bundle.EMPTY)
            }
        }, mainExecutor)
        renderPlayer()
    }

    private fun onNetworkStatus(extras: Bundle) {
        renderSoundStatus(extras)
        val offline = extras.getBoolean(PlaybackService.NETWORK_UNAVAILABLE)
        if (piconsOffline && !offline) {
            artwork.retryFailures()
            if (showingCatalogue) catalogueAdapter.notifyDataSetChanged()
            if (directory.failed) directory.loadMore()
        }
        piconsOffline = offline
        val player = controller
        if (!offline && startupDeadline > SystemClock.elapsedRealtime() && player != null) {
            startupDeadline = 0L
            val requesting = if (savedStation == Stations.luxe && player.currentMediaItem == null) {
                luxeState() in listOf(LuxeRadio.State.CONNECTING, LuxeRadio.State.PLAYING)
            } else {
                player.currentMediaItem != null && player.playWhenReady &&
                    player.playerError == null && player.playbackState != Player.STATE_ENDED
            }
            if (!requesting) savedStation?.let(::select)
        }
        renderPlayer()
    }

    private fun screen(): LinearLayout = column().apply {
        dashboardInfo.value = television || carScreen
        typingInGuide = false
        newsFooter = null
        newsTicker = null
        tickerSource = null
        clockTime = null
        clockDate = null
        weatherChip = null
        prayerChip = null
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        background = getDrawable(R.drawable.radio_background)
        isFocusableInTouchMode = !television
        val inlineCategories = showingCatalogue && (television || landscape)
        val mastheadHeight = dp(when {
            television && inlineCategories -> 36
            smallTouchGuide -> 46
            carScreen && inlineCategories -> 52
            television || inlineCategories -> 44
            landscape -> 36
            else -> 40
        })
        val masthead = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, 0)
            menuButton = button("⋮", ::showMainMenu).apply {
                id = View.generateViewId()
                contentDescription = "Menu"
                stateDescription = layoutLabel(displayLayout)
                textSize = if (television) 28f else 24f
                includeFontPadding = false
                background = pressable(R.drawable.radio_header)
            }
            val iconSize = dp(if (television) 28 else 24)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_radio)
                if (inlineCategories) contentDescription = getString(R.string.app_name)
            }, LinearLayout.LayoutParams(iconSize, iconSize))
            if (inlineCategories) {
                addView(if (television || carScreen) tvCategoryBar() else categoryBar(), LinearLayout.LayoutParams(0, -1, 1f).apply {
                    marginStart = dp(6)
                    marginEnd = dp(6)
                })
            } else {
                addView(label(getString(R.string.app_name), if (television) 18 else 14, textColor, bold = true).apply {
                    setPadding(dp(7), 0, dp(4), 0)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            if (dashboardInfo.value) {
                addView(tvClockBar(), LinearLayout.LayoutParams(-2, -1).apply { marginEnd = dp(6) })
            }
            if (profile.headerInfoChips && inlineCategories) {
                val chipHeight = dp(40)
                weatherChip = WeatherChip(context, ::showWeather).also {
                    addView(it, LinearLayout.LayoutParams(-2, chipHeight).apply { marginEnd = dp(6) })
                }
                prayerChip = PrayerChip(context, ::showPrayerTimes).also {
                    addView(it, LinearLayout.LayoutParams(-2, chipHeight).apply { marginEnd = dp(4) })
                }
                renderLocalTiles()
            }
            addView(menuButton, LinearLayout.LayoutParams(dp(if (television || inlineCategories && !carScreen) 44 else 48), -1))
        }
        addView(masthead, LinearLayout.LayoutParams(-1, mastheadHeight).apply { bottomMargin = dp(4) })
        val hideWhileTyping = mutableListOf<View>(masthead)
        addView(layoutContent(hideWhileTyping), LinearLayout.LayoutParams(-1, 0, 1f))
        // The Cockpit carries the headlines inside its player; the presets keep the whole height for logos.
        if (dashboardInfo.value && !carScreen) {
            val footer = newsBar()
            addView(footer, LinearLayout.LayoutParams(-1, dp(if (smallTouchGuide) 34 else if (compactGuide) 42 else 48)).apply {
                topMargin = dp(6)
            })
            for (control in listOfNotNull(previous, playButton, next, playerFavorite)) {
                control.nextFocusDownId = footer.id
            }
            footer.nextFocusUpId = playButton.id
        }
        setOnApplyWindowInsetsListener { view, insets ->
            val keyboard = WindowInsets.Type.ime()
            val edges = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or keyboard)
            val horizontal = dp(if (compactTv) 22 else if (television) 40 else 10)
            val vertical = dp(if (compactTv) 10 else if (television) 24 else 6)
            view.setPadding(edges.left + horizontal, edges.top + vertical, edges.right + horizontal, edges.bottom + vertical)
            val editingLandscape = landscape && !television && showingCatalogue && insets.isVisible(keyboard)
            for (panel in hideWhileTyping) panel.visibility = if (editingLandscape) View.GONE else View.VISIBLE
            typingInGuide = editingLandscape
            updateNewsBarVisibility()
            insets
        }
        if (!television) requestFocus()
    }

    private fun layoutContent(hideWhileTyping: MutableList<View>): View = when (displayLayout) {
        DisplayLayout.GUIDE -> if (touchGuide) row().apply {
            // Cockpit: the player is the hero on the left, one column of stations on the right.
            val playerWeight = if (resources.configuration.screenWidthDp >= 700) .56f else .52f
            addView(cockpitPanel().also(hideWhileTyping::add), LinearLayout.LayoutParams(0, -1, playerWeight)
                .apply { marginEnd = dp(10) })
            addView(stationList(grid = true), LinearLayout.LayoutParams(0, -1, 1f - playerWeight))
        } else if (tvGuide) row().apply {
            val playerWeight = .28f
            addView(stationList(grid = true), LinearLayout.LayoutParams(0, -1, 1f - playerWeight).apply { marginEnd = dp(10) })
            addView(stationPanel(true), LinearLayout.LayoutParams(0, -1, playerWeight))
            if (television) {
                list.nextFocusRightId = playButton.id
                list.nextFocusUpId = stationSearch.id
                previous.nextFocusLeftId = list.id
            }
        } else column().apply {
            if (!landscape) addPortraitCategories()
            val content = if (landscape) wideColumns(stationPanel(true).also(hideWhileTyping::add), stationList()) else column().apply {
                addView(stationList(), LinearLayout.LayoutParams(-1, 0, 1f))
                addView(stationPanel(false, mini = true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        DisplayLayout.MOSAIC -> column().apply {
            if (!television && !landscape) addPortraitCategories()
            // Presets on car screens, mosaic elsewhere: logo tiles above a player docked at the bottom.
            addView(stationList(grid = true), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(stationPanel(false, mini = true).also(hideWhileTyping::add),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (touchMosaic) 6 else 8) })
        }
        DisplayLayout.LISTENING -> column().apply {
            catalogueButton = button("‹  Toutes les radios") { openLayout(catalogueLayout) }.apply {
                id = View.generateViewId()
                contentDescription = "Toutes les radios"
                nextFocusUpId = menuButton.id
            }
            menuButton.nextFocusDownId = catalogueButton.id
            addView(catalogueButton, LinearLayout.LayoutParams(dp(174), dp(42)))
            addView(stationPanel(true, full = true), LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
        }
    }

    private fun LinearLayout.addPortraitCategories() {
        addView(categoryBar(), LinearLayout.LayoutParams(-1, dp(42))
            .apply { bottomMargin = dp(4) })
    }

    private fun tvClockBar(): View = row().apply {
        gravity = Gravity.CENTER_VERTICAL
        clockTime = label("", if (compactGuide) 16 else 19, textColor, bold = true).apply {
            includeFontPadding = false
            setSingleLine()
        }.also { addView(it, LinearLayout.LayoutParams(-2, -2)) }
        // Car headers give the date's room to the five category tabs; the date stays in the Infos panel.
        if (!carScreen) {
            clockDate = label("", if (compactTv) 10 else 12, muted).apply {
                includeFontPadding = false
                setSingleLine()
            }.also { addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) }) }
        }
        renderTvClock()
    }

    private fun renderTvClock() {
        val now = LocalDateTime.now(RadioLocalInfo.moroccoTime)
        clockTime?.text = clockTimeFormat.format(now)
        val dateFormat = if (resources.configuration.screenWidthDp < 560) shortClockDateFormat else clockDateFormat
        clockDate?.text = dateFormat.format(now)
    }

    private fun tvCategoryBar(): LinearLayout = row().apply {
        val bar = this
        // Segmented control: one rounded track, the active category filled with the primary colour.
        background = getDrawable(R.drawable.radio_segment)
        val barPadding = dp(if (carScreen) 4 else 3)
        setPadding(barPadding, barPadding, barPadding, barPadding)
        fun addCategory(choice: StationTab): TextView = button(
            if ((carScreen || compactGuideTabs) && choice == StationTab.MOROCCO) "Maroc" else choice.label,
        ) { selectTab(choice) }.apply {
            id = View.generateViewId()
            nextFocusUpId = menuButton.id
            if (carScreen || compactGuideTabs) contentDescription = choice.label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            val maxSize = if (carScreen) 15 else if (compactGuide) 13 else 15
            setAutoSizeTextTypeUniformWithConfiguration(if (carScreen) 12 else 10, maxSize, 1, TypedValue.COMPLEX_UNIT_SP)
            if (compactGuideTabs || carScreen && resources.configuration.screenWidthDp < 700) {
                // Narrow car headers (213 dpi) keep the labels readable by dropping the tab icons.
                setPadding(dp(3), 0, dp(3), 0)
            } else {
                gravity = Gravity.CENTER
                setPadding(dp(if (compactGuide) 6 else 10), 0, dp(if (compactGuide) 6 else 10), 0)
                val size = dp(if (compactGuide && !carScreen) 14 else 16)
                val symbol = getDrawable(stationTabIcon(choice))?.apply { setBounds(0, 0, size, size) }
                setCompoundDrawablesRelative(symbol, null, null, null)
                compoundDrawableTintList = getColorStateList(R.color.radio_tab_icon)
                compoundDrawablePadding = dp(if (compactGuide) 4 else 6)
            }
            val weight = when {
                carScreen -> if (choice == StationTab.FOR_YOU) 1.2f else 1f
                !compactGuideTabs && choice == StationTab.MOROCCO -> 1.3f
                else -> 1f
            }
            bar.addView(this, LinearLayout.LayoutParams(0, -1, weight)
                .apply { if (choice != StationTab.entries.last()) marginEnd = dp(3) })
        }
        tabs = StationTab.entries.associateWith(::addCategory)
    }

    private fun selectTab(choice: StationTab) {
        if (tab != choice) catalogueFocusStation = null
        tab = choice
        prefs.edit().putString("tab", choice.name).apply()
        updateDirectory(resetScroll = true)
    }

    private fun stationTabIcon(choice: StationTab): Int = when (choice) {
        StationTab.FOR_YOU -> R.drawable.ic_tab_foryou
        StationTab.MOROCCO -> R.drawable.ic_tab_radio
        StationTab.WORLD -> R.drawable.ic_tab_world
        StationTab.QURAN -> R.drawable.ic_tab_quran
        StationTab.FAVORITES -> R.drawable.ic_tab_favorites
    }

    private fun layoutLabel(layout: DisplayLayout): String = when {
        television -> layout.label
        layout == DisplayLayout.GUIDE -> "Cockpit"
        layout == DisplayLayout.MOSAIC -> "Présélections"
        else -> layout.label
    }

    private val wideSheet get() = resources.configuration.screenWidthDp >= 600

    /** Settings panel: every option shows its current value, so the driver sees the state before tapping. */
    private fun showMainMenu() {
        data class Entry(val icon: Int, val title: String, val value: String?, val action: () -> Unit)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        val sound = RadioSound.profile(prefs).label + if (prefs.getBoolean(RadioSound.CLICKS, false)) " · clics" else ""
        val entries = buildList {
            add(Entry(R.drawable.ic_settings_display, "Affichage", layoutLabel(displayLayout), ::showDisplayMenu))
            if (dashboardInfo.value) add(Entry(R.drawable.ic_settings_info, infoLabel(), "Actualités · météo · salat", ::showTvInfo))
            add(Entry(R.drawable.ic_settings_theme, "Jour / Nuit", Appearance.restore(prefs).label, ::showAppearance))
            add(Entry(R.drawable.ic_settings_sound, "Effets sonores", sound, ::showSoundEffects))
            add(Entry(R.drawable.ic_radio_car_start, "Démarrage voiture", carResume().label, ::showCarStartup))
            add(Entry(R.drawable.ic_tab_favorites, "Boutons du volant",
                if (prefs.getBoolean(ListeningPrefs.WHEEL_PRESETS, false)) "Présélections seulement" else "Toute la liste",
                ::showWheelSettings))
            add(Entry(R.drawable.ic_settings_scores, "Scores en direct", "FotMob", ::showLiveScores))
            add(Entry(R.drawable.ic_settings_popup, "Pop-up des stations", "Notifications Android", ::configureStationPopup))
            add(Entry(R.drawable.ic_radio_update, "Mises à jour", version?.let { "Version $it" }, radioUpdates::show))
        }
        val sheet = RadioSheet(this, "Réglages", getString(R.string.app_name))
        sheet.grid(if (wideSheet) 2 else 1, entries.map { entry ->
            entry.title to {
                sheet.optionView(entry.title, entry.value, entry.icon, chevron = true) {
                    sheet.dismiss()
                    entry.action()
                }
            }
        })
        sheet.show()
    }

    private fun configureStationPopup() {
        StationPopup.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!prefs.getBoolean("station_popup_permission_requested", false) ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            prefs.edit().putBoolean("station_popup_permission_requested", true).apply()
            stationPopupPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        try {
            val notificationsEnabled = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
            val settings = if (notificationsEnabled) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, StationPopup.CHANNEL_ID)
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            }
            startActivity(settings.putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Réglages de notifications indisponibles", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLiveScores() {
        val pages = listOf(
            Triple("Matchs en direct", "Tous les championnats", "https://www.fotmob.com/?show=ongoing"),
            Triple("Botola Pro", "Calendrier et résultats", "https://www.fotmob.com/fr/leagues/530/fixtures/botola-pro"),
        )
        val sheet = RadioSheet(this, "Scores en direct", "FotMob")
        for ((title, detail, url) in pages) {
            sheet.option(title, detail, R.drawable.ic_settings_scores, chevron = true) {
                sheet.dismiss()
                val link = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
                try {
                    startActivity(link)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, "FotMob ou un navigateur est nécessaire pour ouvrir les scores.",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
        sheet.show()
    }

    private fun showSoundEffects() {
        val sheet = RadioSheet(this, "Effets sonores")
        val status = label("", 12, muted).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(dp(4), 0, 0, dp(4))
        }
        lateinit var clicks: View
        clicks = sheet.option("Clics de l’interface", "Son discret à chaque appui",
            selected = prefs.getBoolean(RadioSound.CLICKS, false)) {
            val enabled = !prefs.getBoolean(RadioSound.CLICKS, false)
            prefs.edit().putBoolean(RadioSound.CLICKS, enabled).apply()
            if (enabled) RadioSound.playClick(this, prefs)
            sheet.markSelected(clicks, enabled)
        }
        sheet.caption("ÉGALISEUR")
        val profileRows = mutableMapOf<SoundProfile, View>()
        sheet.grid(SoundProfile.entries.size, SoundProfile.entries.map { profile ->
            profile.label to {
                sheet.optionView(profile.label, selected = profile == RadioSound.profile(prefs)) {
                    prefs.edit().putString(RadioSound.PROFILE, profile.name).apply()
                    profileRows.forEach { (choice, row) -> sheet.markSelected(row, choice == profile) }
                    renderSoundStatus(controller?.sessionExtras ?: Bundle.EMPTY)
                }.also { profileRows[profile] = it }
            }
        })
        sheet.body.addView(status, LinearLayout.LayoutParams(-1, -2))
        soundStatusLabel = status
        renderSoundStatus(controller?.sessionExtras ?: Bundle.EMPTY)
        val panel = dolbyPanel
        val dolbyAllowed = panel != null && prefs.getBoolean(RadioSound.DOLBY, false)
        val dolby = sheet.option("Dolby", when {
            panel == null -> "Indisponible sur cet appareil"
            dolbyAllowed -> "ON · Autorisé dans la radio"
            else -> "OFF · Désactivé dans la radio"
        }, chevron = true) {
            sheet.dismiss()
            panel?.let(::showDolbySettings)
        }
        enable(dolby, panel != null)
        sheet.dialog.setOnDismissListener { soundStatusLabel = null }
        sheet.show()
    }

    private fun showDolbySettings(panel: DolbyPanel) {
        val sheet = RadioSheet(this, "Dolby")
        lateinit var off: View
        lateinit var on: View
        lateinit var settings: View
        fun allow(value: Boolean) {
            prefs.edit().putBoolean(RadioSound.DOLBY, value).apply()
            sheet.markSelected(off, !value)
            sheet.markSelected(on, value)
            enable(settings, value)
        }
        val enabled = prefs.getBoolean(RadioSound.DOLBY, false)
        off = sheet.option("OFF", "Désactivé", selected = !enabled) { allow(false) }
        on = sheet.option("ON", "Autorisé dans la radio", selected = enabled) { allow(true) }
        settings = sheet.option("Réglages Dolby", "Ouvre le panneau du système", chevron = true) {
            val sessionId = controller?.sessionExtras?.getInt(RadioSound.DOLBY_SESSION_ID) ?: 0
            runCatching { openDolbyPanel.launch(panel.settingsIntent(this, sessionId)) }.onFailure {
                Toast.makeText(this, "Réglages Dolby indisponibles", Toast.LENGTH_SHORT).show()
            }
        }
        enable(settings, enabled)
        sheet.show()
    }

    private fun renderSoundStatus(extras: Bundle) {
        val label = soundStatusLabel ?: return
        val selected = RadioSound.profile(prefs)
        label.text = when {
            selected == SoundProfile.NORMAL -> "Son d’origine"
            savedStation == Stations.luxe && controller?.currentMediaItem == null -> "Luxe Radio utilise son propre lecteur"
            else -> when (extras.getString(RadioSound.STATUS)) {
                RadioSound.Status.ACTIVE.name -> "Actif sur le lecteur intégré"
                RadioSound.Status.UNAVAILABLE.name -> "Égaliseur indisponible sur cet appareil"
                else -> "En attente de lecture"
            }
        }
    }

    private fun showDisplayMenu() {
        val layouts = DisplayLayout.entries
        val details = layouts.map {
            when (it) {
                DisplayLayout.GUIDE -> if (television) "Grille des stations et lecteur" else "Lecteur à gauche, stations à droite"
                DisplayLayout.MOSAIC -> if (television) "Grandes vignettes" else "Grandes tuiles de logos, lecteur en bas"
                DisplayLayout.LISTENING -> "Lecteur plein écran"
            }
        }
        showChoiceSheet(if (television) "Affichage TV" else "Affichage", layouts.map(::layoutLabel), displayLayout.ordinal,
            details = details) { index ->
            val layout = layouts[index]
            prefs.edit().putString(DISPLAY_LAYOUT, layout.name).apply()
            openLayout(layout)
        }
    }

    private fun openLayout(layout: DisplayLayout) {
        if (layout == displayLayout) return
        if (television) rememberTvFocus()
        displayLayout = layout
        if (carScreen) radioFavoritesOnly = false
        if (layout != DisplayLayout.LISTENING) {
            catalogueLayout = layout
            prefs.edit().putString(CATALOGUE_LAYOUT, layout.name).apply()
        }
        returnFromListening.isEnabled = !showingCatalogue
        if (::liveDot.isInitialized) liveDot.removeCallbacks(livePulse)
        livePulsing = false
        revealCatalogueSelection = true
        setContentView(screen())
        renderRows()
        restoreTvCatalogueFocus()
    }

    private fun stationAt(position: Int): RadioStation? = if (catalogueInGrid) {
        rows.getOrNull(position)
    } else {
        (listEntries.getOrNull(position) as? ListEntry.Station)?.radio
    }

    private fun rememberTvFocus() {
        if (showingCatalogue) stationAt(list.selectedItemPosition)?.let { catalogueFocusStation = it.url }
    }

    private fun cataloguePosition(stationId: String?): Int = if (stationId == null) -1 else
        (0 until catalogueAdapter.count).firstOrNull { stationAt(it)?.url == stationId } ?: -1

    private fun restoreTvCatalogueFocus() {
        if (!television) return
        if (!showingCatalogue) {
            if (!playButton.requestFocus()) catalogueButton.requestFocus()
            return
        }
        val position = cataloguePosition(catalogueFocusStation)
        if (position >= 0) {
            list.requestFocus()
            list.setSelection(position)
        } else {
            tabs[tab]?.requestFocus()
        }
    }

    private fun revealCatalogueStation(stationId: String): Boolean {
        val position = cataloguePosition(stationId)
        if (position < 0) return false
        val child = list.getChildAt(position - list.firstVisiblePosition)
        if (child == null || child.top < list.paddingTop || child.bottom > list.height - list.paddingBottom) {
            scrollCatalogueTo(position)
        }
        return true
    }

    /**
     * In touch mode GridView lays out setSelectionFromTop() from its invalid selected position (-1), drawing the
     * "load more" cell first and breaking later scrolling. Grids use setSelection() before their first layout,
     * which falls back to the requested position, and a short smooth scroll once they show rows.
     */
    private fun scrollCatalogueTo(position: Int) {
        when {
            television -> list.setSelection(position)
            list !is GridView -> list.setSelectionFromTop(position, 0)
            list.childCount == 0 -> list.setSelection(position)
            else -> list.smoothScrollToPositionFromTop(position, 0, 150)
        }
    }

    private fun wideColumns(left: View, right: View): LinearLayout = row().apply {
        val playerWeight = if (tightLandscape) .36f else .39f
        addView(left, LinearLayout.LayoutParams(0, -1, playerWeight).apply { marginEnd = dp(8) })
        addView(right, LinearLayout.LayoutParams(0, -1, 1f - playerWeight))
    }

    private fun categoryBar(): LinearLayout = row().apply {
        isBaselineAligned = false
        background = getDrawable(R.drawable.radio_category_line)
        val configuration = resources.configuration
        val inlineIcons = landscape
        val compactTabs = inlineIcons && configuration.screenWidthDp < 480
        val symbolSize = dp(if (compactTabs) 14 else 16)
        val maxTextSize = if (configuration.screenWidthDp >= 650) 14 else 13
        val tabFont = Typeface.create(Typeface.DEFAULT, 600, false)
        fun addCategory(title: String, icon: Int, weight: Float, action: () -> Unit): TextView {
            if (childCount > 0) addView(View(context).apply {
                setBackgroundColor(getColor(R.color.radio_list_rule))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(1), dp(22)).apply { gravity = Gravity.CENTER_VERTICAL })
            val caption = if (title == StationTab.MOROCCO.label && inlineIcons && configuration.screenWidthDp < 400)
                "Maroc" else title
            return button(caption, action).also { view ->
                if (caption != title) view.contentDescription = title
                view.typeface = tabFont
                view.maxLines = 1
                view.ellipsize = TextUtils.TruncateAt.END
                view.setAutoSizeTextTypeUniformWithConfiguration(10, maxTextSize, 1, TypedValue.COMPLEX_UNIT_SP)
                view.background = pressable(R.drawable.radio_tab)
                view.setPadding(dp(if (inlineIcons && !compactTabs) 8 else 2), dp(if (inlineIcons) 0 else 2), dp(2), dp(3))
                view.setTextColor(getColorStateList(R.color.radio_tab_text))
                val symbol = getDrawable(icon)?.apply { setBounds(0, 0, symbolSize, symbolSize) }
                if (inlineIcons) {
                    view.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    view.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    view.setCompoundDrawablesRelative(symbol, null, null, null)
                    view.compoundDrawablePadding = dp(if (compactTabs) 3 else 5)
                } else {
                    view.setCompoundDrawablesRelative(null, symbol, null, null)
                    view.includeFontPadding = false
                }
                view.compoundDrawableTintList = getColorStateList(R.color.radio_tab_icon)
                addView(view, LinearLayout.LayoutParams(0, -1, weight).apply {
                    marginStart = dp(if (compactTabs) 2 else 3)
                    marginEnd = dp(if (compactTabs) 2 else 3)
                    bottomMargin = dp(3)
                })
            }
        }
        tabs = StationTab.entries.associateWith { choice ->
            addCategory(choice.label, stationTabIcon(choice), if (choice == StationTab.MOROCCO) 1.3f else 1f) {
                selectTab(choice)
            }
        }
    }

    private fun stationList(grid: Boolean = false): View = column().apply {
        worldRegion = label("", if (television && !compactTv) 13 else 11, accent, bold = true).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        addView(worldRegion, LinearLayout.LayoutParams(-1, dp(22)))
        sortButton = button("Trier", ::chooseSort).apply {
            textSize = if (carScreen) 14f else 12f
            maxLines = 1
            setPadding(dp(if (shortLandscape) 6 else 10), 0, dp(if (shortLandscape) 6 else 10), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_radio_sort, 0, 0, 0)
            compoundDrawablePadding = dp(6)
        }
        categoryButton = button("Genres") {}.apply {
            textSize = if (carScreen) 14f else 12f
            maxLines = 1
            setPadding(dp(4), 0, dp(4), 0)
        }
        stationSearch = EditText(context).apply {
            if (tvGuide) id = View.generateViewId()
            hint = if (tightLandscape || touchGuide && resources.configuration.screenWidthDp < 700) "Chercher" else "Rechercher…"
            contentDescription = "Rechercher une station ou un récitateur"
            setSingleLine(true)
            textSize = if (television && !compactTv && !tvGuide) 17f else 14f
            setTextColor(textColor)
            setHintTextColor(muted)
            setText(query)
            background = getDrawable(R.drawable.radio_control)
            remoteFocus()
            val searchPadding = dp(if (tightLandscape) 8 else 12)
            setPadding(searchPadding, 0, searchPadding, 0)
            if (!tightLandscape) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_radio_search, 0, 0, 0)
                compoundDrawablePadding = dp(9)
            }
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
                windowInsetsController?.hide(WindowInsets.Type.ime())
                clearFocus()
                true
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun afterTextChanged(text: Editable?) = Unit
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                    query = text.toString()
                    updateDirectory(resetScroll = true)
                }
            })
        }
        radioFilter = transportButton(R.drawable.ic_tab_favorites, "Favoris") {
            radioFavoritesOnly = !radioFavoritesOnly
            updateDirectory(resetScroll = true)
        }.apply {
            imageTintList = ColorStateList.valueOf(accent)
        }
        countryButton = button("") { chooseContinent() }.apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8), 0, dp(6), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_tab_world, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(accent)
        }
        val toolbarHeight = dp(when {
            carScreen -> 48
            tvGuide -> 34
            compactTv -> 38
            tightLandscape -> 40
            landscape -> 44
            else -> 48
        })
        val stationToolbar = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(stationSearch, LinearLayout.LayoutParams(0, -1, 1f))
            addView(countryButton, LinearLayout.LayoutParams(dp(if (shortLandscape || carScreen) 84 else if (television && !compactTv) 128 else 104), -1)
                .apply { marginStart = dp(6) })
            if (!television && !carScreen) addView(radioFilter, LinearLayout.LayoutParams(toolbarHeight, -1).apply { marginStart = dp(6) })
            addView(sortButton, LinearLayout.LayoutParams(dp(if (carScreen) 86 else if (shortLandscape) 66
                else if (television && !compactTv) 92 else 76), -1)
                .apply { marginStart = dp(6) })
        }
        addView(stationToolbar, LinearLayout.LayoutParams(-1, toolbarHeight).apply { bottomMargin = dp(if (tvGuide) 4 else 6) })
        this@RadioActivity.stationToolbar = stationToolbar
        chipBar = row().apply { gravity = Gravity.CENTER_VERTICAL }
        chipScroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            addView(chipBar, ViewGroup.LayoutParams(-2, -1))
        }
        addView(chipScroller, LinearLayout.LayoutParams(-1, dp(if (carScreen) 42 else if (tvGuide) 34 else 38))
            .apply { bottomMargin = dp(if (tvGuide) 4 else 6) })
        addView(FrameLayout(context).apply {
            if (!grid || tvGuide) {
                background = getDrawable(R.drawable.radio_catalogue)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            empty = label("", 15, muted).apply { gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0) }
            val catalog = if (grid) GridView(context).apply {
                numColumns = when {
                    touchGuide -> 1
                    touchMosaic -> if (resources.configuration.screenWidthDp < 700) 3 else 4
                    tvGuide -> if (resources.configuration.screenWidthDp >= 1_250) 3 else 2
                    television -> if (resources.configuration.screenWidthDp < 760) 3 else 4
                    shortLandscape -> 2
                    landscape -> if (resources.configuration.screenWidthDp < 700) 3 else 4
                    else -> 2
                }
                horizontalSpacing = dp(if (tvGuide) 4 else 10)
                verticalSpacing = dp(if (touchGuide) 4 else if (tvGuide) 2 else 8)
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
                adapter = tileAdapter
            } else ListView(context).apply {
                divider = ColorDrawable(getColor(R.color.radio_list_rule))
                dividerHeight = dp(1)
                itemsCanFocus = !television
                adapter = stationAdapter
            }
            list = catalog.apply {
                id = View.generateViewId()
                isSoundEffectsEnabled = false
                if (television) {
                    setSelector(R.drawable.radio_focus)
                    setDrawSelectorOnTop(true)
                }
                if (grid || television || shortLandscape) {
                    setOnItemClickListener { _, _, position, _ ->
                        val station = stationAt(position)
                        if (station != null) {
                            RadioSound.playClick(this@RadioActivity, prefs)
                            if (television) catalogueFocusStation = station.url
                            select(station)
                        } else if (inlineDirectoryPage && position == catalogueAdapter.count - 1) {
                            RadioSound.playClick(this@RadioActivity, prefs)
                            directory.loadMore()
                        }
                    }
                }
                if (carScreen) {
                    setOnItemLongClickListener { _, _, position, _ ->
                        val station = stationAt(position) ?: return@setOnItemLongClickListener false
                        toggleFavorite(station)
                        val message = if (station.url in favorites) "Ajoutée aux favoris" else "Retirée des favoris"
                        Toast.makeText(this@RadioActivity, "${station.name} · $message", Toast.LENGTH_SHORT).show()
                        true
                    }
                }
                contentDescription = if (grid && !tvGuide) "Mosaïque des stations" else "Liste des stations"
            }
            addView(list, FrameLayout.LayoutParams(-1, -1))
            addView(empty, FrameLayout.LayoutParams(-1, -1))
            list.emptyView = empty
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        directoryAction = button("") { directory.loadMore() }
        addView(directoryAction, LinearLayout.LayoutParams(-1, dp(if (television) 48 else 44)).apply { topMargin = dp(4) })
    }

    private fun activeSort(): StationSort = StationSort.options(tab)
        .firstOrNull { it.name == prefs.getString("sort_${tab.name}", null) } ?: StationSort.DEFAULT

    private fun activeCategory(): MoroccoCategory = MoroccoCategory.restore(prefs.getString("morocco_category", null))

    private fun activeQuranSection(): QuranSection? =
        QuranSection.entries.firstOrNull { it.name == prefs.getString("quran_section", null) }

    private suspend fun rollNewsHeadlines() {
        combine(tvStationInfo, stationHeadlines) { info, headlines ->
            StationNews.bulletins(info?.publisher, headlines).ifEmpty { listOfNotNull(info?.fallback) }
        }
            .distinctUntilChanged().collectLatest { edition ->
                if (edition.isEmpty()) {
                    tickerBulletin = null
                    if (newsFooter?.hasFocus() == true) playButton.requestFocus()
                    updateNewsBarVisibility()
                    return@collectLatest
                }
                if (edition.size == 1 && edition.single().url == null) {
                    setTickerBulletin(edition.single())
                    return@collectLatest
                }
                var index = edition.indexOfFirst { it == tickerBulletin }.coerceAtLeast(0)
                while (true) {
                    delay(setTickerBulletin(edition[index]))
                    index = (index + 1) % edition.size
                }
            }
    }

    private fun setTickerBulletin(bulletin: NewsBulletin): Long {
        if (tickerBulletin == bulletin && bulletin.url == null) return tickerDuration
        tickerBulletin = bulletin
        updateNewsBarVisibility()
        tickerStartedAt = SystemClock.uptimeMillis()
        tickerDuration = if (bulletin.url == null || touchGuide) 0L else newsTicker?.durationFor(bulletin.text) ?: 8_000L
        tickerSource?.text = bulletin.source
        newsTicker?.showHeadline(bulletin.text, tickerStartedAt, tickerDuration)
        return if (touchGuide && bulletin.url != null) 10_000L else tickerDuration
    }

    private fun updateNewsBarVisibility() {
        newsFooter?.visibility = if (tickerBulletin != null && !typingInGuide) View.VISIBLE else View.GONE
    }

    private suspend fun refreshStationNews(publisher: NewsPublisher) {
        while (true) {
            val cached = stationNews[publisher]
            val edition = cached?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() } ?: run {
                val headlines = withContext(Dispatchers.IO) {
                    runCatching { StationNews.fetch(publisher) }.getOrDefault(emptyList())
                }
                val retryAt = SystemClock.elapsedRealtime() + if (headlines.isEmpty()) 180_000L else 900_000L
                NewsEdition(retryAt, headlines.ifEmpty { cached?.headlines.orEmpty() }).also { stationNews[publisher] = it }
            }
            stationHeadlines.value = edition.headlines
            delay((edition.expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(1L))
        }
    }

    private suspend fun refreshLocalTiles() {
        combine(localInfo.city, localInfo.weather, localInfo.prayerCalendars) { _, _, _ -> Unit }.collectLatest {
            while (true) {
                renderLocalTiles()
                delay(untilNextMinute())
            }
        }
    }

    private fun renderLocalTiles() {
        val city = localInfo.city.value
        val prayers = localInfo.prayerStatus(city)
        weatherTile?.bind(localInfo.weather.value, city)
        prayerTile?.bind(prayers, city)
        weatherChip?.bind(localInfo.weather.value, city)
        prayerChip?.bind(prayers, city)
    }

    private fun localInfoBar(): View = row().apply {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        weatherTile = WeatherTile(context, compactTv, ::showWeather).also {
            addView(it, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(5) })
        }
        prayerTile = PrayerTile(context, compactTv, ::showPrayerTimes).also {
            addView(it, LinearLayout.LayoutParams(0, -1, 1.55f))
        }
        renderLocalTiles()
    }

    private fun newsBar(): View = row().apply {
        newsFooter = this
        id = View.generateViewId()
        visibility = if (tickerBulletin == null) View.GONE else View.VISIBLE
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        gravity = Gravity.CENTER_VERTICAL
        background = pressable(R.drawable.radio_control)
        isFocusable = true
        contentDescription = "${infoLabel()} · Actualités, météo et salat"
        remoteFocus()
        setRadioClick(::showTvInfo)
        val source = label(tickerBulletin?.source.orEmpty(), if (compactGuide) 12 else 14,
            getColor(R.color.radio_news_badge), bold = true).apply {
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.radio_news_badge)
        }
        tickerSource = source
        addView(source, LinearLayout.LayoutParams(dp(if (compactGuide) 52 else 62), dp(if (compactGuide) 26 else 30))
            .apply { marginStart = dp(6) })
        val ticker = HeadlineTicker(context, if (touchGuide) 14 else if (compactTv) 16 else 19, textColor).apply {
            tickerBulletin?.let { showHeadline(it.text, tickerStartedAt, tickerDuration) }
        }
        newsTicker = ticker
        addView(ticker, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun infoLabel() = if (television) "Infos TV" else "Infos"

    private fun showTvInfo() {
        val publisher = tvStationInfo.value?.publisher?.label ?: "RFI"
        val sheet = RadioSheet(this, infoLabel(), localInfo.city.value.label)
        val entries = listOf(
            Triple("Actualités", publisher, ::showNews),
            Triple("Météo", localInfo.weather.value?.let { "${it.degrees}° · ${it.condition.label}" }, ::showWeather),
            Triple("Salat · مواقيت الصلاة", "Horaires du jour", ::showPrayerTimes),
            Triple("Ville", localInfo.city.value.label, ::chooseInfoCity),
        )
        sheet.grid(if (wideSheet) 2 else 1, entries.map { (title, detail, action) ->
            title to {
                sheet.optionView(title, detail, chevron = true) {
                    sheet.dismiss()
                    action()
                }
            }
        })
        sheet.show()
    }

    private fun chooseInfoCity() {
        val cities = RadioCity.entries
        showChoiceSheet("Ville", cities.map(RadioCity::label), localInfo.city.value.ordinal,
            subtitle = "Pour la météo et la salat", columns = if (wideSheet) 3 else 2) { index ->
            val city = cities[index]
            if (localInfo.choose(city)) {
                prefs.edit().putString("tv_info_city", city.name).apply()
                renderLocalTiles()
            }
        }
    }

    private fun showWeather() {
        val dialog = infoDialog("Météo · ${localInfo.city.value.label}", RadioLocalInfo.WEATHER_SITE)
        val request = lifecycleScope.launch {
            localInfo.weather.collect { report ->
                val text = if (report == null) "Météo indisponible pour le moment." else {
                    val limits = if (report.low != null && report.high != null) {
                        "\nMin. ${report.low} °C · Max. ${report.high} °C"
                    } else ""
                    "${report.degrees} °C · ${report.condition.label}$limits\nPrévision à ${report.at.toLocalTime()} (GMT)"
                }
                dialog.setMessage("$text\n\nDonnées Open-Meteo · CC BY 4.0")
            }
        }
        dialog.setOnDismissListener { request.cancel() }
    }

    private fun showPrayerTimes() {
        val city = localInfo.city.value
        val dialog = infoDialog("Salat · ${city.label}", RadioLocalInfo.HABOUS_SITE)
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.FRENCH)
        val request = lifecycleScope.launch {
            localInfo.prayerCalendars.collectLatest {
                while (true) {
                    val status = localInfo.prayerStatus(city)
                    val prayers = localInfo.prayerTimes(city, status.date)
                    val text = if (prayers.isEmpty()) "Horaires indisponibles pour cette date." else {
                        val hours = prayers.joinToString("\n") {
                            "${it.prayer.label}  ·  ${it.dateTime.toLocalTime()}  ·  ${it.prayer.arabic}"
                        }
                        "${dateFormat.format(status.date)}\n" +
                            "Maintenant · ${prayerDescription(status.current, status.date)}\n" +
                            "Ensuite · ${prayerDescription(status.next, status.date)}\n\n$hours"
                    }
                    dialog.setMessage("$text\n\nMinistère des Habous et des Affaires islamiques")
                    delay(untilNextMinute())
                }
            }
        }
        dialog.setOnDismissListener { request.cancel() }
    }

    private fun infoDialog(title: String, source: String): AlertDialog =
        AlertDialog.Builder(this).setTitle(title).setMessage("Chargement…")
            .setNeutralButton("Source") { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, source.toUri())) }
                    .onFailure { Toast.makeText(this, "Navigateur indisponible", Toast.LENGTH_SHORT).show() }
            }.setPositiveButton("Fermer", null).show().apply {
                window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
            }

    private fun showNews() = showNewsDialog(tvStationInfo.value?.publisher, stationHeadlines, artwork, compactTv)

    private fun chooseSort() {
        val options = StationSort.options(tab)
        showChoiceSheet("Trier les stations", options.map { it.labelFor(tab) }, options.indexOf(activeSort()),
            subtitle = tab.label, columns = if (wideSheet) 2 else 1) { index ->
            prefs.edit().putString("sort_${tab.name}", options[index].name).apply()
            updateDirectory(resetScroll = true)
        }
    }

    private fun chooseContinent() {
        val dialog = AlertDialog.Builder(this).setTitle("Choisir un continent").setNegativeButton("Fermer", null).create()
        val selected = WorldContinent.forCountry(country)
        val content = column().apply {
            setPadding(dp(16), dp(8), dp(16), 0)
            WorldContinent.entries.chunked(3).forEach { choices ->
                addView(row().apply {
                    isBaselineAligned = false
                    choices.forEach { continent ->
                        addView(button(continent.label) {
                            dialog.dismiss()
                            chooseCountry(continent)
                        }.apply {
                            textSize = 13f
                            maxLines = 2
                            isSelected = continent == selected
                            if (isSelected) background = pressable(R.drawable.radio_selected)
                        }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(4) })
                    }
                }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
            }
        }
        dialog.setView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
    }

    private fun chooseCountry(continent: WorldContinent) {
        val countries = StationDirectory.countries.filter { it != "MA" && it in continent.countryCodes }
        var shown = countries
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        val dialog = AlertDialog.Builder(this).setTitle("Pays · ${continent.label}")
            .setNegativeButton("Continents") { _, _ -> chooseContinent() }.create()
        lateinit var countryList: ListView
        val content = column().apply {
            setPadding(dp(16), 0, dp(16), 0)
            val input = EditText(context).apply {
                hint = "Rechercher un pays"
                setSingleLine(true)
                remoteFocus()
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                setOnEditorActionListener { _, action, _ ->
                    if (action != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
                    dialog.window?.insetsController?.hide(WindowInsets.Type.ime())
                    clearFocus()
                    true
                }
            }
            addView(input, LinearLayout.LayoutParams(-1, dp(48)))
            countryList = ListView(context).apply {
                this.adapter = adapter
                if (television) setSelector(R.drawable.radio_focus)
                setOnItemClickListener { _, _, position, _ ->
                    dialog.dismiss()
                    chooseWorldCountry(shown[position])
                }
            }
            addView(countryList, LinearLayout.LayoutParams(-1, dp(200)))
            adapter.addAll(shown.map(StationDirectory::countryName))
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun afterTextChanged(text: Editable?) = Unit
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                    val key = RadioCatalogue.searchKey(text.toString())
                    shown = countries.filter {
                        RadioCatalogue.searchKey(StationDirectory.countryName(it)).contains(key) || it.lowercase() == key
                    }
                    adapter.clear()
                    adapter.addAll(shown.map(StationDirectory::countryName))
                }
            })
        }
        dialog.setView(content)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
        if (television) countryList.requestFocus()
    }

    private fun stationPanel(wide: Boolean, mini: Boolean = false, full: Boolean = false): View {
        val dashboardPlayer = wide && !television && !touchGuide && !mini && !full
        val horizontalMini = mini && (television || landscape)
        val splitListening = full && landscape && !television
        val compactSidePlayer = wide && shortLandscape && !full
        val tightSidePlayer = compactSidePlayer && tightLandscape && !touchGuide
        val tightListening = splitListening && tightLandscape
        val stackedPresentation = wide && !tightSidePlayer && !tightListening
        return (if (horizontalMini || splitListening) row() else column()).apply {
            playerCategory = null
            weatherTile = null
            prayerTile = null
            background = getDrawable(R.drawable.radio_player)
            if (mini) gravity = Gravity.CENTER_VERTICAL
            if (full) gravity = Gravity.CENTER
            val padding = dp(if (shortLandscape) 7 else if (compactTv) 10 else if (television) 16 else 12)
            val topPadding = if (mini || compactSidePlayer) 3 else if (television && !compactTv) 12 else 8
            val bottomPadding = if (mini || compactSidePlayer) 3 else if (television && !compactTv) 14 else 8
            setPadding(padding, dp(topPadding), padding, dp(bottomPadding))
            val compactLandscape = compactTv || (wide && resources.configuration.screenHeightDp < 420)
            val listeningArtwork = if (full) {
                val height = resources.configuration.screenHeightDp
                when {
                    television -> (height - if (compactTv) 298 else 365).coerceIn(48, 140)
                    tightListening -> 42
                    splitListening -> (height / 3).coerceIn(48, 144)
                    else -> (height / 4).coerceIn(75, 176)
                }
            } else 0
            val artwork = StationArtwork(if (wide) 26 else 16, showCredits = true)
            playerArtwork = artwork
            val artworkSize = dp(when {
                full -> listeningArtwork
                tightSidePlayer -> 26
                !wide -> 48
                compactTv -> 46
                tvGuide -> 68
                compactSidePlayer -> 46
                dashboardPlayer && compactLandscape -> 66
                compactLandscape -> 54
                television -> 92
                else -> 96
            })
            val motifHeight = dp(when {
                full -> listeningArtwork + 8
                tightSidePlayer -> 33
                !wide -> 56
                compactTv -> 54
                tvGuide -> 94
                compactSidePlayer -> 55
                dashboardPlayer && compactLandscape -> 78
                compactLandscape -> 72
                television -> 130
                else -> 122
            })
            val audioStrip = if (dashboardInfo.value) dp(if (compactGuide) 17 else 21) else 0
            val frameHeight = motifHeight + audioStrip
            val artworkFrame = FrameLayout(context).apply {
                audioMotif = PremiumRadioMotif(context, artwork, belowLogo = dashboardInfo.value)
                if (dashboardInfo.value) {
                    addView(FrameLayout(context).apply {
                        addView(artwork, FrameLayout.LayoutParams(artworkSize, artworkSize, Gravity.CENTER))
                    }, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = audioStrip })
                    addView(audioMotif, FrameLayout.LayoutParams(-1, audioStrip, Gravity.BOTTOM))
                } else {
                    addView(audioMotif, FrameLayout.LayoutParams(-1, -1))
                    addView(artwork, FrameLayout.LayoutParams(artworkSize, artworkSize, Gravity.CENTER))
                }
            }
            val presentation = (if (stackedPresentation) playerColumn(artworkFrame, artwork, frameHeight, artworkSize, audioStrip)
                else row()).apply {
                playerPanel = this
                gravity = if (stackedPresentation) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
                if (stackedPresentation && !compactSidePlayer) addView(label("", if (television && !compactTv) 13 else 11, accent, bold = true).apply {
                    gravity = Gravity.CENTER
                    letterSpacing = .08f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    playerCategory = this
                }, LinearLayout.LayoutParams(-1, -2))
                if (dashboardInfo.value && showDashboardTiles && !mini) {
                    addView(localInfoBar(), LinearLayout.LayoutParams(-1, dp(if (compactTv) 56 else 64)).apply {
                        topMargin = dp(4)
                    })
                }
                addView(artworkFrame, LinearLayout.LayoutParams(when {
                    stackedPresentation -> -1
                    tightSidePlayer -> dp(35)
                    tightListening -> dp(52)
                    else -> dp(76)
                }, frameHeight).apply {
                    if (stackedPresentation) {
                        topMargin = dp(if (compactSidePlayer) 2 else if (compactLandscape) 4 else 5)
                        bottomMargin = dp(if (compactSidePlayer) 2 else if (compactLandscape) 4 else 6)
                    } else marginEnd = dp(if (tightSidePlayer) 3 else 10)
                })
                val alignment = if (stackedPresentation) View.TEXT_ALIGNMENT_CENTER else View.TEXT_ALIGNMENT_VIEW_START
                addView(column().apply {
                    val titleSize = when {
                        tightListening -> 18
                        full -> if (shortLandscape) 23 else if (compactTv || !television) 26 else 36
                        !wide -> 17
                        tightSidePlayer -> 13
                        compactSidePlayer -> 16
                        tvGuide -> if (compactTv) 16 else 20
                        compactLandscape -> 19
                        television -> 25
                        else -> 21
                    }
                    playerTitle = label("", titleSize, textColor, bold = true).apply {
                        textAlignment = alignment
                        if (compactSidePlayer) includeFontPadding = false
                        maxLines = if ((compactTv && !tvGuide) || mini || shortTouchGuide ||
                            (full && (television || shortLandscape))) 1 else 2
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    addView(playerTitle, LinearLayout.LayoutParams(-1, -2))
                    val subtitleSize = if (tightSidePlayer) 11 else if (television && !compactTv) 16 else 13
                    playerSubtitle = label("", subtitleSize, muted).apply {
                        textAlignment = alignment
                        if (compactSidePlayer) includeFontPadding = false
                        maxLines = if (tightSidePlayer || tightListening || mini || compactTv || shortTouchGuide) 1 else 2
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    addView(playerSubtitle, LinearLayout.LayoutParams(-1, -2))
                }, if (stackedPresentation) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
            }
            val presentationSize = when {
                horizontalMini || splitListening -> LinearLayout.LayoutParams(0, -2, 1f)
                wide -> LinearLayout.LayoutParams(-1, 0, 1f)
                else -> LinearLayout.LayoutParams(-1, -2)
            }
            addView(presentation, presentationSize)
            val controlsWidth = when {
                splitListening -> dp(minOf(310, resources.configuration.screenWidthDp / 2))
                horizontalMini -> dp(if (compactTv) 280 else if (television) 380 else 300)
                full -> dp(minOf(310, resources.configuration.screenWidthDp - 44))
                else -> -1
            }
            addView(playbackBar(wide, full, mini), LinearLayout.LayoutParams(controlsWidth, -2).apply {
                if (splitListening) marginStart = dp(8)
                else if (!horizontalMini && (mini || full)) topMargin = dp(6)
            })
        }
    }

    /**
     * Cockpit player for car screens: logo and station side by side, the station headlines,
     * then large round controls a driver can reach without looking twice.
     */
    private fun cockpitPanel(): View = column().apply {
        playerCategory = null
        weatherTile = null
        prayerTile = null
        background = getDrawable(R.drawable.radio_player)
        val short = shortTouchGuide
        setPadding(dp(14), dp(if (short) 8 else 14), dp(14), dp(if (short) 8 else 12))
        val logoSize = dp(if (short) 78 else 128)
        val strip = dp(if (short) 14 else 18)
        val artwork = StationArtwork(26, showCredits = true)
        playerArtwork = artwork
        audioMotif = PremiumRadioMotif(context, artwork, belowLogo = true)
        val hero = row().apply {
            playerPanel = this
            gravity = Gravity.CENTER_VERTICAL
            addView(FrameLayout(context).apply {
                addView(artwork, FrameLayout.LayoutParams(logoSize, logoSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
                addView(audioMotif, FrameLayout.LayoutParams(-1, strip, Gravity.BOTTOM))
            }, LinearLayout.LayoutParams(logoSize, logoSize + strip + dp(4)).apply { marginEnd = dp(14) })
            addView(column().apply {
                addView(label("", 12, accent, bold = true).apply {
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    letterSpacing = .06f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    playerCategory = this
                }, LinearLayout.LayoutParams(-1, -2))
                playerTitle = label("", if (short) 20 else 25, textColor, bold = true).apply {
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    maxLines = if (short) 1 else 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                addView(playerTitle, LinearLayout.LayoutParams(-1, -2))
                playerSubtitle = label("", 13, muted).apply {
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    maxLines = if (short) 1 else 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                addView(playerSubtitle, LinearLayout.LayoutParams(-1, -2))
                statusBadge = statusBadgeView(wide = false, mini = true).apply {
                    minimumHeight = dp(28)
                    playerStatus.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
                addView(statusBadge, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(if (short) 4 else 8) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        // Spare height goes mostly below the station so the controls stay anchored at the bottom.
        addView(View(context), LinearLayout.LayoutParams(-1, 0, .35f))
        addView(hero, LinearLayout.LayoutParams(-1, -2))
        addView(View(context), LinearLayout.LayoutParams(-1, 0, .65f))
        val ticker = newsBar()
        addView(ticker, LinearLayout.LayoutParams(-1, dp(if (short) 38 else 50)).apply {
            topMargin = dp(6)
            bottomMargin = dp(if (short) 6 else 10)
        })
        val side = dp(if (short) 48 else 54)
        val main = dp(if (short) 58 else 66)
        addView(row().apply {
            gravity = Gravity.CENTER
            previous = transportButton(R.drawable.ic_radio_previous, "Station précédente") { skip(-1) }
            playButton = transportButton(R.drawable.ic_radio_play, "Écouter", primary = true) { toggle() }
            next = transportButton(R.drawable.ic_radio_next, "Station suivante") { skip(1) }
            val favoriteButton = transportButton(R.drawable.ic_tab_favorites, "Favori") {
                val current = findStation(controller?.currentMediaItem?.mediaId) ?: savedStation
                current?.let(::toggleFavorite)
            }
            playerFavorite = favoriteButton
            listOf(previous, next, favoriteButton).forEach { roundControl(it) }
            roundControl(playButton, primary = true)
            val gap = dp(if (short) 12 else 16)
            addView(previous, LinearLayout.LayoutParams(side, side))
            addView(playButton, LinearLayout.LayoutParams(main, main).apply { marginStart = gap; marginEnd = gap })
            addView(next, LinearLayout.LayoutParams(side, side))
            addView(favoriteButton, LinearLayout.LayoutParams(side, side).apply { marginStart = gap })
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun playerColumn(
        frame: FrameLayout, artwork: View, preferredHeight: Int, preferredSize: Int, audioStrip: Int,
    ): LinearLayout =
        object : LinearLayout(this) {
            init { orientation = VERTICAL }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val frameParams = frame.layoutParams as LayoutParams
                var availableHeight = preferredHeight
                if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    // Reserve the title and metadata before deciding how much space the logo can use.
                    val naturalHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    var reserved = paddingTop + paddingBottom + frameParams.topMargin + frameParams.bottomMargin
                    for (index in 0 until childCount) {
                        val child = getChildAt(index)
                        if (child === frame || child.visibility == GONE) continue
                        measureChildWithMargins(child, widthMeasureSpec, 0, naturalHeight, 0)
                        val params = child.layoutParams as LayoutParams
                        reserved += child.measuredHeight + params.topMargin + params.bottomMargin
                    }
                    availableHeight = (MeasureSpec.getSize(heightMeasureSpec) - reserved).coerceIn(0, preferredHeight)
                }
                frameParams.height = availableHeight
                val size = minOf(preferredSize, (availableHeight - audioStrip).coerceAtLeast(0))
                artwork.layoutParams.apply {
                    width = size
                    height = size
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }

    /** Status line under the station name: the LIVE pill while on air, otherwise a short message. */
    private fun statusBadgeView(wide: Boolean, mini: Boolean): LinearLayout {
        playerStatus = label("", 13, muted, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        return row().apply {
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.radio_status)
            setPadding(dp(6), dp(if (wide) 0 else 4), dp(6), dp(if (wide) 0 else 4))
            liveIndicator = row().apply {
                gravity = Gravity.CENTER_VERTICAL
                visibility = View.GONE
                val lineColor = getColor(R.color.radio_live_rule)
                fun line() = View(context).apply {
                    setBackgroundColor(lineColor)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                if (!mini) addView(line(), LinearLayout.LayoutParams(dp(20), dp(1)))
                val badge = row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = getDrawable(R.drawable.radio_live)
                    setPadding(dp(7), 0, dp(7), 0)
                    contentDescription = "Radio en direct"
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    liveDot = View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.WHITE)
                        }
                    }
                    addView(liveDot, LinearLayout.LayoutParams(dp(5), dp(5)).apply { marginEnd = dp(5) })
                    addView(label("LIVE", 12, Color.WHITE, bold = true).apply {
                        gravity = Gravity.CENTER
                        letterSpacing = .06f
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(-2, -1))
                }
                addView(badge, LinearLayout.LayoutParams(-2, dp(22)).apply {
                    if (!mini) {
                        marginStart = dp(7)
                        marginEnd = dp(7)
                    }
                })
                if (!mini) addView(line(), LinearLayout.LayoutParams(dp(20), dp(1)))
            }
            addView(liveIndicator, LinearLayout.LayoutParams(-2, -2))
            addView(playerStatus, LinearLayout.LayoutParams(-2, -2))
        }
    }

    private fun playbackBar(wide: Boolean, full: Boolean, mini: Boolean): View = (if (wide) column() else row()).apply {
        gravity = Gravity.CENTER
        statusBadge = statusBadgeView(wide, mini)
        // Grow with two-line messages such as "Connexion perdue · reprise automatique" instead of clipping them.
        if (wide) statusBadge.minimumHeight = dp(if (compactGuide) 28 else 33)
        addView(statusBadge, if (wide) LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) }
            else LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        if (wide && !television && !touchGuide && !mini && !full) {
            addView(View(context).apply {
                setBackgroundColor(getColor(R.color.radio_list_rule))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(8); marginEnd = dp(8) })
        }
        val roundControls = touchMosaic && !wide
        val controlHeight = dp(when {
            roundControls -> 50
            smallTouchGuide -> 44
            touchGuide -> 48
            shortLandscape -> 42
            compactTv || !wide -> 44
            television -> 54
            else -> 48
        })
        addView(row().apply {
            gravity = Gravity.CENTER
            previous = transportButton(R.drawable.ic_radio_previous, "Station précédente") { skip(-1) }
            playButton = transportButton(R.drawable.ic_radio_play, "Écouter", primary = true) { toggle() }
            next = transportButton(R.drawable.ic_radio_next, "Station suivante") { skip(1) }
            if (roundControls) listOf(previous, next).forEach { roundControl(it) }
            if (roundControls) roundControl(playButton, primary = true)
            fun controlParams(width: Int, weight: Float = 1f) = if (roundControls) {
                LinearLayout.LayoutParams(controlHeight, controlHeight)
            } else if (wide && (!full || !television)) {
                LinearLayout.LayoutParams(0, controlHeight, weight)
            } else LinearLayout.LayoutParams(width, controlHeight)
            addView(previous, controlParams(controlHeight))
            addView(playButton, (if (roundControls) LinearLayout.LayoutParams(controlHeight + dp(8), controlHeight + dp(8))
                else controlParams(dp(if (television && wide) 66 else 56), 1.18f)).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            })
            addView(next, controlParams(controlHeight))
            if (television || full || mini || wide) {
                val favoriteButton = transportButton(R.drawable.ic_tab_favorites, "Favori") {
                    val current = findStation(controller?.currentMediaItem?.mediaId) ?: savedStation
                    current?.let(::toggleFavorite)
                }
                playerFavorite = favoriteButton
                if (roundControls) roundControl(favoriteButton)
                addView(favoriteButton, controlParams(controlHeight).apply { marginStart = dp(6) })
            }
        }, LinearLayout.LayoutParams(if (wide) -1 else -2, if (roundControls) -2 else controlHeight))
    }

    /** Circular car control; the primary one is filled so play/pause stands out at a glance. */
    private fun roundControl(button: ImageButton, primary: Boolean = false) {
        button.background = RippleDrawable(ripple,
            getDrawable(if (primary) R.drawable.radio_round_primary else R.drawable.radio_round), null)
        if (primary) button.imageTintList = ColorStateList.valueOf(getColor(R.color.radio_on_primary))
    }

    private fun transportButton(icon: Int, description: String, primary: Boolean = false, action: () -> Unit) =
        ImageButton(this).apply {
            id = View.generateViewId()
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(if (primary) accent else textColor)
            scaleType = ImageView.ScaleType.CENTER
            background = pressable()
            contentDescription = description
            isFocusable = true
            remoteFocus()
            setRadioClick(action)
        }

    private sealed interface ListEntry {
        data class Heading(val title: String, val rightAligned: Boolean) : ListEntry
        data class Station(val radio: RadioStation, val number: Int) : ListEntry
        data object DirectoryPage : ListEntry
    }

    private val stationAdapter = object : BaseAdapter() {
        override fun getCount(): Int = listEntries.size
        override fun getItem(position: Int): ListEntry = listEntries[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun areAllItemsEnabled(): Boolean = false
        override fun isEnabled(position: Int): Boolean = when (getItem(position)) {
            is ListEntry.Heading -> false
            is ListEntry.Station -> true
            ListEntry.DirectoryPage -> !directory.loading
        }
        override fun getViewTypeCount(): Int = 3
        override fun getItemViewType(position: Int): Int = when (getItem(position)) {
            is ListEntry.Station -> 0
            is ListEntry.Heading -> 1
            ListEntry.DirectoryPage -> 2
        }
        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View = when (val item = getItem(position)) {
            is ListEntry.Heading -> (recycled as? TextView ?: label("", 14, accent, bold = true).apply {
                layoutParams = AbsListView.LayoutParams(-1, dp(if (television) 36 else 32))
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPaddingRelative(dp(12), 0, dp(12), 0)
                isAccessibilityHeading = true
            }).apply {
                text = item.title
                layoutDirection = if (item.rightAligned) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            }
            is ListEntry.Station -> (recycled as? StationLine ?: StationLine(parent.context)).apply {
                bind(item.radio, item.number)
            }
            ListEntry.DirectoryPage -> directoryPageView(recycled)
        }
    }

    private val tileAdapter = object : BaseAdapter() {
        override fun getCount() = rows.size + if (inlineDirectoryPage) 1 else 0
        override fun getItem(position: Int) = rows.getOrNull(position)
        override fun getItemId(position: Int) = position.toLong()
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = getItem(position) != null || !directory.loading
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (getItem(position) == null) 1 else 0
        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
            val station = getItem(position) ?: return directoryPageView(recycled)
            return (recycled as? StationTile ?: StationTile()).apply { bind(station) }
        }
    }

    private fun directoryPageView(recycled: View?): View =
        (recycled as? TextView ?: label("", 13, accent, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(6), 0, dp(6), 0)
            layoutParams = AbsListView.LayoutParams(-1, dp(if (tvGuide) tvGuideRowDp else 48))
            background = pressable(R.drawable.radio_control)
        }).apply {
            text = directoryPageLabel
            enable(this, !directory.loading)
        }

    private inner class StationTile : LinearLayout(this@RadioActivity) {
        private val heightDp = when {
            tvGuide -> tvGuideRowDp
            touchMosaic -> if (shortLandscape) 92 else 108
            compactTv -> (resources.configuration.screenHeightDp * .16).toInt().coerceIn(57, 66)
            television -> (resources.configuration.screenHeightDp * .18).toInt().coerceIn(72, 134)
            shortLandscape -> 66
            else -> 96
        }
        private val logo = StationArtwork(18)
        private val idleBackground = pressable(if (tvGuide) R.drawable.radio_station_rule else R.drawable.radio_control)
        private val selectedBackground = pressable(R.drawable.radio_selected)
        private val name = label("", when {
            touchGuide -> 15
            touchMosaic -> 14
            tvGuide && !compactTv -> 14
            tvGuide || compactTv || shortLandscape -> 13
            television -> 17
            else -> 15
        }, textColor, bold = true).apply {
            gravity = if (tvGuide) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            includeFontPadding = false
            // One line in every guide: a second line pushed the subtitle out of the fixed-height row.
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val subtitle = label("", when {
            carScreen -> 12
            tvGuide && compactTv -> 9
            tvGuide || compactTv || shortLandscape -> 10
            else -> 12
        }, muted).apply {
            gravity = if (tvGuide) Gravity.START else Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val playingMark = if (tvGuide) ImageView(context).apply {
            setImageResource(R.drawable.ic_radio_play)
            imageTintList = ColorStateList.valueOf(accent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.INVISIBLE
        } else null
        private val favoriteMark = if (touchGuide) ImageView(context).apply {
            setImageResource(R.drawable.ic_radio_favorite_filled)
            imageTintList = ColorStateList.valueOf(amber)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        } else null

        init {
            layoutParams = AbsListView.LayoutParams(-1, dp(heightDp))
            background = idleBackground
            if (tvGuide) {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(7), 0, dp(7), 0)
                val artSize = dp((heightDp - 6).coerceAtMost(42))
                addView(logo, LinearLayout.LayoutParams(artSize, artSize).apply { marginEnd = dp(8) })
                addView(column().apply {
                    addView(name, LinearLayout.LayoutParams(-1, -2))
                    addView(subtitle, LinearLayout.LayoutParams(-1, -2))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val markSize = dp(if (touchGuide) 16 else 14)
                favoriteMark?.let { addView(it, LinearLayout.LayoutParams(markSize, markSize).apply { marginStart = dp(4) }) }
                playingMark?.let { addView(it, LinearLayout.LayoutParams(markSize, markSize).apply { marginStart = dp(4) }) }
            } else {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(3), dp(5), dp(3))
                val artSize = dp((heightDp - if (compactTv || shortLandscape) 37 else 47).coerceIn(20, 68))
                addView(logo, LinearLayout.LayoutParams(artSize, artSize).apply { bottomMargin = dp(2) })
                addView(name, LinearLayout.LayoutParams(-1, -2))
                addView(subtitle, LinearLayout.LayoutParams(-1, -2))
            }
        }

        fun bind(station: RadioStation) {
            val selected = station.url == highlighted?.first
            val playing = selected && highlighted?.second == true
            val tileBackground = if (selected) selectedBackground else idleBackground
            if (background !== tileBackground) background = tileBackground
            contentDescription = "Écouter ${station.name}"
            val favorite = touchGuide && station.url in favorites
            val playbackState = if (playing) "En écoute" else if (selected) "Sélectionnée" else null
            stateDescription = if (favorite) listOfNotNull(playbackState, "Favori").joinToString(" · ") else playbackState
            favoriteMark?.visibility = if (favorite) View.VISIBLE else View.GONE
            if (tvGuide) layoutDirection = directionOf(station.name)
            logo.bind(station)
            name.text = station.name
            name.setTextColor(if (selected) accent else textColor)
            subtitle.text = rowNotes[station.url] ?: when {
                tvGuide -> Stations.listDescription(station).orEmpty()
                playing -> "En écoute"
                else -> Stations.subtitle(station)
            }
            subtitle.visibility = if (subtitle.text.isEmpty()) View.GONE else View.VISIBLE
            playingMark?.visibility = if (playing) View.VISIBLE else View.INVISIBLE
        }
    }

    private inner class StationLine(context: Context) : LinearLayout(context) {
        private val idleBackground = RippleDrawable(ripple, null, ColorDrawable(Color.WHITE))
        private val selectedBackground = pressable(R.drawable.radio_selected)
        private val number = label("", 11, muted, bold = true).apply {
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val playingIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_radio_play)
            imageTintList = ColorStateList.valueOf(accent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val selectionMark = View(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(accent)
            }
        }
        private val badge = StationArtwork(if (television && !compactTv) 17 else 12)
        private val name = label("", if (television && !compactTv) 21 else 17, textColor, bold = true).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        private val subtitle = label("", if (television && !compactTv) 15 else 12, muted).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val star = if (television) null else ImageButton(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            background = pressable(R.drawable.radio_tab)
            isFocusable = true
        }

        init {
            layoutParams = AbsListView.LayoutParams(-1, -2)
            minimumHeight = dp(if (television && !compactTv) 66 else 54)
            gravity = Gravity.CENTER_VERTICAL
            background = idleBackground
            addView(FrameLayout(context).apply {
                addView(selectionMark, FrameLayout.LayoutParams(dp(2), dp(20), Gravity.START or Gravity.CENTER_VERTICAL)
                    .apply { marginStart = dp(2) })
                addView(number, FrameLayout.LayoutParams(-1, -1))
                addView(playingIcon, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(38), dp(44)))
            val largeArt = television && !compactTv
            val logoSize = dp(if (largeArt) 54 else 44)
            addView(badge, LinearLayout.LayoutParams(logoSize, logoSize).apply {
                marginEnd = dp(if (largeArt) 12 else 9)
            })
            addView(column().apply { addView(name); addView(subtitle) }, LinearLayout.LayoutParams(0, -2, 1f))
            if (television) {
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_radio_play)
                    imageTintList = ColorStateList.valueOf(muted)
                    scaleType = ImageView.ScaleType.CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
            } else addView(star, LinearLayout.LayoutParams(dp(48), dp(48)))
        }

        fun bind(station: RadioStation, position: Int) {
            val selected = station.url == highlighted?.first
            val playing = selected && highlighted?.second == true
            val rowBackground = if (selected) selectedBackground else idleBackground
            if (background !== rowBackground) background = rowBackground
            layoutDirection = directionOf(station.name)
            selectionMark.visibility = if (selected) View.VISIBLE else View.GONE
            contentDescription = "Écouter ${station.name}"
            stateDescription = if (playing) "En écoute" else if (selected) "Sélectionnée" else null
            if (!television) setRadioClick { select(station) }
            number.text = position.toString().padStart(2, '0')
            number.visibility = if (playing) View.INVISIBLE else View.VISIBLE
            number.setTextColor(if (selected) accent else muted)
            playingIcon.visibility = if (playing) View.VISIBLE else View.GONE
            badge.bind(station)
            name.text = station.name
            name.setTextColor(if (selected) accent else textColor)
            subtitle.text = rowNotes[station.url] ?: Stations.subtitle(station)
            subtitle.setTextColor(if (playing) accent else muted)
            star?.let {
                bindFavorite(it, station)
                it.setRadioClick { toggleFavorite(station) }
            }
        }
    }

    private inner class StationArtwork(textSize: Int, private val showCredits: Boolean = false) : FrameLayout(this@RadioActivity) {
        private val initials = label("FM", textSize, accent, bold = true).apply { gravity = Gravity.CENTER }
        private val logo = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.INVISIBLE }
        private var logoWork: Job? = null
        private var station: RadioStation? = null
        private var artworkGeneration = -1
        private var requestedPixels = 0

        private val targetPixels get() = (minOf(width, height) - dp(6)).coerceAtLeast(0)

        // Logo and initials swap with INVISIBLE, never GONE: a cached logo can arrive during a list layout pass,
        // when AbsListView drops layout requests, and a view coming back from GONE would then stay 0 × 0.
        init {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            if (showCredits) remoteFocus()
            addView(initials, LayoutParams(-1, -1))
            addView(logo, LayoutParams(-1, -1).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }

        fun bind(station: RadioStation?) {
            if (this.station == station && artworkGeneration == artwork.generation) {
                if (requestedPixels != targetPixels) loadArtwork()
                return
            }
            this.station = station
            artworkGeneration = artwork.generation
            logoWork?.cancel()
            requestedPixels = 0
            initials.text = when {
                station == null -> "FM"
                Stations.isReciter(station) -> "ق"
                else -> station.name.split(' ').take(2).joinToString("") { it.take(1) }
            }
            initials.visibility = View.VISIBLE
            logo.visibility = View.INVISIBLE
            logo.setImageDrawable(null)
            val portrait = station?.let(ReciterPortraits::forStation)
            bindCredit(station, portrait)
            if (station == null || Stations.isReciter(station)) {
                logo.setImageResource(if (station == null) R.drawable.ic_radio else R.drawable.ic_tab_quran)
                logo.visibility = View.VISIBLE
                initials.visibility = View.INVISIBLE
            }
            loadArtwork()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (targetPixels != requestedPixels) loadArtwork()
        }

        private fun loadArtwork() {
            val size = targetPixels
            logoWork?.cancel()
            requestedPixels = size
            val station = station ?: return
            if (size == 0) return
            val show: (Bitmap?) -> Unit = { bitmap ->
                if (this.station == station && requestedPixels == size && bitmap != null) {
                    logo.setImageBitmap(bitmap)
                    logo.visibility = View.VISIBLE
                    initials.visibility = View.INVISIBLE
                }
            }
            val portrait = ReciterPortraits.forStation(station)
            logoWork = if (portrait != null) artwork.loadPortrait(portrait.image, size, show)
                else artwork.load(station.logoUrl, size, show)
        }

        private fun bindCredit(station: RadioStation?, portrait: ReciterPortrait?) {
            if (!showCredits) return
            val name = station?.let(Stations::reciterName)
            if (portrait == null || name == null) {
                setOnClickListener(null)
                isClickable = false
                isFocusable = false
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                return
            }
            contentDescription = "Crédit photo · $name"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
            setOnClickListener { showPhotoCredit(name, portrait) }
        }

        override fun onDetachedFromWindow() {
            logoWork?.cancel()
            artworkGeneration = -1
            super.onDetachedFromWindow()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (artworkGeneration == -1) bind(station)
        }
    }

    private fun showPhotoCredit(name: String, portrait: ReciterPortrait) {
        val message = SpannableStringBuilder().append(portrait.author).append("\n")
        val licenseStart = message.length
        message.append(portrait.license)
        portrait.licenseUrl?.let {
            message.setSpan(URLSpan(it), licenseStart, message.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        message.append("\n\n")
        val sourceStart = message.length
        message.append("Photo sur Wikimedia Commons")
        message.setSpan(URLSpan(portrait.sourceUrl), sourceStart, message.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val dialog = AlertDialog.Builder(this)
            .setTitle(name).setMessage(message).setPositiveButton("Fermer", null).show()
        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun updateDirectory(resetScroll: Boolean = false) {
        val code = when {
            tab == StationTab.MOROCCO && !radioFavoritesOnly && activeCategory() != MoroccoCategory.REGIONAL -> "MA"
            tab == StationTab.WORLD -> country
            else -> null
        }
        directory.open(code?.let { StationDirectory.Query(it, query, activeSort()) })
        renderRows(resetScroll)
    }

    private fun renderRows(resetScroll: Boolean = false) {
        val sort = activeSort()
        val category = activeCategory()
        val notes = mutableMapOf<String, String>()
        val chapters = mutableMapOf<String, Int>()
        var listed = if (tab == StationTab.FOR_YOU) forYou().onEach { suggestion ->
            notes[suggestion.station.url] = suggestion.reason
            suggestion.chapter?.let { chapters[suggestion.station.url] = it }
        }.map(Suggestion::station) else Stations.visible(tab, favorites, query, radioFavoritesOnly, directory.stations,
            directoryBookmarks, sort, category, favoriteOrder, activeQuranSection())
        if (tab == StationTab.QURAN) HerrazRecitations.find(query)?.let { surah ->
            // A surah name in the search starts Hicham El Herraz's recitation at that surah.
            val herraz = Stations.herrazStation
            notes[herraz.url] = "سورة ${surah.name} · ${surah.latin}"
            chapters[herraz.url] = surah.number
            listed = listOf(herraz) + listed.filter { it != herraz }
        }
        if (tab == StationTab.FAVORITES && sort == StationSort.DEFAULT) {
            listed.forEachIndexed { index, station -> notes.putIfAbsent(station.url, "Présélection ${index + 1}") }
        }
        val unavailable = prefs.getStringSet(ListeningPrefs.UNAVAILABLE, emptySet()).orEmpty()
        if (tab != StationTab.FAVORITES && listed.any { it.url in unavailable }) {
            val (working, failing) = listed.partition { it.url !in unavailable }
            failing.forEach { notes.putIfAbsent(it.url, "Indisponible récemment") }
            listed = working + failing
        }
        rows = listed
        rowNotes = notes
        rowChapters = chapters
        PlaybackService.setVisibleStations(rows)
        listEntries = buildList {
            var previousSection: String? = null
            rows.forEachIndexed { index, station ->
                val section = when {
                    tab == StationTab.QURAN -> Stations.quranSection(station)?.label
                    tab == StationTab.MOROCCO && sort == StationSort.GROUPS -> Stations.group(station).label
                    tab == StationTab.MOROCCO && sort == StationSort.DEFAULT && category == MoroccoCategory.ALL -> {
                        val group = Stations.group(station)
                        if (group == StationGroup.REGIONAL) group.label
                        else "RADIOS NATIONALES ET WEBRADIOS"
                    }
                    else -> null
                }
                if (section != null && section != previousSection) add(ListEntry.Heading(section, tab == StationTab.QURAN))
                previousSection = section
                add(ListEntry.Station(station, index + 1))
            }
            if (inlineDirectoryPage) add(ListEntry.DirectoryPage)
        }
        if (!showingCatalogue) {
            renderPlayer()
            return
        }
        val onRadioTab = tab == StationTab.MOROCCO
        worldRegion.visibility = if (tab == StationTab.WORLD && !tightLandscape && !tvGuide) View.VISIBLE else View.GONE
        worldRegion.text = WorldContinent.forCountry(country)?.label.orEmpty()
        stationToolbar.visibility = if (tab == StationTab.FOR_YOU) View.GONE else View.VISIBLE
        renderChips()
        sortButton.visibility = if (tab == StationTab.QURAN || tab == StationTab.FOR_YOU) View.GONE else View.VISIBLE
        sortButton.contentDescription = "Trier les stations · ${sort.labelFor(tab)}"
        categoryButton.visibility = View.GONE
        countryButton.visibility = if (tab == StationTab.WORLD) View.VISIBLE else View.GONE
        countryButton.text = "${StationDirectory.countryName(country)} ▾"
        countryButton.contentDescription = "Choisir un continent ou un pays · ${StationDirectory.countryName(country)}"
        radioFilter.visibility = if (onRadioTab && !television && !carScreen) View.VISIBLE else View.GONE
        radioFilter.isSelected = radioFavoritesOnly
        radioFilter.contentDescription =
            if (radioFavoritesOnly) "Afficher toutes les radios marocaines" else "Afficher les radios marocaines favorites"
        radioFilter.background = pressable(if (radioFavoritesOnly) R.drawable.radio_selected else R.drawable.radio_control)
        empty.text = when {
            onRadioTab && radioFavoritesOnly && query.isBlank() -> "Aucune radio marocaine favorite"
            tab == StationTab.FAVORITES && favorites.isEmpty() -> if (carScreen) {
                "Appuie longuement sur une station pour l’ajouter"
            } else "Utilise l’étoile pour ajouter une station"
            directory.loading -> "Chargement des radios…"
            directory.failed -> "Annuaire indisponible"
            tab == StationTab.WORLD && query.isBlank() -> "Aucune station HTTPS disponible pour ce pays"
            else -> "Aucune station trouvée"
        }
        directoryAction.text = directoryPageLabel
        directoryAction.visibility = if (directory.hasMore && !compactDirectory) View.VISIBLE else View.GONE
        enable(directoryAction, !directory.loading)
        for ((choice, button) in tabs) {
            button.isSelected = choice == tab
            if (television || carScreen) styleTvCategory(button)
        }
        if (television) {
            val category = tabs[tab]
            menuButton.nextFocusDownId = category?.id ?: View.NO_ID
            if (tvGuide) {
                stationSearch.nextFocusUpId = category?.id ?: View.NO_ID
                stationSearch.nextFocusDownId = list.id
                tabs.values.forEach { it.nextFocusDownId = list.id }
            }
        }
        catalogueAdapter.notifyDataSetChanged()
        if (resetScroll) {
            revealCatalogueSelection = false
            scrollCatalogueTo(0)
        }
        renderPlayer()
    }

    private fun forYou(): List<Suggestion> {
        val city = localInfo.city.value
        val now = LocalDateTime.now(RadioLocalInfo.moroccoTime)
        val presets = Stations.visible(StationTab.FAVORITES, favorites, "", bookmarks = directoryBookmarks,
            favoriteOrder = favoriteOrder)
        return ForYou.suggestions(
            now = now,
            prayers = localInfo.prayerStatus(city).takeIf { it.next != null || it.current != null },
            todayPrayers = localInfo.prayerTimes(city, now.toLocalDate()),
            city = city,
            recents = ListeningPrefs.list(prefs, ListeningPrefs.RECENT_STATIONS).mapNotNull(::findStation),
            presets = presets,
            herrazChapter = prefs.getInt(ListeningPrefs.HERRAZ_CHAPTER, 0).takeIf { it > 0 },
        )
    }

    /** The chip row under the search bar: themes, Quran sections, country shortcuts or preset tools. */
    private fun renderChips() {
        chipBar.removeAllViews()
        when (tab) {
            StationTab.MOROCCO -> {
                val selected = activeCategory()
                val counts = Stations.themeCounts(if (selected == MoroccoCategory.ALL && query.isBlank() && !radioFavoritesOnly) rows
                    else Stations.visible(StationTab.MOROCCO, favorites, "", directory = directory.stations, bookmarks = directoryBookmarks))
                for (theme in MoroccoCategory.entries) {
                    val count = if (theme == MoroccoCategory.ALL) null else counts[theme] ?: 0
                    if (count == 0 && theme != selected) continue
                    addChip(theme.chip, count, theme == selected, "Thème · ${theme.label}") {
                        prefs.edit().putString("morocco_category", theme.name).apply()
                        updateDirectory(resetScroll = true)
                    }
                }
            }
            StationTab.QURAN -> {
                val selected = activeQuranSection()
                addChip("الكل · Tout", null, selected == null, "Toutes les récitations") { chooseQuranSection(null) }
                for (section in QuranSection.entries) addChip(section.label, null, section == selected, section.label) {
                    chooseQuranSection(section)
                }
                prefs.getInt(ListeningPrefs.HERRAZ_CHAPTER, 0).takeIf { it > 0 }?.let(HerrazRecitations::chapter)?.let { surah ->
                    addChip("Reprendre · سورة ${surah.name}", null, false, "Reprendre la sourate ${surah.latin}") {
                        select(Stations.herrazStation, surah.number)
                    }
                }
            }
            StationTab.WORLD -> {
                val recent = ListeningPrefs.list(prefs, ListeningPrefs.RECENT_COUNTRIES).filter { it in StationDirectory.countries }
                val shown = mutableSetOf<String>()
                fun countries(caption: String?, codes: List<String>) {
                    val fresh = codes.filter { it in StationDirectory.countries && it != "MA" && shown.add(it) }
                    if (fresh.isEmpty()) return
                    if (caption != null) chipBar.addView(label(caption, 11, accent, bold = true).apply {
                        setPadding(dp(8), 0, dp(4), 0)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    })
                    for (code in fresh) addChip(StationDirectory.countryName(code), null, code == country,
                        "Pays · ${StationDirectory.countryName(code)}") { chooseWorldCountry(code) }
                }
                countries(null, listOf(country) + recent)
                countries("DIASPORA", listOf("FR", "BE", "ES", "NL", "IT", "DE", "CA", "GB", "US"))
                countries("MONDE ARABE", listOf("DZ", "TN", "EG", "LB", "SA", "AE", "QA", "JO", "MR", "LY"))
                addChip("Autres pays…", null, false, "Choisir un continent ou un pays") { chooseContinent() }
            }
            StationTab.FAVORITES -> if (favorites.size > 1) {
                addChip("Organiser les présélections", null, false, "Changer l’ordre des présélections", ::organizePresets)
                val wheel = prefs.getBoolean(ListeningPrefs.WHEEL_PRESETS, false)
                addChip(if (wheel) "Volant : présélections" else "Volant : toute la liste", null, wheel,
                    "Boutons du volant") { setWheelPresets(!wheel) }
            }
            StationTab.FOR_YOU -> Unit
        }
        chipScroller.visibility = if (chipBar.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun addChip(text: String, count: Int?, selected: Boolean, description: String, action: () -> Unit) {
        val chip = label(if (count == null) text else "$text  $count", 13, if (selected) accent else textColor, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(14), 0, dp(14), 0)
            background = pressable(R.drawable.radio_chip_state)
            isSelected = selected
            isFocusable = true
            contentDescription = if (count == null) description else "$description · $count stations"
            stateDescription = if (selected) "Sélectionné" else null
            remoteFocus()
            setRadioClick(action)
        }
        chipBar.addView(chip, LinearLayout.LayoutParams(-2, -1).apply {
            marginEnd = dp(6)
            topMargin = dp(2)
            bottomMargin = dp(2)
        })
        if (selected) chipScroller.post { chipScroller.scrollTo((chip.left - dp(24)).coerceAtLeast(0), 0) }
    }

    private fun chooseQuranSection(section: QuranSection?) {
        prefs.edit().putString("quran_section", section?.name).apply()
        updateDirectory(resetScroll = true)
    }

    private fun chooseWorldCountry(code: String) {
        country = code
        prefs.edit().putString("world_country", code).apply()
        ListeningPrefs.remember(prefs, ListeningPrefs.RECENT_COUNTRIES, code, 3)
        if (query.isEmpty()) updateDirectory(resetScroll = true) else stationSearch.setText("")
    }

    private fun setWheelPresets(enabled: Boolean) {
        prefs.edit().putBoolean(ListeningPrefs.WHEEL_PRESETS, enabled).apply()
        if (showingCatalogue) renderChips()
    }

    private fun showWheelSettings() {
        val enabled = prefs.getBoolean(ListeningPrefs.WHEEL_PRESETS, false)
        showChoiceSheet("Boutons du volant", listOf("Toute la liste affichée", "Présélections seulement"),
            if (enabled) 1 else 0, subtitle = "Suivant et Précédent du volant ou du Bluetooth",
            details = listOf("Suit l’onglet ouvert à l’écran", "Passe d’une présélection à l’autre")) { index ->
            setWheelPresets(index == 1)
        }
    }

    /** Reorders the presets with large up and down buttons; the order drives the Présélections tiles. */
    private fun organizePresets() {
        val sheet = RadioSheet(this, "Ordre des présélections", "Le n° 1 s’affiche en premier")
        fun render() {
            sheet.body.removeAllViews()
            val presets = Stations.visible(StationTab.FAVORITES, favorites, "", bookmarks = directoryBookmarks,
                favoriteOrder = favoriteOrder)
            presets.forEachIndexed { index, station ->
                sheet.body.addView(row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = getDrawable(R.drawable.radio_option)
                    setPadding(dp(12), dp(4), dp(6), dp(4))
                    minimumHeight = dp(56)
                    addView(label("${index + 1}", 16, accent, bold = true).apply { gravity = Gravity.CENTER },
                        LinearLayout.LayoutParams(dp(28), -2))
                    addView(label(station.name, 15, textColor, bold = true).apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        layoutDirection = directionOf(station.name)
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
                    fun move(by: Int, icon: Float, description: String) = ImageButton(context).apply {
                        setImageResource(R.drawable.ic_radio_chevron)
                        rotation = icon
                        imageTintList = ColorStateList.valueOf(textColor)
                        background = pressable(R.drawable.radio_round)
                        contentDescription = "$description · ${station.name}"
                        remoteFocus()
                        val target = index + by
                        enable(this, target in presets.indices)
                        setRadioClick {
                            val order = presets.map(RadioStation::url).toMutableList()
                            order.add(target, order.removeAt(index))
                            favoriteOrder = order
                            ListeningPrefs.put(prefs.edit(), ListeningPrefs.FAVORITE_ORDER, order).apply()
                            render()
                            renderRows()
                        }
                    }
                    addView(move(-1, -90f, "Monter"), LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(6) })
                    addView(move(1, 90f, "Descendre"), LinearLayout.LayoutParams(dp(48), dp(48)))
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
        }
        render()
        sheet.show()
    }

    private fun styleTvCategory(button: TextView) {
        val onActive = getColor(R.color.radio_on_primary)
        button.background = pressable(if (button.isSelected) R.drawable.radio_segment_active else null)
        button.setTextColor(if (button.isSelected) onActive else textColor)
        if (button.compoundDrawablesRelative[0] != null) {
            button.compoundDrawableTintList = if (button.isSelected) ColorStateList.valueOf(onActive)
                else getColorStateList(R.color.radio_tab_icon)
        }
    }

    private fun renderPlayer() {
        val player = controller
        val active = player?.currentMediaItem?.mediaId ?: savedStation?.takeIf { it == Stations.luxe }?.url
        val station = findStation(active) ?: savedStation
        val luxe = if (station == Stations.luxe) luxeState() else null
        val recording = station?.let(Stations::isRecording) == true
        val chapter = player?.currentMediaItem?.takeIf { it.mediaId == HerrazRecitations.SOURCE }
            ?.mediaMetadata?.subtitle
        val ended = player?.playbackState == Player.STATE_ENDED
        val waitingForNetwork = luxe == null && player?.let(::waitingForNetwork) == true
        val requesting = if (luxe != null) luxe == LuxeRadio.State.PLAYING || luxe == LuxeRadio.State.CONNECTING
            else player?.playWhenReady == true && (player.playerError == null || waitingForNetwork) && !ended
        val playing = if (luxe != null) luxe == LuxeRadio.State.PLAYING else player?.isPlaying == true
        audioMotif.playing = luxe == null && playing
        val live = playing && !recording
        val streamInfo = player?.sessionExtras?.takeIf { it.getString(PlaybackService.STREAM_STATION) == active }
        val programme = streamInfo?.getString(PlaybackService.PROGRAMME_SOURCE)?.let { source ->
            streamInfo.getString(PlaybackService.STREAM_TITLE)?.let { title ->
                BroadcastProgramme(title, source, streamInfo.getString(PlaybackService.PROGRAMME_HOURS))
            }
        } ?: station?.takeIf { live && it.url == active }
            ?.let { BroadcastProgrammes.fromStream(it, streamInfo?.getString(PlaybackService.STREAM_TITLE)) }
        if (dashboardInfo.value) updateTvStationInfo(station, programme?.title ?: streamInfo?.getString(PlaybackService.STREAM_TITLE))
        playerCategory?.apply {
            val source = if (recording) station?.let(Stations::quranSection)?.label else programme?.source
            val format = player?.sessionExtras?.getString(PlaybackService.AUDIO_FORMAT)
                ?.takeIf { playing && !recording && luxe == null }
            text = listOfNotNull(source, format).joinToString(" · ")
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        playerPanel.layoutDirection = directionOf(station?.name.orEmpty())
        playerTitle.text = station?.name ?: "Choisir une station"
        playerSubtitle.text = if (chapter != null) "$chapter · ${HerrazRecitations.NARRATION}" else programme?.let {
            listOfNotNull(it.title, it.hours, it.source.takeIf { playerCategory == null }).joinToString(" · ")
        } ?: station?.let(Stations::subtitle).orEmpty()
        playerSubtitle.visibility = if (playerSubtitle.text.isEmpty()) View.GONE else View.VISIBLE
        playerArtwork.bind(station)
        playerStatus.visibility = if (live) View.GONE else View.VISIBLE
        playerStatus.text = when {
            connectionFailed -> "Lecteur indisponible · réessayer"
            player == null -> "Connexion au lecteur…"
            luxe != null -> when (luxe) {
                LuxeRadio.State.CONNECTING -> "Connexion à Luxe Radio…"
                LuxeRadio.State.PLAYING -> ""
                LuxeRadio.State.MISSING -> "Installer Luxe Radio"
                LuxeRadio.State.SETUP -> "Initialiser Luxe Radio"
                LuxeRadio.State.UNAVAILABLE -> "Luxe Radio indisponible · réessayer"
                LuxeRadio.State.PAUSED -> "En pause"
                LuxeRadio.State.IDLE -> "Prêt à écouter"
            }
            waitingForNetwork && !playing ->
                if (player.sessionExtras.getBoolean(PlaybackService.NETWORK_UNAVAILABLE)) "Connexion perdue · reprise automatique"
                else "Flux interrompu · reprise automatique"
            player.playerError != null && station in Stations.snrt -> "Flux SNRT inaccessible · réessayer"
            player.playerError?.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Source inaccessible · réessayer"
            player.playerError != null -> "Flux indisponible · réessayer"
            live -> ""
            playing -> "● En lecture"
            ended && recording -> "Lecture terminée"
            requesting && player.playbackState == Player.STATE_BUFFERING -> "Connexion au flux…"
            requesting -> "Lecture suspendue"
            active != null -> "En pause"
            else -> "Prêt à écouter"
        }
        playerStatus.setTextColor(when {
            waitingForNetwork && !playing -> amber
            playing -> accent
            else -> muted
        })
        statusBadge.backgroundTintList = ColorStateList.valueOf(when {
            waitingForNetwork && !playing -> getColor(R.color.radio_status_waiting)
            live -> Color.TRANSPARENT
            playing -> getColor(R.color.radio_status_playing)
            else -> getColor(R.color.radio_status_idle)
        })
        updateLiveDot(live)
        playButton.setImageResource(if (requesting) R.drawable.ic_radio_pause else R.drawable.ic_radio_play)
        playButton.contentDescription = when {
            requesting -> "Mettre en pause"
            luxe == LuxeRadio.State.MISSING -> "Installer Luxe Radio"
            luxe == LuxeRadio.State.SETUP -> "Initialiser Luxe Radio"
            else -> "Écouter"
        }
        enable(playButton, (player != null && (station != null || rows.isNotEmpty())) || connectionFailed)
        enable(previous, player != null && rows.size > 1)
        enable(next, player != null && rows.size > 1)
        playerFavorite?.let { bindFavorite(it, station) }
        val selectedStationId = active ?: savedStation?.url
        val stationChanged = highlighted?.first != selectedStationId
        val highlight = selectedStationId to playing
        if (highlighted != highlight) {
            highlighted = highlight
            if (showingCatalogue) catalogueAdapter.notifyDataSetChanged()
        }
        if (!television && showingCatalogue && (stationChanged || revealCatalogueSelection)) {
            revealCatalogueSelection = selectedStationId != null && !revealCatalogueStation(selectedStationId)
        }
    }

    private fun updateTvStationInfo(station: RadioStation?, streamTitle: String?) {
        val previous = tvStationInfo.value
        val publisher = StationNews.forStation(station)
        if (previous?.publisher != publisher) {
            stationHeadlines.value = publisher?.let(stationNews::get)?.headlines.orEmpty()
        }
        tvStationInfo.value = station?.let {
            val title = BroadcastProgrammes.fromStream(it, streamTitle)?.title
                ?: previous?.takeIf { info -> info.id == it.url }?.streamTitle
            val detail = title ?: Stations.listDescription(it)
            val text = listOfNotNull(it.name, detail?.takeUnless { value -> value.equals(it.name, ignoreCase = true) })
                .joinToString(" · ")
            TvStationInfo(it.url, publisher, title, NewsBulletin(if (title == null) "RADIO" else "FLUX", text))
        }
    }

    private fun showCarStartup() {
        val modes = CarResume.entries
        val details = modes.map {
            when (it) {
                CarResume.OFF -> "La radio attend un appui sur Lecture"
                CarResume.AFTER_BOOT -> "Relance la dernière station au démarrage"
                CarResume.ON_OPEN -> "Relance la dernière station à l’ouverture"
            }
        }
        showChoiceSheet("Démarrage voiture", modes.map { it.label }, carResume().ordinal, details = details) { index ->
            prefs.edit().putString("car_resume_mode", modes[index].name).apply {
                if (modes[index] == CarResume.AFTER_BOOT) {
                    val boot = carBootCount()
                    if (boot >= 0) putInt("car_played_boot", boot)
                }
            }.apply()
            startupDeadline = 0L
        }
    }

    private fun showAppearance() {
        val appearance = Appearance.restore(prefs)
        val options = Appearance.entries
        val details = options.map {
            when (it) {
                Appearance.DAY -> "Fond clair"
                Appearance.NIGHT -> "Fond sombre, idéal la nuit"
                Appearance.SYSTEM -> "Suit le réglage d’Android"
            }
        }
        showChoiceSheet("Jour / Nuit", options.map { it.label }, appearance.ordinal, details = details) { index ->
            val selected = options[index]
            prefs.edit().putString(APPEARANCE, selected.name).apply()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(UiModeManager::class.java).setApplicationNightMode(selected.androidMode)
            } else {
                recreate()
            }
        }
    }

    private fun carResume(): CarResume {
        val storedMode = prefs.getString("car_resume_mode", null)
        return CarResume.entries.firstOrNull { it.name == storedMode }
            ?: if (prefs.getBoolean("car_autoplay", false)) CarResume.AFTER_BOOT else CarResume.OFF
    }

    private fun armCarStartup() {
        val mode = carResume()
        if (mode == CarResume.OFF || playbackQuery != null) return
        directoryBookmarks = StationDirectory.decode(prefs.getString("directory_bookmarks", null).orEmpty())
        savedStation = findStation(prefs.getString("last_station", null)) ?: return
        if (mode == CarResume.AFTER_BOOT) {
            val boot = carBootCount()
            if (boot < 0 || boot == prefs.getInt("car_played_boot", -1) ||
                !prefs.edit().putInt("car_played_boot", boot).commit()) return
        }
        startupDeadline = SystemClock.elapsedRealtime() + 30_000
    }

    private fun carBootCount() = runCatching { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1) }
        .getOrDefault(-1)

    private fun updateLiveDot(live: Boolean) {
        liveIndicator.visibility = if (live) View.VISIBLE else View.GONE
        if (!live || !ValueAnimator.areAnimatorsEnabled()) {
            liveDot.removeCallbacks(livePulse)
            livePulsing = false
            liveDot.alpha = 1f
        } else if (!livePulsing) {
            livePulsing = true
            liveDot.postDelayed(livePulse, 900)
        }
    }

    private fun select(station: RadioStation, chapter: Int? = rowChapters[station.url]) {
        val player = controller ?: return
        startupDeadline = 0L
        savedStation = station
        persistStations(station)
        ListeningPrefs.remember(prefs, ListeningPrefs.RECENT_STATIONS, station.url, 8)
        if (station == Stations.luxe) {
            player.sendCustomCommand(PlaybackService.LUXE_PLAY, Bundle.EMPTY)
        } else {
            player.setMediaItem(recitationStart(station, chapter) ?: Stations.item(station))
            player.prepare()
            player.play()
        }
        renderPlayer()
    }

    /** Herraz starts at the requested surah, otherwise where the listener stopped last time. */
    private fun recitationStart(station: RadioStation, chapter: Int?): MediaItem? {
        if (station != Stations.herrazStation) return null
        val saved = prefs.getInt(ListeningPrefs.HERRAZ_CHAPTER, 0).takeIf { it > 0 }
        val number = chapter ?: saved ?: return null
        val position = if (number == saved) prefs.getLong(ListeningPrefs.HERRAZ_POSITION, 0L) else 0L
        val extras = Bundle().apply {
            putInt(ListeningPrefs.HERRAZ_CHAPTER, number)
            putLong(ListeningPrefs.HERRAZ_POSITION, position)
        }
        return Stations.item(station).buildUpon()
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build()).build()
    }

    private fun toggle() {
        val player = controller ?: run { if (connectionFailed) connectPlayer(); return }
        if (savedStation == Stations.luxe && player.currentMediaItem == null) {
            when (luxeState()) {
                LuxeRadio.State.CONNECTING, LuxeRadio.State.PLAYING ->
                    player.sendCustomCommand(PlaybackService.LUXE_PAUSE, Bundle.EMPTY)
                LuxeRadio.State.MISSING -> offerLuxeSetup(install = true)
                LuxeRadio.State.SETUP -> offerLuxeSetup(install = false)
                else -> select(Stations.luxe)
            }
            return
        }
        val current = findStation(player.currentMediaItem?.mediaId)
        val playable = player.playerError == null && player.playbackState != Player.STATE_ENDED
        when {
            (playable || waitingForNetwork(player)) && player.playWhenReady -> player.pause()
            playable && player.playbackState == Player.STATE_READY && current?.let(Stations::isRecording) == true -> player.play()
            else -> (current ?: savedStation ?: rows.firstOrNull())?.let(::select)
        }
    }

    private fun luxeState(): LuxeRadio.State {
        val value = controller?.sessionExtras?.getString(PlaybackService.LUXE_STATE)
        return LuxeRadio.State.entries.firstOrNull { it.name == value } ?: LuxeRadio.State.IDLE
    }

    private fun offerLuxeSetup(install: Boolean) {
        val title = if (install) "Installer Luxe Radio" else "Initialiser Luxe Radio"
        AlertDialog.Builder(this).setTitle(title)
            .setMessage("Ouvre l’application officielle Luxe Radio une première fois, puis reviens pour écouter ici.")
            .setNegativeButton("Annuler", null)
            .setNeutralButton("Réessayer") { _, _ -> select(Stations.luxe) }
            .setPositiveButton(if (install) "Play Store" else "Ouvrir Luxe Radio") { _, _ ->
                val target = if (install) Intent(Intent.ACTION_VIEW, "market://details?id=${LuxeRadio.PACKAGE}".toUri())
                    else packageManager.getLaunchIntentForPackage(LuxeRadio.PACKAGE)
                val opened = target != null && runCatching { startActivity(target) }.isSuccess
                if (!opened && install) runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=${LuxeRadio.PACKAGE}".toUri()))
                }
            }.show()
    }

    private fun findStation(url: String?): RadioStation? =
        Stations.all.firstOrNull { it.url == url } ?: directoryBookmarks.firstOrNull { it.url == url }

    private fun syncPlayingStation(item: MediaItem?) {
        val id = item?.mediaId ?: return
        if (savedStation?.url == id) return
        directoryBookmarks = StationDirectory.decode(prefs.getString("directory_bookmarks", null).orEmpty())
        savedStation = findStation(id) ?: savedStation
        if (findStation(id) != null) ListeningPrefs.remember(prefs, ListeningPrefs.RECENT_STATIONS, id, 8)
    }

    private fun bindFavorite(button: ImageButton, station: RadioStation?) {
        val favorite = station?.url in favorites
        button.setImageResource(if (favorite) R.drawable.ic_radio_favorite_filled else R.drawable.ic_tab_favorites)
        button.imageTintList = ColorStateList.valueOf(if (favorite) amber else muted)
        button.contentDescription = when {
            station == null -> "Favori"
            favorite -> "Retirer ${station.name} des favoris"
            else -> "Ajouter ${station.name} aux favoris"
        }
        enable(button, station != null)
    }

    private fun toggleFavorite(station: RadioStation) {
        favorites = if (station.url in favorites) favorites - station.url else favorites + station.url
        favoriteOrder = if (station.url in favorites) favoriteOrder.filter { it != station.url } + station.url
            else favoriteOrder.filter { it != station.url }
        ListeningPrefs.put(prefs.edit(), ListeningPrefs.FAVORITE_ORDER, favoriteOrder).apply()
        persistStations(station)
        renderRows()
    }

    private fun persistStations(station: RadioStation) {
        if (station.countryCode != null) {
            directoryBookmarks = listOf(station) + directoryBookmarks.filter { it.url != station.url }
        }
        directoryBookmarks = directoryBookmarks.filter { it.url == savedStation?.url || it.url in favorites }
        prefs.edit().putStringSet("favorites", favorites).putString("last_station", savedStation?.url)
            .putString("directory_bookmarks", StationDirectory.encode(directoryBookmarks)).apply()
    }

    private fun waitingForNetwork(player: MediaController): Boolean =
        (player.sessionExtras.getBoolean(PlaybackService.NETWORK_UNAVAILABLE) ||
            player.sessionExtras.getBoolean(PlaybackService.RETRY_PENDING)) &&
            player.currentMediaItem != null && player.playWhenReady && player.playbackState != Player.STATE_ENDED

    private fun directionOf(text: String): Int =
        if (TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

    private fun skip(direction: Int) {
        val current = controller?.currentMediaItem?.mediaId ?: savedStation?.url
        RadioQueue.neighbor(rows, current, direction)?.let(::select)
    }
}
