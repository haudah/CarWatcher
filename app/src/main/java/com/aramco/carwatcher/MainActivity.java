package com.aramco.carwatcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.flic.lib.FlicAppNotInstalledException;
import io.flic.lib.FlicButton;
import io.flic.lib.FlicManager;
import io.flic.lib.FlicManagerInitializedCallback;

import static com.aramco.carwatcher.SettingsActivity.LANGUAGE_SETTING;
import static com.aramco.carwatcher.SettingsActivity.SETTINGS_FILE;

public class MainActivity extends AppCompatActivity
{
    //camera permission request code
    private final int REQUEST_MULTIPLE_PERMISSION = 110;
    //the video list fragment currently shown
    private VideoListFragment videoListFragment;
    //the name of the application's sqllite table
    public final static String TABLE = "CarWatcherTable";
    //the main bottom navigation view
    private BottomNavigationView bottomNavigator;
    //we need to keep track of the current language to know
    //when a recreation is needed
    private int language;
    //the main activity should listen for new captured videos from the
    //capture service so it can update displayed results accordingly
    private BroadcastReceiver onNewVideo = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            refreshFragments();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        //get the language right away
        SharedPreferences sharedPref = getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE);
        language = sharedPref.getInt(LANGUAGE_SETTING, 0);
        String localeString = (language == 0)? "en" : "ar";
        String countryString = (language == 0)? "US" : "MA";
        setLocale(localeString, countryString, false);
        setContentView(R.layout.activity_main);
        //show the app icon in the action bar
        getSupportActionBar().setTitle(getResources().getString(R.string.app_name));
        // Add permission for camera and external storage
        // We do this preemptively since the permission is needed in a service
        List<String> permissions = new ArrayList<String>();
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED)
        {
            permissions.add(android.Manifest.permission.CAMERA);
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED)
        {
            permissions.add(android.Manifest.permission.BLUETOOTH);
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)
        {
            permissions.add(android.Manifest.permission.RECORD_AUDIO);
        }
        if (permissions.size() > 0)
        {
            ActivityCompat.requestPermissions(MainActivity.this,
                    permissions.toArray(new String[permissions.size()]), REQUEST_MULTIPLE_PERMISSION);
            return;
        }

        //replace the main video list fragment
        FragmentManager fm = getSupportFragmentManager();
        videoListFragment = (VideoListFragment)fm.findFragmentById(R.id.fragment_container);
        //fragment might be non-null after config change
        if (videoListFragment == null)
        {
            videoListFragment = VideoListFragment.newInstance(false);
            fm.beginTransaction().add(R.id.fragment_container, videoListFragment).commit();
        }

        //set up the bottom navigation
        bottomNavigator = (BottomNavigationView)findViewById(R.id.navigation);
        bottomNavigator.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem item)
                    {
                        //the new fragment to be inserted
                        Fragment selectedFragment = null;
                        int selectedItem = item.getItemId();
                        switch (selectedItem)
                        {
                            case R.id.navigation_captured:
                                selectedFragment = VideoListFragment.newInstance(false);
                                break;
                            case R.id.navigation_submitted:
                                selectedFragment = VideoListFragment.newInstance(true);
                                break;
                            case R.id.navigation_profile:
                                selectedFragment = ProfileFragment.newInstance();
                                break;
                        }
                        if (selectedFragment != null)
                        {
                            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                            transaction.replace(R.id.fragment_container, selectedFragment).commit();
                            //if its a videoListFragment, update the reference
                            if (selectedFragment instanceof VideoListFragment)
                            {
                                videoListFragment = (VideoListFragment)selectedFragment;
                            }
                            else
                            {
                                videoListFragment = null;
                            }
                        }

                        return true;
                    }
                }
                );

        //get the flic button
        //requestFlicButton();
    }

    private void setLocale(String localeString, String countryString, boolean recreate)
    {
        Configuration config = getBaseContext().getResources().getConfiguration();
        Locale locale = new Locale(localeString, countryString);
        Locale.setDefault(locale);
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
        if (recreate)
        {
            recreate();
        }
    }

    private void requestFlicButton()
    {
        FlicManager.setAppCredentials("5ae200b0-ff5e-4ea8-95bb-1cda697a3af6",
                "ed95527e-0ce0-440f-a097-2c6dbd0493d8", "CarWatcher");
        try
        {
            FlicManager.getInstance(this, new FlicManagerInitializedCallback() {
                @Override
                public void onInitialized(FlicManager manager)
                {
                    manager.initiateGrabButton(MainActivity.this);
                }
            });
        }
        catch (FlicAppNotInstalledException err)
        {
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data)
    {
        FlicManager.getInstance(this, new FlicManagerInitializedCallback() {
            @Override
            public void onInitialized(FlicManager manager)
            {
                FlicButton button = manager.completeGrabButton(requestCode, resultCode, data);
                if (button != null)
                {
                }
                else
                {
                }
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();
        //on resume we should register the broadcast receiver and refresh the list of videos
        IntentFilter filter = new IntentFilter(CaptureService.ACTION_NEW_VIDEO);
        registerReceiver(onNewVideo, filter);
        refreshFragments();
        //check if language was changed (meaning recreation is needed)
        SharedPreferences sharedPref = getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE);
        int newLanguage = sharedPref.getInt(LANGUAGE_SETTING, 0);
        if (newLanguage != language)
        {
            String localeString = (language == 0)? "en" : "ar";
            String countryString = (language == 0)? "US" : "MA";
            setLocale(localeString, countryString, true);
        }
    }

    public void refreshFragments()
    {
        //if there is a videoListFragment currently shown, update its results
        if (videoListFragment != null)
        {
            videoListFragment.refreshList();
        }
        //if there isn't one shown, there should be (i.e. pop the NoContentFragment)
        else
        {
            //replace the main video list fragment
            FragmentManager fm = getSupportFragmentManager();
            videoListFragment = VideoListFragment.newInstance(false);
            fm.beginTransaction().add(R.id.fragment_container, videoListFragment).commit();
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        //on pause be sure to unregister receiver since it's no longer needed
        unregisterReceiver(onNewVideo);
    }

    /**
     * This method is called to show the NoContentFragment in the main fragment container.
     */
    public void showNoContent(NoContentFragment.Reason reason)
    {
        Fragment fragment = NoContentFragment.newInstance(reason);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment).commit();
        //video list fragment has been popped
        videoListFragment = null;
    }

    //only handle the back button press if it is not handled by an active VideoListFragment
    @Override
    public void onBackPressed()
    {
        if (videoListFragment == null || !videoListFragment.onBackPressed())
        {
            super.onBackPressed();
        }
    }

    //the submit button and search button are included in the toolbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.menu_item_settings:
                Intent intent = SettingsActivity.newIntent(MainActivity.this);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Called from the NoContentFragment when the user needs to view captured videos.
     */
    public void showCaptured()
    {
        bottomNavigator.setSelectedItemId(R.id.navigation_captured);
    }
}
