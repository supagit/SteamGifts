package net.mabako.steamgifts.tasks;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import net.mabako.Constants;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.data.Statistics;
import net.mabako.steamgifts.fragments.GiveawayDetailFragment;
import net.mabako.steamgifts.fragments.interfaces.IHasEnterableGiveaways;
import net.mabako.steamgifts.persistentdata.SavedGameInfo;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task to perform the auto joining
 */
public class AutoJoinTask extends AsyncTask<Void, Void, Void> {
    private Context context;
    private boolean resetStats;
    private long autoJoinPeriod;
    public static final String TAG = AutoJoinTask.class.getSimpleName();
    private String foundXsrfToken;
    private Statistics statistics;
    private final SavedGameInfo savedGameInfo;

    AutoJoinCalculator autoJoinCalculator;
    private int points;


    public AutoJoinTask(Context context, long autoJoinPeriod) {
        this.context = context;
        this.autoJoinPeriod = autoJoinPeriod;
        savedGameInfo = new SavedGameInfo(context);
    }

    public AutoJoinTask(Context context, boolean resetStats) {
        this.context = context;
        this.resetStats = resetStats;
        savedGameInfo = new SavedGameInfo(context);
    }

    private boolean isOption(AutoJoinOptions.AutoJoinOption option) {
        return AutoJoinOptions.isOptionBoolean(context, option);
    }

    protected Void doInBackground(Void... params) {
        statistics = new Statistics(context);

        if (resetStats) {
            statistics.reset();
            statistics.updateStatsNotification(0, 0, 0);
            Toast.makeText(context, "Stats reset done", Toast.LENGTH_SHORT).show();
        } else {
            performAutoJoin();
        }
        return null;
    }

    private void performAutoJoin() {
        boolean doAutoJoin = SteamGiftsUserData.getCurrent(context).isLoggedIn()
                && isOption(AutoJoinOptions.AutoJoinOption.AUTO_JOIN_ACTIVATED)
                && (isOption(AutoJoinOptions.AutoJoinOption.AUTO_JOIN_ON_NON_WIFI_CONNECTION) || Utils.isConnectedToWifi(TAG, context));


        autoJoinCalculator = new AutoJoinCalculator(context, autoJoinPeriod);
        points = SteamGiftsUserData.getCurrent(context).getPoints();

        if (doAutoJoin) {
            statistics.updateStatsNotification("Auto Join in progress", "");

            List<Giveaway> giveaways = loadGiveAways(context);
            List<Giveaway> giveAwaysToJoin = autoJoinCalculator.calculateGiveawaysToJoin(giveaways);

            String suspensionText = SteamGiftsUserData.getCurrent(context).getSuspensionText();
            if (suspensionText != null) {
                statistics.updateStatsNotification("Suspended", suspensionText);
            } else if (giveAwaysToJoin.isEmpty()) {
                showAutoJoinNotification(new HashMap<Giveaway, Boolean>());
            } else {
                requestEnterLeave(giveAwaysToJoin, foundXsrfToken);
            }

        }
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
                    if (success) {
                        Log.v(TAG, "entered giveaway " + giveaway.getTitle());
                        Toast.makeText(context, "entered giveaway " + giveaway.getTitle(), Toast.LENGTH_SHORT).show();
                        statistics.addGiveaway(giveaway);

                    } else {
                        Toast.makeText(context, "failed to enter giveaway " + giveaway.getTitle(), Toast.LENGTH_SHORT).show();
                    }

                    giveawaysJoined.put(giveaway, success);
                    if (giveawaysJoined.size() == giveawaysToJoin.size()) {
                        showAutoJoinNotification(giveawaysJoined);
                    }
                }
            }, context, giveaway.getGiveawayId(), xsrfToken, GiveawayDetailFragment.ENTRY_INSERT);
            task.execute();
        }
    }

    private void showAutoJoinNotification(Map<Giveaway, Boolean> joinedGiveawayMap) {
        int pointsSpent = 0;
        int giveawaysEntered = 0;
        long entries = 0;

        for (Map.Entry<Giveaway, Boolean> entrySet : joinedGiveawayMap.entrySet()) {
            Giveaway giveaway = entrySet.getKey();
            Boolean success = entrySet.getValue();
            if (success) {
                giveawaysEntered++;
                pointsSpent += giveaway.getPoints();
                entries += giveaway.getEntries() / giveaway.getCopies();

            }
        }

        long newPoints = points - pointsSpent;
        SteamGiftsUserData.getCurrent(context).setPoints((int) newPoints);

        statistics.updateStatsNotification(giveawaysEntered, pointsSpent, entries);
    }

    protected List<Giveaway> loadGiveAways(Context context) {
        List<Giveaway> giveaways = loadGiveAways(context, 0);

        if (giveaways != null && !giveaways.isEmpty()) {
            int page = 1;
            while (autoJoinCalculator.doesGiveawayEndWithInAutoJoinPeriod(giveaways.get(giveaways.size() - 1)) && page < 5) {
                List<Giveaway> pageGiveaways = loadGiveAways(context, page);
                if (pageGiveaways == null) {
                    break;
                }
                page++;
                giveaways.addAll(pageGiveaways);
            }
        }

        return giveaways;
    }

    protected List<Giveaway> loadGiveAways(Context context, int page) {
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
            return Utils.loadGiveawaysFromList(document, savedGameInfo);
        } catch (Exception e) {
            Log.e(TAG, "Error fetching URL", e);
            return null;
        }
    }
}
