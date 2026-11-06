package com.autodrive.launcher

internal object RadioQueue {
    fun merge(catalogue: List<RadioStation>, saved: List<RadioStation>): List<RadioStation> {
        val savedByUrl = saved.associateBy { it.url }
        return (catalogue + saved).distinctBy { it.url }.map { station ->
            val personal = savedByUrl[station.url]
            station.copy(name = personal?.name ?: station.name, favorite = personal?.favorite ?: station.favorite,
                logoUrl = personal?.logoUrl ?: station.logoUrl)
        }
    }
    fun active(stations: List<RadioStation>, favoritesOnly: Boolean, category: RadioCategory = RadioCategory.ALL) =
        stations.filter { (!favoritesOnly || it.favorite) && RadioCatalogue.matches(it, category) }

    fun neighbor(queue: List<RadioStation>, currentUrl: String?, direction: Int): RadioStation? {
        if (queue.size < 2) return null
        val index = queue.indexOfFirst { it.url == currentUrl }
        if (index < 0) return if (direction < 0) queue.last() else queue.first()
        return queue[Math.floorMod(index + if (direction < 0) -1 else 1, queue.size)]
    }
}
