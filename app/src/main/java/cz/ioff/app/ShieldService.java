package cz.ioff.app;

import android.accessibilityservice.AccessibilityService;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import java.util.*;

public class ShieldService extends AccessibilityService {
    WindowManager wm; View overlay; String blockedPackage; long bypassUntil=0;
    final Set<String> defaults=new HashSet<>(Arrays.asList("com.instagram.android","com.facebook.katana","com.zhiliaoapp.musically","com.google.android.youtube","com.twitter.android","com.reddit.frontpage"));
    public void onServiceConnected(){wm=(WindowManager)getSystemService(WINDOW_SERVICE);}
    public void onAccessibilityEvent(AccessibilityEvent e){
        if(e.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||e.getPackageName()==null)return;
        String pkg=e.getPackageName().toString(); if(pkg.equals(getPackageName()))return;
        SharedPreferences p=getSharedPreferences("ioff",0);
        if(!p.getBoolean("shield_enabled",true)||!protectedNow(p)||System.currentTimeMillis()<bypassUntil)return;
        Set<String> selected=p.getStringSet("blocked_apps",defaults); if(!selected.contains(pkg))return;
        blockedPackage=pkg; showFriction();
    }
    boolean protectedNow(SharedPreferences p){
        if(p.getBoolean("focus_active",false))return true;
        if(!p.getBoolean("morning_shield",true))return false;
        Calendar c=Calendar.getInstance(); int mins=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);
        int until=p.getInt("morning_until",9*60); return mins<until;
    }
    GradientDrawable bg(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(r);return g;}
    void showFriction(){if(overlay!=null)return;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(54,70,54,70);box.setBackgroundColor(Color.rgb(10,12,16));TextView logo=new TextView(this);logo.setText("iOff");logo.setTextColor(Color.rgb(130,255,177));logo.setTextSize(28);logo.setGravity(Gravity.CENTER);box.addView(logo);TextView h=new TextView(this);h.setText("WHY NOW?");h.setTextColor(Color.WHITE);h.setTextSize(34);h.setGravity(Gravity.CENTER);h.setPadding(0,50,0,20);box.addView(h);TextView m=new TextView(this);m.setText("You're in protected time.\nYour attention already has a job.");m.setTextColor(Color.rgb(170,177,190));m.setTextSize(18);m.setGravity(Gravity.CENTER);m.setPadding(0,0,0,50);box.addView(m);Button back=new Button(this);back.setText("GO BACK TO WHAT MATTERS");back.setTextColor(Color.rgb(10,12,16));back.setBackground(bg(Color.rgb(130,255,177),30));back.setOnClickListener(v->{performGlobalAction(GLOBAL_ACTION_BACK);remove();});box.addView(back,new LinearLayout.LayoutParams(-1,120));Button cont=new Button(this);cont.setText("I NEED THIS APP — CONTINUE");cont.setTextColor(Color.WHITE);cont.setBackgroundColor(Color.TRANSPARENT);cont.setOnClickListener(v->{bypassUntil=System.currentTimeMillis()+5*60*1000;getSharedPreferences("ioff",0).edit().putInt(dayKey("bypasses"),getSharedPreferences("ioff",0).getInt(dayKey("bypasses"),0)+1).apply();remove();});box.addView(cont,new LinearLayout.LayoutParams(-1,110));int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,type,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);overlay=box;wm.addView(overlay,lp);}
    String dayKey(String k){return k+"_"+new java.text.SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date());}
    void remove(){if(overlay!=null){wm.removeView(overlay);overlay=null;}}
    public void onInterrupt(){remove();}
    public void onDestroy(){remove();super.onDestroy();}
}
