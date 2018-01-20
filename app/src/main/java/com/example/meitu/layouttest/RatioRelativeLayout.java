package com.example.meitu.layouttest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IllegalFormatFlagsException;
import java.util.Map;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.Pools;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

/*********************************************
 * Author: lhy 2017/1/23
 * ********************************************
 * Version: 版本
 * Author: lhy
 * Changes: 更新点
 * ********************************************
 */
public class RatioRelativeLayout extends RelativeLayout {
    public static final String TAG = "RatioRelativeLayout";
    /**
     * 布局方向：无方向、水平、垂直
     */
    public static final int NO_ORIENTATION = -1;
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    /**
     * 适配模式
     */
    public static final int FIT_XY = 0;
    public static final int FIT_X = 1;
    public static final int FIT_Y = 2;
    // 代表数值未设置
    private static int VALUE_NOT_SET = Integer.MIN_VALUE;
    // 垂直布局规则数组
    private static final int[] RULES_VERTICAL = {ABOVE, BELOW, ALIGN_BASELINE, ALIGN_TOP, ALIGN_BOTTOM};
    // 水平布局规则数组
    private static final int[] RULES_HORIZONTAL = {LEFT_OF, RIGHT_OF, ALIGN_LEFT, ALIGN_RIGHT};
    // 所有依赖布局规则数组
    private static final int[] RULES_ALL_SORT =
        {ABOVE, BELOW, ALIGN_BASELINE, ALIGN_TOP, ALIGN_BOTTOM, LEFT_OF, RIGHT_OF, ALIGN_LEFT, ALIGN_RIGHT};
    // 布局padding
    private int mPaddingLeft = 0, mPaddingRight = 0, mPaddingTop = 0, mPaddingBottom = 0;
    // 是否需要重新计算view序列标志
    private boolean mDirtyHierarchy = true;
    // 通过依赖规则拓扑排序好的node序列
    private DependencyGraph.Node[] mSortedChildren;
    // 规则依赖图，由于拓扑排序
    private final DependencyGraph mGraph = new DependencyGraph();
    // 长宽分块总数
    public float mWidthPiece = 0, mHeightPiece = 0;
    // 设置适配模式
    public int mAdaptType = FIT_XY;
    // 是否需要裁切子布局。
    public boolean mClipChildren = true;
    // 宽高。
    public int mWidth = VALUE_NOT_SET, mHeight = VALUE_NOT_SET;

    public RatioRelativeLayout(Context context) {
        super(context);
    }

