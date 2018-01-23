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
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
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
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CaptureService extends Service
{
    private static final String CAR_WATCHER_ACTION = "com.aramco.carwatcher.CaptureService.BIND";
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private static final String TAG = "CaptureService";
    private static final String EXTRA_CONTINUOUS = "EXTRA_CONTINUOUS";
    private static final String EXTRA_ROTATION = "EXTRA_ROTATION";
    //the interval (in seconds) for recorder rotation during continuous capture mode
    private static final long ROTATION_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    public static final String ACTION_NEW_VIDEO = "com.aramco.carwatcher.CHECK_VIDEOS";
    //the opened camera device
    private CameraDevice cameraDevice;
    //the media recorder that's going to be capturing video
    //points to one of primaryRecorder or secondaryRecorder;
    private MediaRecorder mediaRecorder;
    //the secondary recorder to be used for continuous capture
    private MediaRecorder primaryRecorder;
    private MediaRecorder secondaryRecorder;
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
    //keep reference to notification builder for updating notification
    private NotificationCompat.Builder notifyBuilder;
    //and the notification manager as well
    private NotificationManager notifyManager;
    //this counter keeps track of how many times the recorders have been rotated
    //when in continuous mode
    private int rotationCount = 0;

    @Override
    public void onCreate()
    {
        uiHandler = new Handler();
        super.onCreate();
    }

    private void runOnUiThread(Runnable runnable)
    {
        uiHandler.post(runnable);
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
        intent.putExtra(EXTRA_CONTINUOUS, continuous);
        //Intent intent = new Intent();
        //intent.setComponent(new ComponentName("com.aramco.carwatcher", "com.aramco.carwatcher.CaptureService"));
        return intent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        //check if this is for continuous capture
        continuousCapture = intent.getExtras().getBoolean(EXTRA_CONTINUOUS);
        //if this is an intent for recorder rotation, just rotate and stop
        if (continuousCapture && intent.hasExtra(EXTRA_ROTATION))
        {
            rotateRecorders();
        }
        else
        {
            //check if we need to start or stop recording
            if (!isRecordingVideo)
            {
                //start the camera's background thread
                startBackgroundThread();
                //open the camera for recording
                openCamera();
                //if its going to run in continuous mode,
                //schedule a 30-second recorder shift
                if (continuousCapture)
                {
                    setAlarm(this, true);
                }
            }
            else
            {
                stopRecordingVideo();
                closeCamera();
                stopBackgroundThread();
            }
        }

        //don't restart this service if it's closed by the OS
        return START_NOT_STICKY;
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
        i.putExtra(EXTRA_CONTINUOUS, true);
        PendingIntent pi = PendingIntent.getService(context, 0, i, 0);
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (enable)
        {
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 
                    SystemClock.elapsedRealtime(), ROTATION_INTERVAL, pi);
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
            //as this is a service, start recording as soon as camera is ready
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
            cameraDevice.close();
            CaptureService.this.cameraDevice = null;
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

            // Add permission for camera and let user grant the permission
            // TODO this is no longer needed since permissions are granted in main activity
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            {
                //cant really do anything from a service if we don't have permission
                return;
            }

            primaryRecorder = new MediaRecorder();
            secondaryRecorder = new MediaRecorder();
            mediaRecorder = primaryRecorder;
            manager.openCamera(cameraId, stateCallback, null);
        }
        catch (CameraAccessException | InterruptedException e)
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

        isRecordingVideo = true;
        //show the notification
        notifyManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        Resources resources = getResources();
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
            .setSmallIcon(R.drawable.ic_shutter_dark_grey)
            .setContentTitle(resources.getString(R.string.notify_capturing_title))
            .setContentText(resources.getString(R.string.notify_capturing_text))
            .setSound(uri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true);

        notifyManager.notify(notifyId, notifyBuilder.build());
        wakeScreen(5);

        try
        {
            //if in continuous mode, we also need to set up the secondary recorder
            if (continuousCapture)
            {
                setUpMediaRecorder(primaryRecorder, "-Part1");
                setUpMediaRecorder(secondaryRecorder, "-Part2");
            }
            else
            {
                setUpMediaRecorder(primaryRecorder, null);
            }
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();
            //the surface that will get recorded
            Surface recorderSurface = mediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            previewBuilder.addTarget(recorderSurface);
            if (continuousCapture)
            {
                recorderSurface = secondaryRecorder.getSurface();
                surfaces.add(recorderSurface);
                previewBuilder.addTarget(recorderSurface);
            }
            //start the capture session
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession)
                {
                    previewSession = cameraCaptureSession;
                    updatePreview();
                    //once configured, start the actual recording (only on primary)
                    mediaRecorder.start();
                    //secondaryRecorder.start();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession)
                {
                    //TODO: handle the error
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

    /**
     * Stops the video recording process.
     */
    private void stopRecordingVideo()
    {
        isRecordingVideo = false;
        //if in continuous mode, only stop the currently active recorder
        //but reset both
        if (continuousCapture)
        {
            secondaryRecorder.stop();
            secondaryRecorder.reset();
            //mediaRecorder.reset();
        }
        else
        {
            mediaRecorder.stop();
            mediaRecorder.reset();
        }
        //if all went well, add the new video file to the DB
        String title = new SimpleDateFormat("yyyy/MM/dd - HH:mm").format(new Date());
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(getVideoFilePath(videoFileName, this));
        int milliseconds = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        int duration = milliseconds / 1000;
        //TODO: get the actual location
        String location = "Canyon Road, Dhahran";
        Video newVideo = new Video(0, title, videoFileName, duration, location, false);
        SQLiteDatabase database = new VideoBaseHelper(this).getWritableDatabase();
        VideoBaseHelper.addVideo(newVideo, database);
        //when done creating a video, send a broadcast intent for interested listeners
        sendBroadcast(new Intent(ACTION_NEW_VIDEO));
        Resources resources = getResources();
        //and update the notification
        notifyBuilder
            .setContentTitle(resources.getString(R.string.notify_done_capturing_title))
            .setContentText(resources.getString(R.string.notify_done_capturing_text));
        notifyManager.notify(notifyId, notifyBuilder.build());
        wakeScreen(5);
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

    /**
     * Rotate the recorders so that recording can proceed on a new file.
     */
    private void rotateRecorders()
    {
        Log.i(TAG, "Rotating Recorders");
        rotationCount++;
        if (rotationCount == 2)
        {
            primaryRecorder.stop();
            primaryRecorder.reset();
            secondaryRecorder.start();
        }
        if (rotationCount == 3)
        {
            setAlarm(this, false);
        }
    }
}
