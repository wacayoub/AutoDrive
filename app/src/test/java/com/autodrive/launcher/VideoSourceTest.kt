package com.autodrive.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSourceTest {
    private val id = "M7lc1UVf-VE"

    @Test fun acceptsOfficialYouTubeShareLinks() {
        listOf(id, "https://youtu.be/$id?si=share", "https://www.youtube.com/watch?si=share&v=$id",
            "https://m.youtube.com/shorts/$id", "https://www.youtube.com/live/$id").forEach {
            assertEquals(VideoSource.YouTube(id), VideoSource.youtube(it))
        }
    }

    @Test fun rejectsForeignAndMalformedYouTubeUrls() {
        listOf("https://youtube.com.example.org/watch?v=$id", "https://youtube.com@other.example/watch?v=$id",
            "https://www.youtube.com:444/watch?v=$id", "https://youtu.be/$id/other",
            "https://www.youtube.com/watch?v=<script>", "http://youtu.be/$id").forEach {
            assertNull(VideoSource.youtube(it))
        }
    }

    @Test fun acceptsSharedTitlesWithAnOfficialVideoLink() {
        assertEquals(VideoSource.YouTube(id), VideoSource.youtubeShare("Mon titre\nhttps://youtu.be/$id?si=share"))
        assertEquals(VideoSource.YouTube(id), VideoSource.youtubeShare("https://www.youtube.com/watch?v=$id"))
        assertNull(VideoSource.youtubeShare("https://youtube.com.example.org/watch?v=$id"))
        assertNull(VideoSource.youtubeShare("https://youtube.com@$id.example/watch?v=$id"))
        assertNull(VideoSource.youtubeShare("Texte sans vidéo"))
        assertNull(VideoSource.youtubeShare("x".repeat(8193)))
    }

    @Test fun acceptsDirectSecureStreamLinks() {
        val url = "https://media.example.org/live/playlist.m3u8?token=test"
        assertEquals(VideoSource.Iptv(url), VideoSource.iptv(" $url "))
    }

    @Test fun rejectsUnsafeStreamLinks() {
        listOf("http://media.example.org/live.m3u8", "file:///live.m3u8",
            "https://user:password@media.example.org/live.m3u8", "https://media.example.org/",
            "https://media.example.org/live.m3u8#segment").forEach {
            assertNull(VideoSource.iptv(it))
        }
    }
}
