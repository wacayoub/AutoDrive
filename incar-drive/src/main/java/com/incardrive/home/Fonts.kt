package com.incardrive.home

import android.content.Context
import android.graphics.Typeface

/**
 * The launcher's typeface: Manrope (SIL Open Font License, its licence beside it in the assets), modern and wide open,
 * easy to read at a glance in the car. One file holds every weight; each weight asked for is built once.
 */
internal object Fonts {
    private const val FILE = "fonts/Manrope.ttf"
    private var assets: android.content.res.AssetManager? = null
    private val built = HashMap<Int, Typeface>()

    fun init(context: Context) {
        if (assets == null) assets = context.applicationContext.assets
    }

    /** Manrope at [weight] (200 to 800); the system's sans-serif if the file cannot be read. */
    fun get(weight: Int): Typeface {
        val w = weight.coerceIn(200, 800)
        built[w]?.let { return it }
        val face = assets?.let { manager ->
            runCatching { Typeface.Builder(manager, FILE).setFontVariationSettings("'wght' $w").setWeight(w).build() }.getOrNull()
        } ?: Typeface.create(Typeface.DEFAULT, w, false)
        built[w] = face
        return face
    }
}
