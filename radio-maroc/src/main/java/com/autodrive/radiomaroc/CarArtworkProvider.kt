package com.autodrive.radiomaroc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.autodrive.launcher.RadioArtwork
import com.autodrive.launcher.RadioStation
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.concurrent.Semaphore

/** Exposes curated picons and already-rendered covers to clients of the media session. */
internal class CarArtworkProvider : ContentProvider() {
    companion object {
        private const val AUTHORITY = "com.autodrive.radiomaroc.media-art"
        private const val STATION = "station"
        private const val COVER = "cover"
        private const val IMAGE_TYPE = "image/png"
        private val artworkSlots = Semaphore(2, true)
        private val curatedUrls by lazy {
            Stations.all.mapNotNull { it.logoUrl?.let(RadioStation::validUrl) }.distinct()
                .associateWith { hash(it.toByteArray(Charsets.UTF_8)) }
        }
        private val curatedByKey by lazy { curatedUrls.entries.associate { (url, key) -> key to url } }
        private val images = object : LruCache<Uri, ByteArray>(4 * 1024 * 1024) {
            override fun sizeOf(key: Uri, value: ByteArray): Int = value.size
        }

        fun stationUri(logo: String?): Uri? {
            val valid = logo?.let(RadioStation::validUrl) ?: return null
            return curatedUrls[valid]?.let { uri(STATION, it) }
        }

        fun publish(bytes: ByteArray): Uri = uri(COVER, hash(bytes)).also { images.put(it, bytes) }

        fun stationImage(uri: Uri, artwork: RadioArtwork): ByteArray? {
            images.get(uri)?.let { return it }
            val logo = curatedLogo(uri) ?: return null
            artworkSlots.acquire()
            try {
                val cached = images.get(uri)
                if (cached != null) return cached
                val bitmap = artwork.readLogo(logo, 256) ?: return null
                return png(bitmap).also { images.put(uri, it) }
            } finally {
                artworkSlots.release()
            }
        }

        fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }

        private fun curatedLogo(uri: Uri): String? {
            val key = uri.lastPathSegment ?: return null
            return curatedByKey[key]?.takeIf { uri == uri(STATION, key) }
        }

        private fun uri(kind: String, key: String): Uri =
            Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(kind).appendPath(key).build()

        private fun hash(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private val artwork by lazy {
        RadioArtwork(requireNotNull(context), maxImageBytes = 1024 * 1024, normalizeLogoMargins = true)
    }
    private val fallback by lazy { requireNotNull(context).getDrawable(R.drawable.ic_radio)?.toBitmap(128, 128)?.let(::png) }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || getType(uri) == null) throw FileNotFoundException("Station artwork unavailable")
        val cached = images.get(uri)
        return openPipeHelper(uri, IMAGE_TYPE, null, cached) { output, _, _, _, image ->
            ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream ->
                val bytes = image ?: stationImage(uri, artwork) ?: fallback
                if (bytes != null) stream.write(bytes)
            }
        }
    }

    override fun getType(uri: Uri): String? = IMAGE_TYPE.takeIf { images.get(uri) != null || curatedLogo(uri) != null }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
