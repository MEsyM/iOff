package cz.ioff.app;
import android.service.notification.*;import android.content.*;import java.text.*;import java.util.*;
public class NotificationCaptureService extends NotificationListenerService{
 public void onNotificationPosted(StatusBarNotification s){if(s==null||s.getPackageName()==null)return;SharedPreferences p=getSharedPreferences("ioff",0);Set<String>d=new HashSet<>(Arrays.asList("com.instagram.android","com.facebook.katana","com.zhiliaoapp.musically","com.google.android.youtube","com.twitter.android","com.reddit.frontpage"));Set<String>b=p.getStringSet("blocked_apps",d);if(!b.contains(s.getPackageName()))return;String k="notifications_"+new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date());p.edit().putInt(k,p.getInt(k,0)+1).apply();}
}