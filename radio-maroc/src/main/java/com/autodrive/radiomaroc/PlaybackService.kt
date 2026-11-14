package com.autodrive.radiomaroc

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.autodrive.launcher.RadioArtwork
import com.autodrive.launcher.RadioQueue
import com.autodrive.launcher.RadioStation
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
internal class PlaybackService : MediaLibraryService() {
    companion object {
        const val NETWORK_UNAVAILABLE = "com.autodrive.radiomaroc.NETWORK_UNAVAILABLE"
        const val STREAM_STATION = "com.autodrive.radiomaroc.STREAM_STATION"
        const val STREAM_TITLE = "com.autodrive.radiomaroc.STREAM_TITLE"
        const val AUDIO_FORMAT = "com.autodrive.radiomaroc.AUDIO_FORMAT"
        const val PROGRAMME_SOURCE = "com.autodrive.radiomaroc.PROGRAMME_SOURCE"
        const val PROGRAMME_HOURS = "com.autodrive.radiomaroc.PROGRAMME_HOURS"
        const val LUXE_STATE = "com.autodrive.radiomaroc.LUXE_STATE"
        const val RETRY_PENDING = "com.autodrive.radiomaroc.RETRY_PENDING"
        private const val RETRY_FIRST_DELAY_MS = 2_000L
        private const val RETRY_MAX_DELAY_MS = 30_000L
        // About ten minutes of attempts before leaving the error on screen for the listener.
        private const val RETRY_MAX_ATTEMPTS = 24
        val LUXE_OBSERVE = SessionCommand("com.autodrive.radiomaroc.LUXE_OBSERVE", Bundle.EMPTY)
        val LUXE_PLAY = SessionCommand("com.autodrive.radiomaroc.LUXE_PLAY", Bundle.EMPTY)
        val LUXE_PAUSE = SessionCommand("com.autodrive.radiomaroc.LUXE_PAUSE", Bundle.EMPTY)
        // The activity and service share a process. Keep the latest view's order when listening in the background.
        private val visibleStations = MutableStateFlow<List<RadioStation>?>(null)
        var radioScreenActive = false

        fun setVisibleStations(stations: List<RadioStation>) {
            visibleStations.value = stations.filter { it != Stations.luxe }
        }
    }

