package com.aramco.carwatcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by support$ on 1/10/2018.
 */

public class BootReceiver extends BroadcastReceiver
{
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        Log.i(TAG, "Received broadcast intent: " + intent.getAction());
    }
}
