package net.mabako.steamgifts.tasks;

import android.app.AlarmManager;
import android.content.Context;

import net.mabako.steamgifts.data.GameInfo;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.persistentdata.SavedGamesBlackList;
import net.mabako.steamgifts.persistentdata.SavedGamesBlackListTags;
import net.mabako.steamgifts.persistentdata.SavedGamesMustHaveList;
import net.mabako.steamgifts.persistentdata.SavedGamesWhiteList;
import net.mabako.steamgifts.persistentdata.SavedGamesWhiteListTags;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * AutoJoinCalculator
 * <p/>
 * Created by Supa on 16.03.2016.
 */
public class AutoJoinCalculator {

    private Context context;
    private long urgentJoinPeriod;
    private long nearJoinPeriod;
    private long farJoinPeriod;

    private final SavedGamesBlackList savedGamesBlackList;
    private final SavedGamesWhiteList savedGamesWhiteList;
    private final SavedGamesWhiteListTags savedGamesWhiteListTags;
    private final SavedGamesMustHaveList savedGamesMustHaveList;
    private final SavedGamesBlackListTags savedGamesBlackListTags;
    private final int level;
    private final int greatDemandEntries;

    public AutoJoinCalculator(Context context, long autoJoinPeriod) {
        this.context = context;

        urgentJoinPeriod = AlarmManager.INTERVAL_HALF_HOUR;
        nearJoinPeriod = autoJoinPeriod / 2;
        farJoinPeriod = autoJoinPeriod;

        greatDemandEntries = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.GREAT_DEMAND_ENTRIES);

        level = SteamGiftsUserData.getCurrent(context).getLevel();

