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
        if (AutoJoinOptions.isOptionBoolean(context, AutoJoinOptions.AutoJoinOption.PREFER_HIGH_VALUE_GAMES)) {
            return giveaway.getEntryRatio() * giveaway.getPoints()  * READIBLE_ENTRY_RATIO_FACTOR / 10;
        }
        return giveaway.getEntryRatio() * READIBLE_ENTRY_RATIO_FACTOR;
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
