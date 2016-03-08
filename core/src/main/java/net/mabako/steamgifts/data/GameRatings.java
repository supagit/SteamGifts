package net.mabako.steamgifts.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Supa on 08.03.2016.
 */
public class GameRatings {
    private Map<Integer, Integer> gameIdRatingMap = new HashMap<>();

    public Map<Integer, Integer> getGameIdRatingMap() {
        return gameIdRatingMap;
    }

    public void setGameIdRatingMap(Map<Integer, Integer> gameIdRatingMap) {
        this.gameIdRatingMap = gameIdRatingMap;
    }
}
