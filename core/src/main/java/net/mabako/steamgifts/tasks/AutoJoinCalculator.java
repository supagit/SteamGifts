package net.mabako.steamgifts.tasks;

import android.content.Context;

import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.persistentdata.SavedGiveaways;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AutoJoinCalculator
 * <p/>
 * Created by Supa on 16.03.2016.
 */
public class AutoJoinCalculator {

    private Context context;
    private long autoJoinPeriod;
    private final Set<Integer> bookmarkedGameIds;
    private final Set<String> blackListTags;

    public AutoJoinCalculator(Context context, long autoJoinPeriod) {
        this.context = context;
        this.autoJoinPeriod = autoJoinPeriod;
        bookmarkedGameIds = isOption(AutoJoinOptions.AutoJoinOption.ALWAYS_JOIN_GIVEAWAYS_FOR_BOOKMARKS) ? getBookMarkedGameIds() : new HashSet<Integer>();
        blackListTags = AutoJoinOptions.getOptionBlackListTags(context);
    }

    public List<Giveaway> calculateGiveawaysToJoin(List<Giveaway> giveaways) {
        List<Giveaway> filteredGiveaways = filterGiveaways(giveaways);
        return generateGiveawaysToJoinList(filteredGiveaways);
    }

    private Set<Integer> getBookMarkedGameIds() {
        Set<Integer> bookmarkedGameIds = new HashSet<>();
        SavedGiveaways savedGiveaways = new SavedGiveaways(context);
        for (Giveaway giveaway : savedGiveaways.all()) {
            bookmarkedGameIds.add(giveaway.getGameId());
        }
        savedGiveaways.close();
        return bookmarkedGameIds;
    }

    private List<Giveaway> generateGiveawaysToJoinList(List<Giveaway> filteredGiveaways) {
        int points = SteamGiftsUserData.getCurrent(context).getPoints();

        int minPointsToKeepForBadRatio = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_POINTS_TO_KEEP_FOR_UNTAGGED);
        int minPointsToKeepForGreatRatio = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_POINTS_TO_KEEP_FOR_GREAT_RATIO);

        int pointsLeft = points;

        List<Giveaway> bookmarkedGiveaways = calculateBookmarkedGames(filteredGiveaways);
        filteredGiveaways.removeAll(bookmarkedGiveaways);

        List<Giveaway> taggedGiveaways = calculateTaggedGames(filteredGiveaways);
        filteredGiveaways.removeAll(taggedGiveaways);

        List<Giveaway> blackListedGiveaways = calculateBlackListedGames(filteredGiveaways);
        filteredGiveaways.removeAll(blackListedGiveaways);

        List<Giveaway> result = new ArrayList<>();
        for (Giveaway giveaway : bookmarkedGiveaways) {
            int leftAfterJoin = pointsLeft - giveaway.getPoints();
            if (leftAfterJoin >= 0) {
                result.add(giveaway);
                pointsLeft -= giveaway.getPoints();
            }
        }

        for (Giveaway giveaway : taggedGiveaways) {
            int leftAfterJoin = pointsLeft - giveaway.getPoints();
            if (leftAfterJoin >= minPointsToKeepForGreatRatio) {
                result.add(giveaway);
                pointsLeft -= giveaway.getPoints();
            }
        }

        for (Giveaway giveaway : filteredGiveaways) {
            int leftAfterJoin = pointsLeft - giveaway.getPoints();
            if (leftAfterJoin >= minPointsToKeepForBadRatio) {
                result.add(giveaway);
                pointsLeft -= giveaway.getPoints();
            }
        }

        return result;
    }

    private List<Giveaway> calculateBlackListedGames(List<Giveaway> giveaways) {


        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (giveaway.isTagMatching(blackListTags)) {
                result.add(giveaway);
            }
        }

        sortByRating(result);

        return result;
    }

    private List<Giveaway> calculateTaggedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (AutoJoinOptions.isTagged(context, giveaway)) {
                result.add(giveaway);
            }
        }

        sortByRating(result);

        return result;
    }

    private List<Giveaway> calculateBookmarkedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (bookmarkedGameIds.contains(giveaway.getGameId())) {
                result.add(giveaway);
            }
        }

        sortByRating(result);

        return result;
    }

    private void sortByRating(List<Giveaway> giveaways) {
        Collections.sort(giveaways, new Comparator<Giveaway>() {
            @Override
            public int compare(Giveaway lhs, Giveaway rhs) {
                return rhs.getRating() - lhs.getRating();
            }
        });
    }

    private List<Giveaway> filterGiveaways(List<Giveaway> giveaways) {
        int minimumRating = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_RATING);

        List<Giveaway> result = new ArrayList<>();
        for (Giveaway giveaway : giveaways) {
            if (giveaway.getRating() >= minimumRating
                    && doesGiveawayEndWithInAutoJoinPeriod(giveaway)
                    && !giveaway.isEntered()
                    && !giveaway.isLevelNegative()
                    && !SteamGiftsUserData.getCurrent(context).getName().equals(giveaway.getCreator())) {
                result.add(giveaway);
            }
        }

        return result;
    }

    public boolean doesGiveawayEndWithInAutoJoinPeriod(Giveaway giveaway) {
        final long realTimeDiff = Math.abs(new Date().getTime() - giveaway.getEndTime().getTimeInMillis());
        return realTimeDiff <= autoJoinPeriod;
    }

    private boolean isOption(AutoJoinOptions.AutoJoinOption option) {
        return AutoJoinOptions.isOptionBoolean(context, option);
    }

    public boolean isBookmarked(Giveaway giveaway) {
        return bookmarkedGameIds.contains(giveaway.getGameId());
    }

    public boolean isTagged(Giveaway giveaway) {
        return AutoJoinOptions.isTagged(context, giveaway);
    }

    public boolean isBlackListed(Giveaway giveaway) {
        return giveaway.isTagMatching(blackListTags);
    }
}
