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
 * The driver's own car logo at the left of the top bar. Incar Drive ships no brand logo: the driver picks an image
 * on the head unit (Android's document picker), which is kept, scaled down, in the app's private files.
 */
internal class CarLogo(private val home: HomeActivity) {
    private val file get() = File(home.filesDir, "car_logo.png")
    private var view: ImageView? = null

    val chosen get() = file.isFile

    fun view(): View = ImageView(home).apply {
        view = this
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
        contentDescription = "Logo de la voiture"
        render()
    }

    private fun render() {
        val image = view ?: return
        val bitmap = if (chosen) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null
        image.setImageBitmap(bitmap)
        image.visibility = if (bitmap != null) View.VISIBLE else View.GONE
    }

    fun pick() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        try {
            @Suppress("DEPRECATION")
            home.startActivityForResult(intent, REQUEST)
        } catch (_: ActivityNotFoundException) {
            try {
                @Suppress("DEPRECATION")
                home.startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*"), REQUEST)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(home, "Aucun sélecteur d’images sur ce boîtier.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun remove() {
        file.delete()
        render()
    }

    fun onResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST) return false
        val uri = data?.data
        if (resultCode == Activity.RESULT_OK && uri != null) {
            if (save(uri)) render()
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

    private companion object {
        const val REQUEST = 47
    }
}
