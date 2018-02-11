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
    private static final int COMMENT_MAX_LENGTH = 150;
    private static final int TITLE_MAX_LENGTH = 50;
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        //inflate the main frame view
        View view = getActivity().getLayoutInflater().inflate(R.layout.frame_video_name, null);
        //get the edittext from the view
        final EditText videoNameEditText = (EditText)view.findViewById(R.id.video_name_edittext);
        final EditText videoCommentEditText = (EditText)view.findViewById(R.id.video_comment_edittext);
        final TextView lengthTextView = (TextView)view.findViewById(R.id.video_name_length_textview);
        //get the video from argument
        final Video video = getArguments().getParcelable("ARG_VIDEO");
        final boolean comment = getArguments().getBoolean("ARG_COMMENT");
        //populate the views with video info
        final EditText editText = comment? videoCommentEditText : videoNameEditText;
        String title = comment? video.getComment() : video.getTitle();
        editText.setText(title);
        if (comment)
        {
            videoNameEditText.setVisibility(View.GONE);
            videoCommentEditText.setVisibility(View.VISIBLE);
        }
        //max length of field depends on comment or title
        final int maxLength = comment? COMMENT_MAX_LENGTH : TITLE_MAX_LENGTH;
        lengthTextView.setText(String.format("%d / %d", title.length(), maxLength));
        //get the message string
        int messageId = 
            !comment? R.string.edit_video_name_message : R.string.edit_video_comment_message;
        String message = getResources().getString(messageId);
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
                    String newTitle = editText.getText().toString();
                    if (comment)
                    {
                        video.setComment(newTitle);
                        VideoBaseHelper.editVideoComment(video, database);
                        //update the target VideoFragment
                        ((VideoFragment)getTargetFragment()).updateComment(newTitle);
                    }
                    else
                    {
                        video.setTitle(newTitle);
                        VideoBaseHelper.editVideoTitle(video, database);
                        //update the target VideoFragment
                        ((VideoFragment)getTargetFragment()).updateTitle(newTitle);
                    }
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
        editText.addTextChangedListener(new TextWatcher() {
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
                lengthTextView.setText(String.format("%d / %d", s.length(), maxLength));
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

    /**
     * Contrary to what the name implies, this fragment also handles changes to the video's
     * comment when the comment boolean is true.
     *
     * @param video the video whose details are being modified
     * @param comment true when the dialog fragment is for editing the comment
     */
    public static VideoNameFragment newInstance(Video video, boolean comment)
    {
        Bundle args = new Bundle();
        args.putParcelable("ARG_VIDEO", video);
        args.putBoolean("ARG_COMMENT", comment);
        VideoNameFragment fragment = new VideoNameFragment();
        fragment.setArguments(args);
        return fragment;
    }
}
