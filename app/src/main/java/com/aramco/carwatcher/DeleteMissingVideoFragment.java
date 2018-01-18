package com.aramco.carwatcher;

import android.app.Dialog;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

/**
 * A simple {@link DialogFragment} subclass.
 * Use the {@link DeleteMissingVideoFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class DeleteMissingVideoFragment extends DialogFragment
{
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        //use the builder to construct the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.missing_video_message)
            .setTitle(R.string.missing_video_title)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id)
                {
                    //need to access database to delete video entries
                    SQLiteDatabase database = new VideoBaseHelper(getActivity()).getWritableDatabase();
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id)
                {
                    //do nothing if cancelling
                }
            });
        return builder.create();
    }

    public static DeleteMissingVideoFragment newInstance(Video video)
    {
        Bundle args = new Bundle();
        args.putParcelable("ARG_VIDEO", video);
        DeleteMissingVideoFragment fragment = new DeleteMissingVideoFragment();
        fragment.setArguments(args);
        return fragment;
    }
}
