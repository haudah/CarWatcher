package com.aramco.carwatcher;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.Spinner;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity
{
    //references to needed views
    private RadioButton englishRadioButton;
    private RadioButton arabicRadioButton;
    private CheckBox bluetoothCheckBox;
    private Spinner pairedNamesSpinner;
    //the current language setting (0: english, 1: arabic)
    private int language;
    //the current bluetooth enabled setting
    private boolean bluetooth;
    //the hardware address of the configured bluetooth device
    private String bluetoothAddress;
    //the keys for shared prefs
    private final static String LANGUAGE_SETTING = "LANGUAGE_SETTING";
    private final static int REQUEST_BLUETOOTH_PERMISSION = 105;
    public final static String BLUETOOTH_ENABLED_SETTING = "BLUETOOTH_ENABLED_SETTING";
    public final static String BLUETOOTH_SETTING = "BLUETOOTH_SETTING";
    public final static String SETTINGS_FILE = "CarWatcherSettings";
    //the list of paired bluetooth devices
    private List<BluetoothDevice> pairedDevices;
    //the list of paired bluetooth device friendly names
    private List<String> pairedNames;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        //show the back button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        //get view references
        englishRadioButton = (RadioButton)findViewById(R.id.settings_language_english);
        arabicRadioButton = (RadioButton)findViewById(R.id.settings_language_arabic);
        bluetoothCheckBox = (CheckBox)findViewById(R.id.settings_bluetooth_enabled);
        pairedNamesSpinner = (Spinner)findViewById(R.id.settings_bluetooth_spinner);
        //get current settings (if they've been configured previously)
        SharedPreferences sharedPref = getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE);
        language = sharedPref.getInt(LANGUAGE_SETTING, 0);
        bluetooth = sharedPref.getInt(BLUETOOTH_ENABLED_SETTING, 0) == 1;

        //need permission to use bluetooth
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(SettingsActivity.this,
                    new String[] {Manifest.permission.BLUETOOTH}, REQUEST_BLUETOOTH_PERMISSION);
            return;
        }

        //get list of paired and connected devices
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        Set<BluetoothDevice> pairedDevicesSet = adapter.getBondedDevices();
        //we use a linked list for pairedDevices and pairedNames to ensure the
        //order of the NOT_CONFIGURED option
        pairedDevices = new LinkedList<>();
        pairedNames = new LinkedList<>();
        //the "NOT CONFIGURED" option will correspond to a null object
        pairedDevices.add(null);
        pairedDevices.addAll(pairedDevicesSet);
        String notConfigured = getResources().getString(R.string.not_configured);
        pairedNames.add(notConfigured);
        for (BluetoothDevice d : pairedDevices)
        {
            //skip the first null object
            if (d != null)
            {
                pairedNames.add(d.getName());
            }
        }
        ArrayAdapter<String> pairedNamesAdapter =
            new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, pairedNames);
        pairedNamesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pairedNamesSpinner.setAdapter(pairedNamesAdapter);

        //set up the views according to last saved settings
        if (language == 1)
        {
            arabicRadioButton.setChecked(true);
        }
        if (bluetooth)
        {
            bluetoothCheckBox.setChecked(true);
        }
        bluetoothAddress = sharedPref.getString(BLUETOOTH_SETTING, "NOT_CONFIGURED");
        //if no bluetooth device was configured, set the spinner to the not configured option
        if (bluetoothAddress.equals("NOT_CONFIGURED"))
        {
            pairedNamesSpinner.setSelection(0);
        }
        else
        {
            //check if any of the paired devices has the same mac address as the one
            //the setting is set to. But ignore the first (NOT_CONFIGURED) device as
            //that case was handled above
            for (int i = 1; i < pairedDevices.size(); i++)
            {
                BluetoothDevice d = pairedDevices.get(i);
                if (d.getAddress().equals(bluetoothAddress))
                {
                    //set the spinner index to this item
                    pairedNamesSpinner.setSelection(i);
                }
            }
        }

        //get list of bluetooth profiles
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_settings_save:
                //need to write to shared prefs when saving
                SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                //when saving, check the current status of the views
                if (arabicRadioButton.isChecked())
                {
                    editor.putInt(LANGUAGE_SETTING, 1);
                }
                else
                {
                    editor.putInt(LANGUAGE_SETTING, 0);
                }
                if (bluetoothCheckBox.isChecked())
                {
                    editor.putInt(BLUETOOTH_ENABLED_SETTING, 1);
                }
                else
                {
                    editor.putInt(BLUETOOTH_ENABLED_SETTING, 0);
                }
                int i = pairedNamesSpinner.getSelectedItemPosition();
                String bluetoothAddress = (i != 0)? pairedDevices.get(i).getAddress() : "NOT_CONFIGURED";
                editor.putString(BLUETOOTH_SETTING, bluetoothAddress);
                editor.commit();
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static Intent newIntent(Context packageContext)
    {
        Intent intent = new Intent(packageContext, SettingsActivity.class);
        return intent;
    }

    //the submit button and search button are included in the toolbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_settings, menu);
        return true;
    }
}
