package net.mabako.steamgifts.tasks;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import net.mabako.Constants;
import net.mabako.steamgifts.core.R;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.fragments.GiveawayDetailFragment;
import net.mabako.steamgifts.fragments.interfaces.IHasEnterableGiveaways;
import net.mabako.steamgifts.persistentdata.SavedGiveaways;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Task to perform the auto joining
 */
public class AutoJoinTask extends AsyncTask<Void, Void, Void> {
    enum AutoJoinOption {
        AUTO_JOIN_ACTIVATED,
        ALWAYS_JOIN_GIVEAWAYS_FOR_BOOKMARKS,
        AUTO_JOIN_ON_NON_WIFI_CONNECTION
    }

    public static final int MINIMUM_POINTS_TO_KEEP = 100;
    public static final int MINIMUM_POINTS_TO_KEEP_FOR_BAD_RATIO = 150;
    public static final int MINIMUM_POINTS_TO_KEEP_FOR_GREAT_RATIO = 50;
    public static final double BAD_RATIO = 1.5;
    public static final double GREAT_RATIO = 3.0;

    private Context context;
    private long autoJoinPeriod;
    private static final String TAG = AutoJoinTask.class.getSimpleName();
    private String foundXsrfToken;
    private int points;

    public AutoJoinTask(Context context, long autoJoinPeriod) {
        this.context = context;
        this.autoJoinPeriod = autoJoinPeriod;
    }

    //TODO read options from settings
    private boolean isOption(AutoJoinOption option) {
        switch (option) {
            case AUTO_JOIN_ACTIVATED:
                return true;
            case ALWAYS_JOIN_GIVEAWAYS_FOR_BOOKMARKS:
                return true;
            case AUTO_JOIN_ON_NON_WIFI_CONNECTION:
                return true;
        }
        return false;
    }

    protected Void doInBackground(Void... params) {
        boolean doAutoJoin = SteamGiftsUserData.getCurrent(context).isLoggedIn()
                && isOption(AutoJoinOption.AUTO_JOIN_ACTIVATED)
                && (isOption(AutoJoinOption.AUTO_JOIN_ON_NON_WIFI_CONNECTION) || Utils.isConnectedToWifi(TAG, context));

        if (doAutoJoin) {
            Set<Integer> bookmarkedGameIds = isOption(AutoJoinOption.ALWAYS_JOIN_GIVEAWAYS_FOR_BOOKMARKS) ? getBookMarkedGameIds() : new HashSet<Integer>();

            List<Giveaway> giveaways = loadGiveAways(context);
            List<Giveaway> filteredGiveaways = filterAndSortGiveaways(giveaways, bookmarkedGameIds);
            List<Giveaway> giveAwaysToJoin = calculateGiveawaysToJoin(filteredGiveaways, bookmarkedGameIds);

            requestEnterLeave(giveAwaysToJoin, foundXsrfToken);

        }

        return null;
    }

    @NonNull
    private Set<Integer> getBookMarkedGameIds() {
        Set<Integer> bookmarkedGameIds = new HashSet<>();
        SavedGiveaways savedGiveaways = new SavedGiveaways(context);
        for (Giveaway giveaway : savedGiveaways.all()) {
            bookmarkedGameIds.add(giveaway.getGameId());
        }
        savedGiveaways.close();
        return bookmarkedGameIds;
    }

    private List<Giveaway> calculateGiveawaysToJoin(List<Giveaway> filteredGiveaways, Set<Integer> bookmarkedGameIds) {
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
                if (ratio <= BAD_RATIO && pointsLeft - MINIMUM_POINTS_TO_KEEP < MINIMUM_POINTS_TO_KEEP_FOR_BAD_RATIO) {
                    shouldEnterGiveaway = false;
                }
            }

            //if ratio is too good, we make an exception
            if (ratio >= GREAT_RATIO && pointsLeft - giveaway.getPoints() >= MINIMUM_POINTS_TO_KEEP_FOR_GREAT_RATIO) {
                shouldEnterGiveaway = true;
            }

            if (bookmarkedGameIds.contains(giveaway.getGameId())) {
                shouldEnterGiveaway = true;
            }

            if (shouldEnterGiveaway) {
                result.add(giveaway);
                pointsLeft -= giveaway.getPoints();
            }
        }

        return result;
    }

    private List<Giveaway> filterAndSortGiveaways(List<Giveaway> giveaways, Set<Integer> bookmarkedGameIds) {
        List<Giveaway> result = new ArrayList<>();
        for (Giveaway giveaway : giveaways) {
            if (!giveaway.isEntered() && !giveaway.isLevelNegative() && !SteamGiftsUserData.getCurrent(context).getName().equals(giveaway.getCreator())) {
                final long realTimeDiff = Math.abs(Calendar.getInstance().getTimeInMillis() - giveaway.getEndTime().getTimeInMillis());
                if (bookmarkedGameIds.contains(giveaway.getGameId()) || realTimeDiff <= autoJoinPeriod) {
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
