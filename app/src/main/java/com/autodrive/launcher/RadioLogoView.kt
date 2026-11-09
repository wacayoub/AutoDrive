package com.autodrive.launcher

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.Job

internal class RadioLogoView(context: Context) : FrameLayout(context) {
    private val ui = DriveViews(context)
    private val initials = ui.label("R", 17, bold = true).apply { gravity = Gravity.CENTER }
    private val picture = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.GONE }
    private var work: Job? = null
    private var key: String? = null
    init {
        background = ui.shape(Palette.raised, 10)
        clipToOutline = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(initials, LayoutParams(-1, -1))
        addView(picture, LayoutParams(-1, -1).apply { setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3)) })
    }
    fun bind(station: RadioStation, artwork: RadioArtwork) {
        val newKey = "${station.name}|${station.logoUrl}|${artwork.generation}"
        if (key == newKey) return
        key = newKey
        work?.cancel()
        initials.text = station.name.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).map { it.first() }.joinToString("").uppercase()
        initials.visibility = View.VISIBLE
        picture.visibility = View.GONE
        picture.setImageDrawable(null)
        work = artwork.load(station.logoUrl) { bitmap ->
            if (key != newKey) return@load
            if (bitmap != null) {
                picture.setImageBitmap(bitmap)
                picture.visibility = View.VISIBLE
                initials.visibility = View.GONE
            }
        }
    }
    override fun onDetachedFromWindow() { work?.cancel(); key = null; super.onDetachedFromWindow() }
}
