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
        String location = getString(getColumnIndex(VideoTable.Cols.LOCATION));
        int duration = getInt(getColumnIndex(VideoTable.Cols.DURATION));
        String fileName = getString(getColumnIndex(VideoTable.Cols.FILE_NAME));
        boolean submitted = (getInt(getColumnIndex(VideoTable.Cols.SUBMITTED)) != 0);

        return new Video(id, title, fileName, duration, location, submitted);
    }
}
