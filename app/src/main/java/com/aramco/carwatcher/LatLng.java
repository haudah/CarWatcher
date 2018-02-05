package com.aramco.carwatcher;

import android.os.Parcel;
import android.os.Parcelable;

public class LatLng implements Parcelable
{
    private double latitude;
    private double longitude;

    public LatLng(double latitude, double longitude)
    {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude()
    {
        return latitude;
    }

    public double getLongitude()
    {
        return longitude;
    }

    //PARCELABLE IMPLEMENTATION
    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator()
    {
        public LatLng createFromParcel(Parcel in)
        {
            return new LatLng(in);
        }

        public LatLng[] newArray(int size)
        {
            return new LatLng[size];
        }
    };

    public LatLng(Parcel source)
    {
        latitude = source.readDouble();
        longitude = source.readDouble();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }
    //DO NOT ADD ANYTHING AFTER PARCELABLE IMPLEMENTATION
}
