package com.aramco.carwatcher;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

public class Video implements Parcelable
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

    /**
     * The title is the only attribute of the video that may change on the client.
     */
    public void setTitle(String title)
    {
        this.title = title;
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

    //PARCELABLE IMPLEMENTATION
    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(fileName);
        dest.writeInt(duration);
        dest.writeInt((submitted)? 1 : 0);
        dest.writeString(location);
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator()
    {
        public Video createFromParcel(Parcel in)
        {
            return new Video(in);
        }

        public Video[] newArray(int size)
        {
            return new Video[size];
        }
    };

    public Video(Parcel source)
    {
        id = source.readLong();
        title = source.readString();
        fileName = source.readString();
        duration = source.readInt();
        submitted = source.readInt() != 0;
        location = source.readString();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }
    //DO NOT ADD ANYTHING AFTER PARCELABLE IMPLEMENTATION
}
