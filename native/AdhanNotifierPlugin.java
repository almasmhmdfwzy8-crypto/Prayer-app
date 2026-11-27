package __PKG__;

import android.content.Context;
import android.content.Intent;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
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

    // ---------- Quran Radio (Cairo): official HLS stream, audio only (video track disabled), started only by the user ----------
    // static: the radio keeps playing (foreground service) after the activity and this plugin instance are gone
    private static ExoPlayer radio;
    private static LoudnessEnhancer radioLe;
    private static DynamicsProcessing radioDp;
    private static AdhanNotifierPlugin inst;
    private static final Handler radioMainS = new Handler(Looper.getMainLooper());
    @Override public void load() { super.load(); inst = this; }
    private static void emit(String ev, com.getcapacitor.JSObject o) { try { if (inst != null) inst.notifyListeners(ev, o); } catch (Throwable t) { } }
    static void radioStopFromService() { radioMainS.post(new Runnable() { public void run() { radioReleaseNow(false); emit("radioStopped", new com.getcapacitor.JSObject()); } }); }
    private void radioService(boolean start) {
        try {
            Intent i = new Intent(getContext(), RadioService.class);
            if (start) { if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i); else getContext().startService(i); }
            else getContext().stopService(i);
        } catch (Throwable t) { }
    }
    @PluginMethod
    public void radioStatus(final PluginCall call) {
        radioMain.post(new Runnable() { public void run() {
            com.getcapacitor.JSObject o = new com.getcapacitor.JSObject();
            o.put("playing", radio != null && radio.getPlayWhenReady());
            call.resolve(o);
        } });
    }
    private int radioGainMb = 900; // +9 dB with a peak limiter: the stream is quiet (about -24 LUFS, peaks -6.5 dBFS)

    private static void radioReleaseFx() {
        if (radioLe != null) { try { radioLe.release(); } catch (Throwable t) { } radioLe = null; }
        if (radioDp != null) { try { radioDp.release(); } catch (Throwable t) { } radioDp = null; }
    }

    private void radioApplyBoost(int sid) {
        radioReleaseFx();
        if (sid == 0 || radioGainMb <= 0) return;
        // 1) gain with a peak limiter (Android 9+), so the louder signal does not clip
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION, 2, false, 0, false, 0, false, 0, true).build();
                DynamicsProcessing dp = new DynamicsProcessing(0, sid, cfg);
                dp.setInputGainAllChannelsTo(radioGainMb / 100f);
                dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, true, 0, 1f, 80f, 20f, -2f, 0f));
                dp.setEnabled(true);
                radioDp = dp;
                return;
            } catch (Throwable t) { radioDp = null; }
        }
        // 2) fallback: plain loudness enhancer, a smaller safe gain
        try {
            LoudnessEnhancer le = new LoudnessEnhancer(sid);
            le.setTargetGain(Math.min(radioGainMb, 600));
            le.setEnabled(true);
            radioLe = le;
        } catch (Throwable t) { radioLe = null; }
    }

    @PluginMethod
    public void radioGain(final PluginCall call) {
        final int g = call.getInt("gain", 0);
        radioMain.post(new Runnable() { public void run() {
            radioGainMb = Math.max(0, Math.min(1200, g));
            if (radio != null) radioApplyBoost(radio.getAudioSessionId());
            call.resolve();
        } });
    }
    private final Handler radioMain = new Handler(Looper.getMainLooper());

    private static void radioReleaseNow(boolean stopService) {
        radioReleaseFx();
        if (radio != null) {
            try { radio.stop(); } catch (Exception e) { }
            try { radio.release(); } catch (Exception e) { }
            radio = null;
        }
        if (stopService && inst != null) inst.radioService(false);
    }

    @PluginMethod
    public void radioStart(final PluginCall call) {
        final String url = call.getString("url", "");
        if (!url.startsWith("https://")) { call.reject("bad url"); return; }
        radioGainMb = Math.max(0, Math.min(1200, call.getInt("gain", 900)));
        radioMain.post(new Runnable() {
            public void run() {
                try {
                    radioReleaseNow(true);
                    final ExoPlayer p = new ExoPlayer.Builder(getContext()).build();
                    radio = p;
                    p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build());
                    p.setWakeMode(C.WAKE_MODE_NETWORK);
                    final boolean[] done = new boolean[1];
                    p.addListener(new Player.Listener() {
                        public void onIsPlayingChanged(boolean isPlaying) {
                            if (p != radio) return;
                            if (isPlaying && !done[0]) { done[0] = true; call.resolve(); return; }
                            if (done[0]) {
                                com.getcapacitor.JSObject o = new com.getcapacitor.JSObject();
                                o.put("playing", isPlaying);
                                emit("radioPlaying", o);
                            }
                        }
                        public void onAudioSessionIdChanged(int audioSessionId) {
                            if (p == radio) radioApplyBoost(audioSessionId);
                        }
                        public void onPlayerError(PlaybackException error) {
                            boolean mine = (p == radio);
                            if (mine) radioReleaseNow(true);
                            if (!done[0]) { done[0] = true; call.reject("stream error"); }
                            else if (mine) emit("radioStopped", new com.getcapacitor.JSObject());
                        }
                    });
                    p.setMediaItem(MediaItem.fromUri(url));
                    p.setPlayWhenReady(true);
                    p.prepare();
                    radioApplyBoost(p.getAudioSessionId());
                    radioService(true);
                } catch (Exception e) {
                    radioReleaseNow(true);
                    call.reject("radio failed");
                }
            }
        });
    }

    @PluginMethod
    public void radioStop(final PluginCall call) {
        radioMain.post(new Runnable() { public void run() { radioReleaseNow(true); call.resolve(); } });
    }

    @Override
    protected void handleOnDestroy() {
        // do NOT stop the radio here: it keeps playing in the background service until the user stops it
        if (inst == this) inst = null;
        super.handleOnDestroy();
    }
}
