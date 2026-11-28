package __PKG__;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Plays the adhkar / qiyam / Fajr-alarm sounds itself (MediaPlayer), because a notification channel
 * sound cannot be relied on: Android freezes a channel's sound at creation, and a notification sound
 * follows the ringer mode. The schedule lives here too: one alarm per reminder, re-armed every day and
 * after boot, update and clock changes.
 */
public class ReminderService extends Service {
    public static final String ACTION_FIRE = "__PKG__.REMINDER_FIRE";
    public static final String ACTION_STOP = "__PKG__.REMINDER_STOP";
    public static final String CH = "reminder_v2";
    public static final String CH_WAKE = "wake_v2";
    private static final String PREFS = "reminders";
    private static final String KEY_LIST = "list";
    private static final int NID_WAKE = 7301;
    private static final int REQ_BASE = 6100; // 6100..6139, one request code per stored reminder

    private MediaPlayer player;
    private PowerManager.WakeLock wake;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- stored list ----------

    /** [{k:"morning"|"evening"|"qiyam"|"wake", h:6, m:30, title:"...", body:"..."}] */
    public static void store(Context ctx, String json) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LIST, json == null ? "[]" : json).apply();
    }

    private static JSONArray list(Context ctx) {
        try {
            return new JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    // ---------- schedule ----------

    /** Next occurrence of hh:mm in Cairo time, strictly after now. */
    static long nextAt(int h, int m, long now) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Africa/Cairo"));
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, m);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long at = c.getTimeInMillis();
        if (at <= now + 5000L) at += 86400000L;
        return at;
    }

    private static PendingIntent pi(Context ctx, int idx, JSONObject item) {
        Intent in = new Intent(ctx, ReminderReceiver.class);
        in.setAction(ACTION_FIRE);
        in.putExtra("idx", idx);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(ctx, REQ_BASE + idx, in, fl);
    }

    /** Cancels everything and arms the next occurrence of every stored reminder. Safe to call any time. */
    public static void scheduleAll(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        JSONArray a = list(ctx);
        for (int i = 0; i < 40; i++) am.cancel(pi(ctx, i, null));
        long now = System.currentTimeMillis();
        for (int i = 0; i < a.length() && i < 40; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            arm(ctx, am, i, o, nextAt(o.optInt("h", 6), o.optInt("m", 30), now));
        }
    }

    /** Re-arms one reminder for its next day. Called after it fires. */
    static void scheduleOne(Context ctx, int idx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        JSONArray a = list(ctx);
        JSONObject o = a.optJSONObject(idx);
        if (o == null) return;
        arm(ctx, am, idx, o, nextAt(o.optInt("h", 6), o.optInt("m", 30), System.currentTimeMillis()));
    }

    private static void arm(Context ctx, AlarmManager am, int idx, JSONObject o, long at) {
        PendingIntent p = pi(ctx, idx, o);
        boolean wakeKind = "wake".equals(o.optString("k"));
        try {
            if (wakeKind) {
                // an alarm clock: never batched, shown on the lock screen, survives Doze
                Intent showIn = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
                PendingIntent show = showIn == null ? null : PendingIntent.getActivity(ctx, 2, showIn,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), p);
            } else if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, p);
            }
        } catch (Exception e) {
            // never leave the schedule dead: a coarse alarm still delivers
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p); } catch (Exception e2) { }
        }
    }

    // ---------- service ----------

    @Override
    public void onCreate() {
        super.onCreate();
        makeChannels(this);
    }

    /** The channels carry no sound: this service plays it, so silent mode and frozen channels cannot mute it. */
    public static void makeChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel a = new NotificationChannel(CH, "تذكيرات الأذكار", NotificationManager.IMPORTANCE_HIGH);
        a.setSound(null, null);
        a.enableVibration(true);
        a.setShowBadge(false);
        nm.createNotificationChannel(a);
        NotificationChannel w = new NotificationChannel(CH_WAKE, "منبه الفجر", NotificationManager.IMPORTANCE_HIGH);
        w.setSound(null, null);
        w.enableVibration(true);
        w.setShowBadge(false);
        w.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(w);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        makeChannels(this);
        if (ACTION_STOP.equals(action)) {
            stopPlayback();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        int idx = intent == null ? 0 : intent.getIntExtra("idx", 0);
        JSONObject item = idx < 0 ? testItem(intent) : list(this).optJSONObject(idx);
        if (item == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification n = build(item);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(item.optString("k").equals("wake") ? NID_WAKE : 7300 + idx, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(item.optString("k").equals("wake") ? NID_WAKE : 7300 + idx, n);
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(item.optString("k").equals("wake") ? NID_WAKE : 7300 + idx, n);
        play(item.optString("k"));
        if (idx >= 0) scheduleOne(this, idx);
        return START_NOT_STICKY;
    }

    private static JSONObject testItem(Intent intent) {
        try {
            JSONObject o = new JSONObject();
            o.put("k", intent.getStringExtra("kind") == null ? "morning" : intent.getStringExtra("kind"));
            o.put("title", intent.getStringExtra("title") == null ? "تجربة الصوت" : intent.getStringExtra("title"));
            o.put("body", intent.getStringExtra("body") == null ? "" : intent.getStringExtra("body"));
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    private Notification build(JSONObject item) {
        boolean wakeKind = "wake".equals(item.optString("k"));
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, wakeKind ? CH_WAKE : CH)
                .setSmallIcon(R.drawable.ic_stat_prayer)
                .setContentTitle(item.optString("title", "زاد المؤمن"))
                .setContentText(item.optString("body", ""))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(item.optString("body", "")))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(wakeKind ? NotificationCompat.CATEGORY_ALARM : NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setOngoing(wakeKind);
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            b.setContentIntent(PendingIntent.getActivity(this, 1, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)));
            if (wakeKind) b.setFullScreenIntent(PendingIntent.getActivity(this, 3, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)), true);
        }
        Intent stop = new Intent(this, ReminderService.class);
        stop.setAction(ACTION_STOP);
        b.addAction(0, "إيقاف", PendingIntent.getService(this, 9, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)));
        return b.build();
    }

    private void play(String kind) {
        int res;
        boolean isWake = "wake".equals(kind);
        if ("morning".equals(kind)) res = R.raw.adhkar_morning;
        else if ("evening".equals(kind)) res = R.raw.adhkar_evening;
        else if ("qiyam".equals(kind)) res = R.raw.qiyam;
        else res = R.raw.alarm_fajr;
        stopPlayback();
        try {
            MediaPlayer mp = new MediaPlayer();
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(isWake ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA)
                    .setContentType(isWake ? AudioAttributes.CONTENT_TYPE_SONIFICATION : AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            mp.setAudioAttributes(attrs);
            AssetFileDescriptor afd = getResources().openRawResourceFd(res);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.prepare();
            mp.setLooping(isWake);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zad:reminder");
                wake.acquire(isWake ? 10 * 60000L : Math.max(60000L, mp.getDuration() + 15000L));
            }
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    if (!m.isLooping()) finishQuiet();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    finishQuiet();
                    return true;
                }
            });
            player = mp;
            mp.start();
        } catch (Exception e) {
            stopPlayback();
        }
    }

    private void finishQuiet() {
        stopPlayback();
        stopForeground(true);
        stopSelf();
    }

    private void stopPlayback() {
        if (player != null) {
            try { player.stop(); } catch (Exception e) { }
            try { player.release(); } catch (Exception e) { }
            player = null;
        }
        if (wake != null) {
            try { if (wake.isHeld()) wake.release(); } catch (Exception e) { }
            wake = null;
        }
    }

    @Override
    public void onDestroy() {
        stopPlayback();
        super.onDestroy();
    }

    /** Plays one sound now, without touching the schedule (settings test button). */
    public static void playNow(Context ctx, String kind, String title, String body) {
        Intent i = new Intent(ctx, ReminderService.class);
        i.setAction(ACTION_FIRE);
        i.putExtra("idx", -1);
        i.putExtra("kind", kind);
        i.putExtra("title", title);
        i.putExtra("body", body);
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
        } catch (Exception e) { }
    }

    /** Stops a playing reminder (used when he answers the Fajr questions). Safe when nothing is playing. */
    public static void stop(Context ctx) {
        try { ctx.stopService(new Intent(ctx, ReminderService.class)); } catch (Exception e) { }
    }
  }
