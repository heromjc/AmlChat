package com.amlogic.VideoPlayer;

import android.app.DialogFragment;
import android.os.Bundle;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.KeyEvent;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.util.Log;

import com.amlogic.avpipe.AVTypes;

public class EncoderOptimizationsDialog extends DialogFragment implements OnClickListener{

    private static final String TAG = "EncoderOptimization";
   // public static final String BACK_STACK_NAME = "encoder_optimizations_dialog";

    private RelativeLayout rlOptimizationBack;
    private RelativeLayout rlDummy; /* accepts focus after a textview doesn't want it anymore */

    /* Bitrate */
    private CheckBox cbEncoderBitrate;
    private EditText etEncoderBitrate;
    private Button bEncoderBitrate;
    private EditText etIframeInterval;
    private Spinner spinner;
    private int mFramerate = 0;
    private Button btIframeIntv;
    private CheckBox cbEncoderIframeInterval;
    private int mNum;
    private int spinner_pos = 0;
    private boolean mStarted = false;

    /**
     * Create a new instance of EncoderOptimizationsDialogFragment, providing "num"
     * as an argument.
     */
    static EncoderOptimizationsDialog newInstance(int num, boolean isstarted) {
        EncoderOptimizationsDialog f = new EncoderOptimizationsDialog();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putInt("num", num);
        f.setStartedFlag(isstarted);
        f.setArguments(args);

        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNum = getArguments().getInt("num");
        Log.d(TAG, "mNum = " + mNum);
        // Pick a style based on the num.
        int style = DialogFragment.STYLE_NORMAL, theme = 0;
        switch ((mNum-1)%6) {
            case 1: style = DialogFragment.STYLE_NO_TITLE; break;
            case 2: style = DialogFragment.STYLE_NO_FRAME; break;
            case 3: style = DialogFragment.STYLE_NO_INPUT; break;
            case 4: style = DialogFragment.STYLE_NORMAL; break;
            case 5: style = DialogFragment.STYLE_NORMAL; break;
            case 6: style = DialogFragment.STYLE_NO_TITLE; break;
            case 7: style = DialogFragment.STYLE_NO_FRAME; break;
            case 8: style = DialogFragment.STYLE_NORMAL; break;
        }

         switch ((mNum-1)%6) {
            case 4: theme = android.R.style.Theme_Holo; break;
            case 5: theme = android.R.style.Theme_Holo_Light_Dialog; break;
            case 6: theme = android.R.style.Theme_Holo_Light; break;
            case 7: theme = android.R.style.Theme_Holo_Light_Panel; break;
            case 8: theme = android.R.style.Theme_Holo_Light; break;
        }
        setStyle(style, theme);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.developer_options_encoder_optimizations, container, false);

        rlDummy = (RelativeLayout) view.findViewById(R.id.rlDummy);
        rlOptimizationBack = (RelativeLayout) view.findViewById(R.id.rlOptimizationBack);
        rlOptimizationBack.setOnClickListener(this);

        AVTypes.EncoderOptimizations opts = ((VideoPlayerActivity)getActivity()).getVideoInputDevice().getEncoderOptimizations();

        /* Encoder bitrate */
        cbEncoderBitrate = (CheckBox) view.findViewById(R.id.cbOptimizationEncoderBitrate);
        cbEncoderBitrate.setOnClickListener(this);
        cbEncoderBitrate.setChecked(opts.encoder_bitrate_enable);

