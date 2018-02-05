package com.aramco.carwatcher;

public class VideoDbSchema
{
    public static final class VideoTable
    {
        //the name of the table in sqllite
        public static final String NAME = "videos";
        //and the table columns
        public static final class Cols
        {
            public static final String TITLE = "title";
            public static final String ADDRESS = "address";
            public static final String DURATION = "duration";
            public static final String FILE_NAME = "file_name";
            public static final String SUBMITTED = "submitted";
            public static final String LATITUDE = "latitude";
            public static final String LONGITUDE = "latitude";
        }
    }
}
