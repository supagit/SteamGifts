package net.mabako.steamgifts.receivers;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;

import net.mabako.steamgifts.tasks.DailyDropTask;

import java.util.Calendar;
import java.util.Date;

public class CheckDailyDrops extends AbstractNotificationCheckReceiver {
    public static final long FREQUENCE = AlarmManager.INTERVAL_HALF_HOUR / 3;

    @Override
    public void onReceive(final Context context, Intent intent) {
        int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hourOfDay < 8 || hourOfDay > 20) {
            return;
        }
        new DailyDropTask(context).execute();
    }


}
