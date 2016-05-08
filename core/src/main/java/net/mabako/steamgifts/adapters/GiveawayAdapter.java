package net.mabako.steamgifts.adapters;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import net.mabako.steamgifts.adapters.viewholder.GiveawayListItemViewHolder;
import net.mabako.steamgifts.data.Game;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.fragments.ListFragment;
import net.mabako.steamgifts.persistentdata.FilterData;
import net.mabako.steamgifts.persistentdata.SavedGiveaways;
import net.mabako.steamgifts.receivers.CheckForAutoJoin;
import net.mabako.steamgifts.tasks.AutoJoinCalculator;
import net.mabako.steamgifts.tasks.AutoJoinOptions;
import net.mabako.steamgifts.tasks.AutoJoinTask;
import net.mabako.steamgifts.tasks.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

public class GiveawayAdapter extends EndlessAdapter {
    private static final long serialVersionUID = 4291118458389025091L;

    /**
     * Giveaways that are shown per page.
     */
    private final int itemsPerPage;

    /**
     * Context of this adapter.
     */
    private transient Activity context;

    /**
     * Fragment this is shown in.
     */
    private transient Fragment fragment;

    /**
     * Should we filter the item for any criteria?
     */
    private final boolean filterItems;

    /**
     * Instance of SavedGiveaways to save/unsave giveaways.
     */
    private transient SavedGiveaways savedGiveaways;

    /**
     * Should we load images for this list?
     */
    private final boolean loadImages;
    private transient AutoJoinCalculator autoJoinCalculator;

    public GiveawayAdapter(Activity context, int itemsPerPage, SharedPreferences sharedPreferences) {
        this(context, itemsPerPage, false, sharedPreferences);
    }

    public GiveawayAdapter(Activity context, int itemsPerPage, boolean filterItems, SharedPreferences sharedPreferences) {
        this.context = context;
        this.itemsPerPage = itemsPerPage;
        this.filterItems = filterItems;
        if (this.context != null && sharedPreferences.getString("preference_giveaway_load_images", "details;list").contains("wifi")) {
            this.loadImages = Utils.isConnectedToWifi("loadImage", this.context);
        } else {
            this.loadImages = sharedPreferences.getString("preference_giveaway_load_images", "details;list").contains("list");
        }

        autoJoinCalculator = new AutoJoinCalculator(context, 0);
    }

    public void setFragmentValues(@NonNull Activity activity, @NonNull ListFragment fragment, SavedGiveaways savedGiveaways) {
        setLoadListener(fragment);
        this.context = activity;
        this.fragment = fragment;
        this.savedGiveaways = savedGiveaways;
    }

    @Override
    protected RecyclerView.ViewHolder onCreateActualViewHolder(View view, int viewType) {
        return new GiveawayListItemViewHolder(view, context, this, fragment, savedGiveaways);
    }

    @Override
    public void onBindActualViewHolder(RecyclerView.ViewHolder h, int position) {
        if (h instanceof GiveawayListItemViewHolder) {
            GiveawayListItemViewHolder holder = (GiveawayListItemViewHolder) h;
            Giveaway giveaway = (Giveaway) getItem(position);

            holder.setFrom(giveaway, loadImages);
        }
    }

    @Override
    protected boolean hasEnoughItems(List<IEndlessAdaptable> items) {
        return items.size() >= itemsPerPage;
    }

    public Giveaway findItem(@NonNull String giveawayId) {
        for (IEndlessAdaptable adaptable : getItems()) {
            Giveaway giveaway = (Giveaway) adaptable;
            if (giveaway != null && giveawayId.equals(giveaway.getGiveawayId()))
                return giveaway;
        }
        return null;
    }

    public void removeGiveaway(String giveawayId) {
        for (int position = getItems().size() - 1; position >= 0; --position) {
            Giveaway giveaway = (Giveaway) getItem(position);

            if (giveaway != null && giveawayId.equals(giveaway.getGiveawayId())) {
                removeItem(position);
            }
        }
    }

    public List<RemovedElement> removeHiddenGame(int internalGameId) {
        if (internalGameId == Game.NO_APP_ID)
            throw new IllegalStateException();

        List<EndlessAdapter.RemovedElement> removedElements = new ArrayList<>();
        for (int position = getItems().size() - 1; position >= 0; --position) {
            Giveaway giveaway = (Giveaway) getItem(position);

            if (giveaway != null && giveaway.getInternalGameId() == internalGameId) {
                removedElements.add(removeItem(position));
            }
        }

        // At this point, the first element in removedElements is actually the last element of the original adapter, since we went through it in reverse; so we ... reverse it.
        // Since we store the element and the one before it, we can reasonably get rid of a series of successive giveaways and restore them in the original order.
        Collections.reverse(removedElements);
        return removedElements;
    }

