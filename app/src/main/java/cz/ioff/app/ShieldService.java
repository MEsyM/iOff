package cz.ioff.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public final class ShieldService extends AccessibilityService {
    private static final int BG = Color.rgb(5, 9, 9);
    private static final int SURFACE = Color.rgb(17, 23, 25);
    private static final int TEXT = Color.rgb(243, 247, 245);
    private static final int MUTED = Color.rgb(164, 173, 170);
    private static final int GREEN = Color.rgb(93, 245, 139);
    private static final int RED = Color.rgb(255, 83, 91);
    private static final Set<String> DEFAULT_BLOCKED = new HashSet<>(Arrays.asList(
        "com.instagram.android", "com.facebook.katana", "com.zhiliaoapp.musically",
        "com.google.android.youtube", "com.twitter.android", "com.reddit.frontpage",
        "com.snapchat.android"
    ));

    private WindowManager windowManager;
    private View overlay;
    private String blockedPackage;
    private long bypassUntil;

    @Override public void onServiceConnected() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String packageName = event.getPackageName().toString();
        if (packageName.equals(getPackageName())) return;
        SharedPreferences preferences = getSharedPreferences("ioff", 0);
        if (!preferences.getBoolean("shield_enabled", true)
            || !isProtectedNow(preferences)
            || System.currentTimeMillis() < bypassUntil
            || !preferences.getStringSet("blocked_apps", DEFAULT_BLOCKED).contains(packageName)) return;
        blockedPackage = packageName;
        showOverlay(false, true);
    }

    private boolean isProtectedNow(SharedPreferences preferences) {
        JSONObject active = activeFocus(preferences);
        if (active != null && active.optLong("end") > System.currentTimeMillis()) return true;
        if (!preferences.getBoolean("morning_shield", true)) return false;
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return minute >= preferences.getInt("morning_start", 360)
            && minute < preferences.getInt("morning_until", 540);
    }

    private JSONObject activeFocus(SharedPreferences preferences) {
        try {
            String raw = preferences.getString("focus_active_v2", null);
            return raw == null ? null : new JSONObject(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int experimentDay(SharedPreferences preferences) {
        JSONObject active = activeFocus(preferences);
        return active == null ? preferences.getInt("day", 1) : active.optInt("day", preferences.getInt("day", 1));
    }

    private String appName() {
        if (blockedPackage == null) return "This app";
        if (blockedPackage.contains("instagram")) return "Instagram";
        if (blockedPackage.contains("youtube")) return "YouTube";
        if (blockedPackage.contains("reddit")) return "Reddit";
        if (blockedPackage.contains("facebook")) return "Facebook";
        if (blockedPackage.contains("twitter")) return "X";
        if (blockedPackage.contains("tiktok") || blockedPackage.contains("zhiliao")) return "TikTok";
        if (blockedPackage.contains("snapchat")) return "Snapchat";
        return "This app";
    }

    private void showOverlay(boolean confirmation, boolean countIntervention) {
        removeOverlay();
        SharedPreferences preferences = getSharedPreferences("ioff", 0);
        JSONObject active = activeFocus(preferences);
        String goal = active == null ? "" : active.optString("goal");
        long remaining = active == null ? 0 : Math.max(0, active.optLong("end") - System.currentTimeMillis());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(28), dp(64), dp(28), dp(48));
        content.setBackgroundColor(BG);
        content.addView(text("⊘", 66, RED, Typeface.BOLD));
        content.addView(text(confirmation ? "Break focus intentionally?" : "Stay Focused", 31, TEXT, Typeface.BOLD));
        content.addView(text(
            confirmation ? "You already chose what matters." :
                (goal.isEmpty() ? appName() + " can wait." : "You chose to finish:\n" + goal),
            17, TEXT, Typeface.NORMAL
        ));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(SURFACE, 18));
        card.addView(text((remaining / 60_000) + " min left", 19, TEXT, Typeface.BOLD));
        card.addView(text("“Distraction now\nis a longer tomorrow.”", 15, MUTED, Typeface.ITALIC));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, dp(20), 0, dp(28));
        content.addView(card, cardParams);

        Button back = button("RETURN TO iOff", GREEN, BG);
        back.setOnClickListener(v -> returnToIOff());
        content.addView(back, new LinearLayout.LayoutParams(-1, dp(56)));

        int bypasses = preferences.getInt("exp_" + experimentDay(preferences) + "_bypasses", 0);
        Button pass = button(confirmation ? "CONTINUE FOR 5 MINUTES" : "I REALLY NEED TO OPEN " + appName().toUpperCase(), Color.TRANSPARENT, TEXT);
        pass.setOnClickListener(v -> {
            if (!confirmation) { showOverlay(true, false); return; }
            bypassUntil = System.currentTimeMillis() + 300_000;
            String key = "exp_" + experimentDay(preferences) + "_bypasses";
            preferences.edit().putInt(key, preferences.getInt(key, 0) + 1).apply();
            removeOverlay();
        });
        content.addView(pass, new LinearLayout.LayoutParams(-1, dp(54)));
        content.addView(text(Math.max(0, 3 - bypasses) + " overrides left today", 12, MUTED, Typeface.NORMAL));

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            -1, -1, type, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT
        );
        overlay = content;
        windowManager.addView(overlay, params);
        if (countIntervention) {
            String key = "exp_" + experimentDay(preferences) + "_shield";
            preferences.edit().putInt(key, preferences.getInt(key, 0) + 1).apply();
        }
    }

    private void returnToIOff() {
        Intent intent = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        removeOverlay();
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setPadding(dp(4), dp(10), dp(4), dp(10));
        return view;
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundRect(background, 28));
        return button;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void removeOverlay() {
        if (overlay == null || windowManager == null) return;
        try { windowManager.removeView(overlay); } catch (Exception ignored) { }
        overlay = null;
    }

    @Override public void onInterrupt() { removeOverlay(); }
    @Override public void onDestroy() { removeOverlay(); super.onDestroy(); }
}
