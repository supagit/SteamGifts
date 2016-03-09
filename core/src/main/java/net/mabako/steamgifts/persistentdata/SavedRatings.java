package net.mabako.steamgifts.persistentdata;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.gson.Gson;

import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.data.Rating;

/**
 * Created by Supa on 09.03.2016.
 */
public class SavedRatings extends SavedElements<Rating> {
    static final String DB_TABLE = "ratings";

    public SavedRatings(Context context) {
        super(context, DB_TABLE);
    }

    public Rating get(int elementId) {
        return super.get(Integer.toString(elementId));
    }

    public boolean add(Rating rating, int elementId) {
        return super.add(rating, Integer.toString(elementId));
    }

    @Override
    protected Rating getElement(Gson gson, String json) {
        Rating rating = gson.fromJson(json, Rating.class);
        return rating;
    }

    @Override
    public int compare(Rating lhs, Rating rhs) {
        return rhs.getRating() - lhs.getRating();
    }
}
