package net.mabako.steamgifts.tasks;

import android.content.Context;
import android.graphics.Color;

import net.mabako.steamgifts.data.Giveaway;

/**
 * Created by Supa on 29.02.2016.
 */
public class AutoJoinUtils {
    private static int READIBLE_ENTRY_RATIO_FACTOR = 5000;

    public static double calculateReadibleEntryRatio(Context context, Giveaway giveaway) {
        double factor = 1.0;

        int minimumRating = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_RATING);
        boolean minimumRatingPresent = giveaway.getRating() >= minimumRating;
        if (!minimumRatingPresent) {
            return 0;
        }

        if (AutoJoinOptions.isOptionBoolean(context, AutoJoinOptions.AutoJoinOption.PREFER_HIGH_RATING_GAMES)) {
            int rating = giveaway.getRating();
            if (rating == 0) {
                rating = 50;
            }
            int diffTo75 = rating - 80;
            factor *= ((double) diffTo75 * 2 + 100) / 100;
        }
        if (AutoJoinOptions.isOptionBoolean(context, AutoJoinOptions.AutoJoinOption.PREFER_HIGH_VALUE_GAMES)) {
            factor *= (double) giveaway.getPoints() / 10;
        }

        if (AutoJoinOptions.isOptionBoolean(context, AutoJoinOptions.AutoJoinOption.PREFER_LESS_ENTRIES)) {
            return giveaway.getEntryRatio() * factor * READIBLE_ENTRY_RATIO_FACTOR;
        } else {
            //factor += 0.25;
            return factor * factor * factor * 10;
        }
    }


    public static int calculateRatioColor(Context context, Giveaway giveaway) {
        double ratio = calculateReadibleEntryRatio(context, giveaway) / AutoJoinOptions.getAvarageRatio(context);
        return calculateRatioColor(ratio * 2 / 3);
    }

    private static int calculateRatioColor(double power) {
        if (power >= 1) {
            power = 1;
        }
        int R = (int) (255 * (1 - power));
        int G = (int) (255 * power);
        int B = 0;

        return Color.argb(255, R, G, B);
    }


}
