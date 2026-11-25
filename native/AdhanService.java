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
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import java.io.File;

public class AdhanService extends Service {
    public static final String ACTION_SAL = "__PKG__.SALAWAT";
    public static final String ACTION_SAL_TEST = "__PKG__.SALAWAT_TEST";
    public static final String ACTION_STOP_PLAYBACK = "__PKG__.STOP_PLAYBACK";
    public static final String ACTION_ADHAN = "__PKG__.ADHAN";
    public static final String ACTION_ADHAN_TEST = "__PKG__.ADHAN_TEST";
    public static final String CUSTOM_ADHAN_FILE = "adhan_custom.snd";
    public static final String ACTION_RESCHEDULE = "__PKG__.RESCHEDULE";
    public static final String CUSTOM_SOUND_FILE = "salawat_custom.snd";
    private static final String CH = "prayer_bar";
    private static final String CH_MIN = "prayer_min";
    private static final int NID = 4242;
    private static final int SAL_REQ = 4402;
    private static final int ADHAN_REQ = 4403;
    private static final String CH_ADHAN = "adhan_native";
    private static final String[] NAMES = { "الفجر", "الظهر", "العصر", "المغرب", "العشاء" };

    private MediaPlayer player;
    private PowerManager.WakeLock wake;
    private boolean adhanPlaying = false;
    private boolean salPending = false;
    private AudioManager audio;
    private AudioFocusRequest focusReq;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 30000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.deleteNotificationChannel("prayer_status");
            nm.deleteNotificationChannel("prayer_next2");
            NotificationChannel ch = new NotificationChannel(CH, "الصلاة القادمة", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
            NotificationChannel chm = new NotificationChannel(CH_MIN, "الصلاة القادمة (مخفي)", NotificationManager.IMPORTANCE_MIN);
            chm.setShowBadge(false);
            chm.setSound(null, null);
            chm.enableVibration(false);
            nm.createNotificationChannel(chm);
            NotificationChannel cha = new NotificationChannel(CH_ADHAN, "الأذان", NotificationManager.IMPORTANCE_HIGH);
            cha.setSound(null, null); // the sound is played by this service, so no other notification can cut it
            cha.enableVibration(true);
            nm.createNotificationChannel(cha);
        }
        Notification n = build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NID, n);
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);

        String action = intent == null ? null : intent.getAction();
        if (ACTION_SAL.equals(action)) {
            playSalawat(false);
        } else if (ACTION_SAL_TEST.equals(action)) {
            playSalawat(true);
        } else if (ACTION_ADHAN.equals(action)) {
            long at = intent.getLongExtra("at", 0);
            int idx = intent.getIntExtra("idx", 0);
            // ignore a stale alarm (device was off or in deep sleep for a long time)
            if (at == 0 || System.currentTimeMillis() - at < 10 * 60000L) playAdhan(idx, false);
            scheduleNextAdhan(this, Math.max(at, System.currentTimeMillis()));
            scheduleNextSalawat(this);
            return START_STICKY;
        } else if (ACTION_ADHAN_TEST.equals(action)) {
            playAdhan(0, true);
        } else if (ACTION_STOP_PLAYBACK.equals(action)) {
            stopPlayback();
        }
        scheduleNextAdhan(this, System.currentTimeMillis());
        scheduleNextSalawat(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        stopPlayback();
        super.onDestroy();
    }

    // ---------- Salawat: one native path (this service), scheduled by AlarmManager ----------

    /** Next slot is aligned to the epoch like the web page does it, so it survives time zone changes. */
    static long nextSalawatAt(SharedPreferences sp, long now) {
        long every = Math.max(5, sp.getInt("salMin", 30)) * 60000L;
        return ((now + 1) / every + 1) * every;
    }

    /** Sets (or cancels) the single pending salawat alarm. Safe to call any number of times: same PendingIntent. */
    public static void scheduleNextSalawat(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("adhan", Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, SAL_REQ, new Intent(ctx, SalawatReceiver.class), fl);
        if (am == null) return;
        if (!sp.getBoolean("salOn", false)) {
            am.cancel(pi);
            return;
        }
        long at = nextSalawatAt(sp, System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // Android 12+ without the exact alarm grant: allowed but may be delayed by the system.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    // ---------- Adhan: one native path (alarm -> receiver -> this service), prayer times computed here ----------

    private static double rad(double d) { return d * Math.PI / 180.0; }
    private static double deg(double r) { return r * 180.0 / Math.PI; }
    private static double norm360(double x) { return ((x % 360) + 360) % 360; }

    /** Midnight of the Cairo calendar day that contains `ms`, expressed as UTC ms (same as the web page's cairoDayStartUTC). */
    static long cairoDayStartUTC(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Africa/Cairo"));
        c.setTimeInMillis(ms);
        java.util.Calendar u = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        u.clear();
        u.set(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH), 0, 0, 0);
        return u.getTimeInMillis();
    }

    /** Port of prayerTimes() in index.html (NOAA solar position). Returns fajr, dhuhr, asr, maghrib, isha as UTC ms. */
    static long[] prayerTimes(long dayUTC, double lat, double lon, double asrK, int[] adj) {
        double jd = dayUTC / 86400000.0 + 2440587.5 + 0.5;
        double T = (jd - 2451545) / 36525.0;
        double L0 = norm360(280.46646 + T * (36000.76983 + T * 0.0003032));
        double M = norm360(357.52911 + T * (35999.05029 - 0.0001537 * T));
        double e = 0.016708634 - T * (0.000042037 + 0.0000001267 * T);
        double Mr = rad(M);
        double C = Math.sin(Mr) * (1.914602 - T * (0.004817 + 0.000014 * T))
                + Math.sin(2 * Mr) * (0.019993 - 0.000101 * T)
                + Math.sin(3 * Mr) * 0.000289;
        double trueL = L0 + C;
        double omega = 125.04 - 1934.136 * T;
        double lambda = trueL - 0.00569 - 0.00478 * Math.sin(rad(omega));
        double eps0 = 23 + (26 + (21.448 - T * (46.815 + T * (0.00059 - T * 0.001813))) / 60) / 60;
        double eps = eps0 + 0.00256 * Math.cos(rad(omega));
        double decl = Math.asin(Math.sin(rad(eps)) * Math.sin(rad(lambda)));
        double y = Math.pow(Math.tan(rad(eps) / 2), 2);
        double L0r = rad(L0);
        double eq = y * Math.sin(2 * L0r) - 2 * e * Math.sin(Mr)
                + 4 * e * y * Math.sin(Mr) * Math.cos(2 * L0r)
                - 0.5 * y * y * Math.sin(4 * L0r) - 1.25 * e * e * Math.sin(2 * Mr);
        double eqMin = deg(eq) * 4;
        double noon = 720 - 4 * lon - eqMin;
        double latR = rad(lat);
        double fajr = noon - halfArc(-19.5, latR, decl);
        double asrAlt = Math.atan(1 / (asrK + Math.tan(Math.abs(latR - decl))));
        double asrArc = halfArcRad(asrAlt, latR, decl);
        double dhuhr = noon + 0.5;
        double asr = noon + asrArc;
        double maghrib = noon + halfArc(-0.833, latR, decl);
        double isha = noon + halfArc(-17.5, latR, decl);
        return new long[] {
            dayUTC + Math.round((fajr + adj[0]) * 60000),
            dayUTC + Math.round((dhuhr + adj[1]) * 60000),
            dayUTC + Math.round((asr + adj[2]) * 60000),
            dayUTC + Math.round((maghrib + adj[3]) * 60000),
            dayUTC + Math.round((isha + adj[4]) * 60000)
        };
    }

    private static double halfArc(double altDeg, double latR, double decl) { return halfArcRad(rad(altDeg), latR, decl); }

    private static double halfArcRad(double altRad, double latR, double decl) {
        double cosH = (Math.sin(altRad) - Math.sin(latR) * Math.sin(decl)) / (Math.cos(latR) * Math.cos(decl));
        cosH = Math.max(-1, Math.min(1, cosH));
        return deg(Math.acos(cosH)) * 4;
    }

    /** Settings pushed by the app: city lat/lon, asr factor, minute adjustments. No new location is acquired. */
    private static boolean hasPrayerConfig(SharedPreferences sp) {
        return sp.contains("lat") && sp.contains("lon");
    }

    private static int[] adjOf(SharedPreferences sp) {
        int[] a = new int[5];
        String[] p = sp.getString("adj", "0,0,0,0,0").split(",");
        for (int i = 0; i < 5 && i < p.length; i++) {
            try { a[i] = Integer.parseInt(p[i].trim()); } catch (NumberFormatException e) { }
        }
        return a;
    }

    /** Next enabled prayer strictly after `after`: {index, atMs}, or null when not configured. */
    static long[] nextPrayer(SharedPreferences sp, long after, boolean onlyEnabled) {
        if (!hasPrayerConfig(sp)) return null;
        double lat, lon, asrK;
        try {
            lat = Double.parseDouble(sp.getString("lat", ""));
            lon = Double.parseDouble(sp.getString("lon", ""));
            asrK = Double.parseDouble(sp.getString("asr", "1"));
        } catch (NumberFormatException e) {
            return null;
        }
        int[] adj = adjOf(sp);
        String per = sp.getString("per", "11111");
        long today = cairoDayStartUTC(after);
        long best = Long.MAX_VALUE;
        int bi = -1;
        for (int d = -1; d <= 2; d++) {
            long[] t = prayerTimes(today + d * 86400000L, lat, lon, asrK, adj);
            for (int i = 0; i < 5; i++) {
                if (onlyEnabled && (per.length() > i && per.charAt(i) == '0')) continue;
                if (t[i] > after && t[i] < best) { best = t[i]; bi = i; }
            }
        }
        return bi < 0 ? null : new long[] { bi, best };
    }

    /** Sets (or cancels) the single pending adhan alarm for the next enabled prayer. Never expires: it is recomputed on every fire, boot, update and clock change. */
    public static void scheduleNextAdhan(Context ctx, long after) {
        SharedPreferences sp = ctx.getSharedPreferences("adhan", Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Intent in = new Intent(ctx, AdhanReceiver.class);
        long[] nx = sp.getBoolean("adhanOn", true) ? nextPrayer(sp, after, true) : null;
        if (nx == null) {
            PendingIntent pi0 = PendingIntent.getBroadcast(ctx, ADHAN_REQ, in, fl);
            am.cancel(pi0);
            return;
        }
        in.putExtra("at", nx[1]);
        in.putExtra("idx", (int) nx[0]);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, ADHAN_REQ, in, fl);
        // setAlarmClock: Android never delays or batches these and leaves Doze to deliver them. The *AllowWhileIdle alarms
        // are limited to about one per 9 minutes per app in Doze, and the salawat alarm uses that budget.
        // Needs no exact-alarm permission on Android 12+. The system shows a next-alarm icon; that is the accepted trade-off.
        Intent showIn = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        PendingIntent show = showIn == null ? null : PendingIntent.getActivity(ctx, 2, showIn, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(nx[1], show), pi);
    }

    /** Used only when the service cannot be started (system refused a background start): still tell the user, with the system default notification sound. */
    public static void postFallbackAdhan(Context ctx, int idx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel c = new NotificationChannel("adhan_fallback", "الأذان (احتياطي)", NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(c);
            }
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, "adhan_fallback") : new Notification.Builder(ctx);
            nb.setSmallIcon(R.drawable.ic_stat_prayer).setContentTitle("حان وقت صلاة " + NAMES[Math.max(0, Math.min(4, idx))]).setContentText("الله أكبر").setAutoCancel(true);
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if (launch != null) nb.setContentIntent(PendingIntent.getActivity(ctx, 1, launch, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)));
            nm.notify(5000 + idx, nb.build());
        } catch (Exception e) { }
    }

    private void playAdhan(int idx, boolean test) {
        SharedPreferences sp = getSharedPreferences("adhan", MODE_PRIVATE);
        if (!test && !sp.getBoolean("adhanOn", true)) return;
        String name = NAMES[Math.max(0, Math.min(4, idx))];
        if (!test) {
            // the visual notification (silent channel), posted here so it matches the sound exactly
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_ADHAN) : new Notification.Builder(this);
            nb.setSmallIcon(R.drawable.ic_stat_prayer).setContentTitle("حان وقت صلاة " + name).setContentText("الله أكبر").setAutoCancel(true);
            Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch != null) {
                launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                nb.setContentIntent(PendingIntent.getActivity(this, 1, launch, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)));
            }
            if (nm != null) nm.notify(5000 + idx, nb.build());
        }
        // adhan takes over from a salawat clip that is playing
        File custom = new File(getFilesDir(), CUSTOM_ADHAN_FILE);
        String key = sp.getString("adhanKey", "makkah1");
        if ("custom".equals(key) && custom.exists() && custom.length() > 0) {
            startClip(custom, 0, true);
        } else {
            if ("custom".equals(key)) key = "makkah1";
            int res = getResources().getIdentifier("adhan_" + key, "raw", getPackageName());
            if (res == 0) res = getResources().getIdentifier("adhan_makkah1", "raw", getPackageName());
            startClip(null, res, true);
        }
    }

    // ---------- Salawat: one native path (this service), scheduled by AlarmManager ----------

    private void playSalawat(boolean force) {
        SharedPreferences sp = getSharedPreferences("adhan", MODE_PRIVATE);
        if (!force && !sp.getBoolean("salOn", false)) return;
        if (!force && adhanPlaying) {
            // adhan owns the audio: remember that one slot is due and play it once when the adhan ends
            salPending = true;
            return;
        }
        File custom = new File(getFilesDir(), CUSTOM_SOUND_FILE);
        boolean useCustom = "custom".equals(sp.getString("salSound", "default")) && custom.exists() && custom.length() > 0;
        startClip(useCustom ? custom : null, R.raw.salawat, false);
    }

    /** Plays one clip. `file` first (if not null), then the raw resource as fallback. Adhan asks for exclusive transient focus. */
    private void startClip(File file, int rawRes, boolean isAdhan) {
        stopPlayback();
        if (audio == null) return;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(isAdhan ? AudioAttributes.USAGE_NOTIFICATION : AudioAttributes.USAGE_MEDIA)
                .setContentType(isAdhan ? AudioAttributes.CONTENT_TYPE_SONIFICATION : AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        int focusType = isAdhan ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE : AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
        MediaPlayer mp = null;
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(attrs);
            boolean ok = false;
            if (file != null) {
                try {
                    mp.setDataSource(file.getAbsolutePath());
                    mp.prepare(); // a corrupt or non-audio file fails here
                    ok = true;
                } catch (Exception e) {
                    try { mp.release(); } catch (Exception e2) { }
                    mp = new MediaPlayer();
                    mp.setAudioAttributes(attrs);
                }
            }
            if (!ok) {
                AssetFileDescriptor afd = getResources().openRawResourceFd(rawRes);
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                mp.prepare();
            }
            int granted;
            if (Build.VERSION.SDK_INT >= 26) {
                focusReq = new AudioFocusRequest.Builder(focusType)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                            @Override
                            public void onAudioFocusChange(int change) {
                                // a call or another app took focus: stop quietly instead of fighting it
                                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopPlayback();
                            }
                        }).build();
                granted = audio.requestAudioFocus(focusReq);
            } else {
                granted = audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, focusType);
            }
            if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                try { mp.release(); } catch (Exception e) { }
                stopPlayback();
                return;
            }
            // CPU stays awake for exactly the clip length (plus a margin), released in stopPlayback on every path
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    int dur = Math.max(0, mp.getDuration());
                    wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zad:clip");
                    wake.acquire(Math.min(dur + 10000L, 15 * 60000L));
                }
            } catch (Exception e) { }
            adhanPlaying = isAdhan;
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    finishClip();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    finishClip();
                    return true;
                }
            });
            player = mp;
            mp.start();
        } catch (Exception e) {
            if (mp != null && player != mp) { try { mp.release(); } catch (Exception e2) { } }
            stopPlayback();
        }
    }

    /** Clip ended or failed: release everything, then play one coalesced salawat slot if one came due during an adhan. */
    private void finishClip() {
        boolean wasAdhan = adhanPlaying;
        stopPlayback();
        if (wasAdhan && salPending) {
            salPending = false;
            SharedPreferences sp = getSharedPreferences("adhan", MODE_PRIVATE);
            if (sp.getBoolean("salOn", false)) playSalawat(false);
        }
    }

    private void stopPlayback() {
        if (wake != null) {
            try { if (wake.isHeld()) wake.release(); } catch (Exception e) { }
            wake = null;
        }
        adhanPlaying = false;
        if (player != null) {
            try { player.stop(); } catch (Exception e) { }
            try { player.release(); } catch (Exception e) { }
            player = null;
        }
        if (audio != null) {
            if (Build.VERSION.SDK_INT >= 26) {
                if (focusReq != null) audio.abandonAudioFocusRequest(focusReq);
                focusReq = null;
            } else {
                audio.abandonAudioFocus(null);
            }
        }
    }

    // ---------- Persistent notification ----------

    private void refresh() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NID, build());
    }

    private Object[] nextPrayer() {
        SharedPreferences sp = getSharedPreferences("adhan", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long[] nx = nextPrayer(sp, now, false); // computed here, never runs out
        if (nx != null) return new Object[] { NAMES[(int) nx[0]], nx[1] };
        String raw = sp.getString("times", "");
        if (raw == null || raw.isEmpty()) return null;
        long bestAt = Long.MAX_VALUE;
        String bestName = "";
        for (String item : raw.split(";")) {
            int bar = item.indexOf('|');
            if (bar < 0) continue;
            try {
                long at = Long.parseLong(item.substring(0, bar));
                if (at > now && at < bestAt) {
                    bestAt = at;
                    bestName = item.substring(bar + 1);
                }
            } catch (NumberFormatException e) {
            }
        }
        if (bestAt == Long.MAX_VALUE) return null;
        return new Object[] { bestName, bestAt };
    }

    private int colorOf(String c) {
        try {
            if (c != null && c.length() == 7 && c.charAt(0) == '#') return (int) (0xFF000000L | Long.parseLong(c.substring(1), 16));
        } catch (NumberFormatException e) {
        }
        return 0xFFF6C945;
    }

    /** Readable text colour on a solid background (WCAG relative luminance). */
    private static double lum(int c) {
        double[] v = {(c >> 16 & 255) / 255.0, (c >> 8 & 255) / 255.0, (c & 255) / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }
    /** WCAG contrast of colour c against the fixed dark card #1B222C. */
    private static double contrastOnDark(int c) {
        double a = lum(c) + 0.05, b = lum(0xFF1B222C) + 0.05;
        return a > b ? a / b : b / a;
    }

    private int textOn(int bg) {
        double r = lin(((bg >> 16) & 0xFF) / 255.0), g = lin(((bg >> 8) & 0xFF) / 255.0), b = lin((bg & 0xFF) / 255.0);
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return lum > 0.4 ? 0xFF111111 : 0xFFFFFFFF;
    }

    private double lin(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private Notification build() {
        Object[] nx = nextPrayer();
        SharedPreferences sp = getSharedPreferences("adhan", MODE_PRIVATE);
        String style = sp.getString("nstyle", "full");
        String pos = sp.getString("npos", "bar");
        String color = sp.getString("ncolor", "");
        boolean full = !"short".equals(style);
        boolean colored = color != null && color.length() == 7 && color.charAt(0) == '#';
        String title = "زاد المؤمن";
        String text = "افتح التطبيق عشان المواقيت تتحدّث";
        long at = 0;
        if (nx != null) {
            at = (Long) nx[1];
            long mins = Math.max(0, (at - System.currentTimeMillis()) / 60000L);
            long h = mins / 60L;
            long m = mins % 60L;
            if (full) {
                title = "الصلاة القادمة: " + nx[0];
                text = "متبقي " + (h > 0 ? h + " س " + m + " د" : m + " د");
            } else {
                title = String.valueOf(nx[0]);
                text = "الصلاة القادمة";
            }
        }
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, "hidden".equals(pos) ? CH_MIN : CH)
                .setSmallIcon(R.drawable.ic_stat_prayer)
                .setColor(colorOf(color))
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_bg_art))
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        if (colored) b.setColorized(true);
        if (nx != null && full && Build.VERSION.SDK_INT >= 24) {
            RemoteViews rv = new RemoteViews(getPackageName(), R.layout.pn_custom);
            PendingIntent openApp = openAppIntent();
            if (openApp != null) rv.setOnClickPendingIntent(R.id.pn_root, openApp);
            rv.setTextViewText(R.id.pn_name, String.valueOf(nx[0]));
            rv.setChronometerCountDown(R.id.pn_time, true);
            rv.setChronometer(R.id.pn_time, SystemClock.elapsedRealtime() + (at - System.currentTimeMillis()), null, true);
            if (colored) {
                // The card stays dark. The user's colour only tints the countdown digits.
                int dig = colorOf(color);
                // Card is fixed #1B222C. Use the chosen colour only when it reads on it (contrast 4.5 or more), else the default gold.
                if (contrastOnDark(dig) < 4.5) dig = 0xFFF0CF7F;
                rv.setTextColor(R.id.pn_time, dig);
            }
            b.setCustomContentView(rv).setCustomBigContentView(rv);
            b.setShowWhen(false);
        } else {
            b.setShowWhen(false);
        }
        PendingIntent openAppPi = openAppIntent();
        if (openAppPi != null) b.setContentIntent(openAppPi);
        return b.build();
    }

    /** Opens the app's main screen. Explicit component first (always resolves), launcher intent as a fallback. */
    private PendingIntent openAppIntent() {
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.setClassName(getPackageName(), getPackageName() + ".MainActivity");
        if (getPackageManager().resolveActivity(launch, 0) == null) {
            launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        }
        if (launch == null) return null;
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 0, launch, flags);
    }
}
