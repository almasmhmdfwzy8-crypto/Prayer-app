package __PKG__;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

/**
 * Runs after reboot, app update, and clock / time zone changes.
 * 1) restores the floating adhkar alarm (as before), 2) restarts the persistent notification service,
 *    which re-arms the single salawat alarm.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) return;
        boolean boot = "android.intent.action.BOOT_COMPLETED".equals(action);
        boolean updated = "android.intent.action.MY_PACKAGE_REPLACED".equals(action);
        boolean clock = "android.intent.action.TIME_SET".equals(action) || "android.intent.action.TIMEZONE_CHANGED".equals(action);
        boolean exactGranted = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action);
        if (!boot && !updated && !clock && !exactGranted) return;

        if (boot) restoreFloat(ctx);

        // adhkar / qiyam / Fajr alarm: every alarm is re-armed from the stored daily times
        try { ReminderService.makeChannels(ctx); ReminderService.scheduleAll(ctx); } catch (Exception e) { }

        SharedPreferences ap = ctx.getSharedPreferences("adhan", Context.MODE_PRIVATE);
        // Only when the app already configured the persistent notification or salawat once.
        if (ap.getString("times", "").isEmpty() && !ap.getBoolean("salOn", false) && !ap.contains("lat")) return;
        try {
            Intent s = new Intent(ctx, AdhanService.class);
            s.setAction(AdhanService.ACTION_RESCHEDULE);
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(s);
            } else {
                ctx.startService(s);
            }
        } catch (Exception e) {
            AdhanService.scheduleNextSalawat(ctx);
            AdhanService.scheduleNextAdhan(ctx, System.currentTimeMillis());
        }
    }

    private void restoreFloat(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("float", Context.MODE_PRIVATE);
        if (!sp.getBoolean("enabled", false)) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) return;
        int everyMin = sp.getInt("everyMin", 60);
        long interval = everyMin * 60000L;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 4401, new Intent(ctx, FloatReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + interval, interval, pi);
    }
}
