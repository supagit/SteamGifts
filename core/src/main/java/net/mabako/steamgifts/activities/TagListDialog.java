package net.mabako.steamgifts.activities;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import net.mabako.steamgifts.data.GameInfo;
import net.mabako.steamgifts.fragments.GiveawayListFragment;
import net.mabako.steamgifts.persistentdata.SavedElements;
import net.mabako.steamgifts.persistentdata.SavedGameInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Supa on 17.03.2016.
 */
public class TagListDialog extends Dialog {

    private CommonActivity context;

    public TagListDialog(final CommonActivity context, String title, final SavedElements<String> tagList) {
        super(context);
        this.context = context;

        setTitle(title);

        ScrollView sv = new ScrollView(context);
        final LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);
        sv.addView(ll);

        List<String> tags = generateTags();

        for (final String tag : tags) {
            final CheckBox checkBox = new CheckBox(context);
            checkBox.setText(tag);
            checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (checkBox.isChecked()) {
                        tagList.add(tag, tag);
                    } else {
                        tagList.remove(tag);
                    }
                }
            });


            if (tagList.get(tag) != null) {
                checkBox.setChecked(true);
            }

            ll.addView(checkBox);
        }

        this.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                context.loadFragment(GiveawayListFragment.newInstance(GiveawayListFragment.Type.ALL, null, false));
            }
        });

        setContentView(sv);
    }

    private List<String> generateTags() {
        Set<String> tags = new HashSet<>();
        SavedGameInfo savedGameInfo = new SavedGameInfo(context);
        for (GameInfo gameInfo : savedGameInfo.all()) {
            fixTags(savedGameInfo, gameInfo);
            tags.addAll(gameInfo.getTags());
        }

        List<String> sortedTags = new ArrayList<>(tags);
        Collections.sort(sortedTags);
        savedGameInfo.close();
        return sortedTags;
    }

    private void fixTags(SavedGameInfo savedGameInfo, GameInfo gameInfo) {
        Map<String, String> tagRepairMap = new HashMap<>();
        Set<String> tags = gameInfo.getTags();
        for (String tag : tags) {
            if (tag.startsWith(">")) {
                tagRepairMap.put(tag, tag.replace(">", "").trim());
            } else if (tag.startsWith(" ") || tag.endsWith(" ")) {
                tagRepairMap.put(tag, tag.trim());
            }
        }

        if (!tagRepairMap.isEmpty()) {
            for (Map.Entry<String, String> entrySet : tagRepairMap.entrySet()) {
                tags.remove(entrySet.getKey());
                tags.add(entrySet.getValue());
            }
            savedGameInfo.add(gameInfo, gameInfo.getGameId());
        }
    }


}
