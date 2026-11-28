package __PKG__;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * Owns the Quran radio player. The player lives here (not in the activity) so the stream keeps
 * playing after the app is closed, the screen is locked, or the task is swiped away: the service
 * is a media-playback foreground service and returns START_STICKY, so Android restarts it and it
 * resumes the last stream on its own.
 */
public class RadioService extends Service {

    static final String ACTION_START = "__PKG__.RADIO_START";
    static final String ACTION_STOP = "__PKG__.RADIO_STOP";
    static final String ACTION_PAUSE = "__PKG__.RADIO_PAUSE";
    static final String ACTION_RESUME = "__PKG__.RADIO_RESUME";
    static final String EXTRA_URL = "url";
    static final String EXTRA_GAIN = "gain";

    private static final String CH = "quran_radio";
    private static final int NID = 7311;
    private static final String PREF = "radio-prefs";
    private static final String PREF_URL = "last_url";
    private static final String PREF_GAIN = "gain";

    private static ExoPlayer player;
    private static LoudnessEnhancer radioLe;
    private static DynamicsProcessing radioDp;
    private static int gainMb = 900;
    private static String currentUrl = "";
    private static boolean userStopped = false;
    private AudioManager am;
    private AudioFocusRequest focusRequest;
    private boolean focused = false;

    // ---------- called from the plugin ----------
    public static boolean isPlaying() {
        try { return player != null && player.getPlayWhenReady(); } catch (Throwable t) { return false; }
    }

    public static void start(Context c, String url, int gain) {
        gainMb = Math.max(0, Math.min(1200, gain));
        userStopped = false;
        Intent i = new Intent(c, RadioService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_GAIN, gainMb);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) {
        userStopped = true;
        try {
            // a plain startService: the stop path never calls startForeground, so it must not be
            // started through startForegroundService (that would fail the 5-second foreground rule)
            c.startService(new Intent(c, RadioService.class).setAction(ACTION_STOP));
        } catch (Throwable t) {
            releaseAll();
        }
    }

    public static void setGain(int g) {
        gainMb = Math.max(0, Math.min(1200, g));
        if (player != null) applyBoost(player.getAudioSessionId());
    }

