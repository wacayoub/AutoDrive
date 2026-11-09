package com.autodrive.launcher

import java.net.URI

internal data class RadioStation(
    val name: String,
    val url: String,
    val favorite: Boolean = false,
    val logoUrl: String? = null,
    val tags: Set<String> = emptySet(),
    val countryCode: String? = null,
    val isHls: Boolean = false,
) {
    override fun toString() = name
    companion object {
        fun defaultLogo(url: String): String? = if (url == "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3")
            "https://hitradio.ma/small-logo-hitradio.png" else null
        fun validUrl(input: String): String? = runCatching { URI(input.trim()) }.getOrNull()?.takeIf {
            input.length <= 2048 && it.scheme.equals("https", true) && !it.host.isNullOrBlank() &&
                it.userInfo == null && it.fragment == null && (it.port == -1 || it.port in 1..65535)
        }?.toASCIIString()
    }
}
