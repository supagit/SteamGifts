package net.mabako.steamgifts.tasks;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import net.mabako.Constants;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.data.Rating;
import net.mabako.steamgifts.fragments.GiveawayListFragment;
import net.mabako.steamgifts.persistentdata.FilterData;
import net.mabako.steamgifts.persistentdata.SavedGameInfo;
import net.mabako.steamgifts.persistentdata.SavedRatings;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LoadGiveawayListTask extends AsyncTask<Void, Void, List<Giveaway>> {
    private static final String TAG = LoadGiveawayListTask.class.getSimpleName();

    private final GiveawayListFragment fragment;
    private final int page;
    private final GiveawayListFragment.Type type;
    private final String searchQuery;
    private final boolean showPinnedGiveaways;

    private String foundXsrfToken = null;
    private final SavedGameInfo savedGameInfo;

    public LoadGiveawayListTask(GiveawayListFragment activity, int page, GiveawayListFragment.Type type, String searchQuery, boolean showPinnedGiveaways) {
        this.fragment = activity;
        savedGameInfo = new SavedGameInfo(activity.getContext());
        this.page = page;
        this.type = type;
        this.searchQuery = searchQuery;
        this.showPinnedGiveaways = showPinnedGiveaways && type == GiveawayListFragment.Type.ALL && TextUtils.isEmpty(searchQuery);
    }

    @Override
    protected List<Giveaway> doInBackground(Void... params) {
        Log.d(TAG, "Fetching giveaways for page " + page);

        try {
            // Fetch the Giveaway page

            Connection jsoup = Jsoup.connect("http://www.steamgifts.com/giveaways/search")
                    .userAgent(Constants.JSOUP_USER_AGENT)
                    .timeout(Constants.JSOUP_TIMEOUT);
            jsoup.data("page", Integer.toString(page));

            if (searchQuery != null)
                jsoup.data("q", searchQuery);

            FilterData filterData = FilterData.getCurrent(fragment.getContext());
            if (!filterData.isEntriesPerCopy()) {
                addFilterParameter(jsoup, "entry_max", filterData.getMaxEntries());
                addFilterParameter(jsoup, "entry_min", filterData.getMinEntries());
            }
            if (!filterData.isRestrictLevelOnlyOnPublicGiveaways()) {
                addFilterParameter(jsoup, "level_min", filterData.getMinLevel());
                addFilterParameter(jsoup, "level_max", filterData.getMaxLevel());
            }

            if (type != GiveawayListFragment.Type.ALL)
                jsoup.data("type", type.name().toLowerCase(Locale.ENGLISH));

            if (SteamGiftsUserData.getCurrent(fragment.getContext()).isLoggedIn())
                jsoup.cookie("PHPSESSID", SteamGiftsUserData.getCurrent(fragment.getContext()).getSessionId());
            Document document = jsoup.get();

            SteamGiftsUserData.extract(fragment.getContext(), document);

            // Fetch the xsrf token
            Element xsrfToken = document.select("input[name=xsrf_token]").first();
            if (xsrfToken != null)
                foundXsrfToken = xsrfToken.attr("value");

            // Do away with pinned giveaways.
            if (!showPinnedGiveaways)
                document.select(".pinned-giveaways__outer-wrap").html("");

            // Parse all rows of giveaways
            return Utils.loadGiveawaysFromList(document, savedGameInfo);
        } catch (Exception e) {
            Log.e(TAG, "Error fetching URL", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(List<Giveaway> result) {
        super.onPostExecute(result);
        fragment.addItems(result, page == 1, foundXsrfToken);
    }

    private void addFilterParameter(Connection jsoup, String parameterName, int value) {
        if (value >= 0)
            jsoup.data(parameterName, String.valueOf(value));
    }
}
