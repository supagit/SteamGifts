package net.mabako.steamgifts.data;

import android.app.AlarmManager;

import net.mabako.steamgifts.fragments.profile.ProfileGiveaway;
import net.mabako.steamgifts.persistentdata.SavedElements;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Supa on 14.03.2016.
 */
public class GameInfo {

    private long time;
    private Integer rating;
    private int gameId;
    private int joinCount;

    private Boolean isBundle;
    private Long bundleUpdateTime;
    private Set<String> tags = new HashSet<>();

    private long numberOfEntriesOfAllGiveaways;
    private long numberOfGiveAways;

    public GameInfo(int gameId, long time) {
        this.gameId = gameId;
        this.time = time;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public int getRating() {
        if (rating == null) {
            return 0;
        }
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public int getGameId() {
        return gameId;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void addJoinDelta(int delta) {
        joinCount += delta;
    }

    public boolean isValid() {
        if (rating != null) {
            return true;
        }
        long timeDiff = System.currentTimeMillis() - time;
        return timeDiff < AlarmManager.INTERVAL_DAY * 30;
    }

    public void updateRating(Integer rating) {
        if (rating == null) {
            return;
        }
        if (this.rating == null) {
            this.rating = rating;
            return;
        }
        this.rating = Math.max(this.rating, rating);
    }

    public long getNumberOfEntriesOfAllGiveaways() {
        return numberOfEntriesOfAllGiveaways;
    }

    public void setNumberOfEntriesOfAllGiveaways(long numberOfEntriesOfAllGiveaways) {
        this.numberOfEntriesOfAllGiveaways = numberOfEntriesOfAllGiveaways;
    }

    public long getNumberOfGiveAways() {
        return numberOfGiveAways;
    }

    public void setNumberOfGiveAways(long numberOfGiveAways) {
        this.numberOfGiveAways = numberOfGiveAways;
    }

    public Boolean getIsBundle() {
        return isBundle;
    }

    public void setIsBundle(Boolean isBundle) {
        this.isBundle = isBundle;
        this.bundleUpdateTime = System.currentTimeMillis();
    }

    public void updateGiveaway(Giveaway giveaway) {
        giveaway.setRating(rating != null ? rating : 0);
        giveaway.setTags(tags);
        giveaway.setJoinCount(joinCount);
        long averageEntries = 0;
        long numberOfGiveAways = this.numberOfGiveAways;
        if (numberOfGiveAways != 0) {
            averageEntries = numberOfEntriesOfAllGiveaways / numberOfGiveAways;
        }
        giveaway.setAverageEntries((int)averageEntries);
        giveaway.setBundleGame(isBundle == null ? false: isBundle);
    }

    public boolean isBundleInformationValid() {
        if (isBundle == null || bundleUpdateTime == null) {
            return false;
        }

        if (isBundle) {
            return true;
        }

//        return false;

        long timeDiff = System.currentTimeMillis() - bundleUpdateTime;
        return timeDiff < AlarmManager.INTERVAL_DAY * 1;
    }
}
