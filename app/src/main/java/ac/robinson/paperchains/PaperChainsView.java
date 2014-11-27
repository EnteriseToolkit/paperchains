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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.sonyericsson.zoom.AspectQuotient;
import com.sonyericsson.zoom.ZoomState;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

public class PaperChainsView extends View implements Observer {

	private static final int AUDIO_RECT_ALPHA = 100;
	private static final int SCRIBBLE_ALPHA = 180;

	private Bitmap mBitmap;

	private final ArrayList<Rect> mAudioAreas = new ArrayList<Rect>();
	private final Path mScribblePath = new Path();

	private DragCallback mDragCallback = null;
	private ScribbleCallback mScribbleCallback = null;
	private boolean mScribbleEnabled = false;
	private boolean mDrawAudioAreas = false;

	private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private final Paint mAudioAreaPaint = new Paint();
	private final Paint mScribblePaint = new Paint();

	private final Rect mRectSrc = new Rect();
	private final Rect mRectDst = new Rect();

	private final AspectQuotient mAspectQuotient = new AspectQuotient();
	private ZoomState mZoomState;

	// for retrieving touch points
	private float mTopPos = 0;
	private float mLeftPos = 0;
	private float mScaleFactorX = 1;
	private float mScaleFactorY = 1;

	private float mDownX;
	private float mDownY;
	private final int mScaledTouchSlop;
	private boolean mCanClick;

	public interface DragCallback {
		public void dragStarted();
	}

	public interface ScribbleCallback {
		public void scribbleCompleted(Path scribble);
	}

	public PaperChainsView(Context context, AttributeSet attrs) {
		super(context, attrs);

		Resources resources = getResources();

		mAudioAreaPaint.setStyle(Paint.Style.FILL);
		mAudioAreaPaint.setColor(resources.getColor(R.color.audio_area));
		mAudioAreaPaint.setAlpha(AUDIO_RECT_ALPHA);
		mAudioAreaPaint.setStrokeCap(Paint.Cap.ROUND);

		mScribblePaint.setStyle(Paint.Style.STROKE);
		mScribblePaint.setColor(resources.getColor(R.color.scribble));
		mScribblePaint.setAntiAlias(true);
		mScribblePaint.setAlpha(SCRIBBLE_ALPHA);
		mScribblePaint.setStrokeWidth(resources.getDimensionPixelSize(R.dimen.scribble_stroke_width));
		mScribblePaint.setStrokeCap(Paint.Cap.ROUND);
		mScribblePaint.setStrokeJoin(Paint.Join.ROUND);

		mScaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
	}

	public void setImage(Bitmap bitmap) {
		mBitmap = bitmap;

		if (mBitmap != null) {
			mAspectQuotient.updateAspectQuotient(getWidth(), getHeight(), mBitmap.getWidth(), mBitmap.getHeight());
			mAspectQuotient.notifyObservers();
		}

		invalidate();
	}

	public void setZoomState(ZoomState state) {
		if (mZoomState != null) {
			mZoomState.deleteObserver(this);
		}

		mZoomState = state;
		mZoomState.addObserver(this);

		invalidate();
	}

	public AspectQuotient getAspectQuotient() {
		return mAspectQuotient;
	}

	/**
	 * Converts a touch on screen to a point in the image coordinates
	 *
	 * @param touchPoint the touch point
	 * @return touchPoint in the image coordinates
	 */
	public Point screenPointToImagePoint(PointF touchPoint) {
		return new Point((int) screenXToImageX(touchPoint.x), (int) screenYToImageY(touchPoint.y));
	}

	private float screenXToImageX(float screenX) {
		return (screenX - mLeftPos) / mScaleFactorX;
	}

	private float screenYToImageY(float screenY) {
		return (screenY - mTopPos) / mScaleFactorY;
	}

	/**
	 * Converts a touch on the image to a point on screen
	 *
	 * @param imagePoint the image point
	 * @return imagePoint in the screen coordinates
	 */
	public PointF imagePointToScreenPoint(Point imagePoint) {
		return new PointF((imagePoint.x * mScaleFactorX) + mLeftPos, (imagePoint.y * mScaleFactorY) + mTopPos);
	}

	public void addAudioAreaRect(Rect rect) {
		mAudioAreas.add(rect);
		invalidate();
	}

	public void removeAudioAreaRect(Rect rect) {
		mAudioAreas.remove(rect);
		invalidate();
	}

	public void clearAudioAreaRects() {
		mAudioAreas.clear();
	}

	public void setDragCallback(DragCallback callback) {
		mDragCallback = callback;
	}

	public void setScribbleCallback(ScribbleCallback callback) {
		mScribbleCallback = callback;
	}

	public void setScribbleEnabled(boolean enabled) {
		mScribbleEnabled = enabled;
		if (!enabled) {
			mScribblePath.reset();
		}
		invalidate();
	}

