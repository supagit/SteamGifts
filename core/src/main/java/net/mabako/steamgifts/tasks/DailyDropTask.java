package net.mabako.steamgifts.tasks;

import android.app.AlarmManager;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.data.Statistics;
import net.mabako.steamgifts.fragments.GiveawayDetailFragment;
import net.mabako.steamgifts.fragments.interfaces.IHasEnterableGiveaways;
import net.mabako.steamgifts.persistentdata.SavedErrors;
import net.mabako.steamgifts.persistentdata.SavedGameInfo;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class DailyDropTask
        extends AsyncTask<Void, Void, Void>

{
    private Context context;
    public static final String TAG = DailyDropTask.class.getSimpleName();

    private Statistics statistics;

    public DailyDropTask(Context context) {
        this.context = context;
    }


    protected Void doInBackground(Void... params) {
        statistics = new Statistics(context);
        new FanmilesDailyDropChecker(context, statistics).check();
        return null;
    }


}
