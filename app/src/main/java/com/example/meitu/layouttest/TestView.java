package com.example.meitu.layouttest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/*********************************************
 * Author: lhy 2017/1/12
 * ********************************************
 * Version: 版本
 * Author: lhy
 * Changes: 更新点
 * ********************************************
 */
public class TestView extends View {
    public TestView(Context context) {
        super(context);
    }

    public TestView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TestView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // super.onDraw(canvas);
       // int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null, Canvas.ALL_SAVE_FLAG);
        Paint p1 = new Paint();
        p1.setColor(Color.BLUE);
        canvas.drawRect(new RectF(0, 0, 100, 100), p1);
        p1.setColor(Color.YELLOW);
        p1.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.XOR));
        canvas.drawRect(new RectF(50, 50, 150, 150), p1);
      //  canvas.restoreToCount(saveCount);
    }
}
