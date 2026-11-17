package com.incardrive.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The car maker's logo fetched on the driver's request from Wikimedia Commons, where it is published as a public
 * domain text logo (still a trademark: it is downloaded to the head unit for the driver's own screen, never bundled
 * in the app). The emblem becomes the car logo in chrome silver, the brand name the badge beside it.
 */
internal object BrandLogo {
    // Toyota_carlogo.svg on Wikimedia Commons, rendered as a 960 px PNG: the emblem, then « TOYOTA ».
    private const val TOYOTA = "https://thumb.wikimedia.org/wikipedia/commons/thumb/9/9d/Toyota_carlogo.svg/960px-Toyota_carlogo.svg.png"
    private const val MAX_BYTES = 2 * 1024 * 1024

    /** Downloads the Toyota logo and splits it into (emblem in silver, wordmark); null when it cannot be read. */
    fun toyota(): Pair<Bitmap, Bitmap>? = runCatching {
        val connection = URL(TOYOTA).openConnection() as HttpURLConnection
        val data = try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "IncarDrive (https://github.com/wacayoub/AutoDrive)")
            if (connection.responseCode != 200) return@runCatching null
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) return@runCatching null
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
        val image = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return@runCatching null
        split(image)
    }.getOrNull()

    /** The first block of opaque columns is the emblem; what follows a clear gap is the name. */
    fun split(image: Bitmap): Pair<Bitmap, Bitmap>? {
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h)
        image.getPixels(pixels, 0, w, 0, 0, w, h)
        val filled = BooleanArray(w) { x -> (0 until h).any { y -> Color.alpha(pixels[y * w + x]) > 40 } }
        val start = filled.indexOfFirst { it }.takeIf { it >= 0 } ?: return null
        var end = start
        while (end < w && (end until minOf(w, end + 8)).any { filled[it] }) end++
        val nameStart = (end until w).firstOrNull { filled[it] } ?: return null
        val nameEnd = filled.indexOfLast { it } + 1
        val emblem = trim(Bitmap.createBitmap(image, start, 0, end - start, h)) ?: return null
        val name = trim(Bitmap.createBitmap(image, nameStart, 0, nameEnd - nameStart, h)) ?: return null
        return chrome(emblem) to name
    }

    private fun trim(image: Bitmap): Bitmap? {
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h)
        image.getPixels(pixels, 0, w, 0, 0, w, h)
        val rows = (0 until h).filter { y -> (0 until w).any { x -> Color.alpha(pixels[y * w + x]) > 40 } }
        if (rows.isEmpty()) return null
        return Bitmap.createBitmap(image, 0, rows.first(), w, rows.last() - rows.first() + 1)
    }

    /** The emblem recoloured as polished chrome: light at the top, a darker band below, its shape kept. */
    private fun chrome(image: Bitmap): Bitmap {
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h)
        image.getPixels(pixels, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            val t = y / (h - 1f).coerceAtLeast(1f)
            val shade = when {
                t < .45f -> 236 - (t / .45f * 40).toInt()
                t < .6f -> 196 - ((t - .45f) / .15f * 60).toInt()
                else -> 136 + ((t - .6f) / .4f * 80).toInt()
            }.coerceIn(0, 255)
            for (x in 0 until w) {
                val alpha = Color.alpha(pixels[y * w + x])
                pixels[y * w + x] = Color.argb(alpha, shade, (shade + 4).coerceAtMost(255), (shade + 10).coerceAtMost(255))
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * A « HYBRID » badge drawn by Incar Drive: wide, slightly slanted chrome letters with a dark edge, and a blue
     * sweep after the name, in the spirit of a hybrid car's boot badge. Drawn large so it shrinks cleanly.
     */
    fun hybridBadge(): Bitmap {
        val height = 120
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 800, false)
            textSize = height * .82f
            textSkewX = -.12f
            textScaleX = 1.18f
            letterSpacing = .04f
        }
        val word = "HYBRID"
        val wordWidth = text.measureText(word)
        val sweep = height * .42f
        val width = (wordWidth + sweep + height * .2f).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseline = height * .86f
        val x = height * .06f
        // Dark edge, then the chrome face: bright top, a sharp horizon, a lighter bottom.
        text.style = Paint.Style.STROKE
        text.strokeWidth = height * .06f
        text.strokeJoin = Paint.Join.ROUND
        text.color = 0xFF1C2230.toInt()
        canvas.drawText(word, x, baseline, text)
        text.style = Paint.Style.FILL
        text.shader = LinearGradient(0f, height * .14f, 0f, baseline, intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFE3E8EF.toInt(), 0xFF9AA4B4.toInt(), 0xFF6C7686.toInt(), 0xFFC9D0DA.toInt(),
        ), floatArrayOf(0f, .42f, .5f, .62f, 1f), Shader.TileMode.CLAMP)
        canvas.drawText(word, x, baseline, text)
        // The blue sweep: a tall, slanted crescent after the name.
        val left = x + wordWidth + height * .06f
        val top = height * .12f
        val path = Path().apply {
            moveTo(left + sweep * .62f, top)
            cubicTo(left + sweep * 1.15f, top + height * .2f, left + sweep * .9f, baseline - height * .1f, left + sweep * .18f, baseline)
            cubicTo(left + sweep * .62f, baseline - height * .22f, left + sweep * .8f, top + height * .22f, left + sweep * .62f, top)
            close()
        }
        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, top, 0f, baseline, 0xFF6FA8FF.toInt(), 0xFF1F4FB8.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawPath(path, blue)
        blue.shader = null
        blue.style = Paint.Style.STROKE
        blue.strokeWidth = height * .025f
        blue.color = 0xFFDCE8FF.toInt()
        canvas.drawPath(path, blue)
        return bitmap
    }
}
