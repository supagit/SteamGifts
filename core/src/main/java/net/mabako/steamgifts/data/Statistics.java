package net.mabako.steamgifts.data;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;

import net.mabako.steamgifts.core.R;
import net.mabako.steamgifts.receivers.AbstractNotificationCheckReceiver;
import net.mabako.steamgifts.tasks.AutoJoinTask;

import java.util.Calendar;
import java.util.Date;

/**
 * Statistics
 */
public class Statistics {
    long dailyPointsSpent = 0;
    long dailyGiveawaysEntered = 0;
    long dailyEntries = 0;
    long overallPointsSpent = 0;
    long overallGiveawaysEntered = 0;
    long overallEntries = 0;
    long lastAutoJoin = 0;

    private Context context;

    public Statistics(Context context) {
        this.context = context;
        load();
    }

    private void load() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(AutoJoinTask.TAG, Context.MODE_PRIVATE);
        lastAutoJoin = sharedPreferences.getLong("lastAutoJoin", lastAutoJoin);
        dailyGiveawaysEntered = sharedPreferences.getLong("dailyGiveawaysEntered", dailyGiveawaysEntered);
        dailyPointsSpent = sharedPreferences.getLong("dailyPointsSpent", dailyPointsSpent);
        dailyEntries = sharedPreferences.getLong("dailyEntries", dailyEntries);
        overallGiveawaysEntered = sharedPreferences.getLong("overallGiveawaysEntered", overallGiveawaysEntered);
        overallPointsSpent = sharedPreferences.getLong("overallPointsSpent", overallPointsSpent);
        overallEntries = sharedPreferences.getLong("overallEntries", overallEntries);
    }

    private void save() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(AutoJoinTask.TAG, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        lastAutoJoin = new Date().getTime();
        editor.putLong("lastAutoJoin", lastAutoJoin);
        editor.putLong("dailyGiveawaysEntered", dailyGiveawaysEntered);
        editor.putLong("dailyPointsSpent", dailyPointsSpent);
        editor.putLong("dailyEntries", dailyEntries);
        editor.putLong("overallGiveawaysEntered", overallGiveawaysEntered);
        editor.putLong("overallPointsSpent", overallPointsSpent);
        editor.putLong("overallEntries", overallEntries);
        editor.apply();
    }

    public boolean isNewDay() {
        Date lastDate = new Date(lastAutoJoin);
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(lastDate);
        int day = cal.get(Calendar.DAY_OF_YEAR);
        cal.setTime(now);
        int today = cal.get(Calendar.DAY_OF_YEAR);
        return today != day;
    }

    public void addGiveaway(Giveaway giveaway) {
        dailyGiveawaysEntered++;
        dailyPointsSpent += giveaway.getPoints();
        dailyEntries += giveaway.getEntries();
        overallGiveawaysEntered++;
        overallPointsSpent += giveaway.getPoints();
        overallEntries += giveaway.getEntries();
        save();
    }

    public void showDailyStatsNotification() {
        String title = "Entered: " + overallGiveawaysEntered + " Spent: " + overallPointsSpent;
        String content = "Today Entered: " + dailyGiveawaysEntered + " Spent: " + dailyPointsSpent;

        android.app.Notification.Builder builder = new Notification.Builder(context);
        builder.setContentTitle("Daily Stats")
                .setContentText(content)
                .setSmallIcon(R.drawable.sgwhite).setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH);

        Notification notification = new Notification.BigTextStyle(builder).bigText(title + "\n" + content).build();

        int notificationId = AbstractNotificationCheckReceiver.NotificationId.AUTO_JOIN_STATS.ordinal();
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(notificationId, notification);
    }

    public void showDailyStatsNotificationIfNewDay() {
        if (isNewDay())
        {
            showDailyStatsNotification();
            dailyGiveawaysEntered = 0;
            dailyPointsSpent = 0;
            dailyEntries = 0;
            save();
        }
    }
}