        savedGamesBlackList = new SavedGamesBlackList(context);
        savedGamesWhiteList = new SavedGamesWhiteList(context);
        savedGamesMustHaveList = new SavedGamesMustHaveList(context);
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
        int minPointsToKeepForNotMeetingTheLevel = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_POINTS_TO_KEEP_FOR_NOT_MEETING_LEVEL);
        int pointsToKeepForNonMustHaveGames = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_POINTS_TO_KEEP_FOR_NOT_ON_MUST_HAVE_LIST);

        int pointsLeft = points;

        List<Giveaway> zeroPointGiveaways = calculateZeroPointGames(filteredGiveaways);

        List<Giveaway> blackListedGiveaways = calculateBlackListedGames(filteredGiveaways);
        filteredGiveaways.removeAll(blackListedGiveaways);

        List<Giveaway> urgentMustHaveListedGames = calculateMustHaveListedGames(filteredGiveaways, urgentJoinPeriod);
        filteredGiveaways.removeAll(urgentMustHaveListedGames);

        List<Giveaway> nearMustHaveListedGames = calculateMustHaveListedGames(filteredGiveaways, nearJoinPeriod);
        filteredGiveaways.removeAll(nearMustHaveListedGames);

        List<Giveaway> farMustHaveListedGames = calculateMustHaveListedGames(filteredGiveaways, farJoinPeriod);
        filteredGiveaways.removeAll(farMustHaveListedGames);

        List<Giveaway> urgentWhiteListedGames = calculateWhiteListedGames(filteredGiveaways, urgentJoinPeriod);
        filteredGiveaways.removeAll(urgentWhiteListedGames);

        List<Giveaway> nearWhiteListedGames = calculateWhiteListedGames(filteredGiveaways, nearJoinPeriod);
        filteredGiveaways.removeAll(nearWhiteListedGames);

        List<Giveaway> greatDemandGames = calculateGreatDemandGames(filteredGiveaways, nearJoinPeriod);
        filteredGiveaways.removeAll(greatDemandGames);

        List<Giveaway> taggedGiveaways = calculateTaggedGames(filteredGiveaways, nearJoinPeriod);
        filteredGiveaways.removeAll(taggedGiveaways);

        List<Giveaway> result = new ArrayList<>(zeroPointGiveaways);

        pointsLeft = addGiveaways(pointsLeft, urgentMustHaveListedGames, 0, 0, result);
        pointsLeft = addGiveaways(pointsLeft, nearMustHaveListedGames, minPointsToKeepForNotMeetingTheLevel, 0, result);

        pointsLeft = addGiveaways(pointsLeft, urgentWhiteListedGames, minPointsToKeepForNotMeetingTheLevel, pointsToKeepForNonMustHaveGames, result);
        pointsLeft = addGiveaways(pointsLeft, nearWhiteListedGames, minPointsToKeepForNotMeetingTheLevel, pointsToKeepForNonMustHaveGames, result);

        pointsLeft = addGiveaways(pointsLeft, farMustHaveListedGames, minPointsToKeepForNotMeetingTheLevel, pointsToKeepForNonMustHaveGames*2, result);
        pointsLeft = addGiveaways(pointsLeft, greatDemandGames, minPointsToKeepForNotMeetingTheLevel, pointsToKeepForNonMustHaveGames*2, result);

        pointsLeft = addGiveaways(pointsLeft, taggedGiveaways, minPointsToKeepForNotMeetingTheLevel, Math.max(minPointsToKeepForGreatRatio, pointsToKeepForNonMustHaveGames), result);
        pointsLeft = addGiveaways(pointsLeft, filteredGiveaways, minPointsToKeepForNotMeetingTheLevel, Math.max(minPointsToKeepForBadRatio, pointsToKeepForNonMustHaveGames), result);

        return result;
    }

    private int addGiveaways(int pointsLeft, List<Giveaway> giveaways, int minPointsToKeepForNotMeetingTheLevel, int minPointsToKeep, List<Giveaway> result) {
        if (minPointsToKeepForNotMeetingTheLevel < minPointsToKeep) {
            minPointsToKeepForNotMeetingTheLevel = minPointsToKeep;
        }

        for (Giveaway giveaway : giveaways) {
            int leftAfterJoin = pointsLeft - giveaway.getPoints();

            int pointsToKeep = minPointsToKeep + calculatePointsToKeepForLevel(giveaway, minPointsToKeepForNotMeetingTheLevel - minPointsToKeep);

            if (leftAfterJoin >= Math.max(pointsToKeep, minPointsToKeep)) {
                result.add(giveaway);
                pointsLeft -= giveaway.getPoints();
            }
        }

        giveaways.removeAll(result);
        return pointsLeft;
    }

    private List<Giveaway> calculateZeroPointGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (giveaway.getPoints() == 0) {
                result.add(giveaway);
            }
        }

        return result;
    }

    private List<Giveaway> calculateGreatDemandGames(List<Giveaway> giveaways, long period) {

        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (hasGreatDemand(giveaway) && endsWithinPeriod(giveaway, period)) {
                result.add(giveaway);
            }
        }

        sortByLevelThenEntries(result);

        return result;
    }

    private List<Giveaway> calculateBlackListedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isBlackListedGame(giveaway.getGameId())) {
                result.add(giveaway);
            }
        }

        sortByLevelThenEntries(result);

        return result;
    }

    private List<Giveaway> calculateTaggedGames(List<Giveaway> giveaways, long period) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isTagMatching(giveaway) && endsWithinPeriod(giveaway, period)) {
                result.add(giveaway);
            }
        }

        sortByLevelThenEntries(result);

        return result;
    }


    private List<Giveaway> calculateMustHaveListedGames(List<Giveaway> giveaways, long timePeriod) {
        final long now = System.currentTimeMillis();

        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isMustHaveListedGame(giveaway.getGameId()) && endsWithinPeriod(giveaway, timePeriod)) {
                result.add(giveaway);
            }
        }

        sortByTime(result);

        return result;
    }

    private boolean endsWithinPeriod(Giveaway giveaway, long period) {
        return giveaway.getEndTime().getTimeInMillis() - System.currentTimeMillis() < period;
    }

    private List<Giveaway> calculateWhiteListedGames(List<Giveaway> giveaways, long period) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isWhiteListedGame(giveaway.getGameId()) && endsWithinPeriod(giveaway, period)) {
                result.add(giveaway);
            }
        }

        sortByLevelThenEntries(result);

        return result;
    }

    private void sortByTime(List<Giveaway> giveaways) {
        Collections.sort(giveaways, new Comparator<Giveaway>() {
            @Override
            public int compare(Giveaway lhs, Giveaway rhs) {
                return (int) (lhs.getEndTime().getTimeInMillis() - rhs.getEndTime().getTimeInMillis());
            }
        });
    }

    private void sortByLevelThenEntries(List<Giveaway> giveaways) {
        Collections.sort(giveaways, new Comparator<Giveaway>() {
            @Override
            public int compare(Giveaway lhs, Giveaway rhs) {
                int level = rhs.getLevel() - lhs.getLevel();
                if (level != 0) {
                    return level;
                }

                return lhs.getEstimatedEntriesPerCopy() - rhs.getEstimatedEntriesPerCopy();
            }
        });
    }


    private List<Giveaway> filterGiveaways(List<Giveaway> giveaways) {
        int minimumRating = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_RATING);

        List<Giveaway> result = new ArrayList<>();
        for (Giveaway giveaway : giveaways) {
            if (giveaway.getRating() >= minimumRating
                    && !giveaway.isEntered()
                    && !giveaway.isLevelNegative()
                    && !SteamGiftsUserData.getCurrent(context).getName().equals(giveaway.getCreator())) {
                result.add(giveaway);
            }
        }

        return result;
    }

    public boolean doesGiveawayEndWithInAutoJoinPeriod(Giveaway giveaway) {
        return endsWithinPeriod(giveaway, farJoinPeriod);
    }

    public boolean isTagMatching(Giveaway giveaway) {
        List<String> whiteListTags = savedGamesWhiteListTags.all();
        List<String> blackListTags = savedGamesBlackListTags.all();

        return giveaway.isTagMatching(whiteListTags) && !giveaway.isTagMatching(blackListTags);
    }

    public List<String> getBlackListTags() {
        return savedGamesBlackListTags.all();
    }

    public List<String> getWhiteListTags() {
        return savedGamesWhiteListTags.all();
    }

    public boolean hasBlackListedTag(Giveaway giveaway) {
        List<String> blackListTags = savedGamesBlackListTags.all();
        return giveaway.isTagMatching(blackListTags);
    }

    public boolean hasPoints(Giveaway giveaway) {
        int minimumPoints = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.GREAT_DEMAND_ENTRIES);
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

    public boolean isMustHaveListedGame(int gameId) {
        return savedGamesMustHaveList.get(gameId) != null;
    }

    public void removeFromMustHaveWhiteList(int gameId) {
        savedGamesMustHaveList.remove(gameId);
    }

    public void addToGamesMustHaveList(int gameId) {
        savedGamesMustHaveList.add(new GameInfo(gameId, 0), gameId);
    }

    public int calculatePointsToKeepForLevel(Giveaway giveaway, int pointsToKeepAwayForLevel) {
        if (level == 0) {
            return 0;
        }
        double levelPercentage = (double) giveaway.getLevel() / level;
        if (levelPercentage > 1) {
            levelPercentage = 1;
        }

        double percentagePointsToKeep = 1 - levelPercentage;

        return (int) (percentagePointsToKeep * pointsToKeepAwayForLevel);
    }

    public boolean isMatchingLevel(Giveaway giveaway) {
        return level == giveaway.getLevel();
    }


    public boolean hasGreatDemand(Giveaway giveaway) {
        return giveaway.getAverageEntries() > greatDemandEntries;
    }
}
