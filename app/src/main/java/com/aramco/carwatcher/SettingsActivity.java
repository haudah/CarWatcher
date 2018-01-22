package com.aramco.carwatcher;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.RadioButton;

import java.util.List;

public class SettingsActivity extends AppCompatActivity
{
    //references to needed views
    private RadioButton englishRadioButton;
    private RadioButton arabicRadioButton;
    private CheckBox bluetoothCheckBox;
    //the current language setting (0: english, 1: arabic)
    private int language;
    //the current bluetooth enabled setting
    private boolean bluetooth;
    //the hardware address of the configured bluetooth device
    private String bluetoothAddress;
    //the keys for shared prefs
    private final static String LANGUAGE_SETTING = "LANGUAGE_SETTING";
    private final static String BLUETOOTH_ENABLED_SETTING = "BLUETOOTH_ENABLED_SETTING";
    private final static String BLUETOOTH_SETTING = "BLUETOOTH_SETTING";
    //the list of paired bluetooth devices
    private List<BluetoothDevice> pairedDevices;
    //the list of paired bluetooth device friendly names
    private List<String> pairedNames;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        //get view references
        englishRadioButton = (RadioButton)findViewById(R.id.settings_language_english);
        arabicRadioButton = (RadioButton)findViewById(R.id.settings_language_arabic);
        bluetoothCheckBox = (CheckBox)findViewById(R.id.settings_bluetooth_enabled);
        //get current settings (if they've been configured previously)
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        language = sharedPref.getInt(LANGUAGE_SETTING, 0);
        bluetooth = sharedPref.getInt(BLUETOOTH_ENABLED_SETTING, 0) == 1;
        
        //set up the views according to last saved settings
        if (language == 1)
        {
            arabicRadioButton.setChecked(true);
        }
        if (bluetooth)
        {
            bluetoothCheckBox.setChecked(true);
        }
        String bluetoothName = getResources().getString(R.string.not_configured);
        bluetoothAddress = sharedPref.getString(BLUETOOTH_SETTING, "NOT_CONFIGURED");
        //if no bluetooth device was configured, set the spinner to the not configured option
        SharedPreferences.Editor editor = sharedPref.edit();

        //get list of bluetooth profiles
    }
}
