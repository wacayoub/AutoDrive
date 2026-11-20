package com.incardrive.home

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

/**
 * The roads and speed cameras around the car, shared by the home screen and [DriveWatch], so the map squares are
 * read and fetched once and a camera is announced once, whichever of the two is following the car.
 */
internal object RoadWatch {
    private var limits: RoadLimits? = null
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var fetching = false

    /** The camera already announced, until the car has passed it. */
    @Volatile var warnedCamera: Camera? = null

    /** The home screen's alert setting: [HomeActivity.ROAD_LIMIT] follows the road, 0 is off, else a fixed speed. */
    @Volatile var limitSetting = HomeActivity.ROAD_LIMIT

    fun limits(context: Context): RoadLimits = synchronized(this) {
        limits ?: run {
            val folder = context.applicationContext.noBackupFilesDir
            // Squares kept before 1.0.24 have no one-way roads and miss some cameras: fetched again.
            worker.execute { runCatching { File(folder, "roads").deleteRecursively() } }
            RoadLimits(File(folder, "roads2")).also { limits = it }
        }
    }

    /** Reads or fetches the map squares around the car off the main thread; [done] runs on the main thread after. */
    fun follow(context: Context, latitude: Double, longitude: Double, done: () -> Unit = {}) {
        val roads = limits(context)
        val now = System.currentTimeMillis()
        if (fetching || !roads.needsRefresh(latitude, longitude, now)) return
        fetching = true
        worker.execute {
            try {
                roads.refresh(latitude, longitude, now)
            } catch (_: Exception) {
            } finally {
                fetching = false
                Handler(Looper.getMainLooper()).post(done)
            }
        }
    }

    /** The heading to match roads with: only when moving, when the GPS heading means something. */
    fun bearing(fix: Location, kmh: Int?): Int? = if ((kmh ?: 0) >= 5 && fix.hasBearing()) fix.bearing.toInt() else null

    /**
     * Two short beeps through the media sound, which the head unit plays at the volume the driver listens to (its
     * notification sound is often silent while the radio plays).
     */
    fun beep(durationMs: Int) {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, durationMs)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, durationMs + 200L)
        }
    }
}

/**
 * Speed cameras and the speed limit while another app is in front (Google Maps, Waze, YouTube): the home screen
 * follows the GPS only while it is shown, so this service takes over then, with Android's « location » foreground
 * service and its small notification. It beeps once for each camera ahead and once on passing the road's limit, and
 * shows a red « Radar 400 m » pill at the top of the screen when Incar Drive may draw over other apps.
 */
class DriveWatch : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var manager: LocationManager? = null
    private var pill: TextView? = null
    private var overSpeed = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = follow(location)
        override fun onProviderDisabled(provider: String) = hidePill()
        override fun onProviderEnabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Fonts.init(this)
        val channel = NotificationChannel(CHANNEL, "Radars et limitations", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 0, Intent(this, HomeActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_home_location)
            .setContentTitle("Incar Drive surveille les radars")
            .setContentText("Limitations et radars aussi quand Maps est ouvert")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        runCatching { startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) }
            .onFailure { stopSelf(); return }
        manager = getSystemService(LocationManager::class.java)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        runCatching { manager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper()) }
            .onFailure { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        runCatching { manager?.removeUpdates(listener) }
        hidePill()
        super.onDestroy()
    }

    private fun follow(fix: Location) {
        // The home screen follows the car itself while it is shown.
        if (HomeVisibility.shown) {
            hidePill()
            overSpeed = false
            return
        }
        val kmh = if (fix.hasSpeed()) (fix.speed * 3.6f).toInt() else null
        RoadWatch.follow(this, fix.latitude, fix.longitude)
        val roads = RoadWatch.limits(this)
        val bearing = RoadWatch.bearing(fix, kmh)
        val road = roads.limitAt(fix.latitude, fix.longitude, bearing)
        val setting = RoadWatch.limitSetting
        val limit = if (setting == HomeActivity.ROAD_LIMIT) road?.limit else setting.takeIf { it > 0 }
        val ahead = roads.cameraAhead(fix.latitude, fix.longitude, bearing)?.takeIf { (kmh ?: 0) >= 10 }
        if (ahead == null) RoadWatch.warnedCamera = null
        else if (RoadWatch.warnedCamera != ahead.first) {
            RoadWatch.warnedCamera = ahead.first
            RoadWatch.beep(600)
        }
        val speed = kmh ?: 0
        if (limit != null && speed > limit && !overSpeed) {
            overSpeed = true
            RoadWatch.beep(400)
        } else if (limit == null || speed <= limit - 3) overSpeed = false
        val text = when {
            ahead != null -> listOfNotNull("Radar ${(ahead.second / 10) * 10} m", ahead.first.limit?.let { "$it" }).joinToString(" · ")
            limit != null && speed > limit -> "$speed km/h · limite $limit"
            else -> null
        }
        if (text == null) hidePill() else showPill(text)
    }

    private fun showPill(text: String) {
        if (!Settings.canDrawOverlays(this)) return
        pill?.let { it.text = text; return }
        val density = resources.displayMetrics.density
        val view = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Fonts.get(700)
            setPadding((18 * density).toInt(), (8 * density).toInt(), (18 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 22 * density
                setColor(HomeActivity.CALL_RED)
            }
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (10 * density).toInt()
        }
        if (runCatching { getSystemService(WindowManager::class.java).addView(view, params) }.isSuccess) pill = view
    }

    private fun hidePill() {
        pill?.let { view -> runCatching { getSystemService(WindowManager::class.java).removeView(view) } }
        pill = null
    }

    companion object {
        private const val CHANNEL = "drive_watch"
        private const val NOTIFICATION_ID = 41

        /** Started from the home screen, while it is in front, as Android requires for a location service. */
        fun start(context: Context) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            runCatching { context.startForegroundService(Intent(context, DriveWatch::class.java)) }
        }
    }
}
