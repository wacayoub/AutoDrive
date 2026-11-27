package com.incardrive.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * The last six things played, with their artwork, for the covers of the Studio media card: a tap opens the app that
 * played it. Kept in the app's own files, nothing leaves the head unit.
 */
internal class RecentMedia(context: Context) {
    data class Item(val source: String, val title: String, val file: String)

    private val folder = File(context.filesDir, "recent_media").apply { mkdirs() }
    private val prefs = context.getSharedPreferences(HomeActivity.PREFS, Context.MODE_PRIVATE)
    var items: List<Item> = decode(prefs.getString(KEY, null))
        private set
    private val covers = HashMap<String, Bitmap?>()

    /** Remembers [title] from [source] with its [artwork]; true when the list changed. */
    fun add(source: String, title: String, artwork: Bitmap): Boolean {
        val existing = items.firstOrNull { it.source == source && it.title == title }
        if (existing != null && items.first() == existing) return false
        val file = existing?.file ?: "cover_${System.currentTimeMillis()}.png"
        if (existing == null) runCatching {
            val small = Bitmap.createScaledBitmap(artwork, SIZE, SIZE, true)
            File(folder, file).outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 90, it) }
            covers[file] = small
        }.onFailure { return false }
        val (kept, dropped) = merge(items, Item(source, title, file))
        dropped.forEach { File(folder, it.file).delete(); covers.remove(it.file) }
        items = kept
        prefs.edit().putString(KEY, encode(kept)).apply()
        return true
    }

    fun cover(item: Item): Bitmap? = covers.getOrPut(item.file) { BitmapFactory.decodeFile(File(folder, item.file).path) }

    companion object {
        const val KEY = "recent_media"
        const val MAX = 6
        private const val SIZE = 160

        /** [item] first, without its older copy, at most [MAX]; and the ones that fell off the end. */
        fun merge(list: List<Item>, item: Item): Pair<List<Item>, List<Item>> {
            val all = listOf(item) + list.filterNot { it.source == item.source && it.title == item.title }
            return all.take(MAX) to all.drop(MAX)
        }

        fun encode(list: List<Item>) = list.joinToString("\n") { listOf(it.source, it.file, it.title.replace('\n', ' ').replace('\t', ' ')).joinToString("\t") }

        fun decode(text: String?): List<Item> = text.orEmpty().split('\n').mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size == 3 && parts.all { it.isNotBlank() }) Item(parts[0], parts[2], parts[1]) else null
        }
    }
}
