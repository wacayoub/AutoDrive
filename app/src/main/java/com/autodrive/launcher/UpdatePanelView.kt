package com.autodrive.launcher

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar

@android.annotation.SuppressLint("ViewConstructor")
internal class UpdatePanelView(context: Context, current: String, check: () -> Unit, download: () -> Unit,
    install: () -> Unit) : LinearLayout(context) {
    private val ui = DriveViews(context)
    private val status = ui.label("", 18, bold = true).apply { maxLines = 4 }
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
    private val checkButton = ui.action("Rechercher une mise à jour", run = check)
    private val downloadButton = ui.action("Télécharger la mise à jour", run = download)
    private val installButton = ui.action("Installer la mise à jour", run = install)
    init {
        orientation = VERTICAL
        setPadding(ui.dp(8), 0, ui.dp(8), ui.dp(8))
        addView(ui.label("Version installée : $current", 16, Palette.muted))
        addView(status, LayoutParams(-1, -2).apply { topMargin = ui.dp(12); bottomMargin = ui.dp(8) })
        addView(progress, LayoutParams(-1, ui.dp(8)).apply { bottomMargin = ui.dp(12) })
        for (button in listOf(checkButton, downloadButton, installButton))
            addView(button, LayoutParams(-1, ui.dp(50)).apply { bottomMargin = ui.dp(7) })
        addView(ui.label("Source : github.com/${UpdatePolicy.REPOSITORY}", 13, Palette.muted).apply { maxLines = 2 })
        addView(ui.label("Les réglages sont conservés. Android peut demander d’autoriser AutoDrive comme source puis de confirmer l’installation.",
            14, Palette.muted).apply { maxLines = 4; setPadding(0, ui.dp(10), 0, 0) })
    }
    fun render(value: UpdateState) {
        status.text = value.message
        val busy = value.phase in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING)
        checkButton.isEnabled = !busy
        checkButton.alpha = if (busy) .5f else 1f
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        progress.isIndeterminate = value.phase == UpdatePhase.CHECKING
        progress.progress = value.progress
        downloadButton.visibility = if (value.release != null && value.phase in setOf(UpdatePhase.AVAILABLE, UpdatePhase.ERROR)) View.VISIBLE else View.GONE
        installButton.visibility = if (value.phase == UpdatePhase.READY) View.VISIBLE else View.GONE
    }
}
