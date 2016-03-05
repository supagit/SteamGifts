package net.mabako.steamgifts.tasks;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import net.mabako.Constants;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.data.Statistics;
import net.mabako.steamgifts.fragments.GiveawayDetailFragment;
import net.mabako.steamgifts.fragments.interfaces.IHasEnterableGiveaways;
import net.mabako.steamgifts.persistentdata.SavedGiveaways;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Task to perform the auto joining
 */
public class AutoJoinTask extends AsyncTask<Void, Void, Void> {
    private Context context;
    private long autoJoinPeriod;
    public static final String TAG = AutoJoinTask.class.getSimpleName();
    private String foundXsrfToken;
    private int points;
    private Statistics statistics;


    public AutoJoinTask(Context context, long autoJoinPeriod) {
        this.context = context;
        this.autoJoinPeriod = autoJoinPeriod;
    }

    private boolean isOption(AutoJoinOptions.AutoJoinOption option) {
        return AutoJoinOptions.isOptionBoolean(context, option);
    }

    protected Void doInBackground(Void... params) {
        boolean doAutoJoin = SteamGiftsUserData.getCurrent(context).isLoggedIn()
                && isOption(AutoJoinOptions.AutoJoinOption.AUTO_JOIN_ACTIVATED)
                && (isOption(AutoJoinOptions.AutoJoinOption.AUTO_JOIN_ON_NON_WIFI_CONNECTION) || Utils.isConnectedToWifi(TAG, context));

        statistics = new Statistics(context);

        if (doAutoJoin) {
            Set<Integer> bookmarkedGameIds = isOption(AutoJoinOptions.AutoJoinOption.ALWAYS_JOIN_GIVEAWAYS_FOR_BOOKMARKS) ? getBookMarkedGameIds() : new HashSet<Integer>();

            List<Giveaway> giveaways = loadGiveAways(context);
            if (giveaways != null) {
                List<Giveaway> filteredGiveaways = filterAndSortGiveaways(giveaways, bookmarkedGameIds);
                List<Giveaway> giveAwaysToJoin = calculateGiveawaysToJoin(filteredGiveaways, bookmarkedGameIds);

                if (giveAwaysToJoin.isEmpty()) {
                    showAutoJoinNotification(new HashMap<Giveaway, Boolean>());
                } else {
                    requestEnterLeave(giveAwaysToJoin, foundXsrfToken);
                }
            }
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

        int minPointsToKeepForBadRatio = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_POINTS_TO_KEEP_FOR_BAD_RATIO);
        int minPointsToKeepForGreatRatio = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_POINTS_TO_KEEP_FOR_GREAT_RATIO);
        int minPointsToKeep = (minPointsToKeepForBadRatio + minPointsToKeepForGreatRatio) / 2;

        int badRatio = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.BAD_RATIO);
        int greatRatio = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.GREAT_RATIO);

        int pointsLeft = points;

        List<Giveaway> result = new ArrayList<>();
        for (Giveaway giveaway : filteredGiveaways) {
            double ratio = AutoJoinUtils.calculateReadibleEntryRatio(context, giveaway);
            boolean shouldEnterGiveaway;
            int leftAfterJoin = pointsLeft - giveaway.getPoints();

            if (bookmarkedGameIds.contains(giveaway.getGameId())) {
                shouldEnterGiveaway = true;
            } else if (ratio>=greatRatio) {
                shouldEnterGiveaway = leftAfterJoin >= minPointsToKeepForGreatRatio;
            } else if (ratio<=badRatio) {
                shouldEnterGiveaway = leftAfterJoin >= minPointsToKeepForBadRatio;
            } else {
                shouldEnterGiveaway = leftAfterJoin >= minPointsToKeep;
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
                if (bookmarkedGameIds.contains(giveaway.getGameId()) || doesGiveawayEndWithInAutoJoinPeriod(giveaway)) {
                    result.add(giveaway);
                }
            }
        }

        Collections.sort(result, new Comparator<Giveaway>() {
            @Override
            public int compare(Giveaway lhs, Giveaway rhs) {
                return (int) (10000 * (AutoJoinUtils.calculateReadibleEntryRatio(context, rhs) - AutoJoinUtils.calculateReadibleEntryRatio(context, lhs)));
            }
        });
        return result;
    }

    private boolean doesGiveawayEndWithInAutoJoinPeriod(Giveaway giveaway) {
        final long realTimeDiff = Math.abs(new Date().getTime() - giveaway.getEndTime().getTimeInMillis());
        return realTimeDiff<= autoJoinPeriod;
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
                entries += giveaway.getEntries()/giveaway.getCopies();

            }
        }

        statistics.updateStatsNotification(giveawaysEntered, pointsSpent, entries);
    }

    protected List<Giveaway> loadGiveAways(Context context) {
        List<Giveaway> giveaways = loadGiveAways(context, 0);

        if (giveaways !=null) {
            int page = 1;
            while (doesGiveawayEndWithInAutoJoinPeriod(giveaways.get(giveaways.size() - 1)) && page < 5) {
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
            return Utils.loadGiveawaysFromList(document);
        } catch (Exception e) {
            Log.e(TAG, "Error fetching URL", e);
            return null;
        }
    }
}
