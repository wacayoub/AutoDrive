package com.autodrive.launcher

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** A real app handoff, never a screenshot or imitation of a hosted activity. */
internal class InstalledAppPanel(
    context: Context,
    title: String,
    app: Launchable?,
    links: AppLinks,
    unavailable: String,
    fallbackIcon: String,
    open: () -> Unit,
    configure: (() -> Unit)? = null,
    install: (() -> Unit)? = null,
) : LinearLayout(context) {
    init {
        val ui = DriveViews(context)
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
        background = ui.shape(Palette.surface, 16, end = Palette.background)
        val drawable = app?.let(links::icon)
        val icon = if (drawable == null) ui.icon(fallbackIcon, Palette.shortcut(fallbackIcon), 40)
            else ImageView(context).apply { setImageDrawable(drawable); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        addView(icon, LayoutParams(ui.dp(32), ui.dp(32)).apply { bottomMargin = ui.dp(4) })
        addView(ui.label(title, 23, bold = true).apply { gravity = Gravity.CENTER }, LayoutParams(-1, ui.dp(28)))
        addView(ui.label(if (app == null) unavailable else app.label, 14, Palette.muted).apply {
            gravity = Gravity.CENTER; maxLines = 2
        }, LayoutParams(-1, ui.dp(36)))
        addView(ui.label("Application Android · plein écran", 12, Palette.muted).apply {
            gravity = Gravity.CENTER; maxLines = 2
        }, LayoutParams(-1, ui.dp(28)))
        if (app != null) addView(ui.action("Ouvrir $title", selected = true, run = open),
            LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(4) })
        if (configure != null) addView(ui.action("Choisir $title", run = configure),
            LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(4) })
        if (app == null && install != null) addView(ui.action("Google Play · $title", run = install),
            LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(4) })
    }
}
