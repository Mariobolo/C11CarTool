package com.c11.cartool.dashboard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 固定网格容器：按 列×行 计算格子像素，按 (x,y,spanX,spanY) 摆放卡片。
 * 布局后自动按实际尺寸计算并摆放；不含拖拽/编辑（v0.4 桌面编辑化时替换为 desktop 包的可编辑网格）。
 */
public class DashboardGridView extends FrameLayout {

    private final int columns;
    private final int rows;
    private final int marginPx;
    private final int gapPx;

    private int cellW = 1;
    private int cellH = 1;

    private final Map<View, int[]> slots = new LinkedHashMap<>();

    public DashboardGridView(Context context, int columns, int rows, int marginDp, int gapDp) {
        super(context);
        this.columns = columns;
        this.rows = rows;
        this.marginPx = dp(marginDp);
        this.gapPx = dp(gapDp);
    }

    public DashboardGridView(Context context, AttributeSet attrs) {
        this(context, 12, 6, 16, 10);
    }

    /** 记录卡片位置并加入容器；实际摆放由 onSizeChanged 触发的 applyLayout 完成 */
    public <V extends View> V addCard(V view, int x, int y, int spanX, int spanY) {
        slots.put(view, new int[]{x, y, spanX, spanY});
        addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        return view;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        compute();
        applyLayout();
    }

    /** 按当前尺寸计算格子 */
    public void compute() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        int innerW = w - marginPx * 2;
        int innerH = h - marginPx * 2;
        cellW = (innerW - (columns - 1) * gapPx) / columns;
        cellH = (innerH - (rows - 1) * gapPx) / rows;
        if (cellW <= 0) cellW = 1;
        if (cellH <= 0) cellH = 1;
    }

    /** 重新摆放全部卡片 */
    public void applyLayout() {
        for (Map.Entry<View, int[]> e : slots.entrySet()) {
            View v = e.getKey();
            int[] p = e.getValue();
            int x = p[0], y = p[1], sx = p[2], sy = p[3];
            int w = sx * cellW + (sx - 1) * gapPx;
            int h = sy * cellH + (sy - 1) * gapPx;
            LayoutParams lp = new LayoutParams(w, h);
            lp.leftMargin = marginPx + x * (cellW + gapPx);
            lp.topMargin = marginPx + y * (cellH + gapPx);
            v.setLayoutParams(lp);
        }
    }

    public int getCellW() { return cellW; }
    public int getCellH() { return cellH; }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
