package cz.ioff.app.data

import android.content.SharedPreferences
import cz.ioff.app.DailyLife
import cz.ioff.app.DailyLifeStore
import cz.ioff.app.IdeaCodec
import cz.ioff.app.IdeaState
import cz.ioff.app.MetricKeys
import cz.ioff.app.ParkedIdea

data class ShutdownEntry(
    val finished: String = "",
    val distraction: String = "",
    val tomorrow: String = "",
    val completed: Boolean = false
)

class IOffRepository(val preferences: SharedPreferences) {
    private val dailyLife = DailyLifeStore.from(preferences)

    fun day(): Int = preferences.getInt(KEY_DAY, 1).coerceIn(1, 7)
    fun oneThing(day: Int = day()): String = preferences.getString("one_thing_$day", "") ?: ""
    fun saveOneThing(value: String, day: Int = day()) {
        preferences.edit().putString("one_thing_$day", value.take(200)).apply()
    }

    fun metric(name: String, day: Int = day()): Int =
        preferences.getInt(MetricKeys.experiment(day, name), 0)

    fun total(name: String): Int = (1..7).sumOf { metric(name, it) }

    fun dailyLife(day: Int = day()): DailyLife = dailyLife.load(day)
    fun saveDailyLife(value: DailyLife, day: Int = day()) = dailyLife.save(day, value)
    fun addSport(activity: String, minutes: Int, day: Int = day()) = dailyLife.addSport(day, activity, minutes)
    fun deleteSport(id: Long, day: Int = day()) = dailyLife.deleteSport(day, id)
    fun addSex(note: String, day: Int = day()) = dailyLife.addSex(day, note)
    fun deleteSex(id: Long, day: Int = day()) = dailyLife.deleteSex(day, id)

    fun ideas(): List<ParkedIdea> = IdeaCodec.parse(preferences.getString(KEY_IDEAS, "") ?: "")
    fun parkIdea(text: String) = updateIdeas(IdeaCodec.prepend(rawIdeas(), System.currentTimeMillis(), text))
    fun updateIdea(idea: ParkedIdea, text: String) = updateIdeas(IdeaCodec.update(rawIdeas(), idea.timestamp, text))
    fun setIdeaState(idea: ParkedIdea, state: IdeaState) =
        updateIdeas(IdeaCodec.setState(rawIdeas(), idea.timestamp, state))
    fun deleteIdea(idea: ParkedIdea) = updateIdeas(IdeaCodec.delete(rawIdeas(), idea.timestamp))

    fun shutdown(day: Int = day()) = ShutdownEntry(
        finished = preferences.getString("shutdown_finished_$day", "") ?: "",
        distraction = preferences.getString("shutdown_stole_$day", "") ?: "",
        tomorrow = preferences.getString("shutdown_tomorrow_$day", "") ?: "",
        completed = preferences.getBoolean("shutdown_done_$day", false)
    )

    fun saveShutdown(entry: ShutdownEntry, day: Int = day()) {
        val nextDay = (day + 1).coerceAtMost(7)
        preferences.edit()
            .putString("shutdown_finished_$day", entry.finished.trim())
            .putString("shutdown_stole_$day", entry.distraction.trim())
            .putString("shutdown_tomorrow_$day", entry.tomorrow.trim())
            .putBoolean("shutdown_done_$day", true)
            .putString("one_thing_$nextDay", entry.tomorrow.trim())
            .apply()
    }

    fun morningShieldEnabled(): Boolean = preferences.getBoolean(KEY_MORNING_SHIELD, true)
    fun setMorningShieldEnabled(enabled: Boolean) =
        preferences.edit().putBoolean(KEY_MORNING_SHIELD, enabled).apply()

    fun shieldEnabled(): Boolean = preferences.getBoolean(KEY_SHIELD_ENABLED, true)
    fun setShieldEnabled(enabled: Boolean) = preferences.edit().putBoolean(KEY_SHIELD_ENABLED, enabled).apply()

    fun resetAll() = preferences.edit().clear().putInt(KEY_DAY, 1).apply()

    private fun rawIdeas() = preferences.getString(KEY_IDEAS, "") ?: ""
    private fun updateIdeas(value: String) = preferences.edit().putString(KEY_IDEAS, value).apply()

    companion object {
        const val PREFERENCES_NAME = "ioff"
        private const val KEY_DAY = "day"
        private const val KEY_IDEAS = "ideas"
        private const val KEY_MORNING_SHIELD = "morning_shield"
        private const val KEY_SHIELD_ENABLED = "shield_enabled"
    }
}
