package com.incardrive.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Incar Drive's own map on the home page, free and without an account: OpenStreetMap's tiles drawn around the car,
 * which stays in the middle with an arrow for its heading, the speed cameras found on the road, and, while Google
 * Maps or Waze guides, their next instruction on top. It is not Google Maps: no route line and no traffic; a tap
 * opens the navigation app. Tiles are fetched one by one as the car moves and kept on the head unit, as
 * OpenStreetMap's tile usage policy asks (identified client, cache, no bulk download), with its attribution shown.
 */
internal class OwnMap(private val home: HomeActivity, private val open: () -> Unit) {
    private var tiles: TileView? = null
    private var guide: LinearLayout? = null
    private lateinit var guideArrow: ImageView
    private lateinit var guideStep: TextView
    private lateinit var guideText: TextView
    private lateinit var guideEta: TextView

    fun view(): View = with(home) {
        FrameLayout(this).apply {
            background = glass()
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
            }
            val trafficKey = prefs.getString(TRAFFIC_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }
            val credit = label("", 9, if (night) 0xB3FFFFFF.toInt() else 0xB3000000.toInt(), 500)
            fun credits(note: String?) {
                credit.text = listOfNotNull("© OpenStreetMap", "© TomTom".takeIf { trafficKey != null }, note).joinToString(" · ")
            }
            credits(null)
            val map = TileView(context, night, navColor, trafficKey) { refused ->
                credits(if (refused) "clé trafic refusée" else null)
            }.also { tiles = it }
            map.contentDescription = "Carte autour de la voiture. Toucher pour ouvrir la navigation."
            map.setOnClickListener { open() }
            addView(map, FrameLayout.LayoutParams(-1, -1))
            // Zoom, at the right edge.
            addView(column().apply {
                fun zoom(sign: String, description: String, step: Int) = label(sign, 20, text, 700).apply {
                    gravity = Gravity.CENTER
                    background = pressable(GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(if (night) 0xD9141C2A.toInt() else 0xE6FFFFFF.toInt())
                        setStroke(dp(1), tint(home.text, 0x22))
                    }, 10)
                    contentDescription = description
                    setOnClickListener { map.zoomBy(step) }
                }
                // The compass: its needle shows the north; a touch switches between the road ahead up and the north up.
                val compass = zoom("N", "Orientation de la carte", 0).apply {
                    textSize = 13f
                    setTextColor(0xFFE5262E.toInt())
                    // Round, so it turns cleanly with the map.
                    background = pressable(GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (night) 0xD9141C2A.toInt() else 0xE6FFFFFF.toInt())
                        setStroke(dp(1), tint(home.text, 0x22))
                    }, 17)
                    setOnClickListener {
                        map.headingUp = !map.headingUp
                        prefs.edit().putBoolean(HEADING_UP, map.headingUp).apply()
                    }
                }
                map.headingUp = prefs.getBoolean(HEADING_UP, true)
                map.onRotated = { degrees -> if (compass.rotation != -degrees) compass.rotation = -degrees }
                addView(compass, LinearLayout.LayoutParams(dp(34), dp(34)).apply { bottomMargin = dp(6) })
                addView(zoom("+", "Zoomer", 1), LinearLayout.LayoutParams(dp(34), dp(34)))
                addView(zoom("−", "Dézoomer", -1), LinearLayout.LayoutParams(dp(34), dp(34)).apply { topMargin = dp(6) })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(8) })
            // The navigation app's next instruction, over the top of the map while it guides.
            guide = row().apply {
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(0xF0102A5C.toInt())
                }
                setPadding(dp(8), dp(6), dp(10), dp(6))
                guideArrow = ImageView(context).apply { imageTintList = ColorStateList.valueOf(Color.WHITE) }
                addView(guideArrow, LinearLayout.LayoutParams(dp(30), dp(30)))
                addView(column().apply {
                    guideStep = label("", 17, Color.WHITE, 700, condensed = true)
                    guideText = label("", 11, 0xE6FFFFFF.toInt(), 500).apply { maxLines = 2 }
                    addView(guideStep)
                    addView(guideText)
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
                guideEta = label("", 11, 0xFF9CC4FF.toInt(), 700).apply { gravity = Gravity.END; maxLines = 2 }
                addView(guideEta, wrap().apply { marginStart = dp(6) })
                visibility = View.GONE
                setOnClickListener { open() }
            }
            addView(guide, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { setMargins(dp(8), dp(8), dp(8), 0) })
            fun pill(title: String) = label(title, 12, home.text, 700).apply {
                maxLines = 1
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = pressable(GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (night) 0xE6141C2A.toInt() else 0xF2FFFFFF.toInt())
                    setStroke(dp(1), tint(home.text, 0x22))
                }, 12)
                visibility = View.GONE
            }
            // The street under the car, at the foot of the map.
            streetChip = pill("")
            addView(streetChip, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                setMargins(dp(40), 0, dp(40), dp(22))
            })
            // Shown during a look around: back on the car.
            val recentre = pill("Recentrer").apply {
                setTextColor(navColor)
                setOnClickListener { map.recentre() }
            }
            map.onPanned = { away -> recentre.visibility = if (away) View.VISIBLE else View.GONE }
            addView(recentre, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { setMargins(0, 0, 0, dp(56)) })
            addView(credit.apply {
                setPadding(dp(4), dp(1), dp(4), dp(1))
                background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(if (night) 0x80000000.toInt() else 0x99FFFFFF.toInt())
                }
            }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(6), 0, 0, dp(6)) })
        }
    }

    /** The car's position and, when it moves, its heading in degrees from north. */
    fun position(latitude: Double, longitude: Double, bearing: Float?, kmh: Int?) {
        // Debug builds only: « debug_bearing » turns the map where the emulator's GPS gives no heading.
        val forced = if (home.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            home.prefs.contains("debug_bearing")) home.prefs.getInt("debug_bearing", 0).toFloat() else null
        tiles?.moveTo(latitude, longitude, forced ?: bearing, kmh)
    }

    /** The street the car is on, shown at the foot of the map; null hides it. */
    fun street(name: String?) {
        val chip = streetChip ?: return
        chip.text = name.orEmpty()
        chip.visibility = if (name.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private var streetChip: TextView? = null

    fun cameras(list: List<Camera>) {
        tiles?.cameras = list
    }

    fun guide(nav: NavInfo?) {
        val bar = guide ?: return
        if (nav == null) {
            bar.visibility = View.GONE
            return
        }
        if (nav.arrow != null) {
            guideArrow.setImageDrawable(nav.arrow)
            guideArrow.rotation = 0f
        } else {
            guideArrow.setImageResource(R.drawable.ic_home_navigation)
            guideArrow.rotation = 45f
        }
        val title = nav.step ?: nav.remaining ?: "Guidage"
        guideStep.text = title
        val detail = nav.instruction?.takeIf { plain(it) != plain(title) }
        guideText.text = detail.orEmpty()
        guideText.visibility = if (detail == null) View.GONE else View.VISIBLE
        guideEta.text = listOfNotNull(nav.remaining?.takeIf { nav.step != null }, nav.arrival).joinToString("\n")
        guideEta.setTextColor(nav.flow?.color ?: Color.WHITE)
        bar.visibility = View.VISIBLE
    }

    private fun plain(value: String) = value.filterNot { it.isWhitespace() }.lowercase()

    /** The tiles, the cameras and the car. North is up; the car's arrow turns with its heading. */
    @SuppressLint("ViewConstructor")
    private class TileView(context: Context, private val night: Boolean, private val carColor: Int,
        private val trafficKey: String?, private val trafficRefused: (Boolean) -> Unit) : View(context) {
        // Live traffic (TomTom's flow tiles, with the driver's own free key): kept three minutes in memory only.
        private val traffic = object : LruCache<String, Pair<Bitmap, Long>>(32) {}
        private val trafficLoading = HashSet<String>()
        private var trafficBlockedUntil = 0L
        private val trafficPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val refresh = object : Runnable {
            override fun run() {
                invalidate()
                if (isAttachedToWindow && trafficKey != null) postDelayed(this, 60_000)
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (trafficKey != null) postDelayed(refresh, 60_000)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(refresh)
            super.onDetachedFromWindow()
        }

        private fun trafficTile(z: Int, x: Int, y: Int): Bitmap? {
            val key = trafficKey ?: return null
            val name = "$z/$x/$y"
            val now = System.currentTimeMillis()
            val known = traffic.get(name)
            if (known != null && now - known.second < TRAFFIC_FRESH_MS) return known.first
            if (name !in trafficLoading && now >= trafficBlockedUntil && isShown) {
                trafficLoading += name
                pool.execute {
                    var status = 0
                    val bitmap = runCatching {
                        val style = if (night) "relative0-dark" else "relative0"
                        val connection = URL("https://api.tomtom.com/traffic/map/4/tile/flow/$style/$z/$x/$y.png?key=$key")
                            .openConnection() as HttpURLConnection
                        try {
                            connection.connectTimeout = 8_000
                            connection.readTimeout = 12_000
                            status = connection.responseCode
                            if (status == 200) connection.inputStream.use { BitmapFactory.decodeStream(it) } else null
                        } finally {
                            connection.disconnect()
                        }
                    }.getOrNull()
                    main.post {
                        trafficLoading -= name
                        when {
                            bitmap != null -> {
                                traffic.put(name, bitmap to System.currentTimeMillis())
                                trafficRefused(false)
                                invalidate()
                            }
                            // A refused key or a spent allowance: stop asking for a while instead of hammering.
                            status == 401 || status == 403 || status == 429 -> {
                                trafficBlockedUntil = System.currentTimeMillis() + 10 * 60_000
                                trafficRefused(status != 429)
                            }
                            else -> trafficBlockedUntil = System.currentTimeMillis() + 30_000
                        }
                    }
                }
            }
            return known?.first
        }

        private val density = resources.displayMetrics.density
        // Tiles are 256 px squares; on denser screens they are enlarged so street names stay readable.
        private val tileSize = (256 * density.coerceAtLeast(1f)).toInt()
        private val memory = object : LruCache<String, Bitmap>(48) {}
        private val loading = HashSet<String>()
        private val failed = HashMap<String, Long>()
        private val pool = Executors.newFixedThreadPool(2) { Thread(it, "osm-tiles").apply { isDaemon = true } }
        private val main = Handler(Looper.getMainLooper())
        private val folder = File(context.cacheDir, "osm")
        private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            // Night: the light map inverted and its hues turned back, so roads stay light on a dark ground.
            if (night) colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                .574f, -1.43f, -.144f, 0f, 255f,
                -.426f, -.43f, -.144f, 0f, 255f,
                -.426f, -1.43f, .856f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )).apply { postConcat(ColorMatrix().apply { setScale(.78f, .8f, .9f, 1f) }) })
        }
        private val emptyPaint = Paint().apply { color = if (night) 0xFF121A26.toInt() else 0xFFE6E9ED.toInt() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arrow = Path()
        private val rect = RectF()
        private val source = android.graphics.Rect()
        // Where the car is and where it heads, as the navigation reports it…
        private var latitude = Double.NaN
        private var longitude = Double.NaN
        private var bearing = 0f
        // …and what is drawn now, which glides towards it instead of jumping at each GPS fix.
        private var shownLat = Double.NaN
        private var shownLon = Double.NaN
        private var shownBearing = 0f
        // The zoom the speed (or + / −) asks for, and the one drawn, which glides towards it: between two map levels
        // the tiles of the nearest level are drawn a little larger or smaller, so a little more speed widens the map a little.
        private var targetZoom = 16.5f
        private var shownZoom = 16.5f
        private var smoothKmh = -1f
        // A zoom chosen with + / − holds for a minute; then the zoom follows the speed again.
        private var manualZoomUntil = 0L
        /** The map turns so that the road ahead points up; false keeps the north up. */
        var headingUp = true
            set(value) { field = value; invalidate() }
        /** How far the map is turned now, in degrees, for the compass. */
        val turned get() = if (headingUp) shownBearing else 0f
        // The driver's look around, in pixels of the map at this zoom; back on the car after a while.
        private var panX = 0f
        private var panY = 0f
        private var pannedAt = 0L
        var onPanned: ((Boolean) -> Unit)? = null
        var onRotated: ((Float) -> Unit)? = null
        private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var dragging = false

        var cameras: List<Camera> = emptyList()
            set(value) { field = value; invalidate() }

        init {
            // Tiles older than a month are fetched again: the map changes.
            pool.execute {
                val limit = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
                folder.walkTopDown().filter { it.isFile && it.lastModified() < limit }.forEach { it.delete() }
            }
        }

        fun moveTo(lat: Double, lon: Double, heading: Float?, kmh: Int?) {
            latitude = lat
            longitude = lon
            if (heading != null) bearing = heading
            if (shownLat.isNaN()) { shownLat = lat; shownLon = lon; shownBearing = bearing }
            // Closer in town, wider on fast roads, step by step with the speed (smoothed, so a short burst does not
            // pump the map).
            if (kmh != null) {
                smoothKmh = if (smoothKmh < 0) kmh.toFloat() else smoothKmh + (kmh - smoothKmh) * .25f
                if (System.currentTimeMillis() > manualZoomUntil) {
                    val wanted = speedZoom(smoothKmh)
                    if (kotlin.math.abs(wanted - targetZoom) >= .08f) targetZoom = wanted
                }
            }
            invalidate()
        }

        /** About 17.4 standing still, 16 at 55 km/h, 15 at 95 km/h, 14 at 130 km/h, in between in between. */
        private fun speedZoom(kmh: Float) = (17.4f - kmh * .026f).coerceIn(13.8f, 17.4f)

        fun zoomBy(step: Int) {
            targetZoom = (kotlin.math.round(targetZoom) + step).coerceIn(12f, 18f)
            manualZoomUntil = System.currentTimeMillis() + 60_000
            panX = 0f
            panY = 0f
            invalidate()
        }

        /** Back on the car after a look around. */
        fun recentre() {
            panX = 0f
            panY = 0f
            onPanned?.invoke(false)
            invalidate()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y; lastX = event.x; lastY = event.y
                    dragging = false
                    // The map keeps its drags: the home pages do not slide under the finger.
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!dragging && (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop)) dragging = true
                    if (dragging) {
                        // The finger's move on screen, turned back into the map's own directions.
                        val angle = Math.toRadians(turned.toDouble())
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        panX -= (dx * cos(angle) - dy * kotlin.math.sin(angle)).toFloat()
                        panY -= (dx * kotlin.math.sin(angle) + dy * cos(angle)).toFloat()
                        pannedAt = System.currentTimeMillis()
                        onPanned?.invoke(true)
                        invalidate()
                    }
                    lastX = event.x; lastY = event.y
                }
                android.view.MotionEvent.ACTION_UP -> if (!dragging) performClick()
            }
            return true
        }

        private fun worldX(lon: Double, z: Int) = (lon + 180.0) / 360.0 * (1 shl z)
        private fun worldY(lat: Double, z: Int): Double {
            val r = Math.toRadians(lat)
            return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), emptyPaint)
            if (latitude.isNaN()) {
                paint.color = 0xFF8A93A3.toInt()
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 12 * density
                canvas.drawText("En attente du GPS…", width / 2f, height / 2f, paint)
                return
            }
            // Glide towards the latest fix and heading (the shortest way round for the heading).
            shownLat += (latitude - shownLat) * .12
            shownLon += (longitude - shownLon) * .12
            val turn = ((bearing - shownBearing + 540f) % 360f) - 180f
            shownBearing = (shownBearing + turn * .1f + 360f) % 360f
            val zoomGap = targetZoom - shownZoom
            shownZoom = if (kotlin.math.abs(zoomGap) < .004f) targetZoom else shownZoom + zoomGap * .06f
            val zoom = kotlin.math.round(shownZoom).toInt().coerceIn(12, 18)
            val tileSize = this.tileSize * Math.pow(2.0, (shownZoom - zoom).toDouble()).toFloat()
            val settled = kotlin.math.abs(latitude - shownLat) < 2e-7 && kotlin.math.abs(longitude - shownLon) < 2e-7 &&
                kotlin.math.abs(turn) < .4f && shownZoom == targetZoom
            // A look around ends by itself after fifteen seconds.
            if ((panX != 0f || panY != 0f) && !dragging && System.currentTimeMillis() - pannedAt > 15_000) recentre()
            onRotated?.invoke(turned)

            // Heading up, the car sits low in the card to show the road ahead; north up, near the middle.
            val carX = width / 2f
            val carY = height * (if (headingUp) .68f else .56f)
            val centerX = worldX(shownLon, zoom) + panX / tileSize
            val centerY = worldY(shownLat, zoom) + panY / tileSize
            // Tiles enough to cover the card whichever way it is turned.
            val reach = kotlin.math.hypot(maxOf(carX, width - carX).toDouble(), maxOf(carY, height - carY).toDouble()) / tileSize
            val count = 1 shl zoom
            canvas.save()
            canvas.rotate(-turned, carX, carY)
            var ty = floor(centerY - reach).toInt()
            val lastY = floor(centerY + reach).toInt()
            val lastX = floor(centerX + reach).toInt()
            while (ty <= lastY) {
                var tx = floor(centerX - reach).toInt()
                while (tx <= lastX) {
                    if (ty in 0 until count) {
                        val left = ((tx - centerX) * tileSize + carX).toFloat()
                        val top = ((ty - centerY) * tileSize + carY).toFloat()
                        val wrapped = ((tx % count) + count) % count
                        // One pixel over the edge, so no seam shows between turned tiles.
                        rect.set(left, top, left + tileSize + 1, top + tileSize + 1)
                        // Not fetched yet at this level: the level above, enlarged, rather than a hole.
                        tile(zoom, wrapped, ty)?.let { canvas.drawBitmap(it, null, rect, tilePaint) }
                            ?: memory.get("${zoom - 1}/${wrapped / 2}/${ty / 2}")?.let { above ->
                                val half = above.width / 2
                                source.set(wrapped % 2 * half, ty % 2 * half, wrapped % 2 * half + half, ty % 2 * half + half)
                                canvas.drawBitmap(above, source, rect, tilePaint)
                            }
                        trafficTile(zoom, wrapped, ty)?.let { canvas.drawBitmap(it, null, rect, trafficPaint) }
                    }
                    tx++
                }
                ty++
            }
            canvas.restore()
            // A point of the map on the screen, turned like the map.
            val angle = Math.toRadians(-turned.toDouble())
            fun screen(lat: Double, lon: Double): Pair<Float, Float> {
                val dx = (worldX(lon, zoom) - centerX) * tileSize
                val dy = (worldY(lat, zoom) - centerY) * tileSize
                return (carX + dx * cos(angle) - dy * kotlin.math.sin(angle)).toFloat() to
                    (carY + dx * kotlin.math.sin(angle) + dy * cos(angle)).toFloat()
            }
            // Speed cameras: a red disc with a white rim, upright whichever way the map is turned.
            for (camera in cameras) {
                val (x, y) = screen(camera.latitude, camera.longitude)
                if (x < -20 || y < -20 || x > width + 20 || y > height + 20) continue
                paint.color = Color.WHITE
                canvas.drawCircle(x, y, 8 * density, paint)
                paint.color = 0xFFE5262E.toInt()
                canvas.drawCircle(x, y, 6 * density, paint)
                paint.color = Color.WHITE
                canvas.drawRect(x - 3 * density, y - 2 * density, x + 2 * density, y + 2 * density, paint)
                canvas.drawCircle(x + 3 * density, y, 1.6f * density, paint)
            }
            // The car, where it really is (off centre during a look around): a halo, a white disc, an arrow that
            // points up when the map turns with it, to its heading otherwise.
            val (x, y) = screen(shownLat, shownLon)
            paint.color = (carColor and 0x00FFFFFF) or 0x33000000
            canvas.drawCircle(x, y, 22 * density, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(x, y, 12 * density, paint)
            paint.color = carColor
            canvas.save()
            canvas.rotate(if (headingUp) 0f else shownBearing, x, y)
            arrow.reset()
            arrow.moveTo(x, y - 9 * density)
            arrow.lineTo(x + 7 * density, y + 7 * density)
            arrow.lineTo(x, y + 3.5f * density)
            arrow.lineTo(x - 7 * density, y + 7 * density)
            arrow.close()
            canvas.drawPath(arrow, paint)
            canvas.restore()
            if (!settled && isShown) postInvalidateOnAnimation()
        }

        private fun tile(z: Int, x: Int, y: Int): Bitmap? {
            val key = "$z/$x/$y"
            memory.get(key)?.let { return it }
            val now = System.currentTimeMillis()
            if (key in loading || (failed[key] ?: 0L) > now - RETRY_MS) return null
            loading += key
            pool.execute {
                val bitmap = runCatching { load(z, x, y) }.getOrNull()
                main.post {
                    loading -= key
                    if (bitmap != null) {
                        memory.put(key, bitmap)
                        failed -= key
                        invalidate()
                    } else failed[key] = System.currentTimeMillis()
                }
            }
            return null
        }

        /** From the head unit's cache, else from OpenStreetMap's tile server (then kept). */
        private fun load(z: Int, x: Int, y: Int): Bitmap? {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            val file = File(folder, "$z/$x/$y.png")
            if (file.isFile && file.length() > 0) BitmapFactory.decodeFile(file.path, options)?.let { return it }
            val connection = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("User-Agent", "IncarDrive (+https://github.com/wacayoub/AutoDrive)")
                if (connection.responseCode != 200) return null
                val data = connection.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        if (out.size() > MAX_TILE_BYTES) return null
                    }
                    out.toByteArray()
                }
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return null
                file.parentFile?.mkdirs()
                file.writeBytes(data)
                return bitmap
            } finally {
                connection.disconnect()
            }
        }

        private companion object {
            const val RETRY_MS = 30_000L
            const val MAX_TILE_BYTES = 512 * 1024
            const val TRAFFIC_FRESH_MS = 180_000L
        }
    }

    companion object {
        /** The driver's own TomTom key for the traffic layer, typed once in the settings; none = no traffic. */
        const val TRAFFIC_KEY = "traffic_key"
        const val HEADING_UP = "map_heading_up"
    }
}
