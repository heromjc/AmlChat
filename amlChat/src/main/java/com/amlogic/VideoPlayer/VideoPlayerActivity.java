package com.amlogic.VideoPlayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Debug;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import	android.widget.RadioButton;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import com.google.android.exoplayer.demo.DemoUtil;

import com.amlogic.avpipe.AVTypes;
import com.amlogic.avpipe.IVideoDevice;
import com.amlogic.avpipe.LocalAudioLoopThread;

import com.amlogic.avpipe.ReadRequest;
import com.amlogic.avpipe.VideoCapture;
import com.amlogic.avpipe.VideoDeviceInputImpl;
import com.amlogic.avpipe.VideoDeviceOutputImpl;
import com.amlogic.avpipe.VideoFormatInfo;
import android.os.SystemProperties;
import android.app.FragmentManager;

public class VideoPlayerActivity extends Activity implements
		VideoDeviceInputImpl.EncodedFrameListener
{
	private static final String TAG = "VideoPlayerActivity";
	private static final int BUFFER_SIZE = 512 * 1024;
	private static int FRAME_THROUGHPUT_INTERVAL = 60;
	private static boolean USE_SW_ENCODER = false;
	private static final boolean ENCODER_ONLY = false;
	private static boolean isIPTV = true;
	private VideoFormatInfo mVideoFormatInfo;
	private VideoDeviceInputImpl mVideoInput;
	private VideoDeviceOutputImpl mVideoOutput;
	private Thread mVideoThread = null;
	private ByteBuffer mVideoBuffer;
	private boolean mIsStarted = false;
	private AlertDialog mVideoResDialog = null;
	private boolean mDebug = false;

	private boolean mEncoderCapturing = false;
	private String mEncoderCaptureFilename = "/sdcard/encoder_capture.ts";
	private String mEncoderCaptureFilename_ext = null;

	private static String mKeep_mode_threshold = "/sys/class/thermal/thermal_zone0/keep_mode_threshold";
	private static String mTrip_point_0_temp = "/sys/class/thermal/thermal_zone0/trip_point_0_temp";
	private static String mScaling_max_freq = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq";
	private static String mGpu_scale_mode = "/sys/class/mpgpu/scale_mode";
	private static String mGpu_cur_freq = "/sys/class/mpgpu/cur_freq";
	private String mMaxTemp_Org = null;
	private String mMinTemp_Org = null;
	private String mCpu_max_freq_Org = null;
	private static String mSetMaxTemp = "110";
	private static String mSetMinTemp = "100";
	private static String mSetMaxFreq = "1000000";
	private String mScale_mode_Org = null;
	private static String mScale_mode_val = "2";
	private static String mCur_freq_val = "1";

	private FileOutputStream mEncoderCaptureStream;

	// resources
	private Button mStartStopButton;
	private Button mEncoderOptimizationsButton;
	private Button mEncoderCaptureButton;
	private Button mRequestIdr;
	private RadioGroup mRadioGroup;
	private RadioButton mRadioButton1;
	private RadioButton mRadioButton2;

	private CheckBox mSwEncodeCheckBox;
	private SurfaceView mPreviewSfc;
	private SurfaceView mDecodeSfc;
	private LinearLayout mStatsFrame;
	private Button mDebugLogBtn;
	private String[] mSourceList =
	{ 		"http://192.168.1.108:8080/hls/vod/720p/gopro/gopro.m3u8",
			"http://192.168.1.108:8080/hls/vod/1080p/frozen/frozen.m3u8",
			"http://192.168.1.108:8080/hls/vod/480p/audi/audi.m3u8"
	};

	public static final Sample[] HLSSamples = new Sample[]
	{
		new Sample("Apple TS media playlist",
			"http://192.168.1.108:8080/hls/vod/480p/audi/audi.m3u8",
			DemoUtil.TYPE_HLS),

		new Sample("Apple master playlist",
			"http://192.168.1.108:8080/hls/vod/720p/gopro/gopro.m3u8",
			DemoUtil.TYPE_HLS),

		new Sample(
			"Apple master playlist advanced",
			"http://192.168.1.108:8080/hls/vod/1080p/frozen/frozen.m3u8",
			DemoUtil.TYPE_HLS),
	};
	public static final Sample[] MISCSamples = new Sample[]
	{
		new Sample("misc",
			"/storage/external_storage/sdcard1/vp8_720p.webm",
			DemoUtil.TYPE_OTHER),

		new Sample("misc",
			"/storage/external_storage/sdcard1/gopro.mp4",
			DemoUtil.TYPE_OTHER),
	};
	private int mSourceListSize = mSourceList.length;
	private int mSourceIdx = 0;
	private String mSource = mSourceList[mSourceIdx];
	private LocalAudioLoopThread mAudioLoop;
	public static ConditionVariable sCv = new ConditionVariable();
	private String video_avc = "video/avc";
	private String video_hevc = "video/hevc";


	private Uri contentUri;
	private int contentType;
	private String contentId;
	File ext_dir = new File("/storage/external_storage/sda1/");
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mVideoFormatInfo = new VideoFormatInfo();

		mRadioGroup=(RadioGroup)findViewById(R.id.rg);
		mRadioButton1=(RadioButton)findViewById(R.id.rb1);
		mRadioButton2=(RadioButton)findViewById(R.id.rb2);

		mVideoInput = new VideoDeviceInputImpl();
		mVideoInput.setCallback(new VideoCallback(true));

		if (!ENCODER_ONLY)
		{
		mVideoOutput = new VideoDeviceOutputImpl();
		mVideoOutput.setCallback(new VideoCallback(false));
		}

		mVideoBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

		// either or, one of these next routines will feed the decoder.
		mVideoInput.setEncodedFrameListener(this);
		// runVideoThread()
		SystemProperties.set("amlchat.status.enable", "disable");
		loadResources();
		mAudioLoop = new LocalAudioLoopThread();

		sCv.close();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		mStartStopButton.setText("Start");
		updateStats();
	}

	@Override
	protected void onPause()
	{
		if (mIsStarted)
		{
			stopVideo();
			VideoCapture.Instance().closeCamera();
		}
		if (mVideoResDialog != null)
		{
			mVideoResDialog.dismiss();
		}
		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		VideoCapture.Instance().closeCamera();
		super.onDestroy();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		// TODO Auto-generated method stub
		Log.d(TAG, "onKeyDown1");
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			// 弹出确定退出对话框
			new AlertDialog.Builder(this)
					.setTitle("退出")
					.setMessage("确定退出吗？")
					.setPositiveButton("确定",
							new DialogInterface.OnClickListener()
							{

								@Override
								public void onClick(DialogInterface dialog,
										int which)
								{
									// TODO Auto-generated method stub
									Intent exit = new Intent(Intent.ACTION_MAIN);
									exit.addCategory(Intent.CATEGORY_HOME);
									exit.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
									startActivity(exit);
									System.exit(0);
								}
							})
					.setNegativeButton("取消",
							new DialogInterface.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface dialog,
										int which)
								{
									// TODO Auto-generated method stub
									dialog.cancel();
								}
							}).show();
			// 这里不需要执行父类的点击事件，所以直接return
			return true;
		}
		Log.d(TAG, "onKeyDown2");
		if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
		{
			mSourceIdx++;
			if (mSourceIdx >= mSourceListSize)
				mSourceIdx = 0;
			mSource = mSourceList[mSourceIdx];
		}
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
		{
			mSourceIdx--;
			if (mSourceIdx < 0)
				mSourceIdx = mSourceListSize - 1;
			mSource = mSourceList[mSourceIdx];
		}
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
				|| keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
		{
//			mPlayerWrapper.stop();
//			String source = SystemProperties.get("media.demo.uri", null);
//			if (source == null || source.equals(""))
//				source = mSourceList[mSourceIdx];
//			mPlayerWrapper.setmSource(source);
//			mPlayerWrapper.start();
		}
		// 继续执行父类的其他点击事件
		return super.onKeyDown(keyCode, event);
	}

	public VideoDeviceInputImpl getVideoInputDevice()
	{
		return mVideoInput;
	}

	private void loadResources()
	{
		contentUri = Uri.parse(MISCSamples[1].uri);
		contentType = MISCSamples[1].type;
		contentId = MISCSamples[1].contentId;


		mStatsFrame = (LinearLayout) findViewById(R.id.frameStats);
		mDecodeSfc = (SurfaceView) findViewById(R.id.videoOutput);
		mPreviewSfc = (SurfaceView) findViewById(R.id.videoInput);
		mSwEncodeCheckBox = (CheckBox) findViewById(R.id.cbUseSoftEnc);
		if (isIPTV) {
			mSwEncodeCheckBox.setEnabled(false);
			mSwEncodeCheckBox.setVisibility(View.GONE);
		}
		mRequestIdr = (Button) findViewById(R.id.btRequestidr);
		if(isIPTV) {
			mRequestIdr.setEnabled(false);
			mRequestIdr.setVisibility(View.GONE);
		}
		mRequestIdr.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mVideoInput.setRequestIdr(true);
			}
		});


		mEncoderCaptureButton = (Button) findViewById(R.id.btnEncoderCapture);
		mEncoderCaptureButton.setText("Start Encoder Capture");
		mEncoderCaptureButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mEncoderCapturing = !mEncoderCapturing;
				if (mEncoderCapturing)
				{
					mEncoderCaptureButton.setText("Capturing..");
					mEncoderCaptureFilename_ext = null;
					mEncoderCaptureFilename = "/sdcard/encoder_capture.ts";
					//mEncoderCaptureFilename_ext = "/storage/external_storage/sda1/Video/encoder_capture.ts";
					List<String> sd_path = new ArrayList<String>();
					sd_path = loadDir();
					if(!sd_path.isEmpty()) {
						mEncoderCaptureFilename_ext = sd_path.get(0);
					}

					if(mEncoderCaptureFilename_ext != null) {
						Log.e("TAG", "Exist SdCard");
						File ext_dir_video = new File(mEncoderCaptureFilename_ext + "/Video/");
						if(!ext_dir_video.exists()) {
							ext_dir_video.mkdir();
						}
						mEncoderCaptureFilename_ext = mEncoderCaptureFilename_ext + "/Video/encoder_capture.ts";
						mEncoderCaptureFilename = mEncoderCaptureFilename_ext;
					}
					String text = "write file to " + mEncoderCaptureFilename;
					Toast.makeText(getApplicationContext(), text,
							Toast.LENGTH_SHORT).show();
					try {
						mEncoderCaptureStream = new FileOutputStream(mEncoderCaptureFilename);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				}
				else
				{
					mEncoderCaptureButton.setText("Start Encoder Capture");
					String text = "Wrote log " + mEncoderCaptureFilename;
					try
					{
						mEncoderCaptureStream.flush();
						mEncoderCaptureStream.close();
					} catch (IOException e)
					{
						e.printStackTrace();
					}
					mEncoderCaptureStream = null;
					Toast.makeText(getApplicationContext(), text,
							Toast.LENGTH_SHORT).show();
				}
			}
		});

		mDebugLogBtn = (Button) findViewById(R.id.debugBtn);
		if(isIPTV) {
			mDebugLogBtn.setEnabled(false);
			mDebugLogBtn.setVisibility(View.GONE);
		}
		mDebugLogBtn.setText("Start Logging");
		mDebugLogBtn.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mDebug = !mDebug;
				if (mDebug)
				{
					mDebugLogBtn.setText("Logging...");
					Debug.startMethodTracing("videoplayer");
				}
				else
				{
					mDebugLogBtn.setText("Start Logging");
					Debug.stopMethodTracing();
					String text = "Wrote log /sdcard/videoplayer";
					Toast.makeText(getApplicationContext(), text,
							Toast.LENGTH_SHORT).show();
				}
			}
		});

		mEncoderOptimizationsButton = (Button) findViewById(R.id.encoderOptimizationsButton);
		mEncoderOptimizationsButton
				.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						FragmentManager fm = getFragmentManager();
						EncoderOptimizationsDialog d = EncoderOptimizationsDialog
								.newInstance(1, mIsStarted);
						d.show(fm, "dialog");
					}
				});

		mStartStopButton = (Button) findViewById(R.id.startStopBtn);
		mStartStopButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (mIsStarted == true)
				{
					mStartStopButton.setText("Start");
					mSwEncodeCheckBox.setClickable(true);
					if (mRadioButton1.isChecked()) {
						mVideoFormatInfo.setMimeType(video_avc);
					}
					if (mRadioButton2.isChecked()) {
						mVideoFormatInfo.setMimeType(video_hevc);
					}
					stopVideo();
					mAudioLoop.setRecording(false);
				}
				else
				{
					mSwEncodeCheckBox.setClickable(false);
					if (mRadioButton1.isChecked())
						mVideoFormatInfo.setMimeType(video_avc);
					if (mRadioButton2.isChecked())
						mVideoFormatInfo.setMimeType(video_hevc);
					if(isIPTV)
						showResolutionOptions_iptv();
					else
						showResolutionOptions();
				}
				updateStats();
			}
		});
	}

	/**
	 * 获取节点值
	 */
	private static String getString(String path) {
		String prop = "waiting";// 默认值
		try {
			BufferedReader reader = new BufferedReader(new FileReader(path));
			prop = reader.readLine();
			Log.e("TAG","getString " + path + " == " + prop);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return prop;
	}

	/**
	 * 设置节点值
	 */
	private static void setString(String path, String value) {
		try {
			BufferedWriter bufWriter = null;
			bufWriter = new BufferedWriter(new FileWriter(path));
			bufWriter.write(value);
			bufWriter.close();
			Log.d(TAG,"setString " + value + " to path " + path);
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG,"can't write the " + path);
		}
	}
