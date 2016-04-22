package net.mabako.steamgifts.data;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;

import net.mabako.steamgifts.adapters.IEndlessAdaptable;
import net.mabako.steamgifts.core.R;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import java.util.Calendar;
import java.util.Collection;
import java.util.Set;

public class Giveaway extends BasicGiveaway implements IEndlessAdaptable {
    private static final long serialVersionUID = 1356878822345232771L;
    public static final int VIEW_LAYOUT = R.layout.giveaway_item;

    /**
     * Note: SteamGifts truncates the giveaway title on the giveaway list, thus this may not be
     * accurate prior to opening the giveaway page. {@link GiveawayExtras#setTitle(String)} is
     * used on the details page to fix the title being cut off.
     */
    private String title;

    private String name;
    private Game game;

    /**
     * Who created this giveaway?
     */
    private String creator;

    private int entries;
    private int copies;
    private int points;
    private int rating;
    private int joinCount;

    /**
     * When was this giveaway created?
     */
    private CustomDateTime createdTime;

    /**
     * When will this giveaway end?
     */
    private CustomDateTime endTime;

    /**
     * Have we entered this giveaway?
     */
    private boolean entered;

    private boolean whitelist, group, isPrivate, regionRestricted;

    /**
     * Level required to enter this giveaway.
     */
    private int level;

    /**
     * Id used (exclusively?) for filtering games.
     */
    private int internalGameId;
    private Set<String> tags;

    public Giveaway() {
        super(null);
    }

    public Giveaway(String giveawayId) {
        super(giveawayId);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getGameId() {
        return game != null ? game.getGameId() : Game.NO_APP_ID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public int getEntries() {
        return entries;
    }

    public void setEntries(int entries) {
        this.entries = entries;
    }

    public int getCopies() {
        if (copies == 0) {
            int index = title.indexOf("Copies");
            if (index != -1) {
                int i = title.indexOf("(");
                if (i != -1) {
                    String value = title.substring(i+1,index-1);
                    try {
                        return Integer.parseInt(value);
                    }
                    catch (Exception ex) {
                    }
                }
            }
            return 1;
        }
        return copies;
    }

    public void setCopies(int copies) {
        this.copies = copies;
    }

    public Game.Type getType() {
        return game != null ? game.getType() : Game.Type.APP;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public String getRelativeEndTime(Context context) {
        return endTime != null ? endTime.toString(context) : null;
    }

    public String getShortRelativeEndTime(Context context) {
        return endTime != null ? endTime.toString(context, true) : null;
    }

    public String getRelativeCreatedTime(Context context) {
        return createdTime != null ? createdTime.toString(context) : null;
    }

    public String getShortRelativeCreatedTime(Context context) {
        return createdTime != null ? createdTime.toString(context, true) : null;
    }

    public boolean isOpen() {
        // FIXME
        return endTime == null || endTime.isInTheFuture();
    }

    public boolean isEntered() {
        return entered;
    }

    public void setEntered(boolean entered) {
        this.entered = entered;
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }

    public boolean isGroup() {
        return group;
    }

    public void setGroup(boolean group) {
        this.group = group;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public boolean isLevelPositive() {
        return level > 0 && SteamGiftsUserData.getCurrent(null).getLevel() >= level;
    }

    public boolean isLevelNegative() {
        return level > 0 && !isLevelPositive();
    }

    public boolean isRegionRestricted() {
        return regionRestricted;
    }

    public void setRegionRestricted(boolean regionRestricted) {
        this.regionRestricted = regionRestricted;
    }

    public Calendar getEndTime() {
        return endTime != null ? endTime.getCalendar() : null;
    }

    public void setEndTime(@NonNull String endTime, @NonNull String relativeEndTime) {
        this.endTime = new CustomDateTime(endTime, relativeEndTime.startsWith("Begins in "));
    }

    public Calendar getCreatedTime() {
        return createdTime != null ? createdTime.getCalendar() : null;
    }

    public void setCreatedTime(@NonNull String createdTime) {
        this.createdTime = new CustomDateTime(createdTime, false);
    }

    @Override
    public String toString() {
        return "[GA " + getGiveawayId() + ", " + getGameId() + "]";
    }

    public int getInternalGameId() {
        return internalGameId;
    }

    public void setInternalGameId(int internalGameId) {
        this.internalGameId = internalGameId;
    }

    @Override
    public int getLayout() {
        return VIEW_LAYOUT;
    }

    @Override
    public boolean equals(Object o) {
        if (getGiveawayId() == null && o instanceof Giveaway) {
            Giveaway g = (Giveaway) o;

            // Compare some random attributes
            return title.equals(g.title) && endTime.equals(g.endTime) && createdTime.equals(g.createdTime);
        } else
            return super.equals(o);
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Game getGame() {
        return game;
    }

    public int getEstimatedEntries() {
        if (createdTime == null) {
            return entries;
        }
        final long timeSinceCreation = Math.abs(Calendar.getInstance().getTimeInMillis() - createdTime.getCalendar().getTimeInMillis()) / 1000;
        final long timeToEnd = Math.abs(endTime.getCalendar().getTimeInMillis() - Calendar.getInstance().getTimeInMillis()) / 1000;

        double entriesPerSecond = (double) entries / timeSinceCreation;
        int additionalEntriesToExpect = (int) (timeToEnd * entriesPerSecond);

        return entries + additionalEntriesToExpect;
    }

    public int getEstimatedEntriesPerCopy() {
        return getEstimatedEntries() / getCopies();
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public Set<String> getTags() {
        return tags;
    }

    public boolean isTagMatching(Collection<String> optionTags) {
        if (tags == null) {
            return false;
        }
        for (String tag : optionTags) {
            if (tags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    public int getJoinCount() {
        return joinCount;
    }

    public void setJoinCount(int joinCount) {
        this.joinCount = joinCount;
    }
}
