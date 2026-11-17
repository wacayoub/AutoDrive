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
    fun position(latitude: Double, longitude: Double, bearing: Float?) = tiles?.moveTo(latitude, longitude, bearing)

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
        private var latitude = Double.NaN
        private var longitude = Double.NaN
        private var bearing: Float? = null
        private var zoom = 16

        var cameras: List<Camera> = emptyList()
            set(value) { field = value; invalidate() }

        init {
            // Tiles older than a month are fetched again: the map changes.
            pool.execute {
                val limit = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
                folder.walkTopDown().filter { it.isFile && it.lastModified() < limit }.forEach { it.delete() }
            }
        }

        fun moveTo(lat: Double, lon: Double, heading: Float?) {
            latitude = lat
            longitude = lon
            if (heading != null) bearing = heading
            invalidate()
        }

        fun zoomBy(step: Int) {
            zoom = (zoom + step).coerceIn(12, 18)
            invalidate()
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
            // The car sits a little below the middle, to show more of the road ahead when heading north.
            val carX = width / 2f
            val carY = height * .56f
            val centerX = worldX(longitude, zoom)
            val centerY = worldY(latitude, zoom)
            val firstX = floor(centerX - carX / tileSize).toInt()
            val firstY = floor(centerY - carY / tileSize).toInt()
            val count = 1 shl zoom
            var ty = firstY
            while ((ty - centerY) * tileSize + carY < height) {
                var tx = firstX
                while ((tx - centerX) * tileSize + carX < width) {
                    val left = ((tx - centerX) * tileSize + carX).toFloat()
                    val top = ((ty - centerY) * tileSize + carY).toFloat()
                    if (ty in 0 until count) {
                        val bitmap = tile(zoom, ((tx % count) + count) % count, ty)
                        val wrapped = ((tx % count) + count) % count
                        rect.set(left, top, left + tileSize, top + tileSize)
                        if (bitmap != null) canvas.drawBitmap(bitmap, null, rect, tilePaint)
                        trafficTile(zoom, wrapped, ty)?.let { canvas.drawBitmap(it, null, rect, trafficPaint) }
                    }
                    tx++
                }
                ty++
            }
            // Speed cameras: a red disc with a white rim.
            for (camera in cameras) {
                val x = ((worldX(camera.longitude, zoom) - centerX) * tileSize + carX).toFloat()
                val y = ((worldY(camera.latitude, zoom) - centerY) * tileSize + carY).toFloat()
                if (x < -20 || y < -20 || x > width + 20 || y > height + 20) continue
                paint.color = Color.WHITE
                canvas.drawCircle(x, y, 8 * density, paint)
                paint.color = 0xFFE5262E.toInt()
                canvas.drawCircle(x, y, 6 * density, paint)
                paint.color = Color.WHITE
                canvas.drawRect(x - 3 * density, y - 2 * density, x + 2 * density, y + 2 * density, paint)
                canvas.drawCircle(x + 3 * density, y, 1.6f * density, paint)
            }
            // The car: a halo, a white disc and an arrow turned to its heading.
            paint.color = (carColor and 0x00FFFFFF) or 0x33000000
            canvas.drawCircle(carX, carY, 20 * density, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(carX, carY, 11 * density, paint)
            paint.color = carColor
            canvas.save()
            canvas.rotate(bearing ?: 0f, carX, carY)
            arrow.reset()
            arrow.moveTo(carX, carY - 8 * density)
            arrow.lineTo(carX + 6 * density, carY + 6 * density)
            arrow.lineTo(carX, carY + 3 * density)
            arrow.lineTo(carX - 6 * density, carY + 6 * density)
            arrow.close()
            canvas.drawPath(arrow, paint)
            canvas.restore()
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
    }
}
