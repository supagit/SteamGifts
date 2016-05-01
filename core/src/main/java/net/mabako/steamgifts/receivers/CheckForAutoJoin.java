package net.mabako.steamgifts.receivers;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;

import net.mabako.steamgifts.tasks.AutoJoinTask;
import net.mabako.steamgifts.tasks.Utils;

/**
 * Cyclomatic job to start the auto joining
 */
public class CheckForAutoJoin extends AbstractNotificationCheckReceiver {
    public static final long AUTO_JOIN_FREQUENCE = AlarmManager.INTERVAL_HALF_HOUR;
    public static final long AUTO_JOIN_PERIOD = AlarmManager.INTERVAL_HOUR * 2;
    @Override
    public void onReceive(final Context context, Intent intent) {
        long interval = AUTO_JOIN_PERIOD;
        if (!Utils.isConnectedToWifi("CheckForAutoJoin", context)) {
            interval = AlarmManager.INTERVAL_HOUR;
        }

        new AutoJoinTask(context, interval).execute();
}


}