    @Override
    protected int addFiltered(List<IEndlessAdaptable> items) {
        if (filterItems && fragment != null) {
            FilterData fd = FilterData.getCurrent(fragment.getContext());

            boolean hideGamesWithBadRating = AutoJoinOptions.isOptionBoolean(context, AutoJoinOptions.AutoJoinOption.HIDE_GAMES_WITH_BAD_RATING);
            int minimumRating = AutoJoinOptions.getOptionInteger(context, AutoJoinOptions.AutoJoinOption.MINIMUM_RATING);

            int minPoints = fd.getMinPoints();
            int maxPoints = fd.getMaxPoints();

            boolean hideEntered = fd.isHideEntered();
            boolean hideIgnored = fd.isHideIgnored();
            boolean hideBlacklisted = fd.isHideBlacklisted();

            boolean checkLevelOnlyOnPublicGiveaway = fd.isRestrictLevelOnlyOnPublicGiveaways();
            int minLevel = fd.getMinLevel();
            int maxLevel = fd.getMaxLevel();

            boolean entriesPerCopy = fd.isEntriesPerCopy();
            int minEntries = fd.getMinEntries();
            int maxEntries = fd.getMaxEntries();

            if (minPoints >= 0 || maxPoints >= 0
                    || hideEntered
                    || hideGamesWithBadRating
                    || (checkLevelOnlyOnPublicGiveaway && (minLevel >= 0 || maxLevel >= 0))
                    || (entriesPerCopy && (minEntries >= 0 || maxEntries >= 0))) {
                // Let's actually perform filtering if we have any options set.
                for (ListIterator<IEndlessAdaptable> iter = items.listIterator(items.size()); iter.hasPrevious(); ) {
                    Giveaway giveaway = (Giveaway) iter.previous();
                    int points = giveaway.getPoints();
                    int level = giveaway.getLevel();
                    int entriesPerCopyValue = giveaway.getEntries() / giveaway.getCopies();

                    if (hideEntered && giveaway.isEntered()) {
                        iter.remove();
                    } else if (hideIgnored && autoJoinCalculator.isIgnoreListGame(giveaway.getGameId())) {
                        iter.remove();
                    } else if (hideBlacklisted && (autoJoinCalculator.isBlackListedGame(giveaway.getGameId()) || autoJoinCalculator.hasBlackListedTag(giveaway))) {
                        iter.remove();
                    } else if (points >= 0 && ((minPoints >= 0 && points < minPoints) || (maxPoints >= 0 && points > maxPoints))) {
                        iter.remove();
                    } else if (checkLevelOnlyOnPublicGiveaway && !giveaway.isGroup() && !giveaway.isWhitelist() && ((minLevel >= 0 && level < minLevel) || (maxLevel >= 0 && level > maxLevel))) {
                        iter.remove();
                    } else if (entriesPerCopy && (minEntries >= 0 && entriesPerCopyValue < minEntries) || (maxEntries >= 0 && entriesPerCopyValue > maxEntries)) {
                        iter.remove();
                    } else if (hideGamesWithBadRating && giveaway.getRating() < minimumRating) {
                        iter.remove();
                    }
                }
            }
        }
        return super.addFiltered(items);
    }

    @Override
    public void finishLoading(List<IEndlessAdaptable> addedItems) {
        super.finishLoading(addedItems);

        List<Giveaway> giveaways = new ArrayList<>();
        for (IEndlessAdaptable item : getItems()) {
            if (item instanceof Giveaway) {
                Giveaway giveaway = (Giveaway) item;
                giveaways.add(giveaway);
            }
        }

        List<Giveaway> giveawaysToJoin = new AutoJoinCalculator(context, CheckForAutoJoin.AUTO_JOIN_PERIOD).calculateGiveawaysToJoin(giveaways);
        for (int joinOrder = 0; joinOrder < giveawaysToJoin.size(); joinOrder++) {
            Giveaway giveaway = giveawaysToJoin.get(joinOrder);
            int effectivJoinOrder = joinOrder + 1;
            giveaway.setJoinOrderText("[" + effectivJoinOrder + "/" + giveawaysToJoin.size() + "]");
            notifyItemChanged(giveaway);
        }
    }
}
