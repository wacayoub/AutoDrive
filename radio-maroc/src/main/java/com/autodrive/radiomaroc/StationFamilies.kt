package com.autodrive.radiomaroc

import com.autodrive.launcher.RadioCatalogue
import com.autodrive.launcher.RadioStation

internal object StationFamilies {
    private const val HIT_LOGO = "https://www.hitradio.ma/logo/small-logo-hitradio.webp"
    private const val MEDI_LOGO = "https://www.medi1.com/assets/imgs/medi1_logo_new.png"
    private const val MEDI_CDN = "https://cdn.live.easybroadcast.io"
    private const val MEDI_ART = "https://bridge.medi1.com/images"

    // The Moroccan web player publishes these mounts in its station catalogue.
    val hit = listOf(
        hit("M’Gharba", "mgharba", "Musique marocaine"),
        hit("Urban", "rnb", "Hip-hop et R&B"),
        hit("TikTok", "buzz", "Hits viraux"),
        hit("Party", "dancefloor", "Dance"),
        hit("Classic", "gold", "Classiques"),
        hit("Way Way", "wayway", "Chaâbi"),
        hit("Marruecos España", "marruecosespana", "Maroc et Espagne"),
    )

    private fun hit(name: String, mount: String, genre: String) = RadioStation(
        "HIT RADIO · 100% $name", "https://$mount.ice.infomaniak.ch/$mount-128.mp3",
        logoUrl = HIT_LOGO, tags = setOf(genre),
    )

    // The Maghreb and Afrique live players and the official Medi 1 web radio collection.
    val medi = listOf(
        mediLive("Medi 1 Radio", "83_medi1radio-maghreb_8s9i4bn", "maghreb", "Maghreb · Infos et musique"),
        mediLive("Medi 1 · Afrique", "83_medi1radio-afrique_mh786kz", "afrique", "Afrique · Infos et musique"),
        medi("Acoustique", "Acoustic", "acoustic"),
        medi("Andalouse", "Andalouse", "andalouse"),
        medi("Chanson française", "Francaise", null),
        medi("Classique", "Classique", "classique"),
        medi("DJ", "Dj", "dj"),
        medi("Hits", "Hits", "hits"),
        medi("Jazz", "Jazz", "jazz"),
        medi("Latino", "Latino", "latino"),
        medi("Lounge", "Lounge", "lounge"),
        medi("Nayda", "Nayda", "nayda"),
        medi("Soufi", "Soufi", "soufi"),
        medi("Tarab", "Tarab", "tarab"),
    )

    private fun medi(name: String, mount: String, art: String?) = RadioStation(
        "Medi 1 · $name", "$MEDI_CDN/medi1radio/$mount",
        logoUrl = art?.let { "$MEDI_ART/webradios/new/$it.jpg?123" } ?: MEDI_LOGO,
        tags = setOf("Webradio Medi 1"),
    )

    private fun mediLive(name: String, mount: String, region: String, detail: String) = RadioStation(
        name, "$MEDI_CDN/live/$mount/playlist.m3u8",
        logoUrl = "$MEDI_ART/live/btn_live_${region}_s.png", tags = setOf(detail), isHls = true,
    )

    fun directoryKey(station: RadioStation): String {
        val key = RadioCatalogue.searchKey(station.name)
        return when {
            key == "medi1maghreb" -> "medi1radio"
            key.startsWith("medi1radio") && key != "medi1radio" -> "medi1" + key.removePrefix("medi1radio")
            else -> key
        }
    }
}
