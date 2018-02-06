package com.aramco.carwatcher;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

public class CaptureService extends Service
{
    private static final String CAR_WATCHER_ACTION = "com.aramco.carwatcher.CaptureService.BIND";
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private static final String TAG = "CaptureService";
    private static final String EXTRA_USER_DRIVEN = "EXTRA_USER_DRIVEN";
    private static final String EXTRA_ROTATION = "EXTRA_ROTATION";
    private static final String EXTRA_SET_RUNNING = "EXTRA_SET_RUNNING";
    private static final String EXTRA_SET_STOPPED = "EXTRA_SET_STOPPED";
    //the interval (in seconds) for recorder rotation during continuous capture mode
    private static final long ROTATION_INTERVAL = TimeUnit.SECONDS.toMillis(500);
    //the action sent when a new video is captured
    public static final String ACTION_NEW_VIDEO = "com.aramco.carwatcher.CHECK_VIDEOS";
    //the opened camera device
    private CameraDevice cameraDevice;
    //the media recorder that's going to be capturing video
    //points to one of primaryRecorder or secondaryRecorder;
    private MediaRecorder mediaRecorder;
    //lock to prevent app from closing before releasing camera access
    private Semaphore cameraLock = new Semaphore(1);
    private Integer sensorOrientation;
    //Capture Session needs a background handler running in a separate thread so as
    //not to overload the main thread
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    //whether or not service is currently recording
    private boolean isRecordingVideo = false;
    //whether service is running in continuous mode
    private boolean continuousCapture = false;
    //whether a recording in the current continuous capture has been started yet
    private boolean continuousRecording = false;
    //the UI thread handler
    private Handler uiHandler;
    //the size of the video to capture
    private Size videoSize;
    //the current capture session
    private CameraCaptureSession previewSession;
    //the capture session builder
    private CaptureRequest.Builder previewBuilder;
    //we need to record the file name for the database insertion
    private String videoFileName;
    //we need a notification id to update the notification upon completion
    private int notifyId = 1;
    private int notifyIdContinuous = 2;
    //keep reference to notification builder for updating notification
    private NotificationCompat.Builder notifyBuilder;
    private NotificationCompat.Builder notifyBuilderContinuous;
    //and the notification manager as well
    private NotificationManager notifyManager;
    //this variable keeps track of the last rotated video file, which will need to
    //be deleted when a new one comes in
    private File rotationFile = null;
    //this will only be true for the first run; it is needed to allow the first
    //startRecordingVideo to be called after the camera is open
    private boolean firstRun;
    //the first trigger of the alarm should be ignored since it will start
    //immediately when the alarm is set
    private boolean firstAlarm;
    //in continuous mode, this is the most recently saved file in the current run
    private File lastVideoFile = null;
    //a VideoLocationRequest encapsulates a request for location data for a video
    //that is being captured or was recently captured
    private class VideoLocationRequest
    {
        //video entry that needs a location (populated if the
        //video is created before location was obtained)
        public Video video;
        //the location of this video capture (populated after location is obtained)
        public Location location;
        //the address
    }
    //this is a queue of all the video location requests
    private LinkedList<VideoLocationRequest> locationQueue;
    //this will be true when a location listener is currently listening
    private boolean updatingLocation = false;

    @Override
    public void onCreate()
    {
        super.onCreate();
        firstRun = true;
        //start the camera's background thread
        startBackgroundThread();
        //open the camera for recording
        openCamera();
        //initialize the location queue
        locationQueue = new LinkedList<VideoLocationRequest>();
    }

    @Override
    public void onDestroy()
    {
        closeCamera();
        stopBackgroundThread();
    }

    /**
     * Create intents to send work to the CaptureService.
     *
     * @param context the application context.
     * @param continuous whether the start/stop command is for continuous capture
     * @return an intent that you can send to CaptureService to start/stop recording.
     */
    public static Intent newIntent(Context context, boolean continuous)
    {
        Intent intent = new Intent(context, CaptureService.class);
        //starting/stopping continuous captures are not driven by the user
        //but by conditions like charging/bluetooth
        intent.putExtra(EXTRA_USER_DRIVEN, !continuous);
        return intent;
    }

