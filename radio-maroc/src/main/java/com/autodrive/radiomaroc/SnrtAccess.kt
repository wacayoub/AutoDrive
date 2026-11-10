package com.autodrive.radiomaroc

import android.net.Uri
import android.util.LruCache
import com.autodrive.launcher.readUpTo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** Resolves temporary access issued by the SNRT Live player's CDN for its radio playlists. */
internal class SnrtAccess {
    private val grants = LruCache<String, Grant>(Stations.snrtStreamDirectories.size)

    fun clear() = grants.evictAll()

    fun authorize(uri: Uri): Uri {
        if (uri.scheme != "https" || uri.host != Stations.SNRT_STREAM_HOST || uri.port !in listOf(-1, 443)) return uri
        val parts = uri.pathSegments
        if (parts.size < 3 || parts.first() != "live" || parts[1] !in Stations.snrtStreamDirectories ||
            !parts.last().endsWith(".m3u8") || parts.any { it == "." || it == ".." || '/' in it || '\\' in it }) return uri

        val scope = "/live/${parts[1]}"
        val now = System.currentTimeMillis() / 1000
        val unsigned = uri.buildUpon().clearQuery().apply {
            for (name in uri.queryParameterNames - SIGNED_PARAMETERS) {
                for (value in uri.getQueryParameters(name)) appendQueryParameter(name, value)
            }
        }.build()
        val grant = grants.get(scope)?.takeIf { it.expiresAt > now + 20 } ?: fetch(unsigned, scope, now).also {
            grants.put(scope, it)
        }
        return unsigned.buildUpon()
            .appendQueryParameter("token", grant.token)
            .appendQueryParameter("token_path", grant.scope)
            .appendQueryParameter("expires", grant.expiresAt.toString())
            .build()
    }

    private fun fetch(playlist: Uri, scope: String, now: Long): Grant {
        val endpoint = Uri.parse("https://token.easybroadcast.io/all").buildUpon()
            .appendQueryParameter("url", playlist.toString()).build()
        val connection = URL(endpoint.toString()).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.setRequestProperty("User-Agent", "RadioMarocCoran")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Autorisation SNRT indisponible")
            val body = connection.inputStream.use { it.readUpTo(4097) }
            if (body.size > 4096) throw IOException("Réponse SNRT trop volumineuse")
            return parseGrant(body.toString(Charsets.UTF_8), scope, now)
                ?: throw IOException("Autorisation SNRT invalide ou expirée")
        } finally {
            connection.disconnect()
        }
    }

    internal data class Grant(val token: String, val scope: String, val expiresAt: Long)

    companion object {
        private val SIGNED_PARAMETERS = setOf("token", "token_path", "expires")
        private val tokenPattern = Regex("[A-Za-z0-9_+/=\\-]{16,256}")

        fun parseGrant(body: String, expectedScope: String, now: Long): Grant? = runCatching {
            val fields = body.trim().removePrefix("?").split('&').mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = entry.substring(0, separator)
                if (key !in SIGNED_PARAMETERS) return@mapNotNull null
                key to URLDecoder.decode(entry.substring(separator + 1), Charsets.UTF_8.name())
            }.toMap()
            val token = fields["token"]?.takeIf(tokenPattern::matches) ?: return null
            val scope = fields["token_path"]?.takeIf { it.trimEnd('/') == expectedScope } ?: return null
            val expiresAt = fields["expires"]?.toLongOrNull()?.takeIf { it > now + 5 } ?: return null
            Grant(token, scope, expiresAt)
        }.getOrNull()
    }
}
