package __PKG__;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Base64;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;

@CapacitorPlugin(name = "AdhanNotifier")
public class AdhanNotifierPlugin extends Plugin {

    @PluginMethod
    public void update(PluginCall call) {
        String times = call.getString("times", "");
        String nstyle = call.getString("style", "full");
        String npos = call.getString("pos", "bar");
        String ncolor = call.getString("color", "");
        String ncard = call.getString("card", "");
        String nfrm = call.getString("frame", "");
        String ndig = call.getString("digits", "");
        boolean salOn = Boolean.TRUE.equals(call.getBoolean("salOn", false));
        boolean adhanOn = !Boolean.FALSE.equals(call.getBoolean("adhanOn", true));
        String adhanKey = call.getString("adhanKey", "makkah1");
        String lat = call.getString("lat", "");
        String lon = call.getString("lon", "");
        String asr = call.getString("asr", "1");
        String adj = call.getString("adj", "0,0,0,0,0");
        String per = call.getString("per", "11111");
        int salMin = call.getInt("salMin", 30);
        String salSound = call.getString("salSound", "default");
        getContext().getSharedPreferences("adhan", Context.MODE_PRIVATE).edit()
                .putString("times", times).putString("nstyle", nstyle).putString("npos", npos).putString("ncolor", ncolor)
                .putString("ncard", ncard).putString("nfrm", nfrm).putString("ndig", ndig)
                .putBoolean("salOn", salOn).putInt("salMin", salMin).putString("salSound", salSound)
                .putBoolean("adhanOn", adhanOn).putString("adhanKey", adhanKey)
                .putString("asr", asr).putString("adj", adj).putString("per", per)
                .remove("sal").apply();
        if (!lat.isEmpty() && !lon.isEmpty()) {
            getContext().getSharedPreferences("adhan", Context.MODE_PRIVATE).edit().putString("lat", lat).putString("lon", lon).apply();
        }
        startService();
        call.resolve();
    }

    /** Copies the installed APK (this exact version) into cache/updates so the share sheet can send it. No download, works offline. */
    @PluginMethod
    public void copyApk(PluginCall call) {
        try {
            File src = new File(getContext().getApplicationInfo().sourceDir);
            File dir = new File(getContext().getCacheDir(), "updates");
            if (!dir.exists() && !dir.mkdirs()) { call.reject("no cache dir"); return; }
            new File(dir, "zaid-share.apk").delete();
            File dst = new File(dir, "\u0632\u064E\u0627\u062F\u064F \u0627\u0644\u0652\u0645\u064F\u0624\u0652\u0645\u0650\u0646\u0650.apk");
            InputStream in = new java.io.FileInputStream(src);
            FileOutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
            r.put("size", dst.length());
            call.resolve(r);
        } catch (Exception e) {
            call.reject("copy failed");
        }
    }

    private void startService() {
        Intent i = new Intent(getContext(), AdhanService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            getContext().startForegroundService(i);
        } else {
            getContext().startService(i);
        }
    }

    private File soundTmp(String kind) {
        return new File(getContext().getFilesDir(), ("adhan".equals(kind) ? AdhanService.CUSTOM_ADHAN_FILE : AdhanService.CUSTOM_SOUND_FILE) + ".tmp");
    }

    /** Sound upload in small base64 chunks (kind = "salawat" or "adhan"), so the old phone never holds the whole file twice. */
    @PluginMethod
    public void saveSoundChunk(PluginCall call) {
        String kind = call.getString("kind", "salawat");
        String data = call.getString("data", "");
        boolean first = Boolean.TRUE.equals(call.getBoolean("first", false));
        File tmp = soundTmp(kind);
        long cap = "adhan".equals(kind) ? 6L * 1024 * 1024 : 3L * 1024 * 1024;
        try {
            if (first) tmp.delete();
            byte[] bytes = Base64.decode(data == null ? "" : data, Base64.DEFAULT);
            if (tmp.length() + bytes.length > cap) { tmp.delete(); call.reject("size"); return; }
            try (FileOutputStream out = new FileOutputStream(tmp, true)) { out.write(bytes); }
            call.resolve();
        } catch (Exception e) {
            tmp.delete();
            call.reject("chunk failed");
        }
    }

