package __PKG__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Fired by the single adhan alarm (next enabled prayer). Hands off to AdhanService, the only place that plays the adhan. */
public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Intent i = new Intent(ctx, AdhanService.class);
        i.setAction(AdhanService.ACTION_ADHAN);
        i.putExtra("at", intent.getLongExtra("at", 0));
        i.putExtra("idx", intent.getIntExtra("idx", 0));
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception e) {
            // could not start from the background: still notify (default sound) and re-arm so the schedule never stops
            AdhanService.postFallbackAdhan(ctx, intent.getIntExtra("idx", 0));
            AdhanService.scheduleNextAdhan(ctx, Math.max(intent.getLongExtra("at", 0), System.currentTimeMillis()));
        }
    }
}
