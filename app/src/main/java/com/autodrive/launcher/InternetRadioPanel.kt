@file:android.annotation.SuppressLint("ViewConstructor")

package com.autodrive.launcher

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import java.time.Instant
import java.time.format.DateTimeFormatter

internal class InternetRadioPanel(context: Context, private val artwork: RadioArtwork, private val expanded: Boolean,
    browse: () -> Unit, refresh: () -> Unit, mode: (Boolean) -> Unit, select: (RadioStation) -> Unit,
    favorite: (RadioStation) -> Unit, previous: () -> Unit, toggle: () -> Unit, next: () -> Unit,
    refreshGuide: () -> Unit = {}, openGuideSource: (RadioGuideSource) -> Unit = {}) : LinearLayout(context) {
    private val ui = DriveViews(context)
    private val studio = expanded && resources.configuration.screenWidthDp >= 720 && resources.configuration.screenHeightDp >= 450
    private val logo = RadioLogoView(context)
    private val title = ui.label("Radio Internet", if (studio) 21 else 17, bold = true).apply { maxLines = if (studio) 2 else 1 }
    private val status = ui.label("Prêt à écouter", if (studio) 12 else 11, Palette.muted).apply { maxLines = 2 }
    private val track = ui.label("", 15, Palette.text).apply { maxLines = 2; visibility = View.GONE }
    private val format = ui.label("", 11, Palette.muted).apply { visibility = View.GONE }
    private val playIcon = ui.icon("play", Palette.green)
    private val previousButton: View
    private val nextButton: View
    private val playButton: View
    private val header: View
    private val transport: View
    private var selectedStation: RadioStation? = null
    private var favoritesOnly = false
    private val favoritesButton = ui.action("☆") { mode(!favoritesOnly) }.apply { contentDescription = "Afficher les radios favorites" }
    private val stationFavorite = ui.action("☆") { selectedStation?.let(favorite) }.apply { contentDescription = "Favori de la station choisie" }
    private val list = RadioStationList(context, artwork, expanded, select, favorite, mode, refresh)
    private val programmes = RadioProgrammeView(context, refreshGuide, openGuideSource)
    private val pages = FrameLayout(context)
    private val stationsTab = ui.action("Stations", selected = true) { showProgrammes(false) }
    private val programmesTab = ui.action("Programme") { showProgrammes(true) }
    private var programmeVisible = false
    private var hasPlannedPreview = false
    private val guidePreview = ui.label("", 12, Palette.cyan).apply { visibility = View.GONE }
    private val guideNow = ui.label("", 15, bold = true).apply { maxLines = 3; textDirection = View.TEXT_DIRECTION_FIRST_STRONG }
    private val guideNext = ui.label("", 13, Palette.muted).apply { maxLines = 3; textDirection = View.TEXT_DIRECTION_FIRST_STRONG }
    private val guideProgress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100; progressTintList = android.content.res.ColorStateList.valueOf(Palette.blue)
    }
    private val guideCard = ui.line(true).apply {
        setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
        addView(ui.label("SELON LA GRILLE", 11, Palette.cyan, bold = true), LayoutParams(-1, ui.dp(24)))
        addView(guideNow, LayoutParams(-1, 0, 1f))
        addView(guideProgress, LayoutParams(-1, ui.dp(4)))
        addView(guideNext, LayoutParams(-1, 0, 1f))
        addView(ui.label("Programme ›", 13, Palette.cyan), LayoutParams(-1, ui.dp(24)))
        contentDescription = "Ouvrir le programme de la station"
        ui.touch(this, Palette.surface, 14, Palette.border) { showProgrammes(true) }
    }

    init {
        orientation = if (studio) HORIZONTAL else VERTICAL
        setPadding(ui.dp(7), ui.dp(3), ui.dp(7), ui.dp(3))
        setBackgroundColor(Palette.surface)
        fun browseButton() = FrameLayout(context).apply {
            contentDescription = "Ouvrir la liste complète des radios"
            addView(ui.icon("search", Palette.cyan), FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER))
            ui.touch(this, Palette.raised, 12, action = browse)
        }
        header = ui.line().apply {
            if (expanded) addView(logo, LayoutParams(ui.dp(if (studio) 44 else 40), ui.dp(if (studio) 44 else 40)).apply { marginEnd = ui.dp(7) })
            addView(ui.line(true).apply { addView(title); addView(status) }, LayoutParams(0, -2, 1f))
            if (studio) addView(stationFavorite, LayoutParams(ui.dp(48), ui.dp(48)))
            else if (!expanded) {
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
            listOf(previousButton, playButton, nextButton).forEach { button ->
                addView(button, LayoutParams(0, ui.dp(48), 1f).apply { marginEnd = ui.dp(3) })
            }
        }
        if (expanded) {
            pages.addView(list, FrameLayout.LayoutParams(-1, -1))
            stationsTab.isSelected = true
        }
        val tabs = ui.line().apply {
            addView(stationsTab, LayoutParams(0, ui.dp(48), 1f).apply { marginEnd = ui.dp(4) })
            addView(programmesTab, LayoutParams(0, ui.dp(48), 1f))
        }
        if (studio) {
            addView(ui.line(true).apply {
                background = ui.shape(0xFF12263B.toInt(), 16, Palette.border)
                setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
                addView(header, LayoutParams(-1, ui.dp(76)))
                addView(track, LayoutParams(-1, ui.dp(40)))
                addView(format, LayoutParams(-1, ui.dp(24)))
                addView(transport, LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(6); bottomMargin = ui.dp(10) })
                addView(guideCard, LayoutParams(-1, 0, 1f))
            }, LayoutParams(ui.dp(240), -1).apply { marginEnd = ui.dp(10) })
            addView(ui.line(true).apply {
                addView(tabs); addView(pages, LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(5) })
            }, LayoutParams(0, -1, 1f))
        } else {
            if (expanded && resources.configuration.screenWidthDp >= 600) addView(ui.line().apply {
                addView(header, LayoutParams(0, ui.dp(48), 1f)); addView(transport, LayoutParams(ui.dp(192), ui.dp(48)))
            }) else {
                addView(header, LayoutParams(-1, ui.dp(48))); addView(transport)
            }
            addView(guidePreview, LayoutParams(-1, ui.dp(22)))
            if (expanded) {
                addView(tabs, LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(4) })
                addView(pages, LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(4) })
            } else addView(list, LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(4) })
        }
    }

    private fun showProgrammes(show: Boolean) {
        if (!expanded) return
        if (show) windowInsetsController?.hide(android.view.WindowInsets.Type.ime())
        header.visibility = View.VISIBLE; transport.visibility = View.VISIBLE
        programmeVisible = show
        pages.removeAllViews()
        pages.addView(if (show) programmes else list, FrameLayout.LayoutParams(-1, -1))
        stationsTab.isSelected = !show; programmesTab.isSelected = show
        for ((tab, selected) in listOf(stationsTab to !show, programmesTab to show)) {
            tab.background = ui.shape(if (selected) 0xFF244E73.toInt() else Palette.raised, 12, if (selected) Palette.blue else Palette.border)
        }
    }

    fun render(station: RadioStation?, player: InternetRadio, stations: List<RadioStation>, favorites: Boolean, loading: Boolean, error: Boolean,
        guideState: RadioGuideState = RadioGuideState(), now: Instant = Instant.now()) {
        selectedStation = station; favoritesOnly = favorites
        favoritesButton.contentDescription = if (favorites) "Afficher les radios du Maroc" else "Afficher les radios favorites"
        (favoritesButton.getChildAt(0) as android.widget.TextView).apply { text = if (favorites) "★" else "☆"; setTextColor(Palette.amber) }
        favoritesButton.tooltipText = if (favorites) "Afficher toutes les radios" else "Favoris · ${stations.count { it.favorite }}"
        favoritesButton.background = ui.shape(if (favorites) 0xFF244E73.toInt() else Palette.raised, 12, if (favorites) Palette.blue else Palette.border)
        (stationFavorite.getChildAt(0) as android.widget.TextView).apply { text = if (station?.favorite == true) "★" else "☆"; setTextColor(Palette.amber) }
        stationFavorite.contentDescription = if (station?.favorite == true) "Retirer la station choisie des favoris" else "Ajouter la station choisie aux favoris"
        stationFavorite.isEnabled = station != null
        title.text = station?.name ?: "Radio Internet"; title.contentDescription = "Station choisie : ${station?.name.orEmpty()}"
        if (station != null) logo.bind(station, artwork)
        val playbackStatus = when {
            loading && stations.size < 2 && !player.requestedPlayback -> "Chargement des radios…"
            error && stations.size < 2 && !player.requestedPlayback -> "Catalogue indisponible · ↻"
            else -> player.status
        }
        val titleFromStream = player.trackTitle?.takeIf { player.playing && player.station?.url == station?.url }
        status.text = if (station == null) "Choisis une radio dans la liste" else
            if (!studio && titleFromStream != null) "En lecture · $titleFromStream" else playbackStatus
        status.tooltipText = status.text
        track.text = titleFromStream ?: if (player.playing) "Titre du flux non fourni" else "Infos du flux à l’écoute"
        track.visibility = if (studio) View.VISIBLE else View.GONE
        format.text = player.audioInfo
        format.visibility = if (studio && player.playing && !player.audioInfo.isNullOrBlank()) View.VISIBLE else View.GONE
        playIcon.style(name = if (player.requestedPlayback) "pause" else "play")
        playButton.contentDescription = if (player.requestedPlayback) "Mettre la radio en pause" else "Écouter la radio"
        playButton.isEnabled = station != null; playButton.alpha = if (station != null) 1f else .4f
        val queue = RadioQueue.active(stations, favorites)
        for (button in listOf(previousButton, nextButton)) { button.isEnabled = queue.size > 1; button.alpha = if (queue.size > 1) 1f else .4f }
        list.render(stations, favorites, station?.url, player.station?.url?.takeIf { player.playing }, playbackStatus, loading, error)
        val matching = guideState.takeIf { it.source == RadioGuideSource.forStation(station) } ?: RadioGuideState()
        programmes.render(matching, now)
        val current = matching.guide?.current(now); val nextProgramme = matching.guide?.next(now)
        guidePreview.text = current?.let { "Grille publiée · ${it.programme.title}" }.orEmpty()
        hasPlannedPreview = current != null
        updatePreviewLayout()
        guideNow.text = current?.let { "Prévu ${time(it.start)}–${time(it.end)}\n${it.programme.title}" }
            ?: if (matching.guide != null) "Aucun créneau prévu en ce moment" else matching.message
        guideProgress.visibility = if (current != null) View.VISIBLE else View.GONE
        guideProgress.progress = current?.progress(now) ?: 0
        guideNext.text = nextProgramme?.let {
            val day = if (it.start.atZone(MoroccoRadioTime.zoneAt(it.start)).toLocalDate() == now.atZone(MoroccoRadioTime.zoneAt(now)).toLocalDate()) "HH:mm" else "EEE HH:mm"
            "Ensuite · ${it.start.atZone(MoroccoRadioTime.zoneAt(it.start)).format(DateTimeFormatter.ofPattern(day, java.util.Locale.FRENCH))}\n${it.programme.title}"
        }
            ?: if (matching.guide != null) "Horaires annoncés par la radio, sous réserve de changement." else "Consulte le site de la station pour ses émissions."
    }

    fun setTyping(typing: Boolean) {
        if (!expanded) return
        val hide = typing && !programmeVisible
        header.visibility = if (hide) View.GONE else View.VISIBLE
        transport.visibility = if (hide) View.GONE else View.VISIBLE
    }
    private fun updatePreviewLayout() { guidePreview.visibility = if (hasPlannedPreview && !expanded && height >= ui.dp(230)) View.VISIBLE else View.GONE }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); post { updatePreviewLayout() } }
    private fun time(instant: Instant) = instant.atZone(MoroccoRadioTime.zoneAt(instant)).format(DateTimeFormatter.ofPattern("HH:mm"))
}
