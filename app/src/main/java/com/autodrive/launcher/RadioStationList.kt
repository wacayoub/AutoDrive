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

internal class RadioStationList(context: Context, private val artwork: RadioArtwork, private val expanded: Boolean,
    select: (RadioStation) -> Unit, favorite: (RadioStation) -> Unit, mode: (Boolean) -> Unit, refresh: () -> Unit,
    category: (RadioCategory) -> Unit = {}) : LinearLayout(context) {
    private val ui = DriveViews(context)
    private var all = emptyList<RadioStation>()
    private var favoritesOnly = false
    private var categoryFilter = RadioCategory.ALL
    private var query = ""
    private var selected: String? = null
    private var playing: String? = null
    private var artworkGeneration = -1
    private var statusText = ""
    private var emptyMessage = "Aucune radio disponible"
    private lateinit var filters: LinearLayout
    private lateinit var listView: AbsListView
    private val categoryButtons = RadioCategory.entries.associateWith { value ->
        ui.action(value.title) { if (value == RadioCategory.ALL) mode(false) else category(value) }.apply {
            contentDescription = value.description
            (getChildAt(0) as android.widget.TextView).textSize = 13f
        }
    }
    private val allButton get() = categoryButtons.getValue(RadioCategory.ALL)
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
            addView(android.widget.HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = true
                contentDescription = "Catégories radio · balayage horizontal"
                addView(ui.line().apply {
                    categoryButtons.forEach { (value, button) ->
                        addView(button, LayoutParams(ui.dp(if (value == RadioCategory.NEWS) 116 else 86), ui.dp(48)).apply { marginEnd = ui.dp(3) })
                    }
                })
            }, LayoutParams(0, ui.dp(48), 1f))
            addView(favoritesButton, LayoutParams(ui.dp(108), ui.dp(48)).apply { marginStart = ui.dp(3) })
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
        if (search != null) {
            addView(ui.line().apply {
                addView(search, LayoutParams(0, ui.dp(48), 1f))
                addView(refreshButton, LayoutParams(ui.dp(48), ui.dp(48)))
            })
        }
        addView(filters.apply { visibility = if (expanded) View.VISIBLE else View.GONE })
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
    fun render(stations: List<RadioStation>, favorites: Boolean, selectedUrl: String?, playingUrl: String?, playbackStatus: String, loading: Boolean, error: Boolean,
        category: RadioCategory = RadioCategory.ALL) {
        directoryStatus.visibility = if (loading || error) View.VISIBLE else View.GONE
        directoryStatus.text = if (loading) "Chargement du catalogue du Maroc…" else "Annuaire inaccessible · stations disponibles conservées"
        val selectionChanged = selected != selectedUrl
        val queueChanged = RadioQueue.active(all, favoritesOnly, categoryFilter).map { it.url } != RadioQueue.active(stations, favorites, category).map { it.url }
        val changed = all != stations || favoritesOnly != favorites || categoryFilter != category || selectionChanged || playing != playingUrl || statusText != playbackStatus || artworkGeneration != artwork.generation
        artworkGeneration = artwork.generation
        all = stations; favoritesOnly = favorites; categoryFilter = category; selected = selectedUrl; playing = playingUrl
        statusText = playbackStatus
        (allButton.getChildAt(0) as android.widget.TextView).apply { text = "Toutes ${stations.size}"; textSize = 13f }
        (favoritesButton.getChildAt(0) as android.widget.TextView).apply { text = "★ Favoris ${stations.count { it.favorite }}"; textSize = 13f }
        val choices = categoryButtons.map { (value, button) -> button to (!favorites && category == value) } + (favoritesButton to favorites)
        for ((button, selected) in choices) {
            val newlySelected = selected && !button.isSelected
            button.isSelected = selected
            button.background = ui.shape(if (selected) 0xFF244E73.toInt() else Palette.raised, 12, if (selected) Palette.blue else Palette.border)
            if (newlySelected && button !== favoritesButton) button.post { button.requestRectangleOnScreen(android.graphics.Rect(0,0,button.width,button.height), false) }
        }
        refreshButton.isEnabled = !loading; refreshButton.alpha = if (loading) .4f else 1f
        emptyMessage = when {
            favorites -> "Ajoute des favoris avec ☆ dans la liste Toutes"
            loading -> "Chargement des radios…"
            error -> "Catalogue indisponible · appuie sur ↻ pour réessayer"
            else -> "Aucune radio disponible"
        }
        empty.text = if (query.isNotBlank()) "Aucune radio trouvée" else emptyMessage
        if (changed) filter()
        if (selectionChanged || queueChanged) {
            val position = rows.indexOfFirst { it.url == selectedUrl }
            if (position >= 0 || queueChanged) listView.post { listView.setSelection(position.coerceAtLeast(0)) }
        }
    }
    private fun filter(resetScroll: Boolean = false) {
        val text = query.trim()
        rows = RadioQueue.active(all, favoritesOnly, categoryFilter).filter { text.isEmpty() || RadioCatalogue.search(it, text) }
        empty.text = if (text.isNotEmpty()) "Aucune radio trouvée" else emptyMessage
        adapter.notifyDataSetChanged()
        if (resetScroll) listView.post { listView.setSelection(0) }
    }
    private inner class StationRow(context: Context) : LinearLayout(context) {
        val logo = RadioLogoView(context)
        val name = ui.label("", if (expanded) 17 else 16, bold = true).apply { maxLines = 2; textDirection = View.TEXT_DIRECTION_FIRST_STRONG }
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
            state.text = if (playing) "En lecture" else if (RadioCatalogue.officialPlayer(station) != null) "SNRT · lecteur officiel" else if (selected) statusText else if (station.favorite) "Favori" else
                RadioCatalogue.subtitle(station) ?: if (RadioGuideSource.forStation(station) == RadioGuideSource.ASWAT) "EPG · grille publiée" else "Écouter"
            state.setTextColor(if (playing) Palette.green else Palette.muted)
            star.text = if (station.favorite) "★" else "☆"
        }
    }
}
