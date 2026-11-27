package com.incardrive.home

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentMediaTest {
    private fun item(n: Int, source: String = "app") = RecentMedia.Item(source, "Titre $n", "cover_$n.png")

    @Test
    fun theNewestComesFirstAndOnlySixAreKept() {
        var list = emptyList<RecentMedia.Item>()
        val dropped = ArrayList<RecentMedia.Item>()
        for (n in 1..8) RecentMedia.merge(list, item(n)).let { list = it.first; dropped += it.second }
        assertEquals((8 downTo 3).map { "Titre $it" }, list.map { it.title })
        assertEquals(listOf("Titre 1", "Titre 2"), dropped.map { it.title })
    }

    @Test
    fun playingAgainMovesItToTheFront() {
        val list = listOf(item(3), item(2), item(1))
        val (kept, dropped) = RecentMedia.merge(list, item(1))
        assertEquals(listOf("Titre 1", "Titre 3", "Titre 2"), kept.map { it.title })
        assertEquals(emptyList<RecentMedia.Item>(), dropped)
    }

    @Test
    fun theListSurvivesBeingSaved() {
        val list = listOf(item(1, "com.google.android.youtube"), RecentMedia.Item("radio", "Tab\tet\nligne", "c.png"))
        val back = RecentMedia.decode(RecentMedia.encode(list))
        assertEquals(listOf("Titre 1", "Tab et ligne"), back.map { it.title })
        assertEquals("com.google.android.youtube", back.first().source)
        assertEquals(emptyList<RecentMedia.Item>(), RecentMedia.decode("abîmé"))
    }
}
