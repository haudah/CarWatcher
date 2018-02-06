package com.aramco.carwatcher;

import android.location.Location;
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
    private String address;
    private LatLng latLng;

    /**
     * Constructor taking all the required parameters.
     * 
     * @param id the unique id assigned to the video
     * @param title the name given to the captured video
     * @param fileName name of the video file
     * @param duration the duration of the video (in seconds)
     * @param address the address where the video was captured
     * @param submitted whether or not this video was submitted to CarWatcher
     * @param latLng the lat/lng where this video was captured
     */
    public Video(long id, String title, String fileName, int duration, String address,
            boolean submitted, LatLng latLng)
    {
        this.id = id;
        this.title = title;
        this.fileName = fileName;
        this.duration = duration;
        this.address = address;
        this.submitted = submitted;
        this.latLng = latLng;
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

    public LatLng getLatLng()
    {
        return latLng;
    }

    public String getAddress()
    {
        return address;
    }

    public void setAddress(String address)
    {
        this.address = address;
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
        dest.writeString(address);
        dest.writeParcelable(latLng, flags);
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
        address = source.readString();
        latLng = source.readParcelable(LatLng.class.getClassLoader());
    }

    @Override
    public int describeContents()
    {
        return 0;
    }
    //DO NOT ADD ANYTHING AFTER PARCELABLE IMPLEMENTATION
}
