package com.aramco.carwatcher;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class ApiConnector
{
    private static final String MAPS_API_KEY = "AIzaSyCeq4mEcDnoeeNtKgaZ328i1xtljMVpmOg";
    private static final String GEOCODING_URL = 
        "https://maps.googleapis.com/maps/api/geocode/json?latlng=%.6f,%.6f&key=%s";

    //the ApiConnector keeps track of how many address requests are pending
    //this variable should only be updated from the UI thread
    private int pendingRequests = 0;

    //this async task is used for making api calls on a separate thread
    private class RequestTask extends AsyncTask<Void, Void, String>
    {
        //the coordinates that will be reverse geocoded
        private double latitude;
        private double longitude;
        //the callback that will be called upon completion
        private GetAddressListener listener;

        //constructor is used for passing parameters type-safely
        public RequestTask(double latitude, double longitude, GetAddressListener listener)
        {
            this.latitude = latitude;
            this.longitude = longitude;
            this.listener = listener;
        }

        @Override
        protected String doInBackground(Void... params)
        {
            try
            {
                URL url = new URL(String.format(GEOCODING_URL, latitude, longitude, MAPS_API_KEY));
                Log.d("CARWATCHER", "Opening URL connection.");
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                Log.d("CARWATCHER", "Opened URL connection.");
                connection.setRequestMethod("GET");

                InputStream is = connection.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                String inputLine;
                StringBuilder jsonBuilder = new StringBuilder();
                while ((inputLine = rd.readLine()) != null)
                {
                    jsonBuilder.append(inputLine + "\n");
                }
                String jsonString = jsonBuilder.toString();
                //parse it into a json object
                JSONObject json = new JSONObject(jsonString);
                //get street and city from first result
                String city = null;
                String street = null;
                JSONArray components =
                    json.getJSONArray("results").getJSONObject(0).getJSONArray("address_components");
                for (int i = 0; i < components.length(); i++)
                {
                    //get the component type (only the first type, but verify this)
                    JSONObject component = components.getJSONObject(i);
                    String type = component.getJSONArray("types").getString(0);
                    if (type.equals("administrative_area_level_2") || type.equals("locality"))
                    {
                        city = component.getString("long_name");
                    }
                    else if (type.equals("route"))
                    {
                        street = component.getString("long_name");
                    }
                }
                //if we got both a street and city, we can return a result
                if (city != null && street != null)
                {
                    return String.format("%s, %s", street, city);
                }
                return null;
            }
            catch (IOException e)
            {
                return null;
            }
            catch (JSONException e)
            {
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result)
        {
            pendingRequests--;
            //if result is null, there must have been an error
            if (result == null)
            {
                listener.onErrorResponse();
            }
            else
            {
                listener.onResponse(result);
            }
        }
    }

    /**
     * Get the address corresponding to the specified latitude and longitude coordinates.
     */
    public void getAddress(double latitude, double longitude, GetAddressListener listener)
    {
        //new request pending
        pendingRequests++;
        //create an async task for getting the string
        RequestTask task = new RequestTask(latitude, longitude, listener);
        task.execute();
    }

    /**
     * Returns whether there are any pending requests.
     *
     * @return true if there are pendingRequests
     */
    public boolean isBusy()
    {
        return pendingRequests > 0;
    }
}
