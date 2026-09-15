package cz.ioff.app.data

import android.content.SharedPreferences
import cz.ioff.app.domain.focus.ActiveFocus
import cz.ioff.app.domain.focus.FocusSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SharedPreferencesFocusRepository(private val prefs: SharedPreferences) : FocusRepository {
    private val activeFlow = MutableStateFlow(readActive())
    private val sessionsFlow = MutableStateFlow(readSessions())

    override fun observeActiveFocus(): Flow<ActiveFocus?> = activeFlow

    override suspend fun getActiveFocus(): ActiveFocus? = readActive().also {
        activeFlow.value = it
    }

    override suspend fun startFocus(
        goal: String,
        plannedMinutes: Int,
        experimentDay: Int,
        startedAt: Long,
        endsAt: Long
    ): ActiveFocus {
        readActive()?.let { return it }
        val active = ActiveFocus(
            sessionId = UUID.randomUUID().toString(),
            startedAt = startedAt,
            endsAt = endsAt,
            experimentDay = experimentDay,
            plannedMinutes = plannedMinutes,
            goal = goal.trim(),
            urges = 0
        )
        check(prefs.edit().putString(KEY_ACTIVE, activeJson(active).toString()).commit())
        activeFlow.value = active
        return active
    }

    override suspend fun incrementUrge() {
        val active = readActive() ?: return
        val updated = active.copy(urges = active.urges + 1)
        check(prefs.edit().putString(KEY_ACTIVE, activeJson(updated).toString()).commit())
        activeFlow.value = updated
    }

    override suspend fun finishActiveFocus(endedAt: Long, interrupted: Boolean): FocusSession {
        val active = readActive() ?: error("No active focus session")
        val actualMinutes = ((endedAt - active.startedAt).coerceAtLeast(0L) / 60_000L)
            .toInt()
            .coerceAtMost(active.plannedMinutes)
        val session = FocusSession(
            id = active.sessionId,
            startedAt = active.startedAt,
            endedAt = endedAt,
            experimentDay = active.experimentDay,
            plannedMinutes = active.plannedMinutes,
            actualMinutes = actualMinutes,
            urges = active.urges,
            focusScore = null,
            goal = active.goal,
            output = "",
            interrupted = interrupted
        )
        val sessions = readSessions() + session
        writeSessions(sessions)
        check(prefs.edit().remove(KEY_ACTIVE).commit())
        activeFlow.value = null
        sessionsFlow.value = sessions
        return session
    }

    override suspend fun completeSession(sessionId: String, focusScore: Int, output: String) {
        val sessions = readSessions().map { session ->
            if (session.id == sessionId) {
                session.copy(focusScore = focusScore.coerceIn(1, 10), output = output.trim())
            } else session
        }
        writeSessions(sessions)
        sessionsFlow.value = sessions
    }

    override fun observeSessions(): Flow<List<FocusSession>> = sessionsFlow

    override suspend fun getSessionsForDay(experimentDay: Int): List<FocusSession> =
        readSessions().filter { it.experimentDay == experimentDay }

    override suspend fun clearCurrentFocus() {
        check(prefs.edit().remove(KEY_ACTIVE).commit())
        activeFlow.value = null
    }

    override suspend fun resetSessions() {
        check(prefs.edit().remove(KEY_ACTIVE).remove(KEY_SESSIONS).commit())
        activeFlow.value = null
        sessionsFlow.value = emptyList()
    }

    private fun readActive(): ActiveFocus? {
        return try {
            val raw = prefs.getString(KEY_ACTIVE, null) ?: return null
            val o = JSONObject(raw)
            ActiveFocus(
                sessionId = o.getString("id"),
                startedAt = o.getLong("start"),
                endsAt = o.getLong("end"),
                experimentDay = o.getInt("day"),
                plannedMinutes = o.getInt("planned"),
                goal = o.getString("goal"),
                urges = o.optInt("urges")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun activeJson(active: ActiveFocus): JSONObject = JSONObject()
        .put("id", active.sessionId)
        .put("start", active.startedAt)
        .put("end", active.endsAt)
        .put("day", active.experimentDay)
        .put("planned", active.plannedMinutes)
        .put("goal", active.goal)
        .put("urges", active.urges)

    private fun readSessions(): List<FocusSession> {
        return try {
            val array = JSONArray(prefs.getString(KEY_SESSIONS, "[]") ?: "[]")
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val end = o.optLong("end", 0L)
                    val score = o.optInt("score", 0)
                    add(
                        FocusSession(
                            id = o.getString("id"),
                            startedAt = o.getLong("start"),
                            endedAt = end.takeIf { it > 0L },
                            experimentDay = o.getInt("day"),
                            plannedMinutes = o.getInt("planned"),
                            actualMinutes = o.getInt("actual"),
                            urges = o.getInt("urges"),
                            focusScore = score.takeIf { it > 0 },
                            goal = o.getString("goal"),
                            output = o.optString("output"),
                            interrupted = o.optBoolean("interrupted")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeSessions(sessions: List<FocusSession>) {
        val array = JSONArray()
        sessions.forEach { session ->
            array.put(
                JSONObject()
                    .put("id", session.id)
                    .put("start", session.startedAt)
                    .put("end", session.endedAt ?: 0L)
                    .put("day", session.experimentDay)
                    .put("planned", session.plannedMinutes)
                    .put("actual", session.actualMinutes)
                    .put("urges", session.urges)
                    .put("score", session.focusScore ?: 0)
                    .put("goal", session.goal)
                    .put("output", session.output)
                    .put("interrupted", session.interrupted)
            )
        }
        check(prefs.edit().putString(KEY_SESSIONS, array.toString()).commit())
    }

    companion object {
        private const val KEY_ACTIVE = "focus_active_v2"
        private const val KEY_SESSIONS = "focus_sessions_v2"
    }
}
