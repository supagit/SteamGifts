package net.mabako.steamgifts.adapters.viewholder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import net.mabako.steamgifts.activities.DetailActivity;
import net.mabako.steamgifts.activities.MainActivity;
import net.mabako.steamgifts.adapters.EndlessAdapter;
import net.mabako.steamgifts.adapters.IEndlessAdaptable;
import net.mabako.steamgifts.core.R;
import net.mabako.steamgifts.data.BasicGiveaway;
import net.mabako.steamgifts.data.Game;
import net.mabako.steamgifts.data.GameInfo;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.data.Statistics;
import net.mabako.steamgifts.fragments.GiveawayDetailFragment;
import net.mabako.steamgifts.fragments.GiveawayListFragment;
import net.mabako.steamgifts.fragments.SavedGiveawaysFragment;
import net.mabako.steamgifts.fragments.interfaces.IHasEnterableGiveaways;
import net.mabako.steamgifts.persistentdata.SavedGameInfo;
import net.mabako.steamgifts.persistentdata.SavedGiveaways;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;
import net.mabako.steamgifts.tasks.AutoJoinCalculator;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class GiveawayListItemViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnCreateContextMenuListener, MenuItem.OnMenuItemClickListener {
    private static final String TAG = GiveawayListItemViewHolder.class.getSimpleName();

    private final View itemContainer;
    private final TextView giveawayDetails;
    private final TextView giveawayJoinOrder;
    private final TextView giveawayName;
    private final TextView giveawayRatio;
    private final TextView giveawayTime;
    private final ImageView giveawayImage;

    private final EndlessAdapter adapter;
    private final Activity activity;
    private final Fragment fragment;
    private SavedGiveaways savedGiveaways;

    private final View indicatorWhitelist, indicatorGroup, indicatorLevelPositive, indicatorLevelNegative, indicatorPrivate, indicatorRegionRestricted;

    private static int measuredHeight = 0;
    private final Context context;
    private final AutoJoinCalculator autoJoinCalculator;
    private final SavedGameInfo savedGameInfo;

    public GiveawayListItemViewHolder(View v, Activity activity, EndlessAdapter adapter, Fragment fragment, SavedGiveaways savedGiveaways) {
        super(v);
        itemContainer = v.findViewById(R.id.list_item);
        giveawayName = (TextView) v.findViewById(R.id.giveaway_name);
        giveawayRatio = (TextView) v.findViewById(R.id.giveaway_ratio);
        giveawayDetails = (TextView) v.findViewById(R.id.giveaway_details);
        giveawayJoinOrder = (TextView) v.findViewById(R.id.giveaway_join_order);

        giveawayTime = (TextView) v.findViewById(R.id.time);
        giveawayImage = (ImageView) v.findViewById(R.id.giveaway_image);

        indicatorWhitelist = v.findViewById(R.id.giveaway_list_indicator_whitelist);
        indicatorGroup = v.findViewById(R.id.giveaway_list_indicator_group);
        indicatorLevelPositive = v.findViewById(R.id.giveaway_list_indicator_level_positive);
        indicatorLevelNegative = v.findViewById(R.id.giveaway_list_indicator_level_negative);
        indicatorPrivate = v.findViewById(R.id.giveaway_list_indicator_private);
        indicatorRegionRestricted = v.findViewById(R.id.giveaway_list_indicator_region_restricted);

        this.activity = activity;
        this.context = activity.getBaseContext();
        this.fragment = fragment;
        this.adapter = adapter;
        this.savedGiveaways = savedGiveaways;

        v.setOnClickListener(this);
        v.setOnCreateContextMenuListener(this);

        autoJoinCalculator = new AutoJoinCalculator(context, 0);
        savedGameInfo = new SavedGameInfo(context);
    }


    public void setFrom(Giveaway giveaway, boolean showImage) {
        String title = giveaway.getTitle();
        if (giveaway.getCopies() > 1) {
            title = giveaway.getCopies() + "x " + title;
        }
        giveawayJoinOrder.setText("");
        giveawayName.setText(title);
        giveawayRatio.setText(giveaway.getJoinOrderText());

        if (giveaway.getEndTime() != null) {
            String endTimeText = giveaway.getShortRelativeEndTime(activity);
            if (giveaway.getShortRelativeCreatedTime(activity) != null) {
                endTimeText += " (" + giveaway.getShortRelativeCreatedTime(activity) + ")";
            }
            giveawayTime.setText(endTimeText);
        } else {
            giveawayTime.setVisibility(View.GONE);
        }

        StringBuilder sb = new StringBuilder();
        if (giveaway.getRating() != 0)
            sb.append(giveaway.getRating()).append("% | ");

        if (giveaway.getPoints() >= 0)
            sb.append(giveaway.getPoints()).append("P | ");

        if (giveaway.getJoinCount() > 0) {
            sb.append(giveaway.getJoinCount()).append("J | ");
        }

        int level = autoJoinCalculator.calculateLevel(giveaway);
        if (level > 0) {
            int giveawayLevel = giveaway.getLevel();

            if (giveawayLevel == level) {
                sb.append("L").append(level).append(" | ");
            } else {
                sb.append("L").append(level).append(" (L").append(giveawayLevel).append(")").append(" | ");
            }
        }


        if (giveaway.getEntries() >= 0) {
            int estimatedEntries = giveaway.getEstimatedEntries();
//            sb.append(estimatedEntries).append(" (~").append(giveaway.getAverageEntries()).append(")").append(" entries").append(" | ");
            sb.append(activity.getResources().getQuantityString(R.plurals.entries, estimatedEntries, estimatedEntries)).append(" | ");
        }
        giveawayDetails.setText(sb.length() > 3 ? sb.substring(0, sb.length() - 3) : sb.toString());

        // giveaway_image
        if (giveaway.getGameId() != Game.NO_APP_ID && showImage) {
            Picasso.with(activity).load("http://cdn.akamai.steamstatic.com/steam/" + giveaway.getType().name().toLowerCase(Locale.ENGLISH) + "s/" + giveaway.getGameId() + "/capsule_184x69.jpg").into(giveawayImage, new Callback() {
                /**
                 * We manually set the height of this image to fit the container.
                 */
                @Override
                public void onSuccess() {
                    if (measuredHeight <= 0)
                        measuredHeight = itemContainer.getMeasuredHeight();

                    ViewGroup.LayoutParams params = giveawayImage.getLayoutParams();
                    params.height = measuredHeight;
                }

                @Override
                public void onError() {
                    ViewGroup.LayoutParams params = giveawayImage.getLayoutParams();
                    params.height = 0;
                }
            });
        } else {
            giveawayImage.setImageResource(android.R.color.transparent);
            ViewGroup.LayoutParams params = giveawayImage.getLayoutParams();
            params.height = 0;
        }

        if (autoJoinCalculator.isBlackListedGame(giveaway.getGameId())) {
            StringUtils.setBackgroundDrawable(activity, itemContainer, true, R.attr.colorBlackListed);
        } else if (autoJoinCalculator.isMustHaveListedGameOrUnbundledOrGroup(giveaway)) {
            StringUtils.setBackgroundDrawable(activity, itemContainer, true, R.attr.colorMustHave);
        } else if (autoJoinCalculator.isWhiteListedGame(giveaway.getGameId())) {

            if (autoJoinCalculator.isMatchingWhiteListLevel(giveaway)) {
                StringUtils.setBackgroundDrawable(activity, itemContainer, true, R.attr.colorWhitelisted);
            } else {
                StringUtils.setBackgroundDrawable(activity, itemContainer, true, R.attr.colorWhitelistedNotLevel);
            }
        } else if (autoJoinCalculator.hasGreatDemand(giveaway)) {
            StringUtils.setBackgroundDrawable(activity, itemContainer, true, R.attr.colorPoint);
        } else if (autoJoinCalculator.hasBlackListedTag(giveaway)) {
            StringUtils.setBackgroundDrawable(activity, itemContainer, true, R.attr.colorBlackListed);
        } else if (autoJoinCalculator.isIgnoreListGame(giveaway.getGameId())) {
            StringUtils.setBackgroundDrawable(activity, itemContainer, true, R.attr.colorIgnore);
        } else {
            StringUtils.setBackgroundDrawable(activity, itemContainer, autoJoinCalculator.isTagMatching(giveaway));
        }

        // Check all the indicators
        indicatorLevelNegative.setVisibility(giveaway.isLevelNegative() ? View.VISIBLE : View.GONE);
        indicatorLevelPositive.setVisibility(autoJoinCalculator.isMatchingLevel(giveaway) ? View.VISIBLE : View.GONE);
//        indicatorWhitelist.setVisibility(!autoJoinCalculator.isMatchingLevel(giveaway) && giveaway.isLevelPositive() ? View.VISIBLE : View.GONE);
//        indicatorGroup.setVisibility(View.GONE);
//        indicatorPrivate.setVisibility(View.GONE);
        indicatorPrivate.setVisibility(!giveaway.isBundleGame() ? View.VISIBLE : View.GONE);
        indicatorRegionRestricted.setVisibility(View.GONE);

        indicatorWhitelist.setVisibility(giveaway.isWhitelist() ? View.VISIBLE : View.GONE);
        indicatorGroup.setVisibility(giveaway.isGroup() ? View.VISIBLE : View.GONE);
//        indicatorLevelPositive.setVisibility(giveaway.isLevelPositive() ? View.VISIBLE : View.GONE);
//        indicatorLevelNegative.setVisibility(giveaway.isLevelNegative() ? View.VISIBLE : View.GONE);
//        indicatorPrivate.setVisibility(giveaway.isPrivate() ? View.VISIBLE : View.GONE);
//        indicatorRegionRestricted.setVisibility(giveaway.isRegionRestricted() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClick(View v) {
        Giveaway giveaway = (Giveaway) adapter.getItem(getAdapterPosition());
        if (giveaway.getGiveawayId() != null && giveaway.getName() != null) {
            Intent intent = new Intent(activity, DetailActivity.class);

            if (giveaway.getInternalGameId() != Game.NO_APP_ID) {
                intent.putExtra(GiveawayDetailFragment.ARG_GIVEAWAY, giveaway);
            } else {
                intent.putExtra(GiveawayDetailFragment.ARG_GIVEAWAY, new BasicGiveaway(giveaway.getGiveawayId()));
            }

            activity.startActivityForResult(intent, MainActivity.REQUEST_LOGIN_PASSIVE);
        } else {
            Toast.makeText(activity, R.string.private_giveaway, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        // Are we logged in & do we have a token to submit with our "form"?
        if (fragment != null && (adapter.getXsrfToken() != null || savedGiveaways != null)) {

            // Which giveaway is this even for?
            final Giveaway giveaway = (Giveaway) adapter.getItem(getAdapterPosition());

            // We only know this giveaway exists, not the link to it.
            if (giveaway.getGiveawayId() == null || giveaway.getName() == null)
                return;

            boolean xsrfEvents = adapter.getXsrfToken() != null && giveaway.isOpen();
            boolean loggedIn = SteamGiftsUserData.getCurrent(null).isLoggedIn();

            // Header
            menu.setHeaderTitle(giveaway.getTitle() + " ~" + giveaway.getAverageEntries());

            if (loggedIn && xsrfEvents && fragment instanceof IHasEnterableGiveaways) {
                // Text for Entering or Leaving the giveaway
                String enterText = activity.getString(R.string.enter_giveaway);
                String leaveText = activity.getString(R.string.leave_giveaway);

                // Include the points if we know
                if (giveaway.getPoints() >= 0) {
                    enterText = String.format(activity.getString(R.string.enter_giveaway_with_points), giveaway.getPoints());
                    leaveText = String.format(activity.getString(R.string.leave_giveaway_with_points), giveaway.getPoints());
                }

                // Show the relevant menu item.
                if (giveaway.isEntered()) {
                    menu.add(Menu.NONE, 1, Menu.NONE, leaveText).setOnMenuItemClickListener(this);
                } else {
                    menu.add(Menu.NONE, 2, Menu.NONE, enterText).setOnMenuItemClickListener(this).setEnabled(giveaway.getPoints() <= SteamGiftsUserData.getCurrent(null).getPoints() && giveaway.getLevel() <= SteamGiftsUserData.getCurrent(null).getLevel() && !SteamGiftsUserData.getCurrent(null).getName().equals(giveaway.getCreator()));
                }
            }

//            // Save/Un-save a game
//            if (savedGiveaways != null && giveaway.getEndTime() != null) {
//                if (!savedGiveaways.exists(giveaway.getGiveawayId())) {
//                    menu.add(Menu.NONE, 4, Menu.NONE, R.string.add_saved_giveaway).setOnMenuItemClickListener(this);
//                } else {
//                    menu.add(Menu.NONE, 5, Menu.NONE, R.string.remove_saved_giveaway).setOnMenuItemClickListener(this);
//                }
//            }

            menu.add(Menu.NONE, 6, Menu.NONE, R.string.show_tags).setOnMenuItemClickListener(this);

            if (autoJoinCalculator.isMustHaveListedGame(giveaway.getGameId())) {
                menu.add(Menu.NONE, 11, Menu.NONE, "Remove from Must-Have").setOnMenuItemClickListener(this);
            } else {
                menu.add(Menu.NONE, 12, Menu.NONE, "Add to Must-Have").setOnMenuItemClickListener(this);
            }

            if (autoJoinCalculator.isWhiteListedGame(giveaway.getGameId())) {
                menu.add(Menu.NONE, 9, Menu.NONE, "Remove from Whitelist").setOnMenuItemClickListener(this);
            } else {
                menu.add(Menu.NONE, 10, Menu.NONE, "Add to Whitelist").setOnMenuItemClickListener(this);
            }

            if (autoJoinCalculator.isBlackListedGame(giveaway.getGameId())) {
                menu.add(Menu.NONE, 7, Menu.NONE, "Remove from Blacklist").setOnMenuItemClickListener(this);
            } else {
                menu.add(Menu.NONE, 8, Menu.NONE, "Add to Blacklist").setOnMenuItemClickListener(this);
            }

            if (autoJoinCalculator.isIgnoreListGame(giveaway.getGameId())) {
                menu.add(Menu.NONE, 13, Menu.NONE, "Remove from Ignore List").setOnMenuItemClickListener(this);
            } else {
                menu.add(Menu.NONE, 14, Menu.NONE, "Add to Ignore List").setOnMenuItemClickListener(this);
            }

            if (!giveaway.isBundleGame()) {
                menu.add(Menu.NONE, 15, Menu.NONE, "Set Bundle Info").setOnMenuItemClickListener(this);
            }

//            // Hide a game... forever
//            if (loggedIn && xsrfEvents && giveaway.getInternalGameId() > 0 && fragment instanceof GiveawayListFragment) {
//                menu.add(Menu.NONE, 3, Menu.NONE, R.string.hide_game).setOnMenuItemClickListener(this);
//            }
        } else {
            Log.d(TAG, "Not showing context menu for giveaway. (xsrf-token: " + adapter.getXsrfToken() + ")");
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        Giveaway giveaway = (Giveaway) adapter.getItem(getAdapterPosition());
        if (giveaway == null) {
            Toast.makeText(fragment.getContext(), "Error, please try again.", Toast.LENGTH_SHORT).show();
            return false;
        }

        Log.d(TAG, "onMenuItemClick(" + item.getItemId() + ")");
        switch (item.getItemId()) {
            case 1:
                new Statistics(context).addGiveaway(giveaway);
                ((IHasEnterableGiveaways) fragment).requestEnterLeave(giveaway.getGiveawayId(), GiveawayDetailFragment.ENTRY_DELETE, adapter.getXsrfToken());
                return true;
            case 2:
                new Statistics(context).removeGiveaway(giveaway);
                ((IHasEnterableGiveaways) fragment).requestEnterLeave(giveaway.getGiveawayId(), GiveawayDetailFragment.ENTRY_INSERT, adapter.getXsrfToken());
                return true;
            case 3:
                ((GiveawayListFragment) fragment).requestHideGame(giveaway.getInternalGameId(), giveaway.getTitle());
                return true;
            case 4:
                if (savedGiveaways.add(giveaway, giveaway.getGiveawayId())) {
                    Toast.makeText(fragment.getContext(), R.string.added_saved_giveaway, Toast.LENGTH_SHORT).show();
                }
                return true;
            case 5:
                if (savedGiveaways.remove(giveaway.getGiveawayId())) {
                    Toast.makeText(fragment.getContext(), R.string.removed_saved_giveaway, Toast.LENGTH_SHORT).show();
                    if (fragment instanceof SavedGiveawaysFragment)
                        ((SavedGiveawaysFragment) fragment).onRemoveSavedGiveaway(giveaway.getGiveawayId());
                }
                return true;
            case 6: {
//
//                SavedErrors savedErrors = new SavedErrors(context);
//                List<String> all = savedErrors.all();
//                for (String errorString : all) {
//                    System.err.println(errorString);
//                }
//                savedErrors.removeAll();
//                savedErrors.close();

                Set<String> blackListTags = new HashSet<>(autoJoinCalculator.getBlackListTags());
                Set<String> whiteListTags = new HashSet<>(autoJoinCalculator.getWhiteListTags());

                Set<String> tags = new HashSet<>(giveaway.getTags());

                Set<String> blackListed = generateIntersetingTags(blackListTags, tags);
                Set<String> whiteListed = generateIntersetingTags(whiteListTags, tags);

                tags.removeAll(blackListed);
                tags.removeAll(whiteListed);

                String blackTagText = generateTagString(blackListed);
                String whiteTagText = generateTagString(whiteListed);
                String tagText = generateTagString(tags);

                String text = "";

                if (!blackListed.isEmpty()) {
                    text += "blackListed:\n" + blackTagText + "\n";
                }

                if (!whiteListed.isEmpty()) {
                    text += "whiteListed:\n" + whiteTagText + "\n";
                }

                text += tagText;

                Toast.makeText(fragment.getContext(), text, Toast.LENGTH_LONG).show();
            }
            return true;
            case 7:
                autoJoinCalculator.removeFromGamesBlackList(giveaway.getGameId());
                refreshAdapterForGameId(giveaway.getGameId());
                return true;
            case 8:
                autoJoinCalculator.addToGamesBlackList(giveaway.getGameId());
                refreshAdapterForGameId(giveaway.getGameId());
                return true;
            case 9:
                autoJoinCalculator.removeFromGamesWhiteList(giveaway.getGameId());
                refreshAdapterForGameId(giveaway.getGameId());
                return true;
            case 10:
                autoJoinCalculator.addToGamesWhiteList(giveaway.getGameId());
                refreshAdapterForGameId(giveaway.getGameId());
                return true;
            case 11:
                autoJoinCalculator.removeFromMustHaveWhiteList(giveaway.getGameId());
                refreshAdapterForGameId(giveaway.getGameId());
                return true;
            case 12:
                autoJoinCalculator.addToGamesMustHaveList(giveaway.getGameId());
                refreshAdapterForGameId(giveaway.getGameId());
                return true;
            case 13:
                autoJoinCalculator.removeFromIgnoreList(giveaway.getGameId());
                refreshAdapterForGameId(giveaway.getGameId());
                return true;
            case 14:

                if (autoJoinCalculator.isWhiteListedGame(giveaway.getGameId())
                        || autoJoinCalculator.isMustHaveListedGame(giveaway.getGameId())
                        || autoJoinCalculator.isMustHaveListedGameOrUnbundledOrGroup(giveaway)
                        ) {
                    Toast.makeText(activity, "Cannot Ignore giveaway on white or musthave list", Toast.LENGTH_SHORT).show();
                    return true;
                }

                autoJoinCalculator.addToIgnoreList(giveaway.getGameId());
                refreshAdapterForGameId(giveaway.getGameId());
                return true;

            case 15: {
                GameInfo gameInfo = savedGameInfo.get(giveaway.getGameId());
                gameInfo.setIsBundle(true);
                savedGameInfo.add(gameInfo, giveaway.getGameId());
                giveaway.setBundleGame(true);
                refreshAdapterForGameId(giveaway.getGameId());
            }
                return true;

        }
        return false;
    }

    private void refreshAdapterForGameId(int gameId) {
//        FilterData filterData = FilterData.getCurrent(context);///
//
//        boolean hideIgnored = filterData.isHideIgnored();
//        boolean hideBlacklisted = filterData.isHideBlacklisted();

        for (int i = 0; i < adapter.getItemCount(); i++) {
            IEndlessAdaptable item = adapter.getItem(i);

            if (item instanceof Giveaway) {
                Giveaway giveaway = (Giveaway) item;
                if (giveaway.getGameId() == gameId) {
                    adapter.notifyItemChanged(item);
                }
            }
        }
    }

    @NonNull
    private Set<String> generateIntersetingTags(Set<String> blackListTags, Set<String> tags) {
        Set<String> blackListed = new HashSet<>();
        for (String tag : tags) {
            if (blackListTags.contains(tag)) {
                blackListed.add(tag);
            }
        }
        return blackListed;
    }

    @NonNull
    private String generateTagString(Set<String> tags) {
        String tagText = "";

        if (tags != null) {
            for (String tag : tags) {
                tagText += tag + "\n";
            }
        }
        return tagText;
    }
}