    /** Validates the uploaded file as audio, then swaps it in. Any failure keeps the previous sound untouched. */
    @PluginMethod
    public void finishSound(PluginCall call) {
        String kind = call.getString("kind", "salawat");
        File tmp = soundTmp(kind);
        File dst = new File(getContext().getFilesDir(), "adhan".equals(kind) ? AdhanService.CUSTOM_ADHAN_FILE : AdhanService.CUSTOM_SOUND_FILE);
        if (!tmp.exists() || tmp.length() == 0) { tmp.delete(); call.reject("empty"); return; }
        android.media.MediaPlayer probe = new android.media.MediaPlayer();
        try {
            probe.setDataSource(tmp.getAbsolutePath());
            probe.prepare();
        } catch (Exception e) {
            tmp.delete();
            call.reject("not audio");
            return;
        } finally {
            try { probe.release(); } catch (Exception e) { }
        }
        if (!tmp.renameTo(dst)) { tmp.delete(); call.reject("rename"); return; }
        call.resolve();
    }

    @PluginMethod
    public void cancelSound(PluginCall call) {
        soundTmp(call.getString("kind", "salawat")).delete();
        call.resolve();
    }

    @PluginMethod
    public void clearSalawatSound(PluginCall call) {
        new File(getContext().getFilesDir(), AdhanService.CUSTOM_SOUND_FILE).delete();
        getContext().getSharedPreferences("adhan", Context.MODE_PRIVATE).edit().putString("salSound", "default").apply();
        call.resolve();
    }

