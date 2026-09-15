package cz.ioff.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class BootReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent?){
  if(intent?.action!=Intent.ACTION_BOOT_COMPLETED&&intent?.action!="android.intent.action.LOCKED_BOOT_COMPLETED")return
  val p=context.getSharedPreferences("ioff",0)
  val active=runCatching{p.getString("focus_active_v2",null)?.let{JSONObject(it)}}.getOrNull()?:return
  if(active.optLong("end")<=System.currentTimeMillis())return
  val nm=context.getSystemService(NotificationManager::class.java)
  if(nm?.isNotificationPolicyAccessGranted==true){
   runCatching{nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)}
   p.edit().putBoolean("focus_dnd_owned",true).apply()
  }
 }
}
