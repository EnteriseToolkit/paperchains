/*
 * Copyright (c) 2014 Markus Hi
 * Portions copyright (c) 2014 Simon Robinson
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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.ImageView;

import com.github.lassana.recorder.AudioRecorder;
import com.nineoldandroids.animation.ObjectAnimator;

public class AudioRecorderCircleButton extends ImageView {

	private static final int PRESSED_COLOR_LIGHTUP = 255 / 25;
	private static final int PRESSED_RING_ALPHA = 75;
	private static final int DEFAULT_PRESSED_RING_WIDTH_DIP = 4;
	private static final int DEFAULT_RECORDING_RING_WIDTH_DIP = 0; // default to not shown
	private static final int PRESSED_ANIMATION_TIME_ID = android.R.integer.config_shortAnimTime;
	private static final int RECORDING_ANIMATION_TIME_ID = android.R.integer.config_longAnimTime;

	private int centerY;
	private int centerX;
	private int outerRadius;
	private int pressedRingRadius;
	private int recordingRingRadius;

	private Paint circlePaint;
	private Paint focusPaint;
	private Paint recordingPaint;

	private float pressedAnimationProgress;
	private float recordingAnimationProgress;

	private int pressedRingWidth;
	private int recordingRingWidth;
	private int radiusCorrection;

	private int defaultColor = Color.BLACK;
	private int pressedColor;

	private ObjectAnimator pressedAnimator;
	private ObjectAnimator recordingAnimator;

	private AudioRecorder audioRecorder;
	private float currentAmplitude;
	private boolean hasHiddenRecordingRing;

	public AudioRecorderCircleButton(Context context) {
		super(context);
		init(context, null);
	}

	public AudioRecorderCircleButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	public AudioRecorderCircleButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs);
	}

	@Override
	public void setPressed(boolean pressed) {
		super.setPressed(pressed);

		if (circlePaint != null) {
			circlePaint.setColor(pressed ? pressedColor : defaultColor);
		}

		if (pressed) {
			showPressedRing();
		} else {
			hidePressedRing();
		}
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		if (audioRecorder != null && audioRecorder.isRecording()) {
			canvas.drawCircle(centerX, centerY, recordingRingRadius + recordingAnimationProgress, recordingPaint);

			float newAmplitude = audioRecorder.getMaxAmplitude() / 16384f;
			if (newAmplitude - currentAmplitude > 0.1) {
				currentAmplitude = newAmplitude;
				showRecordingRing();
			} else if (!hasHiddenRecordingRing) {
				hideRecordingRing();
			} else {
				postInvalidateDelayed(recordingAnimator.getDuration());
			}
			currentAmplitude *= 0.82;
		}
		canvas.drawCircle(centerX, centerY, pressedRingRadius + pressedAnimationProgress, focusPaint);
		canvas.drawCircle(centerX, centerY, outerRadius - radiusCorrection, circlePaint);
		super.onDraw(canvas);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		centerX = w / 2;
		centerY = h / 2;
		outerRadius = Math.min(w, h) / 2;

		radiusCorrection = Math.max(pressedRingWidth, recordingRingWidth);
		pressedRingRadius = outerRadius - radiusCorrection - pressedRingWidth / 2;
		recordingRingRadius = outerRadius - radiusCorrection - recordingRingWidth / 2;
	}

	@SuppressWarnings("UnusedDeclaration")
	public float getPressedAnimationProgress() {
		return pressedAnimationProgress;
	}

	@SuppressWarnings("UnusedDeclaration")
	public void setPressedAnimationProgress(float pressedAnimationProgress) {
		this.pressedAnimationProgress = pressedAnimationProgress;
		this.invalidate();
	}

	@SuppressWarnings("UnusedDeclaration")
	public float getRecordingAnimationProgress() {
		return recordingAnimationProgress;
	}

	@SuppressWarnings("UnusedDeclaration")
	public void setRecordingAnimationProgress(float recordingAnimationProgress) {
		this.recordingAnimationProgress = recordingAnimationProgress;
		this.invalidate();
	}

	public void setRecorder(AudioRecorder recorder) {
		audioRecorder = recorder;
		this.invalidate();
	}

	private void setColors(int pressedColor, int recordingColor) {
		this.defaultColor = pressedColor;
		this.pressedColor = getHighlightColor(pressedColor, PRESSED_COLOR_LIGHTUP);

		circlePaint.setColor(defaultColor);
		focusPaint.setColor(defaultColor);
		focusPaint.setAlpha(PRESSED_RING_ALPHA);

		recordingPaint.setColor(recordingColor);
		recordingPaint.setAlpha(PRESSED_RING_ALPHA);

		this.invalidate();
	}

	private void hidePressedRing() {
		pressedAnimator.setFloatValues(pressedRingWidth, 0f);
		pressedAnimator.start();
	}

	private void showPressedRing() {
		pressedAnimator.setFloatValues(pressedAnimationProgress, pressedRingWidth);
		pressedAnimator.start();
	}

	private void hideRecordingRing() {
		hasHiddenRecordingRing = true;
		recordingAnimator.setFloatValues(recordingRingWidth, 0f);
		recordingAnimator.start();
	}

	private void showRecordingRing() {
		hasHiddenRecordingRing = false;
		recordingAnimator.setFloatValues(recordingAnimationProgress, recordingRingWidth);
		recordingAnimator.start();
	}

	private void init(Context context, AttributeSet attrs) {
		this.setFocusable(true);
		this.setScaleType(ScaleType.CENTER_INSIDE);
		setClickable(true);

		circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		circlePaint.setStyle(Paint.Style.FILL);

		focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		focusPaint.setStyle(Paint.Style.STROKE);

		recordingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		recordingPaint.setStyle(Paint.Style.STROKE);

		pressedRingWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
				DEFAULT_PRESSED_RING_WIDTH_DIP, getResources().getDisplayMetrics());

		recordingRingWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
				DEFAULT_RECORDING_RING_WIDTH_DIP, getResources().getDisplayMetrics());

		int pressedColor = Color.BLACK;
		int recordingColor = Color.BLACK;
		if (attrs != null) {
			final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AudioRecorderCircleButton);
			pressedColor = a.getColor(R.styleable.AudioRecorderCircleButton_cb_pressedColor, pressedColor);
			recordingColor = a.getColor(R.styleable.AudioRecorderCircleButton_cb_recordingColor, recordingColor);
			pressedRingWidth = (int) a.getDimension(R.styleable.AudioRecorderCircleButton_cb_pressedRingWidth,
					pressedRingWidth);
			recordingRingWidth = (int) a.getDimension(R.styleable.AudioRecorderCircleButton_cb_recordingRingWidth,
					recordingRingWidth);
			a.recycle();
		}

		setColors(pressedColor, recordingColor);

		focusPaint.setStrokeWidth(pressedRingWidth);
		recordingPaint.setStrokeWidth(recordingRingWidth);

		final int pressedAnimationTime = getResources().getInteger(PRESSED_ANIMATION_TIME_ID);
		pressedAnimator = ObjectAnimator.ofFloat(this, "pressedAnimationProgress", 0f, 0f);
		pressedAnimator.setDuration(pressedAnimationTime);

		final int recordingAnimationTime = getResources().getInteger(RECORDING_ANIMATION_TIME_ID);
		recordingAnimator = ObjectAnimator.ofFloat(this, "recordingAnimationProgress", 0f, 0f);
		recordingAnimator.setDuration(recordingAnimationTime);
	}

	private int getHighlightColor(int color, @SuppressWarnings("SameParameterValue") int amount) {
		return Color.argb(Math.min(255, Color.alpha(color)), Math.min(255, Color.red(color) + amount), Math.min(255,
				Color.green(color) + amount), Math.min(255, Color.blue(color) + amount));
	}
}
