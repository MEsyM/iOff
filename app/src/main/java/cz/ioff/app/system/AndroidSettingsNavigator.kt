package cz.ioff.app.system

import android.content.Context
import android.content.Intent
import android.provider.Settings

interface SettingsNavigator {
    fun openDoNotDisturbAccess()
    fun openAccessibilityAccess()
    fun openUsageAccess()
    fun openNotificationAccess()
}

class AndroidSettingsNavigator(private val context: Context) : SettingsNavigator {
    override fun openDoNotDisturbAccess() = open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    override fun openAccessibilityAccess() = open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    override fun openUsageAccess() = open(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    override fun openNotificationAccess() = open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    private fun open(action: String) {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
