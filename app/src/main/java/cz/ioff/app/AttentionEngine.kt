package cz.ioff.app

import cz.ioff.app.domain.focus.FocusSession
import kotlin.math.roundToInt

data class AttentionSnapshot(
    val deepWorkMinutes: Int,
    val focusAverage: Double,
    val urges: Int,
    val shieldInterventions: Int,
    val bypasses: Int,
    val distractingMinutes: Int
)

object AttentionEngine {
    fun score(x: AttentionSnapshot): Int {
        val deep = (x.deepWorkMinutes / 90.0 * 35).coerceIn(0.0, 35.0)
        val focus = (x.focusAverage / 10.0 * 30).coerceIn(0.0, 30.0)
        val protection = (15 - x.bypasses * 5).coerceAtLeast(0)
        val distraction = (20 - x.distractingMinutes / 3.0).coerceAtLeast(0.0)
        val urgePenalty = (x.urges - 3).coerceAtLeast(0) * 2
        return (deep + focus + protection + distraction - urgePenalty).roundToInt().coerceIn(0, 100)
    }

    fun reclaimedMinutes(currentDistracting: Int, baselineDistracting: Int): Int =
        (baselineDistracting - currentDistracting).coerceAtLeast(0)

    fun insight(sessions: List<FocusSession>): String? {
        val rated = sessions.filter { (it.focusScore ?: 0) > 0 && it.actualMinutes >= 20 }
        if (rated.size < 4) return null
        val short = rated.filter { it.plannedMinutes <= 60 }
        val long = rated.filter { it.plannedMinutes >= 90 }
        if (short.size >= 2 && long.size >= 2) {
            val a = short.mapNotNull { it.focusScore }.average()
            val b = long.mapNotNull { it.focusScore }.average()
            if (kotlin.math.abs(a - b) >= .5) {
                val winner = if (b > a) "90+ minute" else "30–60 minute"
                return "$winner sessions currently produce better focus for you."
            }
        }
        return null
    }
}
