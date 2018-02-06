package com.aramco.carwatcher;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.aramco.carwatcher.VideoDbSchema.VideoTable;

import java.util.ArrayList;
import java.util.List;

public class VideoBaseHelper extends SQLiteOpenHelper
{
    private static final int VERSION = 1;
    private static final String DATABASE_NAME = "videoBase.db";

    public VideoBaseHelper(Context context)
    {
        super(context, DATABASE_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE " + VideoDbSchema.VideoTable.NAME + "(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                VideoTable.Cols.TITLE + " CHAR(20), " +
                VideoTable.Cols.FILE_NAME + " CHAR(50), " +
                VideoTable.Cols.DURATION + " INTEGER, " +
                VideoTable.Cols.SUBMITTED + " INTEGER, " +
                VideoTable.Cols.ADDRESS + " VARCHAR(100), " +
                VideoTable.Cols.LATITUDE + " DOUBLE, " +
                VideoTable.Cols.LONGITUDE + " DOUBLE)"
                );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
    }

    /**
     * Returns a list of videos on the specified page, using the specified number
     * of videos that appear in a single page.
     *
     * @param pageNumber the page number for the videos to be returned
     * @param videosPerPage how many videos are on a single results page
     * @param database the database from which to fetch videos
     * @param submittedOnly whether to return only submitted videos
     * @return a list of videos on the specified page
     */
    public static List<Video> getVideos(int pageNumber, int videosPerPage, SQLiteDatabase database, boolean submittedOnly)
    {
        List<Video> videos = new ArrayList<Video>(videosPerPage);
        String limit = String.valueOf(videosPerPage);
        String offset = String.valueOf(videosPerPage * pageNumber);
        String whereClause = null;
        if (submittedOnly)
        {
            whereClause = "submitted != 0";
        }
        Cursor cursor = database.query(
                VideoTable.NAME,
                null, //return all columns
                whereClause, //return all rows
                null, //return all rows
                null, //no grouping
                null, //no grouping
                null, //default sort order
                offset + "," + limit //return the specified page
                );

        VideoCursorWrapper videoCursor = new VideoCursorWrapper(cursor);

        videoCursor.moveToFirst();
        while (!videoCursor.isAfterLast())
        {
            videos.add(videoCursor.getVideo());
            videoCursor.moveToNext();
        }

        return videos;
    }
    
    /**
     * Converts a Video instance to a ContentValues for insertion into the database.
     *
     * @param video the Video instance to convert to a ContentValues
     * @return a ContentValues instance representing the specified Video
     */
    private static ContentValues getContentValues(Video video)
    {
        ContentValues values = new ContentValues();
        values.put(VideoTable.Cols.TITLE, video.getTitle());
        values.put(VideoTable.Cols.FILE_NAME, video.getFileName());
        values.put(VideoTable.Cols.DURATION, video.getDuration());
        values.put(VideoTable.Cols.SUBMITTED, video.isSubmitted());
        values.put(VideoTable.Cols.ADDRESS, video.getAddress());
        //special treatment for LatLng
        LatLng latLng = video.getLatLng();
        if (latLng != null)
        {
            values.put(VideoTable.Cols.LATITUDE, latLng.getLatitude());
            values.put(VideoTable.Cols.LONGITUDE, latLng.getLongitude());
        }
        else
        {
            values.put(VideoTable.Cols.LATITUDE, -1);
            values.put(VideoTable.Cols.LONGITUDE, -1);
        }
        return values;
    }

    /**
     * Adds a new Video to the database.
     *
     * @param video the video to be added to the database
     * @param database the database instance to which the video should be added
     */
    public static boolean addVideo(Video video, SQLiteDatabase database)
    {
        ContentValues values = getContentValues(video);
        long id = database.insert(VideoTable.NAME, null, values);
        if (id != -1)
        {
            //after inserting into the db, be sure to update the video's id
            //with the one used in the table
            video.setId(id);
            return true;
        }
        return false;
    }

    /**
     * Updates the title of the specified video to the specified string.
     *
     * @param video the video whose title is to be updated
     * @param database the database containing the video to be updated
     */
    public static boolean editVideoTitle(Video video, SQLiteDatabase database)
    {
        ContentValues values = new ContentValues();
        values.put(VideoTable.Cols.TITLE, video.getTitle());
        String id = Long.toString(video.getId());
        return (database.update(VideoTable.NAME, values, "_id=?", new String[] {id}) == 1);
    }

    /**
     * Sets the latitude/longitude of a video entry.
     *
     * @param latitude the latitude of the capture location
     * @param longitude the longitude of the capture location
     */
    public static boolean geocodeVideo(Video video, double latitude,
            double longitude, SQLiteDatabase database)
    {
        ContentValues values = new ContentValues();
        values.put(VideoTable.Cols.LATITUDE, latitude);
        values.put(VideoTable.Cols.LONGITUDE, longitude);
        String id = Long.toString(video.getId());
        return (database.update(VideoTable.NAME, values, "_id=?", new String[] {id}) == 1);
    }

    /**
     * Sets the address of a video entry.
     *
     * @param address the address of the capture location.
     */
    public static boolean addressVideo(Video video, String address, SQLiteDatabase database)
    {
        ContentValues values = new ContentValues();
        values.put(VideoTable.Cols.ADDRESS, address);
        String id = Long.toString(video.getId());
        return (database.update(VideoTable.NAME, values, "_id=?", new String[] {id}) == 1);
    }

    /**
     * Submits a video to the virtual backend.
     */
    public static boolean submitVideos(List<Video> videos, SQLiteDatabase database)
    {
        //just update the row items in the database with
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("_id=?");
        //for each additional video after first, tack on a OR
        for (int i = 1; i < videos.size(); i++)
        {
            whereClause.append(" OR _id=?");
        }
        String[] whereArgs = new String[videos.size()];
        for (int i = 0; i < videos.size(); i++)
        {
            whereArgs[i] = Long.toString(videos.get(i).getId());
        }
        //content values that just updates the submitted column
        ContentValues values = new ContentValues();
        values.put(VideoTable.Cols.SUBMITTED, 1);
        return database.update(VideoTable.NAME, values, whereClause.toString(), whereArgs) == videos.size();
    }

    /**
     * Removes the specified videos from the database.
     *
     * @param videos the videos to be removed from the database.
     * @param database the database instance from which the videos should be removed
     */
    public static boolean removeVideos(List<Video> videos, SQLiteDatabase database)
    {
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("_id=?");
        //for each additional video after first, tack on a OR
        for (int i = 1; i < videos.size(); i++)
        {
            whereClause.append(" OR _id=?");
        }
        String[] whereArgs = new String[videos.size()];
        for (int i = 0; i < videos.size(); i++)
        {
            whereArgs[i] = Long.toString(videos.get(i).getId());
        }
        return database.delete(VideoTable.NAME, whereClause.toString(), whereArgs) == videos.size();
    }

    /**
     * Removes the specified video from the database.
     *
     * @param video the video to be removed from the database.
     * @param database the database instance from which the video should be removed
     */
    public static boolean removeVideo(Video video, SQLiteDatabase database)
    {
        List<Video> videos = new ArrayList<Video>();
        videos.add(video);
        return removeVideos(videos, database);
    }

    /**
     * Updates an existing Video in the specified database.
     *
     * @param video the instance to be updated in the database
     * @param database the database containing the specified instance
     */
    public static void updateVideo(Video video, SQLiteDatabase database)
    {
        ContentValues values = getContentValues(video);
        //get the id, which will be used to specify the instance to be updated
        String id = String.valueOf(video.getId());
        database.update(VideoTable.NAME, values, "_id = ?", new String[] {id});
    }
}
