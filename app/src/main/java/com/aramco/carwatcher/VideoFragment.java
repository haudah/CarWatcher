package com.aramco.carwatcher;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A simple {@link DialogFragment} subclass.
 * Use the {@link VideoFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class VideoFragment extends DialogFragment
{
    //the nameTextView is an instance var since it's the only
    //one that may change
    private TextView nameTextView;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        //inflate the main frame view
        View view = getActivity().getLayoutInflater().inflate(R.layout.frame_video, null);
        //get the video from argument
        final Video video = getArguments().getParcelable("ARG_VIDEO");
        //get references to the views
        View deleteButton = view.findViewById(R.id.video_item_delete);
        View submitButton = view.findViewById(R.id.video_item_submit);
        TextView submitTextView = view.findViewById(R.id.video_item_submit_textview);
        nameTextView = (TextView)view.findViewById(R.id.video_item_name_textview);
        TextView durationTextView = (TextView)view.findViewById(R.id.video_item_duration_textview);
        ImageView nameEditImageView = (ImageView)view.findViewById(R.id.video_item_name_edit_imageview);
        TextView locationTextView = (TextView)view.findViewById(R.id.video_item_location_textview);
        ImageView thumbnailImageView = (ImageView)view.findViewById(R.id.video_item_thumbnail_imageview);
        //only one of these will be shown depending on submission status
        ImageView submitImageView = (ImageView)view.findViewById(R.id.video_item_submit_imageview);
        ImageView submittedImageView = (ImageView)view.findViewById(R.id.video_item_submitted_imageview);
        //set the view content to reflect video info
        nameTextView.setText(video.getTitle());
        int minutes = video.getDuration() / 60;
        int seconds = video.getDuration() - minutes * 60;
        durationTextView.setText(String.format("%02dm:%02ds", minutes, seconds));
        locationTextView.setText(video.getAddress());
        //clicking on edit image should open the name fragment
        nameEditImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                FragmentManager fm = getFragmentManager();
                VideoNameFragment fragment = VideoNameFragment.newInstance(video);
                fragment.setTargetFragment(VideoFragment.this, 0);
                fragment.show(fm, "video_name_fragment");
            }
        });
        //get full path of file using filename
        final String videoFilePath =
            CaptureService.getVideoFilePath(video.getFileName(), getActivity());
        //check if file exists, and if not, offer to delete entry
        File videoFile = new File(videoFilePath);
        if (videoFile.exists())
        {
            //if the video file exists, show its thumbnail
            GlideApp
                .with(getActivity().getApplicationContext())
                .load(Uri.fromFile(videoFile))
                .into(thumbnailImageView);
        }
        //if video was submitted update submission text and color
        if (video.isSubmitted())
        {
            submitTextView.setText(R.string.submitted);
            int color = getActivity().getResources().getColor(R.color.green);
            submitTextView.setTextColor(color);
            submitImageView.setVisibility(View.GONE);
            submittedImageView.setVisibility(View.VISIBLE);
        }
        else
        {
            submitTextView.setText(R.string.video_item_submit);
            int color = getActivity().getResources().getColor(R.color.colorPrimary);
            submitTextView.setTextColor(color);
            submitImageView.setVisibility(View.VISIBLE);
            submittedImageView.setVisibility(View.GONE);
        }
        thumbnailImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                //when user clicks on video item, play it in the media player
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoFilePath));
                intent.setDataAndType(Uri.parse(videoFilePath), "video/mp4");
                startActivity(intent);
            }
        });
        //use the builder to construct the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(view);
        final Dialog dialog = builder.create();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                FragmentManager fm = getFragmentManager();
                //let the DeleteVideosFragment handle deletion
                DeleteVideosFragment fragment =
                    DeleteVideosFragment.newInstance(new ArrayList<Video>(Arrays.asList(video)));
                fragment.show(fm, "delete_videos_fragment");
                dialog.dismiss();
            }
        });
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                //get or create sqlite database
                SQLiteDatabase database = new VideoBaseHelper(getActivity()).getWritableDatabase();
                VideoBaseHelper.submitVideos(new ArrayList<Video>(Arrays.asList(video)), database);
                dialog.dismiss();
            }
        });
        return dialog;
    }

    /**
     * Update the title of the video fragment with the specified string.
     *
     * @param title the new title of the video
     */
    public void updateTitle(String title)
    {
        nameTextView.setText(title);
    }

    public static VideoFragment newInstance(Video video)
    {
        Bundle args = new Bundle();
        args.putParcelable("ARG_VIDEO", video);
        VideoFragment fragment = new VideoFragment();
        fragment.setArguments(args);
        return fragment;
    }
}

