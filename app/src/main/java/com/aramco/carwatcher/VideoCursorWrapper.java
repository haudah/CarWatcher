package com.aramco.carwatcher;

import android.database.Cursor;
import android.database.CursorWrapper;
import com.aramco.carwatcher.VideoDbSchema.VideoTable;

/**
 * This private helper class handles cursor operations involving Video objects.
 */
public class VideoCursorWrapper extends CursorWrapper
{
    public VideoCursorWrapper(Cursor cursor)
    {
        super(cursor);
    }

    /**
     * Returns a single Video object from the cursor's current position.
     */
    public Video getVideo()
    {
        long id = getLong(getColumnIndex("_id"));
        String title = getString(getColumnIndex(VideoTable.Cols.TITLE));
        String fileName = getString(getColumnIndex(VideoTable.Cols.FILE_NAME));
        String comment = getString(getColumnIndex(VideoTable.Cols.COMMENT));
        int duration = getInt(getColumnIndex(VideoTable.Cols.DURATION));
        boolean submitted = (getInt(getColumnIndex(VideoTable.Cols.SUBMITTED)) != 0);
        String address = getString(getColumnIndex(VideoTable.Cols.ADDRESS));
        double latitude = getDouble(getColumnIndex(VideoTable.Cols.LATITUDE));
        double longitude = getDouble(getColumnIndex(VideoTable.Cols.LONGITUDE));
        LatLng latLng = new LatLng(latitude, longitude);

        return new Video(id, title, fileName, comment, duration, address, submitted, latLng);
    }
}
