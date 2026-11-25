package __PKG__;

import android.content.Context;
import android.content.Intent;
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
    private ExoPlayer radio;
    private final Handler radioMain = new Handler(Looper.getMainLooper());

    private void radioReleaseNow() {
        if (radio != null) {
            try { radio.stop(); } catch (Exception e) { }
            try { radio.release(); } catch (Exception e) { }
            radio = null;
        }
    }

    @PluginMethod
    public void radioStart(final PluginCall call) {
        final String url = call.getString("url", "");
        if (!url.startsWith("https://")) { call.reject("bad url"); return; }
        radioMain.post(new Runnable() {
            public void run() {
                try {
                    radioReleaseNow();
                    final ExoPlayer p = new ExoPlayer.Builder(getContext()).build();
                    radio = p;
                    p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build());
                    p.setWakeMode(C.WAKE_MODE_NETWORK);
                    final boolean[] done = new boolean[1];
                    p.addListener(new Player.Listener() {
                        public void onPlaybackStateChanged(int state) {
                            if (state == Player.STATE_READY && !done[0]) { done[0] = true; call.resolve(); }
                        }
                        public void onPlayerError(PlaybackException error) {
                            boolean mine = (p == radio);
                            if (mine) radioReleaseNow();
                            if (!done[0]) { done[0] = true; call.reject("stream error"); }
                            else if (mine) notifyListeners("radioStopped", new com.getcapacitor.JSObject());
                        }
                    });
                    p.setMediaItem(MediaItem.fromUri(url));
                    p.setPlayWhenReady(true);
                    p.prepare();
                } catch (Exception e) {
                    radioReleaseNow();
                    call.reject("radio failed");
                }
            }
        });
    }

    @PluginMethod
    public void radioStop(final PluginCall call) {
        radioMain.post(new Runnable() { public void run() { radioReleaseNow(); call.resolve(); } });
    }

    @Override
    protected void handleOnDestroy() {
        radioMain.post(new Runnable() { public void run() { radioReleaseNow(); } });
        super.handleOnDestroy();
    }
}
