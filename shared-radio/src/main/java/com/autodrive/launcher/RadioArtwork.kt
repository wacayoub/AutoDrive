package com.autodrive.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Bounded, HTTPS-only station artwork. No synthetic branding or UI-thread downloads. */
internal class RadioArtwork(context: Context, private val maxImageBytes: Int = 512 * 1024) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO.limitedParallelism(2)
    private val folder = File(context.cacheDir, "radio-logos")
    private val memory = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failures = mutableSetOf<String>()
    var generation = 0
        private set
    fun retryFailures() { failures.clear(); generation++ }
    fun load(url: String?, ready: (Bitmap?) -> Unit): Job = scope.launch {
        val valid = url?.let(RadioStation::validUrl)
        if (valid == null || valid in failures) { ready(null); return@launch }
        memory.get(valid)?.let { ready(it); return@launch }
        val bitmap = withContext(io) { runCatching { read(valid) }.getOrNull() }
        if (bitmap != null) memory.put(valid, bitmap) else failures.add(valid)
        ready(bitmap)
    }
    private fun read(url: String): Bitmap? {
        folder.mkdirs()
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val cached = File(folder, key)
        if (cached.exists() && cached.length() <= maxImageBytes && System.currentTimeMillis() - cached.lastModified() < 7 * 86400000L) {
            decode(cached.readBytes())?.let { return it }
        }
        var target = url
        repeat(4) {
            val connection = URL(target).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "AutoDrive-Radio/1.6.1")
                if (connection.responseCode in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: return null
                    target = RadioStation.validUrl(URL(URL(target), location).toString()) ?: return null
                } else {
                    if (connection.responseCode != 200) return null
                    val bytes = connection.inputStream.use { it.readNBytes(maxImageBytes + 1) }
                    if (bytes.size > maxImageBytes) return null
                    val bitmap = decode(bytes) ?: return null
                    cached.writeBytes(bytes)
                    val files = folder.listFiles().orEmpty().sortedByDescending { it.lastModified() }
                    var total = 0L
                    files.forEachIndexed { index, file ->
                        total += file.length()
                        if (index >= 64 || total > 12 * 1024 * 1024) file.delete()
                    }
                    return bitmap
                }
            } finally { connection.disconnect() }
        }
        return null
    }
    private fun decode(bytes: ByteArray): Bitmap? {
        // A known generic WordPress favicon is not station branding. Match its bytes, not its
        // URL, so an eventual replacement by the broadcaster's real logo remains eligible.
        if (bytes.size == 4119 && MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) } ==
            "6bdb369337ac2496761c6f063bffea0aa6a91d4662279c399071a468251f51f0") return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 128) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    fun close() { scope.cancel(); memory.evictAll() }
}
