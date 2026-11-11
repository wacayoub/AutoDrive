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
import java.io.ByteArrayOutputStream

@OptIn(UnstableApi::class)
internal class PlaybackService : MediaLibraryService() {
    companion object {
        const val NETWORK_UNAVAILABLE = "com.autodrive.radiomaroc.NETWORK_UNAVAILABLE"
        const val STREAM_STATION = "com.autodrive.radiomaroc.STREAM_STATION"
        const val STREAM_TITLE = "com.autodrive.radiomaroc.STREAM_TITLE"
        const val PROGRAMME_SOURCE = "com.autodrive.radiomaroc.PROGRAMME_SOURCE"
        const val PROGRAMME_HOURS = "com.autodrive.radiomaroc.PROGRAMME_HOURS"
        const val LUXE_STATE = "com.autodrive.radiomaroc.LUXE_STATE"
        val LUXE_OBSERVE = SessionCommand("com.autodrive.radiomaroc.LUXE_OBSERVE", Bundle.EMPTY)
        val LUXE_PLAY = SessionCommand("com.autodrive.radiomaroc.LUXE_PLAY", Bundle.EMPTY)
        val LUXE_PAUSE = SessionCommand("com.autodrive.radiomaroc.LUXE_PAUSE", Bundle.EMPTY)
        // The activity and service share a process. Keep the latest view's order when listening in the background.
        private val visibleStations = MutableStateFlow<List<RadioStation>?>(null)

        fun setVisibleStations(stations: List<RadioStation>) {
            visibleStations.value = stations.filter { it != Stations.luxe }
        }
    }

