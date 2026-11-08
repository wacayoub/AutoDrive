package com.autodrive.launcher

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** Compact shortcut to the application's own Android window. */
internal class InstalledAppPanel(
    context: Context,
    title: String,
    app: Launchable?,
    links: AppLinks,
    unavailable: String,
    fallbackIcon: String,
    open: () -> Unit,
) : LinearLayout(context) {
    init {
        val ui = DriveViews(context)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumWidth = ui.dp(204)
        minimumHeight = ui.dp(56)
        setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8))
        val drawable = app?.let(links::icon)
        val icon = if (drawable == null) ui.icon(fallbackIcon, Palette.shortcut(fallbackIcon), 24)
            else ImageView(context).apply { setImageDrawable(drawable) }
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(icon, LayoutParams(ui.dp(28), ui.dp(28)).apply { marginEnd = ui.dp(10) })
        addView(ui.line(true).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(ui.label(title, 16, bold = true))
            if (app == null) addView(ui.label(unavailable, 12, Palette.muted))
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        if (app != null) {
            addView(ui.label("›", 22, Palette.muted).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }, LayoutParams(ui.dp(19), LayoutParams.WRAP_CONTENT))
            contentDescription = "Ouvrir $title"
            stateDescription = app.label.takeUnless { it.equals(title, ignoreCase = true) }
            ui.touch(this, Palette.surface, 14, Palette.border, action = open)
        } else {
            contentDescription = "$title · $unavailable"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            background = ui.shape(Palette.surface, 14)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = minimumWidth
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) desiredWidth
            else minOf(desiredWidth, availableWidth)
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
    }
}
