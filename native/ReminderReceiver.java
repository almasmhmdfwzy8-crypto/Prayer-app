package __PKG__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Fired by one reminder alarm. Hands off to ReminderService, the only place that plays the sound. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int idx = intent == null ? 0 : intent.getIntExtra("idx", 0);
        Intent i = new Intent(ctx, ReminderService.class);
        i.setAction(ReminderService.ACTION_FIRE);
        i.putExtra("idx", idx);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception e) {
            // the system refused a background start: still re-arm so the schedule never stops
            ReminderService.scheduleOne(ctx, idx);
        }
    }
}
