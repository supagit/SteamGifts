package net.mabako.steamgifts.persistentdata;

import android.content.Context;

import com.google.gson.Gson;

import net.mabako.steamgifts.data.Game;
import net.mabako.steamgifts.data.GameInfo;
import net.mabako.steamgifts.data.Rating;

/**
 * Created by Supa on 14.03.2016.
 */
public class SavedGameInfo extends SavedElements<GameInfo> {
    static final String DB_TABLE = "gameinfo";

    public SavedGameInfo(Context context) {
        super(context, DB_TABLE);
    }

    public GameInfo get(int elementId) {
        return super.get(Integer.toString(elementId));
    }

    public boolean add(GameInfo rating, int elementId) {
        return super.add(rating, Integer.toString(elementId));
    }

    @Override
    protected GameInfo getElement(Gson gson, String json) {
        GameInfo rating = gson.fromJson(json, GameInfo.class);
        return rating;
    }

    @Override
    public int compare(GameInfo lhs, GameInfo rhs) {
        return rhs.getRating() - lhs.getRating();
    }
}