/*
	public int getPropBitrate(int w) {
		int bitrate = 0;
		if (w <= 512) {
			bitrate = 1000000;
		} else if (w <= 640) {
			bitrate = 1500000;
		} else if (w <= 960) {
			bitrate = 2000000;
		} else if (w <= 1280) {
			bitrate = 3000000;
		} else if (w <= 1920) {
			bitrate = 6000000;
		} else {
			bitrate = 6000000;
		}
		return bitrate;
	}
*/
	public void onEncodedFrame(MediaCodec.BufferInfo info)
	{
		if (mVideoInput.read(new ReadRequest(mVideoBuffer)) > 0)
		{
			if (mEncoderCapturing && mEncoderCaptureStream != null)
			{
				mVideoBuffer.rewind();
				try
				{
					if (mVideoBuffer.isDirect())
					{
						byte[] out = new byte[mVideoBuffer.remaining()];
						mVideoBuffer.get(out);
						mEncoderCaptureStream.write(out);
					}
					else
					{
						mEncoderCaptureStream.write(mVideoBuffer.array());
					}
				} catch (IOException e)
				{
					e.printStackTrace();
				}
			}

			mVideoBuffer.rewind();
			if(mVideoOutput.write(mVideoBuffer, info.presentationTimeUs,
					((info.flags & MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0)) == 1) {
				mVideoInput.setRequestIdr(true);
				Log.e(TAG, "debug: request idr");
			}
		}
	}

	private void runVideoThread()
	{
		if (mVideoThread == null)
		{
			mVideoThread = new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					while (true)
					{
						MediaCodec.BufferInfo info = mVideoInput.peek();
						if (info != null)
						{
							if (mVideoInput.read(new ReadRequest(mVideoBuffer)) > 0)
							{
								if (!ENCODER_ONLY)
								{
									mVideoBuffer.rewind();
									mVideoOutput
											.write(mVideoBuffer,
													info.presentationTimeUs,
													((info.flags & MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0));
								}
							}
						}
					}
				}
			});
			mVideoThread.start();
		}
	}

	private void showResolutionOptions()
	{
        String text = "请检查是否有camera插入";
        if (VideoCapture.Instance().openCamera(true) == false) {
            Toast.makeText(getApplicationContext(), text,
                    Toast.LENGTH_SHORT).show();
            return;
        }
		final AVTypes.VideoFmt[] fmts = VideoCapture.Instance()
				.getSupportedFormats();
		CharSequence[] videoResItems = new CharSequence[fmts.length];
		for (int i = 0; i < fmts.length; ++i)
		{
			videoResItems[i] = Integer.toString(fmts[i].width) + "x"
					+ Integer.toString(fmts[i].height);
		}

		// Creating and Building the Dialog
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Select Video Resolution");
		builder.setCancelable(false);
		builder.setSingleChoiceItems(videoResItems, -1,
				new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int item)
					{
						mVideoFormatInfo.setWidth(fmts[item].width);
						mVideoFormatInfo.setHeight(fmts[item].height);
						//mVideoFormatInfo.setBitRate(getPropBitrate(fmts[item].width));
						startVideo();
						startChatAudio();
						mVideoResDialog.dismiss();
					}
				});
		mVideoResDialog = builder.create();
		mVideoResDialog.show();
	}

	private void showResolutionOptions_iptv()
	{
		String text = "请检查是否有camera插入";
		if (VideoCapture.Instance().openCamera(true) == false) {
			Toast.makeText(getApplicationContext(), text,
					Toast.LENGTH_SHORT).show();
			return;
		}
		final AVTypes.VideoFmt[] fmts = VideoCapture.Instance()
				.getSupportedFormats();
		int index = 0;
		for (int i = 0; i < fmts.length; ++i)
		{
			if(fmts[i].width == 1920 && fmts[i].height == 1080)
				index = index + 1;
			if(fmts[i].width == 1280 && fmts[i].height == 720)
				index = index + 1;
			if(fmts[i].width == 640 && fmts[i].height == 480)
				index = index + 1;
			if(fmts[i].width == 320 && fmts[i].height == 240)
				index = index + 1;
		}

		CharSequence[] videoResItems = new CharSequence[index];
		final int[] index_eq = new int[index];
		int kk = 0;
		for (int i = fmts.length - 1; i >= 0 ; --i)
		{
			if(fmts[i].width == 1920 && fmts[i].height == 1080) {
				videoResItems[kk] = Integer.toString(fmts[i].width) + "x"
						+ Integer.toString(fmts[i].height);
				index_eq[kk] = i;
				if(kk<index)
					kk = kk + 1;
			}
			if(fmts[i].width == 1280 && fmts[i].height == 720) {
				videoResItems[kk] = Integer.toString(fmts[i].width) + "x"
						+ Integer.toString(fmts[i].height);
				index_eq[kk] = i;
				if(kk<index)
					kk = kk + 1;
			}
			if(fmts[i].width == 640 && fmts[i].height == 480) {
				videoResItems[kk] = Integer.toString(fmts[i].width) + "x"
						+ Integer.toString(fmts[i].height);
				index_eq[kk] = i;
				if(kk<index)
					kk = kk + 1;
			}
			if(fmts[i].width == 320 && fmts[i].height == 240) {
				videoResItems[kk] = Integer.toString(fmts[i].width) + "x"
						+ Integer.toString(fmts[i].height);
				index_eq[kk] = i;
				if(kk<index)
					kk = kk + 1;
			}
		}

		// Creating and Building the Dialog
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Select Video Resolution");
		builder.setCancelable(false);
		builder.setSingleChoiceItems(videoResItems, -1,
				new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int item)
					{
						mVideoFormatInfo.setWidth(fmts[index_eq[item]].width);
						mVideoFormatInfo.setHeight(fmts[index_eq[item]].height);
						//mVideoFormatInfo.setBitRate(getPropBitrate(fmts[item].width));
						startVideo();
						startChatAudio();
						mVideoResDialog.dismiss();
					}
				});
		builder.setCancelable(true);
		mVideoResDialog = builder.create();
		mVideoResDialog.show();
	}

	private void startVideo()
	{
		mDecodeSfc.setVisibility(View.VISIBLE);
		mPreviewSfc.setVisibility(View.VISIBLE);

		startLatencyTracking();

		if(isIPTV && (mVideoFormatInfo.getWidth() >= 1920)) {
			mMaxTemp_Org = getString(mKeep_mode_threshold);
			mMinTemp_Org = getString(mTrip_point_0_temp);
			mScale_mode_Org = getString(mGpu_scale_mode);
			mCpu_max_freq_Org = getString(mScaling_max_freq);
			Log.e(TAG, "maxTemp = " + mMaxTemp_Org + ";" + "minTemp = " + mMinTemp_Org);
			setString(mGpu_scale_mode,mScale_mode_val);
			setString(mKeep_mode_threshold, mSetMaxTemp);
			setString(mTrip_point_0_temp, mSetMinTemp);
			setString(mScaling_max_freq,mSetMaxFreq);
			if(getString(mGpu_scale_mode).equals(mScale_mode_val)) {
				setString(mGpu_cur_freq, mCur_freq_val);
			}
		}

		if (!ENCODER_ONLY)
		{
			mVideoOutput.setShowView(mDecodeSfc);
			mVideoOutput.open(mVideoFormatInfo, false/*
													 * mSwDecodeCheckBox.isChecked
													 * ()
													 */);
			mVideoOutput.start();
		}

		mVideoInput.setShowView(mPreviewSfc);
		if (mSwEncodeCheckBox.isChecked())
			USE_SW_ENCODER = true;
		else
			USE_SW_ENCODER = false;
		mVideoInput.open(mVideoFormatInfo, USE_SW_ENCODER);
		mVideoInput.start();

		mIsStarted = true;
		mStartStopButton.setText("Stop");
	}
	private void startChatAudio()
	{
		mAudioLoop.setRecording(true);
		Log.d(TAG, "local audio loop started");
		//mAudioLoop.start();
	}

	private void stopVideo()
	{
		mIsStarted = false;

		mVideoInput.stop();
		mVideoInput.close();

		if (!ENCODER_ONLY)
		{
			mVideoOutput.stop();
			mVideoOutput.close();
		}

		mDecodeSfc.setVisibility(View.INVISIBLE);
		mPreviewSfc.setVisibility(View.INVISIBLE);
		if(isIPTV && (mVideoFormatInfo.getWidth() >= 1920)) {
			setString(mKeep_mode_threshold, mMaxTemp_Org);
			setString(mTrip_point_0_temp, mMinTemp_Org);
			setString(mScaling_max_freq, mCpu_max_freq_Org);
			setString(mGpu_scale_mode,mScale_mode_Org);
		}
	}

	private void updateStats()
	{
		final String isShowStatus= SystemProperties.get("amlchat.status.enable");
		runOnUiThread(new Runnable()
		{
			public void run()
			{
				mStatsFrame.removeAllViews();
				if (mIsStarted) {
					if (isShowStatus.equals("enable")) {
					String videoFormat = Integer.toString(mVideoFormatInfo
							.getWidth())
							+ "x"
							+ Integer.toString(mVideoFormatInfo.getHeight());

					addLineToStats("Video Info: " + videoFormat + " at "
							+ (mVideoFormatInfo.getBitRate() / 1000) + "kbps");
//					String showStats = SystemProperties.get(
//							"media.demo.showstats", null);
//					if (showStats == null || showStats.equals(""))
//						return;
					addLineToStats("Time Statistics (camera / encoder / decoder / total):");
/*					addLineToStats("First Frame Delay = "
							+ Integer.toString(getStartupLatencyCam()) + "ms"
							+ " / "
							+ Integer.toString(getStartupLatencyCapture())
							+ "ms" + " / "
							+ Integer.toString(getStartupLatencyPlayback())
							+ "ms" + " / "
							+ Integer.toString(getStartupLatencyPlayback())
							+ "ms");
*/
					addLineToStats("Average Latency = " + "NA" + " / "
							+ Integer.toString(getAverageLatencyCapture())
							+ "ms" + " / "
							+ Integer.toString(getAverageLatencyPlayback())
							+ "ms" + " / "
							+ Integer.toString(getAverageLatencyTotal()) + "ms");
					addLineToStats("Throughput = "
							+ Integer.toString(getThroughputCam()) + "fps"
							+ " / " + Integer.toString(getThroughputCapture())
							+ "fps" + " / "
							+ Integer.toString(getThroughputTotal()) + "fps"
							+ " / " + Integer.toString(getThroughputTotal())
							+ "fps");
					addLineToStats("real_Time_BitrateEnc = " + Integer.toString(getBitrate()) + "Kb/s");
					addLineToStats("Avg_BitrateEnc = " + Integer.toString(getBitrate_Avg()) + "Kb/s");
				}
				}
			}
		});
	}

	private void addLineToStats(String line)
	{
		TextView tv = new TextView(this);
		tv.setTextColor(Color.RED);
		tv.setText(line);
		mStatsFrame.addView(tv);
	}

	private class VideoTag
	{
		public long pts;
		public long nanoTimeStart;
		public long nanoTimeStartDec;
	}

	private List<String> loadDir() {
		File mounts = new File("/proc/mounts");
		List<String> paths = new ArrayList<String>();

		if(mounts.exists()) {
			try {
				BufferedReader reader = new BufferedReader(
						new FileReader(mounts));
				try {
					String text = null;
					while((text = reader.readLine()) != null) {
						Log.d(TAG, text);
						if(text.startsWith("/dev/block/vold/")) {
							String[] splits = text.split(" ");
							Log.d(TAG, "len= " + splits.length);
							if(splits.length > 2) {
								Log.d(TAG, splits[1]);
								paths.add(splits[1]);
							}
						}
					}
				}
				finally{
					reader.close();
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		else {
			Log.w(TAG, "File</proc/mounts> is not exists");
		}
		return paths;
	}

	private static int mFrameCount = 0;
	private static int mFrameCountEnc = 0;
	private static int mFrameCountCam = 0;
	private static long mNanoTimeStart = 0;
	private static long mNanoTimeStartDec = 0;
	private static long mNanoTimeStartEnc = 0;
	private static long mNanoTimeStartCam = 0;
	private static long mStartLatencyDec = 0;
	private static long mStartLatencyEnc = 0;
	private static long mStartLatencyCam = 0;
	private static long mLatencyTotal = 0;
	private static long mLatencyDec = 0;
	private static long mLatencyEnc = 0;
	private static long mAverageLatencyTotal = 0;
	private static long mAverageLatencyDec = 0;
	private static long mAverageLatencyEnc = 0;
	private static int mThroughput = 0;
	private static int mThroughputEnc = 0;
	private static int mThroughputCam = 0;
	private static long mPreviousPTS = -1;
	private static long mPreviousPTSEnc = -1;
	private static long mPreviousPTSDec = -1;
	private static int mBitrate = 0;
	private static long mBitrate_All = 0;
	private static int mBitrateEnc = 0;
	private static int mBitrate_Avg = 0;
	private static int Bitratecount = 0;
	private static long mStartBitrateEnc = 0;
	private static long mFrameCountEnc_Bitrate = 0;
	private static LinkedList<VideoTag> videoTagQ = new LinkedList<VideoTag>();

	private static void startLatencyTracking()
	{
		mBitrate = 0;
		mBitrateEnc = 0;
		mBitrate_All = 0;
		mBitrate_Avg = 0;
		Bitratecount = 0;
		mStartBitrateEnc = 0;
		mFrameCountEnc_Bitrate = 0;
		mFrameCount = 0;
		mFrameCountEnc = 0;
		mFrameCountCam = 0;
		mNanoTimeStart = System.nanoTime();
		mNanoTimeStartEnc = 0;
		mNanoTimeStartDec = 0;
		mNanoTimeStartCam = 0;
		mStartLatencyDec = 0;
		mStartLatencyEnc = 0;
		mStartLatencyCam = 0;
		mLatencyTotal = 0;
		mLatencyDec = 0;
		mLatencyEnc = 0;
		mAverageLatencyTotal = 0;
		mAverageLatencyDec = 0;
		mAverageLatencyEnc = 0;
		mThroughput = 0;
		mThroughputEnc = 0;
		mThroughputCam = 0;
		mPreviousPTS = -1;
		videoTagQ.clear();
	}

	private static int getAverageLatencyCapture()
	{
		return (int) (mAverageLatencyEnc / 1000000);
	}

	private static int getAverageLatencyPlayback()
	{
		return (int) (mAverageLatencyDec / 1000000);
	}

	private static int getAverageLatencyTotal()
	{
		return (int) (mAverageLatencyTotal / 1000000);
	}

	private static int getStartupLatencyCam()
	{
		return (int) (mStartLatencyCam / 1000000);
	}

	private static int getStartupLatencyCapture()
	{
		return (int) (mStartLatencyEnc / 1000000);
	}

	private static int getStartupLatencyPlayback()
	{
		return (int) (mStartLatencyDec / 1000000);
	}

	private static int getThroughputCam()
	{
		return mThroughputCam;
	}

	private static int getThroughputCapture()
	{
		return mThroughputEnc;
	}

	private static int getThroughputTotal()
	{
		return mThroughput;
	}

	private static int getBitrate() {return mBitrateEnc;}

	private static int getBitrate_Avg() {return mBitrate_Avg;}

	private class VideoCallback implements IVideoDevice.IVideoDeviceCallback
	{

		private boolean mInput;

		public VideoCallback(boolean input)
		{
			mInput = input;
		}

		@Override
		public void onFrameRecv(long pts)
		{
			if (mInput)
			{
				long currTime = System.nanoTime();
				if (mNanoTimeStartCam == 0)
				{
					mNanoTimeStartCam = currTime;
				}
				updateCamThroughput(currTime);
			}
		}

		@Override
		public void onFrameIn(long pts)
		{
			long currTime = System.nanoTime();
			if (mInput)
			{
				if (mNanoTimeStartEnc == 0)
				{
					mNanoTimeStartEnc = currTime;
				}
				addNewTag(pts, currTime);
			}
			else
			{
				if (mNanoTimeStartDec == 0)
				{
					mNanoTimeStartDec = currTime;
				}
				setTagDecStartTime(pts, currTime);
			}
		}

		@Override
		public void onFrameOut(long pts , int bitrate)
		{
			long currTime = System.nanoTime();
			if (mInput)
			{
				if (isOutFrameValid(pts))
				{
					updateEncThroughput(currTime, bitrate);
					updateEncLatency(pts, currTime);
				}
				if (ENCODER_ONLY)
				{
					removeTag(pts);
				}
			}
			else
			{
				if ((mPreviousPTS != pts) && (mPreviousPTS != (pts - 10)))
				{
					if (isOutFrameValid(pts))
					{
						updateDecThroughput(currTime);
						updateDecLatency(pts, currTime);
					}
					removeTag(pts);
				}
				mPreviousPTS = pts;
			}
		}

		@Override
		public void onFrameConsumed(long pts)
		{
		}

		private void addNewTag(long pts, long currTime)
		{
			synchronized (videoTagQ)
			{
				VideoTag tag = new VideoTag();
				tag.pts = pts;
				tag.nanoTimeStart = currTime;
				videoTagQ.add(tag);
			}
		}

		private void removeTag(long pts)
		{
			synchronized (videoTagQ)
			{
				while (!videoTagQ.isEmpty())
				{
					// ignore bogus frames on startup
					VideoTag tag = videoTagQ.peek();
					if (tag != null)
					{
						if (pts < (tag.pts - 10))
						{
							break;
						}
					}

					tag = videoTagQ.pop();
					if ((tag.pts == pts) || (tag.pts == (pts + 10)))
					{
						break;
					}
				}
			}
		}

		private boolean isOutFrameValid(long pts)
		{
			synchronized (videoTagQ)
			{
				ListIterator<VideoTag> it = videoTagQ.listIterator(0);
				while (it.hasNext())
				{
					VideoTag tag = it.next();
					if ((tag.pts == pts) || (tag.pts == (pts + 10)))
					{
						return true;
					}
				}
			}
			return false;
		}

		private void setTagDecStartTime(long pts, long currTime)
		{
			synchronized (videoTagQ)
			{
				ListIterator<VideoTag> it = videoTagQ.listIterator(0);
				while (it.hasNext())
				{
					VideoTag tag = it.next();
					if ((tag.pts == pts) || (tag.pts == (pts + 10)))
					{
						tag.nanoTimeStartDec = currTime;
						break;
					}
				}
			}
		}

		private void updateEncLatency(long pts, long currTime)
		{
			synchronized (videoTagQ)
			{
				ListIterator<VideoTag> it = videoTagQ.listIterator(0);
				while (it.hasNext())
				{
					VideoTag tag = it.next();
					if ((tag.pts == pts) || (tag.pts == (pts + 10)))
					{
						mLatencyEnc = mLatencyEnc
								+ (currTime - tag.nanoTimeStart);
						break;
					}
				}
			}
		}

		private void updateDecLatency(long pts, long currTime)
		{
			synchronized (videoTagQ)
			{
				ListIterator<VideoTag> it = videoTagQ.listIterator(0);
				while (it.hasNext())
				{
					VideoTag tag = it.next();
					if ((tag.pts == pts) || (tag.pts == (pts + 10)))
					{
						mLatencyTotal = mLatencyTotal
								+ (currTime - tag.nanoTimeStart);
						mLatencyDec = mLatencyDec
								+ (currTime - tag.nanoTimeStartDec);
						break;
					}
				}
			}
		}

		private void updateCamThroughput(long currTime)
		{
			if (mStartLatencyCam == 0)
			{
				mStartLatencyCam = currTime - mNanoTimeStart;
				updateStats();
			}
			if (++mFrameCountCam == FRAME_THROUGHPUT_INTERVAL)
			{

				int elapsedMS = (int) ((currTime - mNanoTimeStartCam) / 1000000);
				if (elapsedMS > 0)
				{
					mThroughputCam = (int) (mFrameCountCam * 1000 / elapsedMS);
				}

				mFrameCountCam = 0;
				mNanoTimeStartCam = currTime;

				updateStats();
			}
		}

		private void updateEncThroughput(long currTime, int bitrate)
		{
			if (mStartLatencyEnc == 0)
			{
				mStartLatencyEnc = currTime - mNanoTimeStart;
				updateStats();
			}
			mBitrate = mBitrate + bitrate;
			if (++mFrameCountEnc == FRAME_THROUGHPUT_INTERVAL)
			{
				++ Bitratecount;
				mAverageLatencyEnc = mLatencyEnc / mFrameCountEnc;

				int elapsedMS = (int) ((currTime - mNanoTimeStartEnc) / 1000000);
				if (elapsedMS > 0)
				{
					mThroughputEnc = (int) (mFrameCountEnc * 1000 / elapsedMS);
					mBitrateEnc = (int) (mBitrate * 8 / elapsedMS);
					mBitrate_All = mBitrate_All + mBitrateEnc;
					mBitrate_Avg = (int) (mBitrate_All/Bitratecount);
				}
				mBitrate = 0;
				mFrameCountEnc = 0;
				mLatencyEnc = 0;
				mNanoTimeStartEnc = currTime;

				updateStats();
			}
		}

		private void updateDecThroughput(long currTime)
		{
			if (mStartLatencyDec == 0)
			{
				mStartLatencyDec = currTime - mNanoTimeStart;
				updateStats();
			}
			if (++mFrameCount == FRAME_THROUGHPUT_INTERVAL)
			{
				mAverageLatencyTotal = mLatencyTotal / mFrameCount;
				mAverageLatencyDec = mLatencyDec / mFrameCount;

				int elapsedMS = (int) ((currTime - mNanoTimeStartDec) / 1000000);
				if (elapsedMS > 0)
				{
					mThroughput = (int) (mFrameCount * 1000 / elapsedMS);
				}

				mFrameCount = 0;
				mLatencyTotal = 0;
				mLatencyDec = 0;
				mNanoTimeStartDec = currTime;

				updateStats();
			}
		}
	}

	public static class Sample
	{

		public final String name;
		public final String contentId;
		public final String uri;
		public final int type;

		public Sample(String name, String uri, int type)
		{
			this(name, name.toLowerCase(Locale.US).replaceAll("\\s", ""), uri,
					type);
		}

		public Sample(String name, String contentId, String uri, int type)
		{
			this.name = name;
			this.contentId = contentId;
			this.uri = uri;
			this.type = type;
		}

	}
}
