package com.autodrive.radiomaroc

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.autodrive.launcher.RadioArtwork
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
    private var tab = StationTab.MOROCCO
    private var favorites = emptySet<String>()
    private var query = ""
    private var rows = emptyList<RadioStation>()
    private var savedStation: RadioStation? = null
    private var highlighted: Pair<String?, Boolean>? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var connectionFailed = false
    private lateinit var tabs: Map<StationTab, TextView>
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var playerTitle: TextView
    private lateinit var playerSubtitle: TextView
    private lateinit var playerArtwork: StationArtwork
    private lateinit var playerStatus: TextView
    private lateinit var playButton: TextView
    private lateinit var previous: TextView
    private lateinit var next: TextView

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            renderPlayer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("radio_maroc", MODE_PRIVATE)
        artwork = RadioArtwork(this)
        tab = StationTab.entries.firstOrNull { it.name == prefs.getString("tab", null) } ?: StationTab.MOROCCO
        favorites = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
        savedStation = Stations.all.firstOrNull { it.url == prefs.getString("last_station", null) }
        query = savedInstanceState?.getString("query").orEmpty()
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        window.setDecorFitsSystemWindows(false)
        setContentView(screen())
        renderRows()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query)
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
        artwork.close()
        super.onDestroy()
    }

    private fun connectPlayer() {
        connectionFailed = false
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture === future) {
                controller = runCatching { future.get() }.getOrNull()
                connectionFailed = controller == null
                controller?.addListener(playerListener)
                renderPlayer()
            }
        }, mainExecutor)
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
            tabs = StationTab.entries.associateWith { choice ->
                button(choice.label) {
                    tab = choice
                    prefs.edit().putString("tab", choice.name).apply()
                    renderRows(resetScroll = true)
                }.also { view ->
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
            content.addView(stationPanel(true), LinearLayout.LayoutParams(0, -1, .32f).apply { marginEnd = dp(8) })
            content.addView(stationList(), LinearLayout.LayoutParams(0, -1, .68f))
        } else {
            content.addView(stationList(), LinearLayout.LayoutParams(-1, 0, 1f))
            content.addView(stationPanel(false), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(playbackBar(), LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(6) })
        requestFocus()
    }

    private fun stationList(): View = column().apply {
        addView(EditText(context).apply {
            hint = "Rechercher une station ou un récitateur"
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
                    renderRows(resetScroll = true)
                }
            })
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        addView(FrameLayout(context).apply {
            empty = label("", 15, muted).apply { gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0) }
            list = ListView(context).apply {
                divider = android.graphics.drawable.ColorDrawable(0xFF263747.toInt())
                dividerHeight = dp(1)
                itemsCanFocus = true
                contentDescription = "Liste des stations"
                adapter = stationAdapter
            }
            addView(list, FrameLayout.LayoutParams(-1, -1))
            addView(empty, FrameLayout.LayoutParams(-1, -1))
            list.emptyView = empty
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun stationPanel(wide: Boolean): View = (if (wide) column() else row()).apply {
        background = getDrawable(R.drawable.radio_card)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(6), dp(12), dp(6))
        playerArtwork = StationArtwork(if (wide) 25 else 16)
        val artworkSize = dp(if (wide) 66 else 42)
        addView(playerArtwork, LinearLayout.LayoutParams(artworkSize, artworkSize).apply {
            if (wide) bottomMargin = dp(9) else marginEnd = dp(8)
        })
        addView(column().apply {
            if (wide) addView(label("RADIO INTERNET", 11, accent, bold = true))
            playerTitle = label("", if (wide) 22 else 17, textColor, bold = true).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(playerTitle)
            playerSubtitle = label("", 12, muted).apply { maxLines = 2 }
            addView(playerSubtitle)
        }, if (wide) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun playbackBar(): View = row().apply {
        background = getDrawable(R.drawable.radio_header)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(2), 0)
        playerStatus = label("", 13, muted, bold = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        addView(playerStatus, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        previous = button("‹") { skip(-1) }.apply { contentDescription = "Station précédente"; textSize = 24f }
        playButton = button("▶") { toggle() }.apply {
            textSize = 20f
            background = pressable(R.drawable.radio_selected)
        }
        next = button("›") { skip(1) }.apply { contentDescription = "Station suivante"; textSize = 24f }
        addView(previous, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(playButton, LinearLayout.LayoutParams(dp(58), dp(48)).apply { marginStart = dp(4); marginEnd = dp(4) })
        addView(next, LinearLayout.LayoutParams(dp(48), dp(48)))
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
        private val name = label("", 15, textColor, bold = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        private val subtitle = label("", 11, muted).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        private val star = button("☆") {}.apply { background = null; textSize = 21f; setTextColor(amber) }

        init {
            layoutParams = AbsListView.LayoutParams(-1, dp(50))
            gravity = Gravity.CENTER_VERTICAL
            addView(number, LinearLayout.LayoutParams(dp(34), -1))
            addView(badge, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) })
            addView(column().apply { addView(name); addView(subtitle) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(star, LinearLayout.LayoutParams(dp(48), dp(48)))
        }

        fun bind(station: RadioStation, position: Int) {
            val selected = station.url == highlighted?.first
            val playing = selected && highlighted?.second == true
            background = pressable(if (selected) R.drawable.radio_selected else R.drawable.radio_card)
            isActivated = selected
            contentDescription = "Écouter ${station.name}"
            setOnClickListener { select(station) }
            number.text = position.toString().padStart(2, '0')
            badge.bind(station)
            name.text = station.name
            subtitle.text = if (playing) "En lecture" else Stations.subtitle(station)
            subtitle.setTextColor(if (playing) accent else muted)
            val favorite = station.url in favorites
            star.text = if (favorite) "★" else "☆"
            star.contentDescription = if (favorite) "Retirer ${station.name} des favoris" else "Ajouter ${station.name} aux favoris"
            star.setOnClickListener {
                favorites = if (favorite) favorites - station.url else favorites + station.url
                prefs.edit().putStringSet("favorites", favorites).apply()
                renderRows()
            }
        }
    }

    private inner class StationArtwork(textSize: Int) : FrameLayout(this@RadioActivity) {
        private val initials = label("FM", textSize, accent, bold = true).apply { gravity = Gravity.CENTER }
        private val logo = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.GONE }
        private var logoWork: Job? = null
        private var artworkKey: String? = null

        init {
            background = getDrawable(R.drawable.radio_control)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(initials, LayoutParams(-1, -1))
            addView(logo, LayoutParams(-1, -1).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }

        fun bind(station: RadioStation?) {
            if (artworkKey == station?.url) return
            artworkKey = station?.url
            logoWork?.cancel()
            initials.text = when {
                station == null -> "FM"
                station.tags.isEmpty() -> "ق"
                else -> station.name.split(' ').take(2).joinToString("") { it.take(1) }
            }
            initials.visibility = View.VISIBLE
            logo.visibility = View.GONE
            logo.setImageDrawable(null)
            logoWork = artwork.load(station?.logoUrl) { bitmap ->
                if (artworkKey == station?.url && bitmap != null) {
                    logo.setImageBitmap(bitmap)
                    logo.visibility = View.VISIBLE
                    initials.visibility = View.GONE
                }
            }
        }

        override fun onDetachedFromWindow() {
            logoWork?.cancel()
            artworkKey = null
            super.onDetachedFromWindow()
        }
    }

    private fun renderRows(resetScroll: Boolean = false) {
        rows = Stations.visible(tab, favorites, query)
        empty.text = if (tab == StationTab.FAVORITES && favorites.isEmpty()) "Ajoute une station avec ☆" else "Aucune station trouvée"
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
        val station = Stations.all.firstOrNull { it.url == active } ?: savedStation
        val requesting = player?.playWhenReady == true && player.playerError == null
        val playing = player?.isPlaying == true
        playerTitle.text = station?.name ?: "Choisir une station"
        playerSubtitle.text = station?.let(Stations::subtitle) ?: "Sélectionne une chaîne dans la liste"
        playerArtwork.bind(station)
        playerStatus.text = when {
            connectionFailed -> "Lecteur indisponible · réessayer"
            player == null -> "Connexion au lecteur…"
            player.playerError != null -> "Flux indisponible · vérifier Internet ou réessayer"
            playing -> "● En direct"
            requesting && player.playbackState == Player.STATE_BUFFERING -> "Connexion au flux…"
            requesting -> "Lecture suspendue"
            active != null -> "En pause"
            else -> "Prêt à écouter"
        }
        playerStatus.setTextColor(if (playing) accent else muted)
        playButton.text = if (requesting) "Ⅱ" else "▶"
        playButton.contentDescription = if (requesting) "Mettre en pause" else "Écouter"
        enable(playButton, (player != null && (station != null || rows.isNotEmpty())) || connectionFailed)
        enable(previous, player != null && rows.size > 1)
        enable(next, player != null && rows.size > 1)
        val highlight = (active ?: savedStation?.url) to playing
        if (highlighted != highlight) {
            highlighted = highlight
            stationAdapter.notifyDataSetChanged()
        }
    }

    private fun select(station: RadioStation) {
        val player = controller ?: return
        savedStation = station
        prefs.edit().putString("last_station", station.url).apply()
        player.setMediaItem(Stations.item(station))
        player.prepare()
        player.play()
        renderPlayer()
    }

    private fun toggle() {
        val player = controller ?: run { if (connectionFailed) connectPlayer(); return }
        if (player.playWhenReady && player.playerError == null) {
            player.pause()
        } else {
            val current = Stations.all.firstOrNull { it.url == player.currentMediaItem?.mediaId }
            (current ?: savedStation ?: rows.firstOrNull())?.let(::select)
        }
    }

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
        GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(3).toFloat() })
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
