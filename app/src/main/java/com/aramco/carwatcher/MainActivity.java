package com.aramco.carwatcher;

import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
{
    //camera permission request code
    private final int REQUEST_MULTIPLE_PERMISSION = 110;
    //the video list fragment currently shown
    private VideoListFragment videoListFragment;
    //the name of the application's sqllite table
    public final static String TABLE = "CarWatcherTable";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
    }

    /**
     * This method is called to show the NoContentFragment in the main fragment container.
     */
    public void showNoContent(NoContentFragment.Reason reason)
    {
        Fragment fragment = NoContentFragment.newInstance(reason);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment).commit();
    }
}
