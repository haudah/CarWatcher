package com.aramco.carwatcher;

import android.app.Dialog;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.CheckBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link DialogFragment} subclass.
 * Use the {@link DeleteVideosFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class DeleteVideosFragment extends DialogFragment
{
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        //inflate the main frame view
        View view = getActivity().getLayoutInflater().inflate(R.layout.frame_delete_videos, null);
        //get the checkbox from the view
        final CheckBox deleteMediaCheckBox = (CheckBox)view.findViewById(R.id.delete_media_checkbox);
        //get the videos from argument
        final List<Video> videos = getArguments().getParcelableArrayList("ARG_VIDEOS");
        //get the message string
        String message = getResources().getString(R.string.delete_videos_message);
        //use the builder to construct the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(message)
            .setTitle(R.string.missing_video_title)
            .setView(view)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id)
                {
                    //delete the video files first if option is checked
                    if (deleteMediaCheckBox.isChecked())
                    {
                        for (Video v : videos)
                        {
                            //get the full path for the video
                            String videoPath = CaptureService.getVideoFilePath(v.getFileName(), getActivity());
                            File file = new File(videoPath);
                            if (file.exists())
                            {
                                file.delete();
                            }
                        }
                    }
                    //need to access database to delete video entries
                    SQLiteDatabase database = new VideoBaseHelper(getActivity()).getWritableDatabase();
                    VideoBaseHelper.removeVideos(videos, database);
                    //update the VideoListFragment if its set as target
                    if (getTargetFragment() instanceof VideoListFragment)
                    {
                        ((VideoListFragment)getTargetFragment()).stopHighlighting(1);
                    }
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id)
                {
                    //update the VideoListFragment
                    if (getTargetFragment() instanceof VideoListFragment)
                    {
                        ((VideoListFragment)getTargetFragment()).stopHighlighting(0);
                    }
                }
            });
        return builder.create();
    }

    public static DeleteVideosFragment newInstance(ArrayList<Video> videos)
    {
        Bundle args = new Bundle();
        args.putParcelableArrayList("ARG_VIDEOS", videos);
        DeleteVideosFragment fragment = new DeleteVideosFragment();
        fragment.setArguments(args);
        return fragment;
    }
}