        etEncoderBitrate = (EditText) view.findViewById(R.id.etOptimizationEncoderBitrate);
        etEncoderBitrate.setText(Integer.toString(opts.encoder_bitrate_value));
        etEncoderBitrate.setEnabled(cbEncoderBitrate.isChecked());
        etEncoderBitrate.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    InputMethodManager inputManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                    rlDummy.requestFocus();
                    return true;
                }
                else if (event.getAction() == KeyEvent.ACTION_UP) {
                    // prevent forwarding to MainActivity
                    return true;
                }
                return false;
            }
        });

        bEncoderBitrate = (Button) view.findViewById(R.id.bOptimizationEncoderBitrate);
        bEncoderBitrate.setVisibility(cbEncoderBitrate.isChecked() ? View.VISIBLE : View.GONE);
        bEncoderBitrate.setOnClickListener(new View.OnClickListener() {
             public void onClick(View v) {
                 String bitrate =  etEncoderBitrate.getText().toString();
                 if (bitrate.isEmpty() || Integer.parseInt(bitrate) == 0) {
                     Toast.makeText(getActivity(), "Must set a positive number for bitrate", Toast.LENGTH_SHORT);
                 }
                 storeEncoderOptimizations();
             }
        });


        /* Encoder Iframe Interval*/
        cbEncoderIframeInterval = (CheckBox) view.findViewById(R.id.cbOptimizationIframe_Interval);
        cbEncoderIframeInterval.setOnClickListener(this);
        cbEncoderIframeInterval.setChecked(opts.encoder_iframe_interval_enable);
        if (mStarted) {
            cbEncoderIframeInterval.setChecked(false);
            cbEncoderIframeInterval.setEnabled(false);
        }
        etIframeInterval = (EditText) view.findViewById(R.id.etOptimizationiframeInterval);
        etIframeInterval.setText(Integer.toString(opts.encoder_iframe_interval));
        etIframeInterval.setEnabled(cbEncoderIframeInterval.isChecked());
        etIframeInterval.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    InputMethodManager inputManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                    rlDummy.requestFocus();
                    return true;
                }
                else if (event.getAction() == KeyEvent.ACTION_UP) {
                    // prevent forwarding to MainActivity
                    return true;
                }
                return false;
            }
        });

        btIframeIntv = (Button) view.findViewById(R.id.bIframeintervalIframeInterval);
        btIframeIntv.setVisibility(cbEncoderIframeInterval.isChecked() ? View.VISIBLE : View.GONE);
        btIframeIntv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String strIframeInterval =  etEncoderBitrate.getText().toString();
                if (strIframeInterval.isEmpty() || Integer.parseInt(strIframeInterval) == 0) {
                    Toast.makeText(getActivity(), "Must set a positive number for IframeInterval", Toast.LENGTH_SHORT);
                }
                storeEncoderOptimizations();
            }
        });

        /* Encoder framerate*/
        spinner = (Spinner) view.findViewById(R.id.spinnerFramerate);
        String[] fps = getResources().getStringArray(R.array.Encoderfps);
        mFramerate = Integer.parseInt(fps[opts.encoder_framerate_pos].toString());
        Log.e("TAG","mFramerate = " + mFramerate);
        String[] mItems = getResources().getStringArray(R.array.Encoderfps);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_spinner_item, mItems);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(opts.encoder_framerate_pos,true);
        if (mStarted) {
            spinner.setEnabled(false);
        }

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int pos, long id) {

                String[] fps = getResources().getStringArray(R.array.Encoderfps);
                Log.e("TAG", "set fps =" + fps[pos]);
                mFramerate = Integer.parseInt(fps[pos].toString());
                spinner_pos = pos;
                storeEncoderOptimizations();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Another interface callback
            }
        });


        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.rlOptimizationBack:
            storeEncoderOptimizations();
            dismiss();
            break;
        case R.id.cbOptimizationEncoderBitrate:
            etEncoderBitrate.setEnabled(cbEncoderBitrate.isChecked());
            bEncoderBitrate.setVisibility(cbEncoderBitrate.isChecked() ? View.VISIBLE : View.GONE);
            break;
        case R.id.cbOptimizationIframe_Interval:
            etIframeInterval.setEnabled(cbEncoderIframeInterval.isChecked());
            btIframeIntv.setVisibility(cbEncoderIframeInterval.isChecked() ? View.VISIBLE : View.GONE);
            break;
        default:
            storeEncoderOptimizations();
            break;
        }
    }

    private void setStartedFlag(boolean isStarted) {
        mStarted = isStarted;
    }

    private void printOptimizations() {
        AVTypes.EncoderOptimizations opts = ((VideoPlayerActivity)getActivity()).getVideoInputDevice().getEncoderOptimizations();
        Log.v(TAG, "encoder_bitrate enable: " + opts.encoder_bitrate_enable);
        Log.v(TAG, "encoder_bitrate value: " + opts.encoder_bitrate_value);
    }

    private void storeEncoderOptimizations() {
        AVTypes.EncoderOptimizations opts = ((VideoPlayerActivity)getActivity()).getVideoInputDevice().getEncoderOptimizations();
        opts.encoder_bitrate_enable = cbEncoderBitrate.isChecked();
        opts.encoder_iframe_interval_enable = cbEncoderIframeInterval.isChecked();

        String val;
        val = etIframeInterval.getText().toString();
        opts.encoder_iframe_interval = val.isEmpty() ? 0 : Integer.parseInt(val);

        val = etEncoderBitrate.getText().toString();
        opts.encoder_bitrate_value = val.isEmpty() ? 0 : Integer.parseInt(val);

        opts.encoder_framerate_value = mFramerate;

        opts.encoder_framerate_pos = spinner_pos;
        ((VideoPlayerActivity)getActivity()).getVideoInputDevice().setEncoderOptimizations(opts);
    }
}
