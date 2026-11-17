package com.incardrive.home

/** An alert about the road ahead, as the navigation app words it in a notification. */
internal data class RoadAlert(val text: String, val serious: Boolean)

/**
 * Road alerts taken from what Google Maps or Waze write in their own notifications: an accident, a slowdown, road
 * works, a closed road, a hazard. Only their words are used; nothing is looked up elsewhere. A notification that
 * mentions none of these is not an alert.
 */
internal object RoadAlerts {
    // Accidents, closed roads and hazards are shown in red; slowdowns, works and the rest in orange.
    private val serious = Regex(
        """(?i)accident|collision|crash|route ferm[ée]e|road closed|voie ferm[ée]e|lane closed|danger|hazard|""" +
            """v[ée]hicule en panne|stalled vehicle|obstacle|objet sur la (?:route|chauss[ée]e)|object on (?:the )?road|""" +
            """حادث|طريق مغلق|خطر""")
    private val other = Regex(
        """(?i)ralentissement|slowdown|slow traffic|embouteillage|bouchon|traffic jam|congestion|trafic dense|""" +
            """heavy traffic|circulation dense|travaux|road ?works|construction|retard de|delay of|min(?:ute)?s? de retard|""" +
            """contr[ôo]le de vitesse|speed trap|police|ازدحام|أشغال|تأخير""")

    /** The first line among [texts] that reads as an alert, shortened to fit the home screen; null when none does. */
    fun find(texts: List<String>): RoadAlert? {
        for (line in texts) {
            val isSerious = serious.containsMatchIn(line)
            if (!isSerious && !other.containsMatchIn(line)) continue
            val clean = line.replace(Regex("""\s+"""), " ").trim()
            return RoadAlert(if (clean.length > 46) clean.take(45).trimEnd() + "…" else clean, isSerious)
        }
        return null
    }
}
