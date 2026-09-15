package cz.ioff.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent?){if(intent?.action!=Intent.ACTION_BOOT_COMPLETED&&intent?.action!="android.intent.action.LOCKED_BOOT_COMPLETED")return;val p=context.getSharedPreferences("ioff",0);if(!p.getBoolean("focus_active",false))return;val now=System.currentTimeMillis();if(p.getLong("focus_end",0)<=now){p.edit().putBoolean("focus_expired",true).apply();return};val nm=context.getSystemService(NotificationManager::class.java);if(nm?.isNotificationPolicyAccessGranted==true){runCatching{nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)};p.edit().putBoolean("dnd_changed",true).apply()}}
}
