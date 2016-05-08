package net.mabako.steamgifts.persistentdata;

import android.content.Context;

import com.google.gson.Gson;

import net.mabako.steamgifts.data.GameInfo;

/**
 * Created by Supa on 17.03.2016.
 */
public class SavedIgnoreList extends SavedElements<GameInfo> {
    static final String DB_TABLE = "ignorelist";

    public SavedIgnoreList(Context context) {
        super(context, DB_TABLE);
    }

    public GameInfo get(int elementId) {
        return super.get(Integer.toString(elementId));
    }

    public boolean remove(int elementId) {
        return super.remove(Integer.toString(elementId));
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