    private var session: MediaLibrarySession? = null
    private lateinit var engine: ExoPlayer
    private val radioPreferences by lazy { getSharedPreferences("radio_maroc", MODE_PRIVATE) }
    private val audioProcessor = RadioAudioProcessor()
    private val dolbySession = DolbySession(this)
    private var soundStatus = RadioSound.Status.NORMAL
    private val soundPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            RadioSound.PROFILE -> applySoundProfile()
            RadioSound.DOLBY -> updateDolbySession()
        }
    }
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
    private val fallbackLogo by lazy { getDrawable(R.drawable.ic_radio)?.toBitmap(128, 128)?.let(::png) }
    private lateinit var luxe: LuxeRadio
    private var luxeState = LuxeRadio.State.IDLE
    private var luxeProgramme: String? = null
    private val snrtAccess = SnrtAccess()
    private lateinit var connectivity: ConnectivityManager
    private var validatedNetwork: Network? = null
    private var networkUnavailable = false
    private var retryAfterOutage = false
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
            val id = session?.player?.currentMediaItem?.mediaId
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && Stations.snrt.any { it.url == id }) {
                snrtAccess.clear()
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.isPlaying) retryAfterOutage = false
            recoverPlayback(player)
        }
    }

    override fun onCreate() {
        super.onCreate()
        artwork = RadioArtwork(this, maxImageBytes = 1024 * 1024)
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
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(3_000, 12_000, 900, 1_600).build())
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
        radioPreferences.registerOnSharedPreferenceChangeListener(soundPreferenceListener)
        applySoundProfile()
        sessionPlayer = RadioSessionPlayer(engine, { stationQueue().size > 1 }, ::changeStation)
        val reopen = PendingIntent.getActivity(this, 0, Intent(this, RadioActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        session = MediaLibrarySession.Builder(this, sessionPlayer, object : MediaLibrarySession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    if (!isOwnController(controller)) {
                        return if (controller.isTrusted || session.isMediaNotificationController(controller) ||
                            session.isAutomotiveController(controller)) super.onConnect(session, controller)
                        else MediaSession.ConnectionResult.reject()
                    }
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(LUXE_OBSERVE).add(LUXE_PLAY).add(LUXE_PAUSE).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands).build()
                }

                override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                    params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
                    Futures.immediateFuture(LibraryResult.ofItem(CarRadioLibrary.root, params))

                override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                    parentId: String, page: Int, pageSize: Int, params: LibraryParams?):
                    ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = Futures.immediateFuture(
                    LibraryResult.ofItemList(CarRadioLibrary.children(parentId, page, pageSize), params),
                )

                override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                    mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
                    CarRadioLibrary.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                        ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE),
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
                    val resolved = mediaItems.mapNotNull { item ->
                        val station = Stations.all.firstOrNull { it.url == item.mediaId }
                            ?: item.requestMetadata.searchQuery?.let { query ->
                                CarRadioLibrary.stationForQuery(query, lastStationId())
                            }
                        when {
                            station == Stations.luxe -> null
                            station != null -> Stations.item(station)
                            isOwnController(controller) -> Stations.directoryItem(item)
                            else -> null
                        }
                    }
                    if (resolved.isNotEmpty() && luxeState != LuxeRadio.State.IDLE) luxe.leave()
                    return Futures.immediateFuture(resolved)
                }

                override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo):
                    ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val station = CarRadioLibrary.stationForQuery("", lastStationId())
                    return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(
                        listOfNotNull(station?.let(Stations::item)), 0, C.TIME_UNSET,
                    ))
                }
            }).setSessionActivity(reopen).setSessionExtras(sessionExtras()).build()
        connectivity.registerDefaultNetworkCallback(networkCallback, Handler(Looper.getMainLooper()))
        scope.launch { visibleStations.collect { sessionPlayer.refreshStationCommands() } }
    }

    private fun sessionExtras() = Bundle().apply {
        putBoolean(NETWORK_UNAVAILABLE, networkUnavailable)
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
    }

    private fun isOwnController(controller: MediaSession.ControllerInfo): Boolean =
        controller.uid == applicationInfo.uid && controller.packageName == packageName

    private fun lastStationId(): String? = radioPreferences.getString("last_station", null)

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
        return visibleStations.value ?: if (quranStations.any { it.url == current }) quranStations else moroccoStations
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
        engine.setMediaItem(Stations.item(station))
        engine.prepare()
        engine.play()
    }

    private fun watchStation(item: MediaItem?) {
        logoWork?.cancel()
        programmeWork?.cancel()
        activeItem = item
        activeLogo = null
        activeLogoUri = CarArtworkProvider.publish(null)
        programme = null
        icyTitle = null
        publishPresentation()
        if (item == null) return
        val station = Stations.all.firstOrNull { it.url == item.mediaId }
        if (station != null) getSharedPreferences("radio_maroc", MODE_PRIVATE)
            .edit().putString("last_station", station.url).apply()
        val logo = item.mediaMetadata.artworkUri?.takeIf { it.scheme == "https" }
        if (logo != null) logoWork = artwork.load(logo.toString()) { bitmap ->
            if (activeItem === item && bitmap != null) {
                activeLogo = png(bitmap)
                activeLogoUri = CarArtworkProvider.publish(activeLogo)
                publishPresentation()
            }
        }
        val source = BroadcastProgrammes.source(station) ?: return
        programmeWork = scope.launch {
            while (isActive) {
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

    private fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }

    private fun setNetworkUnavailable(unavailable: Boolean) {
        val current = session ?: return
        if (networkUnavailable == unavailable) return
        networkUnavailable = unavailable
        if (unavailable) snrtAccess.clear()
        current.setSessionExtras(sessionExtras())
        recoverPlayback(current.player)
    }

    private fun recoverPlayback(player: Player) {
        if (!player.playWhenReady || player.currentMediaItem == null || player.playbackState == Player.STATE_ENDED) {
            retryAfterOutage = false
            return
        }
        val error = player.playerError?.errorCode
        val networkError = error == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        if (networkUnavailable) {
            retryAfterOutage = error == null || networkError
        } else if (retryAfterOutage && networkError) {
            // Keep the current item's position and let the player retain its play/pause intent.
            retryAfterOutage = false
            player.prepare()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        connectivity.unregisterNetworkCallback(networkCallback)
        radioPreferences.unregisterOnSharedPreferenceChangeListener(soundPreferenceListener)
        dolbySession.update(0)
        scope.cancel()
        artwork.close()
        CarArtworkProvider.publish(null)
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
