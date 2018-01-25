package com.aramco.carwatcher;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.util.Log;

import java.util.List;
import java.util.Set;

/**
 * Created by Hani Audah on 1/10/2018.
 */

public class BluetoothReceiver extends BroadcastReceiver
{
    private static final String TAG = "CaptureReceiver";

    @Override
    public void onReceive(final Context context, Intent intent)
    {
        Log.i(TAG, "Received broadcast intent: " + intent.getAction());
        //first check if vehicle integration is enabled
        SharedPreferences sharedPref =
            context.getSharedPreferences(SettingsActivity.SETTINGS_FILE, Context.MODE_PRIVATE);
        boolean bluetooth = sharedPref.getInt(SettingsActivity.BLUETOOTH_ENABLED_SETTING, 0) == 1;
        final String bluetoothAddress = sharedPref.getString(SettingsActivity.BLUETOOTH_SETTING, "NOT_CONFIGURED");
        //if it's not enabled or configured, do nothing
        if (!bluetooth || bluetoothAddress.equals("NOT_CONFIGURED"))
        {
            stopCaptureIfRunning(context);
            return;
        }
        //get charging status
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, iFilter);
        int chargingStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        //if it's not charging, do nothing
        if (chargingStatus != BatteryManager.BATTERY_STATUS_CHARGING &&
                chargingStatus != BatteryManager.BATTERY_STATUS_FULL)
        {
            stopCaptureIfRunning(context);
            return;
        }
        //check if the configured bluetooth device is connected
        //get list of paired and connected devices
        BluetoothManager manager = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();

        adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int i, final BluetoothProfile bluetoothProfile)
            {
                List<BluetoothDevice> devices = bluetoothProfile.getConnectedDevices();
                boolean found = false;
                for (BluetoothDevice device : devices)
                {
                    //if the device matches the address we're looking for
                    if (device.getAddress().equals(bluetoothAddress))
                    {
                        //the device is charging and bluetooth is connected to the car, we can start
                        //the continuous capture
                        Intent captureIntent = CaptureService.newIntent(context, true);
                        context.startService(captureIntent);
                        found = true;
                        break;
                    }
                }
                if (!found)
                {
                    stopCaptureIfRunning(context);
                }
            }

            @Override
            public void onServiceDisconnected(int i)
            {
            }
        }, BluetoothProfile.HEADSET);

    }

    //this function will send the stop intent to the CaptureService if it's running
    //but do nothing if it's not running
    private void stopCaptureIfRunning(Context context)
    {
        //check if service is running
        ActivityManager manager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
        {
            if (CaptureService.class.getName().equals(service.service.getClassName()))
            {
                //service is running so stop capture (if there is one in progress)
                Intent stopIntent = CaptureService.newIntent(context, true, false);
                context.startService(stopIntent);
            }
        }
    }

    //this function will send the start intent to the CaptureService if it's not running
    //but do nothing if it's already running
    private void startCaptureIfNotRunning(Context context)
    {
        //stop capture (if there isn't one already in progress)
        Intent stopIntent = CaptureService.newIntent(context, true, true);
        context.startService(stopIntent);
    }
}
