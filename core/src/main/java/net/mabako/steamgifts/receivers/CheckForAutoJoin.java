package net.mabako.steamgifts.receivers;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import net.mabako.Constants;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.fragments.GiveawayDetailFragment;
import net.mabako.steamgifts.fragments.interfaces.IHasEnterableGiveaways;
import net.mabako.steamgifts.persistentdata.FilterData;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;
import net.mabako.steamgifts.tasks.EnterLeaveGiveawayTask;
import net.mabako.steamgifts.tasks.Utils;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;

/**
 * Created by Supa on 27.02.2016.
 */
public class CheckForAutoJoin extends AbstractNotificationCheckReceiver {
    private static final String TAG = CheckForAutoJoin.class.getSimpleName();
    private static boolean running = false;
    private Context context;
    private String foundXsrfToken;

    @Override
    public void onReceive(final Context context, Intent intent) {
        this.context = context;

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (running) {
                    return;
                }
                running = true;
                List<Giveaway> giveaways = loadGiveAways(context);


                int points = SteamGiftsUserData.getCurrent(context).getPoints();
//                requestEnterLeave(giveaways.get(0),foundXsrfToken);
//                requestEnterLeave(giveaways.get(1),foundXsrfToken);
                for (Giveaway giveA : giveaways) {

                }
                running = false;
            }
        }).start();
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
                    Toast.makeText(context, "failed to enter giveaway " + giveaway.getTitle(),Toast.LENGTH_SHORT).show();
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

//            if (searchQuery != null)
//                jsoup.data("q", searchQuery);

            FilterData filterData = FilterData.getCurrent(context);
            if (!filterData.isEntriesPerCopy()) {
                addFilterParameter(jsoup, "entry_max", filterData.getMaxEntries());
                addFilterParameter(jsoup, "entry_min", filterData.getMinEntries());
            }
            if (!filterData.isRestrictLevelOnlyOnPublicGiveaways()) {
                addFilterParameter(jsoup, "level_min", filterData.getMinLevel());
                addFilterParameter(jsoup, "level_max", filterData.getMaxLevel());
            }

//            if (type != GiveawayListFragment.Type.ALL)
//                jsoup.data("type", type.name().toLowerCase(Locale.ENGLISH));

            if (SteamGiftsUserData.getCurrent(context).isLoggedIn())
                jsoup.cookie("PHPSESSID", SteamGiftsUserData.getCurrent(context).getSessionId());
            Document document = jsoup.get();

            SteamGiftsUserData.extract(context, document);

            // Fetch the xsrf token
            Element xsrfToken = document.select("input[name=xsrf_token]").first();
            if (xsrfToken != null)
                foundXsrfToken = xsrfToken.attr("value");

            // Do away with pinned giveaways.
//            if (!showPinnedGiveaways)
                document.select(".pinned-giveaways__outer-wrap").html("");

            // Parse all rows of giveaways
            return Utils.loadGiveawaysFromList(document);
        } catch (Exception e) {
            Log.e(TAG, "Error fetching URL", e);
            return null;
        }
    }

    private void addFilterParameter(Connection jsoup, String parameterName, int value) {
        if (value >= 0)
            jsoup.data(parameterName, String.valueOf(value));
    }
}
