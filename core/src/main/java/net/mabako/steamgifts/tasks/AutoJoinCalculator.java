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
import java.util.List;

/**
 * AutoJoinCalculator
 * <p/>
 * Created by Supa on 16.03.2016.
 */
public class AutoJoinCalculator {

    private Context context;
    private long nearJoinPeriod;
    private long farJoinPeriod;

    private final SavedGamesBlackList savedGamesBlackList;
    private final SavedGamesWhiteList savedGamesWhiteList;
    private final SavedGamesWhiteListTags savedGamesWhiteListTags;
    private final SavedGamesMustHaveList savedGamesMustHaveList;
    private final SavedGamesBlackListTags savedGamesBlackListTags;
    private final int level;
    private final int greatDemandEntries;
    private final int minLevelForWhiteList;
    private final int pointsToKeepForMustHaveGames;
    private final int minPointsToKeepForNotMeetingTheLevel;
    private final int treatUnbundledAsMustHaveWithPoints;

    public AutoJoinCalculator(Context context, long autoJoinPeriod) {
        this.context = context;

        nearJoinPeriod = autoJoinPeriod / 2;
        farJoinPeriod = autoJoinPeriod;

        greatDemandEntries = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.GREAT_DEMAND_ENTRIES);
        minLevelForWhiteList = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_LEVEL_FOR_WHITELIST);
        pointsToKeepForMustHaveGames = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_POINTS_TO_KEEP_FOR_NOT_ON_MUST_HAVE_LIST);
        minPointsToKeepForNotMeetingTheLevel = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_POINTS_TO_KEEP_FOR_NOT_MEETING_LEVEL);
        treatUnbundledAsMustHaveWithPoints = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.TREAT_UNBUNDLED_AS_MUST_HAVE_WITH_POINTS);

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

        int pointsLeft = points;

        List<Giveaway> joinedGiveaways = calculateJoinedGiveaway(filteredGiveaways);
        filteredGiveaways.removeAll(joinedGiveaways);

        List<Giveaway> zeroPointGiveaways = calculateZeroPointGames(filteredGiveaways);

        List<Giveaway> blackListedGiveaways = calculateBlackListedGames(filteredGiveaways);
        filteredGiveaways.removeAll(blackListedGiveaways);

        List<Giveaway> mustHaveListedGames = calculateMustHaveListedGames(filteredGiveaways);
        filteredGiveaways.removeAll(mustHaveListedGames);

        List<Giveaway> whiteListedGamesMatchingLevel = calculateWhiteListedGames(filteredGiveaways, minLevelForWhiteList);
        filteredGiveaways.removeAll(whiteListedGamesMatchingLevel);

        List<Giveaway> greatDemandGames = calculateGreatDemandGames(filteredGiveaways, nearJoinPeriod);
        filteredGiveaways.removeAll(greatDemandGames);

        List<Giveaway> whiteListedGamesNotMatchingLevel = calculateWhiteListedGames(filteredGiveaways, 0);
        filteredGiveaways.removeAll(whiteListedGamesNotMatchingLevel);

        List<Giveaway> taggedGiveaways = calculateTaggedGames(filteredGiveaways, nearJoinPeriod);
        filteredGiveaways.removeAll(taggedGiveaways);

        List<Giveaway> result = new ArrayList<>(zeroPointGiveaways);

        pointsLeft = addGiveaways(pointsLeft, mustHaveListedGames, 0, 0, result);
        pointsLeft = addGiveaways(pointsLeft, whiteListedGamesMatchingLevel, minPointsToKeepForNotMeetingTheLevel, pointsToKeepForMustHaveGames, result);
        pointsLeft = addGiveaways(pointsLeft, greatDemandGames, minPointsToKeepForNotMeetingTheLevel, pointsToKeepForMustHaveGames, result);
        pointsLeft = addGiveaways(pointsLeft, whiteListedGamesNotMatchingLevel, minPointsToKeepForNotMeetingTheLevel, minPointsToKeepForNotMeetingTheLevel, result);
        pointsLeft = addGiveaways(pointsLeft, taggedGiveaways, minPointsToKeepForNotMeetingTheLevel, minPointsToKeepForNotMeetingTheLevel, result);
        pointsLeft = addGiveaways(pointsLeft, filteredGiveaways, minPointsToKeepForNotMeetingTheLevel, minPointsToKeepForNotMeetingTheLevel, result);

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

    private List<Giveaway> calculateJoinedGiveaway(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (giveaway.isEntered()) {
                result.add(giveaway);
            }
        }

        return result;
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

        sortByLevelThenTime(result);

        return result;
    }

    private List<Giveaway> calculateBlackListedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isBlackListedGame(giveaway.getGameId())) {
                result.add(giveaway);
            }
        }

        sortByLevelThenTime(result);

        return result;
    }

    private List<Giveaway> calculateTaggedGames(List<Giveaway> giveaways, long period) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isTagMatching(giveaway) && endsWithinPeriod(giveaway, period)) {
                result.add(giveaway);
            }
        }

        sortByLevelThenTime(result);

        return result;
    }


    private List<Giveaway> calculateMustHaveListedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isMustHaveListedGameOrUnbundled(giveaway)) {
                result.add(giveaway);
            }
        }

        sortByTime(result);

        return result;
    }

    private boolean endsWithinPeriod(Giveaway giveaway, long period) {
        return giveaway.getEndTime().getTimeInMillis() - System.currentTimeMillis() < period;
    }

    private List<Giveaway> calculateWhiteListedGames(List<Giveaway> giveaways, int minLevel) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isWhiteListedGame(giveaway.getGameId()) && calculateLevel(giveaway) >= minLevel) {
                result.add(giveaway);
            }
        }

        sortByLevelThenTime(result);

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

    private void sortByLevelThenTime(List<Giveaway> giveaways) {
        Collections.sort(giveaways, new Comparator<Giveaway>() {
            @Override
            public int compare(Giveaway lhs, Giveaway rhs) {
                int level = calculateLevel(rhs) - calculateLevel(lhs);
                if (level != 0) {
                    return level;
                }

                return (int) (lhs.getEndTime().getTimeInMillis() - rhs.getEndTime().getTimeInMillis());
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

    public boolean isMustHaveListedGameOrUnbundled(Giveaway giveaway) {
        return isMustHaveListedGame(giveaway.getGameId()) || (!giveaway.isBundleGame() && giveaway.getPoints() >= treatUnbundledAsMustHaveWithPoints);
    }

    public void removeFromMustHaveWhiteList(int gameId) {
        savedGamesMustHaveList.remove(gameId);
    }

    public void addToGamesMustHaveList(int gameId) {
        savedGamesMustHaveList.add(new GameInfo(gameId, 0), gameId);
    }

    public int calculateLevel(Giveaway giveaway) {
        int giveawayLevel = giveaway.getLevel();
        try {
            if (giveawayLevel >= level) {
                return giveawayLevel;
            }

            long maxShortRunTime = AlarmManager.INTERVAL_HOUR * 2;
            long maxLevelDelta = 2;

            if (giveaway.getEndTime() == null || giveaway.getCreatedTime() == null) {
                return giveawayLevel;
            }

            long giveAwayTime = giveaway.getEndTime().getTimeInMillis() - giveaway.getCreatedTime().getTimeInMillis();

            if (giveAwayTime > maxShortRunTime) {
                return giveawayLevel;
            }

            double perc = (double) (maxShortRunTime - giveAwayTime + AlarmManager.INTERVAL_HOUR) / maxShortRunTime;
            double levelDelta = perc * maxLevelDelta;
            int actualLevelDelta = (int) Math.round(levelDelta);

            int newGiveawayLevel = giveawayLevel + actualLevelDelta;
            if (newGiveawayLevel >= level) {
                newGiveawayLevel = level;
            }
            return newGiveawayLevel;
        }
        catch(Exception ex) {
            ex.printStackTrace();
            return giveawayLevel;
        }

    }

    public int calculatePointsToKeepForLevel(Giveaway giveaway, int pointsToKeepAwayForLevel) {
        int giveawayLevel = calculateLevel(giveaway);
        if (giveawayLevel < minLevelForWhiteList || level <= 0) {
            return pointsToKeepAwayForLevel;
        }

        double levelPercentage = (double) (giveawayLevel - minLevelForWhiteList) / (level - minLevelForWhiteList);
        if (levelPercentage > 1) {
            levelPercentage = 1;
        }

        double percentagePointsToKeep = 1 - levelPercentage;

        return (int) (percentagePointsToKeep * pointsToKeepAwayForLevel);
    }

    public boolean isMatchingLevel(Giveaway giveaway) {
        return level == calculateLevel(giveaway);
    }


    public boolean hasGreatDemand(Giveaway giveaway) {
        return giveaway.getAverageEntries() > greatDemandEntries;
    }

    public boolean isMatchingWhiteListLevel(Giveaway giveaway) {
        return calculateLevel(giveaway) >= minLevelForWhiteList;
    }
}
