package net.mabako.steamgifts.persistentdata;

import android.content.Context;

import com.google.gson.Gson;

import net.mabako.steamgifts.data.GameInfo;

/**
 * Created by Supa on 17.03.2016.
 */
public class SavedGamesWhiteListTags extends SavedElements<String> {
    static final String DB_TABLE = "whitelisttags";

    public SavedGamesWhiteListTags(Context context) {
        super(context, DB_TABLE);
    }

    @Override
    protected String getElement(Gson gson, String json) {
        String rating = gson.fromJson(json, String.class);
        return rating;
    }

    @Override
    public int compare(String lhs, String rhs) {
        return rhs.compareTo(lhs);
    }
}
