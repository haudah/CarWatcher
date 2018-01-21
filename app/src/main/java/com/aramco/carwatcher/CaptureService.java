package com.aramco.carwatcher;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
    public static final String ACTION_NEW_VIDEO = "com.aramco.carwatcher.CHECK_VIDEOS";
    //the opened camera device
    private CameraDevice cameraDevice;
    //the media recorder that's going to be capturing video
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
     * @return an intent that you can send to CaptureService to start/stop recording.
     */
    public static Intent newIntent(Context context)
    {
        Intent intent = new Intent(context, CaptureService.class);
        //Intent intent = new Intent();
        //intent.setComponent(new ComponentName("com.aramco.carwatcher", "com.aramco.carwatcher.CaptureService"));
        return intent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        //check if we need to start or stop recording
        if (!isRecordingVideo)
        {
            //start the camera's background thread
            startBackgroundThread();
            //open the camera for recording
            openCamera();
        }
        else
        {
            stopRecordingVideo();
            closeCamera();
            stopBackgroundThread();
        }

        //don't restart this service if it's closed by the OS
        return START_NOT_STICKY;
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
     * This sets up the media recorder with all necessary recording format info.
     */
    private void setUpMediaRecorder() throws IOException
    {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //set the recording destination file
        mediaRecorder.setOutputFile(getVideoFilePath(this));
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        int rotation = getResources().getConfiguration().orientation;
        switch (sensorOrientation)
        {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mediaRecorder.prepare();
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

            mediaRecorder = new MediaRecorder();
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
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        Resources resources = getResources();
        //channel id
        String channelId = "carwatcher_channel";
        //user-visible name of the channel
        CharSequence name = resources.getString(R.string.notify_channel_name);
        //user-visible description of the channel
        String description = resources.getString(R.string.notify_channel_description);
        int importance = NotificationManager.IMPORTANCE_HIGH;
        //we only need to create a channel on API > 26
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);
            channel.enableVibration(false);
            //get the Uri for the default notification
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
            .setTicker(resources.getString(R.string.notify_capturing_title))
            .setSmallIcon(R.drawable.ic_shutter_dark_grey)
            .setContentTitle(resources.getString(R.string.notify_capturing_title))
            .setContentText(resources.getString(R.string.notify_capturing_text))
            .setAutoCancel(true)
            .build();

        notificationManager.notify(0, notification);

        try
        {
            setUpMediaRecorder();
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
                    //once configured, start the actual recording
                    mediaRecorder.start();
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
        mediaRecorder.stop();
        mediaRecorder.reset();
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
}