    public RatioRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RatioRelativeLayout);
            mHeightPiece = a.getFloat(R.styleable.RatioRelativeLayout_layout_heightSpec, 0);
            mWidthPiece = a.getFloat(R.styleable.RatioRelativeLayout_layout_widthSpec, 0);
            mAdaptType = a.getInt(R.styleable.RatioRelativeLayout_adaptType, FIT_XY);
            mClipChildren = a.getBoolean(R.styleable.RatioRelativeLayout_android_clipChildren, true);
            mPaddingLeft = getPaddingLeft();
            mPaddingRight = getPaddingRight();
            mPaddingBottom = getPaddingBottom();
            mPaddingTop = getPaddingTop();
            a.recycle();
        }
    }

    public RatioRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
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
        return p instanceof LayoutParams;
    }

    /**
     * 设置重新布局，虽然是public但一般不在业务逻辑中直接调用。
     */
    @Override
    public void requestLayout() {
        super.requestLayout();
        mDirtyHierarchy = true;
    }

    /**
     * 核心方法，测量自身及所有child的大小、位置。
     *
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 如果layout不变，不需要重新排序。
        if (mDirtyHierarchy) {
            mDirtyHierarchy = false;
            // 将child根据其依赖规则进行拓扑排序
            sortChildren();
            // printSortChildren();
        }

        ViewGroup.LayoutParams layoutParams = getLayoutParams();

        // 获取当前RatioRelativeLayout的大小，此布局下会屏蔽WRAP_CONTENT，使得RatioRelativeLayout本身有个固定的大小，这样才能对child进行比例布局。
        int width = layoutParams.width > 0 ? layoutParams.width : MeasureSpec.getSize(widthMeasureSpec);
        int height = layoutParams.height > 0 ? layoutParams.height : MeasureSpec.getSize(heightMeasureSpec);

        if (width != mWidth || height != mHeight || mDirtyHierarchy) {
            mWidth = width;
            mHeight = height;

            // 计算比例块大小。
            resolveTotalPiece(width, height);

            // 将child设置的比例margin和size转换成在当前尺度下正式margin与size，
            // 如果只是需要比例布局，而不涉及其他规则，则完全可以复写onMeasure方法，并在调用super.onMeasure方法前加入这一次逻辑即可。
            resolveChildSizeAndMargin(width, height);
            // 重置布局状态。
            resetChildLayoutState();

            // 根据之前排序的node，逐个对它们进行测量。
            DependencyGraph.Node[] nodes = mSortedChildren;
            for (int i = 0; i < nodes.length; i++) {
                final View child = nodes[i].view;
                if (child.getVisibility() != GONE) {
                    final LayoutParams params = (LayoutParams) child.getLayoutParams();
                    // 获取当前child所有规则对应的viewId，如果child不包含当前规则，则为0。
                    int[] rules = params.getRules();
                    // 根据node的方向进行测量。
                    if (nodes[i].orientation == HORIZONTAL) {
                        // 应用水平方向规则，求出child的在此方向上的限制边界。
                        applyHorizontalSizeRules(child, width, rules);
                        // 根据通用规则测量child大小。
                        measureChild(child, params, width, height);
                        // 根据child的边界与测量的child大小设置child的位置。
                        positionChildHorizontal(child, params, width);
                    } else {
                        // 应用水平方向规则，求出child的在此方向上的限制边界。
                        applyVerticalSizeRules(child, height, child.getBaseline());
                        // 根据通用规则测量child大小。
                        measureChild(child, params, width, height);
                        // 根据child的边界与测量的child大小设置child的位置。
                        positionChildVertical(child, params, height);
                    }
                }
            }
        }

        // 设置自身大小
        setMeasuredDimension(width, height);
    }

    /**
     * 处理 widthPiece 与 heightPiece。
     */
    private void resolveTotalPiece(int width, int height) {
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        // 如果其父类也是RatioRelativeLayout，则查看是否需要基础ratioHeight，ratioWidth作为heightSpec和widthSpec。
        if (layoutParams instanceof LayoutParams && (mHeightPiece <= 0 || mWidthPiece <= 0)) {

            mHeightPiece = mHeightPiece <= 0 ? (int) ((LayoutParams) layoutParams).ratioHeight : mHeightPiece;
            mWidthPiece = mWidthPiece <= 0 ? (int) ((LayoutParams) layoutParams).ratioWidth : mWidthPiece;

            float aspectRatio = ((LayoutParams) layoutParams).aspectRatio;
            if (aspectRatio != 0) {
                if (mWidthPiece <= 0 && mHeightPiece > 0) {
                    mWidthPiece = Math.round(mHeightPiece * aspectRatio);
                } else if (mHeightPiece <= 0 && mWidthPiece > 0) {
                    mHeightPiece = Math.round(mWidthPiece / aspectRatio);
                }
            }
        }
        // 根据适配模式重新计算piece。
        if (mAdaptType == FIT_X && mWidthPiece > 0) {
            mHeightPiece = Math.round(mWidthPiece * height / (float) width);
        }
        if (mAdaptType == FIT_Y && mHeightPiece > 0) {
            mWidthPiece = Math.round(mHeightPiece * width / (float) height);
        }
    }

    /**
     * 在onMeasure阶段，childView的位置信息已经存储到params中的，因为RelativeLayout是的布局规则只有确定所有child的位置才能测量出所有child的长度。
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                LayoutParams st = (LayoutParams) child.getLayoutParams();
                child.layout(st.mLeft, st.mTop, st.mRight, st.mBottom);
            }
        }
    }

    /**
     * 将child设置的比例margin和size转换成在当前尺度下正式margin与size.
     *
     * @param width  当前layout真实的width。
     * @param height 当前layout真实的height。
     */
    private void resolveChildSizeAndMargin(int width, int height) {
        if (mWidthPiece <= 0 || mHeightPiece <= 0) {
            return;
        }
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            ViewGroup.LayoutParams params = getChildAt(i).getLayoutParams();
            if (params instanceof LayoutParams) {
                LayoutParams layoutParams = (LayoutParams) params;
                // 计算真实margin。
                if (layoutParams.ratioMarginTop != 0 && mHeightPiece != 0) {
                    layoutParams.topMargin = Math.round(layoutParams.ratioMarginTop / (float) mHeightPiece * height);
                }
                if (layoutParams.ratioMarginBottom != 0 && mHeightPiece != 0) {
                    layoutParams.bottomMargin =
                        Math.round(layoutParams.ratioMarginBottom / (float) mHeightPiece * height);
                }
                if (layoutParams.ratioMarginLeft != 0 && mWidthPiece != 0) {
                    layoutParams.leftMargin = Math.round(layoutParams.ratioMarginLeft / (float) mWidthPiece * width);
                }
                if (layoutParams.ratioMarginRight != 0 && mWidthPiece != 0) {
                    layoutParams.rightMargin = Math.round(layoutParams.ratioMarginRight / (float) mWidthPiece * width);
                }
                // 计算真实的size。
                if (layoutParams.ratioWidth != -1) {
                    layoutParams.width = Math.round(layoutParams.ratioWidth / (float) mWidthPiece * width);
                }
                if (layoutParams.ratioHeight != -1) {
                    layoutParams.height = Math.round(layoutParams.ratioHeight / (float) mHeightPiece * height);
                }
            }
        }
    }

    /**
     * 重置孩子的布局状态。
     */
    private void resetChildLayoutState() {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            ViewGroup.LayoutParams params = getChildAt(i).getLayoutParams();
            if (params instanceof LayoutParams) {
                ((LayoutParams) params).hasLayoutVertical = false;
                ((LayoutParams) params).hasLayoutHorizontal = false;
            }
        }
    }

    /**
     * 将子View进行拓扑排序
     */
    private void sortChildren() {
        final int count = getChildCount();
        // 每个child都有两个node，一个是水平方向的，一个是竖直方向。
        if (mSortedChildren == null || mSortedChildren.length != count * 2) {
            mSortedChildren = new DependencyGraph.Node[count * 2];
        }
        // 清理之前的graph。
        final DependencyGraph graph = mGraph;
        graph.clear();
        // 添加child进入graph中
        for (int i = 0; i < count; i++) {
            graph.add(getChildAt(i));
        }

        graph.getSortedNodes(mSortedChildren, RULES_ALL_SORT);
    }

    /**
     * 测量child的size，每个view都会被测量两遍。
     *
     * @param child
     * @param params
     * @param myWidth
     * @param myHeight
     */
    private void measureChild(View child, LayoutParams params, int myWidth, int myHeight) {
        int[] rules = params.getRules();
        boolean requestHorizontalCenter = rules[CENTER_HORIZONTAL] != 0 || rules[CENTER_IN_PARENT] != 0;
        boolean requestVerticalCenter = rules[CENTER_VERTICAL] != 0 || rules[CENTER_IN_PARENT] != 0;

        int childWidthMeasureSpec;
        childWidthMeasureSpec = getChildMeasureSpec(params.mLeft, params.mRight, params.width, params.leftMargin,
            params.rightMargin, mPaddingLeft, mPaddingRight, myWidth, requestHorizontalCenter);

        int childHeightMeasureSpec;
        childHeightMeasureSpec = getChildMeasureSpec(params.mTop, params.mBottom, params.height, params.topMargin,
            params.bottomMargin, mPaddingTop, mPaddingBottom, myHeight, requestVerticalCenter);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);

        // 最大最小宽高比限制
        if (params.maxAspectRatio != 0 || params.minAspectRatio != 0) {
            if (params.layoutHorizonFirst) {
                // 高依赖宽的情况
                int childWidth = child.getMeasuredWidth();
                if (params.maxAspectRatio > 0 && childWidth / params.maxAspectRatio > child.getMeasuredHeight()) {
                    int minHeight = Math.round(childWidth / params.maxAspectRatio);
                    childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(minHeight, MeasureSpec.EXACTLY);
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                } else if (params.minAspectRatio > 0
                    && childWidth / params.minAspectRatio < child.getMeasuredHeight()) {
                    int maxHeight = Math.round(childWidth / params.minAspectRatio);
                    childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY);
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                }
            } else {
                // 宽依赖高的情况。
                int childHeight = child.getMeasuredHeight();
                if (params.maxAspectRatio > 0 && childHeight * params.maxAspectRatio < child.getMeasuredWidth()) {
                    int maxWidth = Math.round(childHeight * params.maxAspectRatio);
                    childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                } else if (params.minAspectRatio > 0
                    && childHeight * params.minAspectRatio > child.getMeasuredWidth()) {
                    int minWidth = Math.round(childHeight * params.minAspectRatio);
                    childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(minWidth, MeasureSpec.EXACTLY);
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                }
            }
        }

    }

    /**
     * Get a measure spec that accounts for all of the constraints on this view.
     * This includes size constraints imposed by the RelativeLayout as well as
     * the View's desired dimension.
     *
     * @param childStart   The left or top field of the child's layout params
     * @param childEnd     The right or bottom field of the child's layout params
     * @param childSize    The child's desired size (the width or height field of
     *                     the child's layout params)
     * @param startMargin  The left or top margin
     * @param endMargin    The right or bottom margin
     * @param startPadding mPaddingLeft or mPaddingTop
     * @param endPadding   mPaddingRight or mPaddingBottom
     * @param mySize       The width or height of this view (the RelativeLayout)
     * @return MeasureSpec for the child
     * 获取child的MeasureSpec，这个方法会根据child的限制（mTop、mBottom等）构造出合理的大小。
     */
    private int getChildMeasureSpec(int childStart, int childEnd, int childSize, int startMargin, int endMargin,
        int startPadding, int endPadding, int mySize, boolean requestCenter) {
        int childSpecMode = MeasureSpec.UNSPECIFIED;
        int childSpecSize = MeasureSpec.UNSPECIFIED;

        // Figure out start and end bounds.
        int tempStart = childStart;
        int tempEnd = childEnd;

        // If the view did not express a layout constraint for an edge, use
        // view's margins and our padding
        if (tempStart == VALUE_NOT_SET) {
            tempStart = startPadding + startMargin;
        }
        if (tempEnd == VALUE_NOT_SET) {
            tempEnd = mySize - endPadding - endMargin;
        }

        // Figure out maximum size available to this view
        final int maxAvailable = tempEnd - tempStart;

        if (childStart != VALUE_NOT_SET && childEnd != VALUE_NOT_SET && !requestCenter) {
            // Constraints fixed both edges, so child must be an exact size.
            childSpecMode = MeasureSpec.EXACTLY;
            childSpecSize = Math.max(0, maxAvailable);
        } else {
            if (childSize >= 0) {
                // Child wanted an exact size. Give as much as possible.
                childSpecMode = MeasureSpec.EXACTLY;
                // 在父布局设置了ClipChildren为false的情况下不应限制子布局的位置
                if (maxAvailable >= 0 && mClipChildren) {
                    // We have a maximum size in this dimension.
                    childSpecSize = Math.min(maxAvailable, childSize);
                } else {
                    // We can grow in this dimension.
                    childSpecSize = childSize;
                }
            } else if (childSize == LayoutParams.MATCH_PARENT) {
                // Child wanted to be as big as possible. Give all available
                // space.
                childSpecMode = MeasureSpec.EXACTLY;
                childSpecSize = Math.max(0, maxAvailable);
            } else if (childSize == LayoutParams.WRAP_CONTENT) {
                // Child wants to wrap content. Use AT_MOST to communicate
                // available space if we know our max size.
                // 在父布局设置了ClipChildren为false情况下不应限制子布局的位置
                if (maxAvailable >= 0 && mClipChildren) {
                    // We have a maximum size in this dimension.
                    childSpecMode = MeasureSpec.AT_MOST;
                    childSpecSize = maxAvailable;
                } else {
                    // We can grow in this dimension. Child can be as big as it
                    // wants.
                    childSpecMode = MeasureSpec.UNSPECIFIED;
                    childSpecSize = 0;
                }
            }
        }

        return MeasureSpec.makeMeasureSpec(childSpecSize, childSpecMode);
    }

    /**
     * 应用垂直方向上的规则
     *
     * @param myHeight
     * @param myBaseline
     */

    private void applyVerticalSizeRules(View child, int myHeight, int myBaseline) {
        LayoutParams childParams = (LayoutParams) child.getLayoutParams();
        final int[] rules = childParams.getRules();
        LayoutParams anchorParams;

        // 初始化params中的限制，mTop和mBottom。
        childParams.mTop = VALUE_NOT_SET;
        childParams.mBottom = VALUE_NOT_SET;

        // 获取当前child在此规则下的其他child的anchorParams，根据anchorParams的边界来设置自己的锚点。
        anchorParams = getRelatedViewParams(rules, ABOVE, VERTICAL);
        if (anchorParams != null) {
            childParams.mBottom = anchorParams.mTop - (anchorParams.topMargin + childParams.bottomMargin);
        } else if (childParams.alignWithParent && rules[ABOVE] != 0) {
            // 如果当前child有设置当前规则依赖的view，但是这个view消失了，则已父布局做未锚点。
            if (myHeight >= 0) {
                childParams.mBottom = myHeight - mPaddingBottom - childParams.bottomMargin;
            }
        }

        /*
        下面方法的思路同上
         */
        anchorParams = getRelatedViewParams(rules, BELOW, VERTICAL);
        if (anchorParams != null) {
            childParams.mTop = anchorParams.mBottom + (anchorParams.bottomMargin + childParams.topMargin);
        } else if (childParams.alignWithParent && rules[BELOW] != 0) {
            childParams.mTop = mPaddingTop + childParams.topMargin;
        }

        anchorParams = getRelatedViewParams(rules, ALIGN_TOP, VERTICAL);
        if (anchorParams != null) {
            childParams.mTop = anchorParams.mTop + childParams.topMargin;
        } else if (childParams.alignWithParent && rules[ALIGN_TOP] != 0) {
            childParams.mTop = mPaddingTop + childParams.topMargin;
        }

        anchorParams = getRelatedViewParams(rules, ALIGN_BOTTOM, VERTICAL);
        if (anchorParams != null) {
            childParams.mBottom = anchorParams.mBottom - childParams.bottomMargin;
        } else if (childParams.alignWithParent && rules[ALIGN_BOTTOM] != 0) {
            if (myHeight >= 0) {
                childParams.mBottom = myHeight - mPaddingBottom - childParams.bottomMargin;
            }
        }

        // 当设置相对于父布局的位置时，以父布局作为锚点。
        if (0 != rules[ALIGN_PARENT_TOP]) {
            childParams.mTop = mPaddingTop + childParams.topMargin;
        }

        if (0 != rules[ALIGN_PARENT_BOTTOM]) {
            if (myHeight >= 0) {
                childParams.mBottom = myHeight - mPaddingBottom - childParams.bottomMargin;
            }
        }

        // 处理宽高比
        if (childParams.aspectRatio != 0 && childParams.hasLayoutHorizontal) {
            int height;
            if (childParams.mTop != VALUE_NOT_SET && childParams.mBottom != VALUE_NOT_SET) {
                return;
            } else if (childParams.mTop != VALUE_NOT_SET) {
                height = Math.min(Math.round(child.getMeasuredWidth() / childParams.aspectRatio),
                    myHeight - (childParams.mTop + childParams.bottomMargin + mPaddingBottom));
                childParams.mBottom = childParams.mTop + height;
            } else if (childParams.mBottom != VALUE_NOT_SET) {
                height = Math.min(Math.round(child.getMeasuredWidth() / childParams.aspectRatio),
                    childParams.mBottom - (childParams.topMargin + mPaddingTop));
                childParams.mTop = childParams.mBottom - height;
            } else {
                height = Math.min(Math.round(child.getMeasuredWidth() / childParams.aspectRatio),
                    myHeight - (childParams.topMargin + mPaddingTop + childParams.bottomMargin + mPaddingBottom));
                childParams.height = height;
            }
        }

    }

    /**
     * 应用水平方向上的规则
     *
     * @param myWidth
     * @param rules
     */
    private void applyHorizontalSizeRules(View child, int myWidth, int[] rules) {
        LayoutParams childParams = (LayoutParams) child.getLayoutParams();
        // 锚点View的属性
        LayoutParams anchorParams;

        // 初始化params中的限制，mLeft和mRight。
        childParams.mLeft = VALUE_NOT_SET;
        childParams.mRight = VALUE_NOT_SET;

        // 获取当前child在此规则下的其他child的anchorParams，根据anchorParams的边界来设置自己的锚点。
        anchorParams = getRelatedViewParams(rules, LEFT_OF, HORIZONTAL);
        if (anchorParams != null) {
            childParams.mRight = anchorParams.mLeft - (anchorParams.leftMargin + childParams.rightMargin);
        } else if (childParams.alignWithParent && rules[LEFT_OF] != 0) {
            // 如果当前child有设置当前规则依赖的view，但是这个view消失了，则已父布局做未锚点。
            if (myWidth >= 0) {
                childParams.mRight = myWidth - mPaddingRight - childParams.rightMargin;
            }
        }

        /*
         下面方法的思路同上
         */
        anchorParams = getRelatedViewParams(rules, RIGHT_OF, HORIZONTAL);
        if (anchorParams != null) {
            childParams.mLeft = anchorParams.mRight + (anchorParams.rightMargin + childParams.leftMargin);
        } else if (childParams.alignWithParent && rules[RIGHT_OF] != 0) {
            childParams.mLeft = mPaddingLeft + childParams.leftMargin;
        }

        anchorParams = getRelatedViewParams(rules, ALIGN_LEFT, HORIZONTAL);
        if (anchorParams != null) {
            childParams.mLeft = anchorParams.mLeft + childParams.leftMargin;
        } else if (childParams.alignWithParent && rules[ALIGN_LEFT] != 0) {
            childParams.mLeft = mPaddingLeft + childParams.leftMargin;
        }

        anchorParams = getRelatedViewParams(rules, ALIGN_RIGHT, HORIZONTAL);
        if (anchorParams != null) {
            childParams.mRight = anchorParams.mRight - childParams.rightMargin;
        } else if (childParams.alignWithParent && rules[ALIGN_RIGHT] != 0) {
            if (myWidth >= 0) {
                childParams.mRight = myWidth - mPaddingRight - childParams.rightMargin;
            }
        }

        // 当设置相对于父布局的位置时，以父布局作为锚点。
        if (0 != rules[ALIGN_PARENT_LEFT]) {
            childParams.mLeft = mPaddingLeft + childParams.leftMargin;
        }

        if (0 != rules[ALIGN_PARENT_RIGHT]) {
            if (myWidth >= 0) {
                childParams.mRight = myWidth - mPaddingRight - childParams.rightMargin;
            }
        }

        // 下面方法的思路同上
        if (childParams.aspectRatio != 0 && childParams.hasLayoutVertical) {
            int width;
            if (childParams.mLeft != VALUE_NOT_SET && childParams.mRight != VALUE_NOT_SET) {
                return;
            } else if (childParams.mLeft != VALUE_NOT_SET) {
                width = Math.min(Math.round(child.getMeasuredHeight() * childParams.aspectRatio),
                    myWidth - (childParams.mLeft + childParams.rightMargin + mPaddingRight));
                childParams.mRight = childParams.mLeft + width;
            } else if (childParams.mRight != VALUE_NOT_SET) {
                width = Math.min(Math.round(child.getMeasuredHeight() * childParams.aspectRatio),
                    childParams.mRight - (childParams.leftMargin + mPaddingLeft));
                childParams.mLeft = childParams.mRight - width;
            } else {
                width = Math.min(Math.round(child.getMeasuredHeight() * childParams.aspectRatio),
                    myWidth - (childParams.leftMargin + mPaddingLeft + childParams.rightMargin + mPaddingRight));
                childParams.width = width;
            }
        }

    }

    /**
     * 设置child的水平位置
     *
     * @param child
     * @param params
     * @param myWidth
     */
    private void positionChildHorizontal(View child, LayoutParams params, int myWidth) {
        // 根据条件设置真实的right与left
        int[] rules = params.getRules();
        if (rules[CENTER_HORIZONTAL] != 0 || rules[CENTER_IN_PARENT] != 0) {
            // 有居中情况
            int leftPos = params.mLeft != VALUE_NOT_SET ? params.mLeft : params.leftMargin + mPaddingLeft;
            int rightPos =
                params.mRight != VALUE_NOT_SET ? params.mRight : myWidth - params.rightMargin - mPaddingRight;
            params.mLeft = leftPos + ((rightPos - leftPos) - child.getMeasuredWidth()) / 2;
            params.mRight = params.mLeft + child.getMeasuredWidth();
        } else {
            if (params.mRight == VALUE_NOT_SET && params.mLeft != VALUE_NOT_SET) {
                params.mRight = params.mLeft + child.getMeasuredWidth();
            } else if (params.mLeft == VALUE_NOT_SET && params.mRight != VALUE_NOT_SET) {
                params.mLeft = params.mRight - child.getMeasuredWidth();
            } else if (params.mLeft == VALUE_NOT_SET && params.mRight == VALUE_NOT_SET) {
                params.mLeft = params.leftMargin + mPaddingLeft;
                params.mRight = params.mLeft + child.getMeasuredWidth();
            }
        }
        // 标记水平方向已经布局完成。
        params.hasLayoutHorizontal = true;
    }

    /**
     * 设置child的垂直位置
     *
     * @param child
     * @param params
     * @param myHeight
     */
    private void positionChildVertical(View child, LayoutParams params, int myHeight) {
        // 根据条件设置真实的right与left
        int[] rules = params.getRules();
        if (rules[CENTER_VERTICAL] != 0 || rules[CENTER_IN_PARENT] != 0) {
            // 有居中情况
            int topPos = params.mTop != VALUE_NOT_SET ? params.mTop : mPaddingTop + params.topMargin;
            int bottomPos =
                params.mBottom != VALUE_NOT_SET ? params.mBottom : myHeight - params.bottomMargin - mPaddingBottom;
            params.mTop = topPos + ((bottomPos - topPos) - child.getMeasuredHeight()) / 2;
            params.mBottom = params.mTop + child.getMeasuredHeight();
        } else {
            if (params.mBottom == VALUE_NOT_SET && params.mTop != VALUE_NOT_SET) {
                params.mBottom = params.mTop + child.getMeasuredHeight();
            } else if (params.mTop == VALUE_NOT_SET && params.mBottom != VALUE_NOT_SET) {
                params.mTop = params.mBottom - child.getMeasuredHeight();
            } else if (params.mBottom == VALUE_NOT_SET && params.mTop == VALUE_NOT_SET) {
                params.mTop = mPaddingTop + params.topMargin;
                params.mBottom = params.mTop + child.getMeasuredHeight();
            }
        }
        // 标记竖直方向已经布局完成。
        params.hasLayoutVertical = true;
    }

    /**
     * 获取一种规则下当前node的父节点的layoutParams
     *
     * @param rules    当前node的view的依赖数组，包含其依赖的view的id。
     * @param relation 规则
     * @return
     */
    private LayoutParams getRelatedViewParams(int[] rules, int relation, int orientation) {
        View v = getRelatedView(rules, relation, orientation);
        if (v != null) {
            ViewGroup.LayoutParams params = v.getLayoutParams();
            if (params instanceof LayoutParams) {
                return (LayoutParams) v.getLayoutParams();
            }
        }
        return null;
    }

    /**
     * 获取一种规则下当前node的父节点
     *
     * @param rules    当前node的view的依赖数组，包含其依赖的view的id。
     * @param relation 规则
     * @return
     */
    private View getRelatedView(int[] rules, int relation, int orientation) {
        int id = rules[relation];
        if (id != 0) {
            DependencyGraph.Node node = mGraph.mKeyNodes.get(id + "" + orientation);
            if (node == null)
                return null;
            View v = node.view;

            // Find the first non-GONE view up the chain
            // 其直接父节点可能为GONE，这种情况下则去通过依赖链去查找第一个非GONE的父节点
            while (v.getVisibility() == View.GONE) {
                rules = ((LayoutParams) v.getLayoutParams()).getRules();
                node = mGraph.mKeyNodes.get((rules[relation]) + "" + orientation);
                if (node == null)
                    return null;
                v = node.view;
            }

            return v;
        }

        return null;
    }

    /**
     * 自定义的LayoutParams主要包含控件的位置信息，自身长宽比等
     */
    public static class LayoutParams extends RelativeLayout.LayoutParams {
        // child的四个边界
        private int mLeft, mTop, mRight, mBottom;
        // 宽高比
        public float aspectRatio = 0, maxAspectRatio = 0, minAspectRatio = 0;
        // 比例size
        public float ratioWidth = -1, ratioHeight = -1;
        // 比例margin
        public float ratioMarginLeft = 0, ratioMarginRight = 0, ratioMarginTop = 0, ratioMarginBottom = 0;
        // 是否已经布局的竖直方向，水平方向。此标志未用来判断aspectRatio的依赖关系。
        private boolean hasLayoutVertical, hasLayoutHorizontal;
        // 是否先布局水平方向，只有在maxAspectRatio或minAspectRatio设置了的情况下才会生效。
        private boolean layoutHorizonFirst;

        public LayoutParams(ViewGroup.LayoutParams layoutParams) {
            super(layoutParams);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.RatioRelativeLayout_Layout);
            ratioMarginLeft = a.getFloat(R.styleable.RatioRelativeLayout_Layout_layout_ratioMarginLeft, 0);
            ratioMarginRight = a.getFloat(R.styleable.RatioRelativeLayout_Layout_layout_ratioMarginRight, 0);
            ratioMarginTop = a.getFloat(R.styleable.RatioRelativeLayout_Layout_layout_ratioMarginTop, 0);
            ratioMarginBottom = a.getFloat(R.styleable.RatioRelativeLayout_Layout_layout_ratioMarginBottom, 0);
            ratioHeight = a.getFloat(R.styleable.RatioRelativeLayout_Layout_layout_ratioHeight, -1);
            ratioWidth = a.getFloat(R.styleable.RatioRelativeLayout_Layout_layout_ratioWidth, -1);

            String ratioString = a.getString(R.styleable.RatioRelativeLayout_Layout_aspectRatio);
            aspectRatio = TextUtils.isEmpty(ratioString) ? 0 : getAspectRatioByString(ratioString);
            String maxRatioString = a.getString(R.styleable.RatioRelativeLayout_Layout_maxAspectRatio);
            maxAspectRatio = TextUtils.isEmpty(maxRatioString) ? 0 : getAspectRatioByString(maxRatioString);
            String minRatioString = a.getString(R.styleable.RatioRelativeLayout_Layout_minAspectRatio);
            minAspectRatio = TextUtils.isEmpty(minRatioString) ? 0 : getAspectRatioByString(minRatioString);
            if (maxAspectRatio < minAspectRatio) {
                throw new IllegalStateException("maxAspectRatio must larger than minAspectRatio");
            }
            layoutHorizonFirst = a.getBoolean(R.styleable.RatioRelativeLayout_Layout_layoutHorizonFirst, true);
            a.recycle();
        }

        private float getAspectRatioByString(String string) {
            if (TextUtils.isEmpty(string) || !(isNumber(string) ^ string.contains("/"))) {
                throw new IllegalStateException("aspectRatio:" + string + " is illegal.");
            }
            if (isNumber(string)) {
                return Float.parseFloat(string);
            } else {
                int index = string.indexOf("/");
                if (index < 0 || index >= string.length()) {
                    throw new IllegalStateException("aspectRatio:" + string + " is illegal.");
                }
                String dividend = string.substring(0, index);
                String divisor = string.substring(index + 1, string.length());
                if (!isNumber(dividend) || !isNumber(divisor)) {
                    throw new IllegalStateException("aspectRatio:" + string + " is illegal.");
                }
                float a = Float.parseFloat(dividend);
                float b = Float.parseFloat(divisor);
                if (b == 0) {
                    throw new IllegalStateException("aspectRatio: divisor can't be 0");
                }
                return a / b;
            }
        }

        public static boolean isNumber(String str) {
            if (TextUtils.isEmpty(str)) {
                return false;
            }
            Pattern pattern = Pattern.compile("^[-\\+]?[\\d]*[.]?[\\d]*$");
            return pattern.matcher(str).matches();
        }

        /**
         * layoutParams中是否有指定的高度
         *
         * @return
         */
        public boolean hasCertainHeight() {
            return ratioHeight != -1 || height != WRAP_CONTENT;
        }

        /**
         * layoutParams中是否有指定的宽度
         *
         * @return
         */
        public boolean hasCertainWidth() {
            return ratioWidth != -1 || width != WRAP_CONTENT;
        }

        /**
         * 获取规则适用的方向
         *
         * @return
         */
        public static int getRuleOrientation(int rule) {
            for (int i = 0; i < RULES_HORIZONTAL.length; i++) {
                if (RULES_HORIZONTAL[i] == rule) {
                    return HORIZONTAL;
                }
            }
            for (int i = 0; i < RULES_VERTICAL.length; i++) {
                if (RULES_VERTICAL[i] == rule) {
                    return VERTICAL;
                }
            }
            return NO_ORIENTATION;
        }

    }

    /**
     * RelativeLayout的子view依赖图
     */
    private static class DependencyGraph {
        /**
         * List of all views in the graph.
         * 所有node
         */
        private ArrayList<Node> mNodes = new ArrayList<Node>();

        /**
         * List of nodes in the graph. Each node is identified by its
         * view id (see View#getId()).
         * 拥有id的view
         */
        private Map<String, Node> mKeyNodes = new HashMap<>();
        // private SparseArray<DependencyGraph.Node> mKeyNodes = new SparseArray<DependencyGraph.Node>();

        /**
         * Temporary data structure used to build the list of roots
         * for this graph.
         * 没有父节点（依赖）的node
         */
        private ArrayDeque<Node> mRoots = new ArrayDeque<Node>();

        /**
         * Clears the graph.
         * 清理所有node
         */
        void clear() {
            final ArrayList<Node> nodes = mNodes;
            final int count = nodes.size();

            for (int i = 0; i < count; i++) {
                nodes.get(i).release();
            }
            nodes.clear();

            mKeyNodes.clear();
            mRoots.clear();
        }

        /**
         * Adds a view to the graph.
         *
         * @param view The view to be added as a node to the graph.
         *             添加view到graph中，每个view应该包含两个方向的node。
         */
        void add(View view) {
            final int id = view.getId();
            // 添加水平方向node
            final DependencyGraph.Node hNode = DependencyGraph.Node.acquire(view);
            hNode.orientation = HORIZONTAL;
            hNode.key = id + "" + hNode.orientation;
            if (id != View.NO_ID) {
                mKeyNodes.put(hNode.key, hNode);
            }
            mNodes.add(hNode);
            // 添加竖直方向node
            final DependencyGraph.Node vNode = DependencyGraph.Node.acquire(view);
            vNode.orientation = VERTICAL;
            vNode.key = id + "" + vNode.orientation;
            if (id != View.NO_ID) {
                mKeyNodes.put(vNode.key, vNode);
            }
            mNodes.add(vNode);
        }

        /**
         * Builds a sorted list of views. The sorting order depends on the dependencies
         * between the view. For instance, if view C needs view A to be processed first
         * and view A needs view B to be processed first, the dependency graph
         * is: B -> A -> C. The sorted array will contain views B, A and C in this order.
         *
         * @param sorted The sorted list of views. The length of this array must
         *               be equal to getChildCount().
         * @param rules  The list of rules to take into account.
         *               获取子节点的拓扑排序，通过sorted返回结果
         */
        void getSortedNodes(Node[] sorted, int... rules) {
            // 获取所有没有父节点的节点
            final ArrayDeque<Node> roots = findRoots(rules);
            int index = 0;

            DependencyGraph.Node node;
            // 循环遍历所有root节点
            while ((node = roots.pollLast()) != null) {
                final String key = node.key;
                Log.v(TAG, "node:" + node);
                sorted[index++] = node;
                // 获取node的子节点
                final ArrayMap<Node, DependencyGraph> dependents = node.dependents;
                final int count = dependents.size();
                for (int i = 0; i < count; i++) {
                    final DependencyGraph.Node dependent = dependents.keyAt(i);
                    // 获取node的子节点的所有父节点，并去除node
                    final Map<String, Node> dependencies = dependent.dependencies;
                    dependencies.remove(key);
                    // 如果一个node没有父节点了，则将它也加入root中
                    if (dependencies.size() == 0) {
                        roots.add(dependent);
                    }
                }
            }
            // 循环依赖判断
            if (index < sorted.length) {
                throw new IllegalStateException("Circular dependencies cannot exist" + " in RelativeLayout");
            }
        }

        /**
         * Finds the roots of the graph. A root is a node with no dependency and
         * with [0..n] dependents.
         *
         * @param rulesFilter The list of rules to consider when building the
         *                    dependencies
         * @return A list of node, each being a root of the graph
         * <p>
         * 查找所有没有父节点的root节点，此函数也同时会改变所有node的父节点与子节点
         * rulesFilter代表表示父子关系的规则，如水平方向的拓扑排序规则有：LeftOf、RightOf等。
         */
        private ArrayDeque<Node> findRoots(int[] rulesFilter) {
            final Map<String, Node> keyNodes = mKeyNodes;
            final ArrayList<Node> nodes = mNodes;
            final int count = nodes.size();

            // Find roots can be invoked several times, so make sure to clear
            // all dependents and dependencies before running the algorithm
            // 清理所有node的父节点与子节点，排序需要重排
            for (int i = 0; i < count; i++) {
                final DependencyGraph.Node node = nodes.get(i);
                node.dependents.clear();
                node.dependencies.clear();
            }

            // Builds up the dependents and dependencies for each node of the graph
            // 构建每个节点的父节点与子节点
            for (int i = 0; i < count; i++) {
                final DependencyGraph.Node node = nodes.get(i);
                final LayoutParams layoutParams = (LayoutParams) node.view.getLayoutParams();
                final int[] rules = layoutParams.getRules();
                final int rulesCount = rulesFilter.length;
                Log.v(TAG, "node:" + node);
                // 判断view是否有宽高比限制，有限制的话添加依赖
                if (layoutParams.aspectRatio != 0) {
                    LayoutParams params = (LayoutParams) node.view.getLayoutParams();
                    DependencyGraph.Node dependency = null;
                    if (params.hasCertainHeight() ^ params.hasCertainWidth()) {
                        if (params.hasCertainHeight() && node.orientation == HORIZONTAL) {
                            dependency = keyNodes.get(node.view.getId() + "" + VERTICAL);
                        } else if (params.hasCertainWidth() && node.orientation == VERTICAL) {
                            dependency = keyNodes.get(node.view.getId() + "" + HORIZONTAL);
                        }
                    }
                    // Skip unknowns and self dependencies
                    if (dependency != null && dependency != node) {
                        // Add the current node as a dependent
                        dependency.dependents.put(node, this);
                        // Add a dependency to the current node
                        node.dependencies.put(dependency.key, dependency);
                    }
                } else if (layoutParams.maxAspectRatio != 0 && layoutParams.minAspectRatio != 0) {
                    LayoutParams params = (LayoutParams) node.view.getLayoutParams();
                    DependencyGraph.Node dependency = null;
                    if (params.layoutHorizonFirst && node.orientation == VERTICAL) {
                        dependency = keyNodes.get(node.view.getId() + "" + HORIZONTAL);
                    } else if (!params.layoutHorizonFirst && node.orientation == HORIZONTAL) {
                        dependency = keyNodes.get(node.view.getId() + "" + VERTICAL);
                    }
                    // Skip unknowns and self dependencies
                    if (dependency != null && dependency != node) {
                        // Add the current node as a dependent
                        dependency.dependents.put(node, this);
                        // Add a dependency to the current node
                        node.dependencies.put(dependency.key, dependency);
                    }
                }

                // Look only the the rules passed in parameter, this way we build only the
                // dependencies for a specific set of rules
                // 遍历所有的node，通过其rules(即view存储所有与其相关的view的id)，
                // 将当前node加入其依赖的子节点数组，将其依赖加入当前node的父节点数组
                for (int j = 0; j < rulesCount; j++) {
                    DependencyGraph.Node dependency = null;
                    final int rule = rules[rulesFilter[j]];
                    int ruleOrient = LayoutParams.getRuleOrientation(rulesFilter[j]);
                    if (rule > 0 && ruleOrient == node.orientation) {
                        // The node this node depends on
                        dependency = keyNodes.get(rule + "" + ruleOrient);
                    }
                    // Skip unknowns and self dependencies
                    if (dependency == null || dependency == node) {
                        continue;
                    }
                    // Add the current node as a dependent
                    dependency.dependents.put(node, this);
                    // Add a dependency to the current node
                    node.dependencies.put(dependency.key, dependency);
                }
            }

            final ArrayDeque<Node> roots = mRoots;
            roots.clear();

            // Finds all the roots in the graph: all nodes with no dependencies
            // 将所有没有父节点的node加入root数组
            for (int i = 0; i < count; i++) {
                final DependencyGraph.Node node = nodes.get(i);
                if (node.dependencies.size() == 0)
                    roots.addLast(node);
            }

            return roots;
        }

        /**
         * A node in the dependency graph. A node is a view, its list of dependencies
         * and its list of dependents.
         * <p>
         * A node with no dependent is considered a root of the graph.
         */
        static class Node {

            /**
             * The view representing this node in the layout.
             */
            View view;

            /**
             * 当前布局方向
             */
            int orientation;
            /**
             * 当前node的key，由id与orientation组成
             */
            String key;
            /**
             * The list of dependents for this node; a dependent is a node
             * that needs this node to be processed first.
             * 依赖它的节点，为了避免混淆称其为当前node的子节点
             */
            final ArrayMap<Node, DependencyGraph> dependents = new ArrayMap<>();

            /**
             * The list of dependencies for this node.
             * 它依赖的节点，为了避免混淆称其为当前node的父节点
             */
            final Map<String, Node> dependencies = new HashMap<>();

            /*
             * START POOL IMPLEMENTATION
             */
            // The pool is static, so all nodes instances are shared across
            // activities, that's why we give it a rather high limit
            private static final int POOL_LIMIT = 100;
            private static final Pools.SynchronizedPool<Node> sPool = new Pools.SynchronizedPool<>(POOL_LIMIT);

            // 一个静态的Node池，由于relativeLayout每次重排的时候都要新建node，所以使用这种池来避免对象频繁新建、释放。
            static DependencyGraph.Node acquire(View view) {
                DependencyGraph.Node node = sPool.acquire();
                if (node == null) {
                    node = new DependencyGraph.Node();
                }
                node.view = view;
                return node;
            }

            @Override
            public String toString() {
                return "{id:" + view.getId() + "orientation:" + orientation + "}";
            }

            void release() {
                view = null;
                dependents.clear();
                dependencies.clear();

                sPool.release(this);
            }
            /*
             * END POOL IMPLEMENTATION
             */
        }
    }
}
