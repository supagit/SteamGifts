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
    public static final long TEN_MINUTES = AlarmManager.INTERVAL_HALF_HOUR / 3;
    public static final long FULL_AUTO_PERIOD = AUTO_JOIN_PERIOD + TEN_MINUTES;
    @Override
    public void onReceive(final Context context, Intent intent) {
        new AutoJoinTask(context,FULL_AUTO_PERIOD).execute();
    }


}