    // ---------- service lifecycle ----------
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        makeChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            userStopped = true;
            releaseAll();
            abandonFocus();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            if (player != null) player.setPlayWhenReady(false);
            updateNotification();
            return START_STICKY;
        }
        if (ACTION_RESUME.equals(action)) {
            if (player != null) player.setPlayWhenReady(true);
            updateNotification();
            return START_STICKY;
        }

        String url;
        int gain;
        if (ACTION_START.equals(action) && intent != null && intent.hasExtra(EXTRA_URL)) {
            url = intent.getStringExtra(EXTRA_URL);
            gain = intent.getIntExtra(EXTRA_GAIN, gainMb);
        } else {
            // restarted by the system after being killed: resume what the user was listening to
            if (userStopped) { stopSelf(); return START_NOT_STICKY; }
            SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
            url = sp.getString(PREF_URL, "");
            gain = sp.getInt(PREF_GAIN, 900);
        }
        if (url == null || !url.startsWith("https://")) { stopSelf(); return START_NOT_STICKY; }

        gainMb = Math.max(0, Math.min(1200, gain));
        currentUrl = url;
        try {
            SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
            sp.edit().putString(PREF_URL, url).putInt(PREF_GAIN, gainMb).apply();
        } catch (Throwable t) { }

        play(url);
        startForegroundNow();
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // the task being swiped away must not stop the stream: keep the service running
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        // only release when the user stopped it; a system restart keeps the state
        if (userStopped) releaseAll();
        super.onDestroy();
    }

    // ---------- playback ----------
    private void play(String url) {
        releasePlayerOnly();
        try {
            final ExoPlayer p = new ExoPlayer.Builder(getApplicationContext()).build();
            player = p;
            p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build());
            p.setWakeMode(C.WAKE_MODE_NETWORK);
            p.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(), false);
            p.addListener(new Player.Listener() {
                @Override public void onIsPlayingChanged(boolean isPlaying) {
                    if (p != player) return;
                    updateNotification();
                    if (isPlaying) AdhanNotifierPlugin.radioEvent("playing", true);
                    else if (!p.getPlayWhenReady()) AdhanNotifierPlugin.radioEvent("paused", false);
                }
                @Override public void onAudioSessionIdChanged(int audioSessionId) {
                    if (p == player) applyBoost(audioSessionId);
                }
                @Override public void onPlayerError(PlaybackException error) {
                    if (p != player) return;
                    releasePlayerOnly();
                    AdhanNotifierPlugin.radioEvent("stopped", false);
                    stopForeground(true);
                    stopSelf();
                }
            });
            requestFocus();
            p.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
            p.setPlayWhenReady(true);
            p.prepare();
            applyBoost(p.getAudioSessionId());
        } catch (Throwable t) {
            releasePlayerOnly();
            AdhanNotifierPlugin.radioEvent("stopped", false);
            stopForeground(true);
            stopSelf();
        }
    }

    private static void releaseFx() {
        if (radioLe != null) { try { radioLe.release(); } catch (Throwable t) { } radioLe = null; }
        if (radioDp != null) { try { radioDp.release(); } catch (Throwable t) { } radioDp = null; }
    }

    private static void releasePlayerOnly() {
        releaseFx();
        if (player != null) {
            try { player.stop(); } catch (Throwable t) { }
            try { player.release(); } catch (Throwable t) { }
            player = null;
        }
    }

    private static void releaseAll() {
        releasePlayerOnly();
        currentUrl = "";
    }

    private static void applyBoost(int sid) {
        releaseFx();
        if (sid == 0 || gainMb <= 0) return;
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION, 2, false, 0, false, 0, false, 0, true).build();
                DynamicsProcessing dp = new DynamicsProcessing(0, sid, cfg);
                dp.setInputGainAllChannelsTo(gainMb / 100f);
                dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, true, 0, 1f, 80f, 20f, -2f, 0f));
                dp.setEnabled(true);
                radioDp = dp;
                return;
            } catch (Throwable t) { radioDp = null; }
        }
        try {
            LoudnessEnhancer le = new LoudnessEnhancer(sid);
            le.setTargetGain(Math.min(gainMb, 600));
            le.setEnabled(true);
            radioLe = le;
        } catch (Throwable t) { radioLe = null; }
    }

    // ---------- audio focus ----------
    private void requestFocus() {
        try {
            if (am == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                            @Override public void onAudioFocusChange(int change) {
                                if (player == null) return;
                                if (change == AudioManager.AUDIOFOCUS_LOSS) {
                                    player.setPlayWhenReady(false);
                                    AdhanNotifierPlugin.radioEvent("paused", false);
                                } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                    player.setPlayWhenReady(false);
                                } else if (change >= AudioManager.AUDIOFOCUS_GAIN) {
                                    player.setPlayWhenReady(true);
                                }
                            }
                        }).build();
                focused = am.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            } else {
                focused = am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                        == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
        } catch (Throwable t) { focused = false; }
    }

    private void abandonFocus() {
        try {
            if (am == null || !focused) return;
            if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) am.abandonAudioFocusRequest(focusRequest);
            else am.abandonAudioFocus(null);
        } catch (Throwable t) { }
        focused = false;
    }

    // ---------- notification ----------
    private void makeChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CH, "إذاعة القرآن الكريم", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        } catch (Throwable t) { }
    }

    private void startForegroundNow() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NID, n);
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NID, buildNotification());
        } catch (Throwable t) { }
    }

    /** The notification mirrors the in-app player: same title, a play/pause button and a stop button.
     *  While it plays it stays pinned; once paused or stopped it becomes swipeable. */
    private Notification buildNotification() {
        boolean playing = isPlaying();
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Intent toggle = new Intent(this, RadioService.class).setAction(playing ? ACTION_PAUSE : ACTION_RESUME);
        PendingIntent togglePi = PendingIntent.getService(this, 7314, toggle, fl);
        Intent closeI = new Intent(this, RadioService.class).setAction(ACTION_STOP);
        PendingIntent closePi = PendingIntent.getService(this, 7312, closeI, fl);
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent open = launch == null ? null : PendingIntent.getActivity(this, 7313, launch, fl);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_stat_prayer)
                .setContentTitle("إذاعة القرآن الكريم")
                .setContentText(playing ? "بث مباشر الآن" : "متوقفة مؤقتاً")
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setAutoCancel(!playing)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDeleteIntent(closePi)
                .addAction(0, playing ? "إيقاف مؤقت" : "تشغيل", togglePi)
                .addAction(0, "إنهاء", closePi);
        if (open != null) b.setContentIntent(open);
        return b.build();
    }
}
