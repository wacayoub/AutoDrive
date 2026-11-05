package com.autodrive.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.media.session.PlaybackState
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.content.res.ColorStateList
import com.autodrive.launcher.Palette as P
import java.util.Locale

internal interface DashboardActions {
    fun phone()
    fun apps()
    fun settings()
    fun location()
    fun tab(value: MediaTab)
    fun openMedia()
    fun closeVideo()
    fun transport(action: Long)
    fun favorites()
}

@SuppressLint("ViewConstructor") // Constructed with a dashboard action handler.
internal class DashboardView(context: Context, private val actions: DashboardActions) : LinearLayout(context) {
    private val ui = DriveViews(context)
    private val compactWidth = resources.configuration.screenWidthDp < 570
    private val shortHeight = !compactWidth && resources.configuration.screenHeightDp < 395
    private val brand = ui.label("AutoDrive", 23, bold = true)
    private val speed = ui.label("--", 37, bold = true)
    private val gps = ui.label("Waiting for GPS", 12, P.muted)
    private val clock = ui.label("", 20)
    private val wifi = ui.icon("wifi", size = 21)
    private val bluetooth = ui.icon("bluetooth", size = 20)
    private val network = ui.icon("network", size = 21)
    private val mapStatus = ui.label("", 14, P.muted)
    private val mapFrame = FrameLayout(context)
    private lateinit var mapPlaceholder: View
    private val panelGap = View(context)
    private val navigationPanel: View
    private val mediaFrame = FrameLayout(context)
    private val mediaContent = FrameLayout(context)
    private lateinit var mediaHub: View
    private lateinit var mediaBody: View
    private lateinit var youtubeEmpty: View
    private var videoPane: View? = null
    private val mediaTitle = ui.label("", 19, bold = true).apply { maxLines = if (shortHeight) 1 else 2 }
    private val mediaStatus = ui.label("", 13, P.muted).apply { maxLines = 2 }
    private val mediaSub = ui.label("", 13, P.muted)
    private val art = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val artIcon = ui.icon("play", P.blue, 37)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1_000
        progressTintList = ColorStateList.valueOf(P.blue)
        progressBackgroundTintList = ColorStateList.valueOf(P.border)
    }
    private val duration = ui.label("—", 12, P.muted)
    private val tabButtons = mutableMapOf<MediaTab, LinearLayout>()
    private val tabIcons = mutableMapOf<MediaTab, DriveIcon>()
    private val favoriteButton = ui.action("Favorites", run = actions::favorites)
    private val mediaHint = ui.label("", 13, 0xFF75B9FF.toInt(), bold = true).apply { maxLines = 2 }
    private lateinit var mediaDetails: LinearLayout
    private val controls = listOf(
        ui.icon("prev", size = 25),
        ui.icon("play", size = 25),
        ui.icon("next", size = 25)
    )
    private var previous: DashboardState? = null
    private var playAction = PlaybackState.ACTION_PLAY

    init {
        orientation = VERTICAL
        setBackgroundColor(P.background)
        setPadding(ui.dp(13), ui.dp(7), ui.dp(13), ui.dp(12))
        addView(header(), LayoutParams(LayoutParams.MATCH_PARENT, ui.dp(54)))
        with(ui) { gap(7) }
        val panels = ui.line()
        if (compactWidth) panels.orientation = VERTICAL
        navigationPanel = navigation()
        panels.addView(navigationPanel, panelParams(.52f))
        panels.addView(panelGap, if (compactWidth) LayoutParams(1, ui.dp(10)) else LayoutParams(ui.dp(10), 1))
        mediaHub = media()
        mediaFrame.addView(mediaHub, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        panels.addView(mediaFrame, panelParams(.48f))
        if (compactWidth) {
            val scroller = android.widget.ScrollView(context).apply {
                isFillViewport = true
                addView(panels)
            }
            addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        } else addView(panels, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        with(ui) { gap(10) }
        addView(bottom(), LayoutParams(LayoutParams.MATCH_PARENT, ui.dp(57)))
    }

    private fun panelParams(weight: Float): LayoutParams =
        if (compactWidth) LayoutParams(LayoutParams.MATCH_PARENT, ui.dp(340))
        else LayoutParams(0, LayoutParams.MATCH_PARENT, weight)

    private fun header() = ui.line().apply {
        if (!compactWidth) {
            val identity = ui.line()
            identity.addView(ui.icon("brand", P.blue, 25), LayoutParams(ui.dp(29), ui.dp(34)))
            with(ui) { identity.gap(7) }
            identity.addView(ui.line(true).apply {
                addView(brand)
                addView(ui.label("INCARLINK", 10, P.muted).apply { letterSpacing = .16f })
            })
            addView(identity, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(separator(), LayoutParams(ui.dp(1), ui.dp(26)))
        } else {
            addView(ui.icon("brand", P.blue, 25).apply { contentDescription = "Incarlink AutoDrive" },
                LayoutParams(ui.dp(29), ui.dp(34)))
        }
        val readout = ui.line(true).apply {
            gravity = Gravity.CENTER
            val numbers = ui.line().apply {
                gravity = Gravity.CENTER
                addView(speed)
                with(ui) { gap(5) }
                addView(ui.label("km/h", 15, P.muted).apply { gravity = Gravity.BOTTOM })
            }
            addView(numbers, LayoutParams(LayoutParams.MATCH_PARENT, ui.dp(40)))
            gps.gravity = Gravity.CENTER
            addView(gps)
            setOnClickListener { actions.location() }
            isFocusable = true
        }
        addView(readout, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.25f))
        addView(separator(), LayoutParams(ui.dp(1), ui.dp(26)))
        val status = ui.line().apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            val side = if (compactWidth) 26 else 40
            listOf(wifi, bluetooth, network).forEach { addView(it, LayoutParams(ui.dp(side), ui.dp(35))) }
            if (!compactWidth) with(ui) { gap(8) }
            addView(clock)
        }
        addView(status, LayoutParams(0, LayoutParams.MATCH_PARENT, if (compactWidth) 1.35f else 1.15f))
    }

    private fun separator() = View(context).apply { setBackgroundColor(P.border) }

    private fun navigation() = ui.line(true).apply {
        background = ui.shape(P.surface, 18, P.border)
        contentDescription = "Maps panel"
        setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
        val center = ui.line(true).apply {
            gravity = Gravity.CENTER
            addView(ui.label("Google Maps", if (shortHeight) 20 else 23, bold = true).apply { gravity = Gravity.CENTER })
            with(ui) { gap(if (shortHeight) 3 else 7) }
            mapStatus.gravity = Gravity.CENTER
            addView(mapStatus, LayoutParams(LayoutParams.MATCH_PARENT, ui.dp(23)))
            addView(ui.label("Widget officiel de trafic à proximité", 13, P.muted).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            })
        }
        mapPlaceholder = center
        mapFrame.addView(center, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(mapFrame, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun media() = ui.line(true).apply {
        background = ui.shape(P.surface, 18, P.border)
        setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(8))
        val title = ui.line().apply {
            val badge = ui.icon("play", size = 18).apply { background = ui.shape(P.blue, 6) }
            addView(badge, LayoutParams(ui.dp(30), ui.dp(30)))
            with(ui) { gap(10) }
            addView(ui.label("Media Hub", 21, bold = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(favoriteButton, LayoutParams(ui.dp(92), ui.dp(48)))
        }
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, ui.dp(36)))
        with(ui) { gap(if (shortHeight) 2 else 5) }
        val tabs = ui.line().apply {
            background = ui.shape(P.raised, 13)
            MediaTab.entries.forEach { tab ->
                val button = ui.line().apply {
                    gravity = Gravity.CENTER
                    val icon = ui.icon(when (tab) {
                        MediaTab.RADIO -> "radio"
                        MediaTab.YOUTUBE -> "play"
                        MediaTab.IPTV -> "tv"
                    }, size = 16)
                    tabIcons[tab] = icon
                    addView(icon, LayoutParams(ui.dp(19), ui.dp(24)))
                    with(ui) { gap(5) }
                    addView(ui.label(tab.title, 15).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO })
                    contentDescription = tab.title
                }
                tabButtons[tab] = button
                addView(button, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
        }
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, ui.dp(48)))
        with(ui) { gap(if (shortHeight) 5 else 10) }
        val body = ui.line(true)
        mediaBody = body
        val details = ui.line().apply {
            val artwork = FrameLayout(context).apply {
                background = ui.shape(0xFF253240.toInt(), 12, 0xFF374454.toInt(), 0xFF18212C.toInt())
                clipToOutline = true
                addView(art, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                addView(artIcon, FrameLayout.LayoutParams(ui.dp(60), ui.dp(60), Gravity.CENTER))
            }
            addView(artwork, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.1f))
            with(ui) { gap(12) }
            val info = ui.line(true).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(mediaTitle)
                with(ui) { gap(if (shortHeight) 2 else 5) }
                addView(mediaHint)
                if (!shortHeight) {
                    with(ui) { gap(5) }
                    addView(mediaSub)
                }
                with(ui) { gap(if (shortHeight) 2 else 5) }
                addView(mediaStatus)
            }
            addView(info, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        mediaDetails = details
        ui.touch(details, 0, 12, action = actions::openMedia)
        body.addView(details, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        with(ui) { body.gap(if (shortHeight) 3 else 6) }
        val playback = ui.line().apply {
            controls.forEachIndexed { index, icon ->
                addView(icon, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
                ui.touch(icon, 0, 10) {
                    val action = when (index) {
                        0 -> PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        1 -> playAction
                        else -> PlaybackState.ACTION_SKIP_TO_NEXT
                    }
                    actions.transport(action)
                }
            }
        }
        body.addView(playback, LayoutParams(LayoutParams.MATCH_PARENT, ui.dp(48)))
        val timeline = ui.line().apply {
            addView(progress, LayoutParams(0, ui.dp(7), 1f))
            with(ui) { gap(10) }
            addView(duration)
        }
        body.addView(timeline, LayoutParams(LayoutParams.MATCH_PARENT, ui.dp(if (shortHeight) 18 else 25)))
        mediaContent.addView(body, FrameLayout.LayoutParams(-1, -1))
        youtubeEmpty = ui.line(true).apply {
            gravity = Gravity.CENTER
            contentDescription = "YouTube panel"
            background = ui.shape(0xFF19212B.toInt(), 12)
            addView(ui.icon("play", 0xFFFF4444.toInt(), 34), LayoutParams(ui.dp(42), ui.dp(42)))
            addView(ui.label("YouTube", 22, bold = true).apply { gravity = Gravity.CENTER })
            with(ui) { gap(8) }
            addView(ui.label("Partage une vidéo vers AutoDrive\nou ajoute son lien pour la regarder ici.", 14, P.muted).apply {
                maxLines = 2
                gravity = Gravity.CENTER
            })
            with(ui) { gap(12) }
            addView(ui.action("Ajouter une vidéo", run = actions::openMedia), LayoutParams(ui.dp(220), ui.dp(48)))
        }
        mediaContent.addView(youtubeEmpty, FrameLayout.LayoutParams(-1, -1))
        addView(mediaContent, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun bottom() = ui.line().apply {
        val items = listOf(
            Triple("Phone", "phone", actions::phone),
            Triple("Media", "media", ::focusMedia),
            Triple("Apps", "apps", actions::apps),
            Triple("Settings", "settings", actions::settings)
        )
        items.forEachIndexed { index, (title, icon, click) ->
            if (index > 0) with(ui) { gap(7) }
            val color = when (icon) {
                "phone" -> 0xFF64DF7D.toInt()
                "media" -> 0xFF51A9FF.toInt()
                else -> P.text
            }
            val iconName = icon.takeIf { resources.configuration.screenWidthDp >= 480 }
            addView(ui.action(title, iconName, icon == "media", color, click),
                LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun focusMedia() {
        if (videoPane != null) {
            actions.closeVideo()
            return
        }
        val tab = previous?.tab ?: MediaTab.YOUTUBE
        tabButtons[tab]?.requestFocusFromTouch()
        tabButtons[tab]?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    fun setMapWidget(widget: View?) {
        if (mapFrame.childCount > 1) mapFrame.removeViewAt(1)
        mapPlaceholder.visibility = if (widget == null) VISIBLE else GONE
        if (widget != null) {
            mapFrame.addView(widget, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    fun setAdjacentMode(adjacent: Boolean) {
        val visibility = if (adjacent && compactWidth) GONE else VISIBLE
        navigationPanel.visibility = visibility
        panelGap.visibility = visibility
    }

    fun canShowYouTube(): Boolean {
        val target = playerFrame()
        val bounds = android.graphics.Rect()
        return target.width >= ui.dp(200) && target.height >= ui.dp(200) &&
            target.getGlobalVisibleRect(bounds) && bounds.width() >= ui.dp(200) &&
            bounds.height() >= ui.dp(200) && (!compactWidth || bounds.height() == target.height)
    }

    private fun playerFrame(): FrameLayout = if (mediaContent.width >= ui.dp(200) &&
        mediaContent.height >= ui.dp(200)) mediaContent else mediaFrame

    fun setVideo(view: View?) {
        videoPane?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        videoPane = null
        mediaHub.visibility = VISIBLE
        updateMediaBody()
        if (view == null) return
        videoPane = view
        mediaBody.visibility = GONE
        youtubeEmpty.visibility = GONE
        val target = playerFrame()
        if (target === mediaFrame) mediaHub.visibility = GONE
        target.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    private fun updateMediaBody() {
        if (videoPane != null) return
        val youtube = previous?.tab == MediaTab.YOUTUBE
        youtubeEmpty.visibility = if (youtube) VISIBLE else GONE
        mediaBody.visibility = if (youtube) GONE else VISIBLE
    }

    fun render(state: DashboardState) {
        val old = previous
        previous = state
        updateMediaBody()
        if (old?.gps != state.gps) {
            speed.text = state.gps.speed.kmh?.toString() ?: "--"
            gps.text = state.gps.direction?.let { "${state.gps.status} · $it" } ?: state.gps.status
        }
        if (old?.device != state.device) {
            clock.text = state.device.clock
            wifi.style(color = if (state.device.wifi) P.text else P.dim)
            bluetooth.style(color = if (state.device.bluetooth == true) P.text else P.dim)
            network.style(color = if (state.device.network) P.text else P.dim)
            wifi.contentDescription = if (state.device.wifi) "Wi-Fi on" else "Wi-Fi off"
            bluetooth.contentDescription = when (state.device.bluetooth) {
                true -> "Bluetooth on"
                false -> "Bluetooth off"
                null -> "Bluetooth status unavailable"
            }
            network.contentDescription = if (state.device.network) "Internet connected" else "Internet unavailable"
        }
        if (old?.mapsInstalled != state.mapsInstalled || old?.device?.network != state.device.network) {
            mapStatus.text = when {
                !state.mapsInstalled -> "Google Maps app unavailable"
                !state.device.network -> "Offline · Check downloaded maps"
                else -> "Nearby Traffic widget unavailable"
            }
        }
        if (old?.tab != state.tab) {
            for ((tab, view) in tabButtons) {
                val active = tab == state.tab
                view.isSelected = active
                ui.touch(view, if (active) P.blue else 0, 12) {
                    actions.tab(tab)
                }
                tabIcons.getValue(tab).style(color = if (tab == MediaTab.YOUTUBE) 0xFFFF4444.toInt() else P.text)
            }
        }
        val media = state.media
        if (old?.media?.artwork !== media.artwork) art.setImageBitmap(media.artwork)
        art.visibility = if (media.artwork == null) GONE else VISIBLE
        artIcon.visibility = if (media.artwork == null) VISIBLE else GONE
        artIcon.style(when (state.tab) {
            MediaTab.YOUTUBE -> "play"
            MediaTab.RADIO -> "radio"
            MediaTab.IPTV -> "tv"
        }, if (state.tab == MediaTab.YOUTUBE) 0xFFFF4444.toInt() else P.blue)
        mediaTitle.text = media.title ?: state.mediaApp?.label ?: state.tab.title
        mediaSub.text = media.subtitle ?: if (state.tab == MediaTab.RADIO) "Radio app" else "Dashboard playback"
        mediaStatus.text = when {
            state.mediaApp != null -> media.status
            state.tab == MediaTab.YOUTUBE -> "YouTube app unavailable"
            state.mediaAssigned -> "Saved ${state.tab.title} app unavailable"
            else -> "${state.tab.title} app not selected"
        }
        mediaHint.text = when {
            state.tab == MediaTab.YOUTUBE -> "Open player ›"
            state.tab != MediaTab.RADIO -> "Watch here ›"
            state.mediaApp != null -> "Open ${state.tab.title} ›"
            else -> "Choose app ›"
        }
        mediaHint.visibility = if (mediaHint.text.isEmpty()) GONE else VISIBLE
        favoriteButton.visibility = if (state.tab == MediaTab.RADIO && media.favorites.isNotEmpty()) VISIBLE else GONE
        if (shortHeight && old?.media?.favorites != media.favorites) {
            val heading = favoriteButton.parent as View
            heading.layoutParams = heading.layoutParams.apply {
                height = ui.dp(if (favoriteButton.visibility == VISIBLE) 48 else 36)
            }
        }
        playAction = when {
            media.playing && media.supports(PlaybackState.ACTION_PAUSE) -> PlaybackState.ACTION_PAUSE
            !media.playing && media.supports(PlaybackState.ACTION_PLAY) -> PlaybackState.ACTION_PLAY
            media.supports(PlaybackState.ACTION_PLAY_PAUSE) -> PlaybackState.ACTION_PLAY_PAUSE
            media.playing -> PlaybackState.ACTION_PAUSE
            else -> PlaybackState.ACTION_PLAY
        }
        listOf(PlaybackState.ACTION_SKIP_TO_PREVIOUS, playAction, PlaybackState.ACTION_SKIP_TO_NEXT)
            .forEachIndexed { i, action ->
                val control = controls[i]
                val enabled = state.mediaApp != null && media.supports(action)
                control.isEnabled = enabled
                control.style(if (i == 1) (if (media.playing) "pause" else "play") else control.name,
                    if (enabled) P.text else P.dim)
                control.contentDescription = when (i) {
                    0 -> "Previous"
                    2 -> "Next"
                    else -> if (media.playing) "Pause" else "Play"
                }
            }
        progress.progress = if (media.positionMs >= 0 && media.durationMs > 0)
            (media.positionMs.toDouble() / media.durationMs * 1_000).toInt() else 0
        duration.text = if (media.positionMs >= 0) "${time(media.positionMs)} / ${time(media.durationMs)}" else "—"
    }

    private fun time(ms: Long) = String.format(Locale.ROOT, "%d:%02d", ms / 60_000, ms / 1_000 % 60)
}
