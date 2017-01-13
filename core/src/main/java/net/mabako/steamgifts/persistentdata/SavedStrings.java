package net.mabako.steamgifts.persistentdata;

import android.content.Context;

import com.google.gson.Gson;

import net.mabako.steamgifts.data.GameInfo;

public class SavedStrings extends SavedElements<String> {
    static final String DB_TABLE = "strings";

    public SavedStrings(Context context) {
        super(context, DB_TABLE);
    }

    @Override
    protected String getElement(Gson gson, String json) {
        return gson.fromJson(json, String.class);
    }

    @Override
    public int compare(String lhs, String rhs) {
        return lhs.compareTo(rhs);
    }
}
