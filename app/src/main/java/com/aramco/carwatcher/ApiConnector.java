package com.aramco.carwatcher;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class ApiConnector
{
    private static final String MAPS_API_KEY = "AIzaSyCeq4mEcDnoeeNtKgaZ328i1xtljMVpmOg";
    private static final String GEOCODING_URL = 
        "https://maps.googleapis.com/maps/api/geocode/json?latlng=%.6f,%.6f&key=%s";

    /**
     * Get the address corresponding to the specified latitude and longitude coordinates.
     */
    public String getLocationAddress(float latitude, float longitude)
    {
        try
        {
            URL url = new URL(String.format(GEOCODING_URL, latitude, longitude, MAPS_API_KEY));
            HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
            return null;
        }
        catch (IOException e)
        {
            return null;
        }
    }
}
