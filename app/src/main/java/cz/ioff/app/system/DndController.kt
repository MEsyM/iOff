package cz.ioff.app.system

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences

interface DndController {
    fun hasAccess(): Boolean
    suspend fun enterFocusMode()
    suspend fun exitFocusMode()
    suspend fun restoreFocusModeIfRequired()
    suspend fun restoreIfOwned()
}

class AndroidDndController(context: Context, private val prefs: SharedPreferences) : DndController {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun hasAccess() = manager?.isNotificationPolicyAccessGranted == true

    override suspend fun enterFocusMode() {
        val nm = manager ?: return
        if (!nm.isNotificationPolicyAccessGranted || prefs.getBoolean(KEY_OWNED, false)) return
        prefs.edit()
            .putInt(KEY_PREVIOUS, nm.currentInterruptionFilter)
            .putBoolean(KEY_OWNED, true)
            .commit()
        runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
            .onFailure { prefs.edit().putBoolean(KEY_OWNED, false).apply() }
    }

    override suspend fun exitFocusMode() = restoreIfOwned()

    override suspend fun restoreFocusModeIfRequired() {
        val nm = manager ?: return
        if (prefs.getBoolean(KEY_OWNED, false) && nm.isNotificationPolicyAccessGranted) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
        }
    }

    override suspend fun restoreIfOwned() {
        val nm = manager ?: return
        if (!prefs.getBoolean(KEY_OWNED, false)) return
        if (nm.isNotificationPolicyAccessGranted) {
            runCatching {
                nm.setInterruptionFilter(prefs.getInt(KEY_PREVIOUS, NotificationManager.INTERRUPTION_FILTER_ALL))
            }
        }
        prefs.edit().putBoolean(KEY_OWNED, false).apply()
    }

    companion object {
        private const val KEY_OWNED = "focus_dnd_owned"
        private const val KEY_PREVIOUS = "focus_dnd_previous"
    }
}
