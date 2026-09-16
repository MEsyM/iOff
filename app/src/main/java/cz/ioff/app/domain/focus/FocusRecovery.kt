package cz.ioff.app.domain.focus

import cz.ioff.app.data.FocusRepository

interface FocusRecovery {
    suspend fun recover(now: Long): FocusRecoveryResult
}

class DefaultFocusRecovery(private val repository: FocusRepository) : FocusRecovery {
    override suspend fun recover(now: Long): FocusRecoveryResult {
        val active = repository.getActiveFocus() ?: return FocusRecoveryResult.Nothing
        return if (active.endsAt > now) FocusRecoveryResult.Resume(active)
        else FocusRecoveryResult.CompleteExpired(active)
    }
}
