package com.aramco.carwatcher;


import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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
    //the currently highlighted items
    private Set<Video> highlightedVideos;
    //true when the list is in highlight mode
    private boolean highlighting;

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
        //the video list fragment will need to show options when items are highlighted
        setHasOptionsMenu(true);
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
        //initialize set of highlighted videos
        highlightedVideos = new HashSet<Video>();
        //and use it to create the adapter for the list
        adapter = new VideoAdapter(videos);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        if (highlighting)
        {
            inflater.inflate(R.menu.menu_video_list, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.menu_item_delete_video:
                //delete the video files first
                for (Video v : highlightedVideos)
                {
                    //get the full path for the video
                    String videoPath = CaptureService.getVideoFilePath(v.getFileName(), getActivity());
                    File file = new File(videoPath);
                    if (file.exists())
                    {
                        file.delete();
                    }
                }
                VideoBaseHelper.removeVideos(new ArrayList<Video>(highlightedVideos), database);
                stopHighlighting(1);
                return true;
            case R.id.menu_item_submit_video:
                VideoBaseHelper.submitVideos(new ArrayList<Video>(highlightedVideos), database);
                stopHighlighting(2);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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
            implements View.OnClickListener, View.OnLongClickListener
    {
        //references to the views inside the list item
        private View itemView;
        private TextView nameTextView;
        private TextView durationTextView;
        private TextView locationTextView;
        private TextView submittedTextView;
        //the video currently held by the holder (needed for onClick)
        private Video video;
        //the thumbnail image view
        private ImageView thumbnailImageView;

        public VideoHolder(View itemView)
        {
            super(itemView);
            this.itemView = itemView;
            //the holder will handle the view item's clicks
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
            //get references to all the views in the item
            nameTextView = (TextView)itemView.findViewById(R.id.list_item_video_name_textview);
            durationTextView = (TextView)itemView.findViewById(R.id.list_item_video_duration_textview);
            locationTextView = (TextView)itemView.findViewById(R.id.list_item_video_location_textview);
            submittedTextView = (TextView)itemView.findViewById(R.id.list_item_video_submitted_textview);
            thumbnailImageView = (ImageView)itemView.findViewById(R.id.list_item_video_thumbnail_imageview);
        }

        //called by the adapter to have the holder change the view to reflect the item
        //being held
        public void bindVideo(Video v)
        {
            nameTextView.setText(v.getTitle());
            //get the formatted string for duration
            int minutes = v.getDuration() / 60;
            int seconds = v.getDuration() - minutes * 60;
            durationTextView.setText(String.format("%02dm:%02ds", minutes, seconds));
            locationTextView.setText(v.getLocation());
            //if video was submitted updated submission text and color
            if (v.isSubmitted())
            {
                submittedTextView.setText(R.string.yes);
                int color = getActivity().getResources().getColor(R.color.green);
                submittedTextView.setTextColor(color);
            }
            else
            {
                submittedTextView.setText(R.string.no);
                int color = getActivity().getResources().getColor(R.color.red);
                submittedTextView.setTextColor(color);
            }
            //if its a highlighted video, set the background color
            if (highlightedVideos.contains(v))
            {
                int color = getActivity().getResources().getColor(R.color.bluishGrey);
                itemView.setBackgroundColor(color);
            }
            else
            {
                int color = getActivity().getResources().getColor(R.color.white);
                itemView.setBackgroundColor(color);
            }
            this.video = v;
        }

        //clicking on an item should open the video in the phone's gallery
        @Override
        public void onClick(View v)
        {
            //action depends on whether we're highlighting or not
            if (!highlighting)
            {
                //get full path of file using filename
                String videoFilePath =
                    CaptureService.getVideoFilePath(video.getFileName(), getActivity());
                //check if file exists, and if not, offer to delete entry
                File videoFile = new File(videoFilePath);
                if (!videoFile.exists())
                {
                }
                else
                {
                    //when user clicks on video item, play it in the media player
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoFilePath));
                    intent.setDataAndType(Uri.parse(videoFilePath), "video/mp4");
                    startActivity(intent);
                }
            }
            else
            {
                highlight(video, v);
            }
        }
        //long clicking on an item should start the highlighting mode
        @Override
        public boolean onLongClick(View v)
        {
            if (!highlighting)
            {
                highlight(video, v);
            }
            //always consumes the long click
            return true;
        }
    }

    /**
     * Starts the highlighting mode with the specified video highlighted.
     */
    private void highlight(Video video, View view)
    {
        //if it's not already in highlighting mode, set it to highlighting
        //and show the menu icons
        if (!highlighting)
        {
            highlighting = true;
            getActivity().invalidateOptionsMenu();
        }
        //if this item isn't highlighted
        if (!highlightedVideos.contains(video))
        {
            int color = getActivity().getResources().getColor(R.color.bluishGrey);
            view.setBackgroundColor(color);
            highlightedVideos.add(video);
        }
        //if this item is already highlighted
        else
        {
            int color = getActivity().getResources().getColor(R.color.white);
            view.setBackgroundColor(color);
            highlightedVideos.remove(video);
        }
    }

    /**
     * Clears the highlightedVideos list, turns off highlighting mode, and redraws the
     * list. The action on highlighted videos depends on the parameter:
     *
     * @param action 0 indicates cancellation, 1 indicates deletion, 2 indicates submission
     */
    private void stopHighlighting(int action)
    {
        if (action == 0)
        {
            //do nothing
        }
        else if (action == 1)
        {
            for (Video v : highlightedVideos)
            {
                //remove from the main list
                videos.remove(v);
            }
            //notify the adapter that certain items have been removed
            adapter.notifyDataSetChanged();
        }
        else if (action == 2)
        {
            //notify the adapter that certain items have been modified
            adapter.notifyDataSetChanged();
        }
        highlighting = false;
        highlightedVideos.clear();
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
