package net.mabako.steamgifts.tasks;

import android.content.Context;

import net.mabako.Constants;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.persistentdata.SavedGameInfo;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AutoJoinGiveawayLoader
 */
public class AutoJoinGiveawayLoader {
    private String foundXsrfToken;
    private int curPage = 0;
    List<Giveaway> giveaways = new ArrayList<>();
    private Context context;
    private SavedGameInfo savedGameInfo;

    public AutoJoinGiveawayLoader(Context context, SavedGameInfo savedGameInfo) {
        this.context = context;
        this.savedGameInfo = savedGameInfo;
    }

    private boolean loadNextPage() {
        try {
            List<Giveaway> newGiveaways = loadGiveAways(context, curPage);
            curPage++;
            if (newGiveaways == null) {
                return false;
            }

            giveaways.addAll(newGiveaways);
            Map<String, Giveaway> giveawayMap = new HashMap<>();
            if (giveaways != null) {
                for (Giveaway giveaway : giveaways) {
                    giveawayMap.put(giveaway.getGiveawayId(), giveaway);
                }
            }

            giveaways = new ArrayList(giveawayMap.values());

            Collections.sort(giveaways, new Comparator<Giveaway>() {
                @Override
                public int compare(Giveaway lhs, Giveaway rhs) {
                    if (lhs.getEndTime() == null || rhs.getEndTime() == null) {
                        return lhs.getEndTime() == null ? -1 : 1;
                    }
                    return (int) (lhs.getEndTime().getTimeInMillis() - rhs.getEndTime().getTimeInMillis());
                }
            });
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    private List<Giveaway> loadGiveAways(Context context, int page) {
//        Log.d(TAG, "Fetching giveaways for page " + page);

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
//            Log.e(TAG, "Error fetching URL", e);
            return null;
        }
    }

    public String getFoundXsrfToken() {
        return foundXsrfToken;
    }

    public boolean loadWithInPeriod(long autoJoinPeriod) {
        while (true) {
            Giveaway last = getLast();
            if ((last != null && !endsWithinPeriod(last, autoJoinPeriod))) {
                return true;
            }
            if (last != null && curPage > 1) {
                return false;
            }
            loadNextPage();
        }
    }

    private boolean endsWithinPeriod(Giveaway giveaway, long period) {
        if (giveaway.getEndTime() == null) {
            return false;
        }
        return giveaway.getEndTime().getTimeInMillis() - System.currentTimeMillis() < period;
    }

    private Giveaway getLast() {
        if (giveaways.size() == 0) {
            return null;
        }
        return giveaways.get(giveaways.size() - 1);
    }

    public List<Giveaway> getGiveaways() {
        return giveaways;
    }
}
