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
import net.mabako.steamgifts.receivers.CheckForAutoJoin;

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
    private long autoJoinPeriod;
    private final SavedGamesBlackList savedGamesBlackList;
    private final SavedGamesWhiteList savedGamesWhiteList;
    private final SavedGamesWhiteListTags savedGamesWhiteListTags;
    private final SavedGamesMustHaveList savedGamesMustHaveList;
    private final SavedGamesBlackListTags savedGamesBlackListTags;
    private final int level;

    public AutoJoinCalculator(Context context, long autoJoinPeriod) {
        this.context = context;
        this.autoJoinPeriod = autoJoinPeriod;

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

        List<Giveaway> mustHaveListedGames = calculateMustHaveListedGames(filteredGiveaways);
        filteredGiveaways.removeAll(mustHaveListedGames);

        List<Giveaway> giveawaysEndingTooLate = calculateTooLateGiveaways(filteredGiveaways);
        filteredGiveaways.removeAll(giveawaysEndingTooLate);

        List<Giveaway> whiteListedGames = calculateWhiteListedGames(filteredGiveaways);
        filteredGiveaways.removeAll(whiteListedGames);

        List<Giveaway> pointGiveaways = calculatePointGames(filteredGiveaways);
        filteredGiveaways.removeAll(pointGiveaways);

        List<Giveaway> taggedGiveaways = calculateTaggedGames(filteredGiveaways);
        filteredGiveaways.removeAll(taggedGiveaways);

        List<Giveaway> result = new ArrayList<>(zeroPointGiveaways);

        pointsLeft = addGiveaways(pointsLeft, mustHaveListedGames, 0, 0, result);
        pointsLeft = addGiveaways(pointsLeft, whiteListedGames,  minPointsToKeepForNotMeetingTheLevel,pointsToKeepForNonMustHaveGames, result);
        pointsLeft = addGiveaways(pointsLeft, pointGiveaways,  minPointsToKeepForNotMeetingTheLevel, pointsToKeepForNonMustHaveGames,result);
        pointsLeft = addGiveaways(pointsLeft, taggedGiveaways,  minPointsToKeepForNotMeetingTheLevel, Math.max(minPointsToKeepForGreatRatio, pointsToKeepForNonMustHaveGames),result);
        pointsLeft = addGiveaways(pointsLeft, filteredGiveaways,  minPointsToKeepForNotMeetingTheLevel, Math.max(minPointsToKeepForBadRatio, pointsToKeepForNonMustHaveGames),result);

        return result;
    }

    private List<Giveaway> calculateTooLateGiveaways(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        long now = System.currentTimeMillis();
        for (Giveaway giveaway : giveaways) {
            boolean tooLate = giveaway.getEndTime().getTimeInMillis() - now > AlarmManager.INTERVAL_HOUR;
            if (tooLate) {
                result.add(giveaway);
            }
        }

        return result;


    }

    private int addGiveaways(int pointsLeft, List<Giveaway> mustHaveListedGames, int minPointsToKeepForNotMeetingTheLevel, int minPointsToKeep, List<Giveaway> result) {
        boolean breakWhenGiveAwayIsNotMatchingPoints = minPointsToKeepForNotMeetingTheLevel == 0 && minPointsToKeep == 0;

        for (Giveaway giveaway : mustHaveListedGames) {
            int leftAfterJoin = pointsLeft - giveaway.getPoints();

            int pointsToKeep = minPointsToKeep + calculatePointsToKeepForLevel(giveaway, minPointsToKeepForNotMeetingTheLevel - minPointsToKeep);

            if (leftAfterJoin >= Math.max(pointsToKeep, minPointsToKeep)) {
                result.add(giveaway);
                pointsLeft -= giveaway.getPoints();
            } else if (breakWhenGiveAwayIsNotMatchingPoints) {
                break;
            }
        }
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

    private List<Giveaway> calculatePointGames(List<Giveaway> giveaways) {
        int minimumPoints = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.AUTO_JOIN_POINTS);
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (giveaway.getPoints() >= minimumPoints) {
                result.add(giveaway);
            }
        }

        sortByTime(result);

        return result;
    }

    private List<Giveaway> calculateBlackListedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isBlackListedGame(giveaway.getGameId())) {
                result.add(giveaway);
            }
        }

        sortByTime(result);

        return result;
    }

    private List<Giveaway> calculateTaggedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isTagMatching(giveaway)) {
                result.add(giveaway);
            }
        }

        sortByTime(result);

        return result;
    }


    private List<Giveaway> calculateMustHaveListedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isMustHaveListedGame(giveaway.getGameId())) {
                result.add(giveaway);
            }
        }

        sortByTimeOnly(result);

        return result;
    }

    private List<Giveaway> calculateWhiteListedGames(List<Giveaway> giveaways) {
        List<Giveaway> result = new ArrayList<>();

        for (Giveaway giveaway : giveaways) {
            if (isWhiteListedGame(giveaway.getGameId())) {
                result.add(giveaway);
            }
        }

        sortByTime(result);

        return result;
    }

    private void sortByTime(List<Giveaway> giveaways) {
        sortByTime(giveaways, false);
    }

    private void sortByTimeOnly(List<Giveaway> giveaways) {
        Collections.sort(giveaways, new Comparator<Giveaway>() {
            @Override
            public int compare(Giveaway lhs, Giveaway rhs) {
                return (int)(lhs.getEndTime().getTimeInMillis() - rhs.getEndTime().getTimeInMillis());
            }
        });
    }

    private void sortByTime(List<Giveaway> giveaways, final boolean ignoreRating) {
        final long now = System.currentTimeMillis();
        Collections.sort(giveaways, new Comparator<Giveaway>() {
            @Override
            public int compare(Giveaway lhs, Giveaway rhs) {
                boolean lhsInNext30Mins = lhs.getEndTime().getTimeInMillis() - now < AlarmManager.INTERVAL_HALF_HOUR;
                boolean rhsInNext30Mins = rhs.getEndTime().getTimeInMillis() - now < AlarmManager.INTERVAL_HALF_HOUR;

                if (lhsInNext30Mins != rhsInNext30Mins) {
                    return lhsInNext30Mins ? -1 : 1;
                }

                int level = rhs.getLevel() - lhs.getLevel();
                if (level != 0) {
                    return level;
                }

                if (!ignoreRating) {
                    int rating = rhs.getRating() - lhs.getRating();
                    if (rating != 0) {
                        return rating;
                    }
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
        double levelPercentage = (double)giveaway.getLevel() / level;
        if (levelPercentage > 1) {
            levelPercentage = 1;
        }

        double percentagePointsToKeep = 1 - levelPercentage;

        return (int)(percentagePointsToKeep * pointsToKeepAwayForLevel);
    }

    public boolean isMatchingLevel(Giveaway giveaway) {
        return level == giveaway.getLevel();
    }


}
