package net.mabako.steamgifts.tasks;

import android.content.Context;

import net.mabako.steamgifts.data.GameInfo;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.persistentdata.SavedGamesBlackList;
import net.mabako.steamgifts.persistentdata.SavedGamesBlackListTags;
import net.mabako.steamgifts.persistentdata.SavedGamesWhiteList;
import net.mabako.steamgifts.persistentdata.SavedGamesWhiteListTags;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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
    private final SavedGamesBlackList savedGamesBlackList;
    private final SavedGamesWhiteList savedGamesWhiteList;
    private final SavedGamesWhiteListTags savedGamesWhiteListTags;
    private final SavedGamesBlackListTags savedGamesBlackListTags;

    public AutoJoinCalculator(Context context, long autoJoinPeriod) {
        this.context = context;
        this.autoJoinPeriod = autoJoinPeriod;

        savedGamesBlackList = new SavedGamesBlackList(context);
        savedGamesWhiteList = new SavedGamesWhiteList(context);
        savedGamesWhiteListTags = new SavedGamesWhiteListTags(context);
        savedGamesBlackListTags = new SavedGamesBlackListTags(context);
    }

    public List<Giveaway> calculateGiveawaysToJoin(List<Giveaway> giveaways) {
        List<Giveaway> filteredGiveaways = filterGiveaways(giveaways);
        return generateGiveawaysToJoinList(filteredGiveaways);
    }

    private List<Giveaway> generateGiveawaysToJoinList(List<Giveaway> filteredGiveaways) {
        int points = SteamGiftsUserData.getCurrent(context).getPoints();

        int minPointsToKeepForBadRatio = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_POINTS_TO_KEEP_FOR_UNTAGGED);
        int minPointsToKeepForGreatRatio = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_POINTS_TO_KEEP_FOR_GREAT_RATIO);

        int pointsLeft = points;

        List<Giveaway> blackListedGiveaways = calculateBlackListedGames(filteredGiveaways);
        filteredGiveaways.removeAll(blackListedGiveaways);

        List<Giveaway> whiteListedGames = calculateWhiteListedGames(filteredGiveaways);
        filteredGiveaways.removeAll(whiteListedGames);

        List<Giveaway> pointGiveaways = calculatePointGames(filteredGiveaways);
        filteredGiveaways.removeAll(pointGiveaways);

        List<Giveaway> taggedGiveaways = calculateTaggedGames(filteredGiveaways);
        filteredGiveaways.removeAll(taggedGiveaways);


        List<Giveaway> result = new ArrayList<>();
        for (Giveaway giveaway : whiteListedGames) {
            int leftAfterJoin = pointsLeft - giveaway.getPoints();
            if (leftAfterJoin >= 0) {
                result.add(giveaway);
                pointsLeft -= giveaway.getPoints();
            }
        }

        for (Giveaway giveaway : pointGiveaways) {
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

    private List<Giveaway> calculatePointGames(List<Giveaway> giveaways) {
        int minimumPoints = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.AUTO_JOIN_POINTS);
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (giveaway.getPoints() >= minimumPoints) {
                result.add(giveaway);
            }
        }

        sortByRating(result);

        return result;
    }

    private List<Giveaway> calculateBlackListedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isBlackListedGame(giveaway.getGameId())) {
                result.add(giveaway);
            }
        }

        sortByRating(result);

        return result;
    }

    private List<Giveaway> calculateTaggedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isTagMatching(giveaway)) {
                result.add(giveaway);
            }
        }

        sortByRating(result);

        return result;
    }



    private List<Giveaway> calculateWhiteListedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isWhiteListedGame(giveaway.getGameId())) {
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
            if (savedGamesBlackList.get(giveaway.getGameId()) == null
                    && giveaway.getRating() >= minimumRating
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

    public boolean isTagMatching(Giveaway giveaway) {
        List<String> whiteListTags = savedGamesWhiteListTags.all();
        List<String> blackListTags = savedGamesBlackListTags.all();

        return giveaway.isTagMatching(whiteListTags) && !giveaway.isTagMatching(blackListTags);
    }

    public boolean hasBlackListedTag(Giveaway giveaway) {
        List<String> blackListTags = savedGamesBlackListTags.all();
        return giveaway.isTagMatching(blackListTags);
    }

    public boolean hasPoints(Giveaway giveaway) {
        int minimumPoints = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.AUTO_JOIN_POINTS);
        return giveaway.getPoints() >= minimumPoints;
    }

    public boolean isBlackListedGame(int gameId) {
        return savedGamesBlackList.get(gameId) != null;
    }

    public void removeFromGamesBlackList(int gameId) {
        savedGamesBlackList.remove(gameId);
    }

    public void addToGamesBlackList(int gameId) {
        savedGamesBlackList.add(new GameInfo(gameId, 0), gameId);
    }

    public boolean isWhiteListedGame(int gameId) {
        return savedGamesWhiteList.get(gameId) != null;
    }

    public void removeFromGamesWhiteList(int gameId) {
        savedGamesWhiteList.remove(gameId);
    }

    public void addToGamesWhiteList(int gameId) {
        savedGamesWhiteList.add(new GameInfo(gameId, 0), gameId);
    }


}
