/*
 * Copyright (c) 2014 Simon Robinson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ac.robinson.paperchains;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.github.lassana.recorder.AudioRecorder;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;
import com.sonyericsson.zoom.DynamicZoomControl;
import com.sonyericsson.zoom.LongPressZoomListener;
import com.soundcloud.api.Env;
import com.soundcloud.api.Token;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import ac.robinson.dualqrscanner.CodeParameters;
import ac.robinson.dualqrscanner.DecoderActivity;
import ac.robinson.dualqrscanner.ImageParameters;
import ac.robinson.dualqrscanner.QRImageParser;
import ac.robinson.dualqrscanner.ViewfinderView;
import ac.robinson.dualqrscanner.camera.CameraUtilities;

public class PaperChainsActivity extends DecoderActivity {

	private static final int BUTTON_ANIMATION_DURATION = 250; // animation (and removal) time for recording interface

	private static com.soundcloud.playerapi.ApiWrapper sSoundCloudPlayerApiWrapper;
	private static com.soundcloud.api.ApiWrapper sSoundCloudUploaderApiWrapper;

	private static final int MODE_CAPTURE = 0;
	private static final int MODE_LISTEN = 1;
	private static final int MODE_ADD = 2;
	private static final int MODE_IMAGE_ONLY = 3;

	private static final int SOUNDCLOUD_LOGIN_RESULT = 1;

	private static final String BASE_URL = "http://www.enterise.info/";
	private static final String CODE_SERVER_URL = BASE_URL + "codemaker/pages.php";
	public static final String SOUNDCLOUD_LOGIN_URL = BASE_URL + "paperchains/soundcloud.html";

	private PaperChainsView mImageView;
	private DynamicZoomControl mZoomControl;
	private LongPressZoomListener mZoomListener;

	private ImageParameters mImageParameters;

	private final ArrayList<AudioAreaHolder> mAudioAreas = new ArrayList<>();
	private MediaPlayer mAudioPlayer;
	private AudioRecorder mAudioRecorder;
	private Rect mCurrentAudioRect;

	private int mCurrentMode;
	private String mPageId;

	private boolean mAudioAreasLoaded = false;
	private boolean mImageParsed = false;

	private AudioRecorderCircleButton mRecordButton;
	private AudioRecorderCircleButton mPlayButton;
	private AudioRecorderCircleButton mDeleteButton;
	private AudioRecorderCircleButton mSaveButton;

	private RotateAnimation mRotateAnimation;

	private class AudioAreaHolder {
		public final long soundCloudId;
		public final Rect serverRect;
		public Rect imageRect;

		public AudioAreaHolder(long soundCloudId, Rect serverRect) {
			this.soundCloudId = soundCloudId;
			this.serverRect = serverRect;
		}

		public void setImageRect(Rect imageRect) {
			this.imageRect = imageRect;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!CameraUtilities.getIsCameraAvailable(getPackageManager())) {
			Toast.makeText(PaperChainsActivity.this, getString(R.string.hint_no_camera), Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.main);

		setViews(R.id.viewfinder_view, R.id.preview_view, R.id.image_view);
		setResizeImageToView(false); // we want the high quality image

		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		// set up action bar
		ActionBar actionBar = getSupportActionBar();
		actionBar.setTitle(R.string.title_activity_capture);
		actionBar.setDisplayShowTitleEnabled(true);

		int resultPointColour = getResources().getColor(R.color.accent);
		((ViewfinderView) findViewById(R.id.viewfinder_view)).setResultPointColour(resultPointColour);

		// set up SoundCloud API wrappers (without a user token - for playback only, initially)
		setupSoundCloudApiWrappers();

		mCurrentMode = MODE_CAPTURE;

		// set up a zoomable view for the photo
		mImageView = (PaperChainsView) findViewById(R.id.image_view);
		mZoomControl = new DynamicZoomControl();
		mImageView.setZoomState(mZoomControl.getZoomState());
		mZoomControl.setAspectQuotient(mImageView.getAspectQuotient());
		mZoomListener = new LongPressZoomListener(PaperChainsActivity.this);
		mZoomListener.setZoomControl(mZoomControl);

		// set up buttons/handlers
		mImageView.setOnTouchListener(mZoomListener);
		mImageView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onImageClick();
			}
		});
		mImageView.setScribbleCallback(new PaperChainsView.ScribbleCallback() {
			@Override
			public void scribbleCompleted(Path scribble) {
				processScribble(scribble);
			}
		});

		mRecordButton = (AudioRecorderCircleButton) findViewById(R.id.record_button);
		mRecordButton.setOnClickListener(mRecordButtonListener);

		mPlayButton = (AudioRecorderCircleButton) findViewById(R.id.play_button);
		mPlayButton.setOnClickListener(mPlayButtonListener);

		mDeleteButton = (AudioRecorderCircleButton) findViewById(R.id.delete_button);
		mDeleteButton.setOnClickListener(mDeleteButtonListener);

		mSaveButton = (AudioRecorderCircleButton) findViewById(R.id.save_button);
		mSaveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				saveAudio();
			}
		});

		// set up animation for the play/save buttons
		mRotateAnimation = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
				0.5f);
		mRotateAnimation.setDuration(BUTTON_ANIMATION_DURATION);
		mRotateAnimation.setInterpolator(new LinearInterpolator());
		mRotateAnimation.setRepeatCount(Animation.INFINITE);
		mRotateAnimation.setRepeatMode(Animation.RESTART);
	}

	@Override
	public void onPause() {
		super.onPause();
		resetAudioPlayer();
		if (mAudioRecorder != null) {
			if (mAudioRecorder.isRecording()) {
				mAudioRecorder.pause(null);
			}
			// note: not set to null just in case they want to resume recording when returning
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (mCurrentMode == MODE_CAPTURE) {
			// no menus or interaction if we haven't got a page yet
			return super.onCreateOptionsMenu(menu);
		}

		getMenuInflater().inflate(R.menu.menu, menu);
		switch (mCurrentMode) {
			case MODE_LISTEN:
				menu.findItem(R.id.action_listen).setVisible(false);
				break;

			case MODE_ADD:
				menu.findItem(R.id.action_add_audio).setVisible(false);
				break;

			case MODE_IMAGE_ONLY:
				menu.findItem(R.id.action_listen).setVisible(false);
				menu.findItem(R.id.action_add_audio).setVisible(false);
				break;
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_add_audio:
				switchMode(MODE_ADD);
				return true;

			case R.id.action_listen:
				switchMode(MODE_LISTEN);
				return true;

			case R.id.action_rescan:
				switchMode(MODE_CAPTURE);
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onDecodeCompleted() {
		// Toast.makeText(TicQRActivity.this, "Decode completed; now taking picture", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onPageIdFound(final String id) {
		// Toast.makeText(TicQRActivity.this, "Page ID found", Toast.LENGTH_SHORT).show();

		new AsyncHttpClient().get(CODE_SERVER_URL, new RequestParams("lookup", id), new JsonHttpResponseHandler() {
			private void handleFailure(int reason) {
				// nothing we can do except browse the image
				switchMode(MODE_IMAGE_ONLY);
				Toast.makeText(PaperChainsActivity.this, getString(reason), Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
				try {
					if ("ok".equals(response.getString("status"))) {
						JSONArray areas = response.getJSONArray("audioAreas");
						if (areas != null && !areas.isNull(0)) {
							for (int i = 0; i < areas.length(); i++) {
								JSONObject jsonBox = areas.getJSONObject(i);
								mAudioAreas.add(new AudioAreaHolder(jsonBox.getLong("soundCloudId"),
										new Rect(jsonBox.getInt("left"), jsonBox.getInt("top"),
												jsonBox.getInt("right"), jsonBox.getInt("bottom"))));
							}
						}

						mPageId = id;
						mAudioAreasLoaded = true;
						if (mImageParsed) {
							addAudioRects();
							switchMode(MODE_LISTEN);
						}
					} else {
						handleFailure(R.string.hint_json_error);
					}
				} catch (JSONException e) {
					handleFailure(R.string.hint_json_error);
				}
			}

			@Override
			public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
				handleFailure(R.string.hint_connection_error);
			}
		});
	}

	@Override
	protected void onPictureError() {
		// note: an automatic rescan is started whenever this occurs, so this is mainly designed for, e.g.,
		// counting a large number of errors and prompting the user to reposition the camera
		// Toast.makeText(TicQRActivity.this, "Picture error", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onPictureCompleted(Bitmap parsedBitmap, ImageParameters imageParameters,
	                                  CodeParameters codeParameters) {
		// Toast.makeText(TicQRActivity.this, "Picture completed", Toast.LENGTH_SHORT).show();

		mImageView.setImage(parsedBitmap);

		mZoomControl.getZoomState().setPanX(0.5f);
		mZoomControl.getZoomState().setPanY(0.5f);
		mZoomControl.getZoomState().setZoom(1f);
		mZoomControl.startFling(0, 0); // because stopFling doesn't work...

		mImageView.setVisibility(View.VISIBLE);

		mImageParameters = imageParameters;
		// mCodeParameters = codeParameters; // not needed for this application

		mImageParsed = true;
		if (mAudioAreasLoaded) {
			addAudioRects();
			switchMode(MODE_LISTEN);
		}
	}

	private void switchMode(int newMode) {
		// TODO: check we're not recording/saving audio before doing this? (currently we allow it somewhat hackily)
		switch (newMode) {
			case MODE_ADD:
				resetAudioPlayer();
				getSupportActionBar().setTitle(getString(R.string.title_activity_add));
				mZoomListener.setPanZoomEnabled(false);
				mImageView.setScribbleEnabled(true);
				mImageView.setClickable(true);
				mImageView.setDrawAudioRectsEnabled(true);
				mCurrentMode = MODE_ADD;
				supportInvalidateOptionsMenu();
				break;

			case MODE_LISTEN:
				resetRecordingInterface(); // first as it also sets the the activity title
				getSupportActionBar().setTitle(getString(R.string.title_activity_explore));
				mZoomListener.setPanZoomEnabled(true);
				mImageView.setScribbleEnabled(false);
				mImageView.setClickable(true);
				mImageView.setDrawAudioRectsEnabled(false);
				mCurrentMode = MODE_LISTEN;
				supportInvalidateOptionsMenu();
				break;

			case MODE_CAPTURE:
				// reset our configuration and set up for rescanning
				mAudioAreas.clear();
				mImageView.clearAudioAreaRects();

				mAudioAreasLoaded = false;
				mImageParsed = false;
				mImageView.setVisibility(View.INVISIBLE); // must be invisible (not gone) as we need its dimensions

				resetAudioPlayer(); // TODO: fix odd intermittent rotation issue with the play button after rescanning
				resetRecordingInterface(); // first as it also sets the the activity title
				getSupportActionBar().setTitle(R.string.title_activity_capture);
				mZoomListener.setPanZoomEnabled(true);
				mImageView.setScribbleEnabled(false);
				mImageView.setClickable(false);
				mImageView.setDrawAudioRectsEnabled(false);
				mCurrentMode = MODE_CAPTURE;
				supportInvalidateOptionsMenu();

				requestScanResume();
				break;

			case MODE_IMAGE_ONLY:
				// allow image exploration, but no listening
				getSupportActionBar().setTitle(getString(R.string.title_activity_image_only));
				mZoomListener.setPanZoomEnabled(true);
				mCurrentMode = MODE_IMAGE_ONLY;
				supportInvalidateOptionsMenu();
				break;
		}
	}

	private void addAudioRects() {
		for (AudioAreaHolder holder : mAudioAreas) {
			// convert grid-based coordinates to image-based coordinates, accounting for image rotation/inversion by
			// making sure to use the min/max values of each coordinate
			Rect rect = holder.serverRect;
			PointF leftTop = QRImageParser.getImagePosition(mImageParameters, new PointF(rect.left, rect.top));
			PointF rightBottom = QRImageParser.getImagePosition(mImageParameters, new PointF(rect.right, rect.bottom));
			RectF displayRect = new RectF(Math.min(leftTop.x, rightBottom.x), Math.min(leftTop.y, rightBottom.y),
					Math.max(rightBottom.x, leftTop.x), Math.max(leftTop.y, rightBottom.y));
			Rect imageRect = new Rect();
			displayRect.roundOut(imageRect);

			holder.setImageRect(imageRect);
			mImageView.addAudioAreaRect(imageRect);
		}
	}

	private void onImageClick() {
		resetAudioPlayer();
		supportInvalidateOptionsMenu();

		Point touchPoint = mImageView.screenPointToImagePoint(mZoomListener.getLastTouchPoint());
		boolean rectTouched = false;
		for (AudioAreaHolder holder : mAudioAreas) {
			Rect rect = holder.imageRect;
			if (rect.contains(touchPoint.x, touchPoint.y)) {
				rectTouched = true;
				if (mCurrentMode == MODE_ADD) {
					if (rect.equals(mCurrentAudioRect)) {
						mRecordButton.performClick();
					} else {
						resetRecordingInterface();
					}
					break;
				} else if (mCurrentMode == MODE_LISTEN) {
					// TODO: handle overlapping rectangles (pop up several buttons as options?)
					initialisePlaybackButton(mZoomListener.getLastTouchPoint());
					mImageView.setDragCallback(new PaperChainsView.DragCallback() {
						@Override
						public void dragStarted() {
							resetAudioPlayer(); // we don't update the button position on drag; for now, just stop play
						}
					});
					new SoundCloudUrlFetcherTask(PaperChainsActivity.this, sSoundCloudPlayerApiWrapper).execute(holder
							.soundCloudId);
					break;
				}
			}
		}

		// remove a rect and re-enable scribbling when touching outside in add mode
		if (!rectTouched) {
			switch (mCurrentMode) {
				case MODE_ADD:
					resetRecordingInterface();
					break;
				case MODE_LISTEN:
					resetAudioPlayer();
					break;
			}
		}
	}

	private void processScribble(Path scribble) {
		try {
			// the file we're given via createTempFile is unplayable, but the name creation routine is useful...
			File outputFile = File.createTempFile(getString(R.string.app_name), ".mp4", getCacheDir());
			String outputFilePath = outputFile.getAbsolutePath();
			if (outputFile.delete()) {
				// get the bounding box and add to our list
				RectF scribbleBox = new RectF();
				scribble.computeBounds(scribbleBox, true);
				Rect audioArea = new Rect();
				scribbleBox.roundOut(audioArea);
				int scribbleWidth = Math.round(getResources().getDimensionPixelSize(R.dimen.scribble_stroke_width) /
						2f); // expand to include stroke width (half either side of line)
				audioArea.inset(-scribbleWidth, -scribbleWidth);

				// initialise recording
				resetRecordingInterface();
				mAudioRecorder = AudioRecorder.build(PaperChainsActivity.this, outputFilePath);

				mCurrentAudioRect = audioArea;
				mImageView.addAudioAreaRect(audioArea);
				mImageView.setScribbleEnabled(false);

				// position the recording buttons
				PointF centrePoint = mImageView.imagePointToScreenPoint(new Point(audioArea.centerX(),
						audioArea.centerY()));
				initialiseRecordingButtons(centrePoint);
			} else {
				Toast.makeText(PaperChainsActivity.this, getString(R.string.audio_recording_setup_error),
						Toast.LENGTH_SHORT).show();
			}

		} catch (IOException | IllegalArgumentException e) {
			Toast.makeText(PaperChainsActivity.this, getString(R.string.audio_recording_setup_error),
					Toast.LENGTH_SHORT).show();
		}
	}

	private final View.OnClickListener mRecordButtonListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (mAudioRecorder != null) {
				if (mAudioRecorder.isReady() || mAudioRecorder.isPaused()) {
					if (mPlayButton.getVisibility() == View.VISIBLE) {
						animateRecordingInterface(-1, mRecordButton); // -1 = in; hide other controls if visible
					}

					mAudioRecorder.start(new AudioRecorder.OnStartListener() {
						@Override
						public void onStarted() {
							mImageView.setClickable(false);
							mRecordButton.setRecorder(mAudioRecorder);
							mRecordButton.setImageResource(R.drawable.ic_pause_white_24dp);
						}

						@Override
						public void onException(Exception e) {
							mImageView.setClickable(true);
							Toast.makeText(PaperChainsActivity.this, getString(R.string.audio_recording_setup_error),
									Toast.LENGTH_SHORT).show();
						}
					});

				} else if (mAudioRecorder.isRecording()) {
					mRecordButton.setRecorder(null); // set before actually pausing to avoid concurrency issues
					mAudioRecorder.pause(new AudioRecorder.OnPauseListener() {
						@Override
						public void onPaused(String activeRecordFileName) {
							mRecordButton.setImageResource(R.drawable.ic_mic_white_24dp);
							animateRecordingInterface(1, null); // 1 = animate out
						}

						@Override
						public void onException(Exception e) {
							mImageView.setClickable(true);
							Toast.makeText(PaperChainsActivity.this, getString(R.string.audio_recording_pause_error),
									Toast.LENGTH_SHORT).show();
						}
					});
				} else {
					Toast.makeText(PaperChainsActivity.this, getString(R.string.audio_recording_setup_error),
							Toast.LENGTH_SHORT).show();
				}
			} else {
				Toast.makeText(PaperChainsActivity.this, getString(R.string.audio_recording_setup_error),
						Toast.LENGTH_SHORT).show();
			}
		}
	};

	private final View.OnClickListener mPlayButtonListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (mAudioPlayer != null) {
				if (mAudioPlayer.isPlaying()) {
					mAudioPlayer.pause();
					mPlayButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
				} else {
					mAudioPlayer.start();
					mPlayButton.setImageResource(R.drawable.ic_pause_white_24dp);
				}
			} else if (mAudioRecorder != null && mAudioRecorder.isPaused()) {
				streamAudio(mAudioRecorder.getRecordFileName());
			}
		}
	};

	private final View.OnClickListener mDeleteButtonListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			animateRecordingInterface(-1, mDeleteButton); // -1 = animate in
			delayedResetRecordingInterface();
		}
	};

	private void setupSoundCloudApiWrappers() {
		// we use two versions of the SoundCloud API as one works for playback; the other works for upload
		// (neither works for both without editing)
		String id = getString(R.string.soundcloud_client_id);
		String secret = getString(R.string.soundcloud_client_secret);
		sSoundCloudPlayerApiWrapper = new com.soundcloud.playerapi.ApiWrapper(id, secret, null, null);
		sSoundCloudUploaderApiWrapper = new com.soundcloud.api.ApiWrapper(id, secret, null, null, Env.LIVE);
	}

	private String getSoundCloudAccessToken() {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(PaperChainsActivity.this);
		return settings.getString(getString(R.string.pref_soundcloud_access_token), null);
	}

	private void setSoundCloudAccessToken(String token) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(PaperChainsActivity.this);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(getString(R.string.pref_soundcloud_access_token), token);
		editor.commit();
	}

	private void saveAudio() {
		mSaveButton.setClickable(false); // don't allow clicks regardless of status

		String accessToken = getSoundCloudAccessToken();
		if (TextUtils.isEmpty(accessToken)) {
			// TODO: if they *do* have SoundCloud installed, use the in-app token method
			startActivityForResult(new Intent(PaperChainsActivity.this, SoundCloudLoginActivity.class),
					SOUNDCLOUD_LOGIN_RESULT);
		} else {
			animateRecordingInterface(-1, mSaveButton); // -1 = animate in
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					mSaveButton.setImageResource(R.drawable.ic_refresh_white_24dp);
					mSaveButton.startAnimation(mRotateAnimation);
				}
			}, BUTTON_ANIMATION_DURATION); // delayed so that the rotation doesn't interfere with the inwards animation

			new SoundCloudUploadTask(PaperChainsActivity.this, sSoundCloudUploaderApiWrapper, new Token(accessToken,
					null, Token.SCOPE_NON_EXPIRING), mPageId, new Rect(mCurrentAudioRect)).execute(mAudioRecorder
					.getRecordFileName());
		}
	}

	public void audioSaveFailed(int reason) {
		mSaveButton.clearAnimation();
		mSaveButton.setImageResource(R.drawable.ic_done_white_24dp);
		animateRecordingInterface(1, null); // 1 = animate out
		mSaveButton.setClickable(true);
		int messageId = reason == -1 ? R.string.hint_audio_save_failed : reason;
		Toast.makeText(PaperChainsActivity.this, getString(messageId), Toast.LENGTH_SHORT).show();
	}

	public void audioSaveCompleted(final Rect audioRect, final long trackId) {
		// convert back to grid-based coordinates
		final PointF rectLeftTop = QRImageParser.getGridPosition(mImageParameters, new PointF(audioRect.left,
				audioRect.top));
		final PointF rectRightBottom = QRImageParser.getGridPosition(mImageParameters, new PointF(audioRect.right,
				audioRect.bottom));

		// account for image rotation/inversion by making sure to use the min/max values of each coordinate
		int left = Math.round(rectLeftTop.x);
		int top = Math.round(rectLeftTop.y);
		int right = Math.round(rectRightBottom.x);
		int bottom = Math.round(rectRightBottom.y);
		final int leftmost = Math.min(left, right);
		final int topmost = Math.min(top, bottom);
		final int rightmost = Math.max(left, right);
		final int bottommost = Math.max(top, bottom);

		RequestParams params = new RequestParams("newaudio", 1); // 1 reserved for possible future use as box ID
		params.put("left", leftmost);
		params.put("top", topmost);
		params.put("right", rightmost);
		params.put("bottom", bottommost);
		params.put("soundCloudId", trackId);
		params.put("pageId", mPageId);

		// update on the server
		new AsyncHttpClient().get(CODE_SERVER_URL, params, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
				try {
					if ("ok".equals(response.getString("status"))) {
						AudioAreaHolder holder = new AudioAreaHolder(trackId, new Rect(leftmost, topmost, rightmost,
								bottommost));
						holder.setImageRect(new Rect(audioRect));
						mAudioAreas.add(holder);
						mImageView.addAudioAreaRect(holder.imageRect);

						mSaveButton.clearAnimation();
						mSaveButton.setImageResource(R.drawable.ic_done_white_24dp);
						delayedResetRecordingInterface();
					} else {
						audioSaveFailed(R.string.hint_audio_save_failed);
					}
				} catch (JSONException e) {
					audioSaveFailed(R.string.hint_audio_save_failed);
				}
			}

			@Override
			public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
				audioSaveFailed(R.string.hint_audio_save_failed);
			}
		});
	}

	private void initialisePlaybackButton(PointF centrePoint) {
		mPlayButton.setLayoutParams(getLayoutParamsForButtonPosition(centrePoint, mPlayButton.getWidth(),
				mPlayButton.getHeight(), mImageView.getLeft(), mImageView.getTop(), mImageView.getRight(),
				mImageView.getBottom()));
		mPlayButton.setImageResource(R.drawable.ic_pause_white_24dp); // we know there is audio at this location
		mPlayButton.setVisibility(View.VISIBLE);
	}

	private void initialiseRecordingButtons(PointF centrePoint) {
		int parentLeft = mImageView.getLeft();
		int parentTop = mImageView.getTop();
		int parentRight = mImageView.getRight();
		int parentBottom = mImageView.getBottom();
		mRecordButton.setLayoutParams(getLayoutParamsForButtonPosition(centrePoint, mRecordButton.getWidth(),
				mRecordButton.getHeight(), parentLeft, parentTop, parentRight, parentBottom));
		mRecordButton.setVisibility(View.VISIBLE);

		// position the control buttons in anticipation of recording completion
		RelativeLayout.LayoutParams controlLayoutParams = getLayoutParamsForButtonPosition(centrePoint,
				mPlayButton.getWidth(), mPlayButton.getHeight(), parentLeft, parentTop, parentRight, parentBottom);
		mPlayButton.setLayoutParams(controlLayoutParams);
		mDeleteButton.setLayoutParams(controlLayoutParams);
		mSaveButton.setLayoutParams(controlLayoutParams);
		mPlayButton.setImageResource(R.drawable.ic_play_arrow_white_24dp); // in case it was used for playback
		mPlayButton.setVisibility(View.INVISIBLE);
		mDeleteButton.setVisibility(View.INVISIBLE);
		mSaveButton.setVisibility(View.INVISIBLE);

		getSupportActionBar().setTitle(getString(R.string.title_activity_record));
	}

	private RelativeLayout.LayoutParams getLayoutParamsForButtonPosition(PointF buttonPosition, int buttonWidth,
	                                                                     int buttonHeight, int parentLeft,
	                                                                     int parentTop, int parentRight,
	                                                                     int parentBottom) {
		RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(buttonWidth, buttonHeight);
		layoutParams.leftMargin = parentLeft + Math.round(buttonPosition.x - (buttonWidth / 2f));
		layoutParams.topMargin = parentTop + Math.round(buttonPosition.y - (buttonHeight / 2f));

		// need to set negative margins for when the button is close to the edges (its size would be changed otherwise)
		int rightPosition = layoutParams.leftMargin + buttonWidth;
		if (rightPosition > parentRight) {
			layoutParams.rightMargin = parentRight - rightPosition;
		}
		int bottomPosition = layoutParams.topMargin + buttonHeight;
		if (bottomPosition > parentBottom) {
			layoutParams.bottomMargin = parentBottom - bottomPosition;
		}
		return layoutParams;
	}

	private void animateRecordingInterface(int direction, View keyView) {
		mPlayButton.setVisibility(View.VISIBLE);
		mDeleteButton.setVisibility(View.VISIBLE);
		mSaveButton.setVisibility(View.VISIBLE);

		// animate the control buttons out to be equally spaced around the record button
		float buttonOffset = -mPlayButton.getWidth();
		PointF centre = new PointF(0, 0);
		PointF startingPoint = new PointF(0, buttonOffset);
		double radRot = Math.toRadians(-120);
		double cosRot = Math.cos(radRot);
		double sinRot = Math.sin(radRot);
		QRImageParser.rotatePoint(startingPoint, centre, cosRot, sinRot);
		float leftX = startingPoint.x;
		float leftY = startingPoint.y;
		QRImageParser.rotatePoint(startingPoint, centre, cosRot, sinRot);
		float rightX = startingPoint.x;
		float rightY = startingPoint.y;

		RelativeLayout parent;
		AnimatorSet buttonAnimator = new AnimatorSet();
		switch (direction) {
			case 1: // out
				// on an outward animation, we want the three minor buttons to have priority so the record button's
				// larger click area doesn't capture their clicks
				mPlayButton.bringToFront();
				mDeleteButton.bringToFront();
				mSaveButton.bringToFront();
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
					// need to manually re-layout before KitKat
					parent = (RelativeLayout) mPlayButton.getParent();
					parent.requestLayout();
					parent.invalidate();
				}

				buttonAnimator.playTogether(ObjectAnimator.ofFloat(mDeleteButton, "translationX", 0, leftX),
						ObjectAnimator.ofFloat(mDeleteButton, "translationY", 0, leftY),
						ObjectAnimator.ofFloat(mSaveButton, "translationX", 0, rightX),
						ObjectAnimator.ofFloat(mSaveButton, "translationY", 0, rightY),
						ObjectAnimator.ofFloat(mPlayButton, "translationY", 0, buttonOffset));
				buttonAnimator.setInterpolator(new OvershootInterpolator());
				break;
			case -1: // in
				// keyView is the view we want to be at the front after an inward animation
				if (keyView != null) {
					keyView.bringToFront();
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
						// need to manually re-layout before KitKat
						parent = (RelativeLayout) keyView.getParent();
						parent.requestLayout();
						parent.invalidate();
					}
				}

				buttonAnimator.playTogether(ObjectAnimator.ofFloat(mDeleteButton, "translationX", leftX, 0),
						ObjectAnimator.ofFloat(mDeleteButton, "translationY", leftY, 0),
						ObjectAnimator.ofFloat(mSaveButton, "translationX", rightX, 0),
						ObjectAnimator.ofFloat(mSaveButton, "translationY", rightY, 0),
						ObjectAnimator.ofFloat(mPlayButton, "translationY", buttonOffset, 0));
				buttonAnimator.setInterpolator(new AnticipateInterpolator());
				break;
		}
		buttonAnimator.setDuration(BUTTON_ANIMATION_DURATION);
		buttonAnimator.start();
	}

	private void delayedResetRecordingInterface() {
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				resetRecordingInterface();
			}
		}, BUTTON_ANIMATION_DURATION * 2); // show briefly in final position, then hide
	}

	private void resetRecordingInterface() {
		if (mAudioRecorder != null) {
			if (mAudioRecorder.isRecording()) {
				mAudioRecorder.pause(null);
			}
		}
		mAudioRecorder = null; // TODO: delete audio file (if it exists)?

		if (mCurrentAudioRect != null) {
			mImageView.removeAudioAreaRect(mCurrentAudioRect);
		}
		mCurrentAudioRect = null;

		mRecordButton.setVisibility(View.INVISIBLE);
		mRecordButton.setImageResource(R.drawable.ic_mic_white_24dp);
		mPlayButton.clearAnimation();
		mPlayButton.setVisibility(View.INVISIBLE);
		mDeleteButton.setVisibility(View.INVISIBLE);
		mSaveButton.setVisibility(View.INVISIBLE);
		mSaveButton.setClickable(true);

		mImageView.setScribbleEnabled(true);
		getSupportActionBar().setTitle(getString(R.string.title_activity_add));
	}

	public void streamAudioLoadCompleted(String url) {
		streamAudio(url);
	}

	public void streamAudioLoadFailed(int reason) {
		mPlayButton.clearAnimation(); // instead of just cancelling the animation, as that means we can't hide the view
		mPlayButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
		resetAudioPlayer();
		Toast.makeText(PaperChainsActivity.this, getString(reason), Toast.LENGTH_SHORT).show();
	}

	private void streamAudio(String audioPath) {
		resetAudioPlayer();

		mPlayButton.setVisibility(View.VISIBLE); // undo invisible by resetAudioPlayer();
		mPlayButton.setImageResource(R.drawable.ic_refresh_white_24dp);
		mPlayButton.startAnimation(mRotateAnimation);

		mAudioPlayer = new MediaPlayer();
		mAudioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

		try {
			try {
				// slightly hacky way of detecting whether the path is a url or a file, and handling appropriately
				new URL(audioPath); // only for the exception it throws
				mAudioPlayer.setDataSource(audioPath);
			} catch (MalformedURLException e) {
				FileInputStream inputStream = new FileInputStream(audioPath);
				mAudioPlayer.setDataSource(inputStream.getFD());
				inputStream.close();
			}
			mAudioPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
				@Override
				public void onPrepared(MediaPlayer mp) {
					mp.start();
					mPlayButton.clearAnimation();
					mPlayButton.setImageResource(R.drawable.ic_pause_white_24dp);
				}
			});
			mAudioPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
				@Override
				public void onCompletion(MediaPlayer mp) {
					mPlayButton.clearAnimation();
					mPlayButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
				}
			});
			mAudioPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
				@Override
				public boolean onError(MediaPlayer mp, int what, int extra) {
					streamAudioLoadFailed(R.string.hint_soundcloud_load_failed);
					return true;
				}
			});
			mAudioPlayer.prepareAsync();
		} catch (IOException e) {
			streamAudioLoadFailed(R.string.hint_soundcloud_load_failed);
		}
	}

	private void resetAudioPlayer() {
		mPlayButton.clearAnimation();
		mPlayButton.setVisibility(View.INVISIBLE);
		if (mAudioPlayer != null) {
			mAudioPlayer.release();
		}
		mAudioPlayer = null;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		switch (requestCode) {
			case SOUNDCLOUD_LOGIN_RESULT:
				if (resultCode == Activity.RESULT_OK && data != null) {
					String token = data.getStringExtra(SoundCloudLoginActivity.ACCESS_TOKEN_RESULT);
					if (!TextUtils.isEmpty(token)) {
						setSoundCloudAccessToken(token);
						saveAudio(); // try again now that we have a login token
					} else {
						audioSaveFailed(R.string.soundcloud_login_failed);
					}
				} else {
					audioSaveFailed(R.string.soundcloud_login_failed);
				}
				break;

			default:
				super.onActivityResult(requestCode, resultCode, data);
				break;
		}
	}
}
