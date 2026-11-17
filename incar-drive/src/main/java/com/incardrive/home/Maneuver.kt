package com.incardrive.home

/** The shape of the next turn, as far as the navigation app's instruction tells it. */
internal enum class Turn { STRAIGHT, LEFT, RIGHT, ROUNDABOUT, UTURN, ARRIVE }

/**
 * What the « Route » speed card draws on the road for the next instruction of Google Maps or Waze: the turn, the
 * exit of a roundabout when the instruction counts it, the street to take, and how far the turn is.
 *
 * It is read from the instruction's words, in French, English and Arabic; an instruction that says nothing
 * recognisable is drawn as going straight on.
 */
internal data class Maneuver(val turn: Turn, val exit: Int? = null, val street: String? = null, val meters: Int? = null) {
    companion object {
        private val arrive = Regex("""(?i)arriv|destination|الوصول|وجهتك""")
        private val uturn = Regex("""(?i)demi[- ]tour|u[- ]turn|faites? demi|استدر للخلف|انعطف للخلف""")
        private val roundabout = Regex("""(?i)rond[- ]point|giratoire|roundabout|الدوار|دوّار|دوار""")
        private val left = Regex("""(?i)\bgauche\b|\bleft\b|يسار""")
        private val right = Regex("""(?i)\bdroite\b|\bright\b|يمين""")
        private val exitNumber = Regex("""(?i)(\d)\s*(?:re|er|e|ème|eme|st|nd|rd|th)?\s*(?:sortie|exit)|(?:sortie|exit)\s*(\d)""")
        private val exitWords = listOf(
            Regex("""(?i)premi[èe]re|first|الأول""") to 1, Regex("""(?i)deuxi[èe]me|seconde|second|الثاني""") to 2,
            Regex("""(?i)troisi[èe]me|third|الثالث""") to 3, Regex("""(?i)quatri[èe]me|fourth|الرابع""") to 4,
        )
        private val onto = Regex("""(?i)(?:\bsur\b|\bonto\b|\bon\b|\bvers\b|\btoward(?:s)?\b|\bdirection\b|إلى|على)\s+(.{2,40})$""")
        private val distance = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(km|m)\b""")

        private val notAStreet = Regex("""(?i)rond[- ]point|roundabout|sortie|\bexit\b|gauche|droite|\bleft\b|\bright\b|destination""")

        fun of(instruction: String?, step: String?): Maneuver {
            val text = instruction?.trim().orEmpty()
            val turn = when {
                text.isEmpty() -> Turn.STRAIGHT
                arrive.containsMatchIn(text) -> Turn.ARRIVE
                uturn.containsMatchIn(text) -> Turn.UTURN
                roundabout.containsMatchIn(text) -> Turn.ROUNDABOUT
                // « à gauche puis à droite »: the first one named is the next turn.
                left.containsMatchIn(text) && right.containsMatchIn(text) ->
                    if (left.find(text)!!.range.first < right.find(text)!!.range.first) Turn.LEFT else Turn.RIGHT
                left.containsMatchIn(text) -> Turn.LEFT
                right.containsMatchIn(text) -> Turn.RIGHT
                else -> Turn.STRAIGHT
            }
            val exit = if (turn != Turn.ROUNDABOUT) null else
                exitNumber.find(text)?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull() }
                    ?: exitWords.firstOrNull { it.first.containsMatchIn(text) }?.second
            val street = onto.find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',')
                // A name, not a leftover of the instruction (« la gauche », « 2e sortie »…).
                ?.takeUnless { distance.containsMatchIn(it) || it.first().isDigit() || notAStreet.containsMatchIn(it) }
            return Maneuver(turn, exit, street, meters(step))
        }

        /** « 110 m » or « 1,2 km » in metres. */
        fun meters(step: String?): Int? {
            val match = distance.find(step ?: return null) ?: return null
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            return (if (match.groupValues[2].equals("km", ignoreCase = true)) value * 1000 else value).toInt()
        }
    }
}
