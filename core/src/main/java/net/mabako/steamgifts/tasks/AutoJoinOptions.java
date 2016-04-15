package net.mabako.steamgifts.tasks;

import android.content.Context;
import android.preference.PreferenceManager;

import net.mabako.steamgifts.data.Giveaway;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Supa on 29.02.2016.
 */
public class AutoJoinOptions {
    public enum AutoJoinOption {
        AUTO_JOIN_ACTIVATED("preferences_autojoin_activated", true),
        ALWAYS_JOIN_GIVEAWAYS_FOR_BOOKMARKS("preferences_autojoin_always_join_bookmarks", true),
        AUTO_JOIN_ON_NON_WIFI_CONNECTION("preferences_autojoin_on_non_wifi", true),

        MINIMUM_POINTS_TO_KEEP_FOR_UNTAGGED("preferences_autojoin_minimum_points_to_keep_for_untagged", 150),
        MINIMUM_POINTS_TO_KEEP_FOR_GREAT_RATIO("preferences_autojoin_minimum_points_to_keep_for_tagged", 50),
        MINIMUM_POINTS_TO_KEEP_FOR_NOT_MEETING_LEVEL("preferences_autojoin_minimum_points_to_keep_for_not_meeting_level", 50),
        MINIMUM_RATING("preferences_autojoin_minimum_rating", 75),
        HIDE_GAMES_WITH_BAD_RATING("preferences_hide_low_rating_games", false),
        AUTO_JOIN_POINTS("preferences_autojoin_games_with_points",30);

        private String preference;
        private Integer defaultInteger;
        private boolean defaultBoolean;

        AutoJoinOption(String preference, boolean defaultBoolean) {
            this.preference = preference;
            this.defaultBoolean = defaultBoolean;
        }

        AutoJoinOption(String preference, Integer defaultInteger) {
            this.preference = preference;
            this.defaultInteger = defaultInteger;
        }

        public String getPreference() {
            return preference;
        }

        public boolean getDefaultBoolean() {
            return defaultBoolean;
        }

        public Integer getDefaultInteger() {
            return defaultInteger;
        }
    }

    public static boolean isOptionBoolean(Context context, AutoJoinOption option) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(option.getPreference(), option.getDefaultBoolean());
    }

    public static int getOptionInteger(Context context, AutoJoinOption option) {
        try {
            String stringValue = PreferenceManager.getDefaultSharedPreferences(context).getString(option.getPreference(), option.getDefaultInteger().toString());
            return Integer.parseInt(stringValue);
        } catch (Exception ex) {
            return option.getDefaultInteger();
        }
    }
}