    /**
     * This variant adds the extra for setting a target state of a continuous capture, as opposed to toggling
     * the current state.
     *
     * @param context the application context
     * @param continuous whether the start/stop command is for continuous capture
     * @param targetRunning whether the target state of the continuous capture is running
     * @return an intent that you can send to CaptureService to start/stop recording.
     */
    public static Intent newIntent(Context context, boolean continuous, boolean targetRunning)
    {
        Intent intent = newIntent(context, continuous);
        if (targetRunning)
        {
            intent.putExtra(EXTRA_SET_RUNNING, true);
        }
        else
        {
            intent.putExtra(EXTRA_SET_STOPPED, true);
        }
        return intent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        //the bluetooth receiver may send commands that target a specific state, unlike
        //other intents that only toggle without regard to current state
        if (intent.hasExtra(EXTRA_SET_RUNNING) && continuousCapture)
        {
            //if we're targeting a running continuous capture but
            //it's already running..do nothing
            return START_NOT_STICKY;
        }
        else if (intent.hasExtra(EXTRA_SET_STOPPED) && !continuousCapture)
        {
            //if we're targeting a stopped continuous capture but
            //it's already stopped..do nothing
            return START_NOT_STICKY;
        }
        //check if this is for continuous capture (i.e. not user-driven)
        //TODO: what if there's a non-continuous capture in progress
        boolean userDriven = true;
        if (intent.hasExtra("EXTRA_USER_DRIVEN"))
        {
            userDriven = intent.getExtras().getBoolean(EXTRA_USER_DRIVEN);
        }
        if (!continuousCapture && !userDriven)
        {
            continuousCapture = true;
            //always reset the rotation file when starting a continuous capture
            rotationFile = null;
        }
        //the continuousRecord flag is what starts and stops the user-requested recording during
        //a continuous capture
        boolean rotate = intent.getExtras().getBoolean(EXTRA_ROTATION);

        //check if we need to start or stop recording
        if (!isRecordingVideo)
        {
            //if this is a rotation but there is no capture currently going, do nothing
            //TODO: that should never happen though??
            if (!rotate)
            {
                showNotification(userDriven, true);
                getLocation(this);
                //start the actual recording unless its the firstRun
                if (!firstRun)
                {
                    startRecordingVideo();
                }
                firstRun = false;
                //if its going to run in continuous mode,
                //schedule a 30-second recorder shift
                if (continuousCapture)
                {
                    setAlarm(this, true);
                }
            }
        }
        else
        {
            //if we're continuousRecording, but this is a user-driven command, stop recording
            //(also stop recording if this is not a continuous capture)
            //(also stop recording for a rotation)
            if (!continuousCapture || (!userDriven && !rotate) || (rotate ^ continuousRecording))
            {
                if (continuousCapture && !rotate && !userDriven)
                {
                    showNotification(false, false);
                    setAlarm(this, false);
                }
                //if we're about to stop a continuousRecording or non-continuous capture
                //show the video captured notification
                if (!rotate)
                {
                    if (continuousRecording || !continuousCapture)
                    {
                        showNotification(true, false);
                    }
                }
                stopRecordingVideo(rotate, userDriven, this);
            }
            //if a continuous capture was running, no recording was in progress,
            //and this is a user-driven command
            else if (continuousCapture && !continuousRecording)
            {
                continuousRecording = true;
                //show notfication for user-driven
                getLocation(this);
                showNotification(true, true);
            }
        }

        //don't restart this service if it's closed by the OS
        return START_NOT_STICKY;
    }

