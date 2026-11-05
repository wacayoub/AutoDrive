package com.autodrive.launcher

import java.net.URI
import java.util.Locale

internal sealed interface VideoSource {
    data class YouTube(val id: String) : VideoSource
    data class Iptv(val url: String) : VideoSource

    companion object {
        private val idPattern = Regex("[A-Za-z0-9_-]{11}")
        private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")
        private val videoPaths = setOf("shorts", "live", "embed")

        fun youtube(input: String): YouTube? {
            val text = input.trim()
            if (idPattern.matches(text)) return YouTube(text)
            val uri = https(text) ?: return null
            if (uri.port != -1 && uri.port != 443) return null
            val segments = uri.path.trim('/').split('/')
            val host = uri.host.lowercase(Locale.ROOT)
            val id = when {
                host == "youtu.be" && segments.size == 1 -> segments[0]
                host in youtubeHosts && uri.path == "/watch" -> uri.rawQuery?.split('&')
                    ?.firstOrNull { it.startsWith("v=") }?.substring(2)
                host in youtubeHosts && segments.size == 2 && segments[0] in videoPaths -> segments[1]
                else -> null
            }
            return id?.takeIf(idPattern::matches)?.let(::YouTube)
        }

        fun youtubeShare(input: String): YouTube? {
            if (input.length > 8192) return null
            youtube(input)?.let { return it }
            return input.split(Regex("\\s+"))
                .filter { it.startsWith("https://") }
                .firstNotNullOfOrNull(::youtube)
        }

        fun iptv(input: String): Iptv? {
            val text = input.trim()
            val uri = https(text) ?: return null
            if (uri.path.isNullOrBlank() || uri.path == "/" || uri.fragment != null) return null
            return Iptv(text)
        }

        private fun https(text: String): URI? {
            if (text.length > 2048) return null
            return runCatching { URI(text) }.getOrNull()?.takeIf {
                it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() && it.userInfo == null
            }
        }
    }
}
