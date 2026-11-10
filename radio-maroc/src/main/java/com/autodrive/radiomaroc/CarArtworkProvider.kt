package com.autodrive.radiomaroc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException
import java.security.MessageDigest

/** Serves only the current station's public artwork, with no access to local files or remote URLs. */
internal class CarArtworkProvider : ContentProvider() {
    companion object {
        private const val AUTHORITY = "com.autodrive.radiomaroc.media-art"
        @Volatile private var cover: Pair<Uri, ByteArray>? = null

        fun publish(bytes: ByteArray?): Uri? {
            if (bytes == null) {
                cover = null
                return null
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val uri = Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(hash).build()
            cover = uri to bytes
            return uri
        }
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val bytes = cover?.takeIf { it.first == uri && mode == "r" }?.second
            ?: throw FileNotFoundException("Station artwork unavailable")
        return openPipeHelper(uri, "image/png", null, bytes) { output, _, _, _, image ->
            ParcelFileDescriptor.AutoCloseOutputStream(output).use { it.write(image) }
        }
    }

    override fun getType(uri: Uri): String? = "image/png".takeIf { cover?.first == uri }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
