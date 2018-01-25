package com.aramco.carwatcher;

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
    public void onReceive(Context context, Intent intent)
    {
        Log.i(TAG, "Received broadcast intent: " + intent.getAction());
        //first check if vehicle integration is enabled
        SharedPreferences sharedPref =
            context.getSharedPreferences(SettingsActivity.SETTINGS_FILE, Context.MODE_PRIVATE);
        boolean bluetooth = sharedPref.getInt(SettingsActivity.BLUETOOTH_ENABLED_SETTING, 0) == 1;
        String bluetoothAddress = sharedPref.getString(SettingsActivity.BLUETOOTH_SETTING, "NOT_CONFIGURED");
        //if it's not enabled or configured, do nothing
        if (!bluetooth || bluetoothAddress.equals("NOT_CONFIGUREDX"))
        {
            stopCaptureIfRunning(context);
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
        }
        //check if the configured bluetooth device is connected
        //get list of paired and connected devices
        BluetoothManager manager = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        //get the list of connected devices for the major profiles
        int[] profiles =
            new int[] {BluetoothProfile.GATT, BluetoothProfile.A2DP, BluetoothProfile.HEADSET};
        BluetoothDevice found = null;
        for (int profile : profiles)
        {
            List<BluetoothDevice> connectedDevices = null;
            try
            {
                connectedDevices = manager.getConnectedDevices(profile);
            }
            catch (IllegalArgumentException e)
            {
                int x;
                x = 1;
                //if profile is not supported, just move on to next one
                continue;
            }
            //check if the one we're looking for is here
            for (BluetoothDevice device : connectedDevices)
            {
                if (device.getAddress().equals(bluetoothAddress))
                {
                    found = device;
                    break;
                }
            }
            if (found != null)
            {
                //the device we're looking for is connected
                break;
            }
        }
        //if the device is not connected, stop here
        if (found == null)
        {
            stopCaptureIfRunning(context);
        }
        //the device is charging and bluetooth is connected to the car, we can start
        //the continuous capture
        Intent captureIntent = CaptureService.newIntent(context, true);
        context.startService(captureIntent);
    }

    //this function will send the stop intent to the CaptureService if it's running
    //but do nothing if it's not running
    private void stopCaptureIfRunning(Context context)
    {
        Intent stopIntent = CaptureService.newIntent(context, true);
        context.startService(stopIntent);
    }
}
