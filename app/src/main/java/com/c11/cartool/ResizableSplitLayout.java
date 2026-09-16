package com.c11.cartool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 可拖动分隔条的水平三分屏布局
 *
 * 用法:
 *   ResizableSplitLayout layout = new ResizableSplitLayout(context);
 *   layout.addView(navView);    // 第1个
 *   layout.addView(carView);    // 第2个
 *   layout.addView(mediaView);  // 第3个
 *   // 两个分隔条自动生成在 view 之间
 *
 * 特性:
 *   - 拖动白色滑块调整左右区域宽度
 *   - 每个区域有最小宽度限制
 *   - 支持保存/恢复比例
 *   - 点击分隔条可重置为默认比例
 */
public class ResizableSplitLayout extends LinearLayout {

    private static final int DIVIDER_WIDTH_DP = 8;
    private static final int HIT_AREA_DP = 24;
    private static final float MIN_RATIO = 0.12f;

    private final List<View> panels = new ArrayList<>();
    private final List<DividerView> dividers = new ArrayList<>();
    private final float[] ratios = new float[3];

    private int dividerWidth;
    private int hitArea;
    private int draggingIndex = -1;
    private float startX;
    private float startRatio1, startRatio2;

    private final Paint dividerPaint = new Paint();
    private final Paint handlePaint = new Paint();

    public interface OnRatioChangedListener {
        void onRatioChanged(float[] ratios);
    }

    private OnRatioChangedListener listener;

    public ResizableSplitLayout(Context context) {
        this(context, null);
    }

    public ResizableSplitLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ResizableSplitLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);

        float density = context.getResources().getDisplayMetrics().density;
        dividerWidth = (int) (DIVIDER_WIDTH_DP * density);
        hitArea = (int) (HIT_AREA_DP * density);

        dividerPaint.setColor(0xFF333333);
        dividerPaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(0xFFFFFFFF);
        handlePaint.setStyle(Paint.Style.FILL);

        // 默认比例: 40% / 35% / 25%
        ratios[0] = 0.40f;
        ratios[1] = 0.35f;
        ratios[2] = 0.25f;
    }

    public void setOnRatioChangedListener(OnRatioChangedListener l) {
        this.listener = l;
    }

    public float[] getRatios() {
        return ratios.clone();
    }

    public void setRatios(float r0, float r1, float r2) {
        float sum = r0 + r1 + r2;
        ratios[0] = r0 / sum;
        ratios[1] = r1 / sum;
        ratios[2] = r2 / sum;
        requestLayout();
    }

    public void resetRatios() {
        ratios[0] = 0.40f;
        ratios[1] = 0.35f;
        ratios[2] = 0.25f;
        requestLayout();
        if (listener != null) listener.onRatioChanged(ratios.clone());
    }

    @Override
    public void addView(View child, int index, android.view.ViewGroup.LayoutParams params) {
        if (panels.size() < 3) {
            panels.add(child);
            super.addView(child, params);
            // 在第1、2个 panel 后插入分隔条
            if (panels.size() < 3) {
                DividerView divider = new DividerView(getContext(), panels.size() - 1);
                dividers.add(divider);
                super.addView(divider, new LayoutParams(dividerWidth, LayoutParams.MATCH_PARENT));
            }
        } else {
            super.addView(child, index, params);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int totalWidth = MeasureSpec.getSize(widthMeasureSpec);
        int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
        int dividerTotal = dividerWidth * dividers.size();
        int available = totalWidth - dividerTotal;

        for (int i = 0; i < panels.size(); i++) {
            View panel = panels.get(i);
            int w = (int) (available * ratios[i]);
            panel.measure(
                    MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
        }
        for (DividerView d : dividers) {
            d.measure(
                    MeasureSpec.makeMeasureSpec(dividerWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(totalWidth, totalHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x = l;
        int h = b - t;
        int dividerTotal = dividerWidth * dividers.size();
        int available = (r - l) - dividerTotal;

        for (int i = 0; i < panels.size(); i++) {
            View panel = panels.get(i);
            int w = (int) (available * ratios[i]);
            panel.layout(x, t, x + w, b);
            x += w;
            if (i < dividers.size()) {
                dividers.get(i).layout(x, t, x + dividerWidth, b);
                x += dividerWidth;
            }
        }
    }

    /**
     * 分隔条 View — 处理触摸拖动
     */
    private class DividerView extends View {
        final int index;
        private final Rect handleRect = new Rect();

        DividerView(Context context, int index) {
            super(context);
            this.index = index;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();

            // 背景
            canvas.drawRect(0, 0, w, h, dividerPaint);

            // 中央白色滑块 (小米风格)
            int handleW = (int) (w * 0.5f);
            int handleH = (int) (h * 0.12f);
            int left = (w - handleW) / 2;
            int top = (h - handleH) / 2;
            handleRect.set(left, top, left + handleW, top + handleH);
            canvas.drawRoundRect(
                    new android.graphics.RectF(handleRect),
                    handleW / 2f, handleW / 2f, handlePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            // 扩大触摸热区
            float x = event.getRawX();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    draggingIndex = index;
                    startX = x;
                    startRatio1 = ratios[0];
                    startRatio2 = ratios[1];
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (draggingIndex == index) {
                        float dx = x - startX;
                        float totalW = getWidth() == 0 ? 1 : ResizableSplitLayout.this.getWidth();
                        float deltaRatio = dx / totalW;
                        applyDrag(deltaRatio);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (draggingIndex == index) {
                        draggingIndex = -1;
                        getParent().requestDisallowInterceptTouchEvent(false);
                        if (listener != null) listener.onRatioChanged(ratios.clone());
                        return true;
                    }
                    break;
            }
            return super.onTouchEvent(event);
        }
    }

    /**
     * 应用拖动 — index=0 拖第1/2个之间, index=1 拖第2/3个之间
     */
    private void applyDrag(float delta) {
        if (draggingIndex == 0) {
            // 拖第一个分隔条: r0 +/- delta, r1 -/+ delta, r2 不变
            float newR0 = clamp(startRatio1 + delta, MIN_RATIO, 1 - MIN_RATIO * 2);
            float newR1 = clamp(startRatio2 - delta, MIN_RATIO, 1 - MIN_RATIO * 2);
            if (newR0 >= MIN_RATIO && newR1 >= MIN_RATIO) {
                ratios[0] = newR0;
                ratios[1] = newR1;
            }
        } else if (draggingIndex == 1) {
            // 拖第二个分隔条: r1 +/- delta, r2 -/+ delta, r0 不变
            float newR1 = clamp(startRatio1 + delta, MIN_RATIO, 1 - MIN_RATIO * 2);
            float newR2 = clamp(ratios[2] - delta, MIN_RATIO, 1 - MIN_RATIO * 2);
            if (newR1 >= MIN_RATIO && newR2 >= MIN_RATIO) {
                ratios[1] = newR1;
                ratios[2] = newR2;
            }
        }
        requestLayout();
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
