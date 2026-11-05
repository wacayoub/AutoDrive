package com.autodrive.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/** A native-app entry point, not an embedded map, website or simulated navigation view. */
@SuppressLint("ViewConstructor") // Programmatic view; AppLinks is required and no XML inflation is used.
internal class NativeMapsPanel(context: Context, private val links: AppLinks) : LinearLayout(context) {
    private val ui = DriveViews(context)
    private val tiny = resources.configuration.screenHeightDp < 300
    private val dense = resources.configuration.screenHeightDp < 400

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = ui.shape(Palette.surface, 16, end = Palette.background)
        setPadding(ui.dp(if (tiny) 6 else 18), ui.dp(if (tiny) 4 else 12),
            ui.dp(if (tiny) 6 else 18), ui.dp(if (tiny) 4 else 12))
        refresh()
    }

    fun refresh() {
        removeAllViews()
        val available = links.mapsAvailable()
        if (!tiny) {
            val drawable = runCatching { context.packageManager.getApplicationIcon(AppLinks.MAPS) }.getOrNull()
            val icon = if (drawable != null) ImageView(context).apply {
                setImageDrawable(drawable)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            } else ui.icon("nav", Palette.green, 38)
            addView(icon, LayoutParams(ui.dp(if (dense) 32 else 44), ui.dp(if (dense) 32 else 44)))
            with(ui) { gap(if (dense) 6 else 10) }
            addView(ui.label("Google Maps", if (dense) 21 else 24, bold = true).apply {
                gravity = Gravity.CENTER
            }, LayoutParams(-1, ui.dp(if (dense) 28 else 34)))
            addView(ui.label(if (available) "Application Android" else "Application absente ou désactivée", 14,
                if (available) Palette.cyan else Palette.amber).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            }, LayoutParams(-1, ui.dp(38)))
            if (!dense) addView(ui.label(if (available)
                "Recherche, itinéraires et guidage dans Google Maps, en plein écran."
                else "Installe ou réactive Google Maps pour utiliser la navigation Android.", 15, Palette.muted).apply {
                gravity = Gravity.CENTER
                maxLines = 4
            }, LayoutParams(-1, -2))
            with(ui) { gap(if (dense) 8 else 14) }
        }
        addView(ui.action(if (available) "Ouvrir Google Maps" else "Installer Google Maps", if (tiny) null else "nav",
            iconColor = Palette.green) {
            if (available) {
                if (!links.openMaps()) {
                    refresh()
                    Toast.makeText(context, "Google Maps ne peut pas être ouvert. Vérifie son installation.", Toast.LENGTH_LONG).show()
                }
            } else if (!links.installMaps()) {
                Toast.makeText(context, "Aucune boutique ou navigateur disponible pour installer Maps.", Toast.LENGTH_LONG).show()
            }
        }.apply {
            if (tiny) (getChildAt(0) as android.widget.TextView).textSize = 14f
        }, LayoutParams(-1, ui.dp(48)))
    }
}
