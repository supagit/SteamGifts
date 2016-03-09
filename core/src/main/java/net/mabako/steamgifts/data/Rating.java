package net.mabako.steamgifts.data;

import android.app.AlarmManager;

import net.mabako.steamgifts.adapters.IEndlessAdaptable;

/**
 * Created by Supa on 09.03.2016.
 */
public class Rating {
    private long time;
    private int rating;
    private int gameId;

    public Rating(long time, int rating, int gameId) {
        this.time = time;
        this.rating = rating;
        this.gameId = gameId;
    }

    @Override
    public int hashCode() {
        return gameId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof BasicGiveaway))
            return false;

        return gameId == ((Rating) o).gameId;
    }

    public long getTime() {
        return time;
    }

    public int getRating() {
        return rating;
    }

    public int getGameId() {
        return gameId;
    }

    public boolean isRatingValid() {
        if (rating != 0) {
            return true;
        }
        long timeDiff = System.currentTimeMillis() - time;
        return timeDiff >= AlarmManager.INTERVAL_DAY*30*3;
    }
}