    private void showNotification(boolean userDriven, boolean start)
    {
        Resources resources = getResources();
        //certain configs only need to be done once
        if (notifyManager == null)
        {
            //show the notification
            notifyManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            //channel id
            String channelId = "carwatcher_channel";
            //user-visible name of the channel
            CharSequence name = resources.getString(R.string.notify_channel_name);
            //user-visible description of the channel
            String description = resources.getString(R.string.notify_channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            //get the Uri for the default notification sound
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            //we only need to create a channel on API > 26
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                NotificationChannel channel = new NotificationChannel(channelId, name, importance);
                channel.setDescription(description);
                channel.enableVibration(false);
                notifyManager.createNotificationChannel(channel);
            }

            notifyBuilder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_shutter_white)
                .setSound(uri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
            notifyBuilderContinuous = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_shutter_white)
                .setContentTitle(resources.getString(R.string.notify_continuous_title))
                .setContentText(resources.getString(R.string.notify_continuous_text))
                .setSound(uri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        }
        if (userDriven)
        {
            if (start)
            {
                //when user clicks on notification, it's as if flic was clicked to
                //stop the userDriven capture
                Intent captureIntent = CaptureService.newIntent(this, false);
                PendingIntent pendingIntent = PendingIntent.getService(this, 0, captureIntent, FLAG_UPDATE_CURRENT);
                notifyBuilder
                    .setContentTitle(resources.getString(R.string.notify_capturing_title))
                    .setContentText(resources.getString(R.string.notify_capturing_text))
                    .setContentIntent(pendingIntent);
            }
            else
            {
                //when user clicks on notification, captured video should start playing
                String videoFilePath = getVideoFilePath(videoFileName, this);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoFilePath));
                intent.setDataAndType(Uri.parse(videoFilePath), "video/mp4");
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT);
                notifyBuilder
                    .setContentTitle(resources.getString(R.string.notify_done_capturing_title))
                    .setContentText(resources.getString(R.string.notify_done_capturing_text))
                    .setContentIntent(pendingIntent);
            }
            notifyManager.notify(notifyId, notifyBuilder.build());
        }
        else
        {
            if (start)
            {
                //when user clicks on notification, monitor mode should stop
                Intent intent = CaptureService.newIntent(this, true);
                PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, FLAG_UPDATE_CURRENT);
                notifyBuilderContinuous
                    .setContentTitle(resources.getString(R.string.notify_continuous_title))
                    .setContentText(resources.getString(R.string.notify_continuous_text))
                    .setContentIntent(pendingIntent);
            }
            else
            {
                //this notification should take no action, so contentIntent is set to null
                notifyBuilderContinuous
                    .setContentTitle(resources.getString(R.string.notify_done_continuous_title))
                    .setContentText(resources.getString(R.string.notify_done_continuous_text))
                    .setContentIntent(null);
            }
            notifyManager.notify(notifyIdContinuous, notifyBuilderContinuous.build());
        }
        wakeScreen(5);
    }

    /**
     * Sets up the callback for getting user location. The callback will check if there are any newly
     * added videos waiting for location data and update the database entries if so. For videos that
     * have not been completed yet, the locationQueue entries will be populated with location data
     * so that upon completion, the location is readily available.
     */
    private int attempt = 0;
    private void getLocation(final Context context)
    {
        //add new location request to the queue
        locationQueue.add(new VideoLocationRequest());
        //we might still be getting location for a previous capture
        if (updatingLocation)
        {
            //and if so, we just a new entry on the queue..wait for the location listener
            //to update it
            return;
        }
        //we'll now be getting location updates
        updatingLocation = true;
        attempt = 0;
        final LocationManager locationManager =
            (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location)
            {
                //if the location is not accurate to within 20 meters, reject it
                //also, I noticed some trailing calls after removing the listener, so make sure
                //updaingLocation is actually true
                //TODO: for testing, I'll just accept the fourth attempt, no matter how inaccurate
                if (location.getAccuracy() > 20 && updatingLocation && attempt < 3)
                {
                    attempt++;
                    return;
                }
                //its a solid location, check for any queue items awaiting location data
                List<VideoLocationRequest> toRemove = new ArrayList<VideoLocationRequest>();
                //if any already captured videos were located, the list needs refresh
                boolean needsRefresh = false;
                for (VideoLocationRequest request : locationQueue)
                {
                    //if video was already captured but not yet located,
                    //update the db entry and pop item from the queue
                    if (request.video != null && request.video.getLatLng() != null)
                    {
                        SQLiteDatabase database = new VideoBaseHelper(context).getWritableDatabase();
                        VideoBaseHelper.geocodeVideo(request.video,
                                location.getLatitude(), location.getLongitude(), database);
                        toRemove.add(request);
                        needsRefresh = true;
                    }
                    //if video is not done capturing, just store the location and wait for
                    //the onReady method to fetch it when creating the db entry
                    else
                    {
                        request.location = location;
                    }
                }

                //now go through the videos, removing them from the queue
                for (VideoLocationRequest request : toRemove)
                {
                    locationQueue.remove(request);
                }
                //if refresh is needed, send the broadcast
                if (needsRefresh)
                {
                    sendBroadcast(new Intent(ACTION_NEW_VIDEO));
                }
                //all except possibly one element of the queue were already created in db, keep a record of that one
                //now we need to reverse geocode to get location string
                ApiConnector.getAddress(location.getLatitude(), location.getLongitude(), new GetAddressListener() {
                    @Override
                    public void onResponse(String address)
                    {
                    }
                });

                //once an accurate location is obtained, there is no need to listen for further
                //location changes..the next capture will retrigger listening
                locationManager.removeUpdates(this);
                //done updating location
                updatingLocation = false;
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };
        //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    }

    /**
     * Sets up an alarm that will trigger the recorder rotation during continuous capture mode.
     *
     * @param enable whether to setup or terminate the alarm
     */
    private void setAlarm(Context context, boolean enable)
    {
        Intent i = new Intent(context, CaptureService.class);
        i.putExtra(EXTRA_ROTATION, true);
        //rotations are obviously not user-driven
        i.putExtra(EXTRA_USER_DRIVEN, false);
        PendingIntent pi = PendingIntent.getService(context, 0, i, 0);
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (enable)
        {
            firstAlarm = true;
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + ROTATION_INTERVAL, ROTATION_INTERVAL, pi);
        }
        else
        {
            alarmManager.cancel(pi);
            pi.cancel();
        }
    }

    /**
     * Returns null as this service doesn't provide binding.
     */
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    /**
     * Starts the background thread and its handler.
     */
    private void startBackgroundThread()
    {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its handler.
     */
    private void stopBackgroundThread()
    {
        backgroundThread.quitSafely();
        try
        {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * This StateCallback gets called when the camera device status changes.
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice)
        {
            CaptureService.this.cameraDevice = cameraDevice;
            cameraLock.release();
            startRecordingVideo();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice)
        {
            cameraLock.release();
            cameraDevice.close();
            CaptureService.this.cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error)
        {
            cameraLock.release();
            //cameraDevice.close();
            //CaptureService.this.cameraDevice = null;
        }
    };

    /**
     * This sets up the media recorder(s) with all necessary recording format info.
     *
     * @param recorder the media recorder to set up
     */
    private void setUpMediaRecorder(MediaRecorder recorder, String suffix) throws IOException
    {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //set the recording destination file
        //if there is a suffix-add it to the end
        String outputFile = getVideoFilePath(this);
        if (suffix != null)
        {
            outputFile += suffix;
        }
        recorder.setOutputFile(getVideoFilePath(this));
        recorder.setVideoEncodingBitRate(10000000);
        recorder.setVideoFrameRate(30);
        recorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        int rotation = getResources().getConfiguration().orientation;
        switch (sensorOrientation)
        {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                recorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                recorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        recorder.prepare();
    }

    /**
     * Choose a video size with aspect ratio 3x4 and not larger than 1080p.
     *
     * @param choices the list of available sizes
     * @return the video size
     */
    private static Size chooseVideoSize(Size[] choices)
    {
        for (Size size : choices)
        {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080)
            {
                return size;
            }
        }

        Log.e(TAG, "Couldn't find a suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Gets the backward-facing instance camera of the device, and asks for permission if required.
     */
    private void openCamera()
    {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try
        {
            //try to acquire a lock on the camera
            if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS))
            {
                throw new RuntimeException("Could not acquire lock on camera.");
            }
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null)
            {
                throw new RuntimeException("Could not get available video sizes");
            }
            videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            //get the camera sensor orientation
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            manager.openCamera(cameraId, stateCallback, null);
        }
        catch (CameraAccessException | InterruptedException | SecurityException e)
        {
        }
    }

    /**
     * Closes the camera device and releases the mediaRecorder.
     */
    private void closeCamera()
    {
        try
        {
            cameraLock.acquire();
            if (cameraDevice != null)
            {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (mediaRecorder != null)
            {
                mediaRecorder.release();
                mediaRecorder = null;
            }
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException("Could not acquire lock on camera.");
        }
        finally
        {
            cameraLock.release();
        }
    }

    /**
     * Starts the video recording process.
     */
    private void startRecordingVideo()
    {
        //only call this once you have the camera device ready
        if (cameraDevice == null)
        {
            return;
        }
        //a new media recorder is needed to start each session (dont ask questions)
        mediaRecorder = new MediaRecorder();

        isRecordingVideo = true;

        try
        {
            setUpMediaRecorder(mediaRecorder, null);
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();
            //the surface that will get recorded
            Surface recorderSurface = mediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            previewBuilder.addTarget(recorderSurface);
            //start the capture session
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession)
                {
                    previewSession = cameraCaptureSession;
                    updatePreview();
                    //once configured, start the actual recording (only on primary)
                    mediaRecorder.start();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession)
                {
                    //TODO: handle the error
                }

                @Override
                public void onReady(CameraCaptureSession cameraCaptureSession)
                {
                    //how can we know if this is the first onReady after abortCaptures
                    if (!onReadyRun)
                    {
                        return;
                    }
                    //block the next one
                    onReadyRun = false;
                    boolean rotate = onReadyRotate;
                    boolean userDriven = onReadyUserDriven;
                    Context context = onReadyContext;
                    //stop doing this here..wait for abort captures to complete (i.e onReady)
                    isRecordingVideo = false;
                    mediaRecorder.stop();
                    mediaRecorder.reset();
                    if (!rotate)
                    {
                        //We will only be adding a new video entry now if:
                        //1) This is not a continuous capture, and every stop should result in a new entry
                        //2) This is a continuous capture, but we're already continuousRecording and the
                        //   user is stopping the continuousRecording
                        //3) This is a continuous capture, and we're already continuousRecording but the
                        //   entire capture is being stopped (e.g. bluetooth out of range)
                        if (!continuousCapture || continuousRecording)
                        {
                            String title = new SimpleDateFormat("yyyy/MM/dd - HH:mm").format(new Date());
                            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                            mmr.setDataSource(getVideoFilePath(videoFileName, context));
                            int milliseconds = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                            int duration = milliseconds / 1000;
                            //TODO: get the actual location
                            LatLng latLng = null;
                            String address = getResources().getString(R.string.getting_location);
                            //check if location was already obtained during capture; note that if it was, it would be in the
                            //last element added to the location queue
                            if (locationQueue.getLast().location != null)
                            {
                                Location location = locationQueue.getLast().location;
                                latLng = new LatLng(location.getLatitude(), location.getLongitude());
                                //address string will simply be the coordinates until the actual address is obtained
                                address = String.format("%.6f,%.6f", latLng.getLatitude(), latLng.getLongitude());
                            }
                            Video newVideo = new Video(0, title, videoFileName, duration, address, false, latLng);
                            SQLiteDatabase database = new VideoBaseHelper(context).getWritableDatabase();
                            VideoBaseHelper.addVideo(newVideo, database);
                            //when done creating a video, send a broadcast intent for interested listeners
                            sendBroadcast(new Intent(ACTION_NEW_VIDEO));
                        }
                        //we should also update the notification if we're stopping a continuous capture
                        if (continuousCapture && !userDriven)
                        {
                            continuousCapture = false;
                            //delete the last rotated file when stopping a continuous capture
                            if (rotationFile != null)
                            {
                                rotationFile.delete();
                            }
                            //dont worry, it will be nullified on a new cont capture
                        }
                    }
                    else
                    {
                        //if this is a rotation, remember the video file,
                        //and overwrite the last one (if there is one)
                        if (rotationFile != null)
                        {
                            rotationFile.delete();
                        }
                        //get the video file path for the newly rotated video file
                        rotationFile = new File(getVideoFilePath(videoFileName, context));
                    }
                    if (previewSession != null)
                    {
                        previewSession.close();
                        previewSession = null;
                    }
                    //if this is a rotation, we need to start another recording ASAP
                    //also start another recording if this is a continuous recording and we are stopping based on a
                    //user-driven request
                    if (rotate || (continuousRecording && userDriven))
                    {
                        startRecordingVideo();
                    }

                    //we have to set continuousRecording to false, so this was moved to the onReady callback
                    //if this is a continuous capture, and recording is being stopped
                    //(not rotated) then we're no longer continuousRecording
                    if (!rotate)
                    {
                        continuousRecording = false;
                    }
                }
            }, backgroundHandler);
        }
        catch (CameraAccessException | IOException e)
        {
            e.printStackTrace();
            //if there is an exception, assume we're no longer recording
            isRecordingVideo = false;
        }
    }

    //these will be used by onReady function
    private boolean onReadyRotate = false;
    private boolean onReadyUserDriven = false;
    private Context onReadyContext = null;
    private boolean onReadyRun = false;

    /**
     * Stops the video recording process.
     */
    private void stopRecordingVideo(boolean rotate, boolean userDriven, Context context)
    {
        //if we're continuousRecording and this is a rotation, just ignore it
        if (continuousRecording && rotate)
        {
            return;
        }
        try
        {
            //give onReady everything it needs to do its work
            onReadyRun = true;
            onReadyUserDriven = userDriven;
            onReadyRotate = rotate;
            onReadyContext = context;
            previewSession.stopRepeating();
            previewSession.abortCaptures();
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Update the camera preview.
     */
    private void updatePreview()
    {
        if (cameraDevice == null)
        {
            return;
        }

        try
        {
            setUpCaptureRequestBuilder(previewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            previewSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder)
    {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    /**
     * Generates a path for recording new videos.
     */
    @NonNull
    private String getVideoFilePath(Context context)
    {
        final File dir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM);
        //if CarWatcher directory does not exist, create it
        File carWatcherDir = new File(dir.getAbsolutePath() + "/CarWatcher/");
        if (!carWatcherDir.exists())
        {
            carWatcherDir.mkdirs();
        }
        videoFileName = System.currentTimeMillis() + ".mp4";
        return (dir == null ? "" : (dir.getAbsolutePath() + "/CarWatcher/")) + videoFileName;
    }

    /**
     * Gets the full path of a video given its file name by just prepending the standard
     * directory.
     *
     * @param fileName the fileName for the file whose path needs to be retrieved
     * @return full path of specified fileName
     */
    public static String getVideoFilePath(String fileName, Context context)
    {
        final File dir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/CarWatcher/")) + fileName;
    }

    /**
     * This function can be used to wake the screen for the specified number of seconds.
     *
     * @param seconds the number of seconds to keep the screen awake
     */
    private void wakeScreen(int seconds)
    {
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = pm.isScreenOn();
        if (!isScreenOn)
        {
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP |
                    PowerManager.ON_AFTER_RELEASE,"CaptureScreenLocak");
            wl.acquire(seconds * 1000);
            PowerManager.WakeLock wl_cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"CaptureCoreLock");
            wl_cpu.acquire(seconds * 1000);
        }
    }
}
