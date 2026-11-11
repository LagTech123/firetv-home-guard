package dev.local.homeguard;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Watches for Amazon's home screen coming to the front and starts the chosen launcher
 * through an AlarmManager PendingIntent. A launch sent by the alarm comes from system_server,
 * so it is not held back by the 5 s app-switch delay that follows the HOME key.
 *
 * Pause it without uninstalling:  adb shell settings put global homeguard_pause 1
 * Resume:                         adb shell settings put global homeguard_pause 0
 */
public class GuardService extends AccessibilityService {
    private static final String TAG = "homeguard";
    private static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private static final String TARGET_PKG = "com.spocky.projengmenu";
    private static final String TARGET_FALLBACK_ACTIVITY = "com.spocky.projengmenu.ui.home.MainActivity";
    private static final long DEBOUNCE_MS = 1500;

    private long lastFire = -DEBOUNCE_MS;

    @Override
    protected void onServiceConnected() {
        Log.i(TAG, "connected; target=" + TARGET_PKG);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null || !AMAZON_HOME.contentEquals(pkg)) return;

        Log.i(TAG, "amazon window: " + e.getClassName());
        if (Settings.Global.getInt(getContentResolver(), "homeguard_pause", 0) == 1) {
            Log.i(TAG, "paused, ignoring");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastFire < DEBOUNCE_MS) return;
        lastFire = now;
        fire();
    }

    private Intent buildLaunchIntent() {
        PackageManager pm = getPackageManager();
        // 1) a HOME-category intent aimed only at the target, so it behaves like a real HOME start
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(TARGET_PKG);
        if (pm.resolveActivity(home, 0) != null) return home;
        // 2) its leanback / normal launcher entry
        Intent i = pm.getLeanbackLaunchIntentForPackage(TARGET_PKG);
        if (i == null) i = pm.getLaunchIntentForPackage(TARGET_PKG);
        if (i != null) return i;
        // 3) explicit component
        return new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(TARGET_PKG, TARGET_FALLBACK_ACTIVITY));
    }

    private void fire() {
        try {
            Intent i = buildLaunchIntent();
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            long t = SystemClock.elapsedRealtime() + 20;
            // plain setExact: the allow-while-idle variant is rate-limited per app (~5 s apart), which made repeat HOME presses slow
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, t, pi);
            Log.i(TAG, "alarm set for " + i);
        } catch (Throwable t) {
            Log.e(TAG, "fire failed", t);
        }
    }

    @Override
    public void onInterrupt() { }
}