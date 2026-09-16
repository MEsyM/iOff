package cz.ioff.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

object UsageMetrics {
 private val distracting=setOf("com.instagram.android","com.facebook.katana","com.zhiliaoapp.musically","com.google.android.youtube","com.twitter.android","com.reddit.frontpage","com.snapchat.android")
 fun hasAccess(c:Context):Boolean { val a=c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager;return a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.packageName)==AppOpsManager.MODE_ALLOWED }
 fun collect(c:Context,p:android.content.SharedPreferences,day:Int){if(!hasAccess(c))return;val now=System.currentTimeMillis();val cal=Calendar.getInstance().apply{timeInMillis=now;set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)};val start=cal.timeInMillis;val u=c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager;val events=u.queryEvents(start,now);val e=UsageEvents.Event();var opens=0;val foreground=mutableMapOf<String,Long>();var distractingMs=0L
  while(events.hasNextEvent()){events.getNextEvent(e);if(e.packageName==c.packageName)continue;when(e.eventType){UsageEvents.Event.ACTIVITY_RESUMED->{opens++;if(e.packageName in distracting)foreground[e.packageName]=e.timeStamp};UsageEvents.Event.ACTIVITY_PAUSED,UsageEvents.Event.ACTIVITY_STOPPED->{foreground.remove(e.packageName)?.let{if(e.packageName in distracting)distractingMs+=(e.timeStamp-it).coerceAtLeast(0)}}}}
  foreground.forEach{(pkg,t)->if(pkg in distracting)distractingMs+=(now-t).coerceAtLeast(0)}
  p.edit().putInt(MetricKeys.experiment(day,"opens"),opens).putInt(MetricKeys.experiment(day,"distracting"),(distractingMs/60000).toInt()).putLong("usage_last_collected",now).apply()
 }
}
