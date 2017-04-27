package com.example.meitu.layouttest;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Created by meitu on 2016/11/7.
 */

public class RatioLayout extends FrameLayout {
    public static int NO_POSITION = -1;

    /**
     * 垂直、水平方向的权重总和
     */
    private int mWidthSum = 0;
    private int mHeightSum = 0;

    /**
     * 判断子布局位置信息是否有提供的mask
     */
    private static int X_START_MASK = 1 << 0;
    private static int Y_START_MASK = 1 << 1;
    private static int X_END_MASK = 1 << 2;
    private static int Y_END_MASK = 1 << 3;

    public RatioLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public RatioLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RatioLayout(Context context) {
        this(context, null);

    }

    /**
     * 初始化，获取信息
     * @param context
     * @param attrs
     */
    public void init(Context context, AttributeSet attrs) {
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RatioLayout);
        mWidthSum = a.getInt(R.styleable.RatioLayout_widthSum, 0);
        mHeightSum = a.getInt(R.styleable.RatioLayout_heightSum, 0);
        a.recycle();
    }

    /**
     * 测量自身以及子布局
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 设置自身size大小
        int height = getLayoutParams().height > 0 ? getLayoutParams().height : 0;
        int width = getLayoutParams().width > 0 ? getLayoutParams().width : 0;
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
        // 根据比例计算子布局的大小
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            measureChildByRatio(getChildAt(i));
        }
    }

    /**
     * 测量子布局的大小，根据子布局设置的LayoutParams的位置信息计算大小，同时更新位置信息，提供给布局时使用。
     * @param child 子布局
     */
    public void measureChildByRatio(View child) {
        LayoutParams params = (LayoutParams) child.getLayoutParams();
        float childWidth = 0, childHeight = 0;
        // 根据子布局的LayoutParams信息，计算子布局的长宽
        if ((!checkWidthCertain(params.layoutSpec) || !checkHeightCertain(params.layoutSpec))
            && params.aspectRatio != 0) {
            // 当有一个方向的长度权重不确定时，根据view设置的长宽比来计算size。
            if (checkHeightCertain(params.layoutSpec)) {
                // 当垂直方向的长度确定时，计算水平方向宽度
                childHeight = getMeasuredHeight() * (params.yEnd - params.yStart) / (float) mHeightSum;
                childWidth = childHeight * params.aspectRatio;
                float widthWeight = childWidth / getMeasuredWidth() * mWidthSum;
                if ((params.layoutSpec & X_START_MASK) != 0) {
                    // 水平方向start确定时，根据宽度计算end。
                    params.xEnd = params.xStart + Math.round(widthWeight);
                } else if ((params.layoutSpec & X_END_MASK) != 0) {
                    // 水平方向end确定时，根据宽度计算start。
                    params.xStart = params.xEnd - Math.round(widthWeight);
                } else if ((params.gravity & Gravity.CENTER_HORIZONTAL) != 0) {
                    // 当水平方向start、end都不确定并且重心为居中时分别计算。
                    params.xStart = Math.round(mWidthSum / 2f - widthWeight / 2);
                    params.xEnd = Math.round(mWidthSum / 2f + widthWeight / 2);
                    Log.v("lhy", "xStart:" + params.xStart + "," + params.xEnd);
                } else {
                    // 当水平方向start、end都不确定并且无重心时。
                    params.xStart = 0;
                    params.xEnd = Math.round(widthWeight);
                }
            } else if (checkWidthCertain(params.layoutSpec)) {
                // 当水平方向确定时，计算垂直方向高度
                childWidth = getMeasuredWidth() * (params.xEnd - params.xStart) / (float) mWidthSum;
                childHeight = childWidth / params.aspectRatio;
                float heightWeight = childHeight / getMeasuredHeight() * mHeightSum;
                if ((params.layoutSpec & Y_START_MASK) != 0) {
                    // 垂直方向start确定时，根据长度计算end
                    params.yEnd = params.yStart + Math.round(heightWeight);
                } else if ((params.layoutSpec & Y_END_MASK) != 0) {
                    // 垂直方向end确定时，根据长度计算start
                    params.yStart = params.yEnd - Math.round(heightWeight);
                } else if ((params.gravity & Gravity.CENTER_VERTICAL) != 0) {
                    // 垂直方向start、end都不确定并且重心为居中时分别计算。
                    params.yStart = Math.round(mHeightSum / 2f - heightWeight / 2);
                    params.yEnd = Math.round(mHeightSum / 2f + heightWeight / 2);
                } else {
                    // 垂直方向start、end都不确定并且无重心时，靠边放置
                    params.yStart = 0;
                    params.yEnd = Math.round(heightWeight);
                }
            }
        } else {
            // 水平、垂直方向的权重都确定时，直接计算大小
            childWidth = getMeasuredWidth() * (params.xEnd - params.xStart) / (float) mWidthSum;
            childHeight = getMeasuredHeight() * (params.yEnd - params.yStart) / (float) mHeightSum;
        }
        // 将封装好的MeasureSpec传递给子布局，布局模式默认EXACTLY,因为要强制子布局的大小与我们计算的一致
        child.measure(MeasureSpec.makeMeasureSpec(Math.round(childWidth), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(Math.round(childHeight), MeasureSpec.EXACTLY));
    }

    /**
     * 布局本身以及子布局
     * @param changed
     * @param left
     * @param top
     * @param right
     * @param bottom
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            // 根据LayoutParams中的位置信息计算真实的布局位置
            int childLeft = Math.round((right - left) * params.xStart / (float) mWidthSum);
            int childTop = Math.round((bottom - top) * params.yStart / (float) mHeightSum);
            Log.v("lhy", "child:" + childLeft + "," + (childLeft + child.getMeasuredWidth()));
            // 对子布局进行布局
            child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(),
                childTop + child.getMeasuredHeight());
        }
    }

    /**
     * 检查水平方向的权重是否确定
     * @param layoutSpec
     * @return true代表确定
     */
    private boolean checkWidthCertain(int layoutSpec) {
        return (layoutSpec & (X_START_MASK | X_END_MASK)) == (X_START_MASK | X_END_MASK);
    }

    /**
     * 检查垂直方向的权重是否确定
     * @param layoutSpec
     * @return true代表确定
     */
    private boolean checkHeightCertain(int layoutSpec) {
        return (layoutSpec & (Y_START_MASK | Y_END_MASK)) == (Y_START_MASK | Y_END_MASK);
    }

    /**
     * 自定义的LayoutParams主要包含控件的位置信息，自身长宽比等
     */
    public static class LayoutParams extends FrameLayout.LayoutParams {
        public int layoutSpec;
        public int xStart;
        public int yStart;
        public int xEnd;
        public int yEnd;
        public int gravity;
        public float aspectRatio;

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams layoutParams) {
            super(layoutParams);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            final TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.RatioLayout_Layout);
            xStart = a.getInt(R.styleable.RatioLayout_Layout_x_start, NO_POSITION);
            xEnd = a.getInt(R.styleable.RatioLayout_Layout_x_end, NO_POSITION);
            yStart = a.getInt(R.styleable.RatioLayout_Layout_y_start, NO_POSITION);
            yEnd = a.getInt(R.styleable.RatioLayout_Layout_y_end, NO_POSITION);
            aspectRatio = a.getFloat(R.styleable.RatioLayout_Layout_aspect_ratio, 0);
            gravity = a.getInt(R.styleable.RatioLayout_Layout_android_layout_gravity, 0);
            layoutSpec = getLayoutSpec();
            a.recycle();
        }

        private int getLayoutSpec() {
            int layoutSpec = 0;
            if (xStart != NO_POSITION) {
                layoutSpec |= X_START_MASK;
            }
            if (xEnd != NO_POSITION) {
                layoutSpec |= X_END_MASK;
            }
            if (yStart != NO_POSITION) {
                layoutSpec |= Y_START_MASK;
            }
            if (yEnd != NO_POSITION) {
                layoutSpec |= Y_END_MASK;
            }
            return layoutSpec;
        }
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        return new LayoutParams(lp);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof RatioLayout.LayoutParams;
    }
}
