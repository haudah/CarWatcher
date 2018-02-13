package com.aramco.carwatcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by support$ on 1/10/2018.
 */

public class CaptureReceiver extends BroadcastReceiver
{
    private static final String TAG = "CaptureReceiver";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        Log.i(TAG, "Received broadcast intent: " + intent.getAction());
        //TODO: FOR DEBUGGING
        if (intent.getExtras() != null && intent.getExtras().containsKey("DEBUG"))
        {
            Intent captureIntent = CaptureService.newIntent(context, true);
            context.startService(captureIntent);
            return;
        }
        //same intent is used for capture/stop capturing
        Intent captureIntent = CaptureService.newIntent(context, false);
        context.startService(captureIntent);
    }
}

