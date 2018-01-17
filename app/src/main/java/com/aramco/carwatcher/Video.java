package com.aramco.carwatcher;

import java.io.File;

public class Video
{
    private long id;
    private String title;
    private String fileName;
    private int duration;
    private boolean submitted;
    private String location;

    /**
     * Constructor taking all the required parameters.
     * 
     * @param id the unique id assigned to the video
     * @param title the name given to the captured video
     * @param fileName name of the video file
     * @param duration the duration of the video (in seconds)
     * @param submitted whether or not this video was submitted to CarWatcher
     */
    public Video(long id, String title, String fileName, int duration, String location, boolean submitted)
    {
        this.id = id;
        this.title = title;
        this.fileName = fileName;
        this.duration = duration;
        this.location = location;
        this.submitted = submitted;
    }

    /**
     * The Video's id may need to be updated to reflect the one used in a database.
     *
     * @param id the new id of the video instance
     */
    public void setId(long id)
    {
        this.id = id;
    }

    public long getId()
    {
        return id;
    }

    public String getTitle()
    {
        return title;
    }

    public String getFileName()
    {
        return fileName;
    }

    public int getDuration()
    {
        return duration;
    }

    public boolean isSubmitted()
    {
        return submitted;
    }

    public String getLocation()
    {
        return location;
    }
}