    /** Plays the selected adhan once through the same service path the alarm uses. */
    @PluginMethod
    public void playAdhanTest(PluginCall call) {
        Intent i = new Intent(getContext(), AdhanService.class);
        i.setAction(AdhanService.ACTION_ADHAN_TEST);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                getContext().startForegroundService(i);
            } else {
                getContext().startService(i);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("start failed");
        }
    }

    /** Plays the current salawat sound once through the same service path the alarm uses. */
    @PluginMethod
    public void playSalawatTest(PluginCall call) {
        Intent i = new Intent(getContext(), AdhanService.class);
        i.setAction(AdhanService.ACTION_SAL_TEST);
        if (Build.VERSION.SDK_INT >= 26) {
            getContext().startForegroundService(i);
        } else {
            getContext().startService(i);
        }
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        // stop any playing salawat clip only; the persistent notification service keeps running
        Intent i = new Intent(getContext(), AdhanService.class);
        i.setAction(AdhanService.ACTION_STOP_PLAYBACK);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                getContext().startForegroundService(i);
            } else {
                getContext().startService(i);
            }
        } catch (Exception e) {
            // app not allowed to start the service right now: nothing to stop in that case
        }
        call.resolve();
    }


    // ---------- Adhkar / qiyam / Fajr alarm: scheduled and played natively ----------

    /** The page sends its reminders; the service owns the alarms and plays the sounds itself. */
    @PluginMethod
    public void setReminders(PluginCall call) {
        String json = call.getString("items", "[]");
        try {
            JSONArray a = new JSONArray(json);
            if (a.length() > 40) { call.reject("too many"); return; }
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                int h = o.optInt("h", -1), m = o.optInt("m", -1);
                if (h < 0 || h > 23 || m < 0 || m > 59) { call.reject("bad time"); return; }
            }
        } catch (Exception e) {
            call.reject("bad list");
            return;
        }
        ReminderService.store(getContext(), json);
        ReminderService.makeChannels(getContext());
        ReminderService.scheduleAll(getContext());
        call.resolve();
    }

    /** Stops the Fajr alarm sound once he has answered. */
    @PluginMethod
    public void stopReminder(PluginCall call) {
        ReminderService.stop(getContext());
        call.resolve();
    }

    /** Settings: play one reminder sound now, without touching the schedule. */
    @PluginMethod
    public void testReminder(PluginCall call) {
        String kind = call.getString("kind", "morning");
        String title = call.getString("title", "تجربة الصوت");
        String body = call.getString("body", "");
        ReminderService.makeChannels(getContext());
        ReminderService.playNow(getContext(), kind, title, body);
        call.resolve();
    }

    /** Settings: open the "Alarms & reminders" screen (Android 12+). */
    @PluginMethod
    public void openExactAlarms(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getContext().getPackageName()));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            }
        } catch (Exception e) { }
        call.resolve();
    }

    /** Settings: one call that tells the page what is allowed right now. */
    @PluginMethod
    public void reminderState(PluginCall call) {
        com.getcapacitor.JSObject o = new com.getcapacitor.JSObject();
        boolean exact = true;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                AlarmManager am = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
                exact = am != null && am.canScheduleExactAlarms();
            }
        } catch (Exception e) { }
        o.put("exact", exact);
        o.put("sdk", Build.VERSION.SDK_INT);
        try {
            o.put("notif", androidx.core.app.NotificationManagerCompat.from(getContext()).areNotificationsEnabled());
        } catch (Exception e) { o.put("notif", true); }
        try {
            PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            o.put("battery", pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName()));
        } catch (Exception e) { o.put("battery", false); }
        call.resolve(o);
    }

    @PluginMethod
    public void openBattery(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void installUpdate(PluginCall call) {
        String url = call.getString("url", "");
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.connect();
                File dir = new File(getContext().getCacheDir(), "updates");
                dir.mkdirs();
                File f = new File(dir, "update.apk");
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                }
                c.disconnect();
                Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
                call.resolve();
            } catch (Exception e) {
                call.reject("update failed: " + e.getMessage());
            }
        }).start();
    }

    // ---------- Quran Radio (Cairo): the player lives in RadioService ----------
    // The stream is owned by RadioService (media-playback foreground service, START_STICKY) so it keeps
    // playing after the app is closed or the task is swiped away. This plugin only forwards commands.
    private static AdhanNotifierPlugin inst;

    @Override public void load() { super.load(); inst = this; }

    static void radioEvent(String kind, boolean playing) {
        try {
            if (inst == null) return;
            com.getcapacitor.JSObject o = new com.getcapacitor.JSObject();
            o.put("playing", playing);
            String ev = "stopped".equals(kind) ? "radioStopped" : ("paused".equals(kind) ? "radioPaused" : "radioPlaying");
            inst.notifyListeners(ev, o);
        } catch (Throwable t) { }
    }

    @PluginMethod
    public void radioStatus(final PluginCall call) {
        com.getcapacitor.JSObject o = new com.getcapacitor.JSObject();
        o.put("playing", RadioService.isPlaying());
        call.resolve(o);
    }

    @PluginMethod
    public void radioStart(final PluginCall call) {
        final String url = call.getString("url", "");
        if (url == null || !url.startsWith("https://")) { call.reject("bad url"); return; }
        try {
            RadioService.start(getContext(), url, call.getInt("gain", 900));
            call.resolve();
        } catch (Throwable t) {
            call.reject("radio failed");
        }
    }

    @PluginMethod
    public void radioStop(final PluginCall call) {
        try { RadioService.stop(getContext()); } catch (Throwable t) { }
        call.resolve();
    }

    @PluginMethod
    public void radioGain(final PluginCall call) {
        RadioService.setGain(call.getInt("gain", 0));
        call.resolve();
    }

    /** Opens the battery-optimisation screen so the phone stops killing background playback. */
    @PluginMethod
    public void batterySettings(final PluginCall call) {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 23) {
                i = new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            } else {
                i = new Intent(android.provider.Settings.ACTION_SETTINGS);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Throwable t) {
            call.reject("cannot open settings");
        }
    }

    @Override
    protected void handleOnDestroy() {
        // do NOT stop the radio here: RadioService owns the player and keeps it running
        if (inst == this) inst = null;
        super.handleOnDestroy();
    }
}
