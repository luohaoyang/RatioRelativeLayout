package com.example.meitu.layouttest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

/*********************************************
 * Author: lhy 2017/3/11
 * ********************************************
 * Version: 版本
 * Author: lhy
 * Changes: 更新点
 * ********************************************
 */
public class LoadingView extends ImageView {
    MaterialProgressDrawable mProgressDrawable;

    public LoadingView(Context context) {
        this(context, null);
    }

    public LoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mProgressDrawable = new MaterialProgressDrawable(context, this);
        setBackgroundDrawable(mProgressDrawable);
        mProgressDrawable.setColorSchemeColors(0xffff5986);

    }

    @Override
    public void setVisibility(int visibility) {
        if (visibility != VISIBLE) {
            mProgressDrawable.stop();
        } else {
            mProgressDrawable.start();
        }
        super.setVisibility(visibility);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mProgressDrawable.start();
    }
}
