package net.mabako.steamgifts.tasks;

import android.content.Context;
import android.preference.PreferenceManager;

/**
 * Created by Supa on 29.02.2016.
 */
public class AutoJoinOptions {
    public enum AutoJoinOption {
        SHOW_AUTO_JOIN_RATIO("preferences_autojoin_show_ratio", true),
        AUTO_JOIN_ACTIVATED("preferences_autojoin_activated", true),
        ALWAYS_JOIN_GIVEAWAYS_FOR_BOOKMARKS("preferences_autojoin_always_join_bookmarks", true),
        AUTO_JOIN_ON_NON_WIFI_CONNECTION("preferences_autojoin_on_non_wifi", true),
        PREFER_LESS_ENTRIES("preferences_autojoin_prefer_less_entries", true),
        PREFER_HIGH_VALUE_GAMES("preferences_autojoin_prefer_high_value_games", true),
        PREFER_HIGH_RATING_GAMES("preferences_autojoin_prefer_high_rating_games", true),

        MINIMUM_POINTS_TO_KEEP_FOR_BAD_RATIO("preferences_autojoin_minimum_points_to_keep_for_bad_ratio", 150),
        MINIMUM_POINTS_TO_KEEP_FOR_GREAT_RATIO("preferences_autojoin_minimum_points_to_keep_for_great_ratio", 50),
        BAD_RATIO("preferences_autojoin_bad_ratio", 15),
        GREAT_RATIO("preferences_autojoin_great_ratio", 30),
        MINIMUM_RATING("preferences_autojoin_minimum_rating", 75);

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

    public static int getAvarageRatio(Context context) {
        return (getOptionInteger(context, AutoJoinOption.BAD_RATIO) + getOptionInteger(context, AutoJoinOption.GREAT_RATIO)) / 2;

    }
}
