package com.autodrive.radiomaroc

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextDirectionHeuristics
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.autodrive.launcher.RadioArtwork
import com.autodrive.launcher.RadioCatalogue
import com.autodrive.launcher.RadioQueue
import com.autodrive.launcher.RadioStation
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job

class RadioActivity : Activity() {
    private val accent = 0xFF79D3F6.toInt()
    private val textColor = 0xFFF5F6F7.toInt()
    private val muted = 0xFFB0BDC7.toInt()
    private val amber = 0xFFFFCF73.toInt()
    private lateinit var prefs: SharedPreferences
    private lateinit var artwork: RadioArtwork
    private lateinit var directory: DirectoryBrowser
    private var tab = StationTab.MOROCCO
    private var radioFavoritesOnly = false
    private var country = "FR"
    private var directoryBookmarks = emptyList<RadioStation>()
    private var favorites = emptySet<String>()
    private var query = ""
    private var rows = emptyList<RadioStation>()
    private var savedStation: RadioStation? = null
    private var highlighted: Pair<String?, Boolean>? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var connectionFailed = false
    private var piconsOffline = false
    private lateinit var tabs: Map<StationTab, TextView>
    private lateinit var radioFilter: TextView
    private lateinit var countryButton: TextView
    private lateinit var directoryAction: TextView
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var playerTitle: TextView
    private lateinit var playerPanel: LinearLayout
    private lateinit var playerSubtitle: TextView
    private var playerCategory: TextView? = null
    private lateinit var playerArtwork: StationArtwork
    private lateinit var playerStatus: TextView
    private lateinit var playButton: ImageButton
    private lateinit var previous: ImageButton
    private lateinit var next: ImageButton

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            renderPlayer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("radio_maroc", MODE_PRIVATE)
        artwork = RadioArtwork(this, maxImageBytes = 1024 * 1024)
        directory = DirectoryBrowser(StationDirectory(this)) { renderRows() }
        tab = StationTab.entries.firstOrNull { it.name == prefs.getString("tab", null) } ?: StationTab.MOROCCO
        country = prefs.getString("world_country", null)?.takeIf { it in StationDirectory.countries && it != "MA" } ?: "FR"
        favorites = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
        directoryBookmarks = StationDirectory.decode(prefs.getString("directory_bookmarks", null).orEmpty())
        savedStation = findStation(prefs.getString("last_station", null))
        query = savedInstanceState?.getString("query").orEmpty()
        radioFavoritesOnly = savedInstanceState?.getBoolean("radio_favorites_only") == true
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        window.setDecorFitsSystemWindows(false)
        setContentView(screen())
        updateDirectory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query)
        outState.putBoolean("radio_favorites_only", radioFavoritesOnly)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        connectPlayer()
    }

    override fun onStop() {
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    override fun onDestroy() {
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
                onNetworkStatus(controller?.sessionExtras ?: Bundle.EMPTY)
            }
        }, mainExecutor)
        renderPlayer()
    }

    private fun onNetworkStatus(extras: Bundle) {
        val offline = extras.getBoolean(PlaybackService.NETWORK_UNAVAILABLE)
        if (piconsOffline && !offline) {
            artwork.retryFailures()
            stationAdapter.notifyDataSetChanged()
            if (directory.failed) directory.loadMore()
        }
        piconsOffline = offline
        renderPlayer()
    }

    private fun screen(): LinearLayout = column().apply {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        background = getDrawable(R.drawable.radio_background)
        isFocusableInTouchMode = true
        setOnApplyWindowInsetsListener { view, insets ->
            val edges = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            view.setPadding(edges.left + dp(10), edges.top + dp(4), edges.right + dp(10), edges.bottom + dp(8))
            insets
        }
        val wide = resources.configuration.screenWidthDp >= 560 && resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
        val masthead = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(8), 0)
            addView(ImageView(context).apply { setImageResource(R.drawable.ic_radio) }, LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(label(getString(R.string.app_name), 16, textColor, bold = true).apply {
                setPadding(dp(8), 0, 0, 0)
                maxLines = 2
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val categories = row().apply {
            isBaselineAligned = false
            tabs = StationTab.entries.associateWith { choice ->
                button(choice.label) {
                    tab = choice
                    prefs.edit().putString("tab", choice.name).apply()
                    updateDirectory(resetScroll = true)
                }.also { view ->
                    view.textSize = 12f
                    addView(view, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
                }
            }
        }
        if (wide) {
            addView(row().apply {
                background = getDrawable(R.drawable.radio_header)
                addView(masthead, LinearLayout.LayoutParams(dp(205), -1))
                addView(categories, LinearLayout.LayoutParams(0, -1, 1f))
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        } else {
            addView(masthead, LinearLayout.LayoutParams(-1, dp(36)))
            addView(categories, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        }
        val content = if (wide) row() else column()
        if (wide) {
            content.addView(stationPanel(true), LinearLayout.LayoutParams(0, -1, .36f).apply { marginEnd = dp(8) })
            content.addView(stationList(), LinearLayout.LayoutParams(0, -1, .64f))
        } else {
            content.addView(stationList(), LinearLayout.LayoutParams(-1, 0, 1f))
            content.addView(stationPanel(false), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        requestFocus()
    }

    private fun stationList(): View = column().apply {
        val search = EditText(context).apply {
            hint = "Rechercher…"
            contentDescription = "Rechercher une station ou un récitateur"
            setSingleLine(true)
            textSize = 14f
            setTextColor(textColor)
            setHintTextColor(muted)
            setText(query)
            background = getDrawable(R.drawable.radio_control)
            setPadding(dp(12), 0, dp(12), 0)
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
        radioFilter = button("★ Favoris") {
            radioFavoritesOnly = !radioFavoritesOnly
            updateDirectory(resetScroll = true)
        }
        countryButton = button("") { chooseCountry() }.apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        addView(row().apply {
            addView(search, LinearLayout.LayoutParams(0, -1, 1f))
            addView(countryButton, LinearLayout.LayoutParams(dp(128), -1).apply { marginStart = dp(6) })
            addView(radioFilter, LinearLayout.LayoutParams(dp(94), -1).apply { marginStart = dp(6) })
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        addView(FrameLayout(context).apply {
            empty = label("", 15, muted).apply { gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0) }
            list = ListView(context).apply {
                divider = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                dividerHeight = dp(4)
                itemsCanFocus = true
                contentDescription = "Liste des stations"
                adapter = stationAdapter
            }
            addView(list, FrameLayout.LayoutParams(-1, -1))
            addView(empty, FrameLayout.LayoutParams(-1, -1))
            list.emptyView = empty
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        directoryAction = button("") { directory.loadMore() }
        addView(directoryAction, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
    }

    private fun chooseCountry() {
        val countries = StationDirectory.countries.filter { it != "MA" }
        var shown = countries
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        val dialog = AlertDialog.Builder(this).setTitle("Choisir un pays").setNegativeButton("Fermer", null).create()
        val content = column().apply {
            setPadding(dp(16), 0, dp(16), 0)
            val input = EditText(context).apply {
                hint = "Rechercher un pays"
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                setOnEditorActionListener { _, action, _ ->
                    if (action != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
                    dialog.window?.insetsController?.hide(WindowInsets.Type.ime())
                    clearFocus()
                    true
                }
            }
            addView(input, LinearLayout.LayoutParams(-1, dp(48)))
            addView(ListView(context).apply {
                this.adapter = adapter
                setOnItemClickListener { _, _, position, _ ->
                    country = shown[position]
                    prefs.edit().putString("world_country", country).apply()
                    dialog.dismiss()
                    updateDirectory(resetScroll = true)
                }
            }, LinearLayout.LayoutParams(-1, dp(200)))
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
    }

    private fun stationPanel(wide: Boolean): View = column().apply {
        background = getDrawable(R.drawable.radio_player)
        setPadding(dp(12), dp(if (wide) 8 else 6), dp(12), dp(8))
        val presentation = (if (wide) column() else row()).apply {
            playerPanel = this
            gravity = if (wide) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
            if (wide) addView(label("", 11, accent, bold = true).apply {
                gravity = Gravity.CENTER
                playerCategory = this
            }, LinearLayout.LayoutParams(-1, -2))
            playerArtwork = StationArtwork(if (wide) 26 else 16, showCredits = true)
            val artworkSize = dp(when {
                !wide -> 48
                resources.configuration.screenHeightDp >= 420 -> 94
                else -> 64
            })
            addView(playerArtwork, LinearLayout.LayoutParams(artworkSize, artworkSize).apply {
                if (wide) {
                    topMargin = dp(5)
                    bottomMargin = dp(6)
                } else marginEnd = dp(10)
            })
            val alignment = if (wide) View.TEXT_ALIGNMENT_CENTER else View.TEXT_ALIGNMENT_VIEW_START
            addView(column().apply {
                playerTitle = label("", if (wide) 21 else 17, textColor, bold = true).apply {
                    textAlignment = alignment
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                addView(playerTitle, LinearLayout.LayoutParams(-1, -2))
                playerSubtitle = label("", 12, muted).apply {
                    textAlignment = alignment
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                addView(playerSubtitle, LinearLayout.LayoutParams(-1, -2))
            }, if (wide) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
        }
        addView(presentation, if (wide) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(-1, -2))
        addView(playbackBar(wide), LinearLayout.LayoutParams(-1, -2).apply {
            if (!wide) topMargin = dp(6)
        })
    }

    private fun playbackBar(wide: Boolean): View = (if (wide) column() else row()).apply {
        gravity = Gravity.CENTER
        playerStatus = label("", 13, muted, bold = true).apply {
            gravity = if (wide) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        addView(playerStatus, if (wide) LinearLayout.LayoutParams(-1, dp(37))
            else LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        addView(row().apply {
            gravity = Gravity.CENTER
            previous = transportButton(R.drawable.ic_radio_previous, "Station précédente") { skip(-1) }
            playButton = transportButton(R.drawable.ic_radio_play, "Écouter", primary = true) { toggle() }
            next = transportButton(R.drawable.ic_radio_next, "Station suivante") { skip(1) }
            addView(previous, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(playButton, LinearLayout.LayoutParams(dp(56), dp(48)).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            })
            addView(next, LinearLayout.LayoutParams(dp(48), dp(48)))
        }, LinearLayout.LayoutParams(if (wide) -1 else -2, dp(48)))
    }

    private fun transportButton(icon: Int, description: String, primary: Boolean = false, action: () -> Unit) =
        ImageButton(this).apply {
            setImageResource(icon)
            scaleType = ImageView.ScaleType.CENTER
            background = pressable(if (primary) R.drawable.radio_primary else R.drawable.radio_control)
            contentDescription = description
            isFocusable = true
            setOnClickListener { action() }
        }

    private val stationAdapter = object : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): RadioStation = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View =
            (recycled as? StationCard ?: StationCard(parent.context)).apply { bind(getItem(position), position + 1) }
    }

    private inner class StationCard(context: Context) : LinearLayout(context) {
        private val number = label("", 12, accent, bold = true).apply { gravity = Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        private val badge = StationArtwork(12)
        private val name = label("", 17, textColor, bold = true).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        private val subtitle = label("", 12, muted).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val star = button("☆") {}.apply { background = null; textSize = 21f; setTextColor(amber) }

        init {
            layoutParams = AbsListView.LayoutParams(-1, -2)
            minimumHeight = dp(52)
            gravity = Gravity.CENTER_VERTICAL
            addView(number, LinearLayout.LayoutParams(dp(34), -1))
            addView(badge, LinearLayout.LayoutParams(dp(46), dp(38)).apply { marginEnd = dp(9) })
            addView(column().apply { addView(name); addView(subtitle) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(star, LinearLayout.LayoutParams(dp(48), dp(48)))
        }

        fun bind(station: RadioStation, position: Int) {
            val selected = station.url == highlighted?.first
            val playing = selected && highlighted?.second == true
            layoutDirection = directionOf(station.name)
            background = pressable(if (selected) R.drawable.radio_selected else R.drawable.radio_card)
            isActivated = selected
            contentDescription = when {
                station in Stations.snrt -> "Ouvrir ${station.name} sur SNRT Live"
                Stations.officialPlayer(station) != null -> "Ouvrir le lecteur officiel de ${station.name}"
                else -> "Écouter ${station.name}"
            }
            setOnClickListener { select(station) }
            number.text = if (playing) "▶" else position.toString().padStart(2, '0')
            badge.bind(station)
            name.text = station.name
            subtitle.text = if (playing && !Stations.isRecording(station)) "En lecture" else Stations.subtitle(station)
            subtitle.setTextColor(if (playing) accent else muted)
            val favorite = station.url in favorites
            star.text = if (favorite) "★" else "☆"
            star.contentDescription = if (favorite) "Retirer ${station.name} des favoris" else "Ajouter ${station.name} aux favoris"
            star.setOnClickListener {
                favorites = if (favorite) favorites - station.url else favorites + station.url
                persistStations(station)
                renderRows()
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
            tab == StationTab.MOROCCO && !radioFavoritesOnly -> "MA"
            tab == StationTab.WORLD -> country
            else -> null
        }
        directory.open(code?.let { StationDirectory.Query(it, query) })
        renderRows(resetScroll)
    }

    private fun renderRows(resetScroll: Boolean = false) {
        rows = Stations.visible(tab, favorites, query, radioFavoritesOnly, directory.stations, directoryBookmarks)
        val onRadioTab = tab == StationTab.MOROCCO
        countryButton.visibility = if (tab == StationTab.WORLD) View.VISIBLE else View.GONE
        countryButton.text = "${StationDirectory.countryName(country)} ▾"
        countryButton.contentDescription = "Choisir un pays · ${StationDirectory.countryName(country)}"
        radioFilter.visibility = if (onRadioTab) View.VISIBLE else View.GONE
        radioFilter.isSelected = radioFavoritesOnly
        radioFilter.contentDescription =
            if (radioFavoritesOnly) "Afficher toutes les radios marocaines" else "Afficher les radios marocaines favorites"
        radioFilter.background = pressable(if (radioFavoritesOnly) R.drawable.radio_selected else R.drawable.radio_control)
        empty.text = when {
            onRadioTab && radioFavoritesOnly && query.isBlank() -> "Ajoute une radio marocaine avec ☆"
            tab == StationTab.FAVORITES && favorites.isEmpty() -> "Ajoute une station avec ☆"
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
            button.isSelected = choice == tab
            button.background = pressable(if (choice == tab) R.drawable.radio_selected else R.drawable.radio_control)
        }
        stationAdapter.notifyDataSetChanged()
        if (resetScroll) list.setSelection(0)
        renderPlayer()
    }

    private fun renderPlayer() {
        val player = controller
        val active = player?.currentMediaItem?.mediaId
        val station = findStation(active) ?: savedStation
        val official = Stations.officialPlayer(station) != null
        val recording = station?.let(Stations::isRecording) == true
        val ended = player?.playbackState == Player.STATE_ENDED
        val waitingForNetwork = player?.let(::waitingForNetwork) == true
        val requesting = player?.playWhenReady == true && (player.playerError == null || waitingForNetwork) && !ended
        val playing = player?.isPlaying == true
        playerCategory?.text = if (recording) "تلاوات الحرم" else "LECTEUR RADIO"
        playerPanel.layoutDirection = directionOf(station?.name.orEmpty())
        playerTitle.text = station?.name ?: "Choisir une station"
        playerSubtitle.text = station?.let(Stations::subtitle) ?: "Sélectionne une chaîne dans la liste"
        playerArtwork.bind(station)
        playerStatus.text = when {
            station in Stations.snrt -> "Écouter sur SNRT Live"
            official -> "Ouvrir le lecteur officiel"
            connectionFailed -> "Lecteur indisponible · réessayer"
            player == null -> "Connexion au lecteur…"
            waitingForNetwork && !playing -> "Connexion perdue · reprise automatique"
            player.playerError != null -> "Flux indisponible · vérifier Internet ou réessayer"
            playing -> if (recording) "● En lecture" else "● En direct"
            ended && recording -> "Lecture terminée"
            requesting && player.playbackState == Player.STATE_BUFFERING -> "Connexion au flux…"
            requesting -> "Lecture suspendue"
            active != null -> "En pause"
            else -> "Prêt à écouter"
        }
        playerStatus.setTextColor(when {
            waitingForNetwork && !playing -> amber
            playing && !recording -> 0xFF79E4AD.toInt()
            playing -> accent
            else -> muted
        })
        playButton.setImageResource(if (requesting) R.drawable.ic_radio_pause else R.drawable.ic_radio_play)
        playButton.contentDescription = when {
            station in Stations.snrt -> "Ouvrir le lecteur officiel SNRT"
            official -> "Ouvrir le lecteur officiel"
            requesting -> "Mettre en pause"
            else -> "Écouter"
        }
        enable(playButton, official || (player != null && (station != null || rows.isNotEmpty())) || connectionFailed)
        enable(previous, player != null && rows.size > 1)
        enable(next, player != null && rows.size > 1)
        val highlight = (active ?: savedStation?.url) to playing
        if (highlighted != highlight) {
            highlighted = highlight
            stationAdapter.notifyDataSetChanged()
        }
    }

    private fun select(station: RadioStation) {
        Stations.officialPlayer(station)?.let { url ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                controller?.pause()
                controller?.clearMediaItems()
                savedStation = station
                persistStations(station)
                renderPlayer()
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Aucun navigateur disponible pour le lecteur officiel", Toast.LENGTH_LONG).show()
            }
            return
        }
        val player = controller ?: return
        savedStation = station
        persistStations(station)
        player.setMediaItem(Stations.item(station))
        player.prepare()
        player.play()
        renderPlayer()
    }

    private fun toggle() {
        val official = savedStation?.takeIf { Stations.officialPlayer(it) != null }
        if (official != null) {
            select(official)
            return
        }
        val player = controller ?: run { if (connectionFailed) connectPlayer(); return }
        val current = findStation(player.currentMediaItem?.mediaId)
        val playable = player.playerError == null && player.playbackState != Player.STATE_ENDED
        when {
            (playable || waitingForNetwork(player)) && player.playWhenReady -> player.pause()
            playable && player.playbackState == Player.STATE_READY && current?.let(Stations::isRecording) == true -> player.play()
            else -> (current ?: savedStation ?: rows.firstOrNull())?.let(::select)
        }
    }

    private fun findStation(url: String?): RadioStation? =
        Stations.all.firstOrNull { it.url == url } ?: directoryBookmarks.firstOrNull { it.url == url }

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
        setOnClickListener { action() }
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun pressable(drawable: Int) = RippleDrawable(ColorStateList.valueOf(0x2EFFFFFF), getDrawable(drawable),
        GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() })
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
