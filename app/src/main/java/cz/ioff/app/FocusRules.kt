package cz.ioff.app

object FocusRules {
    fun normalizedGoal(goal: String): String = goal.trim()
    fun canStart(goal: String): Boolean = normalizedGoal(goal).isNotEmpty()
    fun durationMinutes(value: Int): Int = value.coerceIn(1, 24 * 60)
}
