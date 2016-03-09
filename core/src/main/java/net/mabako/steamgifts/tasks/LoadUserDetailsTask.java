package net.mabako.steamgifts.tasks;

import android.os.AsyncTask;
import android.util.Log;

import net.mabako.Constants;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.data.User;
import net.mabako.steamgifts.fragments.UserDetailFragment;
import net.mabako.steamgifts.persistentdata.SavedRatings;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;

public class LoadUserDetailsTask extends AsyncTask<Void, Void, List<Giveaway>> {
    private static final String TAG = LoadUserDetailsTask.class.getSimpleName();

    private final UserDetailFragment.UserGiveawayListFragment fragment;
    private final String path;
    private final int page;
    private final User user;
    private String foundXsrfToken;
    private final SavedRatings savedRatings;

    public LoadUserDetailsTask(UserDetailFragment.UserGiveawayListFragment fragment, String path, int page, User user) {
        this.fragment = fragment;
        savedRatings = new SavedRatings(fragment.getContext());
        this.path = path;
        this.page = page;
        this.user = user;
    }

    @Override
    protected List<Giveaway> doInBackground(Void... params) {
        Log.d(TAG, "Fetching giveaways for user " + path + " on page " + page);

        try {
            // Fetch the Giveaway page
            Connection connection = Jsoup.connect("http://www.steamgifts.com/user/" + path + "/search")
                    .userAgent(Constants.JSOUP_USER_AGENT)
                    .timeout(Constants.JSOUP_TIMEOUT);
            connection.data("page", Integer.toString(page));
            if (SteamGiftsUserData.getCurrent(fragment.getContext()).isLoggedIn())
                connection.cookie("PHPSESSID", SteamGiftsUserData.getCurrent(fragment.getContext()).getSessionId());

            connection.followRedirects(false);
            Connection.Response response = connection.execute();
            Document document = response.parse();

            if (response.statusCode() == 200) {

                SteamGiftsUserData.extract(fragment.getContext(), document);

                if (!user.isLoaded())
                    loadUser(document);

                // Parse all rows of giveaways
                return Utils.loadGiveawaysFromList(document, savedRatings);
            } else {
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching URL", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(List<Giveaway> result) {
        super.onPostExecute(result);

        if (!user.isLoaded() && result != null) {
            user.setLoaded(true);
            fragment.onUserUpdated(user);
        }

        fragment.addItems(result, page == 1, foundXsrfToken);
    }

    private void loadUser(Document document) {
        // If this isn't the user we're logged in as, we'd get some user id.
        Element idElement = document.select("input[name=child_user_id]").first();
        if (idElement != null) {
            user.setId(Integer.valueOf(idElement.attr("value")));
        } else {
            Log.v(TAG, "No child_user_id");
        }

        user.setWhitelisted(!document.select(".sidebar__shortcut__whitelist.is-selected").isEmpty());
        user.setBlacklisted(!document.select(".sidebar__shortcut__blacklist.is-selected").isEmpty());

        // Fetch the xsrf token - this, again, is only present if we're on another user's page.
        Element xsrfToken = document.select("input[name=xsrf_token]").first();
        if (xsrfToken != null)
            foundXsrfToken = xsrfToken.attr("value");

        user.setName(document.select(".featured__heading__medium").first().text());
        user.setAvatar(Utils.extractAvatar(document.select(".global__image-inner-wrap").first().attr("style")));
        user.setUrl(document.select(".sidebar a[data-tooltip=\"Visit Steam Profile\"]").first().attr("href"));

        Elements columns = document.select(".featured__table__column");
        user.setRole(columns.first().select("a[href^=/roles/").text());
        user.setComments(parseInt(columns.first().select(".featured__table__row__right").get(3).text()));

        Elements right = columns.last().select(".featured__table__row__right");

        // Both won and created have <a href="...">[amount won]</a> [value of won items],
        // so it's impossible to get the text for the amount directly.
        Element won = right.get(1);
        user.setWon(parseInt(won.select("a").first().text()));
        won.select("a").html("");
        user.setWonAmount(won.text().trim());

        Element created = right.get(2);
        user.setCreated(parseInt(created.select("a").first().text()));
        created.select("a").html("");
        user.setCreatedAmount(created.text().trim());

        user.setLevel((int) Float.parseFloat(right.get(3).select("span").first().attr("title")));
    }

    private static int parseInt(String str) {
        return Integer.parseInt(str.replace(",", ""));
    }
}
