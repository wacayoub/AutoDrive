package com.autodrive.launcher

import android.Manifest
import android.app.AlertDialog
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private enum class Screen { DASHBOARD, APPS, SETTINGS }

    private lateinit var ui: DriveViews
    private lateinit var links: AppLinks
    private lateinit var gps: GpsMonitor
    private lateinit var media: MediaSessions
    private lateinit var model: DashboardModel
    private lateinit var dashboard: DashboardView
    private lateinit var mapWidget: HomeMapWidget
    private lateinit var video: DashboardVideo
    private lateinit var root: android.widget.FrameLayout
    private var apps: List<Launchable> = emptyList()
    private var mapsInstalled = false
    private var displayedWidgetId: Int? = null
    private var screen = Screen.DASHBOARD
    private var rendering: Job? = null
    private var active = false
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            if (active) handler.postDelayed(this, 1_000)
        }
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (active) gps.start()
        refresh()
        if (screen == Screen.SETTINGS) show(Screen.SETTINGS)
    }
    private val homeRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (screen == Screen.SETTINGS) show(Screen.SETTINGS)
    }
    private val widgetRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        mapWidget.completeBinding(it.resultCode == Activity.RESULT_OK)
        updateMapWidget()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = DriveViews(this)
        links = AppLinks(this)
        gps = GpsMonitor(this, ::refresh)
        media = MediaSessions(this, ::refresh)
        mapWidget = HomeMapWidget(this)
        video = DashboardVideo(this, { uri ->
            val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(AppLinks.YOUTUBE)
            if (!links.start(intent)) message("YouTube app unavailable")
        }, {
            closeDashboardVideo()
            message("YouTube player stopped · Try again")
        })
        model = ViewModelProvider(this)[DashboardModel::class.java]
        root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Palette.background)
            setOnApplyWindowInsetsListener { view, insets ->
                val types = WindowInsets.Type.displayCutout() or
                    if (isInMultiWindowMode || android.os.Build.VERSION.SDK_INT >= 35)
                        WindowInsets.Type.systemBars() else 0
                val safe = insets.getInsets(types)
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                insets
            }
        }
        setContentView(root)
        dashboard = DashboardView(this, dashboardActions)
        dashboard.setAdjacentMode(isInMultiWindowMode)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (video.showing) closeDashboardVideo() else show(Screen.DASHBOARD)
            }
        }.also { panelBack = it })
        val restore = savedInstanceState?.getString("screen")
        show(Screen.entries.firstOrNull { it.name == restore } ?: Screen.DASHBOARD)
        receiveSharedYouTube(intent)
        hideSystemBars()
    }

    private lateinit var panelBack: OnBackPressedCallback

    override fun onStart() {
        super.onStart()
        mapWidget.start()
        rendering = CoroutineScope(Dispatchers.Main.immediate).launch {
            model.state.collect(dashboard::render)
        }
    }

    override fun onResume() {
        super.onResume()
        active = true
        reloadApps()
        gps.start()
        media.start()
        tick.run()
        if (screen != Screen.DASHBOARD) show(screen)
        hideSystemBars()
        if (screen == Screen.DASHBOARD && !isInMultiWindowMode) {
            mapWidget.setUpAutomatically(widgetRequest::launch)
        }
        updateMapWidget()
        restoreDashboardPlayer()
    }

    override fun onPause() {
        active = false
        closeDashboardVideo()
        handler.removeCallbacks(tick)
        gps.stop()
        media.stop()
        refresh()
        super.onPause()
    }

    override fun onStop() {
        rendering?.cancel()
        rendering = null
        mapWidget.stop()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode) window.insetsController?.show(WindowInsets.Type.systemBars())
        else hideSystemBars()
        if (::dashboard.isInitialized) {
            closeDashboardVideo()
            dashboard.setAdjacentMode(isInMultiWindowMode)
        }
        if (::root.isInitialized) root.requestApplyInsets()
    }

    private fun hideSystemBars() {
        if (isInMultiWindowMode) return
        window.insetsController?.apply {
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsets.Type.systemBars())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (receiveSharedYouTube(intent)) return
        if (intent.hasCategory(Intent.CATEGORY_HOME)) show(Screen.DASHBOARD)
    }

    private fun receiveSharedYouTube(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return false
        val source = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(VideoSource::youtubeShare)
        if (source == null) {
            message("Ce partage ne contient pas de lien vidéo YouTube valide")
            return true
        }
        links.dashboardYouTubeId = source.id
        links.tab = MediaTab.YOUTUBE
        show(Screen.DASHBOARD)
        refresh()
        return true
    }

    private fun restoreDashboardPlayer() {
        if (active && screen == Screen.DASHBOARD && links.tab == MediaTab.YOUTUBE) openYouTubePreview()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen.name)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("AppWidgetHost requires an activity request code for provider configuration.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == HomeMapWidget.CONFIGURE) {
            mapWidget.finishConfiguration(resultCode == Activity.RESULT_OK)
            updateMapWidget()
        }
    }

    private fun reloadApps() {
        apps = links.apps()
        mapsInstalled = links.mapsAvailable()
    }

    private fun updateMapWidget() {
        val id = mapWidget.widgetId.takeIf { mapWidget.bound() }
        if (id != displayedWidgetId) {
            val view = id?.let { mapWidget.view() }
            dashboard.setMapWidget(view)
            displayedWidgetId = id.takeIf { view != null }
        }
    }

    private fun refresh() {
        val tab = links.tab
        val fix = gps.snapshot()
        val app = links.forTab(tab, apps)
        media.select(app?.component?.packageName)
        model.update(DashboardState(
            tab, fix, deviceStatus(this), mapsInstalled, app,
            mediaAssigned = links.association(tab.name) != null, media = media.snapshot()
        ))
    }

    private val dashboardActions = object : DashboardActions {
        override fun phone() {
            val association = links.association(AppLinks.PHONE)
            val app = apps.firstOrNull { it.component == association }
            when {
                app != null -> launch(app)
                links.dial() -> Unit
                else -> message("Phone app unavailable")
            }
        }
        override fun apps() {
            reloadApps()
            show(Screen.APPS)
        }
        override fun settings() = show(Screen.SETTINGS)
        override fun location() = requestGps()
        override fun tab(value: MediaTab) {
            closeDashboardVideo()
            links.tab = value
            refresh()
            if (value == MediaTab.YOUTUBE) openYouTubePreview()
        }
        override fun openMedia() {
            val tab = links.tab
            when (tab) {
                MediaTab.RADIO -> openExternalMedia(tab)
                MediaTab.YOUTUBE -> if (links.dashboardYouTubeId == null) enterVideoLink(tab) else openYouTubePreview()
                MediaTab.IPTV -> chooseIptvSource()
            }
        }
        override fun closeVideo() = closeDashboardVideo()
        override fun transport(action: Long) {
            if (!media.command(action)) message("Media control unavailable")
            refresh()
        }
        override fun favorites() {
            val items = model.state.value.media.favorites
            if (items.isEmpty()) return
            AlertDialog.Builder(this@MainActivity).setTitle("Radio favorites")
                .setItems(items.map { it.second }.toTypedArray()) { _, i ->
                    if (!media.favorite(items[i].first)) message("Favorite action unavailable")
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun openExternalMedia(tab: MediaTab) {
        val app = links.forTab(tab, apps)
        when {
            app != null -> launch(app)
            tab == MediaTab.YOUTUBE -> message("Official YouTube app unavailable")
            else -> pickApp(tab.name, tab.title)
        }
    }

    private fun openYouTubePreview() {
        val source = links.dashboardYouTubeId?.let { VideoSource.youtube(it) }
        if (source != null) dashboard.post { openDashboardVideo(source, retryLayout = true) }
    }

    private fun chooseIptvSource() {
        val tab = MediaTab.IPTV
        val selected = links.forTab(tab, apps)
        val choices = listOf(
            "Watch an HTTPS HLS stream here" to { enterVideoLink(tab) },
            (selected?.let { "Open ${it.label}" } ?: "Choose IPTV app") to { openExternalMedia(tab) }
        )
        AlertDialog.Builder(this).setTitle(tab.title)
            .setItems(choices.map { it.first }.toTypedArray()) { _, index -> choices[index].second() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun enterVideoLink(tab: MediaTab) {
        val youtube = tab == MediaTab.YOUTUBE
        val input = EditText(this).apply {
            hint = if (youtube) "YouTube link or video ID" else "https://…/channel.m3u8"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            filters = arrayOf(android.text.InputFilter.LengthFilter(2048))
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (youtube) "Watch YouTube here" else "Watch IPTV here")
            .setMessage(if (youtube) "Paste a video link from YouTube Share."
                else "Use a direct HTTPS HLS link supplied by your IPTV provider for authorized playback.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Load video", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString()
                val source = if (youtube) VideoSource.youtube(text) else VideoSource.iptv(text)
                if (source == null) {
                    input.error = if (youtube) "Enter a YouTube video link or ID" else "Enter a direct HTTPS HLS link"
                } else {
                    if (source is VideoSource.YouTube) links.dashboardYouTubeId = source.id
                    dialog.window?.insetsController?.hide(WindowInsets.Type.ime())
                    dialog.dismiss()
                    // Let the dashboard recover its full player viewport after the keyboard closes.
                    dashboard.postDelayed({ openDashboardVideo(source, retryLayout = true) }, 450)
                }
            }
        }
        dialog.show()
    }

    private fun openDashboardVideo(source: VideoSource, retryLayout: Boolean) {
        val targetTab = if (source is VideoSource.YouTube) MediaTab.YOUTUBE else MediaTab.IPTV
        if (!active || screen != Screen.DASHBOARD || links.tab != targetTab || video.showing) return
        if (source is VideoSource.YouTube && links.dashboardYouTubeId != source.id) return
        if (source is VideoSource.YouTube && !dashboard.canShowYouTube()) {
            if (retryLayout) dashboard.postDelayed({ openDashboardVideo(source, retryLayout = false) }, 450)
            else message("This window is too small for the YouTube player · Open YouTube")
            return
        }
        try {
            dashboard.setVideo(video.create(source))
            panelBack.isEnabled = true
        } catch (error: RuntimeException) {
            android.util.Log.w("AutoDrive", "Dashboard player initialization failed (${error.javaClass.simpleName})")
            closeDashboardVideo()
            message("Video player unavailable")
        }
    }

    private fun closeDashboardVideo() {
        if (!video.showing) return
        dashboard.setVideo(null)
        video.close()
        panelBack.isEnabled = screen != Screen.DASHBOARD
    }

    private fun launch(app: Launchable) {
        if (!links.open(app)) {
            reloadApps()
            refresh()
            message("${app.label} is unavailable")
            if (screen == Screen.APPS) show(Screen.APPS)
        }
    }

    private fun requestGps() {
        when {
            !gps.permitted() -> permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION))
            gps.snapshot().status == "GPS disabled" -> systemSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            else -> message(gps.snapshot().status)
        }
    }

    private fun pickApp(key: String, title: String) {
        reloadApps()
        val options = apps.toList()
        AlertDialog.Builder(this).setTitle("$title app")
            .setItems(options.map { "${it.label}  ·  ${it.component.packageName}" }.toTypedArray()) { _, index ->
                links.saveAssociation(key, options[index].component)
                refresh()
                if (screen == Screen.SETTINGS) show(Screen.SETTINGS)
            }
            .setNeutralButton("Clear selection") { _, _ ->
                links.saveAssociation(key, null)
                refresh()
                if (screen == Screen.SETTINGS) show(Screen.SETTINGS)
            }.setNegativeButton("Cancel", null)
            .setMessageIfEmpty(options.isEmpty())
            .show()
    }

    private fun AlertDialog.Builder.setMessageIfEmpty(empty: Boolean): AlertDialog.Builder =
        if (empty) setMessage("No launchable apps are available") else this

    private fun chooseHome() {
        val manager = getSystemService(RoleManager::class.java)
        if (manager.isRoleAvailable(RoleManager.ROLE_HOME) && !manager.isRoleHeld(RoleManager.ROLE_HOME)) {
            try {
                homeRequest.launch(manager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // Some boxes expose Home selection only in Settings.
            }
        }
        systemSettings(Settings.ACTION_HOME_SETTINGS)
    }

    private fun systemSettings(action: String) {
        if (!links.start(Intent(action))) message("This Android setting is unavailable")
    }

    private fun show(value: Screen) {
        closeDashboardVideo()
        screen = value
        panelBack.isEnabled = value != Screen.DASHBOARD
        root.removeAllViews()
        root.addView(when (value) {
            Screen.DASHBOARD -> dashboard
            Screen.APPS -> drawer()
            Screen.SETTINGS -> settings()
        }, android.widget.FrameLayout.LayoutParams(-1, -1))
        restoreDashboardPlayer()
    }

    private fun page(title: String, content: View): View = ui.line(true).apply {
        setPadding(ui.dp(13), ui.dp(8), ui.dp(13), ui.dp(10))
        val header = ui.line().apply {
            addView(ui.action("Back") { show(Screen.DASHBOARD) }, LinearLayout.LayoutParams(ui.dp(96), ui.dp(48)))
            with(ui) { gap(14) }
            addView(ui.label(title, 25, bold = true))
        }
        addView(header)
        with(ui) { gap(10) }
        addView(ScrollView(this@MainActivity).apply { addView(content) },
            LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun drawer(): View {
        val columns = (resources.configuration.screenWidthDp / 180).coerceIn(1, 4)
        return page("Apps", GridLayout(this).apply {
            columnCount = columns
            for ((index, app) in apps.withIndex()) {
                val button = ui.action(app.label) { launch(app) }
                val icon = android.widget.ImageView(this@MainActivity).apply {
                    setImageDrawable(links.icon(app))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                button.addView(icon, 0, LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)).apply { marginEnd = ui.dp(8) })
                addView(button, GridLayout.LayoutParams(
                    GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1, GridLayout.FILL, 1f)
                ).apply {
                    width = 0
                    height = ui.dp(61)
                    setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3))
                })
            }
            if (apps.isEmpty()) addView(ui.label("No launchable apps", 18))
        })
    }

    private fun settings(): View = page("Settings", ui.line(true).apply {
        fun row(title: String, run: () -> Unit) {
            val item = ui.action(title, run = run).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(ui.dp(13), 0, ui.dp(13), 0)
            }
            addView(item, LinearLayout.LayoutParams(-1, ui.dp(49)))
            with(ui) { gap(5) }
        }
        val home = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
        row(if (home) "Android Home app · AutoDrive" else "Choose Android Home app", ::chooseHome)
        for ((key, title) in listOf(AppLinks.PHONE to "Phone", MediaTab.RADIO.name to "Radio", MediaTab.IPTV.name to "IPTV")) {
            val component = links.association(key)
            val selected = apps.firstOrNull { it.component == component }?.label
                ?: if (component == null) "Choose app" else "Saved app unavailable"
            row("$title · $selected") { pickApp(key, title) }
        }
        row(if (media.hasAccess()) "Media access · On" else "Media access · Off") {
            systemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        row("GPS · ${gps.snapshot().status}", ::requestGps)
        row("Bluetooth status permission") { permission.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) }
        if (!mapWidget.bound() && mapWidget.available()) row("Enable Google Maps widget") {
            show(Screen.DASHBOARD)
            if (!mapWidget.bind(widgetRequest::launch)) message("Maps Nearby Traffic widget is unavailable")
            updateMapWidget()
        }
        row(if (links.dashboardYouTubeId == null) "Set dashboard YouTube video" else "Change dashboard YouTube video") {
            links.tab = MediaTab.YOUTUBE
            show(Screen.DASHBOARD)
            refresh()
            enterVideoLink(MediaTab.YOUTUBE)
        }
        if (links.forTab(MediaTab.YOUTUBE, apps) != null) row("Open YouTube app") { openExternalMedia(MediaTab.YOUTUBE) }
        row("Android settings") { systemSettings(Settings.ACTION_SETTINGS) }
        val details = listOf(
            "Google Maps · ${links.mapsVersion() ?: "Unavailable"}",
            "Google Play services · ${links.playServices()}",
            "Map mode · Google Maps Nearby Traffic display",
            "Media access reads supported session metadata and controls. Dashboard playback starts when you press Play."
        )
        details.forEach { text ->
            addView(ui.label(text, 14, Palette.muted).apply {
                maxLines = 4
                setPadding(ui.dp(7), ui.dp(6), ui.dp(7), ui.dp(6))
            })
        }
    })

    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
