package cz.ioff.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cz.ioff.app.data.ProtectionStore
import cz.ioff.app.data.ProtectionType

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED && intent?.action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        val preferences = context.getSharedPreferences("ioff", 0)
        val session = ProtectionStore(preferences).activeSession(System.currentTimeMillis()) ?: return
        if (session.type != ProtectionType.FOCUS || !session.active) return

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager?.isNotificationPolicyAccessGranted == true) {
            runCatching { notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
            preferences.edit().putBoolean("focus_dnd_owned", true).apply()
        }
    }
}
