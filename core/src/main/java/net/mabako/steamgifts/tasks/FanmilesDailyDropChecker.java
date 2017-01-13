package net.mabako.steamgifts.tasks;

import android.content.Context;
import android.widget.Toast;

import net.mabako.Constants;
import net.mabako.steamgifts.data.Statistics;
import net.mabako.steamgifts.persistentdata.SavedGamesBlackList;
import net.mabako.steamgifts.persistentdata.SavedStrings;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class FanmilesDailyDropChecker {

    private Context context;
    private Statistics statistics;
    private final SavedStrings savedStrings;

    public FanmilesDailyDropChecker(Context context, Statistics statistics) {
        this.context = context;
        this.statistics = statistics;
        savedStrings = new SavedStrings(context);
    }

    public void check() {
        List<String> dailyDrops = getDailyDrops();
        dailyDrops.remove("Eure täglichen Drops");
        //sending mails, see: http://stackoverflow.com/questions/2020088/sending-email-in-android-using-javamail-api-without-using-the-default-built-in-a/2033124#2033124
        statistics.updateDailyDropsNotification("Daily Drops", dailyDrops, isNewDailyDrops(dailyDrops));
    }

    private boolean isNewDailyDrops(List<String> currentDailyDrops) {
        List<String> all = new ArrayList<>(savedStrings.all());

        updateSave(currentDailyDrops);

        if (currentDailyDrops.size() > all.size()) {
            return true;
        }

        all.removeAll(currentDailyDrops);

        return !all.isEmpty();
    }

    private void updateSave(List<String> currentDailyDrops) {
        savedStrings.removeAll();
        for (String drop : currentDailyDrops) {
            savedStrings.add(drop, drop);
        }
    }

    public List<String> getDailyDrops() {
        List<String> result = new ArrayList<>();
        try {
            Connection jsoup = Jsoup.connect("https://www.fanmiles.com/DE/de/partners/dailydrop/")
                    .timeout(Constants.JSOUP_TIMEOUT);

            Document document = jsoup.get();

            Elements offerElements = document.getElementsByClass("partner-offers");

            for (Element element : offerElements) {
                Elements hTags = element.select("h2");
                for (Element tag : hTags) {
                    String text = tag.text();
                    result.add(text);
                }
            }
        } catch (Exception ex) {
        }
        return result;
    }

}
