package com.autodrive.radiomaroc

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
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
import android.util.Log
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class RadioActivity : ComponentActivity() {
    private val dolbyPanel by lazy { DolbyPanel.find(this) }
    private val openDolbyPanel = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

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

    private enum class Appearance(val label: String, val nightFlag: Int?) {
        DAY("Jour", Configuration.UI_MODE_NIGHT_NO),
        NIGHT("Nuit", Configuration.UI_MODE_NIGHT_YES),
        SYSTEM("Système", null);

        companion object {
            fun restore(prefs: SharedPreferences): Appearance {
                val saved = prefs.getString(APPEARANCE, null)
                return entries.firstOrNull { it.name == saved }
                    ?: if (prefs.getBoolean(LEGACY_DAY_APPEARANCE, false)) DAY else NIGHT
            }
        }
    }

    private enum class DisplayLayout(val label: String) {
        GUIDE("Guide TV"), MOSAIC("Mosaïque"), LISTENING("Écran d’écoute");

        companion object {
            fun restore(name: String?) = entries.firstOrNull { it.name == name } ?: GUIDE
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
    private val radioCity = MutableStateFlow(RadioCity.CASABLANCA)
    private val weather = MutableStateFlow<RadioWeather?>(null)
    private val prayerCalendars = MutableStateFlow<Map<YearMonth, HabousCalendar>>(emptyMap())
    private var nextWeatherFetchAt = 0L
    private var weatherTile: WeatherTile? = null
    private var prayerTile: PrayerTile? = null
    private var playerCategory: TextView? = null
    private lateinit var playerArtwork: StationArtwork
    private lateinit var playerStatus: TextView
    private lateinit var statusBadge: LinearLayout
    private lateinit var liveIndicator: LinearLayout
    private lateinit var liveDot: View
    private var livePulse: ObjectAnimator? = null
    private lateinit var audioMotif: PremiumRadioMotif
    private lateinit var playButton: ImageButton
    private lateinit var previous: ImageButton
    private lateinit var next: ImageButton
    private var playerFavorite: ImageButton? = null
    private var soundStatusLabel: TextView? = null
    private val television: Boolean
        get() = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    private val compactTv: Boolean
        get() = television && (resources.configuration.screenHeightDp < 430 || resources.configuration.screenWidthDp < 760)
    private val landscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val shortLandscape get() = !television && landscape && resources.configuration.screenHeightDp < 330
    private val tightLandscape get() = shortLandscape && resources.configuration.screenHeightDp < 280
    private val showingCatalogue get() = displayLayout != DisplayLayout.LISTENING
    private val tvGuide get() = television && displayLayout == DisplayLayout.GUIDE
    private val tvGuideRowDp get() = if (compactTv) 30 else 36
    private val catalogueInGrid get() = tvGuide || displayLayout == DisplayLayout.MOSAIC
    private val catalogueAdapter get() = if (catalogueInGrid) tileAdapter else stationAdapter
    private val compactDirectory get() = tvGuide || shortLandscape
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
        val nightFlag = Appearance.restore(newBase.getSharedPreferences(PREFERENCES, MODE_PRIVATE)).nightFlag
        super.attachBaseContext(if (nightFlag == null) newBase else {
            newBase.createConfigurationContext(Configuration().apply { uiMode = nightFlag })
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val artworkCacheMb = if (television && resources.displayMetrics.density >= 2f) 8 else 2
        artwork = RadioArtwork(this, maxImageBytes = 1024 * 1024, memoryBytes = artworkCacheMb * 1024 * 1024,
            normalizeLogoMargins = true)
        directory = DirectoryBrowser(StationDirectory(this)) { renderRows() }
        radioUpdates = RadioUpdates(this, savedInstanceState)
        tab = StationTab.restore(prefs.getString("tab", null))
        country = prefs.getString("world_country", null)?.takeIf { it in StationDirectory.countries && it != "MA" } ?: "FR"
        favorites = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
        directoryBookmarks = StationDirectory.decode(prefs.getString("directory_bookmarks", null).orEmpty())
        savedStation = findStation(prefs.getString("last_station", null))
        playbackQuery = voiceQuery(intent)
        restoringActivity = savedInstanceState != null
        startupDeadline = savedInstanceState?.getLong("startup_deadline") ?: 0L
        query = savedInstanceState?.getString("query").orEmpty()
        radioFavoritesOnly = !television && savedInstanceState?.getBoolean("radio_favorites_only") == true
        displayLayout = DisplayLayout.restore(savedInstanceState?.getString("display_screen") ?: prefs.getString(DISPLAY_LAYOUT, null))
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
        if (television) lifecycleScope.launch {
            radioCity.value = RadioCity.restore(prefs.getString("tv_info_city", null))
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { rollNewsHeadlines() }
                launch {
                    tvStationInfo.map { it?.publisher }.distinctUntilChanged().collectLatest { publisher ->
                        publisher?.let { refreshStationNews(it) }
                    }
                }
                launch { radioCity.collectLatest { refreshWeather(it) } }
                launch { refreshPrayerCalendars() }
                launch { refreshLocalTiles() }
                launch {
                    while (true) {
                        renderTvClock()
                        delay(untilNextMinute())
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
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        background = getDrawable(R.drawable.radio_background)
        isFocusableInTouchMode = !television
        val inlineCategories = showingCatalogue && (television || landscape)
        val mastheadHeight = dp(when {
            television && inlineCategories -> 36
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
                addView(if (television) tvCategoryBar() else categoryBar(), LinearLayout.LayoutParams(0, -1, 1f).apply {
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
            if (television) {
                addView(tvClockBar(), LinearLayout.LayoutParams(-2, -1).apply { marginEnd = dp(6) })
            }
            addView(menuButton, LinearLayout.LayoutParams(dp(if (television || inlineCategories) 44 else 48), -1))
        }
        addView(masthead, LinearLayout.LayoutParams(-1, mastheadHeight).apply { bottomMargin = dp(4) })
        val hideWhileTyping = mutableListOf<View>(masthead)
        addView(layoutContent(hideWhileTyping), LinearLayout.LayoutParams(-1, 0, 1f))
        if (television) {
            val footer = newsBar()
            addView(footer, LinearLayout.LayoutParams(-1, dp(if (compactTv) 42 else 48)).apply {
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
            insets
        }
        if (!television) requestFocus()
    }

    private fun layoutContent(hideWhileTyping: MutableList<View>): View = when (displayLayout) {
        DisplayLayout.GUIDE -> if (television) row().apply {
            addView(stationList(grid = true), LinearLayout.LayoutParams(0, -1, .72f).apply { marginEnd = dp(10) })
            addView(stationPanel(true), LinearLayout.LayoutParams(0, -1, .28f))
            list.nextFocusRightId = playButton.id
            list.nextFocusUpId = stationSearch.id
            previous.nextFocusLeftId = list.id
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
            if (shortLandscape) {
                val player = stationPanel(true).also(hideWhileTyping::add)
                addView(wideColumns(player, stationList(grid = true)), LinearLayout.LayoutParams(-1, 0, 1f))
            } else {
                addView(stationList(grid = true), LinearLayout.LayoutParams(-1, 0, 1f))
                addView(stationPanel(false, mini = true).also(hideWhileTyping::add),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
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
        clockTime = label("", if (compactTv) 16 else 19, textColor, bold = true).apply {
            includeFontPadding = false
            setSingleLine()
        }.also { addView(it, LinearLayout.LayoutParams(-2, -2)) }
        clockDate = label("", if (compactTv) 10 else 12, muted).apply {
            includeFontPadding = false
            setSingleLine()
        }.also { addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) }) }
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
        background = getDrawable(R.drawable.radio_control)
        setPadding(dp(3), dp(3), dp(3), dp(3))
        fun addCategory(title: String, icon: Int, action: () -> Unit): TextView = button(title, action).apply {
            id = View.generateViewId()
            nextFocusUpId = menuButton.id
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            val maxSize = if (compactTv) 13 else 15
            setAutoSizeTextTypeUniformWithConfiguration(10, maxSize, 1, TypedValue.COMPLEX_UNIT_SP)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(dp(if (compactTv) 6 else 10), 0, dp(4), 0)
            val size = dp(if (compactTv) 14 else 16)
            val symbol = getDrawable(icon)?.apply { setBounds(0, 0, size, size) }
            setCompoundDrawablesRelative(symbol, null, null, null)
            compoundDrawableTintList = getColorStateList(R.color.radio_tab_icon)
            compoundDrawablePadding = dp(if (compactTv) 4 else 6)
            bar.addView(this, LinearLayout.LayoutParams(0, -1, if (title == StationTab.MOROCCO.label) 1.3f else 1f)
                .apply { marginEnd = dp(3) })
        }
        tabs = StationTab.entries.associateWith { choice ->
            addCategory(choice.label, stationTabIcon(choice)) { selectTab(choice) }
        }
    }

    private fun selectTab(choice: StationTab) {
        if (tab != choice) catalogueFocusStation = null
        tab = choice
        prefs.edit().putString("tab", choice.name).apply()
        updateDirectory(resetScroll = true)
    }

    private fun stationTabIcon(choice: StationTab): Int = when (choice) {
        StationTab.MOROCCO -> R.drawable.ic_tab_radio
        StationTab.WORLD -> R.drawable.ic_tab_world
        StationTab.QURAN -> R.drawable.ic_tab_quran
        StationTab.FAVORITES -> R.drawable.ic_tab_favorites
    }

    private fun layoutLabel(layout: DisplayLayout): String =
        if (!television && layout == DisplayLayout.GUIDE) "Guide" else layout.label

    private fun choiceDialog(title: String): AlertDialog.Builder =
        if (tightLandscape) AlertDialog.Builder(this, R.style.RadioCompactAlert)
        else AlertDialog.Builder(this).setTitle(title)

    private fun showMainMenu() {
        val actions = mutableListOf(
            "Affichage" to ::showDisplayMenu,
            "Jour / Nuit" to ::showAppearance,
            "Effets sonores" to ::showSoundEffects,
            "Démarrage voiture" to ::showCarStartup,
            "Mises à jour" to radioUpdates::show,
        )
        if (television) actions.add(1, "Infos TV" to ::showTvInfo)
        val menu = if (tightLandscape) AlertDialog.Builder(this, R.style.RadioCompactMenu) else choiceDialog("Menu")
        menu
            .setItems(actions.map { it.first }.toTypedArray()) { _, index -> actions[index].second() }
            .show()
    }

    private fun showSoundEffects() {
        val status = label("", 12, muted).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val profiles = SoundProfile.entries.associateBy { View.generateViewId() }
        val content = column().apply {
            setPadding(dp(16), dp(8), dp(16), dp(6))
            addView(CheckBox(context).apply {
                text = "Clics de l’interface"
                textSize = 14f
                setTextColor(textColor)
                isSoundEffectsEnabled = false
                isChecked = prefs.getBoolean(RadioSound.CLICKS, false)
                setOnCheckedChangeListener { _, enabled ->
                    prefs.edit().putBoolean(RadioSound.CLICKS, enabled).apply()
                    if (enabled) RadioSound.playClick(this@RadioActivity, prefs)
                }
            }, LinearLayout.LayoutParams(-1, dp(if (tightLandscape) 36 else 44)))
            addView(label("Égaliseur", 13, textColor, bold = true))
            addView(RadioGroup(context).apply {
                orientation = LinearLayout.HORIZONTAL
                for ((viewId, profile) in profiles) {
                    addView(RadioButton(context).apply {
                        id = viewId
                        text = profile.label
                        textSize = 13f
                        setTextColor(textColor)
                        isSoundEffectsEnabled = false
                    }, RadioGroup.LayoutParams(0, -1, 1f))
                }
                check(profiles.entries.first { it.value == RadioSound.profile(prefs) }.key)
                setOnCheckedChangeListener { _, id ->
                    val profile = profiles[id] ?: return@setOnCheckedChangeListener
                    prefs.edit().putString(RadioSound.PROFILE, profile.name).apply()
                    RadioSound.playClick(this@RadioActivity, prefs)
                    renderSoundStatus(controller?.sessionExtras ?: Bundle.EMPTY)
                }
            }, LinearLayout.LayoutParams(-1, dp(if (tightLandscape) 38 else 44)))
            addView(status, LinearLayout.LayoutParams(-1, -2))
        }
        soundStatusLabel = status
        renderSoundStatus(controller?.sessionExtras ?: Bundle.EMPTY)
        val panel = dolbyPanel
        val dolbyAllowed = panel != null && prefs.getBoolean(RadioSound.DOLBY, false)
        val dolbyText = when {
            panel == null -> "Dolby · Indisponible"
            dolbyAllowed -> "Dolby · ON"
            else -> "Dolby · OFF"
        }
        val dialog = choiceDialog("Effets sonores")
            .setView(content)
            .setNegativeButton("Fermer", null)
            .setNeutralButton(dolbyText) { _, _ -> panel?.let(::showDolbySettings) }
            .setOnDismissListener { soundStatusLabel = null }
            .show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
            isEnabled = panel != null
            setTextColor(if (dolbyAllowed) accent else getColor(R.color.radio_dolby_off))
            stateDescription = when {
                panel == null -> "Panneau Dolby indisponible sur cet appareil"
                dolbyAllowed -> "Autorisé dans la radio"
                else -> "Désactivé dans la radio"
            }
        }
    }

    private fun showDolbySettings(panel: DolbyPanel) {
        val enabled = prefs.getBoolean(RadioSound.DOLBY, false)
        val dialog = choiceDialog("Dolby")
            .setSingleChoiceItems(arrayOf("OFF · Désactivé", "ON · Autorisé dans la radio"), if (enabled) 1 else 0) { prompt, which ->
                val allow = which == 1
                prefs.edit().putBoolean(RadioSound.DOLBY, allow).apply()
                (prompt as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = allow
            }
            .setNegativeButton("Retour") { _, _ -> showSoundEffects() }
            .setPositiveButton("Réglages Dolby") { _, _ ->
                val sessionId = controller?.sessionExtras?.getInt(RadioSound.DOLBY_SESSION_ID) ?: 0
                runCatching { openDolbyPanel.launch(panel.settingsIntent(this, sessionId)) }.onFailure {
                    Toast.makeText(this, "Réglages Dolby indisponibles", Toast.LENGTH_SHORT).show()
                }
            }.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = enabled
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
        val title = if (television) "Affichage TV" else "Affichage"
        choiceDialog(title)
            .setSingleChoiceItems(layouts.map(::layoutLabel).toTypedArray(), displayLayout.ordinal) { dialog, index ->
                dialog.dismiss()
                val layout = layouts[index]
                prefs.edit().putString(DISPLAY_LAYOUT, layout.name).apply()
                openLayout(layout)
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun openLayout(layout: DisplayLayout) {
        if (layout == displayLayout) return
        if (television) rememberTvFocus()
        displayLayout = layout
        if (layout != DisplayLayout.LISTENING) {
            catalogueLayout = layout
            prefs.edit().putString(CATALOGUE_LAYOUT, layout.name).apply()
        }
        returnFromListening.isEnabled = !showingCatalogue
        livePulse?.cancel()
        livePulse = null
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
            list.setSelectionFromTop(position, 0)
        }
        return true
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
            textSize = 12f
            maxLines = 1
            setPadding(dp(if (shortLandscape) 6 else 10), 0, dp(if (shortLandscape) 6 else 10), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_radio_sort, 0, 0, 0)
            compoundDrawablePadding = dp(6)
        }
        categoryButton = button("Genres", ::chooseCategory).apply {
            textSize = 12f
            maxLines = 1
            setPadding(dp(4), 0, dp(4), 0)
        }
        stationSearch = EditText(context).apply {
            if (tvGuide) id = View.generateViewId()
            hint = if (tightLandscape) "Chercher" else "Rechercher…"
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
            tvGuide -> 34
            compactTv -> 38
            tightLandscape -> 40
            landscape -> 44
            else -> 48
        })
        val stationToolbar = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(stationSearch, LinearLayout.LayoutParams(0, -1, 1f))
            addView(countryButton, LinearLayout.LayoutParams(dp(if (shortLandscape) 84 else if (television && !compactTv) 128 else 104), -1)
                .apply { marginStart = dp(6) })
            if (!television) addView(radioFilter, LinearLayout.LayoutParams(toolbarHeight, -1).apply { marginStart = dp(6) })
            addView(categoryButton, LinearLayout.LayoutParams(dp(if (shortLandscape) 56 else 66), -1)
                .apply { marginStart = dp(6) })
            addView(sortButton, LinearLayout.LayoutParams(dp(if (shortLandscape) 66 else if (television && !compactTv) 92 else 76), -1)
                .apply { marginStart = dp(6) })
        }
        addView(stationToolbar, LinearLayout.LayoutParams(-1, toolbarHeight).apply { bottomMargin = dp(if (tvGuide) 4 else 6) })
        addView(FrameLayout(context).apply {
            if (!grid || tvGuide) {
                background = getDrawable(R.drawable.radio_catalogue)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            empty = label("", 15, muted).apply { gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0) }
            val catalog = if (grid) GridView(context).apply {
                numColumns = when {
                    tvGuide -> if (resources.configuration.screenWidthDp >= 1_250) 3 else 2
                    television -> if (resources.configuration.screenWidthDp < 760) 3 else 4
                    shortLandscape -> 2
                    landscape -> if (resources.configuration.screenWidthDp < 700) 3 else 4
                    else -> 2
                }
                horizontalSpacing = dp(if (tvGuide) 4 else 10)
                verticalSpacing = dp(if (tvGuide) 2 else 8)
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

    private fun activeCategory(): MoroccoCategory = MoroccoCategory.entries
        .firstOrNull { it.name == prefs.getString("morocco_category", null) } ?: MoroccoCategory.ALL

    private fun chooseCategory() {
        val options = MoroccoCategory.entries
        val dialog = AlertDialog.Builder(this).setTitle("Catégories Radio Maroc")
            .setSingleChoiceItems(options.map { it.label }.toTypedArray(), activeCategory().ordinal) { picker, index ->
                prefs.edit().putString("morocco_category", options[index].name).apply()
                picker.dismiss()
                updateDirectory(resetScroll = true)
            }.setNegativeButton("Fermer", null).show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
        if (television) dialog.listView.requestFocus()
        dialog.listView.setSelection(0)
    }

    private suspend fun rollNewsHeadlines() {
        combine(tvStationInfo, stationHeadlines) { info, headlines ->
            StationNews.bulletins(info?.publisher, headlines).ifEmpty { listOfNotNull(info?.fallback) }
        }
            .distinctUntilChanged().collectLatest { edition ->
                if (edition.isEmpty()) {
                    tickerBulletin = null
                    if (newsFooter?.hasFocus() == true) playButton.requestFocus()
                    newsFooter?.visibility = View.GONE
                    return@collectLatest
                }
                newsFooter?.visibility = View.VISIBLE
                if (edition.size == 1) {
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
        if (tickerBulletin == bulletin) return tickerDuration
        tickerBulletin = bulletin
        tickerStartedAt = SystemClock.uptimeMillis()
        tickerDuration = if (bulletin.url == null) 0L else newsTicker?.durationFor(bulletin.text) ?: 8_000L
        tickerSource?.text = bulletin.source
        newsTicker?.showHeadline(bulletin.text, tickerStartedAt, tickerDuration)
        return tickerDuration
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

    private suspend fun refreshWeather(city: RadioCity) {
        while (true) {
            if (SystemClock.elapsedRealtime() >= nextWeatherFetchAt) {
                val report = withContext(Dispatchers.IO) { runCatching { RadioLocalInfo.weather(city) }.getOrNull() }
                weather.value = report
                nextWeatherFetchAt = SystemClock.elapsedRealtime() + if (report == null) 300_000L else 900_000L
            }
            delay((nextWeatherFetchAt - SystemClock.elapsedRealtime()).coerceAtLeast(1L))
        }
    }

    private suspend fun refreshPrayerCalendars() {
        while (true) {
            val today = LocalDate.now(RadioLocalInfo.moroccoTime)
            val months = listOf(today, today.minusDays(1), today.plusDays(1)).map(YearMonth::from).toSet()
            val calendars = prayerCalendars.value.filterKeys { it in months }.toMutableMap()
            val missing = months - calendars.keys
            if (missing.isNotEmpty()) {
                calendars += withContext(Dispatchers.IO) {
                    missing.mapNotNull { month ->
                        runCatching { RadioLocalInfo.calendar(month) }
                            .onFailure { Log.w("RadioLocalInfo", "Calendrier Habous indisponible pour $month", it) }
                            .getOrNull()?.let { month to it }
                    }
                }
            }
            prayerCalendars.value = calendars.toMap()
            delay(if (months.all { it in calendars }) 3_600_000L else 900_000L)
        }
    }

    private fun localPrayerTimes(city: RadioCity, date: LocalDate): List<PrayerMoment> =
        prayerCalendars.value[YearMonth.from(date)]?.prayers(city, date).orEmpty()

    private fun localPrayerStatus(city: RadioCity): PrayerStatus {
        val today = LocalDate.now(RadioLocalInfo.moroccoTime)
        val offset = prayerCalendars.value[YearMonth.from(today)]?.offset ?: RadioLocalInfo.moroccoTime
        val now = LocalDateTime.now(offset)
        val date = now.toLocalDate()
        return RadioLocalInfo.prayerStatus(
            now, localPrayerTimes(city, date.minusDays(1)), localPrayerTimes(city, date), localPrayerTimes(city, date.plusDays(1)),
        )
    }

    private fun untilNextMinute() = 60_000L - System.currentTimeMillis() % 60_000L

    private suspend fun refreshLocalTiles() {
        combine(radioCity, weather, prayerCalendars) { _, _, _ -> Unit }.collectLatest {
            while (true) {
                renderLocalTiles()
                delay(untilNextMinute())
            }
        }
    }

    private fun renderLocalTiles() {
        val city = radioCity.value
        weatherTile?.bind(weather.value, city)
        prayerTile?.bind(localPrayerStatus(city), city)
    }

    private fun localInfoBar(): View = row().apply {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        weatherTile = WeatherTile().also {
            addView(it, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(5) })
        }
        prayerTile = PrayerTile().also {
            addView(it, LinearLayout.LayoutParams(0, -1, 1.55f))
        }
        renderLocalTiles()
    }

    private fun LinearLayout.prepareLocalTile(action: () -> Unit) {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(4), 0, dp(4), 0)
        background = pressable(R.drawable.radio_control)
        isFocusable = true
        remoteFocus()
        setRadioClick(action)
    }

    private fun tileLine(text: String, size: Int, color: Int, bold: Boolean = false) =
        label(text, size, color, bold).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

    private inner class WeatherTile : LinearLayout(this@RadioActivity) {
        private val headingColor = getColor(R.color.radio_weather_label)
        private val icon = WeatherIcon(context, headingColor)
        private val temperature = tileLine("", if (compactTv) 15 else 19, textColor, bold = true)
        private val detail = tileLine("", if (compactTv) 10 else 11, muted)

        init {
            prepareLocalTile(::showWeather)
            setPadding(dp(2), 0, dp(2), 0)
            addView(tileLine("Météo", if (compactTv) 10 else 11, headingColor, bold = true), LayoutParams(-1, -2))
            addView(row().apply {
                gravity = Gravity.CENTER
                val size = dp(if (compactTv) 17 else 23)
                addView(icon, LayoutParams(size, size).apply { marginEnd = dp(3) })
                addView(temperature, LayoutParams(-2, -2))
            }, LayoutParams(-1, -2).apply { topMargin = dp(2) })
            addView(detail, LayoutParams(-1, -2))
        }

        fun bind(report: RadioWeather?, city: RadioCity) {
            icon.bind(report)
            temperature.text = report?.let { "${it.degrees}°" } ?: "—"
            detail.text = report?.condition?.label ?: "Indisponible"
            contentDescription = listOfNotNull("Météo", city.label, report?.let { "${it.degrees} °C" }, detail.text)
                .joinToString(" · ")
        }
    }

    private inner class PrayerTile : LinearLayout(this@RadioActivity) {
        private val headingColor = getColor(R.color.radio_prayer_label)
        private val currentName = tileLine("", if (compactTv) 10 else 12, textColor, bold = true)
        private val nextName = tileLine("", if (compactTv) 10 else 12, textColor, bold = true)
        private val currentTime = tileLine("", if (compactTv) 12 else 15, textColor, bold = true)
        private val nextTime = tileLine("", if (compactTv) 12 else 15, headingColor, bold = true)

        init {
            prepareLocalTile(::showPrayerTimes)
            addView(tileLine("Salat", if (compactTv) 10 else 11, headingColor, bold = true), LayoutParams(-1, -2))
            addView(row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(prayerColumn("Maint.", currentName, currentTime), LayoutParams(0, -2, 1f))
                addView(View(context).apply { setBackgroundColor(getColor(R.color.radio_control_border)) },
                    LayoutParams(dp(1), dp(if (compactTv) 28 else 34)))
                addView(prayerColumn("Ensuite", nextName, nextTime), LayoutParams(0, -2, 1f))
            }, LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }

        private fun prayerColumn(label: String, name: TextView, time: TextView) = column().apply {
            addView(tileLine(label, if (compactTv) 9 else 10, muted), LayoutParams(-1, -2))
            addView(name, LayoutParams(-1, -2))
            addView(time, LayoutParams(-1, -2))
        }

        fun bind(status: PrayerStatus, city: RadioCity) {
            currentName.text = status.current?.prayer?.label ?: "—"
            nextName.text = status.next?.prayer?.label ?: "—"
            currentTime.text = status.current?.dateTime?.toLocalTime()?.toString().orEmpty()
            nextTime.text = status.next?.dateTime?.toLocalTime()?.toString().orEmpty()
            contentDescription = "Salat · ${city.label} · Maintenant ${prayerDescription(status.current, status.date)}" +
                " · Ensuite ${prayerDescription(status.next, status.date)}"
        }
    }

    private fun prayerDescription(moment: PrayerMoment?, date: LocalDate): String {
        if (moment == null) return "—"
        val day = when {
            moment.dateTime.toLocalDate() < date -> " hier"
            moment.dateTime.toLocalDate() > date -> " demain"
            else -> ""
        }
        return "${moment.prayer.label} ${moment.dateTime.toLocalTime()}$day"
    }

    private fun newsBar(): View = row().apply {
        newsFooter = this
        id = View.generateViewId()
        visibility = if (tickerBulletin == null) View.GONE else View.VISIBLE
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        gravity = Gravity.CENTER_VERTICAL
        background = pressable(R.drawable.radio_control)
        isFocusable = true
        contentDescription = "Infos TV · Actualités, météo et salat"
        remoteFocus()
        setRadioClick(::showTvInfo)
        val source = label(tickerBulletin?.source.orEmpty(), if (compactTv) 12 else 14,
            getColor(R.color.radio_news_badge), bold = true).apply {
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.radio_news_badge)
        }
        tickerSource = source
        addView(source, LinearLayout.LayoutParams(dp(if (compactTv) 52 else 62), dp(if (compactTv) 26 else 30))
            .apply { marginStart = dp(6) })
        val ticker = HeadlineTicker(context, if (compactTv) 16 else 19, textColor).apply {
            tickerBulletin?.let { showHeadline(it.text, tickerStartedAt, tickerDuration) }
        }
        newsTicker = ticker
        addView(ticker, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun showTvInfo() {
        val publisher = tvStationInfo.value?.publisher?.label ?: "RFI"
        val options = arrayOf("Actualités · $publisher", "Météo", "Salat · مواقيت الصلاة", "Ville · ${radioCity.value.label}")
        val actions = listOf(::showNews, ::showWeather, ::showPrayerTimes, ::chooseInfoCity)
        val dialog = AlertDialog.Builder(this).setTitle("Infos TV")
            .setItems(options) { _, index -> actions[index]() }
            .setNegativeButton("Fermer", null).show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
        dialog.listView.requestFocus()
    }

    private fun chooseInfoCity() {
        val cities = RadioCity.entries
        val dialog = AlertDialog.Builder(this).setTitle("Ville pour la météo et la salat")
            .setSingleChoiceItems(cities.map(RadioCity::label).toTypedArray(), radioCity.value.ordinal) { picker, index ->
                val city = cities[index]
                if (city != radioCity.value) {
                    weather.value = null
                    nextWeatherFetchAt = 0L
                    radioCity.value = city
                    prefs.edit().putString("tv_info_city", city.name).apply()
                    renderLocalTiles()
                }
                picker.dismiss()
            }.setNegativeButton("Fermer", null).show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
        dialog.listView.requestFocus()
    }

    private fun showWeather() {
        val dialog = infoDialog("Météo · ${radioCity.value.label}", RadioLocalInfo.WEATHER_SITE)
        val request = lifecycleScope.launch {
            weather.collect { report ->
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
        val city = radioCity.value
        val dialog = infoDialog("Salat · ${city.label}", RadioLocalInfo.HABOUS_SITE)
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.FRENCH)
        val request = lifecycleScope.launch {
            prayerCalendars.collectLatest {
                while (true) {
                    val status = localPrayerStatus(city)
                    val prayers = localPrayerTimes(city, status.date)
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

    private fun showNews() {
        val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.FRENCH).withZone(RadioLocalInfo.moroccoTime)
        val publisher = tvStationInfo.value?.publisher
        val cache = mutableMapOf<NewsTopic, List<NewsHeadline>>()
        var request: Job? = null
        val status = label("", 14, muted).apply { gravity = Gravity.CENTER }
        val headlines = object : ArrayAdapter<NewsHeadline>(this, android.R.layout.simple_list_item_1) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val article = requireNotNull(getItem(position))
                return ((convertView as? NewsStoryRow) ?: NewsStoryRow()).apply { bind(article, dateFormat) }
            }
        }
        val list = ListView(this).apply {
            id = View.generateViewId()
            adapter = headlines
            setSelector(R.drawable.radio_focus)
            setOnItemClickListener { _, _, position, _ ->
                val article = requireNotNull(headlines.getItem(position))
                val source = listOfNotNull(article.publisher.label, article.publishedAt?.let(dateFormat::format)).joinToString(" · ")
                AlertDialog.Builder(this@RadioActivity).setTitle(article.title)
                    .setMessage(listOfNotNull(source, article.summary).joinToString("\n\n"))
                    .setPositiveButton("Retour", null)
                    .setNeutralButton("Lire l’article") { _, _ ->
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, article.url.toUri())) }
                            .onFailure { Toast.makeText(this@RadioActivity, "Navigateur indisponible", Toast.LENGTH_SHORT).show() }
                    }.show().window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
            }
        }
        val buttons = mutableMapOf<NewsTopic?, TextView>()
        fun load(topic: NewsTopic?) {
            request?.cancel()
            buttons.forEach { (choice, control) ->
                control.isSelected = choice == topic
                control.setTextColor(if (choice == topic) accent else textColor)
            }
            list.nextFocusUpId = buttons.getValue(topic).id
            headlines.clear()
            list.setSelection(0)
            if (topic == null) {
                status.text = "Chargement des titres…"
                request = lifecycleScope.launch {
                    stationHeadlines.collect { current ->
                        val articles = current.filter { it.publisher == publisher }
                        headlines.clear()
                        if (articles.isEmpty()) {
                            status.text = "Titres de la station indisponibles. Choisis une rubrique RFI."
                        } else headlines.addAll(articles)
                    }
                }
                return
            }
            cache[topic]?.let {
                headlines.addAll(it)
                return
            }
            status.text = "Chargement des titres…"
            request = lifecycleScope.launch {
                val articles = withContext(Dispatchers.IO) { runCatching { RadioNews.fetch(topic) }.getOrDefault(emptyList()) }
                if (articles.isEmpty()) {
                    status.text = "Titres indisponibles. Sélectionne la rubrique pour réessayer."
                } else {
                    cache[topic] = articles
                    headlines.addAll(articles)
                }
            }
        }
        val content = column().apply {
            setPadding(dp(16), dp(4), dp(16), 0)
            addView(row().apply {
                val topics = if (publisher == null) NewsTopic.entries else listOf(null) + NewsTopic.entries
                topics.forEach { topic ->
                    val control = button(topic?.label ?: "Station") { load(topic) }.apply {
                        id = View.generateViewId()
                        textSize = if (compactTv && publisher != null) 11f else 12f
                        maxLines = 1
                        nextFocusDownId = list.id
                    }
                    buttons[topic] = control
                    addView(control, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(4) })
                }
            }, LinearLayout.LayoutParams(-1, dp(40)))
            addView(FrameLayout(context).apply {
                addView(list, FrameLayout.LayoutParams(-1, -1))
                addView(status, FrameLayout.LayoutParams(-1, -1))
                list.emptyView = status
            }, LinearLayout.LayoutParams(-1, dp(if (compactTv) 150 else 240)).apply { topMargin = dp(6) })
        }
        val dialog = AlertDialog.Builder(this).setTitle("Actualités · ${publisher?.label ?: "RFI"}").setView(content)
            .setPositiveButton("Fermer", null).show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
        dialog.setOnDismissListener { request?.cancel() }
        val initial = if (publisher == null) NewsTopic.WORLD else null
        load(initial)
        buttons.getValue(initial).requestFocus()
    }

    private inner class NewsStoryRow : LinearLayout(this@RadioActivity) {
        private val title = label("", if (compactTv) 14 else 16, textColor, bold = true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        private val metadata = label("", 12, muted).apply { maxLines = 1 }
        private val photo = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(getColor(R.color.radio_control))
            }
            clipToOutline = true
        }
        private var photoTask: Job? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            minimumHeight = dp(58)
            addView(photo, LayoutParams(dp(72), dp(46)).apply { marginEnd = dp(10) })
            addView(column().apply {
                addView(title, LayoutParams(-1, -2))
                addView(metadata, LayoutParams(-1, -2))
            }, LayoutParams(0, -2, 1f))
        }

        fun bind(article: NewsHeadline, dateFormat: DateTimeFormatter) {
            title.text = article.title
            metadata.text = listOfNotNull(article.publisher.label, article.publishedAt?.let(dateFormat::format)).joinToString(" · ")
            photoTask?.cancel()
            photo.setImageDrawable(null)
            photo.visibility = if (article.imageUrl == null) View.GONE else View.VISIBLE
            photoTask = article.imageUrl?.let { url ->
                artwork.load(url, dp(92)) { bitmap ->
                    photo.setImageBitmap(bitmap)
                    photo.visibility = if (bitmap == null) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun chooseSort() {
        val options = StationSort.options(tab)
        val selected = options.indexOf(activeSort())
        val dialog = AlertDialog.Builder(this).setTitle("Trier les stations")
            .setSingleChoiceItems(options.map { it.labelFor(tab) }.toTypedArray(), selected) { picker, index ->
                prefs.edit().putString("sort_${tab.name}", options[index].name).apply()
                picker.dismiss()
                updateDirectory(resetScroll = true)
            }.setNegativeButton("Fermer", null).show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.radio_dialog)
        if (television) dialog.listView.requestFocus()
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
                    country = shown[position]
                    prefs.edit().putString("world_country", country).apply()
                    dialog.dismiss()
                    if (query.isEmpty()) updateDirectory(resetScroll = true) else stationSearch.setText("")
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
        val dashboardPlayer = wide && !television && !mini && !full
        val horizontalMini = mini && (television || landscape)
        val splitListening = full && landscape && !television
        val compactSidePlayer = wide && shortLandscape && !full
        val tightSidePlayer = compactSidePlayer && tightLandscape
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
            val audioStrip = if (television) dp(if (compactTv) 17 else 21) else 0
            val frameHeight = motifHeight + audioStrip
            val artworkFrame = FrameLayout(context).apply {
                audioMotif = PremiumRadioMotif(context, artwork, belowLogo = television)
                if (television) {
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
                if (television && !mini) {
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
                        maxLines = if ((compactTv && !tvGuide) || mini || (full && (television || shortLandscape))) 1 else 2
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    addView(playerTitle, LinearLayout.LayoutParams(-1, -2))
                    val subtitleSize = if (tightSidePlayer) 11 else if (television && !compactTv) 16 else 13
                    playerSubtitle = label("", subtitleSize, muted).apply {
                        textAlignment = alignment
                        if (compactSidePlayer) includeFontPadding = false
                        maxLines = if (tightSidePlayer || tightListening || mini || compactTv) 1 else 2
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

    private fun playbackBar(wide: Boolean, full: Boolean, mini: Boolean): View = (if (wide) column() else row()).apply {
        gravity = Gravity.CENTER
        playerStatus = label("", 13, muted, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        statusBadge = row().apply {
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
                    addView(label("LIVE", 11, Color.WHITE, bold = true).apply {
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
        addView(statusBadge, if (wide) LinearLayout.LayoutParams(-1, dp(if (compactTv) 28 else 33)).apply { bottomMargin = dp(4) }
            else LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        if (wide && !television && !mini && !full) {
            addView(View(context).apply {
                setBackgroundColor(getColor(R.color.radio_list_rule))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(8); marginEnd = dp(8) })
        }
        val controlHeight = dp(if (shortLandscape) 42 else if (compactTv || !wide) 44 else if (television) 54 else 48)
        addView(row().apply {
            gravity = Gravity.CENTER
            previous = transportButton(R.drawable.ic_radio_previous, "Station précédente") { skip(-1) }
            playButton = transportButton(R.drawable.ic_radio_play, "Écouter", primary = true) { toggle() }
            next = transportButton(R.drawable.ic_radio_next, "Station suivante") { skip(1) }
            fun controlParams(width: Int, weight: Float = 1f) = if (wide && (!full || !television)) {
                LinearLayout.LayoutParams(0, controlHeight, weight)
            } else LinearLayout.LayoutParams(width, controlHeight)
            addView(previous, controlParams(controlHeight))
            addView(playButton, controlParams(dp(if (television && wide) 66 else 56), 1.18f).apply {
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
                addView(favoriteButton, controlParams(controlHeight).apply { marginStart = dp(6) })
            }
        }, LinearLayout.LayoutParams(if (wide) -1 else -2, controlHeight))
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
            compactTv -> (resources.configuration.screenHeightDp * .16).toInt().coerceIn(57, 66)
            television -> (resources.configuration.screenHeightDp * .18).toInt().coerceIn(72, 134)
            shortLandscape -> 66
            else -> 96
        }
        private val logo = StationArtwork(18)
        private val idleBackground = pressable(if (tvGuide) R.drawable.radio_station_rule else R.drawable.radio_control)
        private val selectedBackground = pressable(R.drawable.radio_selected)
        private val name = label("", when {
            tvGuide && !compactTv -> 14
            tvGuide || compactTv || shortLandscape -> 13
            television -> 17
            else -> 15
        }, textColor, bold = true).apply {
            gravity = if (tvGuide) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val subtitle = label("", when {
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

        init {
            layoutParams = AbsListView.LayoutParams(-1, dp(heightDp))
            background = idleBackground
            if (tvGuide) {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(7), 0, dp(7), 0)
                val artSize = dp(heightDp - 6)
                addView(logo, LinearLayout.LayoutParams(artSize, artSize).apply { marginEnd = dp(8) })
                addView(column().apply {
                    addView(name, LinearLayout.LayoutParams(-1, -2))
                    addView(subtitle, LinearLayout.LayoutParams(-1, -2))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                playingMark?.let { addView(it, LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginStart = dp(4) }) }
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
            stateDescription = if (playing) "En écoute" else if (selected) "Sélectionnée" else null
            if (tvGuide) layoutDirection = directionOf(station.name)
            logo.bind(station)
            name.text = station.name
            name.setTextColor(if (selected) accent else textColor)
            subtitle.text = when {
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
            subtitle.text = Stations.subtitle(station)
            subtitle.setTextColor(if (playing) accent else muted)
            star?.let {
                bindFavorite(it, station)
                it.setRadioClick { toggleFavorite(station) }
            }
        }
    }

    private inner class StationArtwork(textSize: Int, private val showCredits: Boolean = false) : FrameLayout(this@RadioActivity) {
        private val initials = label("FM", textSize, accent, bold = true).apply { gravity = Gravity.CENTER }
        private val logo = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.GONE }
        private var logoWork: Job? = null
        private var artworkKey: String? = null
        private var artworkGeneration = -1

        init {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            if (showCredits) remoteFocus()
            addView(initials, LayoutParams(-1, -1))
            addView(logo, LayoutParams(-1, -1).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }

        fun bind(station: RadioStation?) {
            if (artworkKey == station?.url && artworkGeneration == artwork.generation) return
            artworkKey = station?.url
            artworkGeneration = artwork.generation
            logoWork?.cancel()
            initials.text = when {
                station == null -> "FM"
                Stations.isReciter(station) -> "ق"
                else -> station.name.split(' ').take(2).joinToString("") { it.take(1) }
            }
            initials.visibility = View.VISIBLE
            logo.visibility = View.GONE
            logo.setImageDrawable(null)
            val portrait = station?.let(ReciterPortraits::forStation)
            bindCredit(station, portrait)
            if (portrait != null) {
                logo.setImageResource(portrait.image)
                logo.visibility = View.VISIBLE
                initials.visibility = View.GONE
                return
            }
            if (station == null || Stations.isReciter(station)) {
                logo.setImageResource(if (station == null) R.drawable.ic_radio else R.drawable.ic_tab_quran)
                logo.visibility = View.VISIBLE
                initials.visibility = View.GONE
            }
            val targetPixels = layoutParams?.let { maxOf(it.width, it.height) - dp(6) } ?: 128
            logoWork = artwork.load(station?.logoUrl, targetPixels) { bitmap ->
                if (artworkKey == station?.url && bitmap != null) {
                    logo.setImageBitmap(bitmap)
                    logo.visibility = View.VISIBLE
                    initials.visibility = View.GONE
                }
            }
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
            artworkKey = null
            super.onDetachedFromWindow()
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
            tab == StationTab.MOROCCO && !radioFavoritesOnly &&
                activeCategory() in setOf(MoroccoCategory.ALL, MoroccoCategory.DIRECTORY) -> "MA"
            tab == StationTab.WORLD -> country
            else -> null
        }
        directory.open(code?.let { StationDirectory.Query(it, query, activeSort()) })
        renderRows(resetScroll)
    }

    private fun renderRows(resetScroll: Boolean = false) {
        val sort = activeSort()
        val category = activeCategory()
        rows = Stations.visible(tab, favorites, query, radioFavoritesOnly, directory.stations, directoryBookmarks, sort, category)
        PlaybackService.setVisibleStations(rows)
        listEntries = buildList {
            var previousSection: String? = null
            rows.forEachIndexed { index, station ->
                val section = when {
                    tab == StationTab.QURAN -> Stations.quranSection(station)?.label
                    tab == StationTab.MOROCCO && sort == StationSort.GROUPS -> Stations.group(station).label
                    tab == StationTab.MOROCCO && sort == StationSort.DEFAULT && category == MoroccoCategory.ARABIC ->
                        Stations.musicCategory(station)?.label
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
        sortButton.visibility = if (tab == StationTab.QURAN) View.GONE else View.VISIBLE
        sortButton.contentDescription = "Trier les stations · ${sort.labelFor(tab)}"
        categoryButton.visibility = if (onRadioTab) View.VISIBLE else View.GONE
        categoryButton.isSelected = category != MoroccoCategory.ALL
        categoryButton.text = if (categoryButton.isSelected) "Genres ▾" else "Genres"
        categoryButton.contentDescription = "Catégories Radio Maroc · ${category.label}"
        countryButton.visibility = if (tab == StationTab.WORLD) View.VISIBLE else View.GONE
        countryButton.text = "${StationDirectory.countryName(country)} ▾"
        countryButton.contentDescription = "Choisir un continent ou un pays · ${StationDirectory.countryName(country)}"
        radioFilter.visibility = if (onRadioTab && !television) View.VISIBLE else View.GONE
        radioFilter.isSelected = radioFavoritesOnly
        radioFilter.contentDescription =
            if (radioFavoritesOnly) "Afficher toutes les radios marocaines" else "Afficher les radios marocaines favorites"
        radioFilter.background = pressable(if (radioFavoritesOnly) R.drawable.radio_selected else R.drawable.radio_control)
        empty.text = when {
            onRadioTab && radioFavoritesOnly && query.isBlank() -> "Aucune radio marocaine favorite"
            tab == StationTab.FAVORITES && favorites.isEmpty() -> "Utilise l’étoile pour ajouter une station"
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
            if (television) styleTvCategory(button)
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
            if (television) list.setSelection(0) else list.setSelectionFromTop(0, 0)
        }
        renderPlayer()
    }

    private fun styleTvCategory(button: TextView) {
        button.background = pressable(if (button.isSelected) R.drawable.radio_selected else R.drawable.radio_tab)
        button.setTextColor(if (button.isSelected) accent else textColor)
    }

    private fun renderPlayer() {
        val player = controller
        val active = player?.currentMediaItem?.mediaId ?: savedStation?.takeIf { it == Stations.luxe }?.url
        val station = findStation(active) ?: savedStation
        val luxe = if (station == Stations.luxe) luxeState() else null
        val recording = station?.let(Stations::isRecording) == true
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
        if (television) updateTvStationInfo(station, programme?.title ?: streamInfo?.getString(PlaybackService.STREAM_TITLE))
        playerCategory?.apply {
            text = if (recording) "تلاوات الحرم" else programme?.source.orEmpty()
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        playerPanel.layoutDirection = directionOf(station?.name.orEmpty())
        playerTitle.text = station?.name ?: "Choisir une station"
        playerSubtitle.text = programme?.let {
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
            waitingForNetwork && !playing -> "Connexion perdue · reprise automatique"
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
        choiceDialog("Démarrage voiture")
            .setSingleChoiceItems(modes.map { it.label }.toTypedArray(), carResume().ordinal) { dialog, index ->
                prefs.edit().putString("car_resume_mode", modes[index].name).apply {
                    if (modes[index] == CarResume.AFTER_BOOT) {
                        val boot = carBootCount()
                        if (boot >= 0) putInt("car_played_boot", boot)
                    }
                }.apply()
                startupDeadline = 0L
                dialog.dismiss()
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun showAppearance() {
        val appearance = Appearance.restore(prefs)
        val options = Appearance.entries
        choiceDialog("Apparence")
            .setSingleChoiceItems(options.map { it.label }.toTypedArray(), appearance.ordinal) { dialog, index ->
                dialog.dismiss()
                val selected = options[index]
                if (selected != appearance) {
                    prefs.edit().putString(APPEARANCE, selected.name).apply()
                    recreate()
                }
            }
            .setNegativeButton("Fermer", null)
            .show()
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
            livePulse?.cancel()
            livePulse = null
            liveDot.alpha = 1f
        } else if (livePulse == null) {
            livePulse = ObjectAnimator.ofFloat(liveDot, View.ALPHA, 1f, .2f).apply {
                duration = 650
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }

    private fun select(station: RadioStation) {
        val player = controller ?: return
        startupDeadline = 0L
        savedStation = station
        persistStations(station)
        if (station == Stations.luxe) {
            player.sendCustomCommand(PlaybackService.LUXE_PLAY, Bundle.EMPTY)
        } else {
            player.setMediaItem(Stations.item(station))
            player.prepare()
            player.play()
        }
        renderPlayer()
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
        player.sessionExtras.getBoolean(PlaybackService.NETWORK_UNAVAILABLE) &&
            player.currentMediaItem != null && player.playWhenReady && player.playbackState != Player.STATE_ENDED

    private fun directionOf(text: String): Int =
        if (TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

    private fun skip(direction: Int) {
        val current = controller?.currentMediaItem?.mediaId ?: savedStation?.url
        RadioQueue.neighbor(rows, current, direction)?.let(::select)
    }

    private fun enable(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else .45f
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(value: String, action: () -> Unit) = label(value, 14, textColor, bold = true).apply {
        gravity = Gravity.CENTER
        background = pressable(R.drawable.radio_control)
        isFocusable = true
        remoteFocus()
        setRadioClick(action)
    }

    private fun View.setRadioClick(action: () -> Unit) {
        isSoundEffectsEnabled = false
        setOnClickListener {
            RadioSound.playClick(this@RadioActivity, prefs)
            action()
        }
    }

    private fun View.remoteFocus() {
        if (television) foreground = getDrawable(R.drawable.radio_focus)
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun pressable(drawable: Int? = null) = RippleDrawable(ripple, drawable?.let(::getDrawable),
        GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() })
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
