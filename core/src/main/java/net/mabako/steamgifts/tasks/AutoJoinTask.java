package net.mabako.steamgifts.tasks;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import net.mabako.Constants;
import net.mabako.steamgifts.core.R;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.fragments.GiveawayDetailFragment;
import net.mabako.steamgifts.fragments.interfaces.IHasEnterableGiveaways;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task to perform the auto joining
 */
public class AutoJoinTask extends AsyncTask<Void, Void, Void> {
    private Context context;
    private long autoJoinPeriod;
    private static final String TAG = AutoJoinTask.class.getSimpleName();
    private String foundXsrfToken;

    public static final int MINIMUM_POINTS_TO_KEEP = 100;
    public static final int MINIMUM_POINTS_TO_KEEP_FOR_BAD_RATIO = 150;
    public static final int MINIMUM_POINTS_TO_KEEP_FOR_GREAT_RATIO = 50;

    private int points;

    public AutoJoinTask(Context context, long autoJoinPeriod) {
        this.context = context;
        this.autoJoinPeriod = autoJoinPeriod;
    }

    protected Void doInBackground(Void... params) {
        if (SteamGiftsUserData.getCurrent(context).isLoggedIn()) {
            List<Giveaway> giveaways = loadGiveAways(context);
            List<Giveaway> filteredGiveaways = filterAndSortGiveaways(giveaways);
            List<Giveaway> giveAwaysToJoin = calculateGiveawaysToJoin(filteredGiveaways);

            requestEnterLeave(giveAwaysToJoin, foundXsrfToken);
        }
        return null;
    }

    private List<Giveaway> calculateGiveawaysToJoin(List<Giveaway> filteredGiveaways) {
        points = SteamGiftsUserData.getCurrent(context).getPoints();

        int pointsLeft = points;

        List<Giveaway> result = new ArrayList<>();
        for (Giveaway giveaway : filteredGiveaways) {
            double ratio = giveaway.getReadibleEntryRatio();
            boolean shouldEnterGiveaway = false;
            if (pointsLeft - giveaway.getPoints() >= MINIMUM_POINTS_TO_KEEP) {
                shouldEnterGiveaway = true;
                //when ratio is bad and we don't have to spent
                //do not spent points when factor is too small and we are too sharp at the bottom
                if (ratio < 1 && pointsLeft - MINIMUM_POINTS_TO_KEEP < MINIMUM_POINTS_TO_KEEP_FOR_BAD_RATIO) {
                    shouldEnterGiveaway = false;
                }
            }

            //if ratio is too good, we make an exception
            if (ratio > 3 && pointsLeft - giveaway.getPoints() >= MINIMUM_POINTS_TO_KEEP_FOR_GREAT_RATIO) {
                shouldEnterGiveaway = true;
            }

            if (shouldEnterGiveaway) {
                result.add(giveaway);
                pointsLeft -= giveaway.getPoints();
            }
        }

        return result;
    }

    private List<Giveaway> filterAndSortGiveaways(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();
        for (Giveaway giveaway : giveaways) {
            if (!giveaway.isEntered() && !giveaway.isLevelNegative()) {
                final long realTimeDiff = Math.abs(Calendar.getInstance().getTimeInMillis() - giveaway.getEndTime().getTimeInMillis());
                if (realTimeDiff <= autoJoinPeriod) {
                    result.add(giveaway);
                }
            }
        }
        Collections.sort(result, new Comparator<Giveaway>() {
            @Override
            public int compare(Giveaway lhs, Giveaway rhs) {
                return (int) (10000 * (rhs.getEntryRatio() - lhs.getEntryRatio()));
            }
        });
        return result;
    }

    public void requestEnterLeave(final List<Giveaway> giveawaysToJoin, String xsrfToken) {
        final Map<Giveaway, Boolean> giveawaysJoined = new HashMap<>();

        for (final Giveaway giveaway : giveawaysToJoin) {
            EnterLeaveGiveawayTask task = new EnterLeaveGiveawayTask(new IHasEnterableGiveaways() {
                @Override
                public void requestEnterLeave(String giveawayId, String what, String xsrfToken) {
                }

                @Override
                public void onEnterLeaveResult(String giveawayId, String what, Boolean success, boolean propagate) {
                    giveawaysJoined.put(giveaway, success);
                    if (giveawaysJoined.size() == giveawaysToJoin.size()) {
                        showNotification(context, giveawaysJoined);
                    }

                    if (success) {
                        Log.v(TAG, "entered giveaway " + giveaway.getTitle());
                        Toast.makeText(context, "entered giveaway " + giveaway.getTitle(), Toast.LENGTH_SHORT).show();

                    } else {
                        Toast.makeText(context, "failed to enter giveaway " + giveaway.getTitle(), Toast.LENGTH_SHORT).show();
                    }
                }
            }, context, giveaway.getGiveawayId(), xsrfToken, GiveawayDetailFragment.ENTRY_INSERT);
            task.execute();
        }
    }

    private void showNotification(Context context, Map<Giveaway, Boolean> joinedGiveawayMap) {
        int pointsSpent = 0;
        int giveawaysEntered = 0;
        int giveawaysEnteringFailed = 0;

        for (Map.Entry<Giveaway, Boolean> entrySet : joinedGiveawayMap.entrySet()) {
            Giveaway giveaway = entrySet.getKey();
            Boolean success = entrySet.getValue();
            if (success) {
                giveawaysEntered++;
                pointsSpent += giveaway.getPoints();

            } else {
                giveawaysEnteringFailed++;
            }
        }
        int pointsLeft = points - pointsSpent;

        String title = "Entered Giveaways " + giveawaysEntered;
        if (giveawaysEnteringFailed != 0) {
            title += " Failed: " + giveawaysEnteringFailed;
        }

        String content = "Points spent: " + pointsSpent + " left: " + pointsLeft;

        Notification notification = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.sgwhite)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setAutoCancel(true)
                .build();

        int notificationId = (int) System.currentTimeMillis();
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(notificationId, notification);
    }

    protected List<Giveaway> loadGiveAways(Context context) {
        int page = 0;
        Log.d(TAG, "Fetching giveaways for page " + page);

        try {
            // Fetch the Giveaway page
            Connection jsoup = Jsoup.connect("http://www.steamgifts.com/giveaways/search")
                    .userAgent(Constants.JSOUP_USER_AGENT)
                    .timeout(Constants.JSOUP_TIMEOUT);
            jsoup.data("page", Integer.toString(page));

            if (SteamGiftsUserData.getCurrent(context).isLoggedIn())
                jsoup.cookie("PHPSESSID", SteamGiftsUserData.getCurrent(context).getSessionId());
            Document document = jsoup.get();

            SteamGiftsUserData.extract(context, document);

            // Fetch the xsrf token
            Element xsrfToken = document.select("input[name=xsrf_token]").first();
            if (xsrfToken != null)
                foundXsrfToken = xsrfToken.attr("value");

            // Do away with pinned giveaways.
            document.select(".pinned-giveaways__outer-wrap").html("");

            // Parse all rows of giveaways
            return Utils.loadGiveawaysFromList(document);
        } catch (Exception e) {
            Log.e(TAG, "Error fetching URL", e);
            return null;
        }
    }
}
