package com.aramco.carwatcher;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link NoContentFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class NoContentFragment extends Fragment
{
    private static final String ARG_REASON = "reason";
    //the main prompt text
    private TextView promptTextView;
    //the resolution button
    private Button fixButton;
    //true when there is an active capture session
    private boolean capturing;

    //list of reasons for this no content fragment being shown
    public enum Reason
    {
        //user has not captured any videos
        NO_CAPTURED_VIDEOS,
        //user has not submitted any videos
        NO_SUBMITTED_VIDEOS
    }

    private Reason reason;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param reason the reason for this fragment being shown
     * @return A new instance of fragment NoContentFragment.
     */
    public static NoContentFragment newInstance(Reason reason)
    {
        NoContentFragment fragment = new NoContentFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_REASON, reason);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        //fetch the reason from arguments
        if (getArguments() != null)
        {
            reason = (Reason)getArguments().getSerializable(ARG_REASON);
        }
        capturing = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        //Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_no_content, container, false);
        //get view references
        promptTextView = (TextView)v.findViewById(R.id.no_content_prompt_textview);
        fixButton = (Button)v.findViewById(R.id.no_content_fix_button);

        //show the message corresponding to the reason
        switch (reason)
        {
            case NO_CAPTURED_VIDEOS:
                promptTextView.setText(R.string.no_captured_videos);
                break;
            case NO_SUBMITTED_VIDEOS:
                promptTextView.setText(R.string.no_submitted_videos);
                break;
        }

        //fixButton should start the capture service
        fixButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                Intent intent = new Intent();
                intent.setAction("com.aramco.carwatcher.TOGGLECAPTURE");
                //same intent is used for capture/stop capturing
                //Intent captureIntent = CaptureService.newIntent(getActivity(), false);
                getActivity().startService(intent);
                //getActivity().sendBroadcast(intent);
                if (!capturing)
                {
                    ((Button)v).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_record_red, 0, 0, 0);
                    ((Button)v).setText(R.string.stop_capturing);
                    capturing = true;
                }
                else
                {
                    ((Button)v).setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    ((Button)v).setText(R.string.capture);
                    capturing = false;
                }
            }
        });

        return v;
    }
}
