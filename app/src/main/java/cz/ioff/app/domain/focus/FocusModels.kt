package cz.ioff.app.domain.focus

data class FocusSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val experimentDay: Int,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val urges: Int,
    val focusScore: Int?,
    val goal: String,
    val output: String,
    val interrupted: Boolean
)

data class ActiveFocus(
    val sessionId: String,
    val startedAt: Long,
    val endsAt: Long,
    val experimentDay: Int,
    val plannedMinutes: Int,
    val goal: String,
    val urges: Int
)

enum class FocusPhase { SETUP, ACTIVE, COMPLETING }

sealed interface FocusRecoveryResult {
    data object Nothing : FocusRecoveryResult
    data class Resume(val focus: ActiveFocus) : FocusRecoveryResult
    data class CompleteExpired(val focus: ActiveFocus) : FocusRecoveryResult
}
