package com.autodrive.launcher

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

internal class InternetRadioPanel(context: Context, pick: () -> Unit, catalogue: () -> Unit,
    previous: () -> Unit, toggle: () -> Unit, next: () -> Unit, favorite: () -> Unit) : LinearLayout(context) {
    private val ui = DriveViews(context)
    private val title = ui.label("Choisir une station", 22, bold = true)
    private val track = ui.label("", 14, Palette.muted)
    private val status = ui.label("Prêt à écouter", 13, Palette.muted)
    private val playIcon = ui.icon("play", Palette.green)
    private val favoriteButton = ui.label("☆", 26, Palette.amber).apply { gravity = Gravity.CENTER }
    private val previousButton: View
    private val nextButton: View
    private val playButton: View
    init {
        orientation = VERTICAL
        setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(8))
        setBackgroundColor(Palette.surface)
        addView(ui.line().apply {
            addView(ui.icon("radio", Palette.amber), LayoutParams(ui.dp(30), ui.dp(30)))
            addView(ui.label("Radio Internet", 17, bold = true), LayoutParams(0, ui.dp(48), 1f))
            favoriteButton.contentDescription = "Ajouter aux favoris radio"
            ui.touch(favoriteButton, Palette.surface, 12, action = favorite)
            addView(favoriteButton, LayoutParams(ui.dp(48), ui.dp(48)))
        })
        addView(title, LayoutParams(-1, ui.dp(32)))
        addView(track, LayoutParams(-1, ui.dp(22)))
        addView(status.apply { maxLines = 2 }, LayoutParams(-1, ui.dp(36)))
        fun control(description: String, icon: View, action: () -> Unit) = FrameLayout(context).apply {
            contentDescription = description
            addView(icon, FrameLayout.LayoutParams(ui.dp(26), ui.dp(26), Gravity.CENTER))
            ui.touch(this, Palette.raised, 12, action = action)
        }
        previousButton = control("Station précédente", ui.icon("prev"), previous)
        playButton = control("Écouter la radio", playIcon, toggle)
        nextButton = control("Station suivante", ui.icon("next"), next)
        addView(ui.line().apply {
            for (button in listOf(previousButton, playButton, nextButton)) addView(button,
                LayoutParams(0, ui.dp(48), 1f).apply { marginEnd = ui.dp(4) })
        })
        addView(ui.line().apply {
            addView(ui.action("Stations", run = pick), LayoutParams(0, ui.dp(48), 1f).apply { topMargin = ui.dp(6); marginEnd = ui.dp(4) })
            addView(ui.action("Maroc", run = catalogue), LayoutParams(0, ui.dp(48), 1f).apply { topMargin = ui.dp(6) })
        })
    }
    fun render(station: RadioStation?, player: InternetRadio, count: Int) {
        title.text = station?.name ?: "Choisir une station"
        track.text = if (player.station?.url == station?.url) player.trackTitle.orEmpty() else ""
        status.text = if (station == null) "Ajoute un flux radio HTTPS" else player.status
        playIcon.style(name = if (player.playing) "pause" else "play")
        playButton.contentDescription = if (player.playing) "Mettre la radio en pause" else "Écouter la radio"
        playButton.isEnabled = station != null
        playButton.alpha = if (station != null) 1f else .4f
        favoriteButton.text = if (station?.favorite == true) "★" else "☆"
        favoriteButton.contentDescription = if (station?.favorite == true) "Retirer des favoris radio" else "Ajouter aux favoris radio"
        favoriteButton.isEnabled = station != null
        for (button in listOf(previousButton, nextButton)) { button.isEnabled = count > 1; button.alpha = if (count > 1) 1f else .4f }
    }
}
