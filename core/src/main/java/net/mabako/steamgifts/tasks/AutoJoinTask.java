package net.mabako.steamgifts.tasks;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import net.mabako.Constants;
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
import java.util.List;

/**
 * Task to perform the auto joining
 */
public class AutoJoinTask extends AsyncTask<Void, Void, Void> {
    private Context context;
    private long autoJoinPeriod;
    private static final String TAG = AutoJoinTask.class.getSimpleName();
    private String foundXsrfToken;

    public static final int MINIMUM_POINTS_TO_KEEP = 100;

    public AutoJoinTask(Context context, long autoJoinPeriod) {
        this.context = context;
        this.autoJoinPeriod = autoJoinPeriod;
    }

    protected Void doInBackground(Void... params) {
        if (SteamGiftsUserData.getCurrent(context).isLoggedIn()) {
            List<Giveaway> giveaways = loadGiveAways(context);
            List<Giveaway> filteredGiveaways = filterAndSortGiveaways(giveaways);
            List<Giveaway> giveAwaysToJoin = calculateGiveawaysToJoin(filteredGiveaways);

            for (Giveaway giveaway : giveAwaysToJoin) {
                requestEnterLeave(giveaway, foundXsrfToken);
            }
        }
        return null;
    }

    private List<Giveaway> calculateGiveawaysToJoin(List<Giveaway> filteredGiveaways) {
        int points = SteamGiftsUserData.getCurrent(context).getPoints();

        int pointsToSpent = points - MINIMUM_POINTS_TO_KEEP;

        List<Giveaway> result = new ArrayList<>();
        for (Giveaway giveaway : filteredGiveaways) {
            if (giveaway.getPoints()<=pointsToSpent) {
                result.add(giveaway);
                pointsToSpent -= giveaway.getPoints();
            }
        }

        return result;
    }

    private List<Giveaway> filterAndSortGiveaways(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();
        for (Giveaway giveaway : giveaways) {
            if (!giveaway.isEntered() && !giveaway.isLevelNegative()) {
                final long realTimeDiff = Math.abs(Calendar.getInstance().getTimeInMillis() - giveaway.getEndTime().getTimeInMillis());
                if (realTimeDiff<=autoJoinPeriod) {
                    result.add(giveaway);
                }
            }
        }
        Collections.sort(result, new Comparator<Giveaway>() {
            @Override
            public int compare(Giveaway lhs, Giveaway rhs) {
                return (int)(10000 * (rhs.getEntryRatio() - lhs.getEntryRatio()));
            }
        });
        return result;
    }

    public void requestEnterLeave(final Giveaway giveaway, String xsrfToken) {
        EnterLeaveGiveawayTask task = new EnterLeaveGiveawayTask(new IHasEnterableGiveaways() {
            @Override
            public void requestEnterLeave(String giveawayId, String what, String xsrfToken) {
            }

            @Override
            public void onEnterLeaveResult(String giveawayId, String what, Boolean success, boolean propagate) {
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