	public void setDrawAudioRectsEnabled(boolean enabled) {
		mDrawAudioAreas = enabled;
		invalidate();
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		if (mScribbleEnabled && mScribbleCallback != null) {
			// handle scribbling on the image
			final float x = screenXToImageX(event.getX());
			final float y = screenYToImageY(event.getY());

			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					mScribblePath.reset();
					mScribblePath.moveTo(x, y);
					break;

				case MotionEvent.ACTION_MOVE:
					mScribblePath.lineTo(x, y);
					break;

				case MotionEvent.ACTION_UP:
					mScribblePath.lineTo(x, y);
					mScribbleCallback.scribbleCompleted(mScribblePath);
					break;

				default:
					break;
			}
		} else if (isClickable()) {
			// handle click so we don't click after moving beyond the touch slop
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					mDownX = event.getX();
					mDownY = event.getY();
					mCanClick = true;
					break;

				case MotionEvent.ACTION_MOVE:
					final float scrollX = mDownX - event.getX();
					final float scrollY = mDownY - event.getY();
					final float dist = (float) Math.sqrt(scrollX * scrollX + scrollY * scrollY);
					if (dist >= mScaledTouchSlop) {
						if (mCanClick && mDragCallback != null) {
							mDragCallback.dragStarted();
						}
						mCanClick = false;
					}
					break;

				case MotionEvent.ACTION_UP:
					if (mCanClick) {
						performClick();
					}
					break;
			}
		}

		invalidate();
		return true; //super.onTouchEvent(event);
	}

	/**
	 * Apply the current zoom and scale to the given canvas
	 *
	 * @param canvas The Canvas to zoom and scale
	 */
	private void applyZoomAndScale(Canvas canvas) {
		final float aspectQuotient = mAspectQuotient.get();

		final int viewWidth = getWidth();
		final int viewHeight = getHeight();
		final int bitmapWidth = mBitmap.getWidth();
		final int bitmapHeight = mBitmap.getHeight();

		final float panX = mZoomState.getPanX();
		final float panY = mZoomState.getPanY();
		final float zoomX = mZoomState.getZoomX(aspectQuotient) * viewWidth / bitmapWidth;
		final float zoomY = mZoomState.getZoomY(aspectQuotient) * viewHeight / bitmapHeight;

		// set up the source and destination rectangles
		mRectSrc.left = (int) (panX * bitmapWidth - viewWidth / (zoomX * 2));
		mRectSrc.top = (int) (panY * bitmapHeight - viewHeight / (zoomY * 2));
		mRectSrc.right = (int) (mRectSrc.left + viewWidth / zoomX);
		mRectSrc.bottom = (int) (mRectSrc.top + viewHeight / zoomY);
		mRectDst.left = getPaddingLeft();
		mRectDst.top = getPaddingTop();
		mRectDst.right = viewWidth - getPaddingRight();
		mRectDst.bottom = viewHeight - getPaddingBottom();

		// adjust the source rectangle so that it fits within the source image.
		if (mRectSrc.left < 0) {
			mRectDst.left += -mRectSrc.left * zoomX;
			mRectSrc.left = 0;
		}
		if (mRectSrc.right > bitmapWidth) {
			mRectDst.right -= (mRectSrc.right - bitmapWidth) * zoomX;
			mRectSrc.right = bitmapWidth;
		}
		if (mRectSrc.top < 0) {
			mRectDst.top += -mRectSrc.top * zoomY;
			mRectSrc.top = 0;
		}
		if (mRectSrc.bottom > bitmapHeight) {
			mRectDst.bottom -= (mRectSrc.bottom - bitmapHeight) * zoomY;
			mRectSrc.bottom = bitmapHeight;
		}

		// scale the canvas so the visible area is the zoomed part of the map
		final float leftPos = (-mRectSrc.left * zoomX) + mRectDst.left;
		final float topPos = (-mRectSrc.top * zoomY) + mRectDst.top;
		final float rightPos = ((bitmapWidth - mRectSrc.right) * zoomX) + mRectDst.right;
		final float bottomPos = ((bitmapHeight - mRectSrc.bottom) * zoomY) + mRectDst.bottom;

		final float scaleFactorX = (rightPos - leftPos) / bitmapWidth;
		final float scaleFactorY = (bottomPos - topPos) / bitmapHeight;

		canvas.translate(leftPos, topPos);
		canvas.scale(scaleFactorX, scaleFactorY, 0, 0);

		// save for touch interaction at this zoom level to avoid calculating again
		mLeftPos = leftPos;
		mTopPos = topPos;
		mScaleFactorX = scaleFactorX;
		mScaleFactorY = scaleFactorY;
	}

	private boolean isVisible(Canvas canvas, Rect r) {
		return !canvas.quickReject(r.left, r.top, r.right, r.bottom, Canvas.EdgeType.BW);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		canvas.save();

		if (mBitmap != null && mZoomState != null) {
			canvas.drawBitmap(mBitmap, mRectSrc, mRectDst, mBitmapPaint);

			// zoom/scale to the correct position
			if (!isInEditMode()) {
				applyZoomAndScale(canvas);
			}

			// draw audio areas and current scribble
			if (mDrawAudioAreas) {
				for (Rect rect : mAudioAreas) {
					if (!isVisible(canvas, rect)) {
						continue; // don't draw rects that aren't visible
					}
					canvas.drawRect(rect, mAudioAreaPaint);
				}
			}
			if (mScribbleEnabled) {
				canvas.drawPath(mScribblePath, mScribblePaint);
			}
		} else {
			canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
		}

		canvas.restore();
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		if (mBitmap != null) {
			mAspectQuotient.updateAspectQuotient(right - left, bottom - top, mBitmap.getWidth(), mBitmap.getHeight());
			mAspectQuotient.notifyObservers();
		}
	}

	public void update(Observable observable, Object data) {
		invalidate();
	}
}
