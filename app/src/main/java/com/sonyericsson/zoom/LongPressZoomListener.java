/*
 * Copyright (c) 2010, Sony Ericsson Mobile Communication AB. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *    * Redistributions of source code must retain the above copyright notice, this
 *      list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright notice,
 *      this list of conditions and the following disclaimer in the documentation
 *      and/or other materials provided with the distribution.
 *    * Neither the name of the Sony Ericsson Mobile Communication AB nor the names
 *      of its contributors may be used to endorse or promote products derived from
 *      this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sonyericsson.zoom;

import android.content.Context;
import android.graphics.PointF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Listener for controlling zoom state through touch events
 */
public class LongPressZoomListener implements View.OnTouchListener {

	/**
	 * Enum defining listener modes. Before the view is touched the listener is in the UNDEFINED mode. Once touch
	 * starts
	 * it can enter either one of the other two modes: If the user scrolls over the view the listener will enter PAN
	 * mode, if the user lets his finger rest and makes a longpress the listener will enter ZOOM mode.
	 */
	private enum Mode {
		UNDEFINED, PAN, ZOOM, SCALE
	}

	/**
	 * Current listener mode
	 */
	private Mode mMode = Mode.UNDEFINED;

	/**
	 * Zoom control to manipulate
	 */
	private DynamicZoomControl mZoomControl;

	/**
	 * Whether pan and zoom are enabled
	 */
	private boolean mPanZoomEnabled;

	/**
	 * X-coordinate of previously handled touch event
	 */
	private float mX;

	/**
	 * Y-coordinate of previously handled touch event
	 */
	private float mY;

	/**
	 * X-coordinate of latest down event
	 */
	private float mDownX;

	/**
	 * Y-coordinate of latest down event
	 */
	private float mDownY;

	/**
	 * Velocity tracker for touch events
	 */
	private VelocityTracker mVelocityTracker;

	/**
	 * Distance touch can wander before we think it's scrolling
	 */
	private final int mScaledTouchSlop;

	/** Duration in ms before a press turns into a long press */
	// private final int mLongPressTimeout; // we now use the default platform "double tap to zoom"

	/**
	 * Used to schedule a long press haptic feedback vibration
	 */
	private boolean mPerformHapticFeedback;

	/**
	 * Maximum velocity for fling
	 */
	private final int mScaledMaximumFlingVelocity;

	/**
	 * To fix issue on Nexus 5 (and possibly others) where prev ScaleGestureDetector values are the same as current
	 */
	private float mPreviousScaleSpan = 0;

	private ScaleGestureDetector mScaleDetector;

	/**
	 * Creates a new instance
	 *
	 * @param context Application context
	 */
	public LongPressZoomListener(Context context) {
		// mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
		mScaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		mScaledMaximumFlingVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();
		mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
		mPanZoomEnabled = true;
	}

	/**
	 * Sets the zoom control to manipulate
	 *
	 * @param control Zoom control
	 */
	public void setZoomControl(DynamicZoomControl control) {
		mZoomControl = control;
	}

	/**
	 * Runnable that enters zoom mode
	 */
	private final Runnable mLongPressRunnable = new Runnable() {
		public void run() {
			mMode = Mode.ZOOM;
			mPerformHapticFeedback = true;
		}
	};

	// returns the last position touched on the screen (for use with onClick)
	public PointF getLastTouchPoint() {
		return new PointF(mX, mY);
	}

	// implements View.OnTouchListener
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		final int action = event.getAction();
		final float x = event.getX();
		final float y = event.getY();

		if (mPerformHapticFeedback) {
			v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); // vibrate to indicate long press
			mPerformHapticFeedback = false;
		}

		mScaleDetector.onTouchEvent(event); // check for scaling on all touch events

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
		mVelocityTracker.addMovement(event);

		switch (action) {
			case MotionEvent.ACTION_DOWN:
				mZoomControl.stopFling();
				// TODO: disabled - confusing and non-standard behaviour (also bad with new double tap to zoom API)
				// v.postDelayed(mLongPressRunnable, mLongPressTimeout);
				mDownX = x;
				mDownY = y;
				mX = x;
				mY = y;
				break;

			case MotionEvent.ACTION_MOVE:
				final float dx = (x - mX) / v.getWidth();
				final float dy = (y - mY) / v.getHeight();

				// TODO: check for scale mode won't work due to pointer IDs/masking going to default - fix?
				if (mPanZoomEnabled) {
					if (mScaleDetector.isInProgress() || mMode == Mode.SCALE) {
						mMode = Mode.SCALE;
						if (mPreviousScaleSpan > 0 || mPreviousScaleSpan < 0) {
							mZoomControl.zoom(mScaleDetector.getCurrentSpan() / mPreviousScaleSpan,
									mScaleDetector.getFocusX() / v.getWidth(), mScaleDetector.getFocusY() / v
											.getHeight());
						}
						mPreviousScaleSpan = mScaleDetector.getCurrentSpan();
					} else if (mMode == Mode.ZOOM) {
						mZoomControl.zoom((float) Math.pow(20, -dy), mDownX / v.getWidth(), mDownY / v.getHeight());
					} else if (mMode == Mode.PAN) {
						mZoomControl.pan(-dx, -dy);
					} else {
						final float scrollX = mDownX - x;
						final float scrollY = mDownY - y;

						final float dist = (float) Math.sqrt(scrollX * scrollX + scrollY * scrollY);
						if (dist >= mScaledTouchSlop) {
							v.removeCallbacks(mLongPressRunnable);
							mMode = Mode.PAN;
						}
					}
				}

				mX = x;
				mY = y;
				break;

			case MotionEvent.ACTION_UP:
				if (mMode == Mode.PAN) {
					mVelocityTracker.computeCurrentVelocity(1000, mScaledMaximumFlingVelocity);
					mZoomControl.startFling(-mVelocityTracker.getXVelocity() / v.getWidth(),
							-mVelocityTracker.getYVelocity() / v.getHeight());
				} else {
					mZoomControl.startFling(0, 0);
				}
				mX = x;
				mY = y;
				// intentionally passing through to reset

			default:
				mVelocityTracker.recycle();
				mVelocityTracker = null;
				v.removeCallbacks(mLongPressRunnable);
				mMode = Mode.UNDEFINED;
				mPreviousScaleSpan = 0;
				break;

		}

		return false; // false so the normal onClick event works if we've allowed a click
	}

	public void setPanZoomEnabled(boolean enabled) {
		mPanZoomEnabled = enabled;
	}

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			return true;
		}
	}

}
