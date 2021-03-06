/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.readboy.wearlauncher.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.TextView;

import com.readboy.wearlauncher.Launcher;
import com.readboy.wearlauncher.application.AppInfo;

/**
 * TextView that draws a bubble behind the text. We cannot use a
 * LineBackgroundSpan because we want to make the bubble taller than the text
 * and TextView's clip is too aggressive.
 */
public class IconImageView extends ImageView {
	private static final String TAG = "IconImageView";
	static final float SHADOW_LARGE_RADIUS = 4.0f;
	static final float SHADOW_SMALL_RADIUS = 1.75f;
	static final float SHADOW_Y_OFFSET = 2.0f;
	static final int SHADOW_LARGE_COLOUR = 0xDD000000;
	static final int SHADOW_SMALL_COLOUR = 0xCC000000;

	private int mPrevAlpha = -1;
	private boolean mBackgroundSizeChanged;
	private Drawable mBackground;
	/**
	 * the Original application Bitmap Icon
	 */
	private Bitmap mOriginalBitmap;
	Launcher mLauncher;


	public IconImageView(Context context) {
		super(context);
		init(context);
	}

	public IconImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public IconImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	Matrix mMatrix;

	public Bitmap resizeImage(Bitmap bitmap, int w, int h) {
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();

		float scaleWidth = ((float) w) / width;
		float scaleHeight = ((float) h) / height;
		if (null == mMatrix) {
			mMatrix = new Matrix();
		} else {
			mMatrix.reset();
		}
		mMatrix.postScale(scaleWidth, scaleHeight);
		Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, mMatrix, true);
		return resizedBitmap;
	}

	private void init(Context context) {
		mLauncher = (Launcher) context;
		mBackground = getBackground();
	}

	public void applyFromShortcutInfo(AppInfo info, IconCache iconCache) {
		Bitmap b = info.getIcon(iconCache);
		mOriginalBitmap = b;
		setDrawable(b);
		setTag(info);
	}

	private void setDrawable(Bitmap bitmap) {
		int iconWidth = bitmap.getWidth();
		int iconHeight = bitmap.getHeight();
		Bitmap b = Bitmap.createBitmap(iconWidth, iconHeight, Bitmap.Config.ARGB_8888);
		Bitmap tempB = Bitmap.createBitmap(iconWidth, iconHeight, Bitmap.Config.ARGB_8888);

		// Draw Mask.
		Canvas c = new Canvas(tempB);
		c.drawColor(0x44000000);
		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
		c.drawBitmap(bitmap, 0, 0, paint);
		c.setBitmap(null);

		c.setBitmap(b);
		paint.setXfermode(null);
		c.drawBitmap(bitmap, 0, 0, paint);
		c.drawBitmap(tempB, 0, 0, paint);
		c.setBitmap(null);

		StateListDrawable state = new StateListDrawable();
		state.addState(new int[] { android.R.attr.state_pressed }, new FastBitmapDrawable(b));
		state.addState(new int[] { -android.R.attr.state_enabled }, new FastBitmapDrawable(b));
		state.addState(new int[] {}, new FastBitmapDrawable(bitmap));
		setBackground(state);
	}

	@Override
	protected boolean setFrame(int left, int top, int right, int bottom) {
		if (getLeft() != left || getRight() != right || getTop() != top || getBottom() != bottom) {
			mBackgroundSizeChanged = true;
		}
		return super.setFrame(left, top, right, bottom);
	}

	@Override
	protected boolean verifyDrawable(Drawable who) {
		return who == mBackground || super.verifyDrawable(who);
	}

	@Override
	public void setTag(Object tag) {
		super.setTag(tag);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		boolean result = super.onTouchEvent(event);

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			break;
		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP:
			break;
		}
		return result;
	}

	@Override
	public void draw(Canvas canvas) {
		final Drawable background = mBackground;
		final int scrollX = getScrollX();
		final int scrollY = getScrollY();
		if (background != null) {

			if (mBackgroundSizeChanged) {
				background.setBounds(0, 0, getRight() - getLeft(), getBottom() - getTop());
				mBackgroundSizeChanged = false;
			}

			if ((scrollX | scrollY) == 0) {
				background.draw(canvas);
			} else {
				canvas.translate(scrollX, scrollY);
				background.draw(canvas);
				canvas.translate(-scrollX, -scrollY);
			}
		}

		super.draw(canvas);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		if (mBackground != null)
			mBackground.setCallback(this);
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		if (mBackground != null)
			mBackground.setCallback(null);
	}

	@Override
	protected boolean onSetAlpha(int alpha) {
		if (mPrevAlpha != alpha) {
			mPrevAlpha = alpha;
			super.onSetAlpha(alpha);
		}
		return true;
	}

	@Override
	public void cancelLongPress() {
		super.cancelLongPress();
	}

}
