package com.autodrive.launcher

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

internal class InternetRadioPanel(context: Context, private val artwork: RadioArtwork, private val expanded: Boolean,
    browse: () -> Unit, refresh: () -> Unit, mode: (Boolean) -> Unit, select: (RadioStation) -> Unit,
    favorite: (RadioStation) -> Unit, previous: () -> Unit, toggle: () -> Unit, next: () -> Unit) : LinearLayout(context) {
    private val ui = DriveViews(context)
    private val logo = RadioLogoView(context)
    private val title = ui.label("Radio Internet", 17, bold = true)
    private val status = ui.label("Prêt à écouter", 11, Palette.muted).apply { maxLines = 2 }
    private val playIcon = ui.icon("play", Palette.green)
    private val previousButton: View
    private val nextButton: View
    private val playButton: View
    private lateinit var header: View
    private lateinit var transport: View
    private val favoritesButton = ui.action("☆") { mode(!favoritesOnly) }.apply { contentDescription = "Afficher les radios favorites" }
    private var favoritesOnly = false
    private val list = RadioStationList(context, artwork, expanded, select, favorite, mode, refresh)
    init {
        orientation = VERTICAL
        setPadding(ui.dp(7), ui.dp(3), ui.dp(7), ui.dp(3))
        setBackgroundColor(Palette.surface)
        fun browseButton() = FrameLayout(context).apply {
            contentDescription = "Ouvrir la liste complète des radios"
            addView(ui.icon("search", Palette.cyan), FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER))
            ui.touch(this, Palette.raised, 12, action = browse)
        }
        header = ui.line().apply {
            if (expanded) addView(logo, LayoutParams(ui.dp(40), ui.dp(40)).apply { marginEnd = ui.dp(7) })
            addView(ui.line(true).apply { addView(title); addView(status) }, LayoutParams(0, -2, 1f))
            if (!expanded) {
                addView(favoritesButton, LayoutParams(ui.dp(48), ui.dp(48)))
                addView(browseButton(), LayoutParams(ui.dp(48), ui.dp(48)))
            }
        }
        fun control(description: String, icon: View, action: () -> Unit) = FrameLayout(context).apply {
            contentDescription = description
            addView(icon, FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER))
            ui.touch(this, Palette.raised, 12, action = action)
        }
        previousButton = control("Station précédente", ui.icon("prev"), previous)
        playButton = control("Écouter la radio", playIcon, toggle)
        nextButton = control("Station suivante", ui.icon("next"), next)
        transport = ui.line().apply {
            for (button in listOf(previousButton, playButton, nextButton)) addView(button,
                LayoutParams(0, ui.dp(48), 1f).apply { marginEnd = ui.dp(3) })
        }
        if (expanded && resources.configuration.screenWidthDp >= 600) addView(ui.line().apply {
            addView(header, LayoutParams(0, ui.dp(48), 1f))
            addView(transport, LayoutParams(ui.dp(192), ui.dp(48)))
        }) else {
            addView(header, LayoutParams(-1, ui.dp(48)))
            addView(transport)
        }
        addView(list, LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(4) })
    }
    fun render(station: RadioStation?, player: InternetRadio, stations: List<RadioStation>, favorites: Boolean, loading: Boolean, error: Boolean) {
        favoritesOnly = favorites
        favoritesButton.contentDescription = if (favorites) "Afficher les radios du Maroc" else "Afficher les radios favorites"
        (favoritesButton.getChildAt(0) as android.widget.TextView).text = if (favorites) "★" else "☆"
        (favoritesButton.getChildAt(0) as android.widget.TextView).setTextColor(Palette.amber)
        favoritesButton.tooltipText = if (favorites) "Afficher toutes les radios" else "Favoris · ${stations.count { it.favorite }}"
        favoritesButton.background = ui.shape(if (favorites) 0xFF244E73.toInt() else Palette.raised, 12, if (favorites) Palette.blue else Palette.border)
        title.text = station?.name ?: "Radio Internet"
        title.contentDescription = "Station choisie : ${station?.name.orEmpty()}"
        if (station != null) logo.bind(station, artwork)
        val playbackStatus = when {
            loading && stations.size < 2 && !player.requestedPlayback -> "Chargement des radios…"
            error && stations.size < 2 && !player.requestedPlayback -> "Catalogue indisponible · ↻"
            else -> player.status
        }
        val track = player.trackTitle?.takeIf { player.playing && player.station?.url == station?.url }
        status.text = if (station == null) "Choisis une radio dans la liste" else
            if (track != null) "En lecture · $track" else playbackStatus
        status.tooltipText = status.text
        playIcon.style(name = if (player.requestedPlayback) "pause" else "play")
        playButton.contentDescription = if (player.requestedPlayback) "Mettre la radio en pause" else "Écouter la radio"
        playButton.isEnabled = station != null
        playButton.alpha = if (station != null) 1f else .4f
        val queue = RadioQueue.active(stations, favorites)
        for (button in listOf(previousButton, nextButton)) {
            button.isEnabled = queue.size > 1; button.alpha = if (queue.size > 1) 1f else .4f
        }
        list.render(stations, favorites, station?.url, player.station?.url?.takeIf { player.playing }, playbackStatus, loading, error)
    }
    fun setTyping(typing: Boolean) {
        if (!expanded) return
        header.visibility = if (typing) View.GONE else View.VISIBLE
        transport.visibility = if (typing) View.GONE else View.VISIBLE
    }
}
