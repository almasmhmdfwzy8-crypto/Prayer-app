package __PKG__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Fired by the single salawat alarm. Hands off to AdhanService, the only place that plays the clip. */
public class SalawatReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Intent i = new Intent(ctx, AdhanService.class);
        i.setAction(AdhanService.ACTION_SAL);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception e) {
            // Could not start in the background (restricted by the system). Re-arm so the next slot still fires.
            AdhanService.scheduleNextSalawat(ctx);
        }
    }
}
