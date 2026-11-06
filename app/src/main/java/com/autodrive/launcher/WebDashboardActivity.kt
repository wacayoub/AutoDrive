package com.autodrive.launcher

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Browser-based dashboard; no native app embedding is claimed. */
class WebDashboardActivity : ComponentActivity() {
    private lateinit var ui: DriveViews
    private lateinit var links: AppLinks
    private lateinit var radioPhone: RadioAndPhoneStore
    private lateinit var radio: InternetRadio
    private var radioPanel: InternetRadioPanel? = null
    private var expandedRadioPanel: InternetRadioPanel? = null
    private lateinit var radioArtwork: RadioArtwork
    private var radioCatalogue = emptyList<RadioStation>()
    private var radioCatalogueLoaded = false
    private var radioLoading = false
    private var radioDirectoryError = false
    private var directoryJob: Job? = null
    private lateinit var gps: GpsMonitor
    private lateinit var root: FrameLayout
    private lateinit var homeView: LinearLayout
    private lateinit var maps: BrowserPanel
    private var youtube: BrowserPanel? = null
    private lateinit var mediaContent: FrameLayout
    private lateinit var mediaPane: LinearLayout
    private lateinit var mapPane: FrameLayout
    private lateinit var panelGap: View
    private lateinit var speed: android.widget.TextView
    private lateinit var status: android.widget.TextView
    private lateinit var clock: android.widget.TextView
    private val connectivityIcons = mutableMapOf<String, DriveIcon>()
    private val tabs = mutableMapOf<MediaTab, android.view.View>()
    private val keyboardChrome = mutableListOf<android.view.View>()
    private var homeVisible = true
    private var resumed = false
    private var mapFocused = true
    private var mapState: Bundle? = null
    private var youtubeState: Bundle? = null
    private lateinit var updates: UpdateViewModel
    private var updatePanel: UpdatePanelView? = null
    private var updateRendering: Job? = null
    private var installJob: Job? = null
    private var installSourcePending = false
    private var pageBack: (() -> Unit)? = null
    private val portrait get() = resources.configuration.screenHeightDp > resources.configuration.screenWidthDp
    private val compact get() = resources.configuration.screenWidthDp < 600
    private val handler = Handler(Looper.getMainLooper())
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (resumed) gps.start()
        updateStatus()
    }
    private val homePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val installSource = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (installSourcePending) {
            installSourcePending = false
            if (packageManager.canRequestPackageInstalls()) installUpdate()
            else message("Autorisation d’installation non accordée. La mise à jour reste prête.")
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            updateStatus()
            if (resumed) handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setDecorFitsSystemWindows(false)
        ui = DriveViews(this)
        links = AppLinks(this)
        radioPhone = RadioAndPhoneStore(this)
        radio = InternetRadio(this, ::updateRadio)
        radioArtwork = RadioArtwork(this)
        updates = ViewModelProvider(this)[UpdateViewModel::class.java]
        gps = GpsMonitor(this, ::updateStatus)
        mapState = state?.getBundle("maps")
        youtubeState = state?.getBundle("youtube")
        root = FrameLayout(this).apply {
            setBackgroundColor(Palette.background)
            setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(WindowInsets.Type.displayCutout() or WindowInsets.Type.systemBars())
                val keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom
                view.setPadding(safe.left, safe.top, safe.right, maxOf(safe.bottom, keyboard))
                val typing = insets.isVisible(WindowInsets.Type.ime())
                expandedRadioPanel?.setTyping(typing)
                keyboardChrome.forEach { it.visibility = if (typing) View.GONE else View.VISIBLE }
                if (::mediaPane.isInitialized && portrait) {
                    mapPane.visibility = if (typing && !mapFocused) View.GONE else View.VISIBLE
                    mediaPane.visibility = if (typing && mapFocused) View.GONE else View.VISIBLE
                    panelGap.visibility = if (typing) View.GONE else View.VISIBLE
                }
                insets
            }
        }
        maps = BrowserPanel(this, "https://www.google.com/maps/@?api=1&map_action=map", true, ::requestGps, ::openExternal) { mapFocused = true }
        homeView = dashboard()
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!homeVisible) { (pageBack ?: ::showHome).invoke(); return }
                val consumed = if (mapFocused) maps.back() else youtube?.back() == true
                if (!consumed && !getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)) finish()
            }
        })
        showHome()
        hideBars()
    }

    private fun dashboard() = ui.line(true).apply {
        setPadding(ui.dp(12), ui.dp(7), ui.dp(12), ui.dp(10))
        val header = ui.line(compact)
        val headerRow = if (compact) ui.line().apply {
            addView(ui.icon("brand", Palette.blue), LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)))
            addView(ui.label("AutoDrive", if (compact) 20 else 22, bold = true), LinearLayout.LayoutParams(0, -1, 1f))
            speed = ui.label("--", if (compact) 28 else 34, bold = true).apply { gravity = Gravity.CENTER }
            addView(speed)
            addView(ui.label(" km/h", if (compact) 12 else 14, Palette.muted))
            status = ui.label("GPS indisponible", 12, Palette.muted).apply { gravity = Gravity.CENTER }
            if (!compact) addView(status, LinearLayout.LayoutParams(0, -1, 1.3f))
            clock = ui.label("", if (compact) 17 else 19).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
            addView(clock, if (compact) LinearLayout.LayoutParams(0, -1, .7f) else LinearLayout.LayoutParams(-2, -2))
        } else ui.line().apply {
            fun separator() = addView(View(this@WebDashboardActivity).apply { setBackgroundColor(Palette.border) },
                LinearLayout.LayoutParams(ui.dp(1), ui.dp(26)))
            addView(ui.line().apply {
                addView(ui.icon("brand", Palette.blue), LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)))
                addView(ui.label("AutoDrive", 22, bold = true))
            }, LinearLayout.LayoutParams(0, -1, 1f))
            separator()
            addView(ui.line(true).apply {
                gravity = Gravity.CENTER
                addView(ui.line().apply {
                    gravity = Gravity.CENTER
                    speed = ui.label("--", 30, bold = true)
                    addView(speed)
                    addView(ui.label(" km/h", 12, Palette.muted))
                }, LinearLayout.LayoutParams(-1, 0, 1f))
                status = ui.label("GPS indisponible", 10, Palette.muted).apply { gravity = Gravity.CENTER }
                addView(status, LinearLayout.LayoutParams(-1, ui.dp(13)))
            }, LinearLayout.LayoutParams(0, -1, 1f))
            separator()
            addView(ui.line().apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                for (name in listOf("wifi", "bluetooth", "network")) {
                    val icon = ui.icon(name, Palette.dim, 17)
                    connectivityIcons[name] = icon
                    addView(icon, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
                }
                clock = ui.label("", 18).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
                addView(clock, LinearLayout.LayoutParams(ui.dp(92), -1))
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        header.addView(headerRow, LinearLayout.LayoutParams(if (compact) -1 else 0, if (compact) ui.dp(40) else -1,
            if (compact) 0f else 1f))
        if (compact) header.addView(status, LinearLayout.LayoutParams(-1, ui.dp(20)))
        addView(header.also(keyboardChrome::add), LinearLayout.LayoutParams(-1, ui.dp(if (compact) 60 else 50)))
        with(ui) { gap(8) }
        addView(ui.line(portrait).apply {
            mapPane = FrameLayout(this@WebDashboardActivity).apply {
                background = ui.shape(Palette.surface, 18, Palette.border)
                setPadding(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                maps.background = ui.shape(android.graphics.Color.BLACK, 16)
                maps.clipToOutline = true
                addView(maps, FrameLayout.LayoutParams(-1, -1))
            }
            addView(mapPane, panelParams(if (portrait) .46f else .52f))
            panelGap = View(this@WebDashboardActivity)
            addView(panelGap, if (portrait) LinearLayout.LayoutParams(1, ui.dp(10)) else LinearLayout.LayoutParams(ui.dp(10), 1))
            mediaPane = ui.line(true).apply {
                background = ui.shape(Palette.surface, 18, Palette.border)
                setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(4))
                addView(ui.line().apply {
                    gravity = Gravity.CENTER
                    MediaTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            MediaTab.RADIO -> "radio"
                            MediaTab.YOUTUBE -> "youtube"
                            MediaTab.IPTV -> "tv"
                        }
                        val button = FrameLayout(this@WebDashboardActivity).apply {
                            contentDescription = tab.title
                            tooltipText = tab.title
                            minimumWidth = ui.dp(48)
                            minimumHeight = ui.dp(48)
                            addView(View(this@WebDashboardActivity).apply {
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            }, FrameLayout.LayoutParams(ui.dp(40), ui.dp(32), Gravity.CENTER))
                            addView(ui.icon(icon, Palette.shortcut(icon), 22).apply {
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            }, FrameLayout.LayoutParams(ui.dp(26), ui.dp(26), Gravity.CENTER))
                        }
                        tabs[tab] = button
                        addView(button, LinearLayout.LayoutParams(ui.dp(52), ui.dp(48)).apply {
                            marginStart = ui.dp(2)
                            marginEnd = ui.dp(2)
                        })
                    }
                }.also(keyboardChrome::add))
                with(ui) { gap(2) }
                mediaContent = FrameLayout(this@WebDashboardActivity).apply {
                    background = ui.shape(android.graphics.Color.BLACK, 12)
                    clipToOutline = true
                }
                addView(mediaContent, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            addView(mediaPane, panelParams(if (portrait) .54f else .48f))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        with(ui) { gap(10) }
        addView(ui.line().apply {
            val items = listOf(
                Triple("Phone", "phone", { showPhone() }),
                Triple("Media", "media", { select(MediaTab.YOUTUBE) }),
                Triple("Apps", "apps", { showApps() }),
                Triple("Settings", "settings", { showSettings() })
            )
            items.forEachIndexed { index, (title, icon, action) ->
                if (index > 0) with(ui) { gap(7) }
                val buttonColor = if (icon == "media") 0xFF164E80.toInt() else Palette.raised
                val button = if (!compact) ui.action(title, icon, icon == "media", iconColor = Palette.shortcut(icon), run = action).apply {
                    ui.touch(this, buttonColor, 18, Palette.border, end = if (icon == "media") 0xFF123B63.toInt() else Palette.surface,
                        action = action)
                }
                else ui.line(true).apply {
                    gravity = Gravity.CENTER
                    contentDescription = title
                    addView(ui.icon(icon, Palette.shortcut(icon), 22),
                        LinearLayout.LayoutParams(ui.dp(24), ui.dp(25)))
                    addView(ui.label(title, 13).apply {
                        gravity = Gravity.CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    })
                    ui.touch(this, buttonColor, 18, Palette.border, end = Palette.surface, action = action)
                }
                addView(button, LinearLayout.LayoutParams(0, -1, 1f))
            }
        }.also(keyboardChrome::add), LinearLayout.LayoutParams(-1, ui.dp(if (compact) 62 else 54)))
    }

    private fun panelParams(weight: Float) = if (portrait) LinearLayout.LayoutParams(-1, 0, weight)
        else LinearLayout.LayoutParams(0, -1, weight)

    override fun onResume() {
        super.onResume()
        resumed = true
        gps.start()
        tick.run()
        if (homeVisible) startPanels()
        hideBars()
        updates.autoCheck()
    }

    override fun onStart() {
        super.onStart()
        updateRendering = CoroutineScope(Dispatchers.Main.immediate).launch {
            updates.state.collect { updatePanel?.render(it) }
        }
    }

    override fun onStop() { updateRendering?.cancel(); updateRendering = null; super.onStop() }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        gps.stop()
        radio.close()
        stopPanels()
        super.onPause()
    }

    override fun onDestroy() { installJob?.cancel(); directoryJob?.cancel(); radio.close(); radioArtwork.close(); maps.release(); youtube?.release(); super.onDestroy() }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("maps", maps.save() ?: mapState)
        outState.putBundle("youtube", youtube?.save() ?: youtubeState)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); showHome() }

    private fun hideBars() {
        window.insetsController?.apply {
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsets.Type.systemBars())
        }
    }

    private fun updateStatus() {
        if (!::speed.isInitialized) return
        val fix = gps.snapshot()
        speed.text = fix.speed.kmh?.toString() ?: "--"
        val device = deviceStatus(this)
        connectivityIcons["wifi"]?.style(color = if (device.wifi) Palette.text else Palette.dim)
        connectivityIcons["bluetooth"]?.style(color = if (device.bluetooth == true) Palette.cyan else Palette.dim)
        connectivityIcons["network"]?.style(color = if (device.network) Palette.text else Palette.dim)
        status.text = if (device.network) fix.status else "Hors connexion · ${fix.status}"
        clock.text = device.clock
    }

    private fun requestGps() {
        if (!gps.permitted()) permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        else if (!links.start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))) message(gps.snapshot().status)
    }

    private fun startPanels() {
        maps.start(mapState)
        mapState = null
        select(links.tab)
    }

    private fun stopPanels() {
        mapState = maps.save() ?: mapState
        maps.release()
        youtubeState = youtube?.save() ?: youtubeState
        youtube?.release()
    }

    private fun select(tab: MediaTab) {
        if (tab != MediaTab.RADIO) radio.close()
        radioPanel = null
        links.tab = tab
        youtubeState = youtube?.save() ?: youtubeState
        youtube?.release()
        youtube = null
        mediaContent.removeAllViews()
        tabs.forEach { (value, button) ->
            button.isSelected = value == tab
            val frame = button as FrameLayout
            val icon = frame.getChildAt(1) as DriveIcon
            icon.style(color = Palette.shortcut(icon.name))
            frame.getChildAt(0).background = ui.shape(if (value == tab) 0xFF244E73.toInt() else Palette.surface,
                10, if (value == tab) Palette.blue else android.graphics.Color.TRANSPARENT)
            ui.touch(button, android.graphics.Color.TRANSPARENT, 12) { select(value) }
        }
        if (tab == MediaTab.YOUTUBE) {
            val panel = BrowserPanel(this, "https://m.youtube.com/", false, {}, ::openExternal,
                nativeYouTube = { uri -> links.openYouTube(uri) }) { mapFocused = false }
            youtube = panel
            mediaContent.addView(panel, FrameLayout.LayoutParams(-1, -1))
            if (resumed && homeVisible) panel.start(youtubeState)
            youtubeState = null
        } else if (tab == MediaTab.RADIO) {
            val panel = createRadioPanel(false)
            radioPanel = panel
            mediaContent.addView(panel, FrameLayout.LayoutParams(-1, -1))
            updateRadio()
            loadRadioDirectory()
        } else {
            mediaContent.addView(ui.line(true).apply {
                gravity = Gravity.CENTER
                addView(ui.label(tab.title, 23, bold = true))
                addView(ui.label(links.forTab(tab, links.apps())?.label ?: "Choisir une application", 15, Palette.muted))
                addView(ui.action("Ouvrir ${tab.title}") {
                    val app = links.forTab(tab, links.apps())
                    if (app == null) chooseApp(tab) else if (!links.open(app)) message("Application indisponible")
                }, LinearLayout.LayoutParams(-1, ui.dp(48)))
            }, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun showHome() {
        window.insetsController?.hide(WindowInsets.Type.ime())
        expandedRadioPanel = null
        updatePanel = null
        pageBack = null
        homeVisible = true
        root.removeAllViews()
        root.addView(homeView)
        if (resumed) startPanels()
    }

    private fun page(title: String, body: LinearLayout, back: () -> Unit = ::showHome, scroll: Boolean = true) {
        expandedRadioPanel = null
        stopPanels()
        homeVisible = false
        pageBack = back
        root.removeAllViews()
        root.addView(ui.line(true).apply {
            setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(10))
            addView(ui.line().apply {
                addView(ui.action("Back", run = back), LinearLayout.LayoutParams(ui.dp(110), ui.dp(48)))
                addView(ui.label(title, 24, bold = true).apply { setPadding(ui.dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
            })
            if (scroll) addView(ScrollView(this@WebDashboardActivity).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
            else addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    private fun showApps() = page("Applications du boîtier", ui.line(true).apply {
        links.apps().forEach { app -> addView(ui.action(app.label) { if (!links.open(app)) message("Application indisponible") }) }
    })

    private fun showSettings() {
        updatePanel = null
        page("Réglages", ui.line(true).apply {
        fun row(title: String, action: () -> Unit) {
            addView(ui.action(title, run = action).apply {
                (getChildAt(0) as? android.widget.TextView)?.maxLines = 2
            })
        }
        row("GPS · autorisations", ::requestGps)
        row("Mises à jour · ${updates.source.versionName}", ::showUpdates)
        row("Revenir à l’accueil Google Maps") { mapState = null; maps.loadHome(); showHome() }
        row("Revenir à l’accueil YouTube") { youtubeState = null; links.tab = MediaTab.YOUTUBE; showHome() }
        val youtubeApp = links.forTab(MediaTab.YOUTUBE, links.apps())
        val youtubeLabel = youtubeApp?.label ?: if (links.association(MediaTab.YOUTUBE.name) == null)
            "Choisir une application" else "Application enregistrée indisponible"
        row("Application YouTube · $youtubeLabel") { chooseApp(MediaTab.YOUTUBE) }
        row("Ouvrir l’application YouTube") {
            if (!links.openYouTube()) {
                message("Application YouTube indisponible · choisis une application installée")
                chooseApp(MediaTab.YOUTUBE)
            }
        }
        addView(ui.label("Ce choix s’applique à l’application installée. Le panneau YouTube garde son fonctionnement actuel.", 13, Palette.muted).apply {
            maxLines = 3
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        })
        row("Stations radio Internet", ::manageRadioStations)
        row("Catalogue des radios du Maroc") { showMoroccoRadios() }
        row("Application Radio externe") { chooseApp(MediaTab.RADIO) }
        row("Application Téléphone · ${phoneAppLabel()}") { choosePhoneApp(::showSettings) }
        row("Application IPTV") { chooseApp(MediaTab.IPTV) }
        row("Choisir le lanceur Android") {
            val manager = getSystemService(RoleManager::class.java)
            try {
                if (manager.isRoleAvailable(RoleManager.ROLE_HOME) && !manager.isRoleHeld(RoleManager.ROLE_HOME))
                    homePicker.launch(manager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                else if (!links.start(Intent(Settings.ACTION_HOME_SETTINGS))) message("Sélection du lanceur indisponible")
            } catch (_: android.content.ActivityNotFoundException) { message("Sélection du lanceur indisponible") }
        }
        row("Paramètres Android") { links.start(Intent(Settings.ACTION_SETTINGS)) }
        addView(ui.label("Version web : les sites Google restent intacts. Le guidage vocal peut demander l’application Maps. La connexion Google peut nécessiter le navigateur externe.", 14, Palette.muted).apply {
            maxLines = 6
            setPadding(ui.dp(8), ui.dp(12), ui.dp(8), ui.dp(8))
        })
        })
    }

    private fun showUpdates() {
        val panel = UpdatePanelView(this, updates.source.versionName, updates::check, updates::download, ::installUpdate)
        updatePanel = panel
        page("Mises à jour AutoDrive", panel, ::showSettings)
        panel.render(updates.state.value)
    }

    private fun installUpdate() {
        if (installJob?.isActive == true) return
        installJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                val file = updates.verifiedFile()
                if (!packageManager.canRequestPackageInstalls()) {
                    installSourcePending = true
                    installSource.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                } else {
                    val uri = FileProvider.getUriForFile(this@WebDashboardActivity, "$packageName.updates", file)
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (!links.start(intent)) message("Installateur Android indisponible")
                }
            } catch (_: Exception) { message("Impossible d’installer cette mise à jour. Relance la recherche et le téléchargement.") }
        }
    }

    private fun chooseApp(tab: MediaTab) {
        val apps = links.apps()
        val dialog = AlertDialog.Builder(this).setTitle("Application ${tab.title}")
        if (apps.isEmpty()) dialog.setMessage("Aucune application compatible installée")
        else dialog.setItems(apps.map {
            if (tab == MediaTab.YOUTUBE) "${it.label}\n${it.component.packageName}" else it.label
        }.toTypedArray()) { _, index ->
                links.saveAssociation(tab.name, apps[index].component)
                if (homeVisible) select(tab) else showSettings()
            }
        if (tab == MediaTab.YOUTUBE) dialog.setNeutralButton("Effacer le choix") { _, _ ->
            links.saveAssociation(tab.name, null)
            if (!homeVisible) showSettings()
        }
        dialog.setNegativeButton("Annuler", null).show()
    }

    private fun availableRadioStations() = RadioQueue.merge(radioCatalogue, radioPhone.stations)

    private fun selectedRadioStation(): RadioStation? {
        val all = availableRadioStations()
        return all.firstOrNull { it.url == radioPhone.selectedUrl }
            ?: radioPhone.stations.firstOrNull()?.let { saved -> all.firstOrNull { it.url == saved.url } ?: saved }
            ?: all.firstOrNull()
    }

    private fun updateRadio() {
        val stations = availableRadioStations()
        for (panel in listOfNotNull(radioPanel.takeIf { homeVisible }, expandedRadioPanel)) panel.render(selectedRadioStation(), radio,
            stations, radioPhone.favoritesOnly, radioLoading, radioDirectoryError)
    }

    private fun createRadioPanel(expanded: Boolean) = InternetRadioPanel(this, radioArtwork, expanded,
        { showMoroccoRadios() }, { loadRadioDirectory(true) }, {
            radioPhone.favoritesOnly = it; updateRadio()
        }, ::playRadio, { station ->
            radioPhone.saveStation(station.copy(favorite = !station.favorite)); updateRadio()
        }, { changeRadioStation(-1) }, {
            selectedRadioStation()?.let { selected ->
                if (radio.station?.url != selected.url) playRadio(selected) else radio.toggle()
            }
        }, { changeRadioStation(1) })

    private fun playRadio(station: RadioStation) {
        // Keep user edits/favorites while enriching old saved rows with newly available artwork.
        val selected = availableRadioStations().firstOrNull { it.url == station.url } ?: station
        if (!radioPhone.saveStation(selected)) { message("Maximum 200 stations enregistrées"); return }
        radioPhone.selectedUrl = selected.url
        radio.play(selected)
    }

    private fun changeRadioStation(direction: Int) {
        val queue = RadioQueue.active(availableRadioStations(), radioPhone.favoritesOnly)
        RadioQueue.neighbor(queue, selectedRadioStation()?.url, direction)?.let(::playRadio)
    }

    private fun showMoroccoRadios(refresh: Boolean = false) {
        val panel = createRadioPanel(true)
        page("Radio Internet", panel, scroll = false)
        expandedRadioPanel = panel
        updateRadio()
        loadRadioDirectory(refresh)
    }

    private fun loadRadioDirectory(refresh: Boolean = false) {
        if (directoryJob?.isActive == true || (!refresh && radioCatalogueLoaded)) return
        radioLoading = true
        radioDirectoryError = false
        if (refresh) radioArtwork.retryFailures()
        updateRadio()
        directoryJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            val directory = MoroccoRadioDirectory(this@WebDashboardActivity)
            val cached = withContext(Dispatchers.IO) { directory.cached() to directory.needsRefresh() }
            if (cached.first.isNotEmpty()) {
                radioCatalogue = cached.first
                updateRadio()
            }
            if (refresh || cached.first.isEmpty() || cached.second) {
                val fetched = withContext(Dispatchers.IO) { runCatching { directory.fetch() } }
                fetched.onSuccess { radioCatalogue = it }
                radioDirectoryError = fetched.isFailure
                if (fetched.isFailure && radioCatalogue.isNotEmpty() && refresh)
                    message("Actualisation impossible · liste mémorisée conservée")
            }
            radioCatalogueLoaded = true
            radioLoading = false
            updateRadio()
        }
    }

    private fun editRadioStation(existing: RadioStation? = null) {
        val name = android.widget.EditText(this).apply { hint = "Nom de la station"; setSingleLine(true); setText(existing?.name.orEmpty()) }
        val url = android.widget.EditText(this).apply {
            hint = "Lien direct HTTPS · MP3, AAC ou HLS"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true); setText(existing?.url.orEmpty())
        }
        val logo = android.widget.EditText(this).apply {
            hint = "Lien du logo HTTPS · facultatif"; setSingleLine(true); setText(existing?.logoUrl.orEmpty())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val body = ui.line(true).apply { setPadding(ui.dp(16), 0, ui.dp(16), 0); addView(name); addView(url); addView(logo) }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "Ajouter une radio" else "Modifier la radio")
            .setView(body).setPositiveButton("Enregistrer", null).setNegativeButton("Annuler", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = RadioStation(name.text.toString(), url.text.toString(), existing?.favorite ?: false,
                    logo.text.toString().trim().takeIf { it.isNotEmpty() })
                when {
                    value.name.isBlank() -> name.error = "Indique un nom"
                    RadioStation.validUrl(value.url) == null -> url.error = "Lien direct HTTPS valide requis"
                    value.logoUrl != null && RadioStation.validUrl(value.logoUrl) == null -> logo.error = "Lien de logo HTTPS valide requis"
                    existing != null && existing.url != RadioStation.validUrl(value.url) && radioPhone.stations.size >= 200 -> url.error = "Supprime une station avant de modifier son lien"
                    !radioPhone.saveStation(value) -> url.error = "Maximum 200 stations"
                    else -> {
                        val normalized = RadioStation.validUrl(value.url)!!
                        if (radio.station?.url != normalized) radio.close()
                        if (existing != null && existing.url != normalized) {
                            radioPhone.removeStation(existing.url)
                            if (radio.station?.url == existing.url) radio.close()
                        }
                        radioPhone.selectedUrl = normalized
                        updateRadio()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun manageRadioStations() {
        val stations = radioPhone.stations
        val external = links.forTab(MediaTab.RADIO, links.apps())
        val dialog = AlertDialog.Builder(this).setTitle("Gérer les stations")
        if (stations.isEmpty()) dialog.setMessage("Aucune station enregistrée")
        else dialog.setItems(stations.map { it.name }.toTypedArray()) { _, i ->
            val station = stations[i]
            AlertDialog.Builder(this).setTitle(station.name).setItems(arrayOf("Écouter", "Modifier", "Supprimer")) { _, action ->
                when (action) {
                    0 -> { links.tab = MediaTab.RADIO; showHome(); playRadio(station) }
                    1 -> editRadioStation(station)
                    2 -> { if (radio.station?.url == station.url) radio.close(); radioPhone.removeStation(station.url); updateRadio() }
                }
            }.setNegativeButton("Annuler", null).show()
        }
        dialog.setPositiveButton("Ajouter") { _, _ -> editRadioStation() }
            .setNeutralButton("Radio externe") { _, _ ->
                if (external == null) chooseApp(MediaTab.RADIO) else if (!links.open(external)) message("Application radio indisponible")
            }.setNegativeButton("Fermer", null).show()
    }

    private fun phoneAppLabel(): String {
        val saved = links.association(AppLinks.PHONE) ?: return "Téléphone Android"
        return links.apps().firstOrNull { it.component == saved }?.label ?: "Application indisponible"
    }

    private fun choosePhoneApp(after: () -> Unit) {
        val apps = links.apps()
        val dialog = AlertDialog.Builder(this).setTitle("Application Téléphone / Bluetooth")
        if (apps.isEmpty()) dialog.setMessage("Aucune application installée disponible")
        else dialog.setItems(apps.map { "${it.label}\n${it.component.packageName}" }.toTypedArray()) { _, i ->
            links.saveAssociation(AppLinks.PHONE, apps[i].component); after()
        }
        dialog.setNeutralButton("Téléphone Android") { _, _ -> links.saveAssociation(AppLinks.PHONE, null); after() }
            .setNegativeButton("Annuler", null).show()
    }

    private fun showPhone() {
        val panel = PhonePanel(this, radioPhone.phoneDraft, radioPhone.phoneFavorites, phoneAppLabel(),
            { radioPhone.phoneDraft = it }, { number ->
                when {
                    DialNumber.normalize(number) == null -> message("Saisis un numéro valide")
                    !links.dial(number) -> message("Cette application ne reçoit pas les numéros. Utilise Ouvrir le téléphone ou change d’application.")
                }
            }, { choosePhoneApp(::showPhone) }, {
                if (!links.openPhone()) { message("Téléphone indisponible · choisis une application"); choosePhoneApp(::showPhone) }
            }, ::addPhoneFavorite, { favorite ->
                AlertDialog.Builder(this).setTitle("Supprimer ${favorite.name} ?").setPositiveButton("Supprimer") { _, _ ->
                    radioPhone.removePhoneFavorite(favorite.number); showPhone()
                }.setNegativeButton("Annuler", null).show()
            })
        page("Téléphone", panel)
    }

    private fun addPhoneFavorite(number: String) {
        val normalized = DialNumber.normalize(number) ?: run { message("Saisis d’abord un numéro valide"); return }
        val name = android.widget.EditText(this).apply { hint = "Nom du favori"; setSingleLine(true) }
        val dialog = AlertDialog.Builder(this).setTitle("Favori · $normalized").setView(name)
            .setPositiveButton("Enregistrer", null).setNegativeButton("Annuler", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (radioPhone.savePhoneFavorite(PhoneFavorite(name.text.toString(), normalized))) { dialog.dismiss(); showPhone() }
            else name.error = if (name.text.isBlank()) "Indique un nom" else "Maximum 6 favoris"
        } }
        dialog.show()
    }

    private fun openExternal(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        if (uri.scheme in setOf("geo", "google.navigation")) intent.setPackage(AppLinks.MAPS)
        if (!links.start(intent)) message("Aucune application compatible pour ce lien")
    }

    private fun message(value: String) = android.widget.Toast.makeText(this, value, android.widget.Toast.LENGTH_LONG).show()
}
