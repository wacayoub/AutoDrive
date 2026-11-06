package com.autodrive.launcher

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.AbsListView
import android.widget.GridView
import java.util.Locale

internal class RadioStationList(context: Context, private val artwork: RadioArtwork, private val expanded: Boolean,
    select: (RadioStation) -> Unit, favorite: (RadioStation) -> Unit, mode: (Boolean) -> Unit, refresh: () -> Unit) : LinearLayout(context) {
    private val ui = DriveViews(context)
    private var all = emptyList<RadioStation>()
    private var favoritesOnly = false
    private var query = ""
    private var selected: String? = null
    private var playing: String? = null
    private var artworkGeneration = -1
    private var statusText = ""
    private var emptyMessage = "Aucune radio disponible"
    private lateinit var filters: LinearLayout
    private lateinit var listView: AbsListView
    private val allButton = ui.action("Maroc") { mode(false) }.apply { contentDescription = "Afficher les radios du Maroc" }
    private val favoritesButton = ui.action("☆ Favoris") { mode(true) }.apply { contentDescription = "Afficher les radios favorites" }
    private val refreshButton = ui.action("↻", run = refresh).apply { contentDescription = "Actualiser les radios" }
    private val empty = ui.label("Chargement des radios…", 13, Palette.muted).apply { gravity = Gravity.CENTER; maxLines = 3 }
    private val directoryStatus = ui.label("", 12, Palette.amber).apply { visibility = View.GONE }
    private var rows = emptyList<RadioStation>()
    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? StationRow ?: StationRow(context)
            val station = rows[position]
            row.bind(station, station.url == selected, station.url == playing)
            ui.touch(row, if (station.url == selected) 0xFF244E73.toInt() else Palette.surface, 10,
                if (station.url == selected) Palette.blue else android.graphics.Color.TRANSPARENT) { select(station) }
            row.contentDescription = "Écouter ${station.name}"
            ui.touch(row.star, Palette.surface, 10) { favorite(station) }
            row.star.contentDescription = if (station.favorite) "Retirer ${station.name} des favoris" else "Ajouter ${station.name} aux favoris"
            return row
        }
    }
    init {
        orientation = VERTICAL
        filters = ui.line().apply {
            addView(allButton, LayoutParams(0, ui.dp(48), 1f).apply { marginEnd = ui.dp(3) })
            addView(favoritesButton, LayoutParams(0, ui.dp(48), 1f).apply { marginEnd = ui.dp(3) })
            addView(refreshButton, LayoutParams(ui.dp(48), ui.dp(48)))
        }
        val search = if (expanded) EditText(context).apply {
            hint = "Rechercher une radio"; contentDescription = "Rechercher une radio"
            setSingleLine(true); setTextColor(Palette.text); setHintTextColor(Palette.muted)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnEditorActionListener { _, action, event ->
                if (action in setOf(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH, android.view.inputmethod.EditorInfo.IME_ACTION_DONE) ||
                    event?.keyCode in setOf(android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                    windowInsetsController?.hide(android.view.WindowInsets.Type.ime()); true
                } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); filter(true) }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        } else null
        if (search != null && resources.configuration.screenWidthDp >= 600) {
            addView(ui.line().apply {
                addView(search, LayoutParams(0, ui.dp(48), 1f))
                addView(filters, LayoutParams(ui.dp(280), ui.dp(48)))
            })
        } else {
            addView(filters.apply { visibility = if (expanded) View.VISIBLE else View.GONE })
            if (search != null) addView(search, LayoutParams(-1, ui.dp(48)))
        }
        if (expanded) addView(directoryStatus, LayoutParams(-1, ui.dp(22)))
        addView(FrameLayout(context).apply {
            listView = if (expanded) GridView(context).apply {
                numColumns = if (resources.configuration.screenWidthDp >= 600) 2 else 1
                horizontalSpacing = ui.dp(8); verticalSpacing = ui.dp(6)
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
            } else ListView(context).apply { divider = null }
            listView.apply {
                adapter = this@RadioStationList.adapter
                emptyView = empty
                contentDescription = "Liste des radios avec logos"
                isFastScrollEnabled = expanded
                clipToPadding = false
                setPadding(0, ui.dp(3), 0, ui.dp(3))
            }
            addView(listView, FrameLayout.LayoutParams(-1, -1))
            addView(empty, FrameLayout.LayoutParams(-1, -1))
        }, LayoutParams(-1, 0, 1f))
    }
    fun render(stations: List<RadioStation>, favorites: Boolean, selectedUrl: String?, playingUrl: String?, playbackStatus: String, loading: Boolean, error: Boolean) {
        directoryStatus.visibility = if (loading || error) View.VISIBLE else View.GONE
        directoryStatus.text = if (loading) "Chargement du catalogue du Maroc…" else "Annuaire inaccessible · stations disponibles conservées"
        val selectionChanged = selected != selectedUrl
        val queueChanged = RadioQueue.active(all, favoritesOnly).map { it.url } != RadioQueue.active(stations, favorites).map { it.url }
        val changed = all != stations || favoritesOnly != favorites || selectionChanged || playing != playingUrl || statusText != playbackStatus || artworkGeneration != artwork.generation
        artworkGeneration = artwork.generation
        all = stations; favoritesOnly = favorites; selected = selectedUrl; playing = playingUrl
        statusText = playbackStatus
        (allButton.getChildAt(0) as android.widget.TextView).apply { text = "Maroc ${stations.size}"; textSize = 13f }
        (favoritesButton.getChildAt(0) as android.widget.TextView).apply { text = "★ Favoris ${stations.count { it.favorite }}"; textSize = 13f }
        allButton.isSelected = !favorites; favoritesButton.isSelected = favorites
        allButton.background = ui.shape(if (!favorites) 0xFF244E73.toInt() else Palette.raised, 12, if (!favorites) Palette.blue else Palette.border)
        favoritesButton.background = ui.shape(if (favorites) 0xFF244E73.toInt() else Palette.raised, 12, if (favorites) Palette.blue else Palette.border)
        refreshButton.isEnabled = !loading; refreshButton.alpha = if (loading) .4f else 1f
        emptyMessage = when {
            favorites -> "Ajoute des favoris avec ☆ dans la liste Maroc"
            loading -> "Chargement des radios…"
            error -> "Catalogue indisponible · appuie sur ↻ pour réessayer"
            else -> "Aucune radio disponible"
        }
        empty.text = if (query.isNotBlank()) "Aucune radio trouvée" else emptyMessage
        if (changed) filter()
        if (selectionChanged || queueChanged) {
            val position = rows.indexOfFirst { it.url == selectedUrl }
            if (position >= 0) listView.post { listView.setSelection(position) }
        }
    }
    private fun filter(resetScroll: Boolean = false) {
        val text = query.trim().lowercase(Locale.ROOT)
        rows = RadioQueue.active(all, favoritesOnly).filter { text.isEmpty() || it.name.lowercase(Locale.ROOT).contains(text) }
        empty.text = if (text.isNotEmpty()) "Aucune radio trouvée" else emptyMessage
        adapter.notifyDataSetChanged()
        if (resetScroll) listView.post { listView.setSelection(0) }
    }
    private inner class StationRow(context: Context) : LinearLayout(context) {
        val logo = RadioLogoView(context)
        val name = ui.label("", if (expanded) 17 else 16, bold = true).apply { maxLines = 2 }
        val state = ui.label("", 11, Palette.muted)
        val star = ui.label("☆", 23, Palette.amber).apply { gravity = Gravity.CENTER }
        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = ui.dp(if (expanded) 76 else 64)
            setPadding(ui.dp(6), ui.dp(5), 0, ui.dp(5))
            layoutParams = AbsListView.LayoutParams(-1, -2)
            addView(logo, LayoutParams(ui.dp(44), ui.dp(44)).apply { marginEnd = ui.dp(8) })
            addView(ui.line(true).apply { addView(name); addView(state) }, LayoutParams(0, -2, 1f))
            addView(star, LayoutParams(ui.dp(48), ui.dp(48)))
        }
        fun bind(station: RadioStation, selected: Boolean, playing: Boolean) {
            logo.bind(station, artwork); name.text = station.name
            state.text = if (playing) "En lecture" else if (selected) statusText else if (station.favorite) "Favori" else
                if (RadioGuideSource.forStation(station) == RadioGuideSource.ASWAT) "EPG · grille publiée" else "Écouter"
            state.setTextColor(if (playing) Palette.green else Palette.muted)
            star.text = if (station.favorite) "★" else "☆"
        }
    }
}
