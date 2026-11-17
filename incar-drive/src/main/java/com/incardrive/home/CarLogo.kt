package com.incardrive.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import java.io.File
import kotlin.math.max

/**
 * The driver's own car logo at the left of the top bar (and a second image, a badge such as « Hybrid », beside it). Incar Drive ships no brand logo: the driver picks an image
 * on the head unit (Android's document picker), which is kept, scaled down, in the app's private files.
 */
internal class CarLogo(private val home: HomeActivity, private val name: String = "car_logo",
    private val request: Int = 47) {
    private val file get() = File(home.filesDir, "$name.png")
    private var view: ImageView? = null

    val chosen get() = file.isFile

    private var heightPx = 0

    /** The image at [heightDp] high, shrunk beforehand so its edges stay clean. */
    fun view(heightDp: Int): View = ImageView(home).apply {
        view = this
        heightPx = (heightDp * home.resources.displayMetrics.density).toInt()
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "Logo de la voiture"
        render()
    }

    /** The chosen logo, also worn by the car of the « Route » speed style; null when none is chosen. */
    fun bitmap(): Bitmap? = if (chosen) runCatching {
        // Mipmaps let the screen shrink the image smoothly, without jagged edges, at any size.
        BitmapFactory.decodeFile(file.path)?.apply { setHasMipMap(true) }
    }.getOrNull() else null

    private fun render() {
        val image = view ?: return
        val bitmap = bitmap()?.let { fit(it, heightPx) }
        image.setImageBitmap(bitmap)
        image.visibility = if (bitmap != null) View.VISIBLE else View.GONE
    }

    fun pick() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        try {
            @Suppress("DEPRECATION")
            home.startActivityForResult(intent, request)
        } catch (_: ActivityNotFoundException) {
            try {
                @Suppress("DEPRECATION")
                home.startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*"), request)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(home, "Aucun sélecteur d’images sur ce boîtier.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Keeps [bitmap] as this image, at most 512 px on its long side. */
    fun set(bitmap: Bitmap): Boolean = runCatching {
        val scale = 512f / max(bitmap.width, bitmap.height)
        val image = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }.getOrDefault(false)

    fun remove() {
        file.delete()
        render()
    }

    fun onResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != request) return false
        val uri = data?.data
        if (resultCode == Activity.RESULT_OK && uri != null) {
            if (save(uri)) home.recreate()
            else Toast.makeText(home, "Cette image n’a pas pu être lue.", Toast.LENGTH_LONG).show()
        }
        return true
    }

    /** Reads the image at most 512 px on its long side and keeps it as a PNG (transparency kept). */
    private fun save(uri: Uri): Boolean = runCatching {
        val resolver = home.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching false
        val scale = 512f / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1), true) else decoded
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }.getOrDefault(false)


    companion object {
        /**
         * [source] scaled to [heightPx] high, keeping its proportions: halved step by step, each step averaging four
         * pixels, then a last smooth step to the exact size. A single big reduction would skip pixels and leave
         * jagged or broken strokes, as on thin letters.
         */
        fun fit(source: Bitmap, heightPx: Int): Bitmap {
            if (heightPx <= 0 || source.height <= heightPx) return source
            var image = source
            while (image.height / 2 >= heightPx) {
                image = Bitmap.createScaledBitmap(image, (image.width / 2).coerceAtLeast(1), image.height / 2, true)
            }
            val width = (image.width.toLong() * heightPx / image.height).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(image, width, heightPx, true)
        }
    }
}