    private var session: MediaLibrarySession? = null
    private lateinit var engine: ExoPlayer
    private val radioPreferences by lazy { getSharedPreferences("radio_maroc", MODE_PRIVATE) }
    private val stationPopup by lazy { StationPopup(this) }
    private val audioProcessor = RadioAudioProcessor()
    private val dolbySession = DolbySession(this)
    private var soundStatus = RadioSound.Status.NORMAL
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            RadioSound.PROFILE -> applySoundProfile()
            RadioSound.DOLBY -> updateDolbySession()
            "favorites", "directory_bookmarks" -> {
                session?.notifyChildrenChanged(CarRadioLibrary.FAVORITES, carFavorites().size, null)
            }
        }
    }
    private var carQueue: List<RadioStation>? = null
    private lateinit var sessionPlayer: RadioSessionPlayer
    private lateinit var artwork: RadioArtwork
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var logoWork: Job? = null
    private var programmeWork: Job? = null
    private var activeItem: MediaItem? = null
    private var activeLogo: ByteArray? = null
    private var activeLogoUri: Uri? = null
    private var programme: BroadcastProgramme? = null
    private var icyTitle: String? = null
    private val fallbackLogo by lazy { getDrawable(R.drawable.ic_radio)?.toBitmap(128, 128)?.let(CarArtworkProvider::png) }
    private lateinit var luxe: LuxeRadio
    private var luxeState = LuxeRadio.State.IDLE
    private var luxeProgramme: String? = null
    private val snrtAccess = SnrtAccess()
    private lateinit var connectivity: ConnectivityManager
    private var validatedNetwork: Network? = null
    private var networkUnavailable = false
    private var retryAfterOutage = false
    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryAttempt = 0
    private var retryPending = false
    private val retryStream = Runnable {
        setRetryPending(false)
        if (session == null) return@Runnable
        if (engine.playWhenReady && engine.playerError != null && !networkUnavailable) engine.prepare()
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (validated) validatedNetwork = network
            setNetworkUnavailable(!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                (validatedNetwork == network && !validated))
        }

        override fun onLost(network: Network) {
            if (validatedNetwork == network) validatedNetwork = null
            setNetworkUnavailable(true)
        }
    }

    private val recoveryListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val changed = activeItem != null && mediaItem != null && activeItem?.mediaId != mediaItem.mediaId
            if (activeItem?.mediaId != mediaItem?.mediaId) cancelStreamRetry(resetAttempts = true)
            if (changed && !radioScreenActive) {
                mediaItem?.mediaMetadata?.title?.takeIf { it.isNotBlank() }?.let(stationPopup::show)
            }
            watchStation(mediaItem)
            applySoundProfile()
            updateDolbySession()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            applySoundProfile()
            updateDolbySession()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) = updateDolbySession()

        override fun onMetadata(metadata: Metadata) {
            val icy = (0 until metadata.length()).firstNotNullOfOrNull { metadata[it] as? IcyInfo } ?: return
            val title = icy.title?.trim()?.takeIf { it.isNotEmpty() && it.length <= 160 }
            if (activeItem?.mediaId != engine.currentMediaItem?.mediaId || icyTitle == title) return
            icyTitle = title
            publishPresentation()
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = engine.currentMediaItem?.mediaId
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && Stations.snrt.any { it.url == id }) {
                snrtAccess.clear()
            }
            handleStreamError(error)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) cancelStreamRetry(resetAttempts = true)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.isPlaying) retryAfterOutage = false
            if (player.isPlaying) retryAttempt = 0
            recoverPlayback(player)
        }
    }

    override fun onCreate() {
        super.onCreate()
        artwork = RadioArtwork(this, maxImageBytes = 1024 * 1024, normalizeLogoMargins = true)
        luxe = LuxeRadio(this) { state, programme ->
            luxeState = state
            luxeProgramme = programme
            session?.setSessionExtras(sessionExtras())
        }
        connectivity = getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivity.activeNetwork
        val capabilities = connectivity.getNetworkCapabilities(activeNetwork)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
            validatedNetwork = activeNetwork
        }
        networkUnavailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true

        val network = DefaultHttpDataSource.Factory()
            .setUserAgent("RadioMarocCoran/1.0.0")
            .setConnectTimeoutMs(7_000)
            .setReadTimeoutMs(10_000)
        val broadcastNetwork = ResolvingDataSource.Factory(network) { request ->
            val authorized = snrtAccess.authorize(request.uri)
            if (authorized != request.uri) request.withUri(authorized)
            else if (request.uri.scheme == "https" && request.uri.host == Stations.RADIO_2M_STREAM_HOST &&
                request.uri.path?.startsWith("/live/eds/radio_2m/") == true) request.withAdditionalHeaders(
                mapOf("Origin" to "https://radio2m.ma", "Referer" to "https://radio2m.ma/"),
            ) else request
        }
        audioProcessor.profile = RadioSound.profile(radioPreferences)
        RadioSound.prepareClicks(this, radioPreferences)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableAudioTrackPlaybackParams(enableAudioOutputPlaybackParams)
                .setAudioProcessors(arrayOf(audioProcessor))
                .build()
        }
        engine = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(broadcastNetwork))
            // Keep up to 50 s of audio (under 1 MB at 128 kbit/s) so short dead zones on the road stay silent-free.
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build())
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        engine.addListener(recoveryListener)
        engine.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
                applySoundProfile()
            }
        })
        radioPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        applySoundProfile()
        sessionPlayer = RadioSessionPlayer(engine, { stationQueue().size > 1 }, ::changeStation)
        val reopen = PendingIntent.getActivity(this, 0, Intent(this, RadioActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        session = MediaLibrarySession.Builder(this, sessionPlayer, object : MediaLibrarySession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    if (!isOwnController(controller)) {
                        return if (controller.isTrusted || session.isMediaNotificationController(controller) ||
                            session.isAutomotiveController(controller) || session.isAutoCompanionController(controller))
                            super.onConnect(session, controller)
                        else MediaSession.ConnectionResult.reject()
                    }
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(LUXE_OBSERVE).add(LUXE_PLAY).add(LUXE_PAUSE).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands).build()
                }

                override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                    params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
                    Futures.immediateFuture(LibraryResult.ofItem(CarRadioLibrary.root(), CarRadioLibrary.rootParams(params)))

                override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                    parentId: String, page: Int, pageSize: Int, params: LibraryParams?):
                    ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = Futures.immediateFuture(
                    LibraryResult.ofItemList(CarRadioLibrary.children(
                        parentId, page, pageSize, carFavorites(),
                        browser.connectionHints.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 3),
                    ), params),
                )

                override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                    mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
                    CarRadioLibrary.item(mediaId, carFavorites())?.let { LibraryResult.ofItem(it, null) }
                        ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE),
                )

                override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                    query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
                    session.notifySearchResultChanged(browser, query, CarRadioLibrary.search(query, carFavorites()).size, params)
                    return Futures.immediateFuture(LibraryResult.ofVoid(params))
                }

                override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                    query: String, page: Int, pageSize: Int, params: LibraryParams?):
                    ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = Futures.immediateFuture(
                    LibraryResult.ofItemList(CarRadioLibrary.searchItems(query, carFavorites(), page, pageSize), params),
                )

                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    if (!isOwnController(controller)) return super.onCustomCommand(session, controller, customCommand, args)
                    when (customCommand) {
                        LUXE_OBSERVE -> luxe.observe()
                        LUXE_PLAY -> {
                            session.player.pause()
                            session.player.stop()
                            session.player.clearMediaItems()
                            luxe.play()
                        }
                        LUXE_PAUSE -> luxe.pause()
                        else -> return super.onCustomCommand(session, controller, customCommand, args)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
                    val own = isOwnController(controller)
                    val favorites = if (own) emptyList() else carFavorites()
                    var selectedQueue: List<RadioStation>? = null
                    val resolved = mediaItems.flatMap { item ->
                        val selection = CarRadioLibrary.selection(item.mediaId, favorites)
                            ?: item.requestMetadata.searchQuery?.let { query ->
                                CarRadioLibrary.stationForQuery(query, lastStationId(), favorites)
                                    ?.let { CarRadioLibrary.selection(it.url, favorites) }
                            }
                        if (selection != null) {
                            if (selectedQueue == null) selectedQueue = selection.queue
                            Stations.playbackItems(selection.station)
                        } else if (own && item.mediaId != Stations.luxe.url) {
                            listOfNotNull(Stations.directoryItem(item))
                        } else {
                            emptyList()
                        }
                    }
                    if (resolved.isNotEmpty()) {
                        carQueue = if (own) null else selectedQueue
                        if (luxeState != LuxeRadio.State.IDLE) luxe.leave()
                    }
                    return Futures.immediateFuture(resolved)
                }

                override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo):
                    ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val favorites = carFavorites()
                    val station = CarRadioLibrary.stationForQuery("", lastStationId(), favorites)
                    if (!isOwnController(controller)) {
                        carQueue = station?.let { CarRadioLibrary.selection(it.url, favorites)?.queue }
                    }
                    return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(
                        station?.let(Stations::playbackItems).orEmpty(), 0, C.TIME_UNSET,
                    ))
                }
            }).setSessionActivity(reopen).setSessionExtras(sessionExtras()).build()
        connectivity.registerDefaultNetworkCallback(networkCallback, Handler(Looper.getMainLooper()))
        scope.launch { visibleStations.collect { sessionPlayer.refreshStationCommands() } }
    }

    private fun sessionExtras() = Bundle().apply {
        putBoolean(NETWORK_UNAVAILABLE, networkUnavailable)
        putBoolean(RETRY_PENDING, retryPending)
        putString(LUXE_STATE, luxeState.name)
        putString(RadioSound.STATUS, soundStatus.name)
        if (dolbySession.activeId > 0) putInt(RadioSound.DOLBY_SESSION_ID, dolbySession.activeId)
        val currentInfo = luxeProgramme?.let { Stations.luxe.url to it }
            ?: activeItem?.let { item -> (programme?.title ?: icyTitle)?.let { item.mediaId to it } }
        currentInfo?.let { (station, title) ->
            putString(STREAM_STATION, station)
            putString(STREAM_TITLE, title)
            if (station == activeItem?.mediaId) programme?.let {
                putString(PROGRAMME_SOURCE, it.source)
                putString(PROGRAMME_HOURS, it.hours)
            }
        }
        if (::engine.isInitialized) {
            val format = engine.audioFormat
            val codec = when (format?.sampleMimeType) {
                "audio/mpeg" -> "MP3"
                "audio/mp4a-latm" -> "AAC"
                "audio/opus" -> "Opus"
                "audio/flac" -> "FLAC"
                "audio/vorbis" -> "Vorbis"
                else -> null
            }
            val specs = listOfNotNull(
                codec,
                format?.bitrate?.takeIf { it >= 1_000 }?.let { "${it / 1_000} kbit/s" },
            ).joinToString(" · ")
            if (specs.isNotEmpty()) putString(AUDIO_FORMAT, specs)
        }
    }

    private fun isOwnController(controller: MediaSession.ControllerInfo): Boolean =
        controller.uid == applicationInfo.uid && controller.packageName == packageName

    private fun lastStationId(): String? = radioPreferences.getString("last_station", null)

    private fun carFavorites() = CarRadioLibrary.favorites(
        radioPreferences.getStringSet("favorites", emptySet()).orEmpty(),
        StationDirectory.decode(radioPreferences.getString("directory_bookmarks", null).orEmpty()),
    )

    private fun updateDolbySession() {
        val useDolby = radioPreferences.getBoolean(RadioSound.DOLBY, false) && engine.currentMediaItem != null &&
            (engine.playbackState == Player.STATE_BUFFERING || engine.playbackState == Player.STATE_READY)
        val sessionId = if (useDolby) engine.audioSessionId.coerceAtLeast(0) else 0
        if (dolbySession.update(sessionId)) session?.setSessionExtras(sessionExtras())
    }

    private fun applySoundProfile() {
        val profile = RadioSound.profile(radioPreferences)
        audioProcessor.profile = profile
        val status = when {
            profile == SoundProfile.NORMAL -> RadioSound.Status.NORMAL
            engine.currentMediaItem == null || engine.playbackState != Player.STATE_READY -> RadioSound.Status.WAITING
            audioProcessor.hasPcm -> RadioSound.Status.ACTIVE
            else -> RadioSound.Status.UNAVAILABLE
        }
        if (soundStatus != status) {
            soundStatus = status
            session?.setSessionExtras(sessionExtras())
        }
    }

    private val moroccoStations by lazy { Stations.visible(StationTab.MOROCCO, emptySet(), "").filter { it != Stations.luxe } }
    private val quranStations by lazy { Stations.visible(StationTab.QURAN, emptySet(), "") }

    private fun stationQueue(): List<RadioStation> {
        val current = engine.currentMediaItem?.mediaId ?: return emptyList()
        return carQueue?.takeIf { queue -> queue.any { it.url == current } }
            ?: visibleStations.value ?: if (quranStations.any { it.url == current }) quranStations else moroccoStations
    }

    private fun changeStation(direction: Int) {
        val station = RadioQueue.neighbor(stationQueue(), engine.currentMediaItem?.mediaId, direction) ?: return
        RadioSound.playClick(this, radioPreferences)
        if (station.countryCode != null) {
            val prefs = radioPreferences
            val favorites = prefs.getStringSet("favorites", emptySet()).orEmpty()
            val bookmarks = StationDirectory.decode(prefs.getString("directory_bookmarks", null).orEmpty())
            val retained = bookmarks.filter { it.url != station.url && it.url in favorites }
            prefs.edit().putString("last_station", station.url)
                .putString("directory_bookmarks", StationDirectory.encode(listOf(station) + retained)).apply()
        }
        engine.setMediaItems(Stations.playbackItems(station))
        engine.prepare()
        engine.play()
    }

    private fun watchStation(item: MediaItem?) {
        logoWork?.cancel()
        programmeWork?.cancel()
        activeItem = item
        activeLogo = null
        programme = null
        icyTitle = null
        val station = item?.let { selected -> Stations.all.firstOrNull { it.url == selected.mediaId } }
        activeLogoUri = station?.let(CarRadioLibrary::portraitUri)
            ?: CarArtworkProvider.stationUri(item?.mediaMetadata?.artworkUri?.toString())
        publishPresentation()
        if (item == null) return
        if (station != null) getSharedPreferences("radio_maroc", MODE_PRIVATE)
            .edit().putString("last_station", station.url).apply()
        loadActiveLogo(item, station)
        val source = BroadcastProgrammes.source(station) ?: return
        programmeWork = scope.launch {
            while (isActive) {
                // Only query the broadcaster's site while the listener actually wants this station playing.
                if (!engine.playWhenReady) {
                    delay(5_000)
                    if (activeItem !== item) return@launch
                    continue
                }
                val current = if (networkUnavailable) null else withContext(Dispatchers.IO) {
                    runCatching { BroadcastProgrammes.fetch(source) }.getOrNull()
                }
                if (activeItem !== item) return@launch
                if (programme != current) {
                    programme = current
                    publishPresentation()
                }
                delay(120_000)
            }
        }
    }

    private fun loadActiveLogo(item: MediaItem, station: RadioStation?) {
        logoWork?.cancel()
        val portrait = station?.let(ReciterPortraits::forStation)
        val logo = item.mediaMetadata.artworkUri?.takeIf { it.scheme == "https" }
        val publishLogo: (ByteArray?) -> Unit = { bytes ->
            if (activeItem === item && bytes != null) {
                activeLogo = bytes
                activeLogoUri = CarArtworkProvider.publish(bytes)
                publishPresentation()
            }
        }
        val renderLogo: (Bitmap?) -> Unit = { bitmap ->
            if (activeItem === item && bitmap != null) publishLogo(CarArtworkProvider.png(bitmap))
        }
        val curated = CarArtworkProvider.stationUri(logo?.toString())
        logoWork = when {
            portrait != null -> artwork.loadPortrait(portrait.image, 256, renderLogo)
            curated != null -> scope.launch {
                publishLogo(withContext(Dispatchers.IO) {
                    CarArtworkProvider.stationImage(curated, artwork)
                })
            }
            logo != null -> artwork.load(logo.toString(), 256, renderLogo)
            else -> null
        }
    }

    private fun publishPresentation() {
        val item = activeItem
        val metadata = item?.mediaMetadata?.let { base ->
            val title = (programme?.title ?: icyTitle)?.takeUnless { it == base.title?.toString() }
            base.buildUpon()
                .setTitle(title ?: base.title)
                .setDisplayTitle(title ?: base.title)
                .setArtist(if (title == null) base.artist else base.title)
                .setSubtitle(if (title == null) base.subtitle else base.title)
                .setArtworkUri(activeLogoUri ?: Stations.defaultArtwork())
                .setArtworkData(activeLogo ?: fallbackLogo, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()
        }
        sessionPlayer.present(item?.mediaId, metadata)
        session?.setSessionExtras(sessionExtras())
    }

    private fun setNetworkUnavailable(unavailable: Boolean) {
        val current = session ?: return
        if (networkUnavailable == unavailable) return
        networkUnavailable = unavailable
        if (unavailable) snrtAccess.clear()
        else if (activeLogo == null) {
            artwork.retryFailures()
            activeItem?.let { item -> loadActiveLogo(item, Stations.all.firstOrNull { it.url == item.mediaId }) }
        }
        current.setSessionExtras(sessionExtras())
        recoverPlayback(current.player)
    }

    private fun recoverPlayback(player: Player) {
        if (!player.playWhenReady || player.currentMediaItem == null || player.playbackState == Player.STATE_ENDED) {
            retryAfterOutage = false
            return
        }
        val error = player.playerError?.errorCode
        // While offline, SNRT token fetches and HLS segments surface as generic IO errors, not only connection ones.
        val networkError = error != null && isRetryable(error)
        if (networkUnavailable) {
            retryAfterOutage = error == null || networkError
        } else if (retryAfterOutage && networkError) {
            // Keep the current item's position and let the player retain its play/pause intent.
            retryAfterOutage = false
            cancelStreamRetry(resetAttempts = false)
            player.prepare()
        }
    }

    private fun isRetryable(errorCode: Int): Boolean = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_UNSPECIFIED -> true
        else -> false
    }

    private fun handleStreamError(error: PlaybackException) {
        if (!engine.playWhenReady || engine.currentMediaItem == null) return
        // A recitation chapter that cannot be fetched should not end the whole recitation.
        if (engine.hasNextMediaItem() && (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS || retryAttempt >= 1)) {
            retryAttempt = 0
            engine.seekToNextMediaItem()
            engine.prepare()
            return
        }
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            engine.seekToDefaultPosition()
            engine.prepare()
            return
        }
        // Outages are resumed by the network callback once Android reports a usable connection again.
        if (networkUnavailable || !isRetryable(error.errorCode) || retryAttempt >= RETRY_MAX_ATTEMPTS) return
        val delayMs = (RETRY_FIRST_DELAY_MS shl retryAttempt.coerceAtMost(4)).coerceAtMost(RETRY_MAX_DELAY_MS)
        retryAttempt++
        retryHandler.removeCallbacks(retryStream)
        retryHandler.postDelayed(retryStream, delayMs)
        setRetryPending(true)
    }

    private fun cancelStreamRetry(resetAttempts: Boolean) {
        retryHandler.removeCallbacks(retryStream)
        if (resetAttempts) retryAttempt = 0
        setRetryPending(false)
    }

    private fun setRetryPending(pending: Boolean) {
        if (retryPending == pending) return
        retryPending = pending
        session?.setSessionExtras(sessionExtras())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        stationPopup.dismiss()
        retryHandler.removeCallbacks(retryStream)
        connectivity.unregisterNetworkCallback(networkCallback)
        radioPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        dolbySession.update(0)
        scope.cancel()
        artwork.close()
        luxe.close()
        session?.let {
            engine.removeListener(recoveryListener)
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
