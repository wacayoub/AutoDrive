package com.autodrive.launcher

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Reads public directory data; the audio is served by the stations. */
internal object RadioBrowser {
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun <T> read(path: String, userAgent: String, decode: (String) -> T): T {
        var lastFailure: Exception? = null
        for (server in listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info")) {
            try {
                val connection = URL("https://$server/json/$path").openConnection() as HttpURLConnection
                val bytes = try {
                    connection.connectTimeout = 7_000
                    connection.readTimeout = 8_000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", userAgent)
                    connection.setRequestProperty("Accept", "application/json")
                    if (connection.responseCode != 200) throw IOException("Radio Browser unavailable")
                    connection.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
                } finally {
                    connection.disconnect()
                }
                if (bytes.size > MAX_BYTES) throw IOException("Radio Browser response too large")
                return decode(bytes.toString(Charsets.UTF_8))
            } catch (error: Exception) {
                lastFailure = error
            }
        }
        throw IOException("Radio Browser unavailable", lastFailure)
    }
}
