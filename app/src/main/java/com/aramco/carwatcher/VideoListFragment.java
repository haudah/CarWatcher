package com.aramco.carwatcher;


import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link VideoListFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class VideoListFragment extends Fragment
{
    //fragment argument for whether this list is for captured or submitted videos
    private static final String ARG_SUBMITTED_ONLY = "submitted";
    //the number of results in a page from the database
    private static final int RESULTS_PER_PAGE = 10;
    //true when the list is for submitted videos only
    private boolean submittedOnly;

    //the recycler view showing videos
    private RecyclerView videoRecyclerView;
    //the recycler view's adapter
    private VideoAdapter adapter;
    //the list of videos is obtained from the database
    private List<Video> videos;
    //the database that will be used to query for videos
    private SQLiteDatabase database;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param submittedOnly if true, specifies that the list is for submitted videos
     * @return A new instance of fragment VideoListFragment.
     */
    public static VideoListFragment newInstance(boolean submittedOnly)
    {
        VideoListFragment fragment = new VideoListFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_SUBMITTED_ONLY, submittedOnly);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (getArguments() != null)
        {
            submittedOnly = getArguments().getBoolean(ARG_SUBMITTED_ONLY);
        }

        //get or create sqlite database
        database = new VideoBaseHelper(getActivity()).getWritableDatabase();

        //get the list of videos from the video database
        videos = VideoBaseHelper.getVideos(0, RESULTS_PER_PAGE, database);
        //if there are no videos in the list, show the NoContentFragment
        if (videos.size() == 0)
        {
            ((MainActivity)getActivity()).showNoContent(NoContentFragment.Reason.NO_CAPTURED_VIDEOS);
            return;
        }
        //and use it to create the adapter for the list
        adapter = new VideoAdapter(videos);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_video_list, container, false);

        //get references to the needed views
        videoRecyclerView = (RecyclerView)view.findViewById(R.id.video_recycler_view);
        videoRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        videoRecyclerView.setAdapter(adapter);

        return view;
    }

    //the view holder that will hold video views
    private class VideoHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener
    {
        //references to the views inside the list item
        private TextView nameTextView;
        private TextView durationTextView;
        private TextView locationTextView;
        //the video currently held by the holder (needed for onClick)
        private Video video;
        //the thumbnail image view
        private ImageView thumbnailImageView;

        public VideoHolder(View itemView)
        {
            super(itemView);
            //the holder will handle the view item's clicks
            itemView.setOnClickListener(this);
            //get references to all the views in the item
            nameTextView = (TextView)itemView.findViewById(R.id.list_item_video_name_textview);
            durationTextView = (TextView)itemView.findViewById(R.id.list_item_video_duration_textview);
            locationTextView = (TextView)itemView.findViewById(R.id.list_item_video_location_textview);
            thumbnailImageView = (ImageView)itemView.findViewById(R.id.list_item_video_thumbnail_imageview);
        }

        //called by the adapter to have the holder change the view to reflect the item
        //being held
        public void bindVideo(Video v)
        {
            nameTextView.setText(v.getTitle());
            durationTextView.setText(v.getDuration());
            locationTextView.setText(v.getLocation());
        }

        //clicking on an item should open the video in the phone's gallery
        @Override
        public void onClick(View v)
        {
            //TODO: for now doing nothing on click
        }
    }

    //the adapter between the view holder and the recycler view
    private class VideoAdapter extends RecyclerView.Adapter<VideoHolder>
    {
        //the adapter must have a list of videos to be able to fill in view holders
        private List<Video> videos;

        public VideoAdapter(List<Video> videos)
        {
            this.videos = videos;
        }

        @Override
        public VideoHolder onCreateViewHolder(ViewGroup parent, int viewType)
        {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            View view = layoutInflater.inflate(R.layout.list_item_captured_video, parent, false);
            return new VideoHolder(view);
        }

        @Override
        public void onBindViewHolder(VideoHolder holder, int position)
        {
            //get the video from the list and bind it
            holder.bindVideo(videos.get(position));
        }

        @Override
        public int getItemCount()
        {
            return videos.size();
        }
    }
}
