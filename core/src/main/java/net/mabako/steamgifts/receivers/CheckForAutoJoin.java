package net.mabako.steamgifts.receivers;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;

import net.mabako.steamgifts.tasks.AutoJoinTask;

/**
 * Cyclomatic job to start the auto joining
 */
public class CheckForAutoJoin extends AbstractNotificationCheckReceiver {
    public static final long AUTO_JOIN_PERIOD = AlarmManager.INTERVAL_HALF_HOUR;

    @Override
    public void onReceive(final Context context, Intent intent) {
        new AutoJoinTask(context,AUTO_JOIN_PERIOD).execute();

    }


}
