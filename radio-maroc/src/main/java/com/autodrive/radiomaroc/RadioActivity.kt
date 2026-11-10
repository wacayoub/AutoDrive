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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
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

class RadioActivity : ComponentActivity() {
    private companion object {
        const val PREFERENCES = "radio_maroc"
        const val DAY_APPEARANCE = "day_appearance"
    }

    private enum class CarResume(val label: String) {
        OFF("Désactivée"),
        AFTER_BOOT("Après redémarrage Android"),
        ON_OPEN("À chaque ouverture"),
    }

    private enum class TvLayout(val label: String) {
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
    private var tvLayout = TvLayout.GUIDE
    private var tvCatalogue = TvLayout.GUIDE
    private var tvRegions = false
    private var tvStationFocus: String? = null
    private var tvRegionalButton: TextView? = null
    private lateinit var tvLayoutButton: TextView
    private lateinit var tvBackButton: TextView
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
    private lateinit var radioFilter: TextView
    private lateinit var countryButton: TextView
    private lateinit var stationSearch: EditText
    private lateinit var listContext: TextView
    private lateinit var listToolbar: LinearLayout
    private lateinit var sortButton: TextView
    private lateinit var directoryAction: TextView
    private lateinit var list: AbsListView
    private lateinit var empty: TextView
    private lateinit var playerTitle: TextView
    private lateinit var playerPanel: LinearLayout
    private lateinit var playerSubtitle: TextView
    private var playerCategory: TextView? = null
    private lateinit var playerArtwork: StationArtwork
    private lateinit var playerStatus: TextView
    private lateinit var statusBadge: LinearLayout
    private lateinit var liveIndicator: LinearLayout
    private lateinit var liveDot: View
    private var livePulse: ObjectAnimator? = null
    private lateinit var playButton: ImageButton
    private lateinit var previous: ImageButton
    private lateinit var next: ImageButton
    private var playerFavorite: ImageButton? = null
    private val television: Boolean
        get() = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    private val compactTv: Boolean
        get() = television && (resources.configuration.screenHeightDp < 430 || resources.configuration.screenWidthDp < 760)
    private val showingCatalogue get() = !television || tvLayout != TvLayout.LISTENING
    private val catalogueAdapter get() = if (television && tvLayout == TvLayout.MOSAIC) tileAdapter else stationAdapter
    private val returnFromListening = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = openTvLayout(tvCatalogue)
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
        val day = newBase.getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(DAY_APPEARANCE, false)
        val configuration = Configuration().apply {
            uiMode = if (day) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        artwork = RadioArtwork(this, maxImageBytes = 1024 * 1024)
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
        if (television) {
            tvLayout = TvLayout.restore(savedInstanceState?.getString("tv_screen") ?: prefs.getString("tv_layout", null))
            tvCatalogue = TvLayout.restore(prefs.getString("tv_catalogue", null)).takeUnless { it == TvLayout.LISTENING }
                ?: TvLayout.GUIDE
            tvRegions = tab == StationTab.MOROCCO && savedInstanceState?.getBoolean("tv_regions") == true
            tvStationFocus = savedInstanceState?.getString("tv_station_focus")
            onBackPressedDispatcher.addCallback(this, returnFromListening)
            returnFromListening.isEnabled = !showingCatalogue
        }
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        window.setDecorFitsSystemWindows(false)
        setContentView(screen())
        window.decorView.addOnUnhandledKeyEventListener { _, event -> handleStationKey(event) }
        updateDirectory()
        if (television) restoreTvFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query)
        outState.putBoolean("radio_favorites_only", radioFavoritesOnly)
        outState.putLong("startup_deadline", startupDeadline)
        if (television) {
            rememberTvFocus()
            outState.putString("tv_screen", tvLayout.name)
            outState.putBoolean("tv_regions", tvRegions)
            outState.putString("tv_station_focus", tvStationFocus)
        }
        radioUpdates.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
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
        setOnApplyWindowInsetsListener { view, insets ->
            val edges = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            val horizontal = dp(if (compactTv) 22 else if (television) 40 else 10)
            val vertical = dp(if (compactTv) 10 else if (television) 24 else 6)
            view.setPadding(edges.left + horizontal, edges.top + vertical, edges.right + horizontal, edges.bottom + vertical)
            insets
        }
        val wide = television || (resources.configuration.screenWidthDp >= 560 &&
            resources.configuration.screenWidthDp > resources.configuration.screenHeightDp)
        val compactMasthead = wide && !television
        val masthead = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(8), 0)
            val iconSize = dp(if (television && !compactTv) 46 else 36)
            addView(ImageView(context).apply { setImageResource(R.drawable.ic_radio) }, LinearLayout.LayoutParams(iconSize, iconSize))
            addView(column().apply {
                setPadding(dp(9), 0, dp(4), 0)
                val titleSize = if (compactTv) 20 else if (television) 25 else 16
                addView(label(getString(R.string.app_name), titleSize, textColor, bold = true).apply {
                    maxLines = if (compactMasthead) 2 else 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                if (!compactMasthead) addView(label("RADIOS & RÉCITATIONS", if (television && !compactTv) 12 else 9, muted, bold = true).apply {
                    letterSpacing = .10f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (television) {
                tvLayoutButton = button("Affichage TV", ::showTvLayouts).apply {
                    id = View.generateViewId()
                    contentDescription = "Affichage TV · ${tvLayout.label}"
                }
                addView(tvLayoutButton, LinearLayout.LayoutParams(dp(126), dp(48)).apply { marginEnd = dp(8) })
            }
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_radio_car_start)
                imageTintList = ColorStateList.valueOf(accent)
                scaleType = ImageView.ScaleType.CENTER
                background = pressable(R.drawable.radio_control)
                remoteFocus()
                contentDescription = "Démarrage voiture"
                setOnClickListener { showCarStartup() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_radio_update)
                imageTintList = ColorStateList.valueOf(accent)
                scaleType = ImageView.ScaleType.CENTER
                background = pressable(R.drawable.radio_control)
                remoteFocus()
                contentDescription = "Mises à jour"
                setOnClickListener { radioUpdates.show() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        if (television) {
            addView(masthead, LinearLayout.LayoutParams(-1, dp(if (compactTv) 48 else 56)).apply { bottomMargin = dp(8) })
            addView(tvContent(), LinearLayout.LayoutParams(-1, 0, 1f))
            return@apply
        }
        val categories = categoryBar()
        val headerHeight = dp(if (wide) 52 else 54)
        if (wide && !television) {
            addView(wideColumns(masthead, categories).apply {
                background = getDrawable(R.drawable.radio_header)
            }, LinearLayout.LayoutParams(-1, headerHeight).apply { bottomMargin = dp(8) })
        } else {
            addView(masthead, LinearLayout.LayoutParams(-1, dp(if (television && !compactTv) 60 else 48)))
            addView(categories, LinearLayout.LayoutParams(-1, headerHeight).apply { bottomMargin = dp(8) })
        }
        val content = when {
            wide -> wideColumns(stationPanel(true), stationList())
            else -> column().apply {
                addView(stationList(), LinearLayout.LayoutParams(-1, 0, 1f))
                addView(stationPanel(false), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
        }
        addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        requestFocus()
    }

    private fun tvContent(): View = when (tvLayout) {
        TvLayout.GUIDE -> row().apply {
            addView(tvCategoryBar(sidebar = true), LinearLayout.LayoutParams(dp(if (compactTv) 126 else 168), -1)
                .apply { marginEnd = dp(12) })
            addView(stationList(), LinearLayout.LayoutParams(0, -1, .62f).apply { marginEnd = dp(12) })
            addView(stationPanel(true), LinearLayout.LayoutParams(0, -1, .38f))
            list.nextFocusRightId = playButton.id
            previous.nextFocusLeftId = list.id
        }
        TvLayout.MOSAIC -> column().apply {
            addView(tvCategoryBar(sidebar = false), LinearLayout.LayoutParams(-1, dp(if (compactTv) 38 else 44))
                .apply { bottomMargin = dp(5) })
            addView(stationList(grid = true), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(stationPanel(false, mini = true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        TvLayout.LISTENING -> column().apply {
            tvBackButton = button("‹  Toutes les radios") { openTvLayout(tvCatalogue) }.apply {
                id = View.generateViewId()
                contentDescription = "Toutes les radios"
                nextFocusUpId = tvLayoutButton.id
            }
            tvLayoutButton.nextFocusDownId = tvBackButton.id
            addView(tvBackButton, LinearLayout.LayoutParams(dp(174), dp(42)))
            addView(stationPanel(true, full = true), LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun tvCategoryBar(sidebar: Boolean): LinearLayout = (if (sidebar) column() else row()).apply {
        val bar = this
        if (sidebar) {
            addView(label("CATÉGORIES", 12, accent, bold = true).apply { setPadding(dp(10), 0, 0, 0) },
                LinearLayout.LayoutParams(-1, dp(24)))
        }
        fun addCategory(title: String, icon: Int, action: () -> Unit): TextView = button(title, action).apply {
            id = View.generateViewId()
            nextFocusUpId = if (!sidebar || bar.childCount == 1) tvLayoutButton.id else View.NO_ID
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setAutoSizeTextTypeUniformWithConfiguration(11, if (compactTv) 15 else 18, 1, TypedValue.COMPLEX_UNIT_SP)
            gravity = if (sidebar) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            setPadding(dp(9), 0, dp(5), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            compoundDrawableTintList = getColorStateList(R.color.radio_tab_icon)
            compoundDrawablePadding = dp(6)
            bar.addView(this, if (sidebar) {
                LinearLayout.LayoutParams(-1, dp(if (compactTv) 42 else 48)).apply { bottomMargin = dp(4) }
            } else {
                LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(6) }
            })
        }
        tabs = StationTab.entries.associateWith { choice ->
            val button = addCategory(choice.label, stationTabIcon(choice)) { selectTab(choice) }
            if (choice == StationTab.MOROCCO) {
                tvRegionalButton = addCategory("Régionales", R.drawable.ic_radio_region) { selectTab(choice, regional = true) }
            }
            button
        }
    }

    private fun selectTab(choice: StationTab, regional: Boolean = false) {
        if (tab != choice || tvRegions != regional) tvStationFocus = null
        tab = choice
        tvRegions = regional
        prefs.edit().putString("tab", choice.name).apply()
        updateDirectory(resetScroll = true)
    }

    private fun stationTabIcon(choice: StationTab): Int = when (choice) {
        StationTab.MOROCCO -> R.drawable.ic_tab_radio
        StationTab.WORLD -> R.drawable.ic_tab_world
        StationTab.QURAN -> R.drawable.ic_tab_quran
        StationTab.FAVORITES -> R.drawable.ic_tab_favorites
    }

    private fun showTvLayouts() {
        val layouts = TvLayout.entries
        AlertDialog.Builder(this)
            .setTitle("Affichage TV")
            .setSingleChoiceItems(layouts.map { it.label }.toTypedArray(), tvLayout.ordinal) { dialog, index ->
                dialog.dismiss()
                val layout = layouts[index]
                prefs.edit().putString("tv_layout", layout.name).apply()
                openTvLayout(layout)
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun openTvLayout(layout: TvLayout) {
        if (layout == tvLayout) return
        rememberTvFocus()
        tvLayout = layout
        if (layout != TvLayout.LISTENING) {
            tvCatalogue = layout
            prefs.edit().putString("tv_catalogue", layout.name).apply()
        }
        returnFromListening.isEnabled = !showingCatalogue
        livePulse?.cancel()
        livePulse = null
        setContentView(screen())
        renderRows()
        restoreTvFocus()
    }

    private fun stationAt(position: Int): RadioStation? = if (television && tvLayout == TvLayout.MOSAIC) {
        rows.getOrNull(position)
    } else {
        (listEntries.getOrNull(position) as? ListEntry.Station)?.radio
    }

    private fun rememberTvFocus() {
        if (showingCatalogue) stationAt(list.selectedItemPosition)?.let { tvStationFocus = it.url }
    }

    private fun restoreTvFocus() {
        if (!showingCatalogue) {
            if (!playButton.requestFocus()) tvBackButton.requestFocus()
            return
        }
        val station = tvStationFocus
        val position = if (station == null) -1 else (0 until catalogueAdapter.count)
            .firstOrNull { stationAt(it)?.url == station } ?: -1
        if (position >= 0) {
            list.requestFocus()
            list.setSelection(position)
        } else {
            (if (tvRegions) tvRegionalButton else tabs[tab])?.requestFocus()
        }
    }

    private fun wideColumns(left: View, right: View): LinearLayout = row().apply {
        addView(left, LinearLayout.LayoutParams(0, -1, .36f).apply { marginEnd = dp(8) })
        addView(right, LinearLayout.LayoutParams(0, -1, .64f))
    }

    private fun categoryBar(): LinearLayout = row().apply {
        isBaselineAligned = false
        background = getDrawable(R.drawable.radio_category_line)
        val configuration = resources.configuration
        val inlineIcons = television || (configuration.screenWidthDp >= 460 && configuration.fontScale <= 1.3f)
        val maxTextSize = if (compactTv) 15 else if (television) 18 else if (configuration.screenWidthDp >= 650) 14 else 13
        val tabFont = Typeface.create(Typeface.DEFAULT, 600, false)
        tabs = StationTab.entries.associateWith { choice ->
            if (choice != StationTab.entries.first()) addView(View(context).apply {
                setBackgroundColor(getColor(R.color.radio_list_rule))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(1), dp(22)).apply { gravity = Gravity.CENTER_VERTICAL })
            button(choice.label) { selectTab(choice) }.also { view ->
                view.typeface = tabFont
                view.maxLines = 1
                view.ellipsize = TextUtils.TruncateAt.END
                view.setAutoSizeTextTypeUniformWithConfiguration(11, maxTextSize, 1, TypedValue.COMPLEX_UNIT_SP)
                view.background = pressable(R.drawable.radio_tab)
                view.setPadding(dp(if (inlineIcons) 8 else 2), dp(if (inlineIcons) 0 else 5), dp(2), dp(4))
                view.setTextColor(getColorStateList(R.color.radio_tab_text))
                val icon = stationTabIcon(choice)
                if (television) {
                    val iconSize = dp(if (compactTv) 19 else 24)
                    val symbol = getDrawable(icon)?.apply { setBounds(0, 0, iconSize, iconSize) }
                    view.setCompoundDrawablesRelative(null, symbol, null, null)
                    view.compoundDrawablePadding = dp(3)
                    view.setPadding(0, dp(4), 0, dp(4))
                } else if (inlineIcons) {
                    view.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    view.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    view.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
                    view.compoundDrawablePadding = dp(5)
                } else {
                    view.setCompoundDrawablesRelativeWithIntrinsicBounds(0, icon, 0, 0)
                }
                view.compoundDrawableTintList = getColorStateList(R.color.radio_tab_icon)
                val weight = if (choice == StationTab.MOROCCO) 1.35f else 1f
                addView(view, LinearLayout.LayoutParams(0, -1, weight))
            }
        }
    }

    private fun stationList(grid: Boolean = false): View = column().apply {
        listContext = label("", if (television && !compactTv) 13 else 11, accent, bold = true).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            letterSpacing = .08f
            setPadding(dp(4), 0, 0, 0)
        }
        sortButton = button("Trier", ::chooseSort).apply {
            textSize = 12f
            setPadding(dp(10), 0, dp(10), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_radio_sort, 0, 0, 0)
            compoundDrawablePadding = dp(6)
        }
        stationSearch = EditText(context).apply {
            hint = "Rechercher…"
            contentDescription = "Rechercher une station ou un récitateur"
            setSingleLine(true)
            textSize = if (television && !compactTv) 17f else 14f
            setTextColor(textColor)
            setHintTextColor(muted)
            setText(query)
            background = getDrawable(R.drawable.radio_control)
            remoteFocus()
            setPadding(dp(12), 0, dp(12), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_radio_search, 0, 0, 0)
            compoundDrawablePadding = dp(9)
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
        radioFilter = button("Favoris") {
            radioFavoritesOnly = !radioFavoritesOnly
            updateDirectory(resetScroll = true)
        }.apply {
            setPadding(dp(8), 0, dp(6), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_tab_favorites, 0, 0, 0)
        }
        countryButton = button("") { chooseContinent() }.apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8), 0, dp(6), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_tab_world, 0, 0, 0)
        }
        listToolbar = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(listContext, LinearLayout.LayoutParams(0, -1, 1f))
            if (television) {
                addView(stationSearch, LinearLayout.LayoutParams(0, -1, 1.4f))
                addView(countryButton, LinearLayout.LayoutParams(dp(if (compactTv) 104 else 128), -1)
                    .apply { marginStart = dp(6) })
            }
            addView(sortButton, LinearLayout.LayoutParams(dp(if (compactTv) 74 else 92), -1)
                .apply { if (television) marginStart = dp(6) })
        }
        addView(listToolbar, LinearLayout.LayoutParams(-1, dp(if (compactTv) 38 else if (television) 44 else 36))
            .apply { bottomMargin = dp(if (television) 6 else 4) })
        if (!television) {
            addView(row().apply {
                addView(stationSearch, LinearLayout.LayoutParams(0, -1, 1f))
                addView(countryButton, LinearLayout.LayoutParams(dp(128), -1).apply { marginStart = dp(6) })
                addView(radioFilter, LinearLayout.LayoutParams(dp(94), -1).apply { marginStart = dp(6) })
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        }
        addView(FrameLayout(context).apply {
            empty = label("", 15, muted).apply { gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0) }
            val catalog = if (grid) GridView(context).apply {
                numColumns = if (resources.configuration.screenWidthDp < 760) 3 else 4
                horizontalSpacing = dp(10)
                verticalSpacing = dp(8)
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
                if (television) {
                    setSelector(R.drawable.radio_focus)
                    setDrawSelectorOnTop(true)
                    setOnItemClickListener { _, _, position, _ ->
                        stationAt(position)?.let { station ->
                            tvStationFocus = station.url
                            select(station)
                        }
                    }
                }
                contentDescription = if (grid) "Mosaïque des stations" else "Liste des stations"
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

    private fun stationPanel(wide: Boolean, mini: Boolean = false, full: Boolean = false): View =
        (if (mini) row() else column()).apply {
        playerCategory = null
        if (!full) background = getDrawable(R.drawable.radio_player)
        if (mini) gravity = Gravity.CENTER_VERTICAL
        if (full) gravity = Gravity.CENTER
        val padding = dp(if (compactTv) 10 else if (television) 16 else 12)
        val topPadding = if (mini) 3 else if (television && !compactTv) 12 else 8
        val bottomPadding = if (mini) 3 else if (television && !compactTv) 14 else 8
        setPadding(padding, dp(topPadding), padding, dp(bottomPadding))
        val compactLandscape = compactTv || (wide && resources.configuration.screenHeightDp < 420)
        val listeningArtwork = if (full) {
            (resources.configuration.screenHeightDp - if (compactTv) 298 else 365).coerceIn(48, 140)
        } else 0
        val presentation = (if (wide) column() else row()).apply {
            playerPanel = this
            gravity = if (wide) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
            if (wide) addView(label("", if (television && !compactTv) 13 else 11, accent, bold = true).apply {
                gravity = Gravity.CENTER
                letterSpacing = .08f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                playerCategory = this
            }, LinearLayout.LayoutParams(-1, -2))
            playerArtwork = StationArtwork(if (wide) 26 else 16, showCredits = true)
            val artworkSize = dp(when {
                full -> listeningArtwork
                !wide -> 48
                compactTv -> 46
                compactLandscape -> 54
                television -> 92
                else -> 76
            })
            val motifHeight = dp(when {
                full -> listeningArtwork + 8
                !wide -> 56
                compactTv -> 54
                compactLandscape -> 72
                television -> 130
                else -> 122
            })
            addView(FrameLayout(context).apply {
                addView(PremiumRadioMotif(context), FrameLayout.LayoutParams(-1, -1))
                addView(playerArtwork, FrameLayout.LayoutParams(artworkSize, artworkSize, Gravity.CENTER))
            }, LinearLayout.LayoutParams(if (wide) -1 else dp(76), motifHeight).apply {
                if (wide) {
                    topMargin = dp(if (compactLandscape) 4 else 5)
                    bottomMargin = dp(if (compactLandscape) 4 else 6)
                } else marginEnd = dp(10)
            })
            val alignment = if (wide) View.TEXT_ALIGNMENT_CENTER else View.TEXT_ALIGNMENT_VIEW_START
            addView(column().apply {
                val titleSize = when {
                    full -> if (compactTv) 26 else 36
                    !wide -> 17
                    compactLandscape -> 19
                    television -> 25
                    else -> 21
                }
                playerTitle = label("", titleSize, textColor, bold = true).apply {
                    textAlignment = alignment
                    maxLines = if (compactTv || full) 1 else 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                addView(playerTitle, LinearLayout.LayoutParams(-1, -2))
                playerSubtitle = label("", if (television && !compactTv) 16 else 12, muted).apply {
                    textAlignment = alignment
                    maxLines = if (compactLandscape || full) 1 else 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                addView(playerSubtitle, LinearLayout.LayoutParams(-1, -2))
            }, if (wide) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
        }
        val presentationSize = when {
            mini -> LinearLayout.LayoutParams(0, -2, 1f)
            wide -> LinearLayout.LayoutParams(-1, 0, 1f)
            else -> LinearLayout.LayoutParams(-1, -2)
        }
        addView(presentation, presentationSize)
        addView(playbackBar(wide, full, mini), LinearLayout.LayoutParams(if (mini) dp(if (compactTv) 280 else 380)
            else if (full) dp(310) else -1, -2).apply {
            if (!wide && !mini || full) topMargin = dp(6)
        })
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
        val controlHeight = dp(if (compactTv || television && !wide) 44 else if (television) 54 else 48)
        addView(row().apply {
            gravity = Gravity.CENTER
            previous = transportButton(R.drawable.ic_radio_previous, "Station précédente") { skip(-1) }
            playButton = transportButton(R.drawable.ic_radio_play, "Écouter", primary = true) { toggle() }
            next = transportButton(R.drawable.ic_radio_next, "Station suivante") { skip(1) }
            fun controlParams(width: Int, weight: Float = 1f) = if (television && wide && !full) {
                LinearLayout.LayoutParams(0, controlHeight, weight)
            } else LinearLayout.LayoutParams(width, controlHeight)
            addView(previous, controlParams(controlHeight))
            addView(playButton, controlParams(dp(if (television && wide) 66 else 56), 1.18f).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            })
            addView(next, controlParams(controlHeight))
            if (television) {
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
            imageTintList = ColorStateList.valueOf(if (primary) 0xFF132A2A.toInt() else textColor)
            scaleType = ImageView.ScaleType.CENTER
            background = pressable(if (primary) R.drawable.radio_primary else R.drawable.radio_control)
            contentDescription = description
            isFocusable = true
            remoteFocus()
            setOnClickListener { action() }
        }

    private sealed interface ListEntry {
        data class Heading(val title: String, val rightAligned: Boolean) : ListEntry
        data class Station(val radio: RadioStation, val number: Int) : ListEntry
    }

    private val stationAdapter = object : BaseAdapter() {
        override fun getCount(): Int = listEntries.size
        override fun getItem(position: Int): ListEntry = listEntries[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun areAllItemsEnabled(): Boolean = false
        override fun isEnabled(position: Int): Boolean = getItem(position) is ListEntry.Station
        override fun getViewTypeCount(): Int = 2
        override fun getItemViewType(position: Int): Int = if (isEnabled(position)) 0 else 1
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
        }
    }

    private val tileAdapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View =
            (recycled as? StationTile ?: StationTile()).apply { bind(getItem(position)) }
    }

    private inner class StationTile : LinearLayout(this@RadioActivity) {
        private val heightDp = if (compactTv) {
            (resources.configuration.screenHeightDp * .16).toInt().coerceIn(57, 66)
        } else (resources.configuration.screenHeightDp * .18).toInt().coerceIn(72, 134)
        private val logo = StationArtwork(18)
        private val name = label("", if (compactTv) 13 else 17, textColor, bold = true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val subtitle = label("", if (compactTv) 10 else 12, muted).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            layoutParams = AbsListView.LayoutParams(-1, dp(heightDp))
            background = getDrawable(R.drawable.radio_control)
            setPadding(dp(5), dp(3), dp(5), dp(3))
            val artSize = dp((heightDp - if (compactTv) 37 else 47).coerceIn(20, 68))
            addView(logo, LinearLayout.LayoutParams(artSize, artSize).apply { bottomMargin = dp(2) })
            addView(name, LinearLayout.LayoutParams(-1, -2))
            addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        }

        fun bind(station: RadioStation) {
            val active = station.url == highlighted?.first && highlighted?.second == true
            contentDescription = if (active) "Écouter ${station.name} · En écoute" else "Écouter ${station.name}"
            logo.bind(station)
            name.text = station.name
            name.setTextColor(if (active) accent else textColor)
            subtitle.text = if (active) "En écoute" else Stations.subtitle(station)
        }
    }

    private inner class StationLine(context: Context) : LinearLayout(context) {
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
            background = RippleDrawable(ripple, null, ColorDrawable(Color.WHITE))
            addView(FrameLayout(context).apply {
                addView(selectionMark, FrameLayout.LayoutParams(dp(2), dp(20), Gravity.START or Gravity.CENTER_VERTICAL)
                    .apply { marginStart = dp(2) })
                addView(number, FrameLayout.LayoutParams(-1, -1))
                addView(playingIcon, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(38), dp(44)))
            val largeArt = television && !compactTv
            addView(badge, LinearLayout.LayoutParams(dp(if (largeArt) 54 else 44), dp(if (largeArt) 50 else 40)).apply {
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
            layoutDirection = directionOf(station.name)
            selectionMark.visibility = if (selected) View.VISIBLE else View.GONE
            contentDescription = "Écouter ${station.name}"
            if (!television) setOnClickListener { select(station) }
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
                it.setOnClickListener { toggleFavorite(station) }
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
            background = getDrawable(R.drawable.radio_control)
            clipToOutline = true
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
            background = getDrawable(R.drawable.radio_control)
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
            logoWork = artwork.load(station?.logoUrl) { bitmap ->
                if (artworkKey == station?.url && bitmap != null) {
                    background = getDrawable(R.drawable.radio_picon)
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
            tab == StationTab.MOROCCO && !radioFavoritesOnly && !tvRegions -> "MA"
            tab == StationTab.WORLD -> country
            else -> null
        }
        directory.open(code?.let { StationDirectory.Query(it, query, activeSort()) })
        renderRows(resetScroll)
    }

    private fun renderRows(resetScroll: Boolean = false) {
        val sort = activeSort()
        rows = Stations.visible(tab, favorites, query, radioFavoritesOnly, directory.stations, directoryBookmarks, sort)
        if (tvRegions) rows = rows.filter { Stations.region(it) != null }
        PlaybackService.setVisibleStations(rows)
        listEntries = buildList {
            var previousSection: String? = null
            rows.forEachIndexed { index, station ->
                val section = when {
                    tab == StationTab.QURAN -> Stations.quranSection(station)?.label
                    tab == StationTab.MOROCCO -> {
                        val group = Stations.group(station)
                        if (sort == StationSort.GROUPS || group == StationGroup.REGIONAL) group.label
                        else "RADIOS NATIONALES ET WEBRADIOS"
                    }
                    else -> null
                }
                if (section != null && section != previousSection) add(ListEntry.Heading(section, tab == StationTab.QURAN))
                previousSection = section
                add(ListEntry.Station(station, index + 1))
            }
        }
        if (!showingCatalogue) {
            renderPlayer()
            return
        }
        val onRadioTab = tab == StationTab.MOROCCO
        listToolbar.visibility = if (!television && tab == StationTab.QURAN) View.GONE else View.VISIBLE
        listContext.visibility = if (compactTv && tvLayout == TvLayout.GUIDE) View.GONE else View.VISIBLE
        sortButton.visibility = if (tab == StationTab.QURAN) View.GONE else View.VISIBLE
        listContext.text = when (tab) {
            StationTab.MOROCCO -> if (tvRegions) "RADIOS RÉGIONALES" else "RADIO MAROC"
            StationTab.WORLD -> "MONDE · ${WorldContinent.forCountry(country)?.label.orEmpty()}"
            StationTab.FAVORITES -> "FAVORIS"
            StationTab.QURAN -> "القرآن"
        }
        sortButton.contentDescription = "Trier les stations · ${sort.labelFor(tab)}"
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
        directoryAction.text = when {
            directory.loading -> "Chargement de l’annuaire…"
            directory.failed -> "Réessayer l’annuaire"
            else -> "Charger plus de radios"
        }
        directoryAction.visibility = if (directory.hasMore) View.VISIBLE else View.GONE
        enable(directoryAction, !directory.loading)
        for ((choice, button) in tabs) {
            button.isSelected = choice == tab && !tvRegions
            if (television) styleTvCategory(button)
        }
        if (television) {
            tvRegionalButton?.let {
                it.isSelected = tvRegions
                styleTvCategory(it)
            }
            val category = if (tvRegions) tvRegionalButton else tabs[tab]
            tvLayoutButton.nextFocusDownId = category?.id ?: View.NO_ID
            if (tvLayout == TvLayout.GUIDE) {
                list.nextFocusLeftId = category?.id ?: View.NO_ID
                tabs.values.forEach { it.nextFocusRightId = list.id }
                tvRegionalButton?.nextFocusRightId = list.id
            }
        }
        catalogueAdapter.notifyDataSetChanged()
        if (resetScroll) list.setSelection(0)
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
        val live = playing && !recording
        val streamInfo = player?.sessionExtras?.takeIf { it.getString(PlaybackService.STREAM_STATION) == active }
        val programme = streamInfo?.getString(PlaybackService.PROGRAMME_SOURCE)?.let { source ->
            streamInfo.getString(PlaybackService.STREAM_TITLE)?.let { title ->
                BroadcastProgramme(title, source, streamInfo.getString(PlaybackService.PROGRAMME_HOURS))
            }
        } ?: station?.takeIf { live && it.url == active }
            ?.let { BroadcastProgrammes.fromStream(it, streamInfo?.getString(PlaybackService.STREAM_TITLE)) }
        playerCategory?.text = when {
            recording -> "تلاوات الحرم"
            programme != null -> programme.source
            else -> "LECTEUR RADIO"
        }
        playerPanel.layoutDirection = directionOf(station?.name.orEmpty())
        playerTitle.text = station?.name ?: "Choisir une station"
        playerSubtitle.text = programme?.let {
            listOfNotNull(it.title, it.hours, it.source.takeIf { playerCategory == null }).joinToString(" · ")
        } ?: station?.let(Stations::subtitle) ?: "Sélectionne une chaîne dans la liste"
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
        val highlight = (active ?: savedStation?.url) to playing
        if (highlighted != highlight) {
            highlighted = highlight
            if (showingCatalogue) catalogueAdapter.notifyDataSetChanged()
        }
    }

    private fun showCarStartup() {
        val modes = CarResume.entries
        AlertDialog.Builder(this)
            .setTitle("Démarrage voiture")
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
            .setNeutralButton("Jour / Nuit") { _, _ -> showAppearance() }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun showAppearance() {
        val day = prefs.getBoolean(DAY_APPEARANCE, false)
        AlertDialog.Builder(this)
            .setTitle("Apparence")
            .setSingleChoiceItems(arrayOf("Jour", "Nuit"), if (day) 0 else 1) { dialog, index ->
                dialog.dismiss()
                val selectedDay = index == 0
                if (selectedDay != day) {
                    prefs.edit().putBoolean(DAY_APPEARANCE, selectedDay).apply()
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
        setOnClickListener { action() }
    }

    private fun View.remoteFocus() {
        if (television) foreground = getDrawable(R.drawable.radio_focus)
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun pressable(drawable: Int) = RippleDrawable(ripple, getDrawable(drawable),
        GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() })
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
