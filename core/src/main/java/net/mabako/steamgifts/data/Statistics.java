package net.mabako.steamgifts.data;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.google.gson.Gson;

import net.mabako.steamgifts.activities.MainActivity;
import net.mabako.steamgifts.core.R;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;
import net.mabako.steamgifts.receivers.AbstractNotificationCheckReceiver;
import net.mabako.steamgifts.tasks.AutoJoinTask;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Statistics
 */
public class Statistics {
    long todayPointsSpent = 0;
    long todayEntered = 0;
    long todayEntries = 0;
    long overallPointsSpent = 0;
    long overallGiveawaysEntered = 0;
    long overallEntries = 0;
    long lastAutoJoin = 0;
    long dayCount = 0;
    boolean isDirty = false;

    private Context context;
    private Paint mPaint;

    public Statistics(Context context) {
        this.context = context;
        mPaint = new Paint();
        mPaint.setTypeface(Typeface.DEFAULT);
        load();
//        reset();
    }

    public void reset() {
        todayPointsSpent = 0;
        todayEntered = 0;
        todayEntries = 0;
        overallPointsSpent = 0;
        overallGiveawaysEntered = 0;
        overallEntries = 0;
        lastAutoJoin = 0;
        dayCount = 0;
        save();
    }

    private void load() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(AutoJoinTask.TAG, Context.MODE_PRIVATE);
        lastAutoJoin = sharedPreferences.getLong("lastAutoJoin", lastAutoJoin);
        dayCount = sharedPreferences.getLong("dayCount", dayCount);
        todayEntered = sharedPreferences.getLong("todayEntered", todayEntered);
        todayPointsSpent = sharedPreferences.getLong("todayPointsSpent", todayPointsSpent);
        todayEntries = sharedPreferences.getLong("todayEntries", todayEntries);
        overallGiveawaysEntered = sharedPreferences.getLong("overallGiveawaysEntered", overallGiveawaysEntered);
        overallPointsSpent = sharedPreferences.getLong("overallPointsSpent", overallPointsSpent);
        overallEntries = sharedPreferences.getLong("overallEntries", overallEntries);
        isDirty = false;
    }

    private void save() {
        if (isNewDay()) {
            todayEntered = 0;
            todayPointsSpent = 0;
            todayEntries = 0;
            dayCount++;
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences(AutoJoinTask.TAG, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        lastAutoJoin = new Date().getTime();
        editor.putLong("lastAutoJoin", lastAutoJoin);
        editor.putLong("dayCount", dayCount);
        editor.putLong("todayEntered", todayEntered);
        editor.putLong("todayPointsSpent", todayPointsSpent);
        editor.putLong("todayEntries", todayEntries);
        editor.putLong("overallGiveawaysEntered", overallGiveawaysEntered);
        editor.putLong("overallPointsSpent", overallPointsSpent);
        editor.putLong("overallEntries", overallEntries);

        editor.apply();
        isDirty = false;
    }

    private boolean isNewDay() {
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
        todayEntered++;
        todayPointsSpent += giveaway.getPoints();
        todayEntries += giveaway.getEstimatedEntries() / giveaway.getCopies();
        overallGiveawaysEntered++;
        overallPointsSpent += giveaway.getPoints();
        overallEntries += giveaway.getEstimatedEntries() / giveaway.getCopies();
        save();
    }

    public void removeGiveaway(Giveaway giveaway) {
        todayEntered--;
        todayPointsSpent -= giveaway.getPoints();
        todayEntries -= giveaway.getEntries() / giveaway.getCopies();
        overallGiveawaysEntered--;
        overallPointsSpent -= giveaway.getPoints();
        overallEntries -= giveaway.getEntries() / giveaway.getCopies();
        save();
    }

    public void updateStatsNotification(int giveawaysEntered, int pointsSpent, long entries) {
        String title = "Stats - Points Left: " + SteamGiftsUserData.getCurrent(context).getPoints();
        String content = "Just Entered: " + giveawaysEntered + " Spent: " + pointsSpent;

        long avEntries = giveawaysEntered != 0 ? entries / giveawaysEntered : 0;
        long avTodayEntries = todayEntered != 0 ? todayEntries / todayEntered : 0;
        long avOverallEntries = overallGiveawaysEntered != 0 ? overallEntries / overallGiveawaysEntered : 0;

        long avPoints = giveawaysEntered != 0 ? pointsSpent / giveawaysEntered : 0;
        long avTodayPoints = todayEntered != 0 ? todayPointsSpent / todayEntered : 0;
        long avOverallPoints = overallGiveawaysEntered != 0 ? overallPointsSpent / overallGiveawaysEntered : 0;

        long dailyEntered = dayCount != 0 ? overallGiveawaysEntered / dayCount : 0;
        long dailyPointsSpent = dayCount != 0 ? overallPointsSpent / dayCount : 0;

        List<String> lines = new ArrayList<>();
        lines.add(makeRow("Stats", "Last", "Today", "Daily", "Overall"));
        lines.add(makeRow("Entered", giveawaysEntered, todayEntered, dailyEntered, overallGiveawaysEntered));
        lines.add(makeRow("Points", pointsSpent, todayPointsSpent, dailyPointsSpent, overallPointsSpent));
        lines.add(makeRow("Av Entries", avEntries, avTodayEntries, "-", avOverallEntries));
        lines.add(makeRow("Av Points", avPoints, avTodayPoints, "-", avOverallPoints));

        android.app.Notification.Builder builder = new Notification.Builder(context);

        int notificationId = AbstractNotificationCheckReceiver.NotificationId.AUTO_JOIN_STATS.ordinal();

        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, notificationId, notificationIntent, 0);
        builder.setContentIntent(intent);

        builder.setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.sgwhite)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH);

        Notification.InboxStyle inboxStyle = new Notification.InboxStyle(builder);

        for (String line : lines) {
            inboxStyle.addLine(line);
        }

        Notification notification = inboxStyle.build();

        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(notificationId, notification);
    }

    private String makeRow(Object... vals) {
        StringBuilder sb = new StringBuilder();

        float desiredWidth = 0;

        for (int i = 0; i < vals.length; i++) {
            float currentWidth = getTextWidth(sb.toString());
            float correcture = currentWidth - desiredWidth;

            float elementWidth = getWidth(i);
            desiredWidth += elementWidth;

            sb.append(makeText(vals[i], elementWidth - correcture));
        }
        return sb.toString();
    }

    private int getWidth(int column) {
        switch(column){
            case 0:
                return 50;
            case 1:
                return 30;
            case 4:
                return 50;
            default:
                return 40;
        }
    }

    private float getTextWidth(String text) {
        return mPaint.measureText(text, 0, text.length());
    }

    private String makeText(Object text, float maxTextWidth) {
        String curText = text.toString();
        boolean toggle = false;
        while (true) {
            float w = getTextWidth(curText);
            if (w >= maxTextWidth) {
                return curText;
            }
            if (toggle) {
                curText = " " + curText;
            } else {
                curText = curText + " ";
            }
            toggle = !toggle;
        }
    }



}
