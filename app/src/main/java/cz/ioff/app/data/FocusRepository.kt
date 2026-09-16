package cz.ioff.app.data

import cz.ioff.app.domain.focus.ActiveFocus
import cz.ioff.app.domain.focus.FocusSession
import kotlinx.coroutines.flow.Flow

interface FocusRepository {
    fun observeActiveFocus(): Flow<ActiveFocus?>
    suspend fun getActiveFocus(): ActiveFocus?
    suspend fun startFocus(goal: String, plannedMinutes: Int, experimentDay: Int, startedAt: Long, endsAt: Long): ActiveFocus
    suspend fun incrementUrge()
    suspend fun finishActiveFocus(endedAt: Long, interrupted: Boolean): FocusSession
    suspend fun completeSession(sessionId: String, focusScore: Int, output: String)
    fun observeSessions(): Flow<List<FocusSession>>
    suspend fun getSessionsForDay(experimentDay: Int): List<FocusSession>
    suspend fun clearCurrentFocus()
    suspend fun resetSessions()
}
