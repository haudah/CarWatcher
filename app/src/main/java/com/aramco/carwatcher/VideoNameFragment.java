package com.aramco.carwatcher;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link DialogFragment} subclass.
 * Use the {@link DeleteVideosFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class VideoNameFragment extends DialogFragment
{
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        //inflate the main frame view
        View view = getActivity().getLayoutInflater().inflate(R.layout.frame_video_name, null);
        //get the edittext from the view
        final EditText videoNameEditText = (EditText)view.findViewById(R.id.video_name_edittext);
        final TextView lengthTextView = (TextView)view.findViewById(R.id.video_name_length_textview);
        //get the video from argument
        final Video video = getArguments().getParcelable("ARG_VIDEO");
        //populate the views with video info
        String title = video.getTitle();
        videoNameEditText.setText(title);
        lengthTextView.setText(String.format("%d / 50", title.length()));
        //get the message string
        String message = getResources().getString(R.string.edit_video_name_message);
        //use the builder to construct the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(message)
            .setView(view)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id)
                {
                    //need to access database to delete video entries
                    SQLiteDatabase database = new VideoBaseHelper(getActivity()).getWritableDatabase();
                    String newTitle = videoNameEditText.getText().toString();
                    video.setTitle(newTitle);
                    VideoBaseHelper.editVideoTitle(video, database);
                    //update the target VideoFragment
                    ((VideoFragment)getTargetFragment()).updateTitle(newTitle);
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id)
                {
                    //nothing needs to be done on cancellation
                }
            });
        final AlertDialog dialog = builder.create();
        //show the keyboard when the dialog appears
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        //the ok button will be disabled if the name is blank
        videoNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (s.length() == 0)
                {
                    positiveButton.setEnabled(false);
                }
                else
                {
                    positiveButton.setEnabled(true);
                }
                //update the text view to reflect current length
                lengthTextView.setText(String.format("%d / 50", s.length()));
            }
        });

        return dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialog)
    {
        super.onDismiss(dialog);
        //hide keyboard when dialog is dismissed
        InputMethodManager imm =
                (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
    }

    public static VideoNameFragment newInstance(Video video)
    {
        Bundle args = new Bundle();
        args.putParcelable("ARG_VIDEO", video);
        VideoNameFragment fragment = new VideoNameFragment();
        fragment.setArguments(args);
        return fragment;
    }
}
